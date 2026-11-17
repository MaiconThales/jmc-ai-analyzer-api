package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LockContention;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LockContentionRepository extends JpaRepository<LockContention, Long> {

	List<LockContention> findByRecordingIdAndRankLessThanEqualOrderByRankAsc(UUID recordingId, int rank);

	List<LockContention> findByRecordingIdIn(Collection<UUID> recordingIds);
}
