package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.BatchStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AnalysisBatchRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.JfrRecordingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recalcula o estado do lote a partir das gravações, com lock no lote para evitar corrida. */
@Service
@RequiredArgsConstructor
public class BatchStatusService {

	private final AnalysisBatchRepository batchRepository;
	private final JfrRecordingRepository recordingRepository;
	private final Clock clock;

	@Transactional
	public void refresh(UUID batchId) {
		batchRepository.findForUpdate(batchId).ifPresent(batch -> apply(batch,
				recordingRepository.findByBatchIdOrderByCreatedAtAsc(batchId).stream().map(JfrRecording::getStatus)
						.toList()));
	}

	void apply(AnalysisBatch batch, Collection<AnalysisStatus> statuses) {
		BatchStatus status = derive(statuses);
		batch.setStatus(status);
		batch.setFinishedAt(status == BatchStatus.PROCESSING ? null : Instant.now(clock));
	}

	public static BatchStatus derive(Collection<AnalysisStatus> statuses) {
		if (statuses.isEmpty() || statuses.stream()
				.anyMatch(s -> s == AnalysisStatus.PENDING || s == AnalysisStatus.PROCESSING)) {
			return BatchStatus.PROCESSING;
		}
		boolean anyCompleted = statuses.contains(AnalysisStatus.COMPLETED);
		boolean anyFailed = statuses.contains(AnalysisStatus.FAILED);
		if (anyCompleted && anyFailed) {
			return BatchStatus.PARTIAL;
		}
		return anyCompleted ? BatchStatus.COMPLETED : BatchStatus.FAILED;
	}
}
