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

/** Throwable mais lançado. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@Table(name = "exception_hotspot")
public class ExceptionHotspot extends RecordingChild {

	private int rank;

	private String exceptionClass;

	private long eventCount;

	private String sampleMessage;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "stack_frames", columnDefinition = "jsonb")
	private List<StackFrame> stackFrames;
}
