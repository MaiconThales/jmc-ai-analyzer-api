package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.CoverageDomain;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.CoverageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Situação de um domínio na cobertura da gravação. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@Table(name = "coverage_item")
public class CoverageItem extends RecordingChild {

	@Enumerated(EnumType.STRING)
	@Column(name = "domain", nullable = false)
	private CoverageDomain domain;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private CoverageStatus status;

	private String reason;

	private String recommendation;
}
