package io.github.maiconthales.jmc_ai_analyzer_api.application;

import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisResult;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisSession;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStage;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.dto.JfrFileResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.JfrFileCatalogService;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    @Mock
    private AnalysisSessionRepository repository;
    @Mock
    private JfrFileCatalogService catalog;

    private AnalysisSessionService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisSessionService(repository, catalog,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(repository.transitionPendingToRunning(any(), any())).thenReturn(1);
        lenient().when(repository.transitionRunningToFinished(any(), any(), any(Integer.class), any())).thenReturn(1);
    }

    @Test
    void invalidFileIdIsRejectedBeforePersisting() {
        when(catalog.metadata("bad")).thenThrow(new IllegalArgumentException("invalid"));

        assertThrows(IllegalArgumentException.class, () -> service.createSession("bad"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createsPendingSessionFromCatalogMetadataAndFlushesIt() {
        when(catalog.metadata("opaque-id"))
                .thenReturn(new JfrFileResponse("opaque-id", "capture.jfr", 42L, NOW));
        when(repository.saveAndFlush(any(AnalysisSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AnalysisSession session = service.createSession("opaque-id");

        assertEquals(AnalysisStatus.PENDING, session.getStatus());
        assertEquals("capture.jfr", session.getFileName());
        assertEquals(42L, session.getFileSizeBytes());
        verify(catalog).metadata("opaque-id");
        ArgumentCaptor<AnalysisSession> captor = ArgumentCaptor.forClass(AnalysisSession.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals(AnalysisStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    void onlyPendingCanBeStartedAndOnlyRunningCanBeFinished() {
        AnalysisSession pending = new AnalysisSession("capture.jfr", 1, NOW);
        when(repository.findById(any())).thenReturn(java.util.Optional.of(pending));
        when(repository.findByIdForUpdate(any())).thenReturn(java.util.Optional.of(pending));

        service.startSession(UUID.randomUUID());
        assertEquals(AnalysisStatus.RUNNING, pending.getStatus());
        assertEquals(NOW, pending.getStartedAt());

        service.finishSession(UUID.randomUUID(), AnalysisStatus.COMPLETED, 0);
        assertEquals(AnalysisStatus.COMPLETED, pending.getStatus());
        assertEquals(0, pending.getErrorCount());
        assertEquals(NOW, pending.getFinishedAt());

        assertThrows(IllegalStateException.class,
                () -> service.startSession(UUID.randomUUID()));
    }

    @Test
    void preservesStagesAndResultsWhenFinishing() {
        AnalysisSession session = new AnalysisSession("capture.jfr", 1, NOW);
        session.start(NOW);
        session.addStage(new AnalysisStage("rules"));
        session.addResult(new AnalysisResult("rules-result",
                tools.jackson.databind.node.JsonNodeFactory.instance.objectNode()));
        when(repository.findById(any())).thenReturn(java.util.Optional.of(session));
        when(repository.findByIdForUpdate(any())).thenReturn(java.util.Optional.of(session));

        service.finishSession(UUID.randomUUID(), AnalysisStatus.PARTIAL, 1, NOW.plusSeconds(5));

        assertEquals(1, session.getStages().size());
        assertEquals(1, session.getResults().size());
        assertEquals(AnalysisStatus.PARTIAL, session.getStatus());
        assertEquals(1, session.getErrorCount());
    }

    @Test
    void rejectsInvalidTransitionsAndTerminalValues() {
        AnalysisSession pending = new AnalysisSession("capture.jfr", 1, NOW);
        when(repository.findById(any())).thenReturn(java.util.Optional.of(pending));
        when(repository.findByIdForUpdate(any())).thenReturn(java.util.Optional.of(pending));

        assertThrows(IllegalStateException.class,
                () -> service.finishSession(UUID.randomUUID(), AnalysisStatus.FAILED, 1, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> service.finishSession(UUID.randomUUID(), AnalysisStatus.RUNNING, 0, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> service.finishSession(UUID.randomUUID(), AnalysisStatus.FAILED, -1, NOW));
    }

    @Test
    void rejectsFinishBeforeStartTimeBeforeMutatingSession() {
        AnalysisSession running = new AnalysisSession("capture.jfr", 1, NOW);
        running.start(NOW);
        when(repository.findById(any())).thenReturn(java.util.Optional.of(running));
        when(repository.findByIdForUpdate(any())).thenReturn(java.util.Optional.of(running));

        assertThrows(IllegalArgumentException.class,
                () -> service.finishSession(UUID.randomUUID(), AnalysisStatus.COMPLETED, 0,
                        NOW.minusSeconds(1)));
        assertEquals(AnalysisStatus.RUNNING, running.getStatus());
    }

    @Test
    void treatsConditionalUpdateConflictAsInvalidTransition() {
        AnalysisSession pending = new AnalysisSession("capture.jfr", 1, NOW);
        when(repository.findById(any())).thenReturn(java.util.Optional.of(pending));
        when(repository.findByIdForUpdate(any())).thenReturn(java.util.Optional.of(pending));
        when(repository.transitionPendingToRunning(any(), any())).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> service.startSession(UUID.randomUUID()));
    }

    @Test
    void refusesToMutateWhenLockedSessionLookupIsMissing() {
        UUID sessionId = UUID.randomUUID();
        AnalysisSession pending = new AnalysisSession("capture.jfr", 1, NOW);
        when(repository.findById(sessionId)).thenReturn(java.util.Optional.of(pending));
        when(repository.findByIdForUpdate(sessionId)).thenReturn(java.util.Optional.empty());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.startSession(sessionId));

        assertEquals("Analysis session could not be locked for update: " + sessionId,
                failure.getMessage());
        assertEquals(AnalysisStatus.PENDING, pending.getStatus());
        assertTrue(pending.getStages().isEmpty());
        assertTrue(pending.getResults().isEmpty());
        verify(repository, never()).transitionPendingToRunning(any(), any());
    }

    @Test
    void domainDoesNotAllowBypassingLifecycleWithInvalidTransitions() {
        AnalysisSession pending = new AnalysisSession("capture.jfr", 1, NOW);

        assertThrows(IllegalStateException.class,
                () -> pending.finish(AnalysisStatus.COMPLETED, 0, NOW));
        pending.start(NOW);
        assertThrows(IllegalStateException.class,
                () -> pending.start(NOW));
        pending.finish(AnalysisStatus.COMPLETED, 0, NOW);
        assertThrows(IllegalStateException.class,
                () -> pending.finish(AnalysisStatus.FAILED, 1, NOW));
    }

    @Test
    void protectsChildrenCollectionsAndControlsStageLifecycle() {
        AnalysisSession session = new AnalysisSession("capture.jfr", 1, NOW);
        AnalysisStage stage = new AnalysisStage("rules");
        AnalysisResult result = new AnalysisResult("rules-result",
                tools.jackson.databind.node.JsonNodeFactory.instance.objectNode());
        session.addStage(stage);
        session.addResult(result);

        assertThrows(UnsupportedOperationException.class,
                () -> session.getStages().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> session.getResults().clear());
        assertEquals(1, session.getStages().size());
        assertEquals(1, session.getResults().size());

        stage.start(NOW);
        stage.recordError("RULE_FAILED", "rule execution failed");
        assertEquals(AnalysisStatus.RUNNING, stage.getStatus());
        assertEquals("RULE_FAILED", stage.getErrorCode());
        stage.finish(AnalysisStatus.FAILED, NOW.plusSeconds(1));
        assertEquals(AnalysisStatus.FAILED, stage.getStatus());
        assertFalse(session.getStages().isEmpty());
    }

    @Test
    void stageRejectsInvalidTransitionsAndTimes() {
        AnalysisStage stage = new AnalysisStage("rules");

        assertThrows(IllegalArgumentException.class, () -> stage.start(null));
        assertThrows(IllegalStateException.class,
                () -> stage.finish(AnalysisStatus.COMPLETED, NOW));
        stage.start(NOW);
        assertThrows(IllegalStateException.class, () -> stage.start(NOW));
        assertThrows(IllegalArgumentException.class,
                () -> stage.finish(AnalysisStatus.RUNNING, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> stage.finish(AnalysisStatus.COMPLETED, NOW.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> stage.recordError(" ", "message"));
    }
}
