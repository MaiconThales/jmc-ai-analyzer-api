ALTER TABLE analysis_stage
    DROP CONSTRAINT ck_analysis_stage_status;

ALTER TABLE analysis_stage
    ADD CONSTRAINT ck_analysis_stage_status CHECK (
        status IN ('PENDING', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED',
                   'SUCCESS', 'TIMEOUT', 'OVERLOADED')
    );
