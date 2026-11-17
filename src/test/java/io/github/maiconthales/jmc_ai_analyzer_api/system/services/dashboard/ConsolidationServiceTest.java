package io.github.maiconthales.jmc_ai_analyzer_api.system.services.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.dashboard.AppFrameResolver;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.ComparisonRow;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.dashboard.ConsolidationInput;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.dashboard.RecordingDataSet;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.JfrRecording;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RecordingSummary;
import io.github.maiconthales.jmc_ai_analyzer_api.system.mapper.DashboardMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Consolidação com gravações sem resumo ou com valores nulos. */
class ConsolidationServiceTest {

	private final ConsolidationService service = new ConsolidationService();

	@Test
	void missingValuesBecomeNullOrZero() {
		JfrRecording withoutSummary = new JfrRecording(UUID.randomUUID(), "a.jfr", "b", "k", "s", 1);
		JfrRecording emptySummary = new JfrRecording(UUID.randomUUID(), "b.jfr", "b", "k", "s", 1);
		RecordingSummary summary = new RecordingSummary();
		summary.setRecordingId(emptySummary.getId());
		Map<UUID, RecordingSummary> summaries = new HashMap<>();
		summaries.put(emptySummary.getId(), summary);
		RecordingDataSet data = new RecordingDataSet(summaries, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
				Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
		ConsolidationInput in = new ConsolidationInput(List.of(withoutSummary, emptySummary), data,
				new DashboardMapper(mock(AppFrameResolver.class), List.of(), 10));

		assertThat(service.metadata(in).totalDurationMs()).isZero();
		assertThat(service.metadata(in).jvmVersions()).isEmpty();
		assertThat(service.cpu(in).jvmAvg()).isNull();
		assertThat(service.cpu(in).available()).isFalse();
		assertThat(service.heap(in).maxBytes()).isNull();
		assertThat(service.gc(in).count()).isNull();
		assertThat(service.gc(in).pausePct()).isNull();
		assertThat(service.threads(in).started()).isNull();
		assertThat(service.hotMethods(in).top()).isEmpty();
		assertThat(service.exceptions(in).throwablesTotal()).isNull();
		assertThat(service.allocation(in).rateBytesPerSec()).isNull();
		assertThat(service.io(in).totals()).isEmpty();
		assertThat(service.leaks(in).top()).isEmpty();
		assertThat(service.threadCpu(in).top()).isEmpty();
		assertThat(service.locks(in).top()).isEmpty();
		assertThat(service.findings(in)).isEmpty();
		assertThat(service.diagnosis(in)).isEmpty();
		assertThat(service.coverage(in).suggestedCommand()).isNull();
		assertThat(service.healthScore(in)).isNull();
		assertThat(service.comparison(in)).extracting(ComparisonRow::healthScore).containsOnlyNulls();
		assertThat(in.duration(UUID.randomUUID())).isZero();
	}
}
