package io.github.maiconthales.jmc_ai_analyzer_api.system.components;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.StorageUnavailableException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.JfrRecordingRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.BlobStorageService;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.TempFileService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Na inicialização: garante o bucket, limpa temporários esquecidos e recoloca na fila as gravações que estavam
 * pendentes ou em processamento quando a API parou.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryRunner implements ApplicationRunner {

	private final BlobStorageService blobStorage;
	private final TempFileService tempFiles;
	private final JfrRecordingRepository recordingRepository;
	private final AnalysisDispatcher dispatcher;
	private final TransactionTemplate transactionTemplate;

	@Override
	public void run(ApplicationArguments args) {
		try {
			blobStorage.ensureBucket();
		} catch (StorageUnavailableException e) {
			log.warn("Blob storage indisponível na inicialização: {}", e.getMessage());
		}
		int removed = tempFiles.cleanupOld();
		if (removed > 0) {
			log.info("{} temporários antigos removidos", removed);
		}
		List<JfrRecording> pending = transactionTemplate.execute(status -> {
			recordingRepository.updateStatus(AnalysisStatus.PROCESSING, AnalysisStatus.PENDING);
			return recordingRepository.findByStatusIn(List.of(AnalysisStatus.PENDING));
		});
		if (pending != null && !pending.isEmpty()) {
			log.info("Retomando {} gravações pendentes", pending.size());
			dispatcher.dispatch(pending.stream().map(JfrRecording::getId).toList());
		}
	}
}
