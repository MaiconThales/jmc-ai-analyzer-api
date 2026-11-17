package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AllocationHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.ExceptionHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.GcPause;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.HotMethod;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.IoHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LeakSuspect;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LockContention;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.MetricSample;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.ThreadCpu;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.IoType;
import java.util.List;
import java.util.Map;

/** Resultados de cada extrator. {@code available = false} significa que a gravação não tinha os eventos. */
public final class DomainResults {

	private DomainResults() {
	}

	public record Cpu(boolean available, Double jvmAvg, Double jvmMax, Double machineAvg, Double machineMax,
			List<MetricSample> series, List<Double> machineValues) {

		public static Cpu empty() {
			return new Cpu(false, null, null, null, null, List.of(), List.of());
		}
	}

	public record Heap(boolean available, Long initialBytes, Long maxBytes, Long usedPeakBytes, String youngCollector,
			String oldCollector, Long parallelGcThreads, List<MetricSample> series) {
	}

	public record Gc(boolean available, int count, double pauseTotalMs, double pauseAvgMs, double pauseP95Ms,
			double pauseMaxMs, double pausePct, List<GcPause> topPauses) {

		public static Gc empty() {
			return new Gc(false, 0, 0, 0, 0, 0, 0, List.of());
		}
	}

	public record Threads(boolean available, Integer peak, Integer daemonPeak, int started, int ended,
			List<MetricSample> series, List<Double> activeValues) {
	}

	public record Locks(boolean available, List<LockContention> top) {
	}

	public record HotMethods(boolean available, long totalSamples, List<HotMethod> top) {
	}

	public record Allocation(boolean available, Double rateBytesPerSecond, List<AllocationHotspot> top) {
	}

	public record Exceptions(boolean available, Long throwablesTotal, List<ExceptionHotspot> top) {
	}

	public record IoTotal(long bytes, long count, double totalMs) {
	}

	public record Io(boolean available, Map<IoType, IoTotal> totals, List<IoHotspot> top) {
	}

	public record Leaks(boolean available, List<LeakSuspect> top) {
	}

	public record ThreadCpuLoad(boolean available, List<ThreadCpu> top) {
	}
}
