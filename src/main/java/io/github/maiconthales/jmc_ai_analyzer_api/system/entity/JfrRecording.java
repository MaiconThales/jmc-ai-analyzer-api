package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.AnalysisStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
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

/** Um arquivo {@code .jfr} de um lote e os metadados extraídos dele. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "jfr_recording")
public class JfrRecording {

	@Id
	private UUID id;

	private UUID batchId;

	private String originalName;

	private String blobBucket;

	private String blobKey;

	private String sha256;

	private long sizeBytes;

	@Enumerated(EnumType.STRING)
	private AnalysisStatus status;

	@Enumerated(EnumType.STRING)
	private ErrorCode errorCode;

	private String errorMessage;

	private String parserVersion;

	private Instant recordingStart;

	private Instant recordingEnd;

	private Long durationMs;

	private Long eventCount;

	private String jvmName;

	private String jvmVersion;

	private String jvmArgs;

	private String javaArgs;

	private String osName;

	private Integer cpuCores;

	private Long physicalMemoryBytes;

	private Instant processingStartedAt;

	private Instant processingFinishedAt;

	private Instant createdAt;

	public JfrRecording(UUID batchId, String originalName, String blobBucket, String blobKey, String sha256,
			long sizeBytes) {
		this.id = UUID.randomUUID();
		this.batchId = batchId;
		this.originalName = originalName;
		this.blobBucket = blobBucket;
		this.blobKey = blobKey;
		this.sha256 = sha256;
		this.sizeBytes = sizeBytes;
		this.status = AnalysisStatus.PENDING;
		this.createdAt = Instant.now();
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof JfrRecording recording && id != null && id.equals(recording.id);
	}

	@Override
	public int hashCode() {
		return JfrRecording.class.hashCode();
	}
}
