CREATE TABLE analysis_stage (
    id UUID NOT NULL,
    analysis_id UUID NOT NULL,
    stage_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(128),
    error_message VARCHAR(2048),
    CONSTRAINT pk_analysis_stage PRIMARY KEY (id),
    CONSTRAINT fk_analysis_stage_session FOREIGN KEY (analysis_id)
        REFERENCES analysis_session (id) ON DELETE NO ACTION,
    CONSTRAINT ck_analysis_stage_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_analysis_stage_finished_after_started CHECK (
        finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at
    ),
    CONSTRAINT uq_analysis_stage_session_name UNIQUE (analysis_id, stage_name)
);

CREATE INDEX idx_analysis_stage_analysis_id
    ON analysis_stage (analysis_id);

CREATE INDEX idx_analysis_stage_status
    ON analysis_stage (status);
