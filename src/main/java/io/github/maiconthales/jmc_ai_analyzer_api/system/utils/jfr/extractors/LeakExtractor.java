package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.DomainResults;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LeakSuspect;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.FrameCounter;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.StackFrames;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Suspeitos de vazamento: objetos antigos ainda vivos ({@code jdk.OldObjectSample}). */
public final class LeakExtractor {

	static final String NO_CLASS = "(sem classe)";

	private LeakExtractor() {
	}

	public static DomainResults.Leaks extract(ExtractionContext ctx) {
		Map<String, Acc> map = new HashMap<>();
		JfrEvents.forEach(ctx.items(), JfrEventTypes.OLD_OBJECT_SAMPLE, e -> {
			String className = e.className("object");
			Acc acc = map.computeIfAbsent(className == null ? NO_CLASS : className, k -> new Acc());
			acc.samples++;
			Double age = e.millis("objectAge");
			if (age != null) {
				acc.maxAgeMs = Math.max(acc.maxAgeMs == null ? 0 : acc.maxAgeMs, age.longValue());
			}
			Long elements = e.number("arrayElements");
			if (elements != null && elements >= 0) {
				acc.maxArraySize = Math.max(acc.maxArraySize == null ? 0 : acc.maxArraySize, elements);
			}
			acc.frames.add(StackFrames.top(e.stackTrace()));
		});
		List<LeakSuspect> top = map.entrySet().stream()
				.sorted(Comparator.comparingLong((Map.Entry<String, Acc> e) -> e.getValue().samples).reversed()
						.thenComparing(Map.Entry.comparingByKey()))
				.limit(ctx.topN())
				.map(e -> e.getValue().toEntity(e.getKey()))
				.toList();
		IntStream.range(0, top.size()).forEach(i -> top.get(i).setRank(i + 1));
		return new DomainResults.Leaks(!map.isEmpty(), top);
	}

	private static final class Acc {
		private long samples;
		private Long maxAgeMs;
		private Long maxArraySize;
		private final FrameCounter frames = new FrameCounter();

		LeakSuspect toEntity(String className) {
			LeakSuspect leak = new LeakSuspect();
			leak.setClassName(className);
			leak.setSamples(samples);
			leak.setMaxAgeMs(maxAgeMs);
			leak.setMaxArraySize(maxArraySize);
			leak.setStackFrames(frames.mostFrequent());
			return leak;
		}
	}
}
