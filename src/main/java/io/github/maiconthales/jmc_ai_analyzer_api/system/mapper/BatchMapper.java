package io.github.maiconthales.jmc_ai_analyzer_api.system.mapper;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.BatchResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.RecordingStatusResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import java.util.List;

/** Conversão de lote e gravações para DTO. */
public final class BatchMapper {

	private BatchMapper() {
	}

	public static BatchResponse toResponse(AnalysisBatch batch, String software, List<JfrRecording> recordings) {
		return new BatchResponse(batch.getId(), software, batch.getSoftwareVersion(), batch.getEnvironment(),
				batch.getDescription(), batch.getStatus(), batch.getCreatedAt(), batch.getFinishedAt(),
				recordings.stream().map(BatchMapper::toResponse).toList());
	}

	public static RecordingStatusResponse toResponse(JfrRecording r) {
		return new RecordingStatusResponse(r.getId(), r.getOriginalName(), r.getSizeBytes(), r.getStatus(),
				r.getProcessingStartedAt(), r.getProcessingFinishedAt(), r.getErrorCode(), r.getErrorMessage());
	}
}
