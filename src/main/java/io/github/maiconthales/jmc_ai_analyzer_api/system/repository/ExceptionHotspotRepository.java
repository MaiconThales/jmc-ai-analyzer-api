package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.ExceptionHotspot;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExceptionHotspotRepository extends JpaRepository<ExceptionHotspot, Long> {

	List<ExceptionHotspot> findByRecordingIdAndRankLessThanEqualOrderByRankAsc(UUID recordingId, int rank);

	List<ExceptionHotspot> findByRecordingIdIn(Collection<UUID> recordingIds);
}
