package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.SequenceGenerator;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Base das tabelas filhas de {@code jfr_recording}. A exclusão é feita em cascata pelo banco.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class RecordingChild {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "child_seq")
	@SequenceGenerator(name = "child_seq", sequenceName = "child_seq", allocationSize = 50)
	private Long id;

	@Column(name = "recording_id", nullable = false)
	private UUID recordingId;

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		return other != null && getClass() == other.getClass() && id != null
				&& id.equals(((RecordingChild) other).id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}
