package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.TimedValue;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.MetricSample;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.MetricType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * Reduz uma série a no máximo N pontos, dividindo o período em janelas iguais e guardando a média e o máximo
 * de cada janela. Janelas vazias são omitidas.
 */
public final class SeriesDownsampler {

	private SeriesDownsampler() {
	}

	public static List<MetricSample> downsample(MetricType metric, List<TimedValue> values, int maxPoints) {
		if (values.isEmpty()) {
			return List.of();
		}
		List<TimedValue> sorted = values.stream().sorted(Comparator.comparing(TimedValue::ts)).toList();
		if (sorted.size() <= maxPoints) {
			return sorted.stream().map(v -> sample(metric, v.ts(), v.value(), v.value())).toList();
		}
		Instant start = sorted.get(0).ts();
		long spanNanos = Math.max(1, Duration.between(start, sorted.get(sorted.size() - 1).ts()).toNanos());
		TreeMap<Integer, double[]> buckets = new TreeMap<>();
		for (TimedValue v : sorted) {
			long offset = Duration.between(start, v.ts()).toNanos();
			int index = (int) Math.min(maxPoints - 1L, offset * maxPoints / spanNanos);
			double[] acc = buckets.computeIfAbsent(index, i -> new double[] {0, 0, Double.NEGATIVE_INFINITY});
			acc[0] += v.value();
			acc[1]++;
			acc[2] = Math.max(acc[2], v.value());
		}
		List<MetricSample> result = new ArrayList<>(buckets.size());
		buckets.forEach((index, acc) -> result.add(sample(metric,
				start.plusNanos(spanNanos * index / maxPoints), acc[0] / acc[1], acc[2])));
		return result;
	}

	private static MetricSample sample(MetricType metric, Instant ts, double avg, double max) {
		MetricSample sample = new MetricSample();
		sample.setMetric(metric);
		sample.setTs(ts);
		sample.setAvgValue(avg);
		sample.setMaxValue(max);
		return sample;
	}
}
