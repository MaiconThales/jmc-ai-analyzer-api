package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RecordingSummary;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordingSummaryRepository extends JpaRepository<RecordingSummary, UUID> {

	List<RecordingSummary> findByRecordingIdIn(Collection<UUID> recordingIds);
}
