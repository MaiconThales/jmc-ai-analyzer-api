package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisExecutionResult;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisStage;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisStageResult;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.ai.AiHttpClientException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class McpAnalysisAiCoordinatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void consolidatesBeforeCallingAiAndPassesOnlyBoundedMcpData() {
        AiInterpretationService ai = mock(AiInterpretationService.class);
        AtomicBoolean consolidated = new AtomicBoolean();
        doAnswer(invocation -> {
            AnalysisResult result = invocation.getArgument(0);
            consolidated.set(result != null && result.findings().size() == 1);
            return validInterpretation();
        }).when(ai).interpret(any(AnalysisResult.class));

        McpAnalysisAiCoordinator coordinator = new McpAnalysisAiCoordinator(ai,
                new PromptBuilder("v1"));
        coordinator.interpretAfterMcp(execution());

        assertTrue(consolidated.get());
        verify(ai).interpret(any(AnalysisResult.class));
        AnalysisResult sent = org.mockito.Mockito.mockingDetails(ai).getInvocations().stream()
                .findFirst().orElseThrow().getArgument(0);
        assertNotNull(sent);
        assertFalse(sent.toString().toLowerCase().contains("path"));
        assertFalse(sent.toString().toLowerCase().contains("raw"));
        assertEquals("hot.method", sent.findings().getFirst().title());
    }

    @Test
    void doesNotCallAiBeforePostMcpMethodReceivesConsolidatedExecution() {
        AiInterpretationService ai = mock(AiInterpretationService.class);
        org.mockito.Mockito.when(ai.interpret(any(AnalysisResult.class)))
                .thenReturn(validInterpretation());
        verify(ai, never()).interpret(any(AnalysisResult.class));

        McpAnalysisAiCoordinator coordinator = new McpAnalysisAiCoordinator(ai,
                new PromptBuilder("v1"));
        assertNotNull(coordinator.interpret(execution()));
        verify(ai).interpret(any(AnalysisResult.class));
    }

    @Test
    void mapsUnavailableProviderFailureWithoutDroppingMcpExecution() {
        assertFailureKind(AiHttpClientException.Kind.UNAVAILABLE, "UNAVAILABLE");
    }

    @Test
    void mapsFailedProviderFailureWithoutDroppingMcpExecution() {
        assertFailureKind(AiHttpClientException.Kind.FAILED, "FAILED");
    }

    @Test
    void preservesDisabledResultWithoutCallingProvider() {
        AiInterpretationService ai = mock(AiInterpretationService.class);
        org.mockito.Mockito.when(ai.interpret(any(AnalysisResult.class)))
                .thenReturn(new AiInterpretation("DISABLED", "provider", "model", "v1", "{}",
                        "AI provider is disabled"));
        AiInterpretationPersistenceService persistence = mock(AiInterpretationPersistenceService.class);
        UUID analysisId = UUID.randomUUID();
        McpAnalysisExecutionResult input = execution();
        int originalStageCount = input.stages().size();

        AiInterpretation result = coordinator(ai, persistence).interpretAfterMcp(analysisId, input);

        assertEquals("DISABLED", result.status());
        assertEquals(originalStageCount, input.stages().size());
        assertEquals("hot.method", input.stages().getFirst().payload().get(0).get("method").asText());
        verify(ai).interpret(any(AnalysisResult.class));
        verify(persistence).persist(eq(analysisId), argThat(value -> "DISABLED".equals(value.status())));
    }

    private void assertFailureKind(AiHttpClientException.Kind kind, String expectedStatus) {
        AiInterpretationService ai = mock(AiInterpretationService.class);
        org.mockito.Mockito.when(ai.interpret(any(AnalysisResult.class)))
                .thenThrow(new AiHttpClientException("provider failure", kind));
        AiInterpretationPersistenceService persistence = mock(AiInterpretationPersistenceService.class);
        UUID analysisId = UUID.randomUUID();
        McpAnalysisExecutionResult input = execution();

        AiInterpretation result = coordinator(ai, persistence).interpretAfterMcp(analysisId, input);

        assertEquals(expectedStatus, result.status());
        assertEquals(1, input.stages().stream()
                .filter(stage -> stage.stage() == McpAnalysisStage.CPU_HOT_METHODS).count());
        verify(persistence).persist(eq(analysisId), argThat(value -> expectedStatus.equals(value.status())));
    }

    private McpAnalysisAiCoordinator coordinator(AiInterpretationService ai,
                                                  AiInterpretationPersistenceService persistence) {
        return new McpAnalysisAiCoordinator(ai, new PromptBuilder("v1"),
                new AiProviderResponseValidator(mapper), persistence);
    }

    private McpAnalysisExecutionResult execution() {
        return new McpAnalysisExecutionResult(List.of(
                McpAnalysisStageResult.success(McpAnalysisStage.CPU_HOT_METHODS,
                        mapper.readTree("[{\"method\":\"hot.method\",\"severity\":\"HIGH\"}]")),
                McpAnalysisStageResult.failed(McpAnalysisStage.GC_AND_MEMORY,
                        new IllegalStateException("internal failure"))));
    }

    private AiInterpretation validInterpretation() {
        return new AiInterpretation("COMPLETED", "test", "model", "v1",
                "{\"summary\":\"ok\",\"overallSeverity\":\"LOW\",\"findings\":[],\"recommendations\":[],\"possibleCauses\":[],\"status\":\"COMPLETED\"}", "ok");
    }
}
