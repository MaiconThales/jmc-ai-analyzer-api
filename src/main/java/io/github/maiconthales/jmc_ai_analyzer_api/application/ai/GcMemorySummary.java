package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import java.util.List;

public record GcMemorySummary(List<AnalysisFinding> findings) {
    public GcMemorySummary {
        findings = Text.list(findings, AnalysisLimits.MAX_ITEMS_PER_CATEGORY, "findings");
    }
}
