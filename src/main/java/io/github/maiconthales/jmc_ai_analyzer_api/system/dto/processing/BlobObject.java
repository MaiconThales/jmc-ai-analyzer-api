package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing;

import java.time.Instant;

/** Objeto do blob com a data da última modificação. */
public record BlobObject(String key, Instant lastModified) {
}
