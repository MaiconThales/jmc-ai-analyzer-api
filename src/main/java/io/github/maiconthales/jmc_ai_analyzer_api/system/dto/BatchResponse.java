package io.github.maiconthales.jmc_ai_analyzer_api.system.dto;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.BatchStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lote com o estado de cada gravação (resposta do upload e do status). */
public record BatchResponse(
		UUID batchId,
		String software,
		String softwareVersion,
		Environment environment,
		String description,
		BatchStatus status,
		Instant createdAt,
		Instant finishedAt,
		List<RecordingStatusResponse> recordings) {
}
