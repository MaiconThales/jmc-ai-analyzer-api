CREATE TABLE leak_suspect (
    id             BIGINT PRIMARY KEY,
    recording_id   UUID NOT NULL REFERENCES jfr_recording (id) ON DELETE CASCADE,
    rank           INT  NOT NULL,
    class_name     VARCHAR(500) NOT NULL,
    samples        BIGINT NOT NULL,
    max_age_ms     BIGINT,
    max_array_size BIGINT,
    stack_frames   JSONB
);
CREATE INDEX ix_leak_suspect ON leak_suspect (recording_id, rank);

CREATE TABLE thread_cpu (
    id           BIGINT PRIMARY KEY,
    recording_id UUID NOT NULL REFERENCES jfr_recording (id) ON DELETE CASCADE,
    rank         INT  NOT NULL,
    thread_name  VARCHAR(500) NOT NULL,
    cpu_avg      DOUBLE PRECISION NOT NULL,
    cpu_max      DOUBLE PRECISION NOT NULL
);
CREATE INDEX ix_thread_cpu ON thread_cpu (recording_id, rank);
