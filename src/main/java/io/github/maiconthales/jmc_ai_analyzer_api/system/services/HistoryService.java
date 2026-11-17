package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.HistoryItemResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RecordingSummary;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.Software;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.NotFoundException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AnalysisBatchRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.JfrRecordingRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.RecordingSummaryRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.SoftwareRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Histórico paginado de lotes, opcionalmente filtrado por software (RF-08). */
@Service
@RequiredArgsConstructor
public class HistoryService {

	private final AnalysisBatchRepository batchRepository;
	private final JfrRecordingRepository recordingRepository;
	private final RecordingSummaryRepository summaryRepository;
	private final SoftwareRepository softwareRepository;

	@Transactional(readOnly = true)
	public Page<HistoryItemResponse> list(String softwareName, Pageable pageable) {
		Page<AnalysisBatch> page;
		if (softwareName == null || softwareName.isBlank()) {
			page = batchRepository.findAll(pageable);
		} else {
			Software software = softwareRepository.findByName(softwareName)
					.orElseThrow(() -> new NotFoundException("Software", softwareName));
			page = batchRepository.findBySoftwareId(software.getId(), pageable);
		}
		List<UUID> batchIds = page.getContent().stream().map(AnalysisBatch::getId).toList();
		List<JfrRecording> recordings = recordingRepository.findByBatchIdInOrderByCreatedAtAsc(batchIds);
		Map<UUID, List<UUID>> recordingsByBatch = recordings.stream().collect(Collectors.groupingBy(
				JfrRecording::getBatchId, Collectors.mapping(JfrRecording::getId, Collectors.toList())));
		List<UUID> recordingIds = recordings.stream().map(JfrRecording::getId).toList();
		Map<UUID, RecordingSummary> summaries = summaryRepository.findByRecordingIdIn(recordingIds).stream()
				.collect(Collectors.toMap(RecordingSummary::getRecordingId, Function.identity()));
		Map<UUID, Long> warnings = counts(recordingRepository.countWarningFindingsByBatch(batchIds));
		Map<UUID, Long> high = counts(recordingRepository.countHighPriorityItemsByBatch(batchIds));
		Map<UUID, String> names = softwareRepository.findAllById(page.getContent().stream()
				.map(AnalysisBatch::getSoftwareId).distinct().toList()).stream()
				.collect(Collectors.toMap(Software::getId, Software::getName));
		return page.map(b -> new HistoryItemResponse(b.getId(), names.get(b.getSoftwareId()), b.getSoftwareVersion(),
				b.getEnvironment(), b.getDescription(), b.getCreatedAt(), b.getFileCount(), b.getStatus(),
				warnings.getOrDefault(b.getId(), 0L),
				recordingsByBatch.getOrDefault(b.getId(), List.of()).stream().map(summaries::get)
						.filter(Objects::nonNull).map(RecordingSummary::getHealthScore).filter(Objects::nonNull)
						.min(Integer::compare).orElse(null),
				high.getOrDefault(b.getId(), 0L)));
	}

	private static Map<UUID, Long> counts(List<Object[]> rows) {
		return rows.stream().collect(Collectors.toMap(r -> (UUID) r[0], r -> ((Number) r[1]).longValue()));
	}
}
