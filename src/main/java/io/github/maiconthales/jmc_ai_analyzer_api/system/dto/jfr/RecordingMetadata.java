package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr;

import java.time.Instant;

/** Metadados da gravação e do ambiente que a gerou. */
public record RecordingMetadata(
		Instant start,
		Instant end,
		long durationMs,
		long eventCount,
		String jvmName,
		String jvmVersion,
		String jvmArgs,
		String javaArgs,
		String osName,
		Integer cpuCores,
		Integer hardwareThreads,
		Long physicalMemoryBytes) {

	public double durationSeconds() {
		return durationMs / 1000.0;
	}
}
