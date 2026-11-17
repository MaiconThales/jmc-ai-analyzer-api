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

/** Método mais amostrado no topo da pilha. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@Table(name = "hot_method")
public class HotMethod extends RecordingChild {

	private int rank;

	private String methodSignature;

	private long samples;

	private double percent;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "stack_frames", columnDefinition = "jsonb")
	private List<StackFrame> stackFrames;
}
