package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.InvalidJfrFileIdException;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.JfrFileCatalogService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpAnalysisToolAdapterTest {

    @TempDir
    Path directory;

    @Test
    void callsExactlyTheFiveAllowListedToolsWithControlledArgumentsAndPreservesPayload() throws Exception {
        Path recording = Files.createFile(directory.resolve("application.jfr"));
        String fileId = id(recording.getFileName().toString());
        List<Call> calls = new ArrayList<>();
        JsonNode payload = JsonNodeFactory.instance.objectNode()
                .putObject("structured")
                .put("value", "kept-as-is");
        McpProtocolClient client = (name, arguments) -> {
            calls.add(new Call(name, arguments));
            return payload;
        };
        McpAnalysisToolAdapter adapter = new McpAnalysisToolAdapter(
                client, new JfrFileCatalogService(directory, 10));

        assertSame(payload, adapter.analyzeJfrAutomatedRules(fileId, 50.0));
        adapter.getCpuHotMethods(fileId, 10);
        adapter.analyzeGcAndMemory(fileId, 10);
        adapter.findLockContentions(fileId, 50);
        adapter.getFrequentExceptions(fileId, 10);

        assertEquals(List.of(
                McpAnalysisToolAdapter.ANALYZE_JFR_AUTOMATED_RULES,
                McpAnalysisToolAdapter.GET_CPU_HOT_METHODS,
                McpAnalysisToolAdapter.ANALYZE_GC_AND_MEMORY,
                McpAnalysisToolAdapter.FIND_LOCK_CONTENTIONS,
                McpAnalysisToolAdapter.GET_FREQUENT_EXCEPTIONS),
                calls.stream().map(Call::name).toList());
        assertEquals(50.0, calls.get(0).arguments().get("minScore").doubleValue());
        assertEquals(10, calls.get(1).arguments().get("topN").intValue());
        assertEquals(10, calls.get(2).arguments().get("topClasses").intValue());
        assertEquals(50, calls.get(3).arguments().get("minDurationMs").longValue());
        assertEquals(10, calls.get(4).arguments().get("topN").intValue());
        calls.forEach(call -> assertEquals(recording.toAbsolutePath().toString(),
                call.arguments().get("filePath").textValue()));
    }

    @Test
    void rejectsArbitraryPathBeforeCallingMcp() throws Exception {
        Path recording = Files.createFile(directory.resolve("application.jfr"));
        List<Call> calls = new ArrayList<>();
        McpAnalysisToolAdapter adapter = new McpAnalysisToolAdapter(
                (name, arguments) -> {
                    calls.add(new Call(name, arguments));
                    return JsonNodeFactory.instance.objectNode();
                }, new JfrFileCatalogService(directory, 10));

        assertThrows(InvalidJfrFileIdException.class,
                () -> adapter.getCpuHotMethods(recording.toString(), 10));
        assertEquals(0, calls.size());
    }

    private static String id(String fileName) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fileName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record Call(String name, JsonNode arguments) {
    }
}
