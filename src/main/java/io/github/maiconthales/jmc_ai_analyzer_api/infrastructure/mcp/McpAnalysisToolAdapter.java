package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.JfrFileCatalogService;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Component;

/**
 * Typed, allow-listed access to the five analysis tools exposed by
 * {@code mcp-server-jmc}.
 *
 * <p>Callers provide an opaque catalog {@code fileId}, never a filesystem
 * path. The catalog validates and resolves it immediately before the request
 * is built. No tool name is accepted from a request and no JFR event is
 * interpreted in this layer.</p>
 */
@Component
public final class McpAnalysisToolAdapter {

    public static final String ANALYZE_JFR_AUTOMATED_RULES = "analyze_jfr_automated_rules";
    public static final String GET_CPU_HOT_METHODS = "get_cpu_hot_methods";
    public static final String ANALYZE_GC_AND_MEMORY = "analyze_gc_and_memory";
    public static final String FIND_LOCK_CONTENTIONS = "find_lock_contentions";
    public static final String GET_FREQUENT_EXCEPTIONS = "get_frequent_exceptions";

    private final McpProtocolClient client;
    private final JfrFileCatalogService fileCatalog;

    public McpAnalysisToolAdapter(McpProtocolClient client, JfrFileCatalogService fileCatalog) {
        this.client = Objects.requireNonNull(client, "client");
        this.fileCatalog = Objects.requireNonNull(fileCatalog, "fileCatalog");
    }

    public JsonNode analyzeJfrAutomatedRules(String fileId, double minScore) {
        requireFiniteNonNegative(minScore, "minScore");
        return await(callHandle(ANALYZE_JFR_AUTOMATED_RULES, fileId, "minScore", minScore));
    }

    public JsonNode getCpuHotMethods(String fileId, int topN) {
        requirePositive(topN, "topN");
        return await(callHandle(GET_CPU_HOT_METHODS, fileId, "topN", topN));
    }

    public JsonNode analyzeGcAndMemory(String fileId, int topClasses) {
        requirePositive(topClasses, "topClasses");
        return await(callHandle(ANALYZE_GC_AND_MEMORY, fileId, "topClasses", topClasses));
    }

    public JsonNode findLockContentions(String fileId, long minDurationMs) {
        requireNonNegative(minDurationMs, "minDurationMs");
        return await(callHandle(FIND_LOCK_CONTENTIONS, fileId, "minDurationMs", minDurationMs));
    }

    public JsonNode getFrequentExceptions(String fileId, int topN) {
        requirePositive(topN, "topN");
        return await(callHandle(GET_FREQUENT_EXCEPTIONS, fileId, "topN", topN));
    }

    public McpCallHandle analyzeJfrAutomatedRulesAsync(String fileId, double minScore) {
        requireFiniteNonNegative(minScore, "minScore");
        return callHandle(ANALYZE_JFR_AUTOMATED_RULES, fileId, "minScore", minScore);
    }

    public McpCallHandle getCpuHotMethodsAsync(String fileId, int topN) {
        requirePositive(topN, "topN");
        return callHandle(GET_CPU_HOT_METHODS, fileId, "topN", topN);
    }

    public McpCallHandle analyzeGcAndMemoryAsync(String fileId, int topClasses) {
        requirePositive(topClasses, "topClasses");
        return callHandle(ANALYZE_GC_AND_MEMORY, fileId, "topClasses", topClasses);
    }

    public McpCallHandle findLockContentionsAsync(String fileId, long minDurationMs) {
        requireNonNegative(minDurationMs, "minDurationMs");
        return callHandle(FIND_LOCK_CONTENTIONS, fileId, "minDurationMs", minDurationMs);
    }

    public McpCallHandle getFrequentExceptionsAsync(String fileId, int topN) {
        requirePositive(topN, "topN");
        return callHandle(GET_FREQUENT_EXCEPTIONS, fileId, "topN", topN);
    }

    private McpCallHandle callHandle(String toolName, String fileId, String parameterName, double value) {
        return callHandle(toolName, fileId, arguments -> arguments.put(parameterName, value));
    }

    private McpCallHandle callHandle(String toolName, String fileId, String parameterName, int value) {
        return callHandle(toolName, fileId, arguments -> arguments.put(parameterName, value));
    }

    private McpCallHandle callHandle(String toolName, String fileId, String parameterName, long value) {
        return callHandle(toolName, fileId, arguments -> arguments.put(parameterName, value));
    }

    private McpCallHandle callHandle(String toolName, String fileId, ArgumentWriter writer) {
        Path validatedPath = fileCatalog.resolveForMcp(fileId);
        ObjectNode arguments = JsonNodeFactory.instance.objectNode();
        arguments.put("filePath", validatedPath.toString());
        writer.write(arguments);
        return client.callToolAsync(toolName, arguments);
    }

    private static JsonNode await(McpCallHandle handle) {
        try {
            return handle.future().get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            handle.cancel();
            throw new IllegalStateException("Interrupted while calling MCP", exception);
        } catch (ExecutionException exception) {
            handle.close();
            throw new IllegalStateException("MCP call failed", exception.getCause());
        } finally {
            handle.close();
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and not negative");
        }
    }

    @FunctionalInterface
    private interface ArgumentWriter {
        void write(ObjectNode arguments);
    }
}
