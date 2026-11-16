package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisExecutionResult;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisStage;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisStageResult;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded, in-memory contract sent to an AI provider. It deliberately contains
 * no JsonNode, file name, path, recording bytes, or raw JFR content.
 */
public record AnalysisResult(
        AnalysisSummary summary,
        List<AnalysisFinding> findings,
        CpuSummary cpu,
        GcMemorySummary gcMemory,
        LockSummary locks,
        ExceptionSummary exceptions,
        List<AnalysisError> errors,
        AnalysisMetadata metadata) {

    private static final String REDACTED = "[REDACTED]";

    public AnalysisResult {
        summary = Objects.requireNonNull(summary, "summary");
        findings = Text.list(findings, AnalysisLimits.MAX_FINDINGS, "findings");
        cpu = Objects.requireNonNull(cpu, "cpu");
        gcMemory = Objects.requireNonNull(gcMemory, "gcMemory");
        locks = Objects.requireNonNull(locks, "locks");
        exceptions = Objects.requireNonNull(exceptions, "exceptions");
        errors = Text.list(errors, AnalysisLimits.MAX_ERRORS, "errors");
        metadata = Objects.requireNonNull(metadata, "metadata");
    }

    /** Consolidates only the five structured MCP outcomes. */
    public static AnalysisResult fromMcp(McpAnalysisExecutionResult execution) {
        Objects.requireNonNull(execution, "execution");
        List<AnalysisFinding> all = new ArrayList<>();
        List<AnalysisError> errors = new ArrayList<>();
        int success = 0;
        for (McpAnalysisStageResult outcome : execution.stages()) {
            if (outcome.status() == McpAnalysisStageResult.Status.SUCCESS) {
                success++;
                if (outcome.payload() == null || (!outcome.payload().isObject() && !outcome.payload().isArray())) {
                    throw new IllegalArgumentException("MCP payload must be an object or array: " + outcome.stage());
                }
                rejectRawKeys(outcome.payload());
                addFindings(all, outcome.stage(), outcome.payload());
            } else {
                errors.add(new AnalysisError(outcome.stage().resultType(),
                        "MCP_" + outcome.status().name(), safeMessage(outcome.status())));
            }
        }
        if (all.size() > AnalysisLimits.MAX_FINDINGS) {
            throw new IllegalArgumentException("consolidated findings exceed " + AnalysisLimits.MAX_FINDINGS);
        }
        List<AnalysisFinding> cpu = category(all, "cpu_hot_methods");
        List<AnalysisFinding> gc = category(all, "gc_and_memory");
        List<AnalysisFinding> locks = category(all, "lock_contentions");
        List<AnalysisFinding> exceptions = category(all, "frequent_exceptions");
        int failed = execution.stages().size() - success;
        return new AnalysisResult(new AnalysisSummary(success, failed, all.size()), all,
                new CpuSummary(cpu), new GcMemorySummary(gc), new LockSummary(locks),
                new ExceptionSummary(exceptions), errors,
                new AnalysisMetadata("analysis-result-v1", "mcp-structured-results", Instant.EPOCH));
    }

    private static void addFindings(List<AnalysisFinding> target, McpAnalysisStage stage, JsonNode payload) {
        if (payload.isArray()) {
            for (JsonNode item : payload) addOne(target, stage, item);
        } else {
            addOne(target, stage, payload);
        }
    }

    private static void addOne(List<AnalysisFinding> target, McpAnalysisStage stage, JsonNode node) {
        if (!node.isObject()) throw new IllegalArgumentException("MCP array items must be objects");
        String title = scalar(node, "name", scalar(node, "method", scalar(node, "rule", stage.resultType())));
        String detail = scalar(node, "message", scalar(node, "description", "structured MCP finding"));
        String severity = scalar(node, "severity", "INFO");
        target.add(new AnalysisFinding(stage.resultType(), title, detail, severity));
        if (target.size() > AnalysisLimits.MAX_FINDINGS) {
            throw new IllegalArgumentException("MCP findings exceed " + AnalysisLimits.MAX_FINDINGS);
        }
    }

    private static String scalar(JsonNode object, String key, String fallback) {
        JsonNode value = object.get(key);
        return value != null && value.isValueNode() && !value.isNull()
                ? safeMcpText(value.asText(), fallback) : fallback;
    }

    /**
     * MCP messages are technical evidence, not a channel for filesystem names,
     * recording bytes, or raw recording content. Replace suspicious values
     * before they enter the provider-neutral contract.
     */
    private static String safeMcpText(String value, String fallback) {
        String lower = value.toLowerCase(Locale.ROOT);
        boolean recordingPath = lower.contains(".jfr")
                || lower.matches(".*(?:[a-z]:[\\\\/]|^|[\\\\/])[^\\r\\n]*[\\\\/][^\\r\\n]*.*")
                || lower.matches(".*(?:^|\\s)\\.{1,2}[\\\\/].*");
        boolean rawContent = lower.contains("raw") || lower.contains("bytes")
                || lower.contains("byte[]") || lower.contains("base64");
        return recordingPath || rawContent ? REDACTED : value;
    }

    private static List<AnalysisFinding> category(List<AnalysisFinding> values, String category) {
        return values.stream().filter(value -> value.category().equals(category)).limit(AnalysisLimits.MAX_ITEMS_PER_CATEGORY).toList();
    }

    private static void rejectRawKeys(JsonNode node) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String key = field.getKey().toLowerCase(Locale.ROOT);
                if (key.contains("jfr") || key.contains("raw") || key.equals("path") || key.endsWith("_path")
                        || key.contains("filename") || key.contains("file_name")) {
                    throw new IllegalArgumentException("MCP payload contains forbidden raw/file field: " + key);
                }
                rejectRawKeys(field.getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) rejectRawKeys(child);
        }
    }

    private static String safeMessage(McpAnalysisStageResult.Status status) {
        return switch (status) {
            case TIMEOUT -> "MCP stage timed out.";
            case OVERLOADED -> "MCP stage overloaded.";
            default -> "MCP stage failed.";
        };
    }
}
