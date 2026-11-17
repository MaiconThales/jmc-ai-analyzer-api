package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.DomainResults;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.ThreadCpu;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** CPU por thread ({@code jdk.ThreadCPULoad}), em fração de 0 a 1. */
public final class ThreadCpuExtractor {

	static final String UNNAMED = "(sem nome)";

	private ThreadCpuExtractor() {
	}

	public static DomainResults.ThreadCpuLoad extract(ExtractionContext ctx) {
		Map<String, double[]> map = new HashMap<>();
		JfrEvents.forEach(ctx.items(), JfrEventTypes.THREAD_CPU_LOAD, e -> {
			String thread = e.thread();
			double load = orZero(e.fraction("user")) + orZero(e.fraction("system"));
			double[] acc = map.computeIfAbsent(thread == null ? UNNAMED : thread, k -> new double[3]);
			acc[0] += load;
			acc[1]++;
			acc[2] = Math.max(acc[2], load);
		});
		List<ThreadCpu> top = map.entrySet().stream()
				.map(e -> toEntity(e.getKey(), e.getValue()))
				.sorted(Comparator.comparingDouble(ThreadCpu::getCpuAvg).reversed()
						.thenComparing(ThreadCpu::getThreadName))
				.limit(ctx.topN())
				.toList();
		IntStream.range(0, top.size()).forEach(i -> top.get(i).setRank(i + 1));
		return new DomainResults.ThreadCpuLoad(!map.isEmpty(), top);
	}

	private static ThreadCpu toEntity(String thread, double[] acc) {
		ThreadCpu cpu = new ThreadCpu();
		cpu.setThreadName(thread);
		cpu.setCpuAvg(acc[0] / acc[1]);
		cpu.setCpuMax(acc[2]);
		return cpu;
	}

	private static double orZero(Double value) {
		return value == null ? 0 : value;
	}
}
