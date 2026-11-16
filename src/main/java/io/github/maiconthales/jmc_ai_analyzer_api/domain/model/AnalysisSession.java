package io.github.maiconthales.jmc_ai_analyzer_api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "analysis_session")
public class AnalysisSession {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "file_id", length = 1024)
    private String fileId;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "file_modified_at")
    private Instant fileModifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AnalysisStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @OneToMany(mappedBy = "analysis", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<AnalysisStage> stages = new ArrayList<>();

    @OneToMany(mappedBy = "analysis", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<AnalysisResult> results = new ArrayList<>();

    protected AnalysisSession() {
    }

    public AnalysisSession(String fileName, long fileSizeBytes, Instant fileModifiedAt) {
        this(null, fileName, fileSizeBytes, fileModifiedAt);
    }

    public AnalysisSession(String fileId, String fileName, long fileSizeBytes, Instant fileModifiedAt) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSizeBytes = fileSizeBytes;
        this.fileModifiedAt = fileModifiedAt;
        this.status = AnalysisStatus.PENDING;
        this.errorCount = 0;
    }

    public void addStage(AnalysisStage stage) {
        stages.add(stage);
        stage.setAnalysis(this);
    }

    public void addResult(AnalysisResult result) {
        results.add(result);
        result.setAnalysis(this);
    }

    public UUID getId() { return id; }
    public String getFileName() { return fileName; }
    public String getFileId() { return fileId; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public Instant getFileModifiedAt() { return fileModifiedAt; }
    public AnalysisStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public int getErrorCount() { return errorCount; }
    /** Returns a read-only snapshot; changes must go through addStage. */
    public List<AnalysisStage> getStages() {
        return Collections.unmodifiableList(new ArrayList<>(stages));
    }

    /** Returns a read-only snapshot; changes must go through addResult. */
    public List<AnalysisResult> getResults() {
        return Collections.unmodifiableList(new ArrayList<>(results));
    }

    /** Moves a newly-created session into execution. */
    public void start(Instant startedAt) {
        validateStart(startedAt);
        this.status = AnalysisStatus.RUNNING;
        this.startedAt = startedAt;
    }

    /** Validates the start transition without changing the entity. */
    public void validateStart(Instant startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        requireStatus(AnalysisStatus.PENDING, "start");
    }

    /** Finishes an executing session without allowing invalid lifecycle states. */
    public void finish(AnalysisStatus status, int errorCount, Instant finishedAt) {
        validateFinish(status, errorCount, finishedAt);
        this.status = status;
        this.errorCount = errorCount;
        this.finishedAt = finishedAt;
    }

    /** Validates the finish transition without changing the entity. */
    public void validateFinish(AnalysisStatus status, int errorCount, Instant finishedAt) {
        if (status != AnalysisStatus.COMPLETED
                && status != AnalysisStatus.PARTIAL
                && status != AnalysisStatus.FAILED) {
            throw new IllegalArgumentException("A session can only be finished with a terminal status");
        }
        if (errorCount < 0) {
            throw new IllegalArgumentException("errorCount must not be negative");
        }
        if (finishedAt == null) {
            throw new IllegalArgumentException("finishedAt must not be null");
        }
        requireStatus(AnalysisStatus.RUNNING, "finish");
        if (startedAt == null || finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt must not be before startedAt");
        }
    }

    private void requireStatus(AnalysisStatus expected, String operation) {
        if (status != expected) {
            throw new IllegalStateException("Cannot " + operation + " session in status " + status);
        }
    }

}
