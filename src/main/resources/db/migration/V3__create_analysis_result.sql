CREATE TABLE analysis_result (
    id UUID NOT NULL,
    analysis_id UUID NOT NULL,
    result_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_analysis_result PRIMARY KEY (id),
    CONSTRAINT fk_analysis_result_session FOREIGN KEY (analysis_id)
        REFERENCES analysis_session (id) ON DELETE NO ACTION
);

CREATE INDEX idx_analysis_result_analysis_id
    ON analysis_result (analysis_id);

CREATE INDEX idx_analysis_result_type
    ON analysis_result (result_type);
