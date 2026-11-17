package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.dashboard;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AllocationHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.CoverageItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.ExceptionHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.GcPause;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.HotMethod;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.IoHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LeakSuspect;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LockContention;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.MetricSample;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RecordingSummary;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RuleFinding;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.ThreadCpu;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Todos os dados persistidos de um conjunto de gravações, agrupados por {@code recordingId}. */
public record RecordingDataSet(
		Map<UUID, RecordingSummary> summaries,
		Map<UUID, List<MetricSample>> samples,
		Map<UUID, List<GcPause>> gcPauses,
		Map<UUID, List<HotMethod>> hotMethods,
		Map<UUID, List<LockContention>> locks,
		Map<UUID, List<AllocationHotspot>> allocations,
		Map<UUID, List<ExceptionHotspot>> exceptions,
		Map<UUID, List<IoHotspot>> io,
		Map<UUID, List<LeakSuspect>> leaks,
		Map<UUID, List<ThreadCpu>> threadCpu,
		Map<UUID, List<RuleFinding>> findings,
		Map<UUID, List<DiagnosisItem>> diagnosis,
		Map<UUID, List<CoverageItem>> coverage) {

	public static <T> List<T> of(Map<UUID, List<T>> map, UUID recordingId) {
		return map.getOrDefault(recordingId, List.of());
	}
}
