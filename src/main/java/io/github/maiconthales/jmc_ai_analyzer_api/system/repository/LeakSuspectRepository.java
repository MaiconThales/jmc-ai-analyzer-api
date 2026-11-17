package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LeakSuspect;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeakSuspectRepository extends JpaRepository<LeakSuspect, Long> {

	List<LeakSuspect> findByRecordingIdAndRankLessThanEqualOrderByRankAsc(UUID recordingId, int rank);

	List<LeakSuspect> findByRecordingIdIn(Collection<UUID> recordingIds);
}
