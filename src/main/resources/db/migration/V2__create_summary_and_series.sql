CREATE TABLE recording_summary (
    recording_id            UUID PRIMARY KEY REFERENCES jfr_recording (id) ON DELETE CASCADE,
    cpu_jvm_avg             DOUBLE PRECISION,
    cpu_jvm_max             DOUBLE PRECISION,
    cpu_machine_avg         DOUBLE PRECISION,
    cpu_machine_max         DOUBLE PRECISION,
    heap_initial_bytes      BIGINT,
    heap_max_bytes          BIGINT,
    heap_used_peak_bytes    BIGINT,
    gc_young_collector      VARCHAR(64),
    gc_old_collector        VARCHAR(64),
    gc_count                INT,
    gc_pause_total_ms       DOUBLE PRECISION,
    gc_pause_avg_ms         DOUBLE PRECISION,
    gc_pause_p95_ms         DOUBLE PRECISION,
    gc_pause_max_ms         DOUBLE PRECISION,
    gc_pause_pct            DOUBLE PRECISION,
    thread_peak             INT,
    thread_daemon_peak      INT,
    threads_started         INT,
    threads_ended           INT,
    throwables_total        BIGINT,
    allocation_rate_bytes_s DOUBLE PRECISION,
    execution_samples_total BIGINT,
    domains_available       VARCHAR(255),
    health_score            INT CHECK (health_score BETWEEN 0 AND 100),
    suggested_command       TEXT
);

CREATE TABLE metric_sample (
    id           BIGINT PRIMARY KEY,
    recording_id UUID   NOT NULL REFERENCES jfr_recording (id) ON DELETE CASCADE,
    metric       VARCHAR(32) NOT NULL CHECK (metric IN ('CPU_JVM_USER', 'CPU_JVM_SYSTEM', 'CPU_MACHINE', 'HEAP_USED',
                                                        'HEAP_COMMITTED', 'THREADS_ACTIVE', 'THREADS_DAEMON')),
    ts           TIMESTAMPTZ NOT NULL,
    avg_value    DOUBLE PRECISION NOT NULL,
    max_value    DOUBLE PRECISION NOT NULL
);

CREATE INDEX ix_metric_sample ON metric_sample (recording_id, metric, ts);
