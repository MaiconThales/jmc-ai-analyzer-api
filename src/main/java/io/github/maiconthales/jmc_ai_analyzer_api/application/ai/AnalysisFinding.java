package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import java.util.Objects;

public record AnalysisFinding(String category, String title, String detail, String severity) {
    public AnalysisFinding {
        category = Text.required(category, "category");
        title = Text.bounded(title, "title");
        detail = Text.bounded(detail, "detail");
        severity = Text.bounded(severity, "severity");
    }
}
