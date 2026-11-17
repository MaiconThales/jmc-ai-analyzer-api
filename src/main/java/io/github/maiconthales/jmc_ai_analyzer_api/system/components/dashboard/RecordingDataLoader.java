package io.github.maiconthales.jmc_ai_analyzer_api.system.components.dashboard;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.dashboard.RecordingDataSet;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RecordingChild;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RecordingSummary;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AllocationHotspotRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.CoverageItemRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.DiagnosisItemRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.ExceptionHotspotRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.GcPauseRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.HotMethodRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.IoHotspotRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.LeakSuspectRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.LockContentionRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.MetricSampleRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.RecordingSummaryRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.RuleFindingRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.ThreadCpuRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Carrega os dados persistidos de várias gravações com uma consulta por tabela. */
@Component
@RequiredArgsConstructor
public class RecordingDataLoader {

	private final RecordingSummaryRepository summaryRepository;
	private final MetricSampleRepository metricRepository;
	private final GcPauseRepository gcPauseRepository;
	private final HotMethodRepository hotMethodRepository;
	private final LockContentionRepository lockRepository;
	private final AllocationHotspotRepository allocationRepository;
	private final ExceptionHotspotRepository exceptionRepository;
	private final IoHotspotRepository ioRepository;
	private final LeakSuspectRepository leakRepository;
	private final ThreadCpuRepository threadCpuRepository;
	private final RuleFindingRepository findingRepository;
	private final DiagnosisItemRepository diagnosisRepository;
	private final CoverageItemRepository coverageRepository;

	public RecordingDataSet load(Collection<UUID> ids) {
		return new RecordingDataSet(
				summaryRepository.findByRecordingIdIn(ids).stream()
						.collect(Collectors.toMap(RecordingSummary::getRecordingId, Function.identity())),
				group(metricRepository.findByRecordingIdInOrderByTsAsc(ids)),
				group(gcPauseRepository.findByRecordingIdIn(ids)),
				group(hotMethodRepository.findByRecordingIdIn(ids)),
				group(lockRepository.findByRecordingIdIn(ids)),
				group(allocationRepository.findByRecordingIdIn(ids)),
				group(exceptionRepository.findByRecordingIdIn(ids)),
				group(ioRepository.findByRecordingIdIn(ids)),
				group(leakRepository.findByRecordingIdIn(ids)),
				group(threadCpuRepository.findByRecordingIdIn(ids)),
				group(findingRepository.findByRecordingIdIn(ids)),
				group(diagnosisRepository.findByRecordingIdIn(ids)),
				group(coverageRepository.findByRecordingIdIn(ids)));
	}

	private static <T extends RecordingChild> Map<UUID, List<T>> group(List<T> entities) {
		return entities.stream().collect(Collectors.groupingBy(RecordingChild::getRecordingId));
	}
}
