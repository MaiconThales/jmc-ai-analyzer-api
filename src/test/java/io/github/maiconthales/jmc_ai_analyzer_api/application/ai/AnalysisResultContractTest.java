package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisExecutionResult;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisStage;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisStageResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisResultContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void consolidatesStructuredMcpResultsAndKeepsFailuresAsSafeErrors() {
        var execution = new McpAnalysisExecutionResult(List.of(
                McpAnalysisStageResult.success(McpAnalysisStage.CPU_HOT_METHODS,
                        mapper.readTree("[{\"method\":\"a.B\",\"severity\":\"HIGH\"}]")),
                McpAnalysisStageResult.failed(McpAnalysisStage.GC_AND_MEMORY, new RuntimeException("secret"))));

        AnalysisResult result = AnalysisResult.fromMcp(execution);

        assertEquals(1, result.findings().size());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().message().contains("failed"));
        assertTrue(!result.toString().contains("secret"));
    }

    @Test
    void rejectsScalarAndRawFilePayloads() {
        assertThrows(IllegalArgumentException.class, () -> AnalysisResult.fromMcp(
                new McpAnalysisExecutionResult(List.of(McpAnalysisStageResult.success(
                        McpAnalysisStage.CPU_HOT_METHODS, mapper.readTree("42"))))));
        assertThrows(IllegalArgumentException.class, () -> AnalysisResult.fromMcp(
                new McpAnalysisExecutionResult(List.of(McpAnalysisStageResult.success(
                        McpAnalysisStage.CPU_HOT_METHODS, mapper.readTree("{\"jfr\":\"bytes\"}"))))));
    }

    @Test
    void redactsPathsAndRawRecordingValuesFromAllTextualFindingFields() {
        for (String field : List.of("message", "description", "name", "method")) {
            for (String value : List.of(
                    "C:\\\\private\\\\recording.jfr",
                    "raw data",
                    "raw",
                    "bytes",
                    "byte[]",
                    "Base64: SGVsbG8=")) {
                var payload = mapper.readTree("[{\"" + field + "\":\"" + value.replace("\\", "\\\\") + "\"}]");
                AnalysisResult result = AnalysisResult.fromMcp(new McpAnalysisExecutionResult(List.of(
                        McpAnalysisStageResult.success(McpAnalysisStage.CPU_HOT_METHODS, payload))));
                assertTrue(result.findings().getFirst().title().equals("[REDACTED]")
                        || result.findings().getFirst().detail().equals("[REDACTED]"),
                        () -> "Expected redaction for field=" + field + ", value=" + value);
                assertTrue(!result.toString().contains(value));
            }
        }
    }

    @Test
    void enforcesTextAndCollectionLimits() {
        assertThrows(IllegalArgumentException.class, () -> new AnalysisFinding(
                "cpu", "x".repeat(AnalysisLimits.MAX_TEXT + 1), "detail", "INFO"));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisResult(
                new AnalysisSummary(0, 0, AnalysisLimits.MAX_FINDINGS + 1),
                java.util.stream.IntStream.range(0, AnalysisLimits.MAX_FINDINGS + 1)
                        .mapToObj(i -> new AnalysisFinding("x", "t", "d", "INFO")).toList(),
                new CpuSummary(List.of()), new GcMemorySummary(List.of()), new LockSummary(List.of()),
                new ExceptionSummary(List.of()), List.of(), new AnalysisMetadata("v", "m", java.time.Instant.EPOCH)));
    }

    @Test
    void promptVersionIsExplicitDeterministicAndDoesNotInviteToolSelection() {
        AnalysisResult result = AnalysisResult.fromMcp(new McpAnalysisExecutionResult(List.of()));
        PromptBuilder builder = new PromptBuilder("prompt-v9");
        String first = builder.build(result);
        assertEquals(first, builder.build(result));
        assertTrue(first.contains("PROMPT_VERSION=prompt-v9"));
        assertTrue(first.contains("does not choose, invoke, or orchestrate MCP tools"));
        assertTrue(!first.toLowerCase().contains("raw jfr recording is provided"));
    }

    @Test
    void rejectsPromptThatExceedsGlobalLimit() {
        var findings = java.util.stream.IntStream.range(0, AnalysisLimits.MAX_FINDINGS)
                .mapToObj(i -> new AnalysisFinding(i < AnalysisLimits.MAX_ITEMS_PER_CATEGORY ? "cpu" : "gc_and_memory",
                        "title", "x".repeat(AnalysisLimits.MAX_TEXT), "INFO"))
                .toList();
        AnalysisResult result = new AnalysisResult(new AnalysisSummary(1, 0, findings.size()), findings,
                new CpuSummary(findings.stream().limit(AnalysisLimits.MAX_ITEMS_PER_CATEGORY).toList()),
                new GcMemorySummary(findings.stream().skip(AnalysisLimits.MAX_ITEMS_PER_CATEGORY).toList()), new LockSummary(List.of()),
                new ExceptionSummary(List.of()), List.of(), new AnalysisMetadata("v1", "mcp", java.time.Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new PromptBuilder().build(result));
    }

    @Test
    void exposesVersionedProviderNeutralInterpretationContract() {
        AiInterpretation interpretation = new AiInterpretation(
                "COMPLETED", "provider-neutral", "model", "prompt-v1", "{}", "summary");
        assertEquals("COMPLETED", interpretation.status());
        assertEquals("prompt-v1", interpretation.promptVersion());
    }

    @Test
    void providerPayloadUsesUtf8ByteLimit() {
        String payload = "😀".repeat(AnalysisLimits.MAX_PROVIDER_PAYLOAD / 4 + 1);
        assertThrows(IllegalArgumentException.class, () -> new AiInterpretation(
                "COMPLETED", "provider-neutral", "model", "prompt-v1", payload, "summary"));
    }

    @Test
    void promptLimitUsesUtf8BytesRatherThanJavaCharacterCount() {
        String multibyte = "😀".repeat(500);
        var findings = java.util.stream.IntStream.range(0, 14)
                .mapToObj(i -> new AnalysisFinding("other", "title", multibyte, "INFO"))
                .toList();
        AnalysisResult result = new AnalysisResult(new AnalysisSummary(1, 0, findings.size()), findings,
                new CpuSummary(List.of()), new GcMemorySummary(List.of()), new LockSummary(List.of()),
                new ExceptionSummary(List.of()), List.of(), new AnalysisMetadata("v1", "mcp", java.time.Instant.EPOCH));

        String prompt = new PromptBuilder().build(result);
        assertTrue(prompt.length() < AnalysisLimits.MAX_PROMPT_BYTES);
        assertTrue(Text.utf8Length(prompt) < AnalysisLimits.MAX_PROMPT_BYTES);

        var additional = new AnalysisResult(new AnalysisSummary(1, 0, findings.size() + 2),
                java.util.stream.Stream.concat(findings.stream(), java.util.stream.Stream.of(
                        new AnalysisFinding("other", "title", multibyte, "INFO"),
                        new AnalysisFinding("other", "title", multibyte, "INFO"))).toList(),
                new CpuSummary(List.of()), new GcMemorySummary(List.of()), new LockSummary(List.of()),
                new ExceptionSummary(List.of()), List.of(), new AnalysisMetadata("v1", "mcp", java.time.Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new PromptBuilder().build(additional));
    }
}
