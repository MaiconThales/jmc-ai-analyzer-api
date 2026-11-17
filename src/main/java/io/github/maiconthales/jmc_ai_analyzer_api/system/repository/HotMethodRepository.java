package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.HotMethod;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotMethodRepository extends JpaRepository<HotMethod, Long> {

	List<HotMethod> findByRecordingIdAndRankLessThanEqualOrderByRankAsc(UUID recordingId, int rank);

	List<HotMethod> findByRecordingIdIn(Collection<UUID> recordingIds);
}
