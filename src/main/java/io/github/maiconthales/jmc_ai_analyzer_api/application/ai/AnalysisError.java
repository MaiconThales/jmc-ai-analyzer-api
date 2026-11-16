package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

public record AnalysisError(String stage, String code, String message) {
    public AnalysisError {
        stage = Text.required(stage, "stage");
        code = Text.required(code, "code");
        message = Text.bounded(message, "message");
    }
}
