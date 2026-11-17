package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Problema priorizado com evidência, localização e ação. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@Table(name = "diagnosis_item")
public class DiagnosisItem extends RecordingChild {

	@Enumerated(EnumType.STRING)
	@Column(name = "priority", nullable = false)
	private Priority priority;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false)
	private DiagnosisCategory category;

	private String source;

	private int score;

	private String title;

	private String evidence;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "location", columnDefinition = "jsonb")
	private DiagnosisLocation location;

	private String action;

	private String domainRef;
}
