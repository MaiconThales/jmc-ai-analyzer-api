package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.MetricType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ponto de uma série temporal reduzida. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@Table(name = "metric_sample")
public class MetricSample extends RecordingChild {

	@Enumerated(EnumType.STRING)
	@Column(name = "metric", nullable = false)
	private MetricType metric;

	private java.time.Instant ts;

	private double avgValue;

	private double maxValue;
}
