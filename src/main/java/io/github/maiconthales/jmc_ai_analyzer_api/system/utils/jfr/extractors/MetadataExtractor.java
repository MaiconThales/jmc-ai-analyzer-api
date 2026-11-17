package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingMetadata;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.EventView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.IRange;
import org.openjdk.jmc.common.unit.UnitLookup;

/**
 * Metadados da gravação. Variáveis de ambiente, propriedades de sistema e processos nunca são lidos.
 */
public final class MetadataExtractor {

	static final int MAX_OS_LENGTH = 500;

	private MetadataExtractor() {
	}

	public static RecordingMetadata extract(IItemCollection items) {
		Instant start = null;
		Instant end = null;
		for (IRange<IQuantity> range : items.getUnfilteredTimeRanges()) {
			Instant rangeStart = toInstant(range.getStart());
			Instant rangeEnd = toInstant(range.getEnd());
			start = start == null || rangeStart.isBefore(start) ? rangeStart : start;
			end = end == null || rangeEnd.isAfter(end) ? rangeEnd : end;
		}
		long durationMs = start == null ? 0 : Duration.between(start, end).toMillis();
		long eventCount = 0;
		for (var iterable : items) {
			eventCount += iterable.getItemCount();
		}
		AtomicReference<EventView> jvm = first(items, JfrEventTypes.JVM_INFORMATION);
		AtomicReference<EventView> os = first(items, JfrEventTypes.OS_INFORMATION);
		AtomicReference<EventView> cpu = first(items, JfrEventTypes.CPU_INFORMATION);
		AtomicReference<EventView> memory = first(items, JfrEventTypes.PHYSICAL_MEMORY);
		return new RecordingMetadata(start, end, durationMs, eventCount,
				text(jvm, "jvmName"), text(jvm, "jvmVersion"), text(jvm, "jvmArguments"), text(jvm, "javaArguments"),
				osName(text(os, "osVersion")), toInt(number(cpu, "cores")), toInt(number(cpu, "hwThreads")),
				memory.get() == null ? null : memory.get().bytes("totalSize"));
	}

	static String osName(String osVersion) {
		if (osVersion == null) {
			return null;
		}
		String firstLine = osVersion.strip().lines().findFirst().orElse("").strip();
		return firstLine.length() > MAX_OS_LENGTH ? firstLine.substring(0, MAX_OS_LENGTH) : firstLine;
	}

	private static AtomicReference<EventView> first(IItemCollection items, String type) {
		AtomicReference<EventView> ref = new AtomicReference<>();
		JfrEvents.forEach(items, type, e -> ref.compareAndSet(null, e));
		return ref;
	}

	private static String text(AtomicReference<EventView> ref, String field) {
		return ref.get() == null ? null : ref.get().text(field);
	}

	private static Long number(AtomicReference<EventView> ref, String field) {
		return ref.get() == null ? null : ref.get().number(field);
	}

	private static Integer toInt(Long value) {
		return value == null ? null : value.intValue();
	}

	private static Instant toInstant(IQuantity quantity) {
		long nanos = quantity.clampedLongValueIn(UnitLookup.EPOCH_NS);
		return Instant.ofEpochSecond(Math.floorDiv(nanos, 1_000_000_000L), Math.floorMod(nanos, 1_000_000_000L));
	}
}
