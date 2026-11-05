-- ============================================================
-- V5: Security findings — scan ingestion and vulnerability tracking
-- ============================================================

CREATE TABLE IF NOT EXISTS security_scans (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id   UUID NOT NULL REFERENCES applications(id) ON DELETE RESTRICT,
    pipeline_run_id  UUID REFERENCES pipeline_runs(id) ON DELETE SET NULL,
    scanner          VARCHAR(50)  NOT NULL,
    report_format    VARCHAR(20)  NOT NULL,
    artifact_digest  VARCHAR(64),
    scan_timestamp   TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

ALTER TABLE security_scans ADD CONSTRAINT chk_scan_format
    CHECK (report_format IN ('TRIVY_JSON', 'SARIF'));

CREATE INDEX IF NOT EXISTS idx_scans_application_id  ON security_scans(application_id);
CREATE INDEX IF NOT EXISTS idx_scans_scanner         ON security_scans(scanner);

-- ── Security findings ──────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_findings (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id       UUID NOT NULL REFERENCES applications(id) ON DELETE RESTRICT,
    pipeline_run_id      UUID REFERENCES pipeline_runs(id) ON DELETE SET NULL,
    fingerprint          VARCHAR(64) NOT NULL,
    scanner              VARCHAR(50) NOT NULL,
    vulnerability_id     VARCHAR(100),
    rule_id              VARCHAR(255),
    title                VARCHAR(500) NOT NULL,
    description          TEXT,
    severity             VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    affected_package     VARCHAR(255),
    installed_version    VARCHAR(100),
    fixed_version        VARCHAR(100),
    affected_location    VARCHAR(500),
    remediation_url      VARCHAR(500),
    status               VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    first_seen           TIMESTAMPTZ  NOT NULL,
    last_seen            TIMESTAMPTZ  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

-- Each application has at most one finding record per fingerprint
ALTER TABLE security_findings ADD CONSTRAINT uq_findings_app_fingerprint
    UNIQUE (application_id, fingerprint);

ALTER TABLE security_findings ADD CONSTRAINT chk_finding_severity
    CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO', 'UNKNOWN'));

ALTER TABLE security_findings ADD CONSTRAINT chk_finding_status
    CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'REMEDIATED', 'ACCEPTED'));

CREATE INDEX IF NOT EXISTS idx_findings_application_id  ON security_findings(application_id);
CREATE INDEX IF NOT EXISTS idx_findings_severity        ON security_findings(severity);
CREATE INDEX IF NOT EXISTS idx_findings_status          ON security_findings(status);
CREATE INDEX IF NOT EXISTS idx_findings_scanner         ON security_findings(scanner);
CREATE INDEX IF NOT EXISTS idx_findings_vulnerability_id ON security_findings(vulnerability_id);
CREATE INDEX IF NOT EXISTS idx_findings_fingerprint     ON security_findings(fingerprint);
