package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing;

import java.util.List;
import java.util.UUID;

/** Publicado quando um lote é persistido; o processamento começa depois do commit. */
public record RecordingsUploadedEvent(List<UUID> recordingIds) {
}
