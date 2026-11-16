package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence;

import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {
    List<AnalysisResult> findAllByAnalysisIdOrderByIdAsc(UUID analysisId);

    @Modifying
    @Query(value = "INSERT INTO analysis_result "
            + "(id, analysis_id, result_type, payload, created_at) "
            + "VALUES (:id, :analysisId, :resultType, CAST(:payload AS jsonb), CURRENT_TIMESTAMP) "
            + "ON CONFLICT (analysis_id, result_type) DO NOTHING", nativeQuery = true)
    int insertIfAbsentPostgres(@Param("id") UUID id,
                       @Param("analysisId") UUID analysisId,
                       @Param("resultType") String resultType,
                       @Param("payload") String payload);

    /** H2 atomic insert-if-absent (the source is filtered before MERGE, so replay never updates). */
    @Modifying
    @Query(value = "MERGE INTO analysis_result "
            + "(id, analysis_id, result_type, payload, created_at) "
            + "KEY (analysis_id, result_type) "
            + "SELECT :id, :analysisId, :resultType, :payload, CURRENT_TIMESTAMP "
            + "WHERE NOT EXISTS (SELECT 1 FROM analysis_result "
            + "WHERE analysis_id = :analysisId AND result_type = :resultType)", nativeQuery = true)
    int insertIfAbsentH2(@Param("id") UUID id,
                          @Param("analysisId") UUID analysisId,
                          @Param("resultType") String resultType,
                          @Param("payload") String payload);

    boolean existsByAnalysisIdAndResultType(UUID analysisId, String resultType);

    java.util.Optional<AnalysisResult> findByAnalysisIdAndResultType(UUID analysisId, String resultType);
}
