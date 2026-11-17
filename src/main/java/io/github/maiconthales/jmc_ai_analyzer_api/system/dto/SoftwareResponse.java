package io.github.maiconthales.jmc_ai_analyzer_api.system.dto;

import java.time.Instant;
import java.util.List;

/** Software cadastrado, com retenção, pacotes da aplicação e resumo do histórico. */
public record SoftwareResponse(
		String name,
		int retentionDays,
		List<String> appPackages,
		long batchCount,
		Instant lastBatchAt,
		String lastVersion) {
}
