package io.github.maiconthales.jmc_ai_analyzer_api.system.dto;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
import java.time.Instant;
import java.util.UUID;

/** Situação de uma gravação dentro de um lote. */
public record RecordingStatusResponse(
		UUID id,
		String originalName,
		long sizeBytes,
		AnalysisStatus status,
		Instant processingStartedAt,
		Instant processingFinishedAt,
		ErrorCode errorCode,
		String errorMessage) {
}
