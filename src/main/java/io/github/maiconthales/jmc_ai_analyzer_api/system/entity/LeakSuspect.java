package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Classe com objetos antigos ainda vivos (jdk.OldObjectSample). */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@Table(name = "leak_suspect")
public class LeakSuspect extends RecordingChild {

	private int rank;

	private String className;

	private long samples;

	private Long maxAgeMs;

	private Long maxArraySize;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "stack_frames", columnDefinition = "jsonb")
	private List<StackFrame> stackFrames;
}
