package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.AnalysisStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JfrRecordingRepository extends JpaRepository<JfrRecording, UUID> {

	List<JfrRecording> findByBatchIdOrderByCreatedAtAsc(UUID batchId);

	List<JfrRecording> findByBatchIdInOrderByCreatedAtAsc(Collection<UUID> batchIds);

	List<JfrRecording> findByStatusIn(Collection<AnalysisStatus> statuses);

	boolean existsBySha256(String sha256);

	@Query("select distinct r.blobKey from JfrRecording r")
	List<String> findAllBlobKeys();

	@Query("select r.batchId, count(f) from RuleFinding f, JfrRecording r where f.recordingId = r.id "
			+ "and r.batchId in :batchIds "
			+ "and f.severity = io.github.maiconthales.jmc_ai_analyzer_api.system.enums.FindingSeverity.WARNING "
			+ "group by r.batchId")
	List<Object[]> countWarningFindingsByBatch(@Param("batchIds") Collection<UUID> batchIds);

	@Query("select r.batchId, count(d) from DiagnosisItem d, JfrRecording r where d.recordingId = r.id "
			+ "and r.batchId in :batchIds "
			+ "and d.priority = io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority.ALTA "
			+ "group by r.batchId")
	List<Object[]> countHighPriorityItemsByBatch(@Param("batchIds") Collection<UUID> batchIds);

	@Query("select distinct r.sha256 from JfrRecording r where r.batchId in :batchIds")
	List<String> findShaByBatchIdIn(@Param("batchIds") Collection<UUID> batchIds);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update JfrRecording r set r.status = :to where r.status = :from")
	int updateStatus(@Param("from") AnalysisStatus from, @Param("to") AnalysisStatus to);
}
