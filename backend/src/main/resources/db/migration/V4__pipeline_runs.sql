-- ============================================================
-- V4: Pipeline runs — CI/CD build metadata ingestion
-- ============================================================

CREATE TABLE IF NOT EXISTS pipeline_runs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    provider            VARCHAR(20)  NOT NULL,
    external_run_id     VARCHAR(255) NOT NULL,
    workflow_name       VARCHAR(255),
    repository_url      VARCHAR(500) NOT NULL,
    branch_name         VARCHAR(255),
    commit_sha          VARCHAR(64),
    commit_message      TEXT,
    run_number          VARCHAR(50),
    status              VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    trigger_event       VARCHAR(100),
    started_at          TIMESTAMPTZ  NOT NULL,
    completed_at        TIMESTAMPTZ,
    artifact_name       VARCHAR(255),
    artifact_version    VARCHAR(100),
    artifact_digest     VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

-- Unique constraint: one record per provider + repository + external run ID
ALTER TABLE pipeline_runs ADD CONSTRAINT uq_pipeline_runs_external
    UNIQUE (provider, repository_url, external_run_id);

-- Enum check constraints
ALTER TABLE pipeline_runs ADD CONSTRAINT chk_pr_provider
    CHECK (provider IN ('JENKINS', 'GITHUB_ACTIONS'));
ALTER TABLE pipeline_runs ADD CONSTRAINT chk_pr_status
    CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED'));

-- Indexes
CREATE INDEX IF NOT EXISTS idx_pr_application_id ON pipeline_runs(application_id);
CREATE INDEX IF NOT EXISTS idx_pr_status          ON pipeline_runs(status);
CREATE INDEX IF NOT EXISTS idx_pr_provider        ON pipeline_runs(provider);
CREATE INDEX IF NOT EXISTS idx_pr_commit_sha      ON pipeline_runs(commit_sha);
