package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisExecutionResult;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.ai.AiHttpClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/**
 * Coordinates the post-MCP hand-off to an AI provider.
 *
 * <p>The input is deliberately the completed, structured MCP execution rather
 * than a file identifier.  Consequently this service cannot resolve a file,
 * select an MCP tool, invoke MCP, or expose the JFR recording to a provider.</p>
 */
@Service
public final class McpAnalysisAiCoordinator {
    private final AiInterpretationService aiInterpretationService;
    private final PromptBuilder promptBuilder;
    private final AiProviderResponseValidator responseValidator;
    private final AiInterpretationPersistenceService persistenceService;

    @Autowired
    public McpAnalysisAiCoordinator(AiInterpretationService aiInterpretationService,
                                    AiInterpretationPersistenceService persistenceService) {
        this(aiInterpretationService, new PromptBuilder(), new AiProviderResponseValidator(new ObjectMapper()), persistenceService);
    }

    McpAnalysisAiCoordinator(AiInterpretationService aiInterpretationService,
                             PromptBuilder promptBuilder) {
        this(aiInterpretationService, promptBuilder, new AiProviderResponseValidator(new ObjectMapper()), null);
    }

    McpAnalysisAiCoordinator(AiInterpretationService aiInterpretationService,
                             PromptBuilder promptBuilder, AiProviderResponseValidator responseValidator) {
        this(aiInterpretationService, promptBuilder, responseValidator, null);
    }

    McpAnalysisAiCoordinator(AiInterpretationService aiInterpretationService,
                             PromptBuilder promptBuilder, AiProviderResponseValidator responseValidator,
                             AiInterpretationPersistenceService persistenceService) {
        this.aiInterpretationService = Objects.requireNonNull(
                aiInterpretationService, "aiInterpretationService");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
        this.responseValidator = Objects.requireNonNull(responseValidator, "responseValidator");
        this.persistenceService = persistenceService;
    }

    /**
     * Builds the bounded provider-neutral contract only after MCP has returned,
     * then sends that contract through the provider-neutral AI seam.
     */
    public AiInterpretation interpretAfterMcp(McpAnalysisExecutionResult execution) {
        return interpretAfterMcp(null, execution);
    }

    /** Post-MCP interpretation with durable storage tied to the existing analysis session. */
    public AiInterpretation interpretAfterMcp(UUID analysisId, McpAnalysisExecutionResult execution) {
        Objects.requireNonNull(execution, "execution");
        AnalysisResult consolidated = AnalysisResult.fromMcp(execution);

        AiInterpretation interpretation;
        try {
            // Build before invoking the provider so the same bounded prompt
            // contract is checked at the application boundary as well. The HTTP
            // adapter builds the request body from this same AnalysisResult.
            promptBuilder.build(consolidated);
            interpretation = responseValidator.validate(aiInterpretationService.interpret(consolidated));
        } catch (AiHttpClientException failure) {
            // Preserve the provider client's availability classification.  A
            // timeout, transport failure, or 5xx is not equivalent to an
            // invalid response or a provider-side 4xx failure.
            interpretation = failure.kind() == AiHttpClientException.Kind.UNAVAILABLE
                    ? AiInterpretationFailures.unavailable("provider-neutral", "unknown", promptBuilder.version())
                    : AiInterpretationFailures.failed("provider-neutral", "unknown", promptBuilder.version());
        } catch (RuntimeException failure) {
            // The MCP result is authoritative. AI is an optional post-processing
            // step, so an unavailable/malformed adapter must become data, not a
            // failed MCP cycle.
            interpretation = AiInterpretationFailures.failed("provider-neutral", "unknown", promptBuilder.version());
        }
        persistWithoutAffectingMcp(analysisId, interpretation);
        return interpretation;
    }

    /**
     * Persistence is deliberately outside the MCP lifecycle.  If a valid AI
     * answer cannot be stored, make a best-effort attempt to leave an explicit
     * FAILED/UNAVAILABLE interpretation, but never propagate the failure back
     * into the caller which owns the already committed MCP data.
     */
    private void persistWithoutAffectingMcp(UUID analysisId, AiInterpretation interpretation) {
        if (analysisId == null || persistenceService == null) {
            return;
        }
        try {
            persistenceService.persist(analysisId, interpretation);
        } catch (RuntimeException persistenceFailure) {
            AiInterpretation fallback = "UNAVAILABLE".equals(interpretation.status())
                    ? AiInterpretationFailures.unavailable("provider-neutral", "unknown", promptBuilder.version())
                    : AiInterpretationFailures.failed("provider-neutral", "unknown", promptBuilder.version());
            try {
                persistenceService.persist(analysisId, fallback);
            } catch (RuntimeException ignored) {
                // The AI store may itself be unavailable. MCP remains intact.
            }
        }
    }

    /** Short application-service alias for callers that already completed MCP. */
    public AiInterpretation interpret(McpAnalysisExecutionResult execution) {
        return interpretAfterMcp(execution);
    }
}
