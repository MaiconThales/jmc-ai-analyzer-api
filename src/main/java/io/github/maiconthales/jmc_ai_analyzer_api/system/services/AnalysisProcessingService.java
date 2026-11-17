package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.jfr.RecordingAnalyzer;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisOutcome;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingExtraction;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.ProcessingTarget;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.AnalysisInterruptedException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.BlobNotFoundException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.JfrParseException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.StorageUnavailableException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.AnalysisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.diagnosis.DiagnosisService;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Processa uma gravação: baixa do blob, extrai, diagnostica e grava. Um watchdog interrompe a thread quando
 * o tempo passa de {@code app.analysis.timeout}; a interrupção é verificada entre as etapas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisProcessingService {

	static final String MDC_RECORDING = "recordingId";
	static final String MDC_BATCH = "batchId";

	private final RecordingStateService stateService;
	private final BlobStorageService blobStorage;
	private final TempFileService tempFiles;
	private final RecordingAnalyzer analyzer;
	private final DiagnosisService diagnosisService;
	private final AnalysisPersistenceService writer;
	private final BatchStatusService batchStatusService;
	private final ScheduledExecutorService analysisWatchdog;
	private final AnalysisProperties properties;

	public void process(UUID recordingId) {
		Optional<ProcessingTarget> started = stateService.start(recordingId);
		if (started.isEmpty()) {
			log.debug("Gravação {} não está pendente; ignorada", recordingId);
			return;
		}
		ProcessingTarget target = started.get();
		MDC.put(MDC_RECORDING, recordingId.toString());
		MDC.put(MDC_BATCH, target.batchId().toString());
		long begin = System.nanoTime();
		try {
			run(target);
			log.info("Gravação processada em {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
		} finally {
			batchStatusService.refresh(target.batchId());
			MDC.remove(MDC_RECORDING);
			MDC.remove(MDC_BATCH);
		}
	}

	private void run(ProcessingTarget target) {
		Thread worker = Thread.currentThread();
		AtomicBoolean timedOut = new AtomicBoolean();
		ScheduledFuture<?> watchdog = analysisWatchdog.schedule(() -> {
			timedOut.set(true);
			worker.interrupt();
		}, properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
		Path file = tempFiles.create(".jfr");
		try {
			blobStorage.download(target.blobKey(), file);
			RecordingExtraction extraction = analyzer.analyze(file);
			DiagnosisOutcome outcome = diagnosisService.diagnose(extraction, target.environment(),
					target.appPackages());
			watchdog.cancel(false);
			if (timedOut.get()) {
				throw new AnalysisInterruptedException();
			}
			Thread.interrupted();
			writer.writeCompleted(target.recordingId(), extraction, outcome);
		} catch (RuntimeException e) {
			watchdog.cancel(false);
			Thread.interrupted();
			fail(target, e, timedOut.get());
		} finally {
			tempFiles.deleteQuietly(file);
		}
	}

	private void fail(ProcessingTarget target, RuntimeException e, boolean timedOut) {
		ErrorCode code = classify(e, timedOut);
		String message = code == ErrorCode.TIMEOUT
				? "Processamento excedeu " + properties.timeout() + "."
				: e.getMessage();
		if (code == ErrorCode.INTERNAL_ERROR) {
			log.error("Falha inesperada ao processar a gravação", e);
			message = "Erro interno ao processar a gravação.";
		} else {
			log.warn("Gravação falhou com {}: {}", code, e.getMessage());
		}
		stateService.fail(target.recordingId(), code, message);
	}

	static ErrorCode classify(RuntimeException e, boolean timedOut) {
		if (timedOut || e instanceof AnalysisInterruptedException) {
			return ErrorCode.TIMEOUT;
		}
		if (e instanceof JfrParseException) {
			return ErrorCode.PARSE_ERROR;
		}
		if (e instanceof BlobNotFoundException) {
			return ErrorCode.BLOB_NOT_FOUND;
		}
		return e instanceof StorageUnavailableException ? ErrorCode.STORAGE_UNAVAILABLE : ErrorCode.INTERNAL_ERROR;
	}
}
