package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.DomainResults;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.TimedValue;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.MetricSample;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.MetricType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.EventView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.SeriesDownsampler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Uso e configuração do heap e coletores ({@code jdk.GCHeapSummary}, configurações de GC e heap). */
public final class HeapExtractor {

	private HeapExtractor() {
	}

	public static DomainResults.Heap extract(ExtractionContext ctx) {
		List<TimedValue> used = new ArrayList<>();
		List<TimedValue> committed = new ArrayList<>();
		JfrEvents.forEach(ctx.items(), JfrEventTypes.HEAP_SUMMARY, e -> {
			Long heapUsed = e.bytes("heapUsed");
			if (heapUsed != null) {
				used.add(new TimedValue(e.start(), heapUsed));
			}
			Long heapCommitted = e.bytes("heapSpace:committedSize");
			if (heapCommitted != null) {
				committed.add(new TimedValue(e.start(), heapCommitted));
			}
		});
		AtomicReference<EventView> heapConf = new AtomicReference<>();
		JfrEvents.forEach(ctx.items(), JfrEventTypes.HEAP_CONFIGURATION, e -> heapConf.compareAndSet(null, e));
		AtomicReference<EventView> gcConf = new AtomicReference<>();
		JfrEvents.forEach(ctx.items(), JfrEventTypes.GC_CONFIGURATION, e -> gcConf.compareAndSet(null, e));

		List<MetricSample> series = new ArrayList<>();
		series.addAll(SeriesDownsampler.downsample(MetricType.HEAP_USED, used, ctx.maxPoints()));
		series.addAll(SeriesDownsampler.downsample(MetricType.HEAP_COMMITTED, committed, ctx.maxPoints()));
		Long peak = used.stream().mapToDouble(TimedValue::value).max().stream().mapToObj(v -> (long) v).findFirst()
				.orElse(null);
		EventView heap = heapConf.get();
		EventView gc = gcConf.get();
		return new DomainResults.Heap(!used.isEmpty(),
				heap == null ? null : heap.bytes("initialSize"),
				heap == null ? null : heap.bytes("maxSize"),
				peak,
				gc == null ? null : gc.text("youngCollector"),
				gc == null ? null : gc.text("oldCollector"),
				gc == null ? null : gc.number("parallelGCThreads"),
				series);
	}
}
