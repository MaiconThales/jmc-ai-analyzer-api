package io.github.maiconthales.jmc_ai_analyzer_api.domain.model;

import tools.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_result")
public class AnalysisResult {
    private static final tools.jackson.databind.ObjectMapper JSON_MAPPER = new tools.jackson.databind.ObjectMapper();

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private AnalysisSession analysis;

    @Column(name = "result_type", nullable = false, length = 128)
    private String resultType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AnalysisResult() {
    }

    public AnalysisResult(String resultType, JsonNode payload) {
        this.resultType = resultType;
        this.payload = payload;
    }

    void setAnalysis(AnalysisSession analysis) { this.analysis = analysis; }
    public UUID getId() { return id; }
    public AnalysisSession getAnalysis() { return analysis; }
    public String getResultType() { return resultType; }
    /** H2 exposes native JSON parameters as a textual JSON value; normalize that
     * representation at the entity boundary just as Hibernate does for JSONB. */
    public JsonNode getPayload() {
        if (payload != null && payload.isTextual()) {
            try {
                return JSON_MAPPER.readTree(payload.textValue());
            } catch (RuntimeException ignored) {
                // A genuinely textual legacy value remains unchanged.
            }
        }
        return payload;
    }
    public Instant getCreatedAt() { return createdAt; }
}
