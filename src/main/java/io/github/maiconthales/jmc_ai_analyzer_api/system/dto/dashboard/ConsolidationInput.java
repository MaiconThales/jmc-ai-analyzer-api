package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.dashboard;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RecordingSummary;
import io.github.maiconthales.jmc_ai_analyzer_api.system.mapper.DashboardMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Gravações concluídas com seus dados. */
public record ConsolidationInput(List<JfrRecording> recordings, RecordingDataSet data, DashboardMapper mapper) {

	public List<UUID> ids() {
		return recordings.stream().map(JfrRecording::getId).toList();
	}

	public List<RecordingSummary> summaries() {
		return ids().stream().map(data.summaries()::get).filter(Objects::nonNull).toList();
	}

	public <T> List<T> all(Map<UUID, List<T>> map) {
		return ids().stream().flatMap(id -> RecordingDataSet.of(map, id).stream()).toList();
	}

	public boolean anyDomain(String domain) {
		return summaries().stream().anyMatch(s -> DashboardMapper.domains(s).contains(domain));
	}

	public long duration(UUID id) {
		return recordings.stream().filter(r -> r.getId().equals(id)).findFirst()
				.map(r -> r.getDurationMs() == null ? 0L : r.getDurationMs()).orElse(0L);
	}
}
