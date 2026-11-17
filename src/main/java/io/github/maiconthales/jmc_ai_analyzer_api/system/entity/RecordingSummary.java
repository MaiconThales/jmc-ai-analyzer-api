package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resumo numérico de uma gravação (1:1 com {@link JfrRecording}). */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@Table(name = "recording_summary")
public class RecordingSummary {

	@Id
	private UUID recordingId;

	private Double cpuJvmAvg;
	private Double cpuJvmMax;
	private Double cpuMachineAvg;
	private Double cpuMachineMax;
	private Long heapInitialBytes;
	private Long heapMaxBytes;
	private Long heapUsedPeakBytes;
	private String gcYoungCollector;
	private String gcOldCollector;
	private Integer gcCount;
	private Double gcPauseTotalMs;
	private Double gcPauseAvgMs;
	private Double gcPauseP95Ms;
	private Double gcPauseMaxMs;
	private Double gcPausePct;
	private Integer threadPeak;
	private Integer threadDaemonPeak;
	private Integer threadsStarted;
	private Integer threadsEnded;
	private Long throwablesTotal;
	@Column(name = "allocation_rate_bytes_s")
	private Double allocationRateBytesPerSecond;
	private Long executionSamplesTotal;
	private String domainsAvailable;
	private Integer healthScore;
	private String suggestedCommand;

	@Override
	public boolean equals(Object other) {
		return this == other
				|| other instanceof RecordingSummary summary && recordingId != null && recordingId.equals(summary.recordingId);
	}

	@Override
	public int hashCode() {
		return RecordingSummary.class.hashCode();
	}
}
