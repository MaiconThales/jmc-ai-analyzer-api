package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.FindingSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Achado de uma regra automática do JMC. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@Table(name = "rule_finding")
public class RuleFinding extends RecordingChild {

	private String ruleId;

	private String ruleName;

	private String topic;

	@Enumerated(EnumType.STRING)
	@Column(name = "severity", nullable = false)
	private FindingSeverity severity;

	private Double score;

	private String summary;

	private String explanation;

	private String solution;
}
