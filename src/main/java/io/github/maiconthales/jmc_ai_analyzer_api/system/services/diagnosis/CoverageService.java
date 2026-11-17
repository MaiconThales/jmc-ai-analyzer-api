package io.github.maiconthales.jmc_ai_analyzer_api.system.services.diagnosis;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.CoverageDomainSpec;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.CoverageResult;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingExtraction;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.CoverageItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.CoverageDomain;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.CoverageStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.DiagnosisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Cobertura da gravação: o que pode ou não ser diagnosticado e como gravar melhor.
 */
@Service
public class CoverageService {

	static final String BASE_COMMAND = "-XX:StartFlightRecording=settings=profile,duration=10m,filename=app.jfr";


	static final Map<CoverageDomain, CoverageDomainSpec> DOMAINS = new EnumMap<>(Map.ofEntries(
			Map.entry(CoverageDomain.CPU, new CoverageDomainSpec(List.of(JfrEventTypes.CPU_LOAD),
					"Habilite jdk.CPULoad (period=1 s).", null)),
			Map.entry(CoverageDomain.HEAP, new CoverageDomainSpec(List.of(JfrEventTypes.HEAP_SUMMARY),
					"Habilite jdk.GCHeapSummary.", null)),
			Map.entry(CoverageDomain.GC, new CoverageDomainSpec(List.of(JfrEventTypes.GARBAGE_COLLECTION),
					"Habilite jdk.GarbageCollection.", null)),
			Map.entry(CoverageDomain.THREADS, new CoverageDomainSpec(List.of(JfrEventTypes.THREAD_STATISTICS),
					"Habilite jdk.JavaThreadStatistics.", null)),
			Map.entry(CoverageDomain.LOCKS, new CoverageDomainSpec(List.of(JfrEventTypes.MONITOR_ENTER,
					JfrEventTypes.MONITOR_WAIT, JfrEventTypes.THREAD_PARK),
					"Habilite jdk.JavaMonitorEnter com threshold baixo (ex.: 10 ms).", "jdk.JavaMonitorEnter#threshold=10ms")),
			Map.entry(CoverageDomain.HOT_METHODS, new CoverageDomainSpec(List.of(JfrEventTypes.EXECUTION_SAMPLE),
					"Grave por mais tempo sob carga real ou use settings=profile (amostragem a cada 10 ms).", null)),
			Map.entry(CoverageDomain.ALLOCATION, new CoverageDomainSpec(List.of(JfrEventTypes.ALLOCATION_SAMPLE,
					JfrEventTypes.ALLOCATION_IN_NEW_TLAB, JfrEventTypes.ALLOCATION_OUTSIDE_TLAB),
					"Habilite jdk.ObjectAllocationSample.", null)),
			Map.entry(CoverageDomain.EXCEPTIONS, new CoverageDomainSpec(List.of(JfrEventTypes.EXCEPTION_THROW),
					"Habilite jdk.JavaExceptionThrow para ver todas as exceções lançadas (não só os erros).",
					"jdk.JavaExceptionThrow#enabled=true")),
			Map.entry(CoverageDomain.FILE_IO, new CoverageDomainSpec(List.of(JfrEventTypes.FILE_READ, JfrEventTypes.FILE_WRITE),
					"Reduza o threshold de jdk.FileRead/jdk.FileWrite (padrão 10–20 ms) para capturar mais operações.",
					"jdk.FileRead#threshold=1ms,jdk.FileWrite#threshold=1ms")),
			Map.entry(CoverageDomain.SOCKET_IO, new CoverageDomainSpec(List.of(JfrEventTypes.SOCKET_READ,
					JfrEventTypes.SOCKET_WRITE),
					"Reduza o threshold de jdk.SocketRead/jdk.SocketWrite para capturar mais operações.", null)),
			Map.entry(CoverageDomain.LEAK, new CoverageDomainSpec(List.of(JfrEventTypes.OLD_OBJECT_SAMPLE),
					"Habilite jdk.OldObjectSample (e path-to-gc-roots=true para ver quem retém os objetos).", null)),
			Map.entry(CoverageDomain.THREAD_CPU, new CoverageDomainSpec(List.of(JfrEventTypes.THREAD_CPU_LOAD),
					"Habilite jdk.ThreadCPULoad.", null))));


	public CoverageResult evaluate(RecordingExtraction extraction, DiagnosisProperties properties) {
		List<CoverageItem> items = new ArrayList<>();
		Set<String> extras = new LinkedHashSet<>();
		for (Map.Entry<CoverageDomain, CoverageDomainSpec> entry : DOMAINS.entrySet()) {
			CoverageItem item = evaluateDomain(entry.getKey(), entry.getValue(), extraction, properties);
			if (item.getStatus() != CoverageStatus.OK && entry.getValue().extraSetting() != null) {
				extras.add(entry.getValue().extraSetting());
			}
			items.add(item);
		}
		String command = extras.isEmpty() ? BASE_COMMAND : BASE_COMMAND + "," + String.join(",", extras);
		return new CoverageResult(items, command);
	}

	CoverageItem evaluateDomain(CoverageDomain domain, CoverageDomainSpec spec, RecordingExtraction extraction,
			DiagnosisProperties properties) {
		long count = spec.eventTypes().stream().mapToLong(extraction::eventCount).sum();
		String primary = spec.eventTypes().get(0);
		if (count == 0) {
			return item(domain, CoverageStatus.AUSENTE, absenceReason(primary, extraction), spec.recommendation());
		}
		if (domain == CoverageDomain.HOT_METHODS) {
			return hotMethods(count, spec, extraction, properties);
		}
		return item(domain, CoverageStatus.OK, count + " eventos.", null);
	}

	private CoverageItem hotMethods(long count, CoverageDomainSpec spec, RecordingExtraction extraction,
			DiagnosisProperties properties) {
		Duration duration = Duration.ofMillis(extraction.metadata().durationMs());
		List<String> problems = new ArrayList<>();
		if (count < properties.coverage().minExecutionSamples()) {
			problems.add(count + " amostras em " + duration.toSeconds() + " s (mínimo recomendado: "
					+ properties.coverage().minExecutionSamples() + ")");
		}
		if (duration.compareTo(properties.coverage().minDuration()) < 0) {
			problems.add("gravação de " + duration.toSeconds() + " s (mínimo recomendado: "
					+ properties.coverage().minDuration().toMinutes() + " min)");
		}
		if (problems.isEmpty()) {
			return item(CoverageDomain.HOT_METHODS, CoverageStatus.OK, count + " amostras.", null);
		}
		return item(CoverageDomain.HOT_METHODS, CoverageStatus.INSUFICIENTE, String.join("; ", problems) + ".",
				spec.recommendation());
	}

	static String absenceReason(String eventType, RecordingExtraction extraction) {
		Optional<Boolean> enabled = extraction.settings().enabled(eventType);
		if (enabled.isPresent() && !enabled.get()) {
			return eventType + " desabilitado na gravação.";
		}
		if (enabled.isPresent()) {
			String threshold = extraction.settings().value(eventType, "threshold").map(t -> " (limiar " + t + ")")
					.orElse("");
			return eventType + " habilitado" + threshold + ", mas sem ocorrências.";
		}
		return "Nenhum evento " + eventType + " na gravação.";
	}

	private static CoverageItem item(CoverageDomain domain, CoverageStatus status, String reason,
			String recommendation) {
		CoverageItem item = new CoverageItem();
		item.setDomain(domain);
		item.setStatus(status);
		item.setReason(reason);
		item.setRecommendation(recommendation);
		return item;
	}
}
