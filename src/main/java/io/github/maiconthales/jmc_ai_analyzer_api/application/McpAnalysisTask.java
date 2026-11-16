package io.github.maiconthales.jmc_ai_analyzer_api.application;

import java.util.Objects;
import java.util.concurrent.Callable;

/** A single MCP call, deliberately kept independent from persistence. */
public record McpAnalysisTask(McpAnalysisStage stage, Callable<?> call) {
    public McpAnalysisTask {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(call, "call");
    }
}
