package io.github.maiconthales.jmc_ai_analyzer_api.system.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.maiconthales.jmc_ai_analyzer_api.support.JfrFixtures;
import io.github.maiconthales.jmc_ai_analyzer_api.support.TestcontainersConfiguration;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.RetentionJob;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.RetentionResult;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AnalysisBatchRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.JfrRecordingRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.SoftwareRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.BlobStorageService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Fluxos completos com PostgreSQL e SeaweedFS reais (critérios de aceite da spec). */
@SpringBootTest(properties = "app.upload.max-files=5")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EndToEndIntegrationTest {

	@TempDir
	Path tmp;

	@Autowired
	private MockMvc mvc;
	@Autowired
	private ObjectMapper json;
	@Autowired
	private AnalysisBatchRepository batchRepository;
	@Autowired
	private JfrRecordingRepository recordingRepository;
	@Autowired
	private SoftwareRepository softwareRepository;
	@Autowired
	private BlobStorageService blobStorage;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	void examplesCompleteWithDiagnosisCoverageAndDashboards() throws Exception {
		JsonNode batch = upload(uploadRequest("e2e-examples").param("environment", "prod")
				.param("description", "lentidão no fechamento"), 0, 1, 2);
		String batchId = batch.get("batchId").asString();
		JsonNode status = awaitFinished(batchId);

		assertThat(status.get("status").asString()).isEqualTo("COMPLETED");
		assertThat(status.get("environment").asString()).isEqualTo("PROD");
		assertThat(status.get("description").asString()).isEqualTo("lentidão no fechamento");
		String recordingId = status.get("recordings").get(0).get("id").asString();

		JsonNode dashboard = getJson("/api/v1/recordings/" + recordingId + "/dashboard?topN=5");
		List<String> sources = values(dashboard.get("diagnosis"), "source");
		assertThat(sources).contains("APP:TIERED_STOP_LEVEL", "APP:ACTIVE_PROCESSOR_COUNT", "JMC:Allocations.thread",
				"APP:RECORDING_COVERAGE");
		assertThat(coverageStatus(dashboard, "EXCEPTIONS")).isEqualTo("AUSENTE");
		assertThat(coverageStatus(dashboard, "FILE_IO")).isEqualTo("AUSENTE");
		assertThat(coverageStatus(dashboard, "HOT_METHODS")).isEqualTo("INSUFICIENTE");
		assertThat(dashboard.get("coverage").get("suggestedCommand").asString())
				.contains("settings=profile", "jdk.JavaExceptionThrow#enabled=true");
		assertThat(dashboard.get("healthScore").asInt()).isBetween(0, 100);
		for (String block : List.of("cpu", "heap", "gc", "threads", "locks", "hotMethods", "allocation", "exceptions",
				"io", "leakSuspects", "threadCpu")) {
			assertThat(dashboard.get(block).get("available").asBoolean()).as(block).isTrue();
		}
		assertThat(dashboard.get("hotMethods").get("top")).hasSize(5);
		assertThat(dashboard.get("cpu").get("series").get("CPU_MACHINE")).isNotEmpty();
		assertThat(dashboard.get("findings").get(0).get("severity").asString()).isEqualTo("WARNING");
		assertThat(dashboard.get("findings").toString()).doesNotContain("{mostAllocatedType}");
		assertThat(dashboard.get("metadata").get("cpuCores").asInt()).isEqualTo(6);

		JsonNode consolidated = getJson("/api/v1/analyses/" + batchId + "/dashboard");
		assertThat(consolidated.get("metadata").get("recordingCount").asInt()).isEqualTo(3);
		assertThat(consolidated.get("comparison")).hasSize(3);
		assertThat(consolidated.get("cpu").get("series").size()).isEqualTo(3);
		assertThat(consolidated.get("excludedRecordings")).isEmpty();
		assertThat(consolidated.get("diagnosis").get(0).get("recordingIds")).isNotEmpty();
		assertThat(consolidated.get("findings").get(0).get("recordingIds")).isNotEmpty();
		assertThat(consolidated.get("hotMethods").get("totalSamples").asLong())
				.isGreaterThan(dashboard.get("hotMethods").get("totalSamples").asLong());

		JsonNode history = getJson("/api/v1/analyses?software=e2e-examples");
		assertThat(history.get("content").get(0).get("environment").asString()).isEqualTo("PROD");
		assertThat(history.get("content").get(0).get("warningFindings").asLong()).isPositive();
		assertThat(history.get("page").get("totalElements").asInt()).isEqualTo(1);

		assertNoSensitiveData(dashboard.toString());
	}

	@Test
	void corruptedFileMakesBatchPartial() throws Exception {
		MockMultipartHttpServletRequestBuilder request = uploadRequest("e2e-partial");
		request.file(file("ok.jfr", JfrFixtures.bytes(JfrFixtures.example(0))));
		request.file(file("broken.jfr", JfrFixtures.bytes(JfrFixtures.truncated(tmp))));
		JsonNode batch = json.readTree(mvc.perform(request).andExpect(status().isAccepted()).andReturn().getResponse()
				.getContentAsString());
		JsonNode status = awaitFinished(batch.get("batchId").asString());

		assertThat(status.get("status").asString()).isEqualTo("PARTIAL");
		JsonNode failed = status.get("recordings").get(1);
		assertThat(failed.get("status").asString()).isEqualTo("FAILED");
		assertThat(failed.get("errorCode").asString()).isEqualTo("PARSE_ERROR");
		mvc.perform(get("/api/v1/recordings/" + failed.get("id").asString() + "/dashboard"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_READY"))
				.andExpect(jsonPath("$.errorCode").value("PARSE_ERROR"));
		JsonNode consolidated = getJson("/api/v1/analyses/" + batch.get("batchId").asString() + "/dashboard");
		assertThat(consolidated.get("excludedRecordings")).hasSize(1);
	}

	@Test
	void invalidUploadsAreRejectedWithoutPersisting() throws Exception {
		long batches = batchRepository.count();
		int objects = blobStorage.list("recordings/").size();
		byte[] valid = JfrFixtures.bytes(JfrFixtures.example(0));

		mvc.perform(multipart("/api/v1/analyses").file(file("a.jfr", valid)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
		mvc.perform(uploadRequest("e2e-invalid"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_UPLOAD"));
		mvc.perform(uploadRequest("e2e-invalid").file(file("a.txt", valid)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FILE"));
		mvc.perform(uploadRequest("e2e-invalid").file(file("a.jfr", valid))
				.file(file("bad.jfr", JfrFixtures.bytes(JfrFixtures.badMagic(tmp)))))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FILE"));
		MockMultipartHttpServletRequestBuilder tooMany = uploadRequest("e2e-invalid");
		for (int i = 0; i < 6; i++) {
			tooMany.file(file("f" + i + ".jfr", valid));
		}
		mvc.perform(tooMany).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOO_MANY_FILES"));

		assertThat(batchRepository.count()).isEqualTo(batches);
		assertThat(softwareRepository.findByName("e2e-invalid")).isEmpty();
		assertThat(blobStorage.list("recordings/")).hasSize(objects);
	}

	@Test
	void appPackagesChangeAppFrameOfExistingAnalyses() throws Exception {
		JsonNode batch = upload(uploadRequest("e2e-frames"), 0);
		String recordingId = awaitFinished(batch.get("batchId").asString()).get("recordings").get(0).get("id").asString();
		String before = appFrameOfAllocationRate(recordingId);

		mvc.perform(patch("/api/v1/software/e2e-frames").contentType(MediaType.APPLICATION_JSON)
				.content("{\"appPackages\":[\"br.com.sascar.core.tacweb.bean\"],\"retentionDays\":30}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.retentionDays").value(30))
				.andExpect(jsonPath("$.appPackages[0]").value("br.com.sascar.core.tacweb.bean"))
				.andExpect(jsonPath("$.batchCount").value(1));

		assertThat(before).contains("HexaUtil");
		assertThat(appFrameOfAllocationRate(recordingId)).contains("tacweb.bean.Tac");
	}

	@Test
	void softwareEndpointsValidateInput() throws Exception {
		mvc.perform(patch("/api/v1/software/nao-existe").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		upload(uploadRequest("e2e-software"), 1);
		mvc.perform(patch("/api/v1/software/e2e-software").contentType(MediaType.APPLICATION_JSON)
				.content("{\"retentionDays\":0}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
		mvc.perform(get("/api/v1/software")).andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'e2e-software')].retentionDays").value(90));
		mvc.perform(get("/api/v1/analyses?software=nao-existe")).andExpect(status().isNotFound());
	}

	@Test
	void dashboardsReportMissingAndInvalidRequests() throws Exception {
		String random = UUID.randomUUID().toString();
		mvc.perform(get("/api/v1/recordings/" + random + "/dashboard")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/analyses/" + random)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/analyses/" + random + "/dashboard")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/analyses/not-a-uuid")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
		JsonNode batch = upload(uploadRequest("e2e-topn"), 2);
		String batchId = batch.get("batchId").asString();
		awaitFinished(batchId);
		mvc.perform(get("/api/v1/analyses/" + batchId + "/dashboard?topN=0")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

	@Test
	void retentionRemovesExpiredBatchesAndOnlyUnreferencedObjects() throws Exception {
		JsonNode kept = upload(uploadRequest("e2e-keep"), 1);
		JsonNode expired = upload(uploadRequest("e2e-expire"), 1);
		awaitFinished(kept.get("batchId").asString());
		awaitFinished(expired.get("batchId").asString());
		mvc.perform(patch("/api/v1/software/e2e-expire").contentType(MediaType.APPLICATION_JSON)
				.content("{\"retentionDays\":1}")).andExpect(status().isOk());
		UUID expiredId = UUID.fromString(expired.get("batchId").asString());
		transactionTemplate.executeWithoutResult(s -> {
			AnalysisBatch b = batchRepository.findById(expiredId).orElseThrow();
			b.setCreatedAt(Instant.now().minus(Duration.ofDays(3)));
		});
		String sharedKey = recordingRepository.findByBatchIdOrderByCreatedAtAsc(expiredId).get(0).getBlobKey();

		RetentionJob job = new RetentionJob(softwareRepository, batchRepository, recordingRepository, blobStorage,
				transactionTemplate, Clock.offset(Clock.systemUTC(), Duration.ofHours(2)));
		RetentionResult result = job.run();

		assertThat(result.batches()).isEqualTo(1);
		assertThat(batchRepository.findById(expiredId)).isEmpty();
		assertThat(batchRepository.findById(UUID.fromString(kept.get("batchId").asString()))).isPresent();
		assertThat(blobStorage.exists(sharedKey)).as("objeto ainda usado pelo outro software").isTrue();
	}

	@Test
	void swaggerAndHealthAreAvailable() throws Exception {
		JsonNode docs = getJson("/v3/api-docs");
		assertThat(docs.get("paths").propertyNames()).contains("/api/v1/analyses", "/api/v1/analyses/{batchId}",
				"/api/v1/analyses/{batchId}/dashboard", "/api/v1/recordings/{recordingId}/dashboard",
				"/api/v1/software", "/api/v1/software/{name}");
		mvc.perform(get("/actuator/health")).andExpect(status().isOk())
				.andExpect(jsonPath("$.components.blobStorage.status").value("UP"))
				.andExpect(jsonPath("$.components.db.status").value("UP"));
	}

	private void assertNoSensitiveData(String body) throws Exception {
		try (RecordingFile file = new RecordingFile(JfrFixtures.example(0))) {
			List<String> values = new ArrayList<>();
			while (file.hasMoreEvents()) {
				RecordedEvent event = file.readEvent();
				if (event.getEventType().getName().equals("jdk.InitialEnvironmentVariable")) {
					String value = event.getString("value");
					if (value != null && value.length() > 12) {
						values.add(value);
					}
				}
			}
			assertThat(values).isNotEmpty().allSatisfy(v -> assertThat(body).doesNotContain(v));
		}
	}

	private String appFrameOfAllocationRate(String recordingId) throws Exception {
		JsonNode dashboard = getJson("/api/v1/recordings/" + recordingId + "/dashboard");
		return StreamSupport.stream(dashboard.get("diagnosis").spliterator(), false)
				.filter(d -> d.get("source").asString().equals("APP:ALLOCATION_RATE_HIGH"))
				.findFirst().orElseThrow().get("location").get("appFrame").toString();
	}

	private static String coverageStatus(JsonNode dashboard, String domain) {
		return StreamSupport.stream(dashboard.get("coverage").get("items").spliterator(), false)
				.filter(c -> c.get("domain").asString().equals(domain)).findFirst().orElseThrow()
				.get("status").asString();
	}

	private static List<String> values(JsonNode array, String field) {
		return StreamSupport.stream(array.spliterator(), false).map(n -> n.get(field).asString()).toList();
	}

	private JsonNode upload(MockMultipartHttpServletRequestBuilder request, int... examples) throws Exception {
		for (int i : examples) {
			request.file(file(JfrFixtures.EXAMPLES.get(i), JfrFixtures.bytes(JfrFixtures.example(i))));
		}
		return json.readTree(mvc.perform(request).andExpect(status().isAccepted()).andReturn().getResponse()
				.getContentAsString());
	}

	private JsonNode awaitFinished(String batchId) throws Exception {
		await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofMillis(200))
				.until(() -> !"PROCESSING".equals(getJson("/api/v1/analyses/" + batchId).get("status").asString()));
		return getJson("/api/v1/analyses/" + batchId);
	}

	private JsonNode getJson(String url) throws Exception {
		return json.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString());
	}

	private static MockMultipartHttpServletRequestBuilder uploadRequest(String software) {
		return multipart("/api/v1/analyses").param("software", software);
	}

	private static MockMultipartFile file(String name, byte[] content) {
		return new MockMultipartFile("files", name, "application/octet-stream", content);
	}
}
