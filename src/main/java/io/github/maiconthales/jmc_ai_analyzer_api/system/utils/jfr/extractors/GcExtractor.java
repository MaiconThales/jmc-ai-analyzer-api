package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.DomainResults;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.GcPause;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.Stats;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/** Coletas de lixo ({@code jdk.GarbageCollection}). A pausa considerada é {@code sumOfPauses}. */
public final class GcExtractor {

	static final double P95 = 0.95;

	private GcExtractor() {
	}

	public static DomainResults.Gc extract(ExtractionContext ctx) {
		List<GcPause> pauses = new ArrayList<>();
		List<Double> longest = new ArrayList<>();
		JfrEvents.forEach(ctx.items(), JfrEventTypes.GARBAGE_COLLECTION, e -> {
			Double sum = e.millis("sumOfPauses");
			GcPause pause = new GcPause();
			pause.setGcId(e.number("gcId"));
			pause.setCollector(e.text("name"));
			pause.setCause(e.text("cause"));
			pause.setStartTime(e.start());
			pause.setDurationMs(sum == null ? e.durationMs() : sum);
			pauses.add(pause);
			Double longestPause = e.millis("longestPause");
			longest.add(longestPause == null ? pause.getDurationMs() : longestPause);
		});
		if (pauses.isEmpty()) {
			return DomainResults.Gc.empty();
		}
		List<Double> durations = pauses.stream().map(GcPause::getDurationMs).toList();
		double total = durations.stream().mapToDouble(Double::doubleValue).sum();
		double recordingMs = ctx.metadata().durationMs();
		List<GcPause> top = pauses.stream().sorted(Comparator.comparingDouble(GcPause::getDurationMs).reversed())
				.limit(ctx.topN()).toList();
		IntStream.range(0, top.size()).forEach(i -> top.get(i).setRank(i + 1));
		return new DomainResults.Gc(true, pauses.size(), total, total / pauses.size(), Stats.percentile(durations, P95),
				Stats.max(longest), recordingMs > 0 ? total * 100 / recordingMs : 0, top);
	}
}
