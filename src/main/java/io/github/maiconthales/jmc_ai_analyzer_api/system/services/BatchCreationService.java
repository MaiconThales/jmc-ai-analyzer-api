package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.BatchResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.UploadRequest;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.RecordingsUploadedEvent;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.StoredFile;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.Software;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import io.github.maiconthales.jmc_ai_analyzer_api.system.mapper.BatchMapper;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AnalysisBatchRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.JfrRecordingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persiste software, lote e gravações numa transação e agenda o processamento após o commit. */
@Service
@RequiredArgsConstructor
public class BatchCreationService {

	private final SoftwareService softwareService;
	private final AnalysisBatchRepository batchRepository;
	private final JfrRecordingRepository recordingRepository;
	private final BlobStorageService blobStorage;
	private final ApplicationEventPublisher events;

	@Transactional
	public BatchResponse create(UploadRequest request, Environment environment, List<StoredFile> files) {
		Software software = findOrCreate(request.software());
		String version = blankToNull(request.softwareVersion());
		AnalysisBatch batch = batchRepository.save(new AnalysisBatch(software.getId(), version, environment,
				blankToNull(request.description()), files.size()));
		List<JfrRecording> recordings = recordingRepository.saveAll(files.stream()
				.map(f -> new JfrRecording(batch.getId(), f.originalName(), blobStorage.bucket(), f.key(), f.sha256(),
						f.sizeBytes()))
				.toList());
		events.publishEvent(new RecordingsUploadedEvent(recordings.stream().map(JfrRecording::getId).toList()));
		return BatchMapper.toResponse(batch, software.getName(), recordings);
	}

	private Software findOrCreate(String name) {
		return softwareService.find(name).orElseGet(() -> {
			try {
				return softwareService.create(name);
			} catch (DataIntegrityViolationException concurrentCreation) {
				return softwareService.find(name).orElseThrow(() -> concurrentCreation);
			}
		});
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
