package io.github.maiconthales.jmc_ai_analyzer_api.system.services.dashboard;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.dashboard.AppFrameResolver;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.dashboard.RecordingDataLoader;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.BatchDashboard;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.RecordingDashboard;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.RecordingRef;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.dashboard.ConsolidationInput;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.dashboard.RecordingDataSet;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RecordingSummary;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.Software;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.ApiException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.NotFoundException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.NotReadyException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.mapper.DashboardMapper;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.AnalysisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AnalysisBatchRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.JfrRecordingRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.SoftwareRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.diagnosis.DiagnosisService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta os dashboards por gravação (RF-05) e consolidado por lote (RF-06). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

	static final String RECORDING = "Gravação";
	static final String BATCH = "Lote";

	private final JfrRecordingRepository recordingRepository;
	private final AnalysisBatchRepository batchRepository;
	private final SoftwareRepository softwareRepository;
	private final RecordingDataLoader loader;
	private final ConsolidationService consolidation;
	private final AppFrameResolver resolver;
	private final AnalysisProperties properties;

	public RecordingDashboard recording(UUID recordingId, Integer requestedTopN) {
		int topN = topN(requestedTopN);
		JfrRecording r = recordingRepository.findById(recordingId)
				.orElseThrow(() -> new NotFoundException(RECORDING, recordingId));
		if (r.getStatus() != AnalysisStatus.COMPLETED) {
			throw notReady(r);
		}
		AnalysisBatch batch = batchRepository.findById(r.getBatchId()).orElseThrow();
		Software software = softwareRepository.findById(batch.getSoftwareId()).orElseThrow();
		RecordingDataSet data = loader.load(List.of(recordingId));
		DashboardMapper m = new DashboardMapper(resolver, software.appPackageList(), topN);
		RecordingSummary s = data.summaries().getOrDefault(recordingId, new RecordingSummary());
		Set<String> domains = DashboardMapper.domains(s);
		var samples = RecordingDataSet.of(data.samples(), recordingId);
		return new RecordingDashboard(r.getId(), batch.getId(), software.getName(), batch.getSoftwareVersion(),
				r.getOriginalName(), r.getParserVersion(), s.getHealthScore(),
				RecordingDataSet.of(data.diagnosis(), recordingId).stream()
						.sorted(DiagnosisService.ORDER).map(d -> m.diagnosis(d, null)).toList(),
				DashboardMapper.coverage(RecordingDataSet.of(data.coverage(), recordingId), s.getSuggestedCommand()),
				DashboardMapper.metadata(r),
				DashboardMapper.cpu(s, domains, DashboardMapper.series(samples, DashboardMapper.CPU)),
				DashboardMapper.heap(s, domains, DashboardMapper.series(samples, DashboardMapper.HEAP)),
				m.gc(s, domains, RecordingDataSet.of(data.gcPauses(), recordingId)),
				DashboardMapper.threads(s, domains, DashboardMapper.series(samples, DashboardMapper.THREADS)),
				m.locks(domains, RecordingDataSet.of(data.locks(), recordingId)),
				m.hotMethods(s, domains, RecordingDataSet.of(data.hotMethods(), recordingId)),
				m.allocation(s, domains, RecordingDataSet.of(data.allocations(), recordingId)),
				m.exceptions(s, domains, RecordingDataSet.of(data.exceptions(), recordingId)),
				m.io(domains, RecordingDataSet.of(data.io(), recordingId)),
				m.leaks(domains, RecordingDataSet.of(data.leaks(), recordingId)),
				m.threadCpu(domains, RecordingDataSet.of(data.threadCpu(), recordingId)),
				DashboardMapper.findings(RecordingDataSet.of(data.findings(), recordingId), f -> null));
	}

	public BatchDashboard batch(UUID batchId, Integer requestedTopN) {
		int topN = topN(requestedTopN);
		AnalysisBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new NotFoundException(BATCH, batchId));
		Software software = softwareRepository.findById(batch.getSoftwareId()).orElseThrow();
		List<JfrRecording> all = recordingRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
		List<JfrRecording> completed = all.stream().filter(r -> r.getStatus() == AnalysisStatus.COMPLETED).toList();
		if (completed.isEmpty()) {
			throw (NotReadyException) new NotReadyException("Nenhuma gravação do lote está concluída.")
					.with("batchStatus", batch.getStatus());
		}
		DashboardMapper m = new DashboardMapper(resolver, software.appPackageList(), topN);
		ConsolidationInput in = new ConsolidationInput(completed,
				loader.load(completed.stream().map(JfrRecording::getId).toList()), m);
		return new BatchDashboard(batch.getId(), software.getName(), batch.getSoftwareVersion(),
				batch.getEnvironment(), batch.getDescription(), consolidation.healthScore(in),
				consolidation.diagnosis(in), consolidation.coverage(in), consolidation.metadata(in),
				consolidation.cpu(in), consolidation.heap(in), consolidation.gc(in), consolidation.threads(in),
				consolidation.locks(in), consolidation.hotMethods(in), consolidation.allocation(in),
				consolidation.exceptions(in), consolidation.io(in), consolidation.leaks(in),
				consolidation.threadCpu(in), consolidation.findings(in), consolidation.comparison(in),
				refs(completed), refs(all.stream().filter(r -> r.getStatus() != AnalysisStatus.COMPLETED).toList()));
	}

	int topN(Integer requested) {
		int topN = requested == null ? properties.defaultTopN() : requested;
		if (topN < 1 || topN > properties.maxTopN()) {
			throw new ApiException(ErrorCode.INVALID_PARAMETER,
					"'topN' deve estar entre 1 e " + properties.maxTopN() + ".");
		}
		return topN;
	}

	private static NotReadyException notReady(JfrRecording r) {
		NotReadyException e = new NotReadyException(r.getStatus() == AnalysisStatus.FAILED
				? "A análise da gravação falhou."
				: "A gravação ainda está em processamento.");
		e.with("status", r.getStatus());
		if (r.getErrorCode() != null) {
			e.with("errorCode", r.getErrorCode()).with("errorMessage", r.getErrorMessage());
		}
		return e;
	}

	private static List<RecordingRef> refs(List<JfrRecording> recordings) {
		return recordings.stream().map(r -> new RecordingRef(r.getId(), r.getOriginalName(), r.getStatus())).toList();
	}
}
