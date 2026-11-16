package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import java.time.Instant;
import java.util.Objects;

public record AnalysisMetadata(String schemaVersion, String source, Instant createdAt) {
    public AnalysisMetadata {
        schemaVersion = Text.required(schemaVersion, "schemaVersion");
        source = Text.required(source, "source");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
}
