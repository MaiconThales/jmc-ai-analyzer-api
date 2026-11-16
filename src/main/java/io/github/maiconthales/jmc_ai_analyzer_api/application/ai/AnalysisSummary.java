package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

public record AnalysisSummary(int successfulStages, int failedStages, int findingCount) {
    public AnalysisSummary {
        if (successfulStages < 0 || failedStages < 0 || findingCount < 0) {
            throw new IllegalArgumentException("summary counts cannot be negative");
        }
    }
}
