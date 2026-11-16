package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.ai;

import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiClientProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretation;
import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretationService;
import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AnalysisResult;
import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AnalysisLimits;
import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.PromptBuilder;
import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiProviderResponseValidator;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Sends the bounded consolidated result to a configured AI HTTP endpoint. */
@Service("httpAiInterpretationService")
public class HttpAiInterpretationService implements AiInterpretationService {
    private final AiClientProperties properties;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AiProviderResponseValidator responseValidator;

    @Autowired
    public HttpAiInterpretationService(AiClientProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(timeout(properties))
                .build());
    }

    public HttpAiInterpretationService(AiClientProperties properties, ObjectMapper objectMapper,
                                       HttpClient httpClient) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.responseValidator = new AiProviderResponseValidator(objectMapper);
        this.promptBuilder = new PromptBuilder(required(properties.getPromptVersion(), "promptVersion"));
        validate(properties);
    }

    @Override
    public AiInterpretation interpret(AnalysisResult result) {
        Objects.requireNonNull(result, "result");
        if (!properties.isEnabled()) {
            return new AiInterpretation("DISABLED", properties.getProvider(), properties.getModel(),
                    promptBuilder.version(), "{}", "AI provider is disabled");
        }

        String prompt = promptBuilder.build(result);
        String requestJson = requestBody(prompt);
        // This is the transport timeout required by the B5.4 HTTP client. It is
        // deliberately not an AI availability/fallback policy (B5.8).
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl()))
                .timeout(properties.getTimeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson));
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            request.header("Authorization", "Bearer " + properties.getApiKey());
        }

        int attempts = Math.max(1, properties.getRetryMaxAttempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
          try {
            HttpResponse<InputStream> response = httpClient.send(request.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    AiHttpClientException.Kind kind = response.statusCode() >= 500
                            ? AiHttpClientException.Kind.UNAVAILABLE : AiHttpClientException.Kind.FAILED;
                    throw new AiHttpClientException("AI provider returned HTTP " + response.statusCode(), kind);
                }
                String responseBody = readLimitedUtf8(body, response.headers().firstValueAsLong("Content-Length"));
                return responseValidator.validate(responseBody, properties.getProvider(), properties.getModel(),
                        promptBuilder.version());
            }
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiHttpClientException("AI provider request interrupted", exception);
          } catch (java.io.IOException | RuntimeException exception) {
            if (exception instanceof AiHttpClientException clientException) throw clientException;
            AiHttpClientException.Kind kind = exception instanceof IllegalArgumentException
                    ? AiHttpClientException.Kind.FAILED : AiHttpClientException.Kind.UNAVAILABLE;
            if (kind != AiHttpClientException.Kind.UNAVAILABLE || attempt == attempts) {
                throw new AiHttpClientException("AI provider request failed", kind, exception);
            }
          }
        }
        throw new AiHttpClientException("AI provider request failed");
    }

    /** Reads a bounded UTF-8 response without ever materializing an oversized body. */
    private static String readLimitedUtf8(InputStream input, java.util.OptionalLong contentLength)
            throws java.io.IOException {
        if (contentLength.isPresent() && contentLength.getAsLong() > AnalysisLimits.MAX_PROVIDER_PAYLOAD) {
            throw new AiHttpClientException("AI provider response exceeds its limit");
        }

        int limit = AnalysisLimits.MAX_PROVIDER_PAYLOAD;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.min(limit, 8_192));
        byte[] buffer = new byte[8_192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read > limit - total) {
                throw new AiHttpClientException("AI provider response exceeds its limit");
            }
            bytes.write(buffer, 0, read);
            total += read;
        }

        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()));
            return decoded.toString();
        } catch (CharacterCodingException exception) {
            throw new AiHttpClientException("AI provider response is not valid UTF-8", exception);
        }
    }

    private String requestBody(String prompt) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "provider", properties.getProvider(),
                    "model", properties.getModel(),
                    "promptVersion", promptBuilder.version(),
                    "messages", List.of(Map.of("role", "user", "content", prompt))));
        } catch (Exception exception) {
            throw new AiHttpClientException("Could not build AI provider request", exception);
        }
    }

    private static Duration timeout(AiClientProperties properties) {
        return properties.getTimeout() == null ? Duration.ofSeconds(30) : properties.getTimeout();
    }

    private static void validate(AiClientProperties properties) {
        required(properties.getProvider(), "provider");
        required(properties.getModel(), "model");
        required(properties.getBaseUrl(), "baseUrl");
        if (properties.getTimeout() == null || properties.getTimeout().isZero() || properties.getTimeout().isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be non-blank");
        return value;
    }
}
