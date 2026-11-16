package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import java.util.List;

public record CpuSummary(List<AnalysisFinding> hotMethods) {
    public CpuSummary {
        hotMethods = Text.list(hotMethods, AnalysisLimits.MAX_ITEMS_PER_CATEGORY, "hotMethods");
    }
}
