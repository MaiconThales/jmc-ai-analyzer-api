CREATE TABLE diagnosis_item (
    id           BIGINT PRIMARY KEY,
    recording_id UUID NOT NULL REFERENCES jfr_recording (id) ON DELETE CASCADE,
    priority     VARCHAR(8)  NOT NULL CHECK (priority IN ('ALTA', 'MEDIA', 'BAIXA')),
    category     VARCHAR(16) NOT NULL CHECK (category IN ('MEMORIA', 'GC', 'CPU', 'THREADS', 'LOCKS', 'EXCECOES', 'IO',
                                                          'CONFIG_JVM', 'AMBIENTE', 'GRAVACAO')),
    source       VARCHAR(160) NOT NULL,
    score        INT NOT NULL CHECK (score BETWEEN 0 AND 100),
    title        VARCHAR(255) NOT NULL,
    evidence     TEXT,
    location     JSONB,
    action       TEXT,
    domain_ref   VARCHAR(32)
);
CREATE INDEX ix_diagnosis_item ON diagnosis_item (recording_id, priority);

CREATE TABLE coverage_item (
    id             BIGINT PRIMARY KEY,
    recording_id   UUID NOT NULL REFERENCES jfr_recording (id) ON DELETE CASCADE,
    domain         VARCHAR(16) NOT NULL CHECK (domain IN ('CPU', 'HEAP', 'GC', 'THREADS', 'LOCKS', 'HOT_METHODS',
                                                          'ALLOCATION', 'EXCEPTIONS', 'FILE_IO', 'SOCKET_IO', 'LEAK',
                                                          'THREAD_CPU')),
    status         VARCHAR(16) NOT NULL CHECK (status IN ('OK', 'INSUFICIENTE', 'AUSENTE')),
    reason         TEXT,
    recommendation TEXT
);
CREATE INDEX ix_coverage_item ON coverage_item (recording_id);
