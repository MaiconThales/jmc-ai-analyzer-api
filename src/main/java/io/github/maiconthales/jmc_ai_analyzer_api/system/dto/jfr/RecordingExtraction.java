package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr;

import java.util.Map;

/** Tudo o que foi extraído de uma gravação, antes do diagnóstico. */
public record RecordingExtraction(
		RecordingMetadata metadata,
		DomainResults.Cpu cpu,
		DomainResults.Heap heap,
		DomainResults.Gc gc,
		DomainResults.Threads threads,
		DomainResults.Locks locks,
		DomainResults.HotMethods hotMethods,
		DomainResults.Allocation allocation,
		DomainResults.Exceptions exceptions,
		DomainResults.Io io,
		DomainResults.Leaks leaks,
		DomainResults.ThreadCpuLoad threadCpu,
		JmcRulesResult rules,
		RecordingSettings settings,
		Map<String, Long> eventCounts) {

	public long eventCount(String eventType) {
		return eventCounts.getOrDefault(eventType, 0L);
	}
}
