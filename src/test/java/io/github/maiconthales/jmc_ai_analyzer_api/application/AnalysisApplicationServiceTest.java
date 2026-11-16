package io.github.maiconthales.jmc_ai_analyzer_api.application;

import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisSession;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisSessionRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class AnalysisApplicationServiceTest {
    @Test
    void executorRejectionFailsPersistedSessionWithoutChangingAcceptedResponse() {
        AnalysisSessionService sessions = mock(AnalysisSessionService.class);
        McpAnalysisOrchestrator orchestrator = mock(McpAnalysisOrchestrator.class);
        AnalysisSessionRepository repository = mock(AnalysisSessionRepository.class);
        ExecutorService executor = mock(ExecutorService.class);
        AnalysisSession session = mock(AnalysisSession.class);
        UUID id = UUID.randomUUID();
        when(session.getId()).thenReturn(id);
        when(sessions.createSession("opaque-id")).thenReturn(session);
        doThrow(new RejectedExecutionException("queue full"))
                .when(executor).execute(any(Runnable.class));

        AnalysisApplicationService service = new AnalysisApplicationService(
                sessions, orchestrator, repository, executor);

        AnalysisSession returned = service.createAndSchedule(
                new io.github.maiconthales.jmc_ai_analyzer_api.api.AnalysisRequest(
                        "opaque-id", null, null, null, null));

        assertSame(session, returned);
        verify(sessions).failPendingSession(id);
        verifyNoInteractions(orchestrator, repository);
    }
}
