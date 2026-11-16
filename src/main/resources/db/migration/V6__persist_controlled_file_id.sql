ALTER TABLE analysis_session
    ADD COLUMN file_id VARCHAR(1024);

-- Existing sessions predate the controlled-id association. They remain readable
-- as historical snapshots, but cannot be resumed until recreated through the
-- catalog; new sessions always persist file_id from the catalog.
