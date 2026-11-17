CREATE SEQUENCE child_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE software (
    id             UUID         PRIMARY KEY,
    name           VARCHAR(64)  NOT NULL UNIQUE,
    retention_days INT          NOT NULL CHECK (retention_days BETWEEN 1 AND 3650),
    app_packages   TEXT,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE TABLE analysis_batch (
    id               UUID         PRIMARY KEY,
    software_id      UUID         NOT NULL REFERENCES software (id) ON DELETE RESTRICT,
    software_version VARCHAR(64),
    environment      VARCHAR(8)   CHECK (environment IN ('PROD', 'HML', 'DEV', 'LOCAL')),
    description      VARCHAR(500),
    status           VARCHAR(16)  NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    file_count       INT          NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    finished_at      TIMESTAMPTZ
);

CREATE INDEX ix_batch_software_created ON analysis_batch (software_id, created_at DESC);
CREATE INDEX ix_batch_created ON analysis_batch (created_at DESC);

CREATE TABLE jfr_recording (
    id                     UUID          PRIMARY KEY,
    batch_id               UUID          NOT NULL REFERENCES analysis_batch (id) ON DELETE CASCADE,
    original_name          VARCHAR(255)  NOT NULL,
    blob_bucket            VARCHAR(63)   NOT NULL,
    blob_key               VARCHAR(255)  NOT NULL,
    sha256                 VARCHAR(64)   NOT NULL,
    size_bytes             BIGINT        NOT NULL,
    status                 VARCHAR(16)   NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    error_code             VARCHAR(32),
    error_message          VARCHAR(1000),
    parser_version         VARCHAR(64),
    recording_start        TIMESTAMPTZ,
    recording_end          TIMESTAMPTZ,
    duration_ms            BIGINT,
    event_count            BIGINT,
    jvm_name               VARCHAR(255),
    jvm_version            VARCHAR(500),
    jvm_args               TEXT,
    java_args              TEXT,
    os_name                VARCHAR(500),
    cpu_cores              INT,
    physical_memory_bytes  BIGINT,
    processing_started_at  TIMESTAMPTZ,
    processing_finished_at TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_recording_batch ON jfr_recording (batch_id);
CREATE INDEX ix_recording_status ON jfr_recording (status);
CREATE INDEX ix_recording_sha256 ON jfr_recording (sha256);
