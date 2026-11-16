package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import java.util.List;

public record LockSummary(List<AnalysisFinding> contentions) {
    public LockSummary {
        contentions = Text.list(contentions, AnalysisLimits.MAX_ITEMS_PER_CATEGORY, "contentions");
    }
}
