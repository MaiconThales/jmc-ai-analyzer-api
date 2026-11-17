package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AllocationHotspot;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllocationHotspotRepository extends JpaRepository<AllocationHotspot, Long> {

	List<AllocationHotspot> findByRecordingIdAndRankLessThanEqualOrderByRankAsc(UUID recordingId, int rank);

	List<AllocationHotspot> findByRecordingIdIn(Collection<UUID> recordingIds);
}
