package io.github.maiconthales.jmc_ai_analyzer_api.system.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.BlobObject;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.RetentionResult;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.Software;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.StorageUnavailableException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AnalysisBatchRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.JfrRecordingRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.SoftwareRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.BlobStorageService;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.UploadService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class RetentionJobTest {

	private final SoftwareRepository softwares = mock(SoftwareRepository.class);
	private final AnalysisBatchRepository batches = mock(AnalysisBatchRepository.class);
	private final JfrRecordingRepository recordings = mock(JfrRecordingRepository.class);
	private final BlobStorageService blob = mock(BlobStorageService.class);
	private final TransactionTemplate tx = mock(TransactionTemplate.class);
	private final Instant now = Instant.parse("2026-11-17T12:00:00Z");

	@Test
	void deletesOnlyOldUnreferencedObjects() {
		when(tx.execute(any())).thenAnswer(i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(null));
		when(softwares.findAll()).thenReturn(List.of(new Software("a", 30)));
		when(batches.findExpired(any(), any())).thenReturn(List.of());
		when(recordings.findAllBlobKeys()).thenReturn(List.of("recordings/used.jfr"));
		when(blob.list(UploadService.KEY_PREFIX)).thenReturn(List.of(
				new BlobObject("recordings/used.jfr", now.minusSeconds(86_400)),
				new BlobObject("recordings/orphan.jfr", now.minusSeconds(86_400)),
				new BlobObject("recordings/recent.jfr", now.minusSeconds(60)),
				new BlobObject("recordings/no-date.jfr", null)));

		RetentionResult result = job().run();

		assertThat(result).isEqualTo(new RetentionResult(0, 2));
		verify(blob).delete("recordings/orphan.jfr");
		verify(blob).delete("recordings/no-date.jfr");
		verify(blob, never()).delete("recordings/recent.jfr");
		verify(blob, never()).delete("recordings/used.jfr");
	}

	@Test
	void blobOutageIsToleratedAndNullTransactionResultCountsAsZero() {
		when(softwares.findAll()).thenReturn(List.of(new Software("a", 30)));
		when(tx.execute(any())).thenReturn(null);
		when(blob.list(any())).thenThrow(new StorageUnavailableException("fora", null));
		RetentionJob job = job();
		job.scheduled();
		assertThat(job.run()).isEqualTo(new RetentionResult(0, 0));
	}

	private RetentionJob job() {
		return new RetentionJob(softwares, batches, recordings, blob, tx, Clock.fixed(now, ZoneOffset.UTC));
	}
}
