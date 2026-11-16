CREATE TABLE analysis_session (
    id UUID NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    file_modified_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_analysis_session PRIMARY KEY (id),
    CONSTRAINT ck_analysis_session_file_size CHECK (file_size_bytes >= 0),
    CONSTRAINT ck_analysis_session_error_count CHECK (error_count >= 0),
    CONSTRAINT ck_analysis_session_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_analysis_session_finished_after_started CHECK (
        finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at
    )
);

CREATE INDEX idx_analysis_session_status_created
    ON analysis_session (status, created_at DESC);

CREATE INDEX idx_analysis_session_history
    ON analysis_session (created_at DESC, id DESC);

CREATE INDEX idx_analysis_session_file_modified
    ON analysis_session (file_modified_at);
