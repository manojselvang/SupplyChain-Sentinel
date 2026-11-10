-- ============================================================
-- V9: Argo CD deployment tracking
-- ============================================================

CREATE TABLE IF NOT EXISTS deployment_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      UUID NOT NULL REFERENCES applications(id) ON DELETE RESTRICT,
    pipeline_run_id     UUID REFERENCES pipeline_runs(id) ON DELETE SET NULL,
    release_assessment_id UUID REFERENCES release_assessments(id) ON DELETE SET NULL,
    argocd_application  VARCHAR(255) NOT NULL,
    argocd_project      VARCHAR(255),
    environment         VARCHAR(50) NOT NULL,
    cluster             VARCHAR(255),
    namespace           VARCHAR(255),
    git_repo_url        VARCHAR(500),
    git_revision        VARCHAR(255),
    image_reference     VARCHAR(1000) NOT NULL,
    image_digest        VARCHAR(64),
    sync_status         VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    health_status       VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    operation_phase     VARCHAR(50),
    deployed_at         TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    completed_at        TIMESTAMPTZ,
    external_event_id   VARCHAR(255) NOT NULL,
    webhook_evidence    JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

ALTER TABLE deployment_records ADD CONSTRAINT chk_dep_sync
    CHECK (sync_status IN ('SYNCED', 'OUT_OF_SYNC', 'PENDING', 'ERROR', 'UNKNOWN'));
ALTER TABLE deployment_records ADD CONSTRAINT chk_dep_health
    CHECK (health_status IN ('HEALTHY', 'DEGRADED', 'PROGRESSING', 'SUSPENDED', 'MISSING', 'UNKNOWN'));

ALTER TABLE deployment_records ADD CONSTRAINT uq_dep_external
    UNIQUE (argocd_application, external_event_id);

CREATE INDEX IF NOT EXISTS idx_dep_app_id     ON deployment_records(application_id);
CREATE INDEX IF NOT EXISTS idx_dep_pipeline_id  ON deployment_records(pipeline_run_id);
CREATE INDEX IF NOT EXISTS idx_dep_assessment_id ON deployment_records(release_assessment_id);
CREATE INDEX IF NOT EXISTS idx_dep_sync_status   ON deployment_records(sync_status);
CREATE INDEX IF NOT EXISTS idx_dep_health_status  ON deployment_records(health_status);
CREATE INDEX IF NOT EXISTS idx_dep_image_digest   ON deployment_records(image_digest);
CREATE INDEX IF NOT EXISTS idx_dep_environment    ON deployment_records(environment);
