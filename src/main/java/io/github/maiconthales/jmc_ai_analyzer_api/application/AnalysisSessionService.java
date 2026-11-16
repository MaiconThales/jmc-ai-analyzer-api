package io.github.maiconthales.jmc_ai_analyzer_api.application;

import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisSession;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStage;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.dto.JfrFileResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.JfrFileCatalogService;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisSessionRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisStageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.EnumSet;

/**
 * Owns the lifecycle of an analysis session independently of MCP execution.
 *
 * <p>The public input to this service is deliberately an opaque catalog id.
 * File metadata is read at creation time so callers never need to provide, or
 * persist, an arbitrary filesystem path.</p>
 */
@Service
public class AnalysisSessionService {

    private final AnalysisSessionRepository sessionRepository;
    private final JfrFileCatalogService fileCatalog;
    private final AnalysisStageRepository stageRepository;
    private final Clock clock;

    @Autowired
    public AnalysisSessionService(AnalysisSessionRepository sessionRepository,
                                  JfrFileCatalogService fileCatalog,
                                  AnalysisStageRepository stageRepository) {
        this(sessionRepository, fileCatalog, stageRepository, Clock.systemUTC());
    }

    public AnalysisSessionService(AnalysisSessionRepository sessionRepository,
                                  JfrFileCatalogService fileCatalog) {
        this(sessionRepository, fileCatalog, null, Clock.systemUTC());
    }

    AnalysisSessionService(AnalysisSessionRepository sessionRepository,
                           JfrFileCatalogService fileCatalog,
                           Clock clock) {
        this(sessionRepository, fileCatalog, null, clock);
    }

    AnalysisSessionService(AnalysisSessionRepository sessionRepository,
                           JfrFileCatalogService fileCatalog,
                           AnalysisStageRepository stageRepository,
                           Clock clock) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository");
        this.fileCatalog = Objects.requireNonNull(fileCatalog, "fileCatalog");
        this.stageRepository = stageRepository;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Creates and commits a PENDING session before an MCP operation can start. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalysisSession createSession(String fileId) {
        JfrFileResponse file = fileCatalog.metadata(fileId);
        return sessionRepository.saveAndFlush(new AnalysisSession(
                file.id(), file.name(), file.size(), file.modifiedAt()));
    }

    /** Moves a session to RUNNING. No other source state is accepted. */
    @Transactional
    public AnalysisSession startSession(UUID sessionId) {
        AnalysisSession session = getSessionForUpdate(sessionId);
        Instant startedAt = Instant.now(clock);
        session.validateStart(startedAt);
        if (sessionRepository.transitionPendingToRunning(sessionId, startedAt) != 1) {
            throw new IllegalStateException("Cannot start session due to a concurrent state transition");
        }
        session.start(startedAt);
        return getSession(sessionId);
    }

    /** Marks a durably-created session failed when bounded scheduling rejects it. */
    @Transactional
    public void failPendingSession(UUID sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        Instant now = Instant.now(clock);
        if (stageRepository != null) {
            AnalysisSession session = sessionRepository.findByIdForUpdate(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Analysis session not found: " + sessionId));
            AnalysisStage stage = new AnalysisStage("scheduling");
            session.addStage(stage);
            stage.start(now);
            stage.recordError("ANALYSIS_OVERLOADED", "Analysis could not be scheduled.");
            stage.finish(AnalysisStatus.FAILED, now);
            stageRepository.saveAndFlush(stage);
        }
        if (sessionRepository.transitionPendingToFailed(sessionId, now) != 1) {
            throw new IllegalStateException("Cannot fail session because it is no longer pending");
        }
    }

    /**
     * Finishes a running session while retaining all stages and results already
     * attached to it. Only terminal statuses are valid here.
     */
    @Transactional
    public AnalysisSession finishSession(UUID sessionId, AnalysisStatus status,
                                         int errorCount, Instant finishedAt) {
        AnalysisSession session = getSessionForUpdate(sessionId);
        session.validateFinish(status, errorCount, finishedAt);
        if (sessionRepository.transitionRunningToFinished(
                sessionId, status, errorCount, finishedAt) != 1) {
            throw new IllegalStateException("Cannot finish session due to a concurrent state transition");
        }
        session.finish(status, errorCount, finishedAt);
        return getSession(sessionId);
    }

    public AnalysisSession finishSession(UUID sessionId, AnalysisStatus status, int errorCount) {
        return finishSession(sessionId, status, errorCount, Instant.now(clock));
    }

    /**
     * Completes the MCP lifecycle from the complete set of five deterministic
     * outcomes.  This is deliberately separate from result persistence: the
     * MCP transaction commits stages and successful results first, and only
     * then this transaction changes RUNNING to a terminal state.
     */
    @Transactional
    public AnalysisSession finishMcpSession(UUID sessionId, McpAnalysisExecutionResult execution) {
        Objects.requireNonNull(execution, "execution");
        EnumSet<McpAnalysisStage> expected = EnumSet.allOf(McpAnalysisStage.class);
        EnumSet<McpAnalysisStage> actual = EnumSet.noneOf(McpAnalysisStage.class);
        execution.stages().forEach(outcome -> actual.add(outcome.stage()));
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException("MCP execution must contain exactly the five expected stages");
        }

        long successes = execution.stages().stream()
                .filter(outcome -> outcome.status() == McpAnalysisStageResult.Status.SUCCESS)
                .count();
        int errorCount = execution.stages().size() - (int) successes;
        AnalysisStatus status = successes == expected.size()
                ? AnalysisStatus.COMPLETED
                : successes > 0 ? AnalysisStatus.PARTIAL : AnalysisStatus.FAILED;
        return finishSession(sessionId, status, errorCount, Instant.now(clock));
    }

    private AnalysisSession getSession(UUID sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis session not found: " + sessionId));
    }

    private AnalysisSession getSessionForUpdate(UUID sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        // Keep the initial lookup's not-found contract, but never continue with
        // an entity that was loaded without the pessimistic lock.
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis session not found: " + sessionId));
        return sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Analysis session could not be locked for update: " + sessionId));
    }

}
