package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.ProcessingTarget;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AnalysisBatchRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.JfrRecordingRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.SoftwareRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transições de estado de uma gravação em transações curtas. */
@Service
@RequiredArgsConstructor
public class RecordingStateService {

	static final int MAX_ERROR = 1000;

	private final JfrRecordingRepository recordingRepository;
	private final AnalysisBatchRepository batchRepository;
	private final SoftwareRepository softwareRepository;
	private final Clock clock;

	/**
	 * Marca a gravação como PROCESSING se ela estiver PENDING.
	 *
	 * @return vazio se a gravação não existe ou não está pendente
	 */
	@Transactional
	public Optional<ProcessingTarget> start(UUID recordingId) {
		Optional<JfrRecording> found = recordingRepository.findById(recordingId)
				.filter(r -> r.getStatus() == AnalysisStatus.PENDING);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		JfrRecording recording = found.get();
		recording.setStatus(AnalysisStatus.PROCESSING);
		recording.setProcessingStartedAt(Instant.now(clock));
		AnalysisBatch batch = batchRepository.findById(recording.getBatchId()).orElseThrow();
		List<String> appPackages = softwareRepository.findById(batch.getSoftwareId())
				.map(s -> s.appPackageList()).orElse(List.of());
		return Optional.of(new ProcessingTarget(recording.getId(), batch.getId(), recording.getBlobKey(),
				batch.getEnvironment(), appPackages));
	}

	@Transactional
	public void fail(UUID recordingId, ErrorCode code, String message) {
		recordingRepository.findById(recordingId).ifPresent(recording -> {
			recording.setStatus(AnalysisStatus.FAILED);
			recording.setErrorCode(code);
			recording.setErrorMessage(truncate(message));
			recording.setProcessingFinishedAt(Instant.now(clock));
		});
	}

	static String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() > MAX_ERROR ? message.substring(0, MAX_ERROR) : message;
	}
}
