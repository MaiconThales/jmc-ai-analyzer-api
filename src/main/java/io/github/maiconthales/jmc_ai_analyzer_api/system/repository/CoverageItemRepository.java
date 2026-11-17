package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.CoverageItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverageItemRepository extends JpaRepository<CoverageItem, Long> {

	List<CoverageItem> findByRecordingIdIn(Collection<UUID> recordingIds);
}
