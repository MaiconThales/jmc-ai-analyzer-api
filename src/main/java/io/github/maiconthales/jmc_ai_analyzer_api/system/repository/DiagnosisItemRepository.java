package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisItemRepository extends JpaRepository<DiagnosisItem, Long> {

	List<DiagnosisItem> findByRecordingIdIn(Collection<UUID> recordingIds);
}
