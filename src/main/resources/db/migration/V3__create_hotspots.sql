CREATE TABLE gc_pause (
    id           BIGINT PRIMARY KEY,
    recording_id UUID NOT NULL REFERENCES jfr_recording (id) ON DELETE CASCADE,
    rank         INT  NOT NULL,
    gc_id        BIGINT,
    collector    VARCHAR(64),
    cause        VARCHAR(255),
    start_time   TIMESTAMPTZ,
    duration_ms  DOUBLE PRECISION NOT NULL
);
CREATE INDEX ix_gc_pause ON gc_pause (recording_id, rank);

CREATE TABLE hot_method (
    id               BIGINT PRIMARY KEY,
    recording_id     UUID NOT NULL REFERENCES jfr_recording (id) ON DELETE CASCADE,
    rank             INT  NOT NULL,
    method_signature TEXT NOT NULL,
    samples          BIGINT NOT NULL,
    percent          DOUBLE PRECISION NOT NULL,
    stack_frames     JSONB
);
CREATE INDEX ix_hot_method ON hot_method (recording_id, rank);

CREATE TABLE lock_contention (
    id           BIGINT PRIMARY KEY,
    recording_id UUID NOT NULL REFERENCES jfr_recording (id) ON DELETE CASCADE,
    rank         INT  NOT NULL,
    lock_type    VARCHAR(16) NOT NULL CHECK (lock_type IN ('MONITOR_ENTER', 'MONITOR_WAIT', 'THREAD_PARK')),
    class_name   VARCHAR(500) NOT NULL,
    event_count  BIGINT NOT NULL,
    total_ms     DOUBLE PRECISION NOT NULL,
    max_ms       DOUBLE PRECISION NOT NULL,
    stack_frames JSONB
);
CREATE INDEX ix_lock_contention ON lock_contention (recording_id, rank);

CREATE TABLE allocation_hotspot (
    id           BIGINT PRIMARY KEY,
    recording_id UUID NOT NULL REFERENCES jfr_recording (id) ON DELETE CASCADE,
    rank         INT  NOT NULL,
    class_name   VARCHAR(500) NOT NULL,
    weight_bytes BIGINT NOT NULL,
    samples      BIGINT NOT NULL,
    stack_frames JSONB
);
CREATE INDEX ix_allocation_hotspot ON allocation_hotspot (recording_id, rank);

CREATE TABLE exception_hotspot (
    id              BIGINT PRIMARY KEY,
    recording_id    UUID NOT NULL REFERENCES jfr_recording (id) ON DELETE CASCADE,
    rank            INT  NOT NULL,
    exception_class VARCHAR(500) NOT NULL,
    event_count     BIGINT NOT NULL,
    sample_message  VARCHAR(500),
    stack_frames    JSONB
);
CREATE INDEX ix_exception_hotspot ON exception_hotspot (recording_id, rank);

CREATE TABLE io_hotspot (
    id           BIGINT PRIMARY KEY,
    recording_id UUID NOT NULL REFERENCES jfr_recording (id) ON DELETE CASCADE,
    rank         INT  NOT NULL,
    io_type      VARCHAR(16) NOT NULL CHECK (io_type IN ('FILE_READ', 'FILE_WRITE', 'SOCKET_READ', 'SOCKET_WRITE')),
    target       TEXT NOT NULL,
    bytes        BIGINT NOT NULL,
    event_count  BIGINT NOT NULL,
    total_ms     DOUBLE PRECISION NOT NULL,
    max_ms       DOUBLE PRECISION NOT NULL
);
CREATE INDEX ix_io_hotspot ON io_hotspot (recording_id, rank);
