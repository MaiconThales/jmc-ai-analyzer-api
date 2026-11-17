package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.IoType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Alvo de I/O lento. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@Table(name = "io_hotspot")
public class IoHotspot extends RecordingChild {

	private int rank;

	@Enumerated(EnumType.STRING)
	@Column(name = "io_type", nullable = false)
	private IoType ioType;

	private String target;

	private long bytes;

	private long eventCount;

	private double totalMs;

	private double maxMs;
}
