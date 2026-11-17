package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.DomainResults;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LockContention;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.LockType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.FrameCounter;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.StackFrames;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Bloqueios por (tipo, classe do monitor ou do objeto estacionado), ordenados pelo tempo total. */
public final class LockExtractor {

	static final String NO_CLASS = "(sem classe)";

	private LockExtractor() {
	}

	public static DomainResults.Locks extract(ExtractionContext ctx) {
		Map<Key, Acc> map = new HashMap<>();
		collect(ctx, JfrEventTypes.MONITOR_ENTER, LockType.MONITOR_ENTER, "monitorClass", map);
		collect(ctx, JfrEventTypes.MONITOR_WAIT, LockType.MONITOR_WAIT, "monitorClass", map);
		collect(ctx, JfrEventTypes.THREAD_PARK, LockType.THREAD_PARK, "parkedClass", map);
		List<LockContention> top = map.entrySet().stream()
				.sorted(Comparator.comparingDouble((Map.Entry<Key, Acc> e) -> e.getValue().totalMs).reversed())
				.limit(ctx.topN())
				.map(e -> e.getValue().toEntity(e.getKey()))
				.toList();
		IntStream.range(0, top.size()).forEach(i -> top.get(i).setRank(i + 1));
		return new DomainResults.Locks(!map.isEmpty(), top);
	}

	private static void collect(ExtractionContext ctx, String eventType, LockType lockType, String classField,
			Map<Key, Acc> map) {
		JfrEvents.forEach(ctx.items(), eventType, e -> {
			String className = e.className(classField);
			Acc acc = map.computeIfAbsent(new Key(lockType, className == null ? NO_CLASS : className), k -> new Acc());
			double duration = e.durationMs();
			acc.count++;
			acc.totalMs += duration;
			acc.maxMs = Math.max(acc.maxMs, duration);
			acc.frames.add(StackFrames.top(e.stackTrace()));
		});
	}

	private record Key(LockType type, String className) {
	}

	private static final class Acc {
		private long count;
		private double totalMs;
		private double maxMs;
		private final FrameCounter frames = new FrameCounter();

		LockContention toEntity(Key key) {
			LockContention lock = new LockContention();
			lock.setLockType(key.type());
			lock.setClassName(key.className());
			lock.setEventCount(count);
			lock.setTotalMs(totalMs);
			lock.setMaxMs(maxMs);
			lock.setStackFrames(frames.mostFrequent());
			return lock;
		}
	}
}
