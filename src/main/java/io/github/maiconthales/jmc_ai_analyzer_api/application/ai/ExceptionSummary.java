package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import java.util.List;

public record ExceptionSummary(List<AnalysisFinding> exceptions) {
    public ExceptionSummary {
        exceptions = Text.list(exceptions, AnalysisLimits.MAX_ITEMS_PER_CATEGORY, "exceptions");
    }
}
