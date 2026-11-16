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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_interpretation")
public class AiInterpretation {
    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false, updatable = false)
    private AnalysisSession analysis;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AiInterpretationStatus status;

    @Column(name = "provider", nullable = false, length = 128, updatable = false)
    private String provider;
    @Column(name = "model", nullable = false, length = 256, updatable = false)
    private String model;
    @Column(name = "prompt_version", nullable = false, length = 64, updatable = false)
    private String promptVersion;
    @Column(name = "executive_summary", columnDefinition = "TEXT")
    private String executiveSummary;
    @Column(name = "overall_severity", length = 32)
    private String overallSeverity;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private JsonNode payload;
    @Column(name = "error_code", length = 128)
    private String errorCode;
    @Column(name = "error_message", length = 2048)
    private String errorMessage;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiInterpretation() { }

    public AiInterpretation(AnalysisSession analysis, AiInterpretationStatus status, String provider,
                            String model, String promptVersion, String executiveSummary,
                            String overallSeverity, JsonNode payload, String errorCode, String errorMessage) {
        this.analysis = analysis;
        this.status = status;
        this.provider = provider;
        this.model = model;
        this.promptVersion = promptVersion;
        this.executiveSummary = executiveSummary;
        this.overallSeverity = overallSeverity;
        this.payload = payload;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public UUID getId() { return id; }
    public AnalysisSession getAnalysis() { return analysis; }
    public AiInterpretationStatus getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getPromptVersion() { return promptVersion; }
    public String getExecutiveSummary() { return executiveSummary; }
    public String getSummary() { return executiveSummary; }
    public String getOverallSeverity() { return overallSeverity; }
    public JsonNode getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
