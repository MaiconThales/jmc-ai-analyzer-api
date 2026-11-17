package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisBatchRepository extends JpaRepository<AnalysisBatch, UUID> {

	Page<AnalysisBatch> findBySoftwareId(UUID softwareId, Pageable pageable);

	long countBySoftwareId(UUID softwareId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from AnalysisBatch b where b.id = :id")
	Optional<AnalysisBatch> findForUpdate(@Param("id") UUID id);

	Optional<AnalysisBatch> findFirstBySoftwareIdOrderByCreatedAtDesc(UUID softwareId);

	@Query("select b.id from AnalysisBatch b where b.softwareId = :softwareId and b.createdAt < :before "
			+ "and b.status <> io.github.maiconthales.jmc_ai_analyzer_api.system.enums.BatchStatus.PROCESSING")
	List<UUID> findExpired(@Param("softwareId") UUID softwareId, @Param("before") Instant before);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from AnalysisBatch b where b.id in :ids")
	int deleteByIdIn(@Param("ids") Collection<UUID> ids);
}
