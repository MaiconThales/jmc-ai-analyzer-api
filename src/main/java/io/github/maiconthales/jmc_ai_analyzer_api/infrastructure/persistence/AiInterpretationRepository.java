package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence;

import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AiInterpretation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AiInterpretationRepository extends JpaRepository<AiInterpretation, UUID> {
    List<AiInterpretation> findAllByAnalysis_IdOrderByCreatedAtDescIdDesc(UUID analysisId);
}
