package io.github.maiconthales.jmc_ai_analyzer_api.api;

import java.time.Instant;

/** Stable, deliberately non-diagnostic HTTP error representation. */
public record ApiError(Instant timestamp, int status, String code, String message,
                       String correlationId) {
}
