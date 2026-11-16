package io.github.maiconthales.jmc_ai_analyzer_api.dto;

import java.time.Instant;

/** HTTP representation of a JFR file. It deliberately contains no filesystem path. */
public record JfrFileResponse(String id, String name, long size, Instant modifiedAt) {
}
