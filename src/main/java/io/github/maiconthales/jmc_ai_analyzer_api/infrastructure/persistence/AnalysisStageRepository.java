package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence;

import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnalysisStageRepository extends JpaRepository<AnalysisStage, UUID> {
    List<AnalysisStage> findAllByAnalysisIdOrderByIdAsc(UUID analysisId);

    java.util.Optional<AnalysisStage> findByAnalysisIdAndStageName(UUID analysisId, String stageName);
}
