package io.github.maiconthales.jmc_ai_analyzer_api.application;

import java.util.List;
import java.util.EnumSet;
import java.util.Objects;

/** In-memory result handed to the next orchestration step; it is not persisted here. */
public record McpAnalysisExecutionResult(List<McpAnalysisStageResult> stages) {
    public McpAnalysisExecutionResult {
        Objects.requireNonNull(stages, "stages");
        EnumSet<McpAnalysisStage> seen = EnumSet.noneOf(McpAnalysisStage.class);
        for (McpAnalysisStageResult stage : stages) {
            Objects.requireNonNull(stage, "stages must not contain null");
            Objects.requireNonNull(stage.stage(), "stage must not be null");
            Objects.requireNonNull(stage.status(), "status must not be null");
            if (!seen.add(stage.stage())) {
                throw new IllegalArgumentException("Duplicate MCP stage: " + stage.stage());
            }
        }
        stages = List.copyOf(stages);
    }
}
