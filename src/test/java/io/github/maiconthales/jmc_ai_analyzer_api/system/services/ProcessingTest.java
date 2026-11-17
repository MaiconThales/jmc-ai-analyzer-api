package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.maiconthales.jmc_ai_analyzer_api.config.TestProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.AnalysisDispatcher;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.RecoveryRunner;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.jfr.RecordingAnalyzer;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisOutcome;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingExtraction;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.ProcessingTarget;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.RecordingsUploadedEvent;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.BatchStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.AnalysisInterruptedException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.BlobNotFoundException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.JfrParseException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.StorageUnavailableException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.AnalysisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.StorageProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.JfrRecordingRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.diagnosis.DiagnosisService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class ProcessingTest {

	@TempDir
	Path tmp;

	private final UUID recordingId = UUID.randomUUID();
	private final UUID batchId = UUID.randomUUID();
	private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
	private RecordingStateService state;
	private BlobStorageService blob;
	private RecordingAnalyzer analyzer;
	private DiagnosisService diagnosis;
	private AnalysisPersistenceService writer;
	private BatchStatusService batchStatus;
	private TempFileService temp;

	@BeforeEach
	void setUp() {
		state = mock(RecordingStateService.class);
		blob = mock(BlobStorageService.class);
		analyzer = mock(RecordingAnalyzer.class);
		diagnosis = mock(DiagnosisService.class);
		writer = mock(AnalysisPersistenceService.class);
		batchStatus = mock(BatchStatusService.class);
		temp = new TempFileService(new StorageProperties(tmp, Duration.ofHours(1)), Clock.systemUTC());
		when(state.start(recordingId)).thenReturn(Optional.of(new ProcessingTarget(recordingId, batchId, "k", null,
				List.of())));
	}

	@AfterEach
	void tearDown() {
		watchdog.shutdownNow();
	}

	@Test
	void successWritesResultAndRefreshesBatch() {
		RecordingExtraction extraction = mock(RecordingExtraction.class);
		DiagnosisOutcome outcome = new DiagnosisOutcome(List.of(), List.of(), 100, "cmd");
		when(analyzer.analyze(any())).thenReturn(extraction);
		when(diagnosis.diagnose(extraction, null, List.of())).thenReturn(outcome);

		processor(TestProperties.analysis()).process(recordingId);

		verify(writer).writeCompleted(recordingId, extraction, outcome);
		verify(batchStatus).refresh(batchId);
		assertThat(tmp.toFile().list()).isEmpty();
	}

	@Test
	void notPendingRecordingIsIgnored() {
		when(state.start(recordingId)).thenReturn(Optional.empty());
		processor(TestProperties.analysis()).process(recordingId);
		verify(batchStatus, never()).refresh(any());
	}

	@Test
	void failuresAreClassified() {
		when(analyzer.analyze(any())).thenThrow(new JfrParseException("ruim", null));
		processor(TestProperties.analysis()).process(recordingId);
		verify(state).fail(eq(recordingId), eq(ErrorCode.PARSE_ERROR), anyString());

		doThrow(new BlobNotFoundException("k")).when(blob).download(eq("k"), any());
		processor(TestProperties.analysis()).process(recordingId);
		verify(state).fail(eq(recordingId), eq(ErrorCode.BLOB_NOT_FOUND), anyString());

		doThrow(new StorageUnavailableException("fora", null)).when(blob).download(eq("k"), any());
		processor(TestProperties.analysis()).process(recordingId);
		verify(state).fail(eq(recordingId), eq(ErrorCode.STORAGE_UNAVAILABLE), anyString());

		doThrow(new IllegalStateException("bug")).when(blob).download(eq("k"), any());
		processor(TestProperties.analysis()).process(recordingId);
		verify(state).fail(recordingId, ErrorCode.INTERNAL_ERROR, "Erro interno ao processar a gravação.");
	}

	@Test
	void timeoutInterruptsAndMarksTimeout() {
		when(analyzer.analyze(any())).thenAnswer(invocation -> {
			try {
				new CountDownLatch(1).await();
				return null;
			} catch (InterruptedException e) {
				// o extrator real verifica a interrupção entre as etapas e lança esta exceção
				throw new AnalysisInterruptedException();
			}
		});
		AnalysisProperties fast = new AnalysisProperties(1, Duration.ofMillis(100), 300, 50, 10, 50, 1, "t");
		processor(fast).process(recordingId);
		verify(state).fail(eq(recordingId), eq(ErrorCode.TIMEOUT), contains("Processamento excedeu"));
		assertThat(Thread.currentThread().isInterrupted()).isFalse();
	}

	@Test
	void timeoutDetectedAfterAnalysisStillFails() {
		when(analyzer.analyze(any())).thenAnswer(invocation -> {
			try {
				new CountDownLatch(1).await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return mock(RecordingExtraction.class);
		});
		AnalysisProperties fast = new AnalysisProperties(1, Duration.ofMillis(50), 300, 50, 10, 50, 1, "t");
		processor(fast).process(recordingId);
		verify(state).fail(eq(recordingId), eq(ErrorCode.TIMEOUT), anyString());
		verify(writer, never()).writeCompleted(any(), any(), any());
	}

	@Test
	void classifyCoversAllCodes() {
		assertThat(AnalysisProcessingService.classify(new IllegalStateException(), true)).isEqualTo(ErrorCode.TIMEOUT);
		assertThat(AnalysisProcessingService.classify(new AnalysisInterruptedException(), false))
				.isEqualTo(ErrorCode.TIMEOUT);
	}

	@Test
	void dispatcherRunsProcessorAndSwallowsErrors() {
		AnalysisProcessingService processor = mock(AnalysisProcessingService.class);
		doThrow(new IllegalStateException("x")).when(processor).process(recordingId);
		AnalysisDispatcher dispatcher = new AnalysisDispatcher(new SyncTaskExecutor(), processor);
		dispatcher.onUploaded(new RecordingsUploadedEvent(List.of(recordingId, batchId)));
		verify(processor).process(recordingId);
		verify(processor).process(batchId);
	}

	@Test
	void recoveryRequeuesPendingAndToleratesBlobOutage() throws Exception {
		JfrRecordingRepository repository = mock(JfrRecordingRepository.class);
		JfrRecording pending = new JfrRecording(batchId, "a.jfr", "b", "k", "s", 1);
		when(repository.findByStatusIn(List.of(AnalysisStatus.PENDING))).thenReturn(List.of(pending));
		AnalysisDispatcher dispatcher = mock(AnalysisDispatcher.class);
		TransactionTemplate tx = mock(TransactionTemplate.class);
		when(tx.execute(any())).thenAnswer(i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(null));
		doThrow(new StorageUnavailableException("fora", null)).when(blob).ensureBucket();
		Path old = Files.createFile(tmp.resolve("jfr-old.jfr"));
		Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.from(Instant.now().minus(Duration.ofDays(1))));

		new RecoveryRunner(blob, temp, repository, dispatcher, tx).run(null);

		verify(repository).updateStatus(AnalysisStatus.PROCESSING, AnalysisStatus.PENDING);
		verify(dispatcher).dispatch(List.of(pending.getId()));
		assertThat(old).doesNotExist();

		when(repository.findByStatusIn(any())).thenReturn(List.of());
		new RecoveryRunner(blob, temp, repository, dispatcher, tx).run(null);
	}

	@Test
	void batchStatusIsDerivedFromRecordings() {
		assertThat(BatchStatusService.derive(List.of())).isEqualTo(BatchStatus.PROCESSING);
		assertThat(BatchStatusService.derive(List.of(AnalysisStatus.COMPLETED, AnalysisStatus.PENDING)))
				.isEqualTo(BatchStatus.PROCESSING);
		assertThat(BatchStatusService.derive(List.of(AnalysisStatus.PROCESSING))).isEqualTo(BatchStatus.PROCESSING);
		assertThat(BatchStatusService.derive(List.of(AnalysisStatus.COMPLETED))).isEqualTo(BatchStatus.COMPLETED);
		assertThat(BatchStatusService.derive(List.of(AnalysisStatus.COMPLETED, AnalysisStatus.FAILED)))
				.isEqualTo(BatchStatus.PARTIAL);
		assertThat(BatchStatusService.derive(List.of(AnalysisStatus.FAILED))).isEqualTo(BatchStatus.FAILED);

		BatchStatusService service = new BatchStatusService(null, null, Clock.systemUTC());
		AnalysisBatch batch = new AnalysisBatch(UUID.randomUUID(), null, null, null, 1);
		service.apply(batch, List.of(AnalysisStatus.FAILED));
		assertThat(batch.getFinishedAt()).isNotNull();
		service.apply(batch, List.of(AnalysisStatus.PENDING));
		assertThat(batch.getFinishedAt()).isNull();
	}

	@Test
	void tempFilesAreCreatedDeletedAndCleaned() throws Exception {
		Path file = temp.create(".jfr");
		assertThat(file).exists();
		temp.deleteQuietly(file);
		temp.deleteQuietly(null);
		assertThat(file).doesNotExist();
		Files.createFile(tmp.resolve("other.txt"));
		assertThat(temp.cleanupOld()).isZero();
		TempFileService missingDir = new TempFileService(new StorageProperties(tmp.resolve("nope"), Duration.ZERO),
				Clock.systemUTC());
		assertThat(missingDir.cleanupOld()).isZero();
		assertThat(RecordingStateService.truncate(null)).isNull();
		assertThat(RecordingStateService.truncate("e".repeat(2000))).hasSize(1000);
	}

	private AnalysisProcessingService processor(AnalysisProperties properties) {
		return new AnalysisProcessingService(state, blob, temp, analyzer, diagnosis, writer, batchStatus, watchdog, properties);
	}
}
