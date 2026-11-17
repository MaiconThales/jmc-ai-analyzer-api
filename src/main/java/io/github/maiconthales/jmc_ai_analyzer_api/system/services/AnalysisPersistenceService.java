package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisOutcome;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingExtraction;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingMetadata;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RecordingChild;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.mapper.SummaryMapper;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.AnalysisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AllocationHotspotRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AnalysisBatchRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.CoverageItemRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.DiagnosisItemRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.ExceptionHotspotRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.GcPauseRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.HotMethodRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.IoHotspotRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.JfrRecordingRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.LeakSuspectRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.LockContentionRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.MetricSampleRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.RecordingSummaryRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.RuleFindingRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.ThreadCpuRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JvmArgsParser;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Grava todo o resultado de uma gravação numa única transação e marca a gravação como COMPLETED. */
@Service
@RequiredArgsConstructor
public class AnalysisPersistenceService {

	private final JfrRecordingRepository recordingRepository;
	private final AnalysisBatchRepository batchRepository;
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
	private final AnalysisProperties properties;
	private final Clock clock;

	@Transactional
	public void writeCompleted(UUID recordingId, RecordingExtraction extraction, DiagnosisOutcome outcome) {
		JfrRecording recording = recordingRepository.findById(recordingId).orElseThrow();
		applyMetadata(recording, extraction.metadata());
		recording.setParserVersion(properties.parserVersion());
		recording.setStatus(AnalysisStatus.COMPLETED);
		recording.setErrorCode(null);
		recording.setErrorMessage(null);
		recording.setProcessingFinishedAt(Instant.now(clock));
		fillBatchVersion(recording.getBatchId(), extraction.metadata());

		summaryRepository.save(SummaryMapper.toSummary(recordingId, extraction, outcome));
		save(metricRepository, recordingId, concat(extraction.cpu().series(), extraction.heap().series(),
				extraction.threads().series()));
		save(gcPauseRepository, recordingId, extraction.gc().topPauses());
		save(hotMethodRepository, recordingId, extraction.hotMethods().top());
		save(lockRepository, recordingId, extraction.locks().top());
		save(allocationRepository, recordingId, extraction.allocation().top());
		save(exceptionRepository, recordingId, extraction.exceptions().top());
		save(ioRepository, recordingId, extraction.io().top());
		save(leakRepository, recordingId, extraction.leaks().top());
		save(threadCpuRepository, recordingId, extraction.threadCpu().top());
		save(findingRepository, recordingId, extraction.rules().findings());
		save(diagnosisRepository, recordingId, outcome.items());
		save(coverageRepository, recordingId, outcome.coverage());
	}

	private void fillBatchVersion(UUID batchId, RecordingMetadata metadata) {
		AnalysisBatch batch = batchRepository.findById(batchId).orElseThrow();
		if (batch.getSoftwareVersion() == null) {
			JvmArgsParser.jarVersion(metadata.javaArgs()).ifPresent(batch::setSoftwareVersion);
		}
	}

	static void applyMetadata(JfrRecording recording, RecordingMetadata metadata) {
		recording.setRecordingStart(metadata.start());
		recording.setRecordingEnd(metadata.end());
		recording.setDurationMs(metadata.durationMs());
		recording.setEventCount(metadata.eventCount());
		recording.setJvmName(metadata.jvmName());
		recording.setJvmVersion(metadata.jvmVersion());
		recording.setJvmArgs(metadata.jvmArgs());
		recording.setJavaArgs(metadata.javaArgs());
		recording.setOsName(metadata.osName());
		recording.setCpuCores(metadata.cpuCores());
		recording.setPhysicalMemoryBytes(metadata.physicalMemoryBytes());
	}

	private static <T extends RecordingChild> void save(JpaRepository<T, Long> repository, UUID recordingId,
			List<T> entities) {
		entities.forEach(e -> e.setRecordingId(recordingId));
		repository.saveAll(entities);
	}

	@SafeVarargs
	private static <T> List<T> concat(List<T>... lists) {
		List<T> all = new ArrayList<>();
		for (List<T> list : lists) {
			all.addAll(list);
		}
		return all;
	}
}
