package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.DomainResults;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.IoHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.IoType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.EventView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

/** Operações de I/O lentas (acima do limiar dos eventos), por tipo e alvo. */
public final class IoExtractor {

	static final String UNKNOWN_TARGET = "(desconhecido)";

	private IoExtractor() {
	}

	public static DomainResults.Io extract(ExtractionContext ctx) {
		Map<Key, Acc> map = new HashMap<>();
		collect(ctx, JfrEventTypes.FILE_READ, IoType.FILE_READ, "bytesRead", IoExtractor::fileTarget, map);
		collect(ctx, JfrEventTypes.FILE_WRITE, IoType.FILE_WRITE, "bytesWritten", IoExtractor::fileTarget, map);
		collect(ctx, JfrEventTypes.SOCKET_READ, IoType.SOCKET_READ, "bytesRead", IoExtractor::socketTarget, map);
		collect(ctx, JfrEventTypes.SOCKET_WRITE, IoType.SOCKET_WRITE, "bytesWritten", IoExtractor::socketTarget, map);
		Map<IoType, DomainResults.IoTotal> totals = new EnumMap<>(IoType.class);
		map.forEach((key, acc) -> totals.merge(key.type(), new DomainResults.IoTotal(acc.bytes, acc.count, acc.totalMs),
				(a, b) -> new DomainResults.IoTotal(a.bytes() + b.bytes(), a.count() + b.count(),
						a.totalMs() + b.totalMs())));
		List<IoHotspot> top = map.entrySet().stream()
				.sorted(Comparator.comparingDouble((Map.Entry<Key, Acc> e) -> e.getValue().totalMs).reversed())
				.limit(ctx.topN())
				.map(e -> e.getValue().toEntity(e.getKey()))
				.toList();
		IntStream.range(0, top.size()).forEach(i -> top.get(i).setRank(i + 1));
		return new DomainResults.Io(!map.isEmpty(), totals, top);
	}

	private static void collect(ExtractionContext ctx, String eventType, IoType type, String bytesField,
			Function<EventView, String> target, Map<Key, Acc> map) {
		JfrEvents.forEach(ctx.items(), eventType, e -> {
			Acc acc = map.computeIfAbsent(new Key(type, target.apply(e)), k -> new Acc());
			Long bytes = e.bytes(bytesField);
			double duration = e.durationMs();
			acc.bytes += bytes == null || bytes < 0 ? 0 : bytes;
			acc.count++;
			acc.totalMs += duration;
			acc.maxMs = Math.max(acc.maxMs, duration);
		});
	}

	static String fileTarget(EventView e) {
		String path = e.text("path");
		return path == null || path.isBlank() ? UNKNOWN_TARGET : path;
	}

	static String socketTarget(EventView e) {
		String host = e.text("host");
		if (host == null || host.isBlank()) {
			host = e.text("address");
		}
		Long port = e.number("port");
		String base = host == null || host.isBlank() ? UNKNOWN_TARGET : host;
		return port == null ? base : base + ":" + port;
	}

	private record Key(IoType type, String target) {
	}

	private static final class Acc {
		private long bytes;
		private long count;
		private double totalMs;
		private double maxMs;

		IoHotspot toEntity(Key key) {
			IoHotspot hotspot = new IoHotspot();
			hotspot.setIoType(key.type());
			hotspot.setTarget(key.target());
			hotspot.setBytes(bytes);
			hotspot.setEventCount(count);
			hotspot.setTotalMs(totalMs);
			hotspot.setMaxMs(maxMs);
			return hotspot;
		}
	}
}
