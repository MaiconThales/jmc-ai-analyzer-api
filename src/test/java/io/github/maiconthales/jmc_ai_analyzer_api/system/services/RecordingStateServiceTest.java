package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.Software;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AnalysisBatchRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.JfrRecordingRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.SoftwareRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordingStateServiceTest {

	private final JfrRecordingRepository recordings = mock(JfrRecordingRepository.class);
	private final AnalysisBatchRepository batches = mock(AnalysisBatchRepository.class);
	private final SoftwareRepository softwares = mock(SoftwareRepository.class);
	private final RecordingStateService service = new RecordingStateService(recordings, batches, softwares,
			Clock.systemUTC());

	@Test
	void startsOnlyPendingRecordings() {
		Software software = new Software("app", 10);
		software.replaceAppPackages(List.of("com.app"));
		AnalysisBatch batch = new AnalysisBatch(software.getId(), null, null, null, 1);
		JfrRecording recording = new JfrRecording(batch.getId(), "a.jfr", "b", "k", "s", 1);
		when(recordings.findById(recording.getId())).thenReturn(Optional.of(recording));
		when(batches.findById(batch.getId())).thenReturn(Optional.of(batch));
		when(softwares.findById(software.getId())).thenReturn(Optional.of(software));

		assertThat(service.start(recording.getId())).get()
				.satisfies(t -> assertThat(t.appPackages()).containsExactly("com.app"));
		assertThat(recording.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
		assertThat(service.start(recording.getId())).isEmpty();
		assertThat(service.start(UUID.randomUUID())).isEmpty();

		recording.setStatus(AnalysisStatus.PENDING);
		when(softwares.findById(software.getId())).thenReturn(Optional.empty());
		assertThat(service.start(recording.getId())).get().satisfies(t -> assertThat(t.appPackages()).isEmpty());

		service.fail(recording.getId(), ErrorCode.TIMEOUT, "x");
		assertThat(recording.getErrorCode()).isEqualTo(ErrorCode.TIMEOUT);
		service.fail(UUID.randomUUID(), ErrorCode.TIMEOUT, "x");
	}
}
