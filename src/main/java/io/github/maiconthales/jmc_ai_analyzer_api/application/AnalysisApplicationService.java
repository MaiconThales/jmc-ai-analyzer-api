package io.github.maiconthales.jmc_ai_analyzer_api.application;

import io.github.maiconthales.jmc_ai_analyzer_api.api.AnalysisRequest;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisSession;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisSessionRepository;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Boundary service: commits the session first, then schedules the controlled workflow. */
@Service
public class AnalysisApplicationService {
    private final AnalysisSessionService sessions;
    private final McpAnalysisOrchestrator orchestrator;
    private final AnalysisSessionRepository repository;
    private final ExecutorService executor;

    public AnalysisApplicationService(AnalysisSessionService sessions,
                                      McpAnalysisOrchestrator orchestrator,
                                      AnalysisSessionRepository repository,
                                      @Qualifier("analysisExecutor") ExecutorService executor) {
        this.sessions = Objects.requireNonNull(sessions);
        this.orchestrator = Objects.requireNonNull(orchestrator);
        this.repository = Objects.requireNonNull(repository);
        this.executor = Objects.requireNonNull(executor);
    }

    public AnalysisSession createAndSchedule(AnalysisRequest request) {
        AnalysisSession session = sessions.createSession(request.fileId());
        UUID id = session.getId();
        try {
            executor.execute(() -> run(id, request));
        } catch (RuntimeException rejected) {
            // Keep the 202 contract, but never leave a rejected submission pending.
            try {
                sessions.failPendingSession(id);
            } catch (RuntimeException ignored) {
                // A concurrent worker may already have transitioned the row.
            }
        }
        return session;
    }

    private void run(UUID id, AnalysisRequest request) {
        try {
            sessions.startSession(id);
            orchestrator.executeAndPersist(id, request);
        } catch (RuntimeException failure) {
            // The orchestrator normally records stage failures. A failure before it starts
            // is kept as a terminal session state rather than escaping the worker thread.
            try {
                AnalysisSession current = repository.findById(id).orElse(null);
                if (current != null && current.getStatus() == io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus.RUNNING) {
                    sessions.finishSession(id, io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus.FAILED, 1);
                }
            } catch (RuntimeException ignored) { }
        }
    }
}
