package io.github.maiconthales.jmc_ai_analyzer_api.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnalysisRequest(
        @NotBlank String fileId,
        @DecimalMin(value = "0.0", inclusive = true) Double minScore,
        @Min(1) @Max(1000) Integer topN,
        @Min(1) @Max(1000) Integer topClasses,
        @Min(0) @Max(3_600_000) Long minDurationMs) {
    public AnalysisRequest {
        if (minScore != null && (!Double.isFinite(minScore) || minScore > 100.0)) {
            throw new IllegalArgumentException("minScore must be finite and between 0 and 100");
        }
    }

    public double effectiveMinScore() { return minScore == null ? 50.0 : minScore; }
    public int effectiveTopN() { return topN == null ? 10 : topN; }
    public int effectiveTopClasses() { return topClasses == null ? 10 : topClasses; }
    public long effectiveMinDurationMs() { return minDurationMs == null ? 50L : minDurationMs; }
}
