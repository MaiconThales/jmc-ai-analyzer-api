package io.github.maiconthales.jmc_ai_analyzer_api.system.services.dashboard;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.AllocationItemView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.AllocationView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.BatchMetadataView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.ComparisonRow;
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
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.IoView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.LeakItemView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.LeaksView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.LockView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.LocksView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.Point;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.ThreadCpuItemView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.ThreadCpuView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.ThreadsView;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.dashboard.ConsolidationInput;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.dashboard.RecordingDataSet;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AllocationHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.CoverageItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisLocation;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.ExceptionHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.HotMethod;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.IoHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LeakSuspect;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LockContention;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RecordingSummary;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RuleFinding;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.ThreadCpu;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.CoverageDomain;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.MetricType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.mapper.DashboardMapper;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.diagnosis.DiagnosisService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Regras de consolidação de um lote (RF-06): soma por chave nos top-N, médias ponderadas pela duração,
 * união dos diagnósticos e pior situação da cobertura. Recebe somente gravações {@code COMPLETED}.
 */
@Service
public class ConsolidationService {


	public BatchMetadataView metadata(ConsolidationInput in) {
		long duration = in.recordings().stream().mapToLong(r -> r.getDurationMs() == null ? 0 : r.getDurationMs()).sum();
		long events = in.recordings().stream().mapToLong(r -> r.getEventCount() == null ? 0 : r.getEventCount()).sum();
		List<String> versions = in.recordings().stream().map(JfrRecording::getJvmVersion).filter(Objects::nonNull)
				.distinct().sorted().toList();
		return new BatchMetadataView(in.recordings().size(), duration, events, versions);
	}

	public Map<UUID, Map<MetricType, List<Point>>> series(ConsolidationInput in, Set<MetricType> metrics) {
		Map<UUID, Map<MetricType, List<Point>>> series = new LinkedHashMap<>();
		in.ids().forEach(id -> series.put(id,
				DashboardMapper.series(RecordingDataSet.of(in.data().samples(), id), metrics)));
		return series;
	}

	public CpuView<Map<UUID, Map<MetricType, List<Point>>>> cpu(ConsolidationInput in) {
		return new CpuView<>(in.anyDomain("CPU"), weighted(in, RecordingSummary::getCpuJvmAvg),
				max(in, RecordingSummary::getCpuJvmMax), weighted(in, RecordingSummary::getCpuMachineAvg),
				max(in, RecordingSummary::getCpuMachineMax), series(in, DashboardMapper.CPU));
	}

	public HeapView<Map<UUID, Map<MetricType, List<Point>>>> heap(ConsolidationInput in) {
		List<RecordingSummary> s = in.summaries();
		return new HeapView<>(in.anyDomain("HEAP"), maxLong(s, RecordingSummary::getHeapInitialBytes),
				maxLong(s, RecordingSummary::getHeapMaxBytes), maxLong(s, RecordingSummary::getHeapUsedPeakBytes),
				first(s, RecordingSummary::getGcYoungCollector), first(s, RecordingSummary::getGcOldCollector),
				series(in, DashboardMapper.HEAP));
	}

	public GcView gc(ConsolidationInput in) {
		List<RecordingSummary> s = in.summaries();
		int count = s.stream().mapToInt(x -> x.getGcCount() == null ? 0 : x.getGcCount()).sum();
		double total = s.stream().mapToDouble(x -> x.getGcPauseTotalMs() == null ? 0 : x.getGcPauseTotalMs()).sum();
		long duration = in.recordings().stream().mapToLong(r -> r.getDurationMs() == null ? 0 : r.getDurationMs()).sum();
		List<GcPauseView> top = new ArrayList<>();
		for (UUID id : in.ids()) {
			RecordingDataSet.of(in.data().gcPauses(), id).forEach(p -> top.add(DashboardMapper.pause(p, id)));
		}
		top.sort(Comparator.comparingDouble(GcPauseView::durationMs).reversed());
		boolean available = in.anyDomain("GC");
		return new GcView(available, available ? count : null, available ? total : null,
				available && count > 0 ? total / count : null, max(in, RecordingSummary::getGcPauseP95Ms),
				max(in, RecordingSummary::getGcPauseMaxMs), available && duration > 0 ? total * 100 / duration : null,
				top.stream().limit(in.mapper().topN()).toList());
	}

	public ThreadsView<Map<UUID, Map<MetricType, List<Point>>>> threads(ConsolidationInput in) {
		List<RecordingSummary> s = in.summaries();
		return new ThreadsView<>(in.anyDomain("THREADS"), maxInt(s, RecordingSummary::getThreadPeak),
				maxInt(s, RecordingSummary::getThreadDaemonPeak), sumInt(s, RecordingSummary::getThreadsStarted),
				sumInt(s, RecordingSummary::getThreadsEnded), series(in, DashboardMapper.THREADS));
	}

	public LocksView locks(ConsolidationInput in) {
		Map<String, List<LockContention>> groups = DashboardMapper.groupBy(in.all(in.data().locks()),
				l -> l.getLockType() + "|" + l.getClassName());
		List<LockView> merged = groups.values().stream().map(list -> {
			LockContention best = list.stream().max(Comparator.comparingDouble(LockContention::getTotalMs)).orElseThrow();
			return new LockView(best.getLockType(), best.getClassName(), list.stream().mapToLong(LockContention::getEventCount).sum(),
					list.stream().mapToDouble(LockContention::getTotalMs).sum(),
					list.stream().mapToDouble(LockContention::getMaxMs).max().orElse(0), best.getStackFrames(),
					in.mapper().app(best.getStackFrames()));
		}).sorted(Comparator.comparingDouble(LockView::totalMs).reversed()).limit(in.mapper().topN()).toList();
		return new LocksView(in.anyDomain("LOCKS"), merged);
	}

	public HotMethodsView hotMethods(ConsolidationInput in) {
		long total = in.summaries().stream()
				.mapToLong(s -> s.getExecutionSamplesTotal() == null ? 0 : s.getExecutionSamplesTotal()).sum();
		Map<String, List<HotMethod>> groups = DashboardMapper.groupBy(in.all(in.data().hotMethods()),
				HotMethod::getMethodSignature);
		List<HotMethodView> merged = groups.entrySet().stream().map(e -> {
			HotMethod best = e.getValue().stream().max(Comparator.comparingLong(HotMethod::getSamples)).orElseThrow();
			long samples = e.getValue().stream().mapToLong(HotMethod::getSamples).sum();
			return new HotMethodView(e.getKey(), samples, total > 0 ? samples * 100.0 / total : 0,
					best.getStackFrames(), in.mapper().app(best.getStackFrames()));
		}).sorted(Comparator.comparingLong(HotMethodView::samples).reversed().thenComparing(HotMethodView::method))
				.limit(in.mapper().topN()).toList();
		return new HotMethodsView(in.anyDomain("HOT_METHODS"), total, merged);
	}

	public AllocationView allocation(ConsolidationInput in) {
		Map<String, List<AllocationHotspot>> groups = DashboardMapper.groupBy(in.all(in.data().allocations()),
				AllocationHotspot::getClassName);
		List<AllocationItemView> merged = groups.entrySet().stream().map(e -> {
			AllocationHotspot best = e.getValue().stream()
					.max(Comparator.comparingLong(AllocationHotspot::getWeightBytes)).orElseThrow();
			return new AllocationItemView(e.getKey(), e.getValue().stream().mapToLong(AllocationHotspot::getWeightBytes)
					.sum(), e.getValue().stream().mapToLong(AllocationHotspot::getSamples).sum(), best.getStackFrames(),
					in.mapper().app(best.getStackFrames()));
		}).sorted(Comparator.comparingLong(AllocationItemView::weightBytes).reversed()).limit(in.mapper().topN())
				.toList();
		return new AllocationView(in.anyDomain("ALLOCATION"),
				weighted(in, RecordingSummary::getAllocationRateBytesPerSecond), merged);
	}

	public ExceptionsView exceptions(ConsolidationInput in) {
		Map<String, List<ExceptionHotspot>> groups = DashboardMapper.groupBy(in.all(in.data().exceptions()),
				ExceptionHotspot::getExceptionClass);
		List<ExceptionItemView> merged = groups.entrySet().stream().map(e -> {
			ExceptionHotspot best = e.getValue().stream()
					.max(Comparator.comparingLong(ExceptionHotspot::getEventCount)).orElseThrow();
			return new ExceptionItemView(e.getKey(), e.getValue().stream().mapToLong(ExceptionHotspot::getEventCount)
					.sum(), best.getSampleMessage(), best.getStackFrames(), in.mapper().app(best.getStackFrames()));
		}).sorted(Comparator.comparingLong(ExceptionItemView::count).reversed()).limit(in.mapper().topN()).toList();
		Long total = in.summaries().stream().map(RecordingSummary::getThrowablesTotal).filter(Objects::nonNull)
				.reduce(Long::sum).orElse(null);
		return new ExceptionsView(in.anyDomain("EXCEPTIONS"), total, merged);
	}

	public IoView io(ConsolidationInput in) {
		List<IoHotspot> all = in.all(in.data().io());
		Map<String, List<IoHotspot>> groups = DashboardMapper.groupBy(all, i -> i.getIoType() + "|" + i.getTarget());
		List<IoItemView> merged = groups.values().stream().map(list -> new IoItemView(list.get(0).getIoType(),
				list.get(0).getTarget(), list.stream().mapToLong(IoHotspot::getBytes).sum(),
				list.stream().mapToLong(IoHotspot::getEventCount).sum(),
				list.stream().mapToDouble(IoHotspot::getTotalMs).sum(),
				list.stream().mapToDouble(IoHotspot::getMaxMs).max().orElse(0)))
				.sorted(Comparator.comparingDouble(IoItemView::totalMs).reversed()).limit(in.mapper().topN()).toList();
		return new IoView(in.anyDomain("IO"), DashboardMapper.ioTotals(all), merged);
	}

	public LeaksView leaks(ConsolidationInput in) {
		Map<String, List<LeakSuspect>> groups = DashboardMapper.groupBy(in.all(in.data().leaks()),
				LeakSuspect::getClassName);
		List<LeakItemView> merged = groups.entrySet().stream().map(e -> {
			LeakSuspect best = e.getValue().stream().max(Comparator.comparingLong(LeakSuspect::getSamples)).orElseThrow();
			return new LeakItemView(e.getKey(), e.getValue().stream().mapToLong(LeakSuspect::getSamples).sum(),
					maxLong(e.getValue(), LeakSuspect::getMaxAgeMs), maxLong(e.getValue(), LeakSuspect::getMaxArraySize),
					best.getStackFrames(), in.mapper().app(best.getStackFrames()));
		}).sorted(Comparator.comparingLong(LeakItemView::samples).reversed().thenComparing(LeakItemView::className))
				.limit(in.mapper().topN()).toList();
		return new LeaksView(in.anyDomain("LEAK"), merged);
	}

	public ThreadCpuView threadCpu(ConsolidationInput in) {
		Map<String, List<ThreadCpu>> groups = DashboardMapper.groupBy(in.all(in.data().threadCpu()),
				ThreadCpu::getThreadName);
		List<ThreadCpuItemView> merged = groups.entrySet().stream().map(e -> new ThreadCpuItemView(e.getKey(),
				e.getValue().stream().mapToDouble(ThreadCpu::getCpuAvg).max().orElse(0),
				e.getValue().stream().mapToDouble(ThreadCpu::getCpuMax).max().orElse(0)))
				.sorted(Comparator.comparingDouble(ThreadCpuItemView::cpuAvg).reversed()
						.thenComparing(ThreadCpuItemView::threadName))
				.limit(in.mapper().topN()).toList();
		return new ThreadCpuView(in.anyDomain("THREAD_CPU"), merged);
	}

	/** Por {@code ruleId}: maior severidade, maior score e gravações afetadas. */
	public List<FindingView> findings(ConsolidationInput in) {
		Map<String, RuleFinding> best = new LinkedHashMap<>();
		Map<String, List<UUID>> recordings = new LinkedHashMap<>();
		for (UUID id : in.ids()) {
			for (RuleFinding f : RecordingDataSet.of(in.data().findings(), id)) {
				best.merge(f.getRuleId(), f, (a, b) -> DashboardMapper.FINDING_ORDER.compare(a, b) <= 0 ? a : b);
				recordings.computeIfAbsent(f.getRuleId(), k -> new ArrayList<>()).add(id);
			}
		}
		return DashboardMapper.findings(new ArrayList<>(best.values()), f -> recordings.get(f.getRuleId()));
	}

	/** União por (origem, localização): maior prioridade, maior score e gravações afetadas. */
	public List<DiagnosisView> diagnosis(ConsolidationInput in) {
		record Key(String source, DiagnosisLocation location) {
		}
		Map<Key, DiagnosisItem> best = new LinkedHashMap<>();
		Map<Key, List<UUID>> recordings = new LinkedHashMap<>();
		for (UUID id : in.ids()) {
			for (DiagnosisItem d : RecordingDataSet.of(in.data().diagnosis(), id)) {
				Key key = new Key(d.getSource(), d.getLocation());
				best.merge(key, d, (a, b) -> DiagnosisService.ORDER.compare(a, b) <= 0 ? a : b);
				recordings.computeIfAbsent(key, k -> new ArrayList<>()).add(id);
			}
		}
		return best.entrySet().stream().sorted(Map.Entry.comparingByValue(DiagnosisService.ORDER))
				.map(e -> in.mapper().diagnosis(e.getValue(), recordings.get(e.getKey()))).toList();
	}

	/** Por domínio, a pior situação entre as gravações; o comando sugerido vem da gravação com pior cobertura. */
	public CoverageView coverage(ConsolidationInput in) {
		Map<CoverageDomain, CoverageItem> worst = new EnumMap<>(CoverageDomain.class);
		for (CoverageItem item : in.all(in.data().coverage())) {
			worst.merge(item.getDomain(), item, (a, b) -> b.getStatus().compareTo(a.getStatus()) > 0 ? b : a);
		}
		String command = in.summaries().stream().map(RecordingSummary::getSuggestedCommand).filter(Objects::nonNull)
				.max(Comparator.comparingInt(String::length)).orElse(null);
		return DashboardMapper.coverage(new ArrayList<>(worst.values()), command);
	}

	public Integer healthScore(ConsolidationInput in) {
		return in.summaries().stream().map(RecordingSummary::getHealthScore).filter(Objects::nonNull)
				.min(Integer::compare).orElse(null);
	}

	public List<ComparisonRow> comparison(ConsolidationInput in) {
		return in.recordings().stream().map(r -> {
			RecordingSummary s = in.data().summaries().get(r.getId());
			long high = RecordingDataSet.of(in.data().diagnosis(), r.getId()).stream()
					.filter(d -> d.getPriority() == Priority.ALTA).count();
			return new ComparisonRow(r.getId(), r.getOriginalName(), r.getDurationMs(),
					s == null ? null : s.getCpuJvmAvg(), s == null ? null : s.getGcPauseMaxMs(),
					s == null ? null : s.getHealthScore(), high);
		}).toList();
	}

	/** Média ponderada pela duração das gravações; nula se nenhuma gravação tiver o valor. */
	static Double weighted(ConsolidationInput in, Function<RecordingSummary, Double> value) {
		double sum = 0;
		double weight = 0;
		for (UUID id : in.ids()) {
			RecordingSummary s = in.data().summaries().get(id);
			Double v = s == null ? null : value.apply(s);
			if (v != null) {
				double w = Math.max(1, in.duration(id));
				sum += v * w;
				weight += w;
			}
		}
		return weight == 0 ? null : sum / weight;
	}

	static Double max(ConsolidationInput in, Function<RecordingSummary, Double> value) {
		return in.summaries().stream().map(value).filter(Objects::nonNull).max(Double::compare).orElse(null);
	}

	static <T> Long maxLong(List<T> items, Function<T, Long> value) {
		return items.stream().map(value).filter(Objects::nonNull).max(Long::compare).orElse(null);
	}

	static Integer maxInt(List<RecordingSummary> items, Function<RecordingSummary, Integer> value) {
		return items.stream().map(value).filter(Objects::nonNull).max(Integer::compare).orElse(null);
	}

	static Integer sumInt(List<RecordingSummary> items, Function<RecordingSummary, Integer> value) {
		return items.stream().map(value).filter(Objects::nonNull).reduce(Integer::sum).orElse(null);
	}

	static String first(List<RecordingSummary> items, Function<RecordingSummary, String> value) {
		return items.stream().map(value).filter(Objects::nonNull).findFirst().orElse(null);
	}
}
