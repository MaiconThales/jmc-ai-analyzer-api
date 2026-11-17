package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.DomainResults;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.TimedValue;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.MetricSample;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.MetricType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.SeriesDownsampler;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.Stats;
import java.util.ArrayList;
import java.util.List;

/** CPU da JVM e da máquina ({@code jdk.CPULoad}). */
public final class CpuExtractor {

	private CpuExtractor() {
	}

	public static DomainResults.Cpu extract(ExtractionContext ctx) {
		List<TimedValue> user = new ArrayList<>();
		List<TimedValue> system = new ArrayList<>();
		List<TimedValue> machine = new ArrayList<>();
		List<Double> jvmTotal = new ArrayList<>();
		JfrEvents.forEach(ctx.items(), JfrEventTypes.CPU_LOAD, e -> {
			double u = orZero(e.fraction("jvmUser"));
			double s = orZero(e.fraction("jvmSystem"));
			user.add(new TimedValue(e.start(), u));
			system.add(new TimedValue(e.start(), s));
			machine.add(new TimedValue(e.start(), orZero(e.fraction("machineTotal"))));
			jvmTotal.add(u + s);
		});
		if (user.isEmpty()) {
			return DomainResults.Cpu.empty();
		}
		List<Double> machineValues = machine.stream().map(TimedValue::value).toList();
		List<MetricSample> series = new ArrayList<>();
		series.addAll(SeriesDownsampler.downsample(MetricType.CPU_JVM_USER, user, ctx.maxPoints()));
		series.addAll(SeriesDownsampler.downsample(MetricType.CPU_JVM_SYSTEM, system, ctx.maxPoints()));
		series.addAll(SeriesDownsampler.downsample(MetricType.CPU_MACHINE, machine, ctx.maxPoints()));
		return new DomainResults.Cpu(true, Stats.avg(jvmTotal), Stats.max(jvmTotal), Stats.avg(machineValues),
				Stats.max(machineValues), series, machineValues);
	}

	private static double orZero(Double value) {
		return value == null ? 0 : value;
	}
}
