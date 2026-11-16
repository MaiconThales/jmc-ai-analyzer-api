package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisExecutionResult;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisStage;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisStageResult;
import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretationPersistenceService;
import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiClientProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretation;
import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AnalysisResult;
import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.McpAnalysisAiCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HttpAiInterpretationServiceTest {
    private HttpServer server;
    private AtomicReference<String> requestBody;
    private AtomicReference<String> authorization;
    private AtomicReference<String> responseBody;
    private AtomicInteger responseStatus;
    private AtomicLong responseDelayMillis;

    @BeforeEach
    void startServer() throws IOException {
        requestBody = new AtomicReference<>();
        authorization = new AtomicReference<>();
        responseBody = new AtomicReference<>("{\"summary\":\"ok\",\"overallSeverity\":\"LOW\"," 
                + "\"findings\":[],\"recommendations\":[],\"possibleCauses\":[],\"status\":\"COMPLETED\"}");
        responseStatus = new AtomicInteger(200);
        responseDelayMillis = new AtomicLong();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/interpret", this::respond);
        server.start();
    }

    @AfterEach
    void stopServer() { server.stop(0); }

    @Test
    void sendsConfiguredProviderModelStructuredResultAndEnvironmentCredential() throws Exception {
        String secret = "environment-secret";
        AiClientProperties properties = properties(true, "ollama", "model-from-env", secret);
        AiInterpretation response = service(properties).interpret(emptyResult());

        assertEquals("COMPLETED", response.status());
        assertEquals("ollama", response.provider());
        assertEquals("model-from-env", response.model());
        assertTrue(response.payload().contains("\"summary\":\"ok\""));
        assertEquals("Bearer " + secret, authorization.get());
        assertTrue(requestBody.get().contains("\"provider\":\"ollama\""));
        assertTrue(requestBody.get().contains("\"model\":\"model-from-env\""));
        assertTrue(requestBody.get().contains("\"promptVersion\""));
        assertTrue(requestBody.get().contains("messages"));
    }

    @Test
    void disabledDoesNotCallConfiguredEndpoint() {
        AiClientProperties properties = properties(false, "never-called", "unused", "must-not-be-sent");
        AiInterpretation response = service(properties).interpret(emptyResult());

        assertEquals("DISABLED", response.status());
        assertEquals("{}", response.payload());
        assertEquals(null, requestBody.get());
        assertEquals(null, authorization.get());
    }

    @Test
    void classifiesInvalidResponseAsFailed() {
        responseBody.set("not-json");

        AiHttpClientException error = org.junit.jupiter.api.Assertions.assertThrows(
                AiHttpClientException.class, () -> service(properties(true, "test", "model", null))
                        .interpret(emptyResult()));

        assertEquals(AiHttpClientException.Kind.FAILED, error.kind());
    }

    @Test
    void classifiesClientErrorAsFailed() {
        responseStatus.set(400);

        AiHttpClientException error = org.junit.jupiter.api.Assertions.assertThrows(
                AiHttpClientException.class, () -> service(properties(true, "test", "model", null))
                        .interpret(emptyResult()));

        assertEquals(AiHttpClientException.Kind.FAILED, error.kind());
    }

    @Test
    void classifiesServerErrorAsUnavailable() {
        responseStatus.set(503);

        AiHttpClientException error = org.junit.jupiter.api.Assertions.assertThrows(
                AiHttpClientException.class, () -> service(properties(true, "test", "model", null))
                        .interpret(emptyResult()));

        assertEquals(AiHttpClientException.Kind.UNAVAILABLE, error.kind());
    }

    @Test
    void classifiesTimeoutAsUnavailable() {
        responseDelayMillis.set(500);
        AiClientProperties properties = properties(true, "test", "model", null);
        properties.setTimeout(Duration.ofMillis(50));

        AiHttpClientException error = org.junit.jupiter.api.Assertions.assertThrows(
                AiHttpClientException.class, () -> service(properties).interpret(emptyResult()));

        assertEquals(AiHttpClientException.Kind.UNAVAILABLE, error.kind());
    }

    @Test
    void classifiesConnectionFailureAsUnavailable() {
        AiClientProperties properties = properties(true, "test", "model", null);
        properties.setBaseUrl("http://localhost:1/unreachable");

        AiHttpClientException error = org.junit.jupiter.api.Assertions.assertThrows(
                AiHttpClientException.class, () -> service(properties).interpret(emptyResult()));

        assertEquals(AiHttpClientException.Kind.UNAVAILABLE, error.kind());
    }

    @Test
    void coordinatorMapsHttpOutcomesAndPreservesMcpExecution() {
        responseBody.set("not-json");
        assertCoordinatorStatus("FAILED", properties(true, "test", "model", null));

        responseBody.set("{\"summary\":\"ignored\"}");
        responseStatus.set(400);
        assertCoordinatorStatus("FAILED", properties(true, "test", "model", null));

        responseStatus.set(503);
        assertCoordinatorStatus("UNAVAILABLE", properties(true, "test", "model", null));

        responseStatus.set(200);
        responseDelayMillis.set(500);
        AiClientProperties timeoutProperties = properties(true, "test", "model", null);
        timeoutProperties.setTimeout(Duration.ofMillis(50));
        assertCoordinatorStatus("UNAVAILABLE", timeoutProperties);
        responseDelayMillis.set(0);

        AiClientProperties connectionProperties = properties(true, "test", "model", null);
        connectionProperties.setBaseUrl("http://localhost:1/unreachable");
        assertCoordinatorStatus("UNAVAILABLE", connectionProperties);

        assertCoordinatorStatus("DISABLED", properties(false, "test", "model", null));
    }

    @Test
    void providerSecretIsNotIncludedInReturnedFailureMessage() {
        AiClientProperties properties = properties(true, "test", "model", "top-secret");
        properties.setBaseUrl("http://localhost:1/unreachable");

        AiHttpClientException error = org.junit.jupiter.api.Assertions.assertThrows(
                AiHttpClientException.class, () -> service(properties).interpret(emptyResult()));

        assertFalse(error.getMessage().contains("top-secret"));
        assertFalse(error.toString().contains("top-secret"));
    }

    @Test
    void rejectsOversizedChunkedResponseBeforeMaterializingIt() {
        AiClientProperties properties = properties(true, "test", "model", null);
        responseBody.set("x".repeat(io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AnalysisLimits
                .MAX_PROVIDER_PAYLOAD + 1));

        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(
                AiHttpClientException.class, () -> service(properties).interpret(emptyResult()))
                .getMessage().contains("exceeds"));
    }

    private HttpAiInterpretationService service(AiClientProperties properties) {
        return new HttpAiInterpretationService(properties, new ObjectMapper(), HttpClient.newHttpClient());
    }

    private AiClientProperties properties(boolean enabled, String provider, String model, String apiKey) {
        AiClientProperties properties = new AiClientProperties();
        properties.setEnabled(enabled);
        properties.setProvider(provider);
        properties.setModel(model);
        properties.setApiKey(apiKey); // value supplied by AI_API_KEY in deployment secrets
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort() + "/interpret");
        properties.setTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private AnalysisResult emptyResult() {
        return AnalysisResult.fromMcp(new McpAnalysisExecutionResult(List.of()));
    }

    private void assertCoordinatorStatus(String expected, AiClientProperties properties) {
        AiInterpretationPersistenceService persistence = mock(AiInterpretationPersistenceService.class);
        UUID analysisId = UUID.randomUUID();
        McpAnalysisExecutionResult execution = new McpAnalysisExecutionResult(List.of(
                McpAnalysisStageResult.success(McpAnalysisStage.CPU_HOT_METHODS,
                        new ObjectMapper().readTree("[{\"method\":\"preserved.method\"}]"))));

        AiInterpretation result = new McpAnalysisAiCoordinator(
                service(properties), persistence).interpretAfterMcp(analysisId, execution);

        assertEquals(expected, result.status());
        assertEquals("preserved.method", execution.stages().getFirst().payload().get(0).get("method").asText());
        verify(persistence).persist(eq(analysisId), argThat(value -> expected.equals(value.status())));
    }

    private void respond(HttpExchange exchange) throws IOException {
        try {
            Thread.sleep(responseDelayMillis.get());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus.get(), 0);
        try (var output = exchange.getResponseBody()) { output.write(response); }
    }
}
