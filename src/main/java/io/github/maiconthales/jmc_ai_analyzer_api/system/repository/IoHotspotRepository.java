package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.IoHotspot;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IoHotspotRepository extends JpaRepository<IoHotspot, Long> {

	List<IoHotspot> findByRecordingIdAndRankLessThanEqualOrderByRankAsc(UUID recordingId, int rank);

	List<IoHotspot> findByRecordingIdIn(Collection<UUID> recordingIds);
}
