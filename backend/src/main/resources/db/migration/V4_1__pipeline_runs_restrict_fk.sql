-- ============================================================
-- V4.1: Change pipeline_runs.application_id FK from
--       ON DELETE CASCADE to ON DELETE RESTRICT so that
--       deleting an application with pipeline history is
--       blocked at the database level.
-- ============================================================

ALTER TABLE pipeline_runs
    DROP CONSTRAINT IF EXISTS pipeline_runs_application_id_fkey;

ALTER TABLE pipeline_runs
    ADD CONSTRAINT pipeline_runs_application_id_fkey
    FOREIGN KEY (application_id)
    REFERENCES applications(id)
    ON DELETE RESTRICT;
