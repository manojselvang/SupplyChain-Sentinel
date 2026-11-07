-- ============================================================
-- V8: Release assurance — assessments, approvals, audit trail
-- ============================================================

-- ── Release assessments ──────────────────────────────────────

CREATE TABLE IF NOT EXISTS release_assessments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      UUID NOT NULL REFERENCES applications(id) ON DELETE RESTRICT,
    pipeline_run_id     UUID NOT NULL REFERENCES pipeline_runs(id) ON DELETE RESTRICT,
    artifact_digest     VARCHAR(64) NOT NULL,
    risk_score          INTEGER NOT NULL DEFAULT 0,
    decision            VARCHAR(10) NOT NULL,
    policy_version      VARCHAR(50) NOT NULL,
    assessment_status   VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    hard_block_reasons  JSONB,
    score_breakdown     JSONB,
    evidence_snapshot   JSONB,
    requested_by        VARCHAR(255),
    assessed_at         TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

ALTER TABLE release_assessments ADD CONSTRAINT chk_ra_decision
    CHECK (decision IN ('PASS', 'WARN', 'BLOCK'));
ALTER TABLE release_assessments ADD CONSTRAINT chk_ra_status
    CHECK (assessment_status IN ('PENDING_APPROVAL', 'READY_FOR_DEPLOYMENT', 'REJECTED', 'BLOCKED'));

CREATE INDEX IF NOT EXISTS idx_ra_app_id     ON release_assessments(application_id);
CREATE INDEX IF NOT EXISTS idx_ra_pipeline_id ON release_assessments(pipeline_run_id);
CREATE INDEX IF NOT EXISTS idx_ra_decision    ON release_assessments(decision);
CREATE INDEX IF NOT EXISTS idx_ra_status     ON release_assessments(assessment_status);
CREATE INDEX IF NOT EXISTS idx_ra_digest     ON release_assessments(artifact_digest);

-- ── Release approvals ──────────────────────────────────────

CREATE TABLE IF NOT EXISTS release_approvals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id    UUID NOT NULL REFERENCES release_assessments(id) ON DELETE RESTRICT,
    approver_id      UUID,
    decision         VARCHAR(10) NOT NULL,
    justification    TEXT,
    approved_at      TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

ALTER TABLE release_approvals ADD CONSTRAINT chk_rap_decision
    CHECK (decision IN ('APPROVED', 'REJECTED'));

CREATE INDEX IF NOT EXISTS idx_rap_assessment_id ON release_approvals(assessment_id);
CREATE INDEX IF NOT EXISTS idx_rap_approver_id   ON release_approvals(approver_id);

-- ── Audit events ────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS audit_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        UUID,
    actor_role      VARCHAR(50),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       UUID,
    event_details   JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

CREATE INDEX IF NOT EXISTS idx_ae_actor_id    ON audit_events(actor_id);
CREATE INDEX IF NOT EXISTS idx_ae_entity_type  ON audit_events(entity_type);
CREATE INDEX IF NOT EXISTS idx_ae_entity_id    ON audit_events(entity_id);
CREATE INDEX IF NOT EXISTS idx_ae_action       ON audit_events(action);
CREATE INDEX IF NOT EXISTS idx_ae_occurred_at   ON audit_events(occurred_at);
