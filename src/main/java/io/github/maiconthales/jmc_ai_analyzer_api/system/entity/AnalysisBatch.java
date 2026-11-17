package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.BatchStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lote: arquivos enviados num mesmo upload. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "analysis_batch")
public class AnalysisBatch {

	@Id
	private UUID id;

	private UUID softwareId;

	private String softwareVersion;

	@Enumerated(EnumType.STRING)
	private Environment environment;

	private String description;

	@Enumerated(EnumType.STRING)
	private BatchStatus status;

	private int fileCount;

	private Instant createdAt;

	private Instant finishedAt;

	public AnalysisBatch(UUID softwareId, String softwareVersion, Environment environment, String description,
			int fileCount) {
		this.id = UUID.randomUUID();
		this.softwareId = softwareId;
		this.softwareVersion = softwareVersion;
		this.environment = environment;
		this.description = description;
		this.fileCount = fileCount;
		this.status = BatchStatus.PROCESSING;
		this.createdAt = Instant.now();
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof AnalysisBatch batch && id != null && id.equals(batch.id);
	}

	@Override
	public int hashCode() {
		return AnalysisBatch.class.hashCode();
	}
}
