package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.*;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.IoType;

import java.util.List;
import java.util.Map;

/**
 * Resultados de cada extrator. {@code available = false} significa que a gravação não tinha os eventos.
 */
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

        public static Heap empty() {
            return new Heap(false, null, null, null, null, null, null, List.of());
        }
    }

    public record Gc(boolean available, int count, double pauseTotalMs, double pauseAvgMs, double pauseP95Ms,
                     double pauseMaxMs, double pausePct, List<GcPause> topPauses) {

        public static Gc empty() {
            return new Gc(false, 0, 0, 0, 0, 0, 0, List.of());
        }
    }

    public record Threads(boolean available, Integer peak, Integer daemonPeak, int started, int ended,
                          List<MetricSample> series, List<Double> activeValues) {

        public static Threads empty() {
            return new Threads(false, null, null, 0, 0, List.of(), List.of());
        }
    }

    public record Locks(boolean available, List<LockContention> top) {

        public static Locks empty() {
            return new Locks(false, List.of());
        }
    }

    public record HotMethods(boolean available, long totalSamples, List<HotMethod> top) {
        
        public static HotMethods empty() {
            return new HotMethods(false, 0, List.of());
        }
    }

    public record Allocation(boolean available, Double rateBytesPerSecond, List<AllocationHotspot> top) {

        public static Allocation empty() {
            return new Allocation(false, null, List.of());
        }
    }

    public record Exceptions(boolean available, Long throwablesTotal, List<ExceptionHotspot> top) {

        public static Exceptions empty() {
            return new Exceptions(false, null, List.of());
        }
    }

    public record IoTotal(long bytes, long count, double totalMs) {

        public static IoTotal empty() {
            return new IoTotal(0, 0, 0);
        }
    }

    public record Io(boolean available, Map<IoType, IoTotal> totals, List<IoHotspot> top) {

        public static Io empty() {
            return new Io(false, Map.of(), List.of());
        }
    }

    public record Leaks(boolean available, List<LeakSuspect> top) {

        public static Leaks empty() {
            return new Leaks(false, List.of());
        }
    }

    public record ThreadCpuLoad(boolean available, List<ThreadCpu> top) {

        public static ThreadCpuLoad empty() {
            return new ThreadCpuLoad(false, List.of());
        }
    }
}
