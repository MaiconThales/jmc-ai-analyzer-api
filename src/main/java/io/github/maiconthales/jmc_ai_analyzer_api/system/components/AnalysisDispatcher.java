package io.github.maiconthales.jmc_ai_analyzer_api.system.components;

import io.github.maiconthales.jmc_ai_analyzer_api.system.config.AsyncConfig;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.RecordingsUploadedEvent;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.AnalysisProcessingService;
import java.util.Collection;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Enfileira gravações no executor de análise, sempre depois do commit do upload. */
@Slf4j
@Component
public class AnalysisDispatcher {

	private final TaskExecutor executor;
	private final AnalysisProcessingService processor;

	public AnalysisDispatcher(@Qualifier(AsyncConfig.ANALYSIS_EXECUTOR) TaskExecutor executor,
			AnalysisProcessingService processor) {
		this.executor = executor;
		this.processor = processor;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onUploaded(RecordingsUploadedEvent event) {
		dispatch(event.recordingIds());
	}

	public void dispatch(Collection<UUID> recordingIds) {
		recordingIds.forEach(id -> executor.execute(() -> safeProcess(id)));
	}

	private void safeProcess(UUID id) {
		try {
			processor.process(id);
		} catch (RuntimeException e) {
			log.error("Falha ao processar a gravação {}", id, e);
		}
	}
}
