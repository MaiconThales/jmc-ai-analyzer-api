package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.BatchResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.NotFoundException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.mapper.BatchMapper;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AnalysisBatchRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.JfrRecordingRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.SoftwareRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Status de um lote (RF-07). */
@Service
@RequiredArgsConstructor
public class BatchQueryService {

	private final AnalysisBatchRepository batchRepository;
	private final JfrRecordingRepository recordingRepository;
	private final SoftwareRepository softwareRepository;

	@Transactional(readOnly = true)
	public BatchResponse get(UUID batchId) {
		AnalysisBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new NotFoundException("Lote", batchId));
		String software = softwareRepository.findById(batch.getSoftwareId()).map(s -> s.getName()).orElse(null);
		return BatchMapper.toResponse(batch, software, recordingRepository.findByBatchIdOrderByCreatedAtAsc(batchId));
	}
}
