package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.DomainResults;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.TimedValue;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.ExceptionHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.FrameCounter;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.StackFrames;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Exceções e erros lançados. Quando {@code jdk.JavaExceptionThrow} existe, ele já inclui os erros e
 * {@code jdk.JavaErrorThrow} é ignorado para não contar em dobro.
 */
public final class ExceptionExtractor {

	static final int MAX_MESSAGE = 500;
	static final String NO_CLASS = "(sem classe)";

	private ExceptionExtractor() {
	}

	public static DomainResults.Exceptions extract(ExtractionContext ctx) {
		Map<String, Acc> map = new HashMap<>();
		String throwType = JfrEvents.has(ctx.items(), JfrEventTypes.EXCEPTION_THROW)
				? JfrEventTypes.EXCEPTION_THROW
				: JfrEventTypes.ERROR_THROW;
		JfrEvents.forEach(ctx.items(), throwType, e -> {
			String className = e.className("thrownClass");
			Acc acc = map.computeIfAbsent(className == null ? NO_CLASS : className, k -> new Acc());
			acc.count++;
			if (acc.message == null) {
				acc.message = truncate(e.text("message"));
			}
			acc.frames.add(StackFrames.top(e.stackTrace()));
		});
		List<TimedValue> statistics = new ArrayList<>();
		JfrEvents.forEach(ctx.items(), JfrEventTypes.EXCEPTION_STATISTICS, e -> {
			Long throwables = e.number("throwables");
			if (throwables != null) {
				statistics.add(new TimedValue(e.start(), throwables));
			}
		});
		Long total = throwablesTotal(statistics);
		List<ExceptionHotspot> top = map.entrySet().stream()
				.sorted(Comparator.comparingLong((Map.Entry<String, Acc> e) -> e.getValue().count).reversed())
				.limit(ctx.topN())
				.map(e -> e.getValue().toEntity(e.getKey()))
				.toList();
		IntStream.range(0, top.size()).forEach(i -> top.get(i).setRank(i + 1));
		return new DomainResults.Exceptions(!map.isEmpty() || total != null, total, top);
	}

	static Long throwablesTotal(List<TimedValue> statistics) {
		if (statistics.isEmpty()) {
			return null;
		}
		List<TimedValue> sorted = statistics.stream().sorted(Comparator.comparing(TimedValue::ts)).toList();
		return (long) (sorted.get(sorted.size() - 1).value() - sorted.get(0).value());
	}

	static String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) : message;
	}

	private static final class Acc {
		private long count;
		private String message;
		private final FrameCounter frames = new FrameCounter();

		ExceptionHotspot toEntity(String className) {
			ExceptionHotspot hotspot = new ExceptionHotspot();
			hotspot.setExceptionClass(className);
			hotspot.setEventCount(count);
			hotspot.setSampleMessage(message);
			hotspot.setStackFrames(frames.mostFrequent());
			return hotspot;
		}
	}
}
