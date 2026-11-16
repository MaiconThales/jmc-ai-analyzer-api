package io.github.maiconthales.jmc_ai_analyzer_api;

import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretationPersistenceService;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisResult;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisSession;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStage;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisExecutionResult;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisResultPersistenceService;
import io.github.maiconthales.jmc_ai_analyzer_api.application.AnalysisSessionService;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisStage;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisStageResult;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisResultRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisSessionRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisStageRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AiInterpretationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Timestamp;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest
@Testcontainers
class AnalysisPersistencePostgresIntegrationTest {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jmc_ai_analyzer_test")
            .withUsername(randomCredential("tc_user_", 12))
            .withPassword(randomCredential("tc_password_", 32));

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private AnalysisSessionRepository sessionRepository;

    @Autowired
    private AnalysisStageRepository stageRepository;

    @Autowired
    private AnalysisResultRepository resultRepository;

    @Autowired
    private AiInterpretationRepository aiInterpretationRepository;

    @MockitoSpyBean
    private AiInterpretationRepository aiInterpretationRepositorySpy;

    @Autowired
    private AiInterpretationPersistenceService aiInterpretationPersistence;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private McpAnalysisResultPersistenceService resultPersistence;

    @Autowired
    private AnalysisSessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void appliesAiInterpretationSchemaWithJsonbAndProtectedForeignKey() {
        assertEquals(7, flyway.info().applied().length);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = 'analysis_session' AND column_name = 'file_id'",
                Integer.class));
        assertEquals("jsonb", jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'ai_interpretation' "
                        + "AND column_name = 'payload'",
                String.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'fk_ai_interpretation_session'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_ai_interpretation_analysis_created'",
                Integer.class));
    }

    @Test
    void acceptsUnavailableAiInterpretationStatus() {
        UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        UUID interpretationId = UUID.fromString("00000000-0000-0000-0000-000000000104");
        Instant createdAt = Instant.parse("2026-11-16T12:03:00Z");
        insertSession(sessionId, createdAt);

        jdbcTemplate.update("INSERT INTO ai_interpretation "
                        + "(id, analysis_id, status, provider, model, prompt_version, payload) "
                        + "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB))",
                interpretationId, sessionId, "UNAVAILABLE", "test-provider", "test-model", "v1", "{}");

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_interpretation WHERE id = ? AND status = 'UNAVAILABLE'",
                Integer.class, interpretationId));
    }

    @Test
    void persistsAiInterpretationJsonbMetadataAndMapsDisabledAndPartialStatuses() {
        AnalysisSession session = sessionRepository.saveAndFlush(
                new AnalysisSession("ai-roundtrip.jfr", 4096L, Instant.parse("2026-11-16T12:20:00Z")));

        var completed = aiInterpretationPersistence.persist(session.getId(),
                new io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretation("COMPLETED", "provider-pg", "model-pg", "prompt-v5",
                        "{\"summary\":\"ok\",\"overallSeverity\":\"LOW\","
                                + "\"findings\":[],\"recommendations\":[],\"possibleCauses\":[],"
                                + "\"status\":\"COMPLETED\"}", "ok"));
        var disabled = aiInterpretationPersistence.persist(session.getId(),
                new io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretation("DISABLED", "provider-pg", "model-pg", "prompt-v5", "{}",
                        "AI provider is disabled"));
        var partial = aiInterpretationPersistence.persist(session.getId(),
                new io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretation("PARTIAL", "provider-pg", "model-pg", "prompt-v5",
                        "{\"summary\":\"partial\",\"overallSeverity\":\"MEDIUM\","
                                + "\"findings\":[],\"recommendations\":[],\"possibleCauses\":[],"
                                + "\"status\":\"PARTIAL\"}", "partial"));

        var stored = aiInterpretationRepository.findAllByAnalysis_IdOrderByCreatedAtDescIdDesc(session.getId());
        assertEquals(3, stored.size());
        var completedStored = stored.stream().filter(value -> value.getId().equals(completed.getId())).findFirst().orElseThrow();
        assertEquals("COMPLETED", completedStored.getStatus().name());
        assertEquals("provider-pg", completedStored.getProvider());
        assertEquals("model-pg", completedStored.getModel());
        assertEquals("prompt-v5", completedStored.getPromptVersion());
        assertEquals("ok", completedStored.getPayload().path("summary").asText());
        assertEquals("UNAVAILABLE", stored.stream().filter(value -> value.getId().equals(disabled.getId()))
                .findFirst().orElseThrow().getStatus().name());
        assertEquals("FAILED", stored.stream().filter(value -> value.getId().equals(partial.getId()))
                .findFirst().orElseThrow().getStatus().name());
        assertEquals("jsonb", jdbcTemplate.queryForObject(
                "SELECT pg_typeof(payload)::text FROM ai_interpretation WHERE id = ?",
                String.class, completed.getId()));
    }

    @Test
    void rejectsInvalidAiInterpretationWithoutPersistingAndKeepsMcpResult() {
        AnalysisSession session = sessionRepository.saveAndFlush(
                new AnalysisSession("ai-invalid.jfr", 4096L, Instant.now()));
        ObjectNode mcpPayload = objectMapper.createObjectNode();
        mcpPayload.put("preserve", true);
        AnalysisResult mcpResult = new AnalysisResult("cpu_hot_methods", mcpPayload);
        session.addResult(mcpResult);
        resultRepository.saveAndFlush(mcpResult);

        assertThrows(IllegalArgumentException.class, () -> aiInterpretationPersistence.persist(session.getId(),
                new io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretation("COMPLETED", "provider-pg", "model-pg", "prompt-v5", "{}", "bad")));

        assertEquals(0, aiInterpretationRepository.findAllByAnalysis_IdOrderByCreatedAtDescIdDesc(session.getId()).size());
        assertEquals(1, resultRepository.findAllByAnalysisIdOrderByIdAsc(session.getId()).size());
    }

    @Test
    void rollsBackAiInterpretationWhenRepositoryFailsAfterInsert() {
        AnalysisSession session = sessionRepository.saveAndFlush(
                new AnalysisSession("ai-rollback.jfr", 4096L, Instant.now()));
        doAnswer(invocation -> {
            entityManager.persist(invocation.getArgument(0));
            entityManager.flush();
            throw new IllegalStateException("forced AI flush failure");
        }).when(aiInterpretationRepositorySpy).save(org.mockito.ArgumentMatchers.any());

        try {
            assertThrows(IllegalStateException.class, () -> aiInterpretationPersistence.persist(session.getId(),
                    new io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretation("COMPLETED", "provider-pg", "model-pg", "prompt-v5",
                            "{\"summary\":\"ok\",\"overallSeverity\":\"LOW\",\"findings\":[],"
                                    + "\"recommendations\":[],\"possibleCauses\":[],\"status\":\"COMPLETED\"}", "ok")));
        } finally {
            reset(aiInterpretationRepositorySpy);
        }
        assertEquals(0, aiInterpretationRepository.findAllByAnalysis_IdOrderByCreatedAtDescIdDesc(session.getId()).size());
    }

    @Test
    void persistsAndReadsAggregateWithJsonbStablePaginationAndProtectedForeignKeys() {
        Instant now = Instant.parse("2026-11-16T12:00:00Z");
        AnalysisSession session = sessionRepository.saveAndFlush(
                new AnalysisSession("integration.jfr", 4096L, now));

        AnalysisStage stage = new AnalysisStage("thread-dump");
        session.addStage(stage);
        stageRepository.saveAndFlush(stage);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("threadCount", 12);
        payload.putArray("warnings").add("blocked");
        AnalysisResult result = new AnalysisResult("thread-summary", payload);
        session.addResult(result);
        resultRepository.saveAndFlush(result);

        AnalysisSession readSession = sessionRepository.findById(session.getId()).orElseThrow();
        AnalysisStage readStage = stageRepository.findById(stage.getId()).orElseThrow();
        AnalysisResult readResult = resultRepository.findById(result.getId()).orElseThrow();

        assertEquals("integration.jfr", readSession.getFileName());
        assertEquals(session.getId(), readStage.getAnalysis().getId());
        assertEquals("thread-summary", readResult.getResultType());
        assertEquals(payload, readResult.getPayload());
        assertEquals("jsonb", jdbcTemplate.queryForObject(
                "SELECT pg_typeof(payload)::text FROM analysis_result WHERE id = ?",
                String.class, result.getId()));

        UUID olderId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID newerId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        Instant tiedCreatedAt = Instant.parse("2099-11-16T12:00:00Z");
        insertSession(olderId, tiedCreatedAt);
        insertSession(newerId, tiedCreatedAt);

        Page<AnalysisSession> page = sessionRepository.findHistory(PageRequest.of(0, 2));

        assertEquals(2, page.getContent().size());
        assertEquals(newerId, page.getContent().get(0).getId());
        assertEquals(olderId, page.getContent().get(1).getId());
        assertTrue(page.hasNext());

        UUID protectedSessionId = session.getId();
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM analysis_session WHERE id = ?", protectedSessionId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM analysis_stage WHERE analysis_id = ?", Integer.class, protectedSessionId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM analysis_result WHERE analysis_id = ?", Integer.class, protectedSessionId));
        assertNotNull(sessionRepository.findById(protectedSessionId).orElse(null));
    }

    @Test
    void persistsPostgresStageErrorsAndKeepsSuccessfulResult() {
        Instant now = Instant.parse("2026-11-16T12:10:00Z");
        AnalysisSession session = new AnalysisSession("postgres-stage.jfr", 1L, now);
        session.start(now);
        session = sessionRepository.saveAndFlush(session);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ok", true);

        McpAnalysisExecutionResult execution = new McpAnalysisExecutionResult(List.of(
                McpAnalysisStageResult.success(McpAnalysisStage.CPU_HOT_METHODS, payload),
                McpAnalysisStageResult.failed(McpAnalysisStage.AUTOMATED_RULES,
                        new IllegalStateException("token=hidden /srv/private.jfr")),
                McpAnalysisStageResult.timeout(McpAnalysisStage.GC_AND_MEMORY, new java.util.concurrent.TimeoutException()),
                McpAnalysisStageResult.overloaded(McpAnalysisStage.LOCK_CONTENTIONS, new IllegalStateException()),
                McpAnalysisStageResult.failed(McpAnalysisStage.FREQUENT_EXCEPTIONS, new IllegalStateException())));

        resultPersistence.persistSuccessfulResults(session.getId(), execution);
        sessionService.finishMcpSession(session.getId(), execution);

        var stages = stageRepository.findAllByAnalysisIdOrderByIdAsc(session.getId());
        assertEquals(5, stages.size());
        assertEquals(AnalysisStatus.SUCCESS, stages.stream()
                .filter(s -> "cpu_hot_methods".equals(s.getStageName())).findFirst().orElseThrow().getStatus());
        var failed = stages.stream().filter(s -> "automated_rules".equals(s.getStageName()))
                .findFirst().orElseThrow();
        assertEquals(AnalysisStatus.FAILED, failed.getStatus());
        assertEquals("MCP_FAILED", failed.getErrorCode());
        assertTrue(!failed.getErrorMessage().contains("hidden"));
        assertEquals(1, resultRepository.findAllByAnalysisIdOrderByIdAsc(session.getId()).size());
        assertEquals(AnalysisStatus.PARTIAL, sessionRepository.findById(session.getId()).orElseThrow().getStatus());
        assertEquals(4, sessionRepository.findById(session.getId()).orElseThrow().getErrorCount());
    }

    @Test
    void concurrentPostgresResultInsertsUseTheUniqueGuard() throws Exception {
        UUID sessionId = UUID.randomUUID();
        insertSession(sessionId, Instant.now());
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<java.util.concurrent.Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                UUID resultId = UUID.randomUUID();
                results.add(executor.submit(() -> {
                    ready.countDown();
                    go.await(10, TimeUnit.SECONDS);
                    return jdbcTemplate.update("INSERT INTO analysis_result "
                                    + "(id, analysis_id, result_type, payload) VALUES (?, ?, ?, CAST(? AS JSONB)) "
                                    + "ON CONFLICT (analysis_id, result_type) DO NOTHING",
                            resultId, sessionId, "concurrent", "{\"winner\":true}");
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            int inserted = 0;
            for (var result : results) inserted += result.get(10, TimeUnit.SECONDS);
            assertEquals(1, inserted);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM analysis_result WHERE analysis_id = ? AND result_type = 'concurrent'",
                    Integer.class, sessionId));
        } finally {
            executor.shutdownNow();
        }
    }

    private void insertSession(UUID id, Instant createdAt) {
        jdbcTemplate.update("INSERT INTO analysis_session "
                        + "(id, file_name, file_size_bytes, file_modified_at, status, created_at, error_count) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, id + ".jfr", 1L, Timestamp.from(createdAt), "COMPLETED",
                Timestamp.from(createdAt), 0);
    }

    private static String randomCredential(String prefix, int randomBytes) {
        byte[] value = new byte[randomBytes];
        SECURE_RANDOM.nextBytes(value);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
