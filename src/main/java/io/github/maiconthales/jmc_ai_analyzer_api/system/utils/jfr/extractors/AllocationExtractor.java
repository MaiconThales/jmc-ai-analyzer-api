package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.DomainResults;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AllocationHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.FrameCounter;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.StackFrames;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Alocação por classe. Usa {@code jdk.ObjectAllocationSample}; sem ele, usa os eventos de TLAB.
 */
public final class AllocationExtractor {

	static final String NO_CLASS = "(sem classe)";

	private AllocationExtractor() {
	}

	public static DomainResults.Allocation extract(ExtractionContext ctx) {
		Map<String, Acc> map = new HashMap<>();
		if (JfrEvents.has(ctx.items(), JfrEventTypes.ALLOCATION_SAMPLE)) {
			collect(ctx, JfrEventTypes.ALLOCATION_SAMPLE, "weight", map);
		} else {
			collect(ctx, JfrEventTypes.ALLOCATION_IN_NEW_TLAB, "tlabSize", map);
			collect(ctx, JfrEventTypes.ALLOCATION_OUTSIDE_TLAB, "allocationSize", map);
		}
		long total = map.values().stream().mapToLong(a -> a.weight).sum();
		double seconds = ctx.metadata().durationSeconds();
		List<AllocationHotspot> top = map.entrySet().stream()
				.sorted(Comparator.comparingLong((Map.Entry<String, Acc> e) -> e.getValue().weight).reversed())
				.limit(ctx.topN())
				.map(e -> e.getValue().toEntity(e.getKey()))
				.toList();
		IntStream.range(0, top.size()).forEach(i -> top.get(i).setRank(i + 1));
		return new DomainResults.Allocation(!map.isEmpty(), map.isEmpty() || seconds <= 0 ? null : total / seconds, top);
	}

	private static void collect(ExtractionContext ctx, String eventType, String weightField, Map<String, Acc> map) {
		JfrEvents.forEach(ctx.items(), eventType, e -> {
			String className = e.className("objectClass");
			Acc acc = map.computeIfAbsent(className == null ? NO_CLASS : className, k -> new Acc());
			Long weight = e.bytes(weightField);
			acc.weight += weight == null ? 0 : weight;
			acc.samples++;
			acc.frames.add(StackFrames.top(e.stackTrace()));
		});
	}

	private static final class Acc {
		private long weight;
		private long samples;
		private final FrameCounter frames = new FrameCounter();

		AllocationHotspot toEntity(String className) {
			AllocationHotspot hotspot = new AllocationHotspot();
			hotspot.setClassName(className);
			hotspot.setWeightBytes(weight);
			hotspot.setSamples(samples);
			hotspot.setStackFrames(frames.mostFrequent());
			return hotspot;
		}
	}
}
