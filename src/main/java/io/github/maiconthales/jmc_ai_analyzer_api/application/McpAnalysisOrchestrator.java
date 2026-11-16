package io.github.maiconthales.jmc_ai_analyzer_api.application;

import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp.McpAnalysisToolAdapter;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.JfrFileCatalogService;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisSessionRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.dto.JfrFileResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.McpAnalysisAiCoordinator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import io.github.maiconthales.jmc_ai_analyzer_api.api.AnalysisRequest;

/**
 * Creates the fixed allow-listed set of MCP tasks and delegates bounded execution.
 * Defaults are internal analysis parameters, not HTTP input.
 */
@Service
public final class McpAnalysisOrchestrator {
    private final McpAnalysisToolAdapter adapter;
    private final McpAnalysisExecutor executor;
    private final McpAnalysisResultPersistenceService resultPersistence;
    private final AnalysisSessionService sessionService;
    private final AnalysisSessionRepository sessionRepository;
    private final JfrFileCatalogService fileCatalog;
    private final McpAnalysisAiCoordinator aiCoordinator;

    public McpAnalysisOrchestrator(McpAnalysisToolAdapter adapter, McpAnalysisExecutor executor,
                                   McpAnalysisResultPersistenceService resultPersistence,
                                   AnalysisSessionService sessionService,
                                   AnalysisSessionRepository sessionRepository,
                                   JfrFileCatalogService fileCatalog,
                                   McpAnalysisAiCoordinator aiCoordinator) {
        this.adapter = adapter;
        this.executor = executor;
        this.resultPersistence = Objects.requireNonNull(resultPersistence, "resultPersistence");
        this.sessionService = Objects.requireNonNull(sessionService, "sessionService");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository");
        this.fileCatalog = Objects.requireNonNull(fileCatalog, "fileCatalog");
        this.aiCoordinator = Objects.requireNonNull(aiCoordinator, "aiCoordinator");
    }

    public McpAnalysisExecutionResult execute(String fileId) {
        return execute(fileId, null);
    }

    public McpAnalysisExecutionResult execute(String fileId, AnalysisRequest request) {
        double minScore = request == null ? 50.0 : request.effectiveMinScore();
        int topN = request == null ? 10 : request.effectiveTopN();
        int topClasses = request == null ? 10 : request.effectiveTopClasses();
        long minDurationMs = request == null ? 50L : request.effectiveMinDurationMs();
        return executor.execute(List.of(
                new McpAnalysisTask(McpAnalysisStage.AUTOMATED_RULES,
                        () -> adapter.analyzeJfrAutomatedRulesAsync(fileId, minScore)),
                new McpAnalysisTask(McpAnalysisStage.CPU_HOT_METHODS,
                        () -> adapter.getCpuHotMethodsAsync(fileId, topN)),
                new McpAnalysisTask(McpAnalysisStage.GC_AND_MEMORY,
                        () -> adapter.analyzeGcAndMemoryAsync(fileId, topClasses)),
                new McpAnalysisTask(McpAnalysisStage.LOCK_CONTENTIONS,
                        () -> adapter.findLockContentionsAsync(fileId, minDurationMs)),
                new McpAnalysisTask(McpAnalysisStage.FREQUENT_EXCEPTIONS,
                        () -> adapter.getFrequentExceptionsAsync(fileId, topN))));
    }

    /** Derives the controlled file id from the persisted session, then executes and persists. */
    public McpAnalysisExecutionResult executeAndPersist(UUID sessionId) {
        return executeAndPersist(sessionId, null);
    }

    public McpAnalysisExecutionResult executeAndPersist(UUID sessionId, AnalysisRequest request) {
        Objects.requireNonNull(sessionId, "sessionId");
        var session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis session not found: " + sessionId));
        if (session.getStatus() != AnalysisStatus.RUNNING) {
            throw new IllegalStateException("MCP execution requires a RUNNING analysis session");
        }
        String fileId = session.getFileId();
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalStateException("Analysis session has no persisted controlled file id");
        }
        JfrFileResponse current = fileCatalog.metadata(fileId);
        if (current.size() != session.getFileSizeBytes()
                || !Objects.equals(current.modifiedAt(), session.getFileModifiedAt())) {
            throw new IllegalStateException("The session file metadata no longer matches the catalog");
        }
        McpAnalysisExecutionResult execution = execute(fileId, request);
        resultPersistence.persistSuccessfulResults(sessionId, execution);
        // The persistence transaction has committed before this lifecycle
        // transaction runs. A terminal transition can therefore never erase
        // valid MCP rows if it is rejected by a concurrent state change.
        sessionService.finishMcpSession(sessionId, execution);
        // The MCP lifecycle is complete before the provider-neutral hand-off.
        // Only the bounded execution crosses this boundary.
        try {
            aiCoordinator.interpretAfterMcp(sessionId, execution);
        } catch (RuntimeException aiFailure) {
            // The coordinator normally converts provider and persistence
            // failures to an interpretation. Keep this final guard as well:
            // a replacement/misconfigured coordinator must not turn a
            // committed MCP lifecycle into a failed request.
        }
        return execution;
    }
}
