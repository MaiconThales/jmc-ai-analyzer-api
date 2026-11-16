package io.github.maiconthales.jmc_ai_analyzer_api.application;

import tools.jackson.databind.JsonNode;

/** Outcome of one stage; one outcome is produced even when another stage fails. */
public record McpAnalysisStageResult(
        McpAnalysisStage stage,
        Status status,
        JsonNode payload,
        Throwable error) {

    public enum Status { SUCCESS, FAILED, TIMEOUT, OVERLOADED }

    public static McpAnalysisStageResult success(McpAnalysisStage stage, JsonNode payload) {
        return new McpAnalysisStageResult(stage, Status.SUCCESS, payload, null);
    }

    public static McpAnalysisStageResult failed(McpAnalysisStage stage, Throwable error) {
        return new McpAnalysisStageResult(stage, Status.FAILED, null, error);
    }

    public static McpAnalysisStageResult overloaded(McpAnalysisStage stage, Throwable error) {
        return new McpAnalysisStageResult(stage, Status.OVERLOADED, null, error);
    }

    public static McpAnalysisStageResult timeout(McpAnalysisStage stage, Throwable error) {
        return new McpAnalysisStageResult(stage, Status.TIMEOUT, null, error);
    }
}
