CREATE TABLE rule_finding (
    id           BIGINT PRIMARY KEY,
    recording_id UUID NOT NULL REFERENCES jfr_recording (id) ON DELETE CASCADE,
    rule_id      VARCHAR(128) NOT NULL,
    rule_name    VARCHAR(255) NOT NULL,
    topic        VARCHAR(64),
    severity     VARCHAR(8) NOT NULL CHECK (severity IN ('OK', 'INFO', 'WARNING')),
    score        DOUBLE PRECISION,
    summary      TEXT,
    explanation  TEXT,
    solution     TEXT
);
CREATE INDEX ix_rule_finding ON rule_finding (recording_id, severity);
