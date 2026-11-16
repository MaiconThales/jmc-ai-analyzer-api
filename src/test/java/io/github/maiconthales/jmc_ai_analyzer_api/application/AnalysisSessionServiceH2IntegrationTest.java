package io.github.maiconthales.jmc_ai_analyzer_api.application;

import io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretationPersistenceService;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisResult;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisSession;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStage;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisResultRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisSessionRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisStageRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AiInterpretationRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class AnalysisSessionServiceH2IntegrationTest {

    private static final Path JFR_DIRECTORY;

    static {
        try {
            JFR_DIRECTORY = Files.createTempDirectory("analysis-session-jfr-");
            Files.createFile(JFR_DIRECTORY.resolve("integration.jfr"));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void jfrDirectory(DynamicPropertyRegistry registry) {
        registry.add("jfr.data-dir", () -> JFR_DIRECTORY.toString());
    }

    @Autowired
    private AnalysisSessionService service;
    @Autowired
    private AnalysisSessionRepository sessionRepository;
    @MockitoSpyBean
    private AnalysisStageRepository stageRepository;
    @Autowired
    private AnalysisResultRepository resultRepository;
    @Autowired
    private AiInterpretationRepository aiInterpretationRepository;
    @Autowired
    private AiInterpretationPersistenceService aiInterpretationPersistence;
    @MockitoSpyBean
    private AiInterpretationRepository aiInterpretationRepositorySpy;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private McpAnalysisResultPersistenceService resultPersistence;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String fileId;

    @BeforeAll
    void setUpFileId() {
        fileId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("integration.jfr".getBytes());
    }

    @Test
    void invalidFileDoesNotCreateSession() {
        long sessionsBefore = sessionRepository.count();
        assertThrows(RuntimeException.class, () -> service.createSession("missing-file"));
        assertEquals(sessionsBefore, sessionRepository.count());
    }

    @Test
    void persistsStableControlledIdAndKeepsSnapshotWhenFileDisappears() throws Exception {
        Path file = JFR_DIRECTORY.resolve("stable-id.jfr");
        Files.createFile(file);
        String stableId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("stable-id.jfr".getBytes());
        AnalysisSession created = service.createSession(stableId);
        Files.delete(file);
        try {
            AnalysisSession stored = sessionRepository.findById(created.getId()).orElseThrow();
            assertEquals(stableId, stored.getFileId());
            assertEquals("stable-id.jfr", stored.getFileName());
            assertThrows(RuntimeException.class, () -> service.createSession(stableId));
        } finally {
            Files.createFile(file);
        }
    }

    @Test
    void persistsPendingBeforeLifecycleUpdatesAndKeepsChildren() {
        AnalysisSession created = service.createSession(fileId);
        AnalysisSession pending = sessionRepository.findById(created.getId()).orElseThrow();
        assertEquals(AnalysisStatus.PENDING, pending.getStatus());
        assertEquals(fileId, pending.getFileId());

        service.startSession(created.getId());
        AnalysisStage stage = new AnalysisStage("rules");
        pending.addStage(stage);
        stageRepository.saveAndFlush(stage);
        AnalysisResult result = new AnalysisResult("rules-result",
                tools.jackson.databind.node.JsonNodeFactory.instance.objectNode());
        pending.addResult(result);
        resultRepository.saveAndFlush(result);

        service.finishSession(created.getId(), AnalysisStatus.PARTIAL, 1,
                Instant.now().plusSeconds(1));

        AnalysisSession finished = sessionRepository.findById(created.getId()).orElseThrow();
        assertEquals(AnalysisStatus.PARTIAL, finished.getStatus());
        assertEquals(1, finished.getErrorCount());
        assertEquals(1, stageRepository.findAllByAnalysisIdOrderByIdAsc(created.getId()).size());
        assertEquals(1, resultRepository.findAllByAnalysisIdOrderByIdAsc(created.getId()).size());
    }

    @Test
    void persistsAiInterpretationJsonbMetadataAndMapsDisabledAndPartialStatuses() {
        AnalysisSession session = service.createSession(fileId);

        var completed = aiInterpretationPersistence.persist(session.getId(),
                new io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretation("COMPLETED", "provider-h2", "model-h2", "prompt-v5",
                        "{\"summary\":\"ok\",\"overallSeverity\":\"LOW\","
                                + "\"findings\":[],\"recommendations\":[],\"possibleCauses\":[],"
                                + "\"status\":\"COMPLETED\"}", "ok"));
        var disabled = aiInterpretationPersistence.persist(session.getId(),
                new io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretation("DISABLED", "provider-h2", "model-h2", "prompt-v5", "{}",
                        "AI provider is disabled"));
        var partial = aiInterpretationPersistence.persist(session.getId(),
                new io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretation("PARTIAL", "provider-h2", "model-h2", "prompt-v5",
                        "{\"summary\":\"partial\",\"overallSeverity\":\"MEDIUM\","
                                + "\"findings\":[],\"recommendations\":[],\"possibleCauses\":[],"
                                + "\"status\":\"PARTIAL\"}", "partial"));

        var stored = aiInterpretationRepository.findAllByAnalysis_IdOrderByCreatedAtDescIdDesc(session.getId());
        assertEquals(3, stored.size());
        var completedStored = stored.stream().filter(value -> value.getId().equals(completed.getId())).findFirst().orElseThrow();
        assertEquals("COMPLETED", completedStored.getStatus().name());
        assertEquals("provider-h2", completedStored.getProvider());
        assertEquals("model-h2", completedStored.getModel());
        assertEquals("prompt-v5", completedStored.getPromptVersion());
        assertEquals("ok", completedStored.getPayload().path("summary").asText());
        assertEquals("UNAVAILABLE", stored.stream().filter(value -> value.getId().equals(disabled.getId()))
                .findFirst().orElseThrow().getStatus().name());
        assertEquals("FAILED", stored.stream().filter(value -> value.getId().equals(partial.getId()))
                .findFirst().orElseThrow().getStatus().name());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'AI_INTERPRETATION' "
                        + "AND COLUMN_NAME = 'PAYLOAD' AND DATA_TYPE IN ('JSON', 'JSONB')", Integer.class));
    }

    @Test
    void rejectsInvalidAiInterpretationWithoutPersistingAndKeepsMcpResult() {
        AnalysisSession session = service.createSession(fileId);
        var mcpResult = new AnalysisResult("cpu_hot_methods",
                tools.jackson.databind.node.JsonNodeFactory.instance.objectNode());
        session.addResult(mcpResult);
        resultRepository.saveAndFlush(mcpResult);

        assertThrows(IllegalArgumentException.class, () -> aiInterpretationPersistence.persist(session.getId(),
                new io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretation("COMPLETED", "provider-h2", "model-h2", "prompt-v5", "{}", "bad")));

        assertEquals(0, aiInterpretationRepository.findAllByAnalysis_IdOrderByCreatedAtDescIdDesc(session.getId()).size());
        assertEquals(1, resultRepository.findAllByAnalysisIdOrderByIdAsc(session.getId()).size());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackAiInterpretationWhenRepositoryFailsAfterInsert() {
        AnalysisSession session = service.createSession(fileId);
        doAnswer(invocation -> {
            entityManager.persist(invocation.getArgument(0));
            entityManager.flush();
            throw new IllegalStateException("forced AI flush failure");
        }).when(aiInterpretationRepositorySpy).save(org.mockito.ArgumentMatchers.any());

        try {
            assertThrows(IllegalStateException.class, () -> aiInterpretationPersistence.persist(session.getId(),
                    new io.github.maiconthales.jmc_ai_analyzer_api.application.ai.AiInterpretation("COMPLETED", "provider-h2", "model-h2", "prompt-v5",
                            "{\"summary\":\"ok\",\"overallSeverity\":\"LOW\",\"findings\":[],"
                                    + "\"recommendations\":[],\"possibleCauses\":[],\"status\":\"COMPLETED\"}", "ok")));
        } finally {
            reset(aiInterpretationRepositorySpy);
        }
        assertEquals(0, aiInterpretationRepository.findAllByAnalysis_IdOrderByCreatedAtDescIdDesc(session.getId()).size());
    }

    @Test
    void persistsOnlySuccessfulStructuredPayloadsAndKeepsExistingResults() {
        AnalysisSession session = service.createSession(fileId);
        service.startSession(session.getId());
        tools.jackson.databind.node.ObjectNode existingPayload =
                tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        existingPayload.put("alreadyStored", true);
        AnalysisResult existing = new AnalysisResult("existing_result", existingPayload);
        session.addResult(existing);
        resultRepository.saveAndFlush(existing);

        tools.jackson.databind.node.ObjectNode successPayload =
                tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        successPayload.put("count", 7);
        successPayload.putArray("items").add("hot");
        McpAnalysisExecutionResult execution = new McpAnalysisExecutionResult(java.util.List.of(
                McpAnalysisStageResult.success(McpAnalysisStage.CPU_HOT_METHODS, successPayload),
                McpAnalysisStageResult.failed(McpAnalysisStage.GC_AND_MEMORY,
                        new IllegalStateException("simulated failure")),
                McpAnalysisStageResult.failed(McpAnalysisStage.AUTOMATED_RULES,
                        new IllegalStateException("simulated failure")),
                McpAnalysisStageResult.failed(McpAnalysisStage.LOCK_CONTENTIONS,
                        new IllegalStateException("simulated failure")),
                McpAnalysisStageResult.failed(McpAnalysisStage.FREQUENT_EXCEPTIONS,
                        new IllegalStateException("simulated failure"))));

        resultPersistence.persistSuccessfulResults(session.getId(), execution);
        service.finishMcpSession(session.getId(), execution);

        var stored = resultRepository.findAllByAnalysisIdOrderByIdAsc(session.getId());
        assertEquals(2, stored.size());
        AnalysisResult storedExisting = stored.stream()
                .filter(result -> "existing_result".equals(result.getResultType())).findFirst().orElseThrow();
        AnalysisResult storedSuccess = stored.stream()
                .filter(result -> "cpu_hot_methods".equals(result.getResultType())).findFirst().orElseThrow();
        assertEquals(existingPayload, storedExisting.getPayload());
        assertEquals(successPayload, storedSuccess.getPayload());
        assertTrue(stored.stream().noneMatch(result -> "gc_and_memory".equals(result.getResultType())));
    }

    @Test
    void persistsAllMcpStageOutcomesAndFinishesWithoutDeletingResults() {
        AnalysisSession session = service.createSession(fileId);
        service.startSession(session.getId());
        var existingPayload = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        existingPayload.put("preserve", true);
        AnalysisResult existing = new AnalysisResult("existing_result", existingPayload);
        session.addResult(existing);
        resultRepository.saveAndFlush(existing);

        String unsafe = "secret=top-secret {\"password\":\"json-secret\"} "
                + "Authorization: Bearer bearer-secret C:\\private\\recording.jfr\n" + "x".repeat(3000);
        McpAnalysisExecutionResult execution = new McpAnalysisExecutionResult(java.util.List.of(
                McpAnalysisStageResult.success(McpAnalysisStage.CPU_HOT_METHODS,
                        tools.jackson.databind.node.JsonNodeFactory.instance.objectNode()),
                McpAnalysisStageResult.failed(McpAnalysisStage.GC_AND_MEMORY,
                        new IllegalStateException(unsafe)),
                McpAnalysisStageResult.timeout(McpAnalysisStage.AUTOMATED_RULES,
                        new java.util.concurrent.TimeoutException(unsafe)),
                McpAnalysisStageResult.overloaded(McpAnalysisStage.LOCK_CONTENTIONS,
                        new IllegalStateException(unsafe)),
                McpAnalysisStageResult.failed(McpAnalysisStage.FREQUENT_EXCEPTIONS,
                        new IllegalStateException(unsafe))));

        resultPersistence.persistSuccessfulResults(session.getId(), execution);
        service.finishMcpSession(session.getId(), execution);

        var stages = stageRepository.findAllByAnalysisIdOrderByIdAsc(session.getId());
        assertEquals(5, stages.size());
        assertEquals(AnalysisStatus.PARTIAL,
                sessionRepository.findById(session.getId()).orElseThrow().getStatus());
        assertEquals(4, sessionRepository.findById(session.getId()).orElseThrow().getErrorCount());
        assertEquals(1, resultRepository.findAllByAnalysisIdOrderByIdAsc(session.getId()).stream()
                .filter(result -> "existing_result".equals(result.getResultType())).count());
        assertTrue(stages.stream().allMatch(stage -> stage.getStartedAt() != null
                && stage.getFinishedAt() != null
                && stage.getFinishedAt().compareTo(stage.getStartedAt()) >= 0));
        assertEquals(AnalysisStatus.SUCCESS, stages.stream()
                .filter(stage -> "cpu_hot_methods".equals(stage.getStageName())).findFirst().orElseThrow().getStatus());
        var failed = stages.stream().filter(stage -> "gc_and_memory".equals(stage.getStageName()))
                .findFirst().orElseThrow();
        assertEquals(AnalysisStatus.FAILED, failed.getStatus());
        assertEquals("MCP_FAILED", failed.getErrorCode());
        assertTrue(failed.getErrorMessage().length() <= 2048);
        assertEquals("MCP stage failed.", failed.getErrorMessage());
        assertTrue(!failed.getErrorMessage().contains("top-secret"));
        assertTrue(!failed.getErrorMessage().contains("json-secret"));
        assertTrue(!failed.getErrorMessage().contains("bearer-secret"));
        assertTrue(!failed.getErrorMessage().contains("recording.jfr"));
        assertEquals(AnalysisStatus.TIMEOUT, stages.stream()
                .filter(stage -> "automated_rules".equals(stage.getStageName())).findFirst().orElseThrow().getStatus());
        assertEquals(AnalysisStatus.OVERLOADED, stages.stream()
                .filter(stage -> "lock_contentions".equals(stage.getStageName())).findFirst().orElseThrow().getStatus());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackStagesAndResultsWhenTheBatchPersistenceFails() {
        AnalysisSession session = service.createSession(fileId);
        service.startSession(session.getId());
        doThrow(new IllegalStateException("forced flush failure")).when(stageRepository).flush();

        try {
            assertThrows(IllegalStateException.class,
                    () -> resultPersistence.persistSuccessfulResults(session.getId(),
                            completeExecution(McpAnalysisStageResult.success(
                                    McpAnalysisStage.CPU_HOT_METHODS,
                                    tools.jackson.databind.node.JsonNodeFactory.instance.objectNode()))));
        } finally {
            reset(stageRepository);
        }

        assertEquals(0, stageRepository.findAllByAnalysisIdOrderByIdAsc(session.getId()).size());
        assertEquals(0, resultRepository.findAllByAnalysisIdOrderByIdAsc(session.getId()).size());
    }

    @Test
    void rejectsScalarSuccessfulPayloadBeforePersistingAnything() {
        AnalysisSession session = service.createSession(fileId);
        service.startSession(session.getId());
        var scalar = tools.jackson.databind.node.JsonNodeFactory.instance.textNode("not-structured");
        McpAnalysisExecutionResult execution = completeExecution(
                McpAnalysisStageResult.success(McpAnalysisStage.CPU_HOT_METHODS, scalar));

        assertThrows(IllegalArgumentException.class,
                () -> resultPersistence.persistSuccessfulResults(session.getId(), execution));
        assertEquals(0, resultRepository.findAllByAnalysisIdOrderByIdAsc(session.getId()).size());

        var nullPayload = completeExecution(McpAnalysisStageResult.success(McpAnalysisStage.CPU_HOT_METHODS,
                tools.jackson.databind.node.JsonNodeFactory.instance.nullNode()));
        assertThrows(IllegalArgumentException.class,
                () -> resultPersistence.persistSuccessfulResults(session.getId(), nullPayload));
    }

    @Test
    void rejectsSubsetBeforePersistence() {
        AnalysisSession session = service.createSession(fileId);
        service.startSession(session.getId());
        McpAnalysisExecutionResult execution = new McpAnalysisExecutionResult(java.util.List.of(
                McpAnalysisStageResult.failed(McpAnalysisStage.CPU_HOT_METHODS,
                        new IllegalStateException("simulated failure"))));

        assertThrows(IllegalArgumentException.class,
                () -> resultPersistence.persistSuccessfulResults(session.getId(), execution));
    }

    @Test
    void acceptsSuccessfulReplayWithoutDeletingOrReplacingExistingResult() {
        AnalysisSession session = service.createSession(fileId);
        service.startSession(session.getId());
        var payload = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        resultPersistence.persistSuccessfulResults(session.getId(), completeExecution(
                McpAnalysisStageResult.success(McpAnalysisStage.CPU_HOT_METHODS, payload)));

        resultPersistence.persistSuccessfulResults(session.getId(), completeExecution(
                McpAnalysisStageResult.success(McpAnalysisStage.CPU_HOT_METHODS,
                        tools.jackson.databind.node.JsonNodeFactory.instance.objectNode())));
        assertEquals(1, resultRepository.findAllByAnalysisIdOrderByIdAsc(session.getId()).size());
    }

    @Test
    void derivesCompletedWhenAllFiveOutcomesSucceedAndRecordsFinishedAt() {
        AnalysisSession session = service.createSession(fileId);
        service.startSession(session.getId());
        McpAnalysisExecutionResult execution = allOutcomes(McpAnalysisStageResult.Status.SUCCESS);

        resultPersistence.persistSuccessfulResults(session.getId(), execution);
        service.finishMcpSession(session.getId(), execution);

        AnalysisSession finished = sessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(AnalysisStatus.COMPLETED, finished.getStatus());
        assertEquals(0, finished.getErrorCount());
        assertTrue(finished.getFinishedAt() != null);
        assertEquals(5, resultRepository.findAllByAnalysisIdOrderByIdAsc(session.getId()).size());
    }

    @Test
    void derivesFailedWhenNoOutcomeSucceedsWithoutDeletingExistingResults() {
        AnalysisSession session = service.createSession(fileId);
        service.startSession(session.getId());
        var existing = new AnalysisResult("preexisting", tools.jackson.databind.node.JsonNodeFactory.instance.objectNode());
        session.addResult(existing);
        resultRepository.saveAndFlush(existing);
        McpAnalysisExecutionResult execution = allOutcomes(McpAnalysisStageResult.Status.FAILED);

        resultPersistence.persistSuccessfulResults(session.getId(), execution);
        service.finishMcpSession(session.getId(), execution);

        AnalysisSession finished = sessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(AnalysisStatus.FAILED, finished.getStatus());
        assertEquals(5, finished.getErrorCount());
        assertEquals(1, resultRepository.findAllByAnalysisIdOrderByIdAsc(session.getId()).size());
    }

    @Test
    void timeoutAndOverloadAreErrorsAndProducePartialWhenASuccessExists() {
        AnalysisSession session = service.createSession(fileId);
        service.startSession(session.getId());
        McpAnalysisExecutionResult execution = new McpAnalysisExecutionResult(List.of(
                McpAnalysisStageResult.success(McpAnalysisStage.CPU_HOT_METHODS,
                        tools.jackson.databind.node.JsonNodeFactory.instance.objectNode()),
                McpAnalysisStageResult.timeout(McpAnalysisStage.AUTOMATED_RULES, new java.util.concurrent.TimeoutException()),
                McpAnalysisStageResult.overloaded(McpAnalysisStage.GC_AND_MEMORY, new IllegalStateException()),
                McpAnalysisStageResult.failed(McpAnalysisStage.LOCK_CONTENTIONS, new IllegalStateException()),
                McpAnalysisStageResult.timeout(McpAnalysisStage.FREQUENT_EXCEPTIONS, new java.util.concurrent.TimeoutException())));

        resultPersistence.persistSuccessfulResults(session.getId(), execution);
        service.finishMcpSession(session.getId(), execution);

        AnalysisSession finished = sessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(AnalysisStatus.PARTIAL, finished.getStatus());
        assertEquals(4, finished.getErrorCount());
        assertEquals(1, resultRepository.findAllByAnalysisIdOrderByIdAsc(session.getId()).size());
    }

    private static McpAnalysisExecutionResult completeExecution(McpAnalysisStageResult success) {
        return new McpAnalysisExecutionResult(java.util.List.of(
                success,
                McpAnalysisStageResult.failed(McpAnalysisStage.AUTOMATED_RULES, new IllegalStateException()),
                McpAnalysisStageResult.failed(McpAnalysisStage.GC_AND_MEMORY, new IllegalStateException()),
                McpAnalysisStageResult.failed(McpAnalysisStage.LOCK_CONTENTIONS, new IllegalStateException()),
                McpAnalysisStageResult.failed(McpAnalysisStage.FREQUENT_EXCEPTIONS, new IllegalStateException())));
    }

    private static McpAnalysisExecutionResult allOutcomes(McpAnalysisStageResult.Status status) {
        List<McpAnalysisStageResult> outcomes = new ArrayList<>();
        for (McpAnalysisStage stage : McpAnalysisStage.values()) {
            outcomes.add(status == McpAnalysisStageResult.Status.SUCCESS
                    ? McpAnalysisStageResult.success(stage,
                    tools.jackson.databind.node.JsonNodeFactory.instance.objectNode())
                    : McpAnalysisStageResult.failed(stage, new IllegalStateException("simulated failure")));
        }
        return new McpAnalysisExecutionResult(outcomes);
    }

    @Test
    void rejectsNullAndDuplicateStagesBeforePersistence() {
        assertThrows(NullPointerException.class,
                () -> new McpAnalysisExecutionResult(java.util.Arrays.asList((McpAnalysisStageResult) null)));

        McpAnalysisStageResult first = McpAnalysisStageResult.success(
                McpAnalysisStage.CPU_HOT_METHODS,
                tools.jackson.databind.node.JsonNodeFactory.instance.objectNode());
        McpAnalysisStageResult duplicate = McpAnalysisStageResult.success(
                McpAnalysisStage.CPU_HOT_METHODS,
                tools.jackson.databind.node.JsonNodeFactory.instance.objectNode());
        assertThrows(IllegalArgumentException.class,
                () -> new McpAnalysisExecutionResult(java.util.List.of(first, duplicate)));
    }

    @Test
    void rejectsStartingAnAlreadyRunningSession() {
        AnalysisSession session = service.createSession(fileId);
        service.startSession(session.getId());
        assertThrows(IllegalStateException.class, () -> service.startSession(session.getId()));
    }

    @Test
    void terminalSessionCannotBeFinishedAgainAndRetainsMcpResults() {
        AnalysisSession session = service.createSession(fileId);
        service.startSession(session.getId());
        McpAnalysisExecutionResult execution = allOutcomes(McpAnalysisStageResult.Status.SUCCESS);
        resultPersistence.persistSuccessfulResults(session.getId(), execution);
        service.finishMcpSession(session.getId(), execution);

        assertThrows(IllegalStateException.class,
                () -> service.finishMcpSession(session.getId(), execution));
        assertEquals(AnalysisStatus.COMPLETED,
                sessionRepository.findById(session.getId()).orElseThrow().getStatus());
        assertEquals(5, resultRepository.findAllByAnalysisIdOrderByIdAsc(session.getId()).size());
    }

    @Test
    void concurrentH2ResultInsertsUseIndependentTransactionsAndKeepOneRow() throws Exception {
        UUID sessionId = UUID.randomUUID();
        TransactionTemplate committed = new TransactionTemplate(transactionManager);
        committed.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        committed.executeWithoutResult(status -> jdbcTemplate.update(
                "INSERT INTO analysis_session "
                        + "(id, file_name, file_size_bytes, status, error_count) "
                        + "VALUES (?, ?, ?, 'RUNNING', 0)",
                sessionId, "concurrent.jfr", 1L));

        // H2 serializes concurrent MERGE writers at table level; two
        // independent transactions are enough to exercise the winner/loser
        // race without turning the test into a lock-timeout stress test.
        int workers = 2;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<java.util.concurrent.Future<Integer>> inserts = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                inserts.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(go.await(10, TimeUnit.SECONDS));
                    TransactionTemplate independent = new TransactionTemplate(transactionManager);
                    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    return independent.execute(status -> resultPersistence.persistSuccessfulResults(
                            sessionId, completeExecution(McpAnalysisStageResult.success(
                                    McpAnalysisStage.CPU_HOT_METHODS,
                                    tools.jackson.databind.node.JsonNodeFactory.instance.objectNode())))
                            .size());
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            int inserted = 0;
            for (var result : inserts) inserted += result.get(10, TimeUnit.SECONDS);
            assertEquals(1, inserted);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM analysis_result WHERE analysis_id = ? AND result_type = 'cpu_hot_methods'",
                    Integer.class, sessionId));
        } finally {
            go.countDown();
            executor.shutdownNow();
        }
    }
}
