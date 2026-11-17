package io.github.maiconthales.jmc_ai_analyzer_api.system.repository;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.MetricSample;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricSampleRepository extends JpaRepository<MetricSample, Long> {

	List<MetricSample> findByRecordingIdInOrderByTsAsc(Collection<UUID> recordingIds);
}
