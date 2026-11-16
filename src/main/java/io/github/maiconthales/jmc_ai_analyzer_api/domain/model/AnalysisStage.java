package io.github.maiconthales.jmc_ai_analyzer_api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_stage")
public class AnalysisStage {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private AnalysisSession analysis;

    @Column(name = "stage_name", nullable = false, length = 64)
    private String stageName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AnalysisStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    protected AnalysisStage() {
    }

    public AnalysisStage(String stageName) {
        this.stageName = stageName;
        this.status = AnalysisStatus.PENDING;
    }

    void setAnalysis(AnalysisSession analysis) { this.analysis = analysis; }
    public UUID getId() { return id; }
    public AnalysisSession getAnalysis() { return analysis; }
    public String getStageName() { return stageName; }
    public AnalysisStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }

    /** Moves a pending stage into execution. */
    public void start(Instant startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        requireStatus(AnalysisStatus.PENDING, "start");
        this.status = AnalysisStatus.RUNNING;
        this.startedAt = startedAt;
    }

    /** Finishes an executing stage with a terminal status. */
    public void finish(AnalysisStatus status, Instant finishedAt) {
        if (status != AnalysisStatus.COMPLETED
                && status != AnalysisStatus.PARTIAL
                && status != AnalysisStatus.FAILED
                && status != AnalysisStatus.SUCCESS
                && status != AnalysisStatus.TIMEOUT
                && status != AnalysisStatus.OVERLOADED) {
            throw new IllegalArgumentException("A stage can only be finished with a terminal status");
        }
        if (finishedAt == null) {
            throw new IllegalArgumentException("finishedAt must not be null");
        }
        requireStatus(AnalysisStatus.RUNNING, "finish");
        if (startedAt == null || finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt must not be before startedAt");
        }
        this.status = status;
        this.finishedAt = finishedAt;
    }

    /** Records a safe, bounded error description without bypassing lifecycle rules. */
    public void recordError(String errorCode, String errorMessage) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage must not be blank");
        }
        requireStatus(AnalysisStatus.RUNNING, "record error for");
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    private void requireStatus(AnalysisStatus expected, String operation) {
        if (status != expected) {
            throw new IllegalStateException("Cannot " + operation + " stage in status " + status);
        }
    }
}
