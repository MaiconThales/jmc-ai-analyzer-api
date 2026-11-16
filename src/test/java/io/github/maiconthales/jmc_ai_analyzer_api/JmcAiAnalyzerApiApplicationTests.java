package io.github.maiconthales.jmc_ai_analyzer_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.flywaydb.core.Flyway;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisSession;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStage;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisResult;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisSessionRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisStageRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.AnalysisResultRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisOrchestrator;
import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisResultPersistenceService;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp.McpAnalysisToolAdapter;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp.McpProtocolClient;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp.McpStdioClient;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class JmcAiAnalyzerApiApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Environment environment;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AnalysisSessionRepository analysisSessionRepository;

	@Autowired
	private AnalysisStageRepository analysisStageRepository;

	@Autowired
	private AnalysisResultRepository analysisResultRepository;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
	}

	@Test
	void registersMcpAnalysisBeansWithoutStartingAProcess() {
		org.junit.jupiter.api.Assertions.assertNotNull(
			applicationContext.getBean(McpAnalysisToolAdapter.class));
		org.junit.jupiter.api.Assertions.assertNotNull(
			applicationContext.getBean(McpAnalysisOrchestrator.class));
		org.junit.jupiter.api.Assertions.assertNotNull(
			applicationContext.getBean(McpAnalysisResultPersistenceService.class));
		org.junit.jupiter.api.Assertions.assertNotNull(
			applicationContext.getBean(McpProtocolClient.class));
		org.junit.jupiter.api.Assertions.assertFalse(
			applicationContext.getBean(McpStdioClient.class).isRunning());
	}

	@Test
	void localProfileUsesH2WithFlywayEnabledAndSchemaGenerationDisabled() {
		org.junit.jupiter.api.Assertions.assertTrue(
			environment.getProperty("spring.datasource.url").startsWith("jdbc:h2:"));
		org.junit.jupiter.api.Assertions.assertEquals("none",
			environment.getProperty("spring.jpa.hibernate.ddl-auto"));
		org.junit.jupiter.api.Assertions.assertEquals("true",
			environment.getProperty("spring.flyway.enabled"));
		org.junit.jupiter.api.Assertions.assertEquals("classpath:db/migration",
			environment.getProperty("spring.flyway.locations"));
		org.junit.jupiter.api.Assertions.assertNotNull(flyway);
	}

	@Test
	void analysisMigrationsAreAppliedInOrder() {
		org.junit.jupiter.api.Assertions.assertEquals(7, flyway.info().applied().length);
		org.junit.jupiter.api.Assertions.assertEquals(4,
			jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
						+ "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME IN "
						+ "('ANALYSIS_SESSION', 'ANALYSIS_STAGE', 'ANALYSIS_RESULT', 'AI_INTERPRETATION')",
				Integer.class));
	}

	@Test
	void aiInterpretationHasJsonbPayloadMetadataAndProtectedSessionForeignKey() {
		org.junit.jupiter.api.Assertions.assertEquals(1,
			jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
						+ "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'AI_INTERPRETATION' "
						+ "AND COLUMN_NAME = 'PAYLOAD' AND DATA_TYPE IN ('JSON', 'JSONB')",
				Integer.class));
		org.junit.jupiter.api.Assertions.assertEquals(1,
			jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS "
						+ "WHERE CONSTRAINT_SCHEMA = 'PUBLIC' "
						+ "AND CONSTRAINT_NAME = 'FK_AI_INTERPRETATION_SESSION'",
				Integer.class));
		org.junit.jupiter.api.Assertions.assertEquals(1,
			jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
						+ "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'AI_INTERPRETATION' "
						+ "AND INDEX_NAME = 'IDX_AI_INTERPRETATION_ANALYSIS_CREATED'",
				Integer.class));
	}

	@Test
	void aiInterpretationAcceptsUnavailableStatus() {
		UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000030");
		UUID interpretationId = UUID.fromString("00000000-0000-0000-0000-000000000031");
		Instant createdAt = Instant.parse("2026-11-16T12:03:00Z");
		insertSession(sessionId, createdAt);

		jdbcTemplate.update("INSERT INTO ai_interpretation "
				+ "(id, analysis_id, status, provider, model, prompt_version, payload) "
				+ "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON))",
				interpretationId, sessionId, "UNAVAILABLE", "test-provider", "test-model", "v1", "{}");

		org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ai_interpretation WHERE id = ? AND status = 'UNAVAILABLE'",
				Integer.class, interpretationId));
	}

	@Test
	void analysisResultHasJsonbPayloadAndStageHasSessionForeignKey() {
		org.junit.jupiter.api.Assertions.assertEquals(1,
			jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
						+ "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'ANALYSIS_RESULT' "
						+ "AND COLUMN_NAME = 'PAYLOAD' AND DATA_TYPE IN ('JSON', 'JSONB')",
				Integer.class));
		org.junit.jupiter.api.Assertions.assertEquals(1,
			jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS "
						+ "WHERE CONSTRAINT_SCHEMA = 'PUBLIC' "
						+ "AND CONSTRAINT_NAME = 'FK_ANALYSIS_STAGE_SESSION'",
				Integer.class));
	}

	@Test
	void historyUsesCreatedAtAndIdAsStableTieBreakers() {
		// Keep these rows newer than fixtures created by the other tests; the
		// application test context intentionally uses one in-memory database.
		Instant createdAt = Instant.parse("2099-11-16T12:00:00Z");
		UUID olderId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID newerId = UUID.fromString("00000000-0000-0000-0000-000000000002");
		insertSession(olderId, createdAt);
		insertSession(newerId, createdAt);

		Page<AnalysisSession> page = analysisSessionRepository.findHistory(PageRequest.of(0, 2));

		org.junit.jupiter.api.Assertions.assertEquals(2, page.getContent().size());
		org.junit.jupiter.api.Assertions.assertEquals(newerId, page.getContent().get(0).getId());
		org.junit.jupiter.api.Assertions.assertEquals(olderId, page.getContent().get(1).getId());
	}

	@Test
	void stagesAndResultsAreLoadedInStableIdOrder() {
		UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000010");
		insertSession(sessionId, Instant.parse("2026-11-16T12:01:00Z"));
		UUID stageFirst = UUID.fromString("00000000-0000-0000-0000-000000000011");
		UUID stageSecond = UUID.fromString("00000000-0000-0000-0000-000000000012");
		UUID resultFirst = UUID.fromString("00000000-0000-0000-0000-000000000013");
		UUID resultSecond = UUID.fromString("00000000-0000-0000-0000-000000000014");

		jdbcTemplate.update("INSERT INTO analysis_stage "
				+ "(id, analysis_id, stage_name, status) VALUES (?, ?, ?, ?)",
				stageSecond, sessionId, "second", "PENDING");
		jdbcTemplate.update("INSERT INTO analysis_stage "
				+ "(id, analysis_id, stage_name, status) VALUES (?, ?, ?, ?)",
				stageFirst, sessionId, "first", "PENDING");
		jdbcTemplate.update("INSERT INTO analysis_result "
				+ "(id, analysis_id, result_type, payload) VALUES (?, ?, ?, CAST(? AS JSON))",
				resultSecond, sessionId, "second", "{}");
		jdbcTemplate.update("INSERT INTO analysis_result "
				+ "(id, analysis_id, result_type, payload) VALUES (?, ?, ?, CAST(? AS JSON))",
				resultFirst, sessionId, "first", "{}");

		org.junit.jupiter.api.Assertions.assertEquals(
				java.util.List.of(stageFirst, stageSecond),
				analysisStageRepository.findAllByAnalysisIdOrderByIdAsc(sessionId).stream()
						.map(AnalysisStage::getId).toList());
		org.junit.jupiter.api.Assertions.assertEquals(
				java.util.List.of(resultFirst, resultSecond),
				analysisResultRepository.findAllByAnalysisIdOrderByIdAsc(sessionId).stream()
						.map(AnalysisResult::getId).toList());
	}

	@Test
	void deletingSessionDoesNotDeleteHistoricalStagesOrResults() {
		UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000020");
		insertSession(sessionId, Instant.parse("2026-11-16T12:02:00Z"));
		jdbcTemplate.update("INSERT INTO analysis_stage "
				+ "(id, analysis_id, stage_name, status) VALUES (?, ?, ?, ?)",
				UUID.fromString("00000000-0000-0000-0000-000000000021"), sessionId, "protected", "PENDING");
		jdbcTemplate.update("INSERT INTO analysis_result "
				+ "(id, analysis_id, result_type, payload) VALUES (?, ?, ?, CAST(? AS JSON))",
				UUID.fromString("00000000-0000-0000-0000-000000000022"), sessionId, "protected", "{}");

		org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
				() -> jdbcTemplate.update("DELETE FROM analysis_session WHERE id = ?", sessionId));
		org.junit.jupiter.api.Assertions.assertEquals(1,
				jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis_stage WHERE analysis_id = ?",
						Integer.class, sessionId));
		org.junit.jupiter.api.Assertions.assertEquals(1,
				jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis_result WHERE analysis_id = ?",
						Integer.class, sessionId));
	}

	private void insertSession(UUID id, Instant createdAt) {
		jdbcTemplate.update("INSERT INTO analysis_session "
				+ "(id, file_name, file_size_bytes, file_modified_at, status, created_at, error_count) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?)", id, id + ".jfr", 1L, createdAt,
				"COMPLETED", createdAt, 0);
	}

	@Test
	void healthEndpointReturnsApplicationUp() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("application/json"))
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

}
