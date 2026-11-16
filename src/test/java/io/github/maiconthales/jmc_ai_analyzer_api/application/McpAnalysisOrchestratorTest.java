package io.github.maiconthales.jmc_ai_analyzer_api.application;

import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.McpAnalysisAiCoordinator;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.JfrFileCatalogService;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp.McpAnalysisToolAdapter;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp.McpCallHandle;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp.McpProtocolClient;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisSessionRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises the fixed five-tool orchestration seam with no real MCP process. */
class McpAnalysisOrchestratorTest {
    @Test
    void invokesEveryAllowListedToolWithControlledFilePath() {
        List<String> calls = new ArrayList<>();
        McpAnalysisToolAdapter adapter = adapter(calls, false);
        McpAnalysisExecutionResult result = orchestrator(adapter, Duration.ofSeconds(1), Duration.ofSeconds(2))
                .execute("opaque-file-id");

        assertEquals(5, result.stages().size());
        assertTrue(result.stages().stream().allMatch(stage -> stage.status() == McpAnalysisStageResult.Status.SUCCESS));
        assertEquals(List.of(
                McpAnalysisToolAdapter.ANALYZE_JFR_AUTOMATED_RULES,
                McpAnalysisToolAdapter.GET_CPU_HOT_METHODS,
                McpAnalysisToolAdapter.ANALYZE_GC_AND_MEMORY,
                McpAnalysisToolAdapter.FIND_LOCK_CONTENTIONS,
                McpAnalysisToolAdapter.GET_FREQUENT_EXCEPTIONS).size(), calls.size());
        assertTrue(calls.containsAll(List.of(
                McpAnalysisToolAdapter.ANALYZE_JFR_AUTOMATED_RULES,
                McpAnalysisToolAdapter.GET_CPU_HOT_METHODS,
                McpAnalysisToolAdapter.ANALYZE_GC_AND_MEMORY,
                McpAnalysisToolAdapter.FIND_LOCK_CONTENTIONS,
                McpAnalysisToolAdapter.GET_FREQUENT_EXCEPTIONS)));
    }

    @Test
    void preservesSuccessfulStagesWhenTheSimulatedMcpClientFailsOneTool() {
        List<String> calls = new ArrayList<>();
        McpAnalysisToolAdapter adapter = adapter(calls, true);
        McpAnalysisExecutionResult result = orchestrator(adapter, Duration.ofSeconds(1), Duration.ofSeconds(2))
                .execute("opaque-file-id");

        assertEquals(5, result.stages().size());
        assertEquals(4, result.stages().stream()
                .filter(stage -> stage.status() == McpAnalysisStageResult.Status.SUCCESS).count());
        assertEquals(McpAnalysisStageResult.Status.FAILED, result.stages().stream()
                .filter(stage -> stage.stage() == McpAnalysisStage.FREQUENT_EXCEPTIONS)
                .findFirst().orElseThrow().status());
    }

    @Test
    void turnsAClientThatNeverCompletesIntoStageTimeouts() {
        List<String> calls = new ArrayList<>();
        McpAnalysisToolAdapter adapter = adapter(calls, false, true);
        McpAnalysisExecutionResult result = orchestrator(adapter, Duration.ofMillis(25), Duration.ofMillis(150))
                .execute("opaque-file-id");

        assertEquals(5, result.stages().size());
        assertTrue(result.stages().stream().anyMatch(stage -> stage.status() == McpAnalysisStageResult.Status.TIMEOUT));
    }

    private McpAnalysisToolAdapter adapter(List<String> calls, boolean failExceptions) {
        return adapter(calls, failExceptions, false);
    }

    private McpAnalysisToolAdapter adapter(List<String> calls, boolean failExceptions, boolean neverCompletes) {
        McpProtocolClient client = mock(McpProtocolClient.class);
        JfrFileCatalogService catalog = mock(JfrFileCatalogService.class);
        when(catalog.resolveForMcp("opaque-file-id")).thenReturn(Path.of("/controlled/recording.jfr"));
        when(client.callToolAsync(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(JsonNode.class))).thenAnswer(invocation -> {
            String tool = invocation.getArgument(0);
            calls.add(tool);
            if (failExceptions && tool.equals(McpAnalysisToolAdapter.GET_FREQUENT_EXCEPTIONS)) {
                return new McpCallHandle(CompletableFuture.failedFuture(new IllegalStateException("simulated MCP failure")),
                        () -> { });
            }
            if (neverCompletes) {
                return new McpCallHandle(new CompletableFuture<>(), () -> { });
            }
            return McpCallHandle.completed(JsonNodeFactory.instance.arrayNode(), () -> { });
        });
        return new McpAnalysisToolAdapter(client, catalog);
    }

    private McpAnalysisOrchestrator orchestrator(McpAnalysisToolAdapter adapter,
                                                  Duration stageTimeout, Duration sessionTimeout) {
        ExecutorService pool = Executors.newFixedThreadPool(5, runnable -> {
            Thread thread = new Thread(runnable, "simulated-mcp-test");
            thread.setDaemon(true);
            return thread;
        });
        return new McpAnalysisOrchestrator(adapter,
                new McpAnalysisExecutor(pool, stageTimeout, sessionTimeout),
                mock(McpAnalysisResultPersistenceService.class),
                mock(AnalysisSessionService.class), mock(AnalysisSessionRepository.class),
                mock(JfrFileCatalogService.class), mock(McpAnalysisAiCoordinator.class));
    }
}
