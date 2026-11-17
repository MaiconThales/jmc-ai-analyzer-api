package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr;

import java.util.Collection;
import java.util.List;

/** Estatísticas simples sobre listas de valores. */
public final class Stats {

	private Stats() {
	}

	public static Double avg(Collection<Double> values) {
		return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
	}

	public static Double max(Collection<Double> values) {
		return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
	}

	/** Percentil pelo método do posto mais próximo ({@code ceil(p * n) - 1}). */
	public static double percentile(List<Double> values, double percentile) {
		if (values.isEmpty()) {
			return 0;
		}
		List<Double> sorted = values.stream().sorted().toList();
		int index = (int) Math.ceil(percentile * sorted.size()) - 1;
		return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
	}
}
