package io.github.maiconthales.jmc_ai_analyzer_api.api;

import tools.jackson.databind.JsonNode;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** HTTP contract types. These intentionally do not expose MCP or persistence types. */
public final class AnalysisHttpDtos {
    private AnalysisHttpDtos() { }

    public record Session(UUID analysisId, String fileId, String fileName, long fileSizeBytes,
                   Instant fileModifiedAt, AnalysisStatus status, Instant startedAt,
                   Instant finishedAt, Instant createdAt, int errorCount) { }

    public record Stage(UUID stageId, String name, AnalysisStatus status, Instant startedAt,
                 Instant finishedAt, Error error) { }

    public record Error(String code, String message) { }

    public record Result(UUID resultId, String type, JsonNode payload, Instant createdAt) { }

    public record Interpretation(UUID interpretationId, String status, String provider, String model,
                          String promptVersion, String executiveSummary, String overallSeverity,
                          JsonNode payload, Error error, Instant createdAt) { }

    public record Analysis(Session session, List<Stage> stages, List<Result> results,
                    List<Error> errors, Interpretation interpretation) { }

    public record Status(UUID analysisId, AnalysisStatus status, int errorCount,
                  List<Stage> stages) { }

    public record History(List<Analysis> content, int page, int size, long totalElements,
                   int totalPages, boolean first, boolean last) { }
}
