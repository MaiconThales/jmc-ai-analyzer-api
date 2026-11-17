package io.github.maiconthales.jmc_ai_analyzer_api.system.mapper;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.dashboard.AppFrameResolver;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.AllocationItemView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.AllocationView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.CoverageItemView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.CoverageView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.CpuView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.DiagnosisView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.ExceptionItemView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.ExceptionsView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.FindingView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.GcPauseView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.GcView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.HeapView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.HotMethodView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.HotMethodsView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.IoItemView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.IoTotalView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.IoView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.JvmView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.LeakItemView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.LeaksView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.LocationView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.LockView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.LocksView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.MetadataView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.Point;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.ThreadCpuItemView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.ThreadCpuView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.ThreadsView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AllocationHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.CoverageItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisLocation;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.ExceptionHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.GcPause;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.HotMethod;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.IoHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LeakSuspect;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LockContention;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.MetricSample;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RecordingSummary;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RuleFinding;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.StackFrame;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.ThreadCpu;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.IoType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.MetricType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Converte os dados persistidos de uma gravação nos blocos do dashboard, limitando o top-N e resolvendo o
 * frame da aplicação.
 *
 * @param resolver    resolvedor do frame da aplicação
 * @param appPackages pacotes da aplicação do software
 * @param topN        itens de top-N devolvidos
 */
public record DashboardMapper(AppFrameResolver resolver, List<String> appPackages, int topN) {

	public static final Set<MetricType> CPU = Set.of(MetricType.CPU_JVM_USER, MetricType.CPU_JVM_SYSTEM, MetricType.CPU_MACHINE);
	public static final Set<MetricType> HEAP = Set.of(MetricType.HEAP_USED, MetricType.HEAP_COMMITTED);
	public static final Set<MetricType> THREADS = Set.of(MetricType.THREADS_ACTIVE, MetricType.THREADS_DAEMON);

	public static Set<String> domains(RecordingSummary summary) {
		if (summary == null || summary.getDomainsAvailable() == null || summary.getDomainsAvailable().isBlank()) {
			return Set.of();
		}
		return Set.copyOf(Arrays.asList(summary.getDomainsAvailable().split(",")));
	}

	public static Map<MetricType, List<Point>> series(List<MetricSample> samples, Set<MetricType> metrics) {
		Map<MetricType, List<Point>> series = new EnumMap<>(MetricType.class);
		metrics.stream().sorted().forEach(m -> series.put(m, new java.util.ArrayList<>()));
		samples.stream().filter(s -> metrics.contains(s.getMetric()))
				.sorted(Comparator.comparing(MetricSample::getTs))
				.forEach(s -> series.get(s.getMetric()).add(new Point(s.getTs(), s.getAvgValue(), s.getMaxValue())));
		return series;
	}

	public static MetadataView metadata(JfrRecording r) {
		return new MetadataView(r.getRecordingStart(), r.getRecordingEnd(), r.getDurationMs(), r.getEventCount(),
				new JvmView(r.getJvmName(), r.getJvmVersion(), r.getJvmArgs(), r.getJavaArgs()), r.getOsName(),
				r.getCpuCores(), r.getPhysicalMemoryBytes());
	}

	public static <S> CpuView<S> cpu(RecordingSummary s, Set<String> domains, S series) {
		return new CpuView<>(domains.contains("CPU"), s.getCpuJvmAvg(), s.getCpuJvmMax(), s.getCpuMachineAvg(),
				s.getCpuMachineMax(), series);
	}

	public static <S> HeapView<S> heap(RecordingSummary s, Set<String> domains, S series) {
		return new HeapView<>(domains.contains("HEAP"), s.getHeapInitialBytes(), s.getHeapMaxBytes(),
				s.getHeapUsedPeakBytes(), s.getGcYoungCollector(), s.getGcOldCollector(), series);
	}

	public static <S> ThreadsView<S> threads(RecordingSummary s, Set<String> domains, S series) {
		return new ThreadsView<>(domains.contains("THREADS"), s.getThreadPeak(), s.getThreadDaemonPeak(),
				s.getThreadsStarted(), s.getThreadsEnded(), series);
	}

	public GcView gc(RecordingSummary s, Set<String> domains, List<GcPause> pauses) {
		return new GcView(domains.contains("GC"), s.getGcCount(), s.getGcPauseTotalMs(), s.getGcPauseAvgMs(),
				s.getGcPauseP95Ms(), s.getGcPauseMaxMs(), s.getGcPausePct(),
				pauses.stream().sorted(Comparator.comparingInt(GcPause::getRank)).limit(topN)
						.map(p -> pause(p, null)).toList());
	}

	public static GcPauseView pause(GcPause p, UUID recordingId) {
		return new GcPauseView(p.getGcId(), p.getCollector(), p.getCause(), p.getStartTime(), p.getDurationMs(),
				recordingId);
	}

	public LocksView locks(Set<String> domains, List<LockContention> locks) {
		return new LocksView(domains.contains("LOCKS"), locks.stream()
				.sorted(Comparator.comparingInt(LockContention::getRank)).limit(topN).map(this::lock).toList());
	}

	public LockView lock(LockContention l) {
		return new LockView(l.getLockType(), l.getClassName(), l.getEventCount(), l.getTotalMs(), l.getMaxMs(),
				frames(l.getStackFrames()), app(l.getStackFrames()));
	}

	public HotMethodsView hotMethods(RecordingSummary s, Set<String> domains, List<HotMethod> methods) {
		long total = s.getExecutionSamplesTotal() == null ? 0 : s.getExecutionSamplesTotal();
		return new HotMethodsView(domains.contains("HOT_METHODS"), total, methods.stream()
				.sorted(Comparator.comparingInt(HotMethod::getRank)).limit(topN).map(this::hotMethod).toList());
	}

	public HotMethodView hotMethod(HotMethod h) {
		return new HotMethodView(h.getMethodSignature(), h.getSamples(), h.getPercent(), frames(h.getStackFrames()),
				app(h.getStackFrames()));
	}

	public AllocationView allocation(RecordingSummary s, Set<String> domains, List<AllocationHotspot> items) {
		return new AllocationView(domains.contains("ALLOCATION"), s.getAllocationRateBytesPerSecond(), items.stream()
				.sorted(Comparator.comparingInt(AllocationHotspot::getRank)).limit(topN).map(this::allocation)
				.toList());
	}

	public AllocationItemView allocation(AllocationHotspot a) {
		return new AllocationItemView(a.getClassName(), a.getWeightBytes(), a.getSamples(), frames(a.getStackFrames()),
				app(a.getStackFrames()));
	}

	public ExceptionsView exceptions(RecordingSummary s, Set<String> domains, List<ExceptionHotspot> items) {
		return new ExceptionsView(domains.contains("EXCEPTIONS"), s.getThrowablesTotal(), items.stream()
				.sorted(Comparator.comparingInt(ExceptionHotspot::getRank)).limit(topN).map(this::exception).toList());
	}

	public ExceptionItemView exception(ExceptionHotspot e) {
		return new ExceptionItemView(e.getExceptionClass(), e.getEventCount(), e.getSampleMessage(),
				frames(e.getStackFrames()), app(e.getStackFrames()));
	}

	public IoView io(Set<String> domains, List<IoHotspot> items) {
		return new IoView(domains.contains("IO"), ioTotals(items), items.stream()
				.sorted(Comparator.comparingInt(IoHotspot::getRank)).limit(topN).map(DashboardMapper::io).toList());
	}

	public static IoItemView io(IoHotspot i) {
		return new IoItemView(i.getIoType(), i.getTarget(), i.getBytes(), i.getEventCount(), i.getTotalMs(),
				i.getMaxMs());
	}

	public static Map<IoType, IoTotalView> ioTotals(List<IoHotspot> items) {
		Map<IoType, IoTotalView> totals = new EnumMap<>(IoType.class);
		for (IoHotspot i : items) {
			totals.merge(i.getIoType(), new IoTotalView(i.getBytes(), i.getEventCount(), i.getTotalMs()),
					(a, b) -> new IoTotalView(a.bytes() + b.bytes(), a.count() + b.count(), a.totalMs() + b.totalMs()));
		}
		return totals;
	}

	public LeaksView leaks(Set<String> domains, List<LeakSuspect> items) {
		return new LeaksView(domains.contains("LEAK"), items.stream()
				.sorted(Comparator.comparingInt(LeakSuspect::getRank)).limit(topN).map(this::leak).toList());
	}

	public LeakItemView leak(LeakSuspect l) {
		return new LeakItemView(l.getClassName(), l.getSamples(), l.getMaxAgeMs(), l.getMaxArraySize(),
				frames(l.getStackFrames()), app(l.getStackFrames()));
	}

	public ThreadCpuView threadCpu(Set<String> domains, List<ThreadCpu> items) {
		return new ThreadCpuView(domains.contains("THREAD_CPU"), items.stream()
				.sorted(Comparator.comparingInt(ThreadCpu::getRank)).limit(topN).map(DashboardMapper::threadCpu)
				.toList());
	}

	public static ThreadCpuItemView threadCpu(ThreadCpu t) {
		return new ThreadCpuItemView(t.getThreadName(), t.getCpuAvg(), t.getCpuMax());
	}

	/** Achados ordenados por severidade (WARNING primeiro) e score decrescente, nulos por último. */
	public static List<FindingView> findings(List<RuleFinding> findings, Function<RuleFinding, List<UUID>> recordingIds) {
		return findings.stream().sorted(FINDING_ORDER)
				.map(f -> new FindingView(f.getRuleId(), f.getTopic(), f.getRuleName(), f.getSeverity(), f.getScore(),
						f.getSummary(), f.getExplanation(), f.getSolution(), recordingIds.apply(f)))
				.toList();
	}

	public static final Comparator<RuleFinding> FINDING_ORDER = Comparator
			.comparing(RuleFinding::getSeverity, Comparator.reverseOrder())
			.thenComparing(RuleFinding::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
			.thenComparing(RuleFinding::getRuleId);

	public DiagnosisView diagnosis(DiagnosisItem d, List<UUID> recordingIds) {
		return new DiagnosisView(d.getPriority(), d.getCategory(), d.getSource(), d.getScore(), d.getTitle(),
				d.getEvidence(), location(d.getLocation()), d.getAction(), d.getDomainRef(), recordingIds);
	}

	public LocationView location(DiagnosisLocation location) {
		if (location == null) {
			return null;
		}
		return new LocationView(location.thread(), location.target(), frames(location.stackFrames()),
				app(location.stackFrames()));
	}

	public static CoverageView coverage(List<CoverageItem> items, String suggestedCommand) {
		return new CoverageView(items.stream().sorted(Comparator.comparing(CoverageItem::getDomain))
				.map(c -> new CoverageItemView(c.getDomain(), c.getStatus(), c.getReason(), c.getRecommendation()))
				.toList(), suggestedCommand);
	}

	public StackFrame app(List<StackFrame> frames) {
		return resolver.resolve(frames, appPackages);
	}

	private static List<StackFrame> frames(List<StackFrame> frames) {
		return frames == null ? List.of() : frames;
	}

	/** Agrupa por chave preservando a ordem de entrada. */
	public static <T, K> Map<K, List<T>> groupBy(List<T> items, Function<T, K> key) {
		return items.stream().collect(Collectors.groupingBy(key, java.util.LinkedHashMap::new, Collectors.toList()));
	}
}
