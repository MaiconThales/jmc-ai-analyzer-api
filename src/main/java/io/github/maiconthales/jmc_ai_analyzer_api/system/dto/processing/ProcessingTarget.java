package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import java.util.List;
import java.util.UUID;

/** Dados necessários para processar uma gravação, lidos ao marcá-la como PROCESSING. */
public record ProcessingTarget(UUID recordingId, UUID batchId, String blobKey, Environment environment,
		List<String> appPackages) {
}
