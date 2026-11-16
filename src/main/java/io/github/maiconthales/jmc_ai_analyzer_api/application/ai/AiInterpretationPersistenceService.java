package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AiInterpretationStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisSession;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AiInterpretationRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;
import java.util.UUID;

/** Persists only the already validated, bounded provider contract. */
@Service
public class AiInterpretationPersistenceService {
    private final AiInterpretationRepository repository;
    private final AnalysisSessionRepository sessions;
    private final AiProviderResponseValidator validator;
    private final ObjectMapper mapper;

    @Autowired
    public AiInterpretationPersistenceService(AiInterpretationRepository repository,
                                              AnalysisSessionRepository sessions) {
        this(repository, sessions, new AiProviderResponseValidator(new ObjectMapper()), new ObjectMapper());
    }

    AiInterpretationPersistenceService(AiInterpretationRepository repository, AnalysisSessionRepository sessions,
                                       AiProviderResponseValidator validator, ObjectMapper mapper) {
        this.repository = repository;
        this.sessions = sessions;
        this.validator = validator;
        this.mapper = mapper;
    }

    /**
     * AI is an optional post-processing concern.  Always use a transaction of
     * its own so a provider/persistence failure can only roll back the AI row;
     * it must never mark the transaction which committed MCP stages/results as
     * rollback-only.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AiInterpretation persist(
            UUID analysisId, AiInterpretation candidate) {
        // Revalidation here is intentional: this is the last boundary before durable storage.
        AiInterpretation value = validator.validate(candidate);
        AnalysisSession analysis = sessions.findByIdForUpdate(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis session not found: " + analysisId));
        JsonNode payload = mapper.readTree(value.payload());
        String status = value.status().toUpperCase(Locale.ROOT);
        // V4 has no DISABLED/PARTIAL values. DISABLED is the explicit unavailable policy;
        // PARTIAL is retained as a failed provider attempt rather than violating the check.
        AiInterpretationStatus storedStatus = switch (status) {
            case "DISABLED", "UNAVAILABLE" -> AiInterpretationStatus.UNAVAILABLE;
            case "PARTIAL", "FAILED" -> AiInterpretationStatus.FAILED;
            case "COMPLETED" -> AiInterpretationStatus.COMPLETED;
            default -> throw new IllegalArgumentException("Unsupported AI status for persistence: " + status);
        };
        String severity = payload.path("overallSeverity").isTextual()
                ? payload.path("overallSeverity").asText() : null;
        String summary = value.summary();
        return repository.save(new io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AiInterpretation(
                analysis, storedStatus, value.provider(), value.model(), value.promptVersion(),
                summary, severity, payload, null, null));
    }
}
