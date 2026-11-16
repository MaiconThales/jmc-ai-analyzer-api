package io.github.maiconthales.jmc_ai_analyzer_api.api;

import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus;
import java.util.UUID;

public record AnalysisResponse(UUID analysisId, AnalysisStatus status) { }
