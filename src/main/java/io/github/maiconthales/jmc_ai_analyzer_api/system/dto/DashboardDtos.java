package io.github.maiconthales.jmc_ai_analyzer_api.system.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.StackFrame;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.CoverageDomain;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.CoverageStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.FindingSeverity;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.IoType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.LockType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.MetricType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Blocos dos dashboards. Os blocos com séries são genéricos: na gravação a série é
 * {@code Map<MetricType, List<Point>>}; no consolidado é indexada por {@code recordingId}.
 */
public final class DashboardDtos {

	private DashboardDtos() {
	}

	/** Dashboard de uma gravação. */
	public record RecordingDashboard(
			UUID recordingId, UUID batchId, String software, String softwareVersion, String originalName,
			String parserVersion, Integer healthScore, List<DiagnosisView> diagnosis, CoverageView coverage,
			MetadataView metadata, CpuView<Map<MetricType, List<Point>>> cpu,
			HeapView<Map<MetricType, List<Point>>> heap, GcView gc,
			ThreadsView<Map<MetricType, List<Point>>> threads, LocksView locks, HotMethodsView hotMethods,
			AllocationView allocation, ExceptionsView exceptions, IoView io, LeaksView leakSuspects,
			ThreadCpuView threadCpu, List<FindingView> findings) {
	}

	/** Dashboard consolidado de um lote. */
	public record BatchDashboard(
			UUID batchId, String software, String softwareVersion, Environment environment, String description,
			Integer healthScore, List<DiagnosisView> diagnosis, CoverageView coverage, BatchMetadataView metadata,
			CpuView<Map<UUID, Map<MetricType, List<Point>>>> cpu, HeapView<Map<UUID, Map<MetricType, List<Point>>>> heap,
			GcView gc, ThreadsView<Map<UUID, Map<MetricType, List<Point>>>> threads, LocksView locks,
			HotMethodsView hotMethods, AllocationView allocation, ExceptionsView exceptions, IoView io,
			LeaksView leakSuspects, ThreadCpuView threadCpu, List<FindingView> findings,
			List<ComparisonRow> comparison, List<RecordingRef> includedRecordings,
			List<RecordingRef> excludedRecordings) {
	}

	public record Point(Instant ts, double avg, double max) {
	}

	public record MetadataView(Instant start, Instant end, Long durationMs, Long eventCount, JvmView jvm, String os,
			Integer cpuCores, Long physicalMemoryBytes) {
	}

	public record JvmView(String name, String version, String jvmArgs, String javaArgs) {
	}

	public record BatchMetadataView(int recordingCount, long totalDurationMs, long totalEventCount,
			List<String> jvmVersions) {
	}

	public record CpuView<S>(boolean available, Double jvmAvg, Double jvmMax, Double machineAvg, Double machineMax,
			S series) {
	}

	public record HeapView<S>(boolean available, Long initialBytes, Long maxBytes, Long usedPeakBytes,
			String youngCollector, String oldCollector, S series) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GcPauseView(Long gcId, String collector, String cause, Instant start, double durationMs,
			UUID recordingId) {
	}

	public record GcView(boolean available, Integer count, Double pauseTotalMs, Double pauseAvgMs, Double pauseP95Ms,
			Double pauseMaxMs, Double pausePct, List<GcPauseView> topPauses) {
	}

	public record ThreadsView<S>(boolean available, Integer peak, Integer daemonPeak, Integer started, Integer ended,
			S series) {
	}

	public record LockView(LockType type, String className, long count, double totalMs, double maxMs,
			List<StackFrame> stackFrames, StackFrame appFrame) {
	}

	public record LocksView(boolean available, List<LockView> top) {
	}

	public record HotMethodView(String method, long samples, double percent, List<StackFrame> stackFrames,
			StackFrame appFrame) {
	}

	public record HotMethodsView(boolean available, long totalSamples, List<HotMethodView> top) {
	}

	public record AllocationItemView(String className, long weightBytes, long samples, List<StackFrame> stackFrames,
			StackFrame appFrame) {
	}

	public record AllocationView(boolean available, Double rateBytesPerSec, List<AllocationItemView> top) {
	}

	public record ExceptionItemView(String className, long count, String sampleMessage, List<StackFrame> stackFrames,
			StackFrame appFrame) {
	}

	public record ExceptionsView(boolean available, Long throwablesTotal, List<ExceptionItemView> top) {
	}

	public record IoTotalView(long bytes, long count, double totalMs) {
	}

	public record IoItemView(IoType type, String target, long bytes, long count, double totalMs, double maxMs) {
	}

	/** {@code totals} somam os alvos persistidos (top-N), não todo o I/O da gravação. */
	public record IoView(boolean available, Map<IoType, IoTotalView> totals, List<IoItemView> top) {
	}

	public record LeakItemView(String className, long samples, Long maxAgeMs, Long maxArraySize,
			List<StackFrame> stackFrames, StackFrame appFrame) {
	}

	public record LeaksView(boolean available, List<LeakItemView> top) {
	}

	public record ThreadCpuItemView(String threadName, double cpuAvg, double cpuMax) {
	}

	public record ThreadCpuView(boolean available, List<ThreadCpuItemView> top) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FindingView(String ruleId, String topic, String name, FindingSeverity severity, Double score,
			String summary, String explanation, String solution, List<UUID> recordingIds) {
	}

	public record LocationView(String thread, String target, List<StackFrame> stackFrames, StackFrame appFrame) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record DiagnosisView(Priority priority, DiagnosisCategory category, String source, int score, String title,
			String evidence, LocationView location, String action, String domainRef, List<UUID> recordingIds) {
	}

	public record CoverageItemView(CoverageDomain domain, CoverageStatus status, String reason,
			String recommendation) {
	}

	public record CoverageView(List<CoverageItemView> items, String suggestedCommand) {
	}

	public record ComparisonRow(UUID recordingId, String originalName, Long durationMs, Double cpuJvmAvg,
			Double gcPauseMaxMs, Integer healthScore, long highPriorityItems) {
	}

	public record RecordingRef(UUID id, String originalName, AnalysisStatus status) {
	}
}
