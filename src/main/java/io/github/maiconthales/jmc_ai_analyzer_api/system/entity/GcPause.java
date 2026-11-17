package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Uma das maiores pausas de GC. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@Table(name = "gc_pause")
public class GcPause extends RecordingChild {

	private int rank;

	private Long gcId;

	private String collector;

	private String cause;

	private java.time.Instant startTime;

	private double durationMs;
}
