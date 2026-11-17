package io.github.maiconthales.jmc_ai_analyzer_api.system.dto;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.BatchStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import java.time.Instant;
import java.util.UUID;

/** Linha do histórico de lotes. */
public record HistoryItemResponse(
		UUID batchId,
		String software,
		String softwareVersion,
		Environment environment,
		String description,
		Instant createdAt,
		int fileCount,
		BatchStatus status,
		long warningFindings,
		Integer healthScore,
		long highPriorityItems) {
}
