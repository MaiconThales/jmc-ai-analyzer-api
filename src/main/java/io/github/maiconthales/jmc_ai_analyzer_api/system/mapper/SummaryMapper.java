package io.github.maiconthales.jmc_ai_analyzer_api.system.mapper;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisOutcome;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingExtraction;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RecordingSummary;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Montagem do resumo numérico. */
public final class SummaryMapper {

	/** Domínios com dados, na ordem do dashboard. */
	public static String domainsAvailable(RecordingExtraction x) {
		Set<String> domains = new LinkedHashSet<>();
		add(domains, x.cpu().available(), "CPU");
		add(domains, x.heap().available(), "HEAP");
		add(domains, x.gc().available(), "GC");
		add(domains, x.threads().available(), "THREADS");
		add(domains, x.locks().available(), "LOCKS");
		add(domains, x.hotMethods().available(), "HOT_METHODS");
		add(domains, x.allocation().available(), "ALLOCATION");
		add(domains, x.exceptions().available(), "EXCEPTIONS");
		add(domains, x.io().available(), "IO");
		add(domains, x.leaks().available(), "LEAK");
		add(domains, x.threadCpu().available(), "THREAD_CPU");
		return String.join(",", domains);
	}

	private static void add(Set<String> domains, boolean available, String name) {
		if (available) {
			domains.add(name);
		}
	}

	private SummaryMapper() {
	}

	public static RecordingSummary toSummary(UUID recordingId, RecordingExtraction x, DiagnosisOutcome outcome) {
		RecordingSummary s = new RecordingSummary();
		s.setRecordingId(recordingId);
		s.setCpuJvmAvg(x.cpu().jvmAvg());
		s.setCpuJvmMax(x.cpu().jvmMax());
		s.setCpuMachineAvg(x.cpu().machineAvg());
		s.setCpuMachineMax(x.cpu().machineMax());
		s.setHeapInitialBytes(x.heap().initialBytes());
		s.setHeapMaxBytes(x.heap().maxBytes());
		s.setHeapUsedPeakBytes(x.heap().usedPeakBytes());
		s.setGcYoungCollector(x.heap().youngCollector());
		s.setGcOldCollector(x.heap().oldCollector());
		if (x.gc().available()) {
			s.setGcCount(x.gc().count());
			s.setGcPauseTotalMs(x.gc().pauseTotalMs());
			s.setGcPauseAvgMs(x.gc().pauseAvgMs());
			s.setGcPauseP95Ms(x.gc().pauseP95Ms());
			s.setGcPauseMaxMs(x.gc().pauseMaxMs());
			s.setGcPausePct(x.gc().pausePct());
		}
		s.setThreadPeak(x.threads().peak());
		s.setThreadDaemonPeak(x.threads().daemonPeak());
		s.setThreadsStarted(x.threads().started());
		s.setThreadsEnded(x.threads().ended());
		s.setThrowablesTotal(x.exceptions().throwablesTotal());
		s.setAllocationRateBytesPerSecond(x.allocation().rateBytesPerSecond());
		s.setExecutionSamplesTotal(x.hotMethods().totalSamples());
		s.setDomainsAvailable(domainsAvailable(x));
		s.setHealthScore(outcome.healthScore());
		s.setSuggestedCommand(outcome.suggestedCommand());
		return s;
	}
}
