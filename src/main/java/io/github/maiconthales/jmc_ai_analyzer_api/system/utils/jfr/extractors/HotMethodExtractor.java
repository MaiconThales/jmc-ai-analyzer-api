package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.DomainResults;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.HotMethod;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.FrameCounter;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.StackFrames;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Métodos mais amostrados no topo da pilha ({@code jdk.ExecutionSample}). */
public final class HotMethodExtractor {

	private HotMethodExtractor() {
	}

	public static DomainResults.HotMethods extract(ExtractionContext ctx) {
		Map<String, Long> samples = new HashMap<>();
		Map<String, FrameCounter> frames = new HashMap<>();
		long[] total = {0};
		JfrEvents.forEach(ctx.items(), JfrEventTypes.EXECUTION_SAMPLE, e -> {
			total[0]++;
			String method = StackFrames.topMethod(e.stackTrace());
			if (method != null) {
				samples.merge(method, 1L, Long::sum);
				frames.computeIfAbsent(method, k -> new FrameCounter()).add(StackFrames.top(e.stackTrace()));
			}
		});
		List<HotMethod> top = samples.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
				.limit(ctx.topN())
				.map(e -> toEntity(e.getKey(), e.getValue(), total[0], frames.get(e.getKey())))
				.toList();
		IntStream.range(0, top.size()).forEach(i -> top.get(i).setRank(i + 1));
		return new DomainResults.HotMethods(total[0] > 0, total[0], top);
	}

	private static HotMethod toEntity(String method, long count, long total, FrameCounter frames) {
		HotMethod hot = new HotMethod();
		hot.setMethodSignature(method);
		hot.setSamples(count);
		hot.setPercent(count * 100.0 / total);
		hot.setStackFrames(frames.mostFrequent());
		return hot;
	}

	/** Comparador exposto para a consolidação. */
	public static Comparator<HotMethod> bySamples() {
		return Comparator.comparingLong(HotMethod::getSamples).reversed();
	}
}
