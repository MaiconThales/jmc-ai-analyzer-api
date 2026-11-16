package io.github.maiconthales.jmc_ai_analyzer_api.application;

import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisResult;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisSession;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStage;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisResultRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisSessionRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisStageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;

/** Persists only the structured successful output of an MCP execution. */
@Service
public class McpAnalysisResultPersistenceService {
    private final AnalysisSessionRepository sessionRepository;
    private final AnalysisResultRepository resultRepository;
    private final AnalysisStageRepository stageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean h2;

    public McpAnalysisResultPersistenceService(AnalysisSessionRepository sessionRepository,
                                               AnalysisResultRepository resultRepository,
                                               AnalysisStageRepository stageRepository) {
        this(sessionRepository, resultRepository, stageRepository, "");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public McpAnalysisResultPersistenceService(AnalysisSessionRepository sessionRepository,
                                               AnalysisResultRepository resultRepository,
                                               AnalysisStageRepository stageRepository,
                                               @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository");
        this.resultRepository = Objects.requireNonNull(resultRepository, "resultRepository");
        this.stageRepository = Objects.requireNonNull(stageRepository, "stageRepository");
        this.h2 = datasourceUrl.toLowerCase().contains(":h2:");
    }

    /**
     * Adds successful stage payloads to the existing session.  This method is
     * intentionally called after MCP execution has completed: it neither runs
     * MCP nor changes/removes rows already stored for the session.
     */
    @Transactional
    public List<AnalysisResult> persistSuccessfulResults(UUID sessionId,
                                                         McpAnalysisExecutionResult execution) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(execution, "execution");
        // Hold the session row lock through validation and every result insert.
        // A concurrent finish therefore cannot pass its RUNNING check between
        // this check and the inserts (and a finish that wins is observed here).
        AnalysisSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis session not found: " + sessionId));
        session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis session not found: " + sessionId));
        if (session.getStatus() != io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus.RUNNING) {
            throw new IllegalStateException("MCP results can only be persisted for a RUNNING analysis session");
        }

        EnumSet<McpAnalysisStage> expected = EnumSet.allOf(McpAnalysisStage.class);
        EnumSet<McpAnalysisStage> actual = EnumSet.noneOf(McpAnalysisStage.class);
        execution.stages().forEach(stageResult -> actual.add(stageResult.stage()));
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(
                    "MCP execution must contain exactly the five expected stages");
        }

        // Validate the complete batch before changing the managed session. This
        // prevents a malformed later stage from leaving an earlier success
        // attached when this method is called outside a transaction proxy.
        for (McpAnalysisStageResult stageResult : execution.stages()) {
            if (stageResult.status() != McpAnalysisStageResult.Status.SUCCESS) {
                continue;
            }
            if (stageResult.payload() == null
                    || (!stageResult.payload().isObject() && !stageResult.payload().isArray())) {
                throw new IllegalArgumentException(
                        "Successful MCP stage must have an object or array payload: " + stageResult.stage());
            }
        }

        persistStages(session, execution);

        List<AnalysisResult> newResults = new ArrayList<>();
        for (McpAnalysisStageResult stageResult : execution.stages()) {
            if (stageResult.status() != McpAnalysisStageResult.Status.SUCCESS) {
                continue;
            }
            UUID resultId = UUID.randomUUID();
            String payload = objectMapper.writeValueAsString(stageResult.payload());
            int inserted;
            if (h2) {
                inserted = resultRepository.insertIfAbsentH2(resultId, sessionId,
                        stageResult.stage().resultType(), payload);
            } else {
                inserted = resultRepository.insertIfAbsentPostgres(resultId, sessionId,
                        stageResult.stage().resultType(), payload);
            }
            if (inserted == 1) {
                resultRepository.findById(resultId).ifPresent(newResults::add);
            }
        }
        // saveAll inserts only this execution's successes; existing valid rows
        // are deliberately not replaced or deleted.
        resultRepository.flush();
        return List.copyOf(newResults);
    }

    private void persistStages(AnalysisSession session, McpAnalysisExecutionResult execution) {
        Instant startedAt = Instant.now();
        for (McpAnalysisStageResult result : execution.stages()) {
            String stageName = result.stage().resultType();
            AnalysisStage stage = stageRepository.findByAnalysisIdAndStageName(session.getId(), stageName)
                    .orElseGet(() -> {
                        AnalysisStage created = new AnalysisStage(stageName);
                        session.addStage(created);
                        return created;
                    });
            // A replay must not replace a previously recorded terminal stage.
            if (stage.getStatus() != AnalysisStatus.PENDING) {
                continue;
            }
            stage.start(startedAt);
            if (result.status() != McpAnalysisStageResult.Status.SUCCESS) {
                stage.recordError(errorCode(result.status()), safeErrorMessage(result));
            }
            stage.finish(toStageStatus(result.status()), Instant.now());
            stageRepository.save(stage);
        }
        stageRepository.flush();
    }

    private static AnalysisStatus toStageStatus(McpAnalysisStageResult.Status status) {
        return switch (status) {
            case SUCCESS -> AnalysisStatus.SUCCESS;
            case FAILED -> AnalysisStatus.FAILED;
            case TIMEOUT -> AnalysisStatus.TIMEOUT;
            case OVERLOADED -> AnalysisStatus.OVERLOADED;
        };
    }

    private static String errorCode(McpAnalysisStageResult.Status status) {
        return "MCP_" + status.name();
    }

    private static String safeErrorMessage(McpAnalysisStageResult result) {
        // Never persist the peer's exception text. MCP errors can contain JSON,
        // paths, credentials, tokens, or implementation details, and sanitizing
        // every possible representation is less safe than exposing a category.
        // The detailed cause remains an internal diagnostic only.
        return switch (result.status()) {
            case TIMEOUT -> "MCP stage timed out.";
            case OVERLOADED -> "MCP stage was overloaded.";
            default -> "MCP stage failed.";
        };
    }
}
