package io.github.maiconthales.jmc_ai_analyzer_api.system.components;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.BlobObject;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.RetentionResult;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.Software;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.StorageUnavailableException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AnalysisBatchRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.JfrRecordingRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.SoftwareRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.BlobStorageService;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.UploadService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Retenção por software (RF-11): apaga lotes vencidos (a cascata remove os dados filhos) e depois os objetos do
 * blob que não são mais referenciados por nenhuma gravação.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionJob {

	/** Objetos recém-enviados podem pertencer a um upload ainda não persistido. */
	static final Duration UPLOAD_GRACE = Duration.ofHours(1);

	private final SoftwareRepository softwareRepository;
	private final AnalysisBatchRepository batchRepository;
	private final JfrRecordingRepository recordingRepository;
	private final BlobStorageService blobStorage;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;


	@Scheduled(cron = "${app.retention.cron}")
	public void scheduled() {
		run();
	}

	public RetentionResult run() {
		int batches = 0;
		for (Software software : softwareRepository.findAll()) {
			batches += purge(software);
		}
		int objects = deleteOrphanObjects();
		if (batches > 0 || objects > 0) {
			log.info("Retenção concluída: {} lotes e {} objetos removidos", batches, objects);
		}
		return new RetentionResult(batches, objects);
	}

	private int purge(Software software) {
		Instant before = Instant.now(clock).minus(Duration.ofDays(software.getRetentionDays()));
		Integer removed = transactionTemplate.execute(status -> {
			List<UUID> expired = batchRepository.findExpired(software.getId(), before);
			return expired.isEmpty() ? 0 : batchRepository.deleteByIdIn(expired);
		});
		int count = removed == null ? 0 : removed;
		if (count > 0) {
			log.info("Software {}: {} lotes removidos pela retenção de {} dias", software.getName(), count,
					software.getRetentionDays());
		}
		return count;
	}

	private int deleteOrphanObjects() {
		try {
			Set<String> referenced = new HashSet<>(recordingRepository.findAllBlobKeys());
			Instant graceLimit = Instant.now(clock).minus(UPLOAD_GRACE);
			int deleted = 0;
			for (BlobObject object : blobStorage.list(UploadService.KEY_PREFIX)) {
				boolean recent = object.lastModified() != null && object.lastModified().isAfter(graceLimit);
				if (!recent && !referenced.contains(object.key())) {
					blobStorage.delete(object.key());
					deleted++;
				}
			}
			return deleted;
		} catch (StorageUnavailableException e) {
			log.warn("Blob storage indisponível; objetos órfãos serão removidos na próxima execução: {}",
					e.getMessage());
			return 0;
		}
	}
}
