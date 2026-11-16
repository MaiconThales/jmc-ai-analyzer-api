CREATE TABLE ai_interpretation (
    id UUID NOT NULL,
    analysis_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider VARCHAR(128) NOT NULL,
    model VARCHAR(256) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    executive_summary TEXT,
    overall_severity VARCHAR(32),
    payload JSONB,
    error_code VARCHAR(128),
    error_message VARCHAR(2048),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ai_interpretation PRIMARY KEY (id),
    CONSTRAINT fk_ai_interpretation_session FOREIGN KEY (analysis_id)
        REFERENCES analysis_session (id) ON DELETE NO ACTION,
    CONSTRAINT ck_ai_interpretation_status CHECK (
        status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'UNAVAILABLE')
    )
);

CREATE INDEX idx_ai_interpretation_analysis_created
    ON ai_interpretation (analysis_id, created_at DESC, id DESC);

CREATE INDEX idx_ai_interpretation_status
    ON ai_interpretation (status);
