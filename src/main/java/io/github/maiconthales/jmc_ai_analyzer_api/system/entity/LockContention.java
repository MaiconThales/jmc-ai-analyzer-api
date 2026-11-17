package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.LockType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bloqueio mais custoso por classe de monitor. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@Table(name = "lock_contention")
public class LockContention extends RecordingChild {

	private int rank;

	@Enumerated(EnumType.STRING)
	@Column(name = "lock_type", nullable = false)
	private LockType lockType;

	private String className;

	private long eventCount;

	private double totalMs;

	private double maxMs;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "stack_frames", columnDefinition = "jsonb")
	private List<StackFrame> stackFrames;
}
