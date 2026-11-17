package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.maiconthales.jmc_ai_analyzer_api.support.TestcontainersConfiguration;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisLocation;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.HotMethod;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RecordingSummary;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.Software;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.StackFrame;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.BatchStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RepositoryIntegrationTest {

	@Autowired
	private SoftwareRepository softwareRepository;
	@Autowired
	private AnalysisBatchRepository batchRepository;
	@Autowired
	private JfrRecordingRepository recordingRepository;
	@Autowired
	private RecordingSummaryRepository summaryRepository;
	@Autowired
	private HotMethodRepository hotMethodRepository;
	@Autowired
	private DiagnosisItemRepository diagnosisRepository;
	@Autowired
	private EntityManager entityManager;

	@Test
	void persistsGraphWithJsonbAndQueriesTopN() {
		Software software = softwareRepository.save(new Software("billing-service", 30));
		software.replaceAppPackages(List.of("com.empresa", "br.com.cliente"));
		AnalysisBatch batch = batchRepository.save(new AnalysisBatch(software.getId(), "1.0", Environment.PROD, "teste", 1));
		JfrRecording recording = recordingRepository
				.save(new JfrRecording(batch.getId(), "a.jfr", "bucket", "recordings/x.jfr", "a".repeat(64), 10));

		RecordingSummary summary = new RecordingSummary();
		summary.setRecordingId(recording.getId());
		summary.setHealthScore(80);
		summaryRepository.save(summary);

		List<StackFrame> frames = List.of(new StackFrame("com.empresa.A", "run", "()V", 10));
		for (int rank = 1; rank <= 3; rank++) {
			HotMethod method = new HotMethod();
			method.setRecordingId(recording.getId());
			method.setRank(rank);
			method.setMethodSignature("com.empresa.A.run" + rank);
			method.setSamples(10L - rank);
			method.setPercent(1.0);
			method.setStackFrames(frames);
			hotMethodRepository.save(method);
		}
		DiagnosisItem item = new DiagnosisItem();
		item.setRecordingId(recording.getId());
		item.setPriority(Priority.ALTA);
		item.setCategory(DiagnosisCategory.GC);
		item.setSource("APP:GC_PAUSE_HIGH");
		item.setScore(80);
		item.setTitle("Pausas longas");
		item.setLocation(new DiagnosisLocation("main", null, frames));
		diagnosisRepository.save(item);
		entityManager.flush();
		entityManager.clear();

		assertThat(softwareRepository.findByName("billing-service")).get()
				.satisfies(s -> assertThat(s.appPackageList()).containsExactly("com.empresa", "br.com.cliente"));
		assertThat(hotMethodRepository.findByRecordingIdAndRankLessThanEqualOrderByRankAsc(recording.getId(), 2))
				.extracting(HotMethod::getRank).containsExactly(1, 2);
		assertThat(hotMethodRepository.findByRecordingIdIn(List.of(recording.getId())).get(0).getStackFrames())
				.containsExactlyElementsOf(frames);
		assertThat(diagnosisRepository.findByRecordingIdIn(List.of(recording.getId())).get(0).getLocation().stackFrames())
				.containsExactlyElementsOf(frames);
		assertThat(batchRepository.findBySoftwareId(software.getId(), PageRequest.of(0, 10))).hasSize(1);
		assertThat(recordingRepository.existsBySha256("a".repeat(64))).isTrue();
		assertThat(recordingRepository.findShaByBatchIdIn(List.of(batch.getId()))).containsExactly("a".repeat(64));
	}

	@Test
	void deletingBatchCascadesToRecordingsAndChildren() {
		Software software = softwareRepository.save(new Software("cascade", 1));
		AnalysisBatch batch = batchRepository.save(new AnalysisBatch(software.getId(), null, null, null, 1));
		batch.setStatus(BatchStatus.COMPLETED);
		batch.setCreatedAt(Instant.now().minus(5, ChronoUnit.DAYS));
		JfrRecording recording = recordingRepository
				.save(new JfrRecording(batch.getId(), "a.jfr", "bucket", "k", "b".repeat(64), 10));
		RecordingSummary summary = new RecordingSummary();
		summary.setRecordingId(recording.getId());
		summaryRepository.save(summary);
		entityManager.flush();

		List<java.util.UUID> expired = batchRepository.findExpired(software.getId(), Instant.now().minus(1, ChronoUnit.DAYS));
		assertThat(expired).containsExactly(batch.getId());
		assertThat(batchRepository.deleteByIdIn(expired)).isEqualTo(1);

		assertThat(recordingRepository.findById(recording.getId())).isEmpty();
		assertThat(summaryRepository.findById(recording.getId())).isEmpty();
	}

	@Test
	void processingBatchesAreNeverExpired() {
		Software software = softwareRepository.save(new Software("processing", 1));
		AnalysisBatch batch = batchRepository.save(new AnalysisBatch(software.getId(), null, null, null, 1));
		batch.setCreatedAt(Instant.now().minus(5, ChronoUnit.DAYS));
		entityManager.flush();

		assertThat(batchRepository.findExpired(software.getId(), Instant.now())).isEmpty();
	}

	@Test
	void resetsProcessingRecordingsToPending() {
		Software software = softwareRepository.save(new Software("reset", 1));
		AnalysisBatch batch = batchRepository.save(new AnalysisBatch(software.getId(), null, null, null, 1));
		JfrRecording recording = new JfrRecording(batch.getId(), "a.jfr", "bucket", "k", "c".repeat(64), 10);
		recording.setStatus(AnalysisStatus.PROCESSING);
		recordingRepository.save(recording);
		entityManager.flush();

		assertThat(recordingRepository.updateStatus(AnalysisStatus.PROCESSING, AnalysisStatus.PENDING)).isEqualTo(1);
		assertThat(recordingRepository.findByStatusIn(List.of(AnalysisStatus.PENDING))).hasSize(1);
	}
}
