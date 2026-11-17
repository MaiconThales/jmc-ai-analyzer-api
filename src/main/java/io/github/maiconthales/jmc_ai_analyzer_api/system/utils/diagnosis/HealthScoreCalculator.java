package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.DiagnosisProperties;
import java.util.Collection;

/** Nota de saúde: {@code max(0, 100 − peso_ALTA × n_ALTA − peso_MEDIA × n_MEDIA − peso_BAIXA × n_BAIXA)}. */
public final class HealthScoreCalculator {

	private HealthScoreCalculator() {
	}

	public static int calculate(Collection<DiagnosisItem> items, DiagnosisProperties properties) {
		int penalty = 0;
		for (DiagnosisItem item : items) {
			penalty += switch (item.getPriority()) {
				case ALTA -> properties.healthWeights().alta();
				case MEDIA -> properties.healthWeights().media();
				case BAIXA -> properties.healthWeights().baixa();
			};
		}
		return Math.max(0, 100 - penalty);
	}
}
