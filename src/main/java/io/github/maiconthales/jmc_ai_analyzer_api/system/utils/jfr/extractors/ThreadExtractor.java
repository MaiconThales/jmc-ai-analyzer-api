package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.DomainResults;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.TimedValue;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.MetricSample;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.MetricType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.SeriesDownsampler;
import java.util.ArrayList;
import java.util.List;

/** Threads ativas, daemon, criadas e encerradas. */
public final class ThreadExtractor {

	private ThreadExtractor() {
	}

	public static DomainResults.Threads extract(ExtractionContext ctx) {
		List<TimedValue> active = new ArrayList<>();
		List<TimedValue> daemon = new ArrayList<>();
		long[] peak = {Long.MIN_VALUE};
		JfrEvents.forEach(ctx.items(), JfrEventTypes.THREAD_STATISTICS, e -> {
			Long activeCount = e.number("activeCount");
			Long daemonCount = e.number("daemonCount");
			Long peakCount = e.number("peakCount");
			if (activeCount != null) {
				active.add(new TimedValue(e.start(), activeCount));
				peak[0] = Math.max(peak[0], peakCount == null ? activeCount : peakCount);
			}
			if (daemonCount != null) {
				daemon.add(new TimedValue(e.start(), daemonCount));
			}
		});
		int started = (int) JfrEvents.count(ctx.items(), JfrEventTypes.THREAD_START);
		int ended = (int) JfrEvents.count(ctx.items(), JfrEventTypes.THREAD_END);
		List<MetricSample> series = new ArrayList<>();
		series.addAll(SeriesDownsampler.downsample(MetricType.THREADS_ACTIVE, active, ctx.maxPoints()));
		series.addAll(SeriesDownsampler.downsample(MetricType.THREADS_DAEMON, daemon, ctx.maxPoints()));
		Integer daemonPeak = daemon.stream().mapToDouble(TimedValue::value).max().stream()
				.mapToObj(v -> (int) v).findFirst().orElse(null);
		List<Double> activeValues = active.stream().sorted(java.util.Comparator.comparing(TimedValue::ts))
				.map(TimedValue::value).toList();
		return new DomainResults.Threads(!active.isEmpty(), active.isEmpty() ? null : (int) peak[0], daemonPeak,
				started, ended, series, activeValues);
	}
}
