package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.ThreadCpu;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThreadCpuRepository extends JpaRepository<ThreadCpu, Long> {

	List<ThreadCpu> findByRecordingIdAndRankLessThanEqualOrderByRankAsc(UUID recordingId, int rank);

	List<ThreadCpu> findByRecordingIdIn(Collection<UUID> recordingIds);
}
