package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.ai.AiHttpClientException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Optional-AI boundary: provider failures never abort the already completed MCP cycle. */
@Primary
@Service
public final class ResilientAiInterpretationService implements AiInterpretationService {
    private final AiInterpretationService delegate;
    private final AiClientProperties properties;
    private final AiProviderResponseValidator validator;

    public ResilientAiInterpretationService(@Qualifier("httpAiInterpretationService") AiInterpretationService delegate,
                                            AiClientProperties properties,
                                            tools.jackson.databind.ObjectMapper objectMapper) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.validator = new AiProviderResponseValidator(objectMapper);
    }

    @Override
    public AiInterpretation interpret(AnalysisResult result) {
        try {
            return validator.validate(delegate.interpret(result));
        } catch (AiHttpClientException failure) {
            return failure.kind() == AiHttpClientException.Kind.FAILED
                    ? failed() : unavailable();
        } catch (RuntimeException failure) {
            // Includes malformed JSON, schema violations and provider adapters that
            // use their own transport exception. Never leak provider details.
            return failed();
        }
    }

    private AiInterpretation unavailable() {
        return AiInterpretationFailures.unavailable(properties.getProvider(), properties.getModel(), properties.getPromptVersion());
    }

    private AiInterpretation failed() {
        return AiInterpretationFailures.failed(properties.getProvider(), properties.getModel(), properties.getPromptVersion());
    }
}
