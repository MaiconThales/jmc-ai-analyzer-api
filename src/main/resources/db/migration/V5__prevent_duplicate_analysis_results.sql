-- Existing deployments may contain replays from before this constraint existed.
-- Keep the oldest row (created_at, then UUID as a deterministic tie breaker);
-- this is an integrity migration and does not remove distinct valid results.
DELETE FROM analysis_result
WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (
            PARTITION BY analysis_id, result_type
            ORDER BY created_at ASC, id ASC
        ) AS duplicate_number
        FROM analysis_result
    ) duplicates
    WHERE duplicate_number > 1
);

ALTER TABLE analysis_result
    ADD CONSTRAINT uk_analysis_result_session_type UNIQUE (analysis_id, result_type);
