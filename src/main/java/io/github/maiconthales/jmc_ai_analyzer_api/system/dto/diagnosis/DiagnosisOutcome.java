package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.CoverageItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import java.util.List;

/** Resultado completo do diagnóstico. */
public record DiagnosisOutcome(List<DiagnosisItem> items, List<CoverageItem> coverage, int healthScore,
		String suggestedCommand) {
}
