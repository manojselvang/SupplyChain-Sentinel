-- ============================================================
-- V6: Scan-to-finding junction table for historical traceability
-- ============================================================

CREATE TABLE IF NOT EXISTS security_scan_findings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id     UUID NOT NULL REFERENCES security_scans(id)     ON DELETE CASCADE,
    finding_id  UUID NOT NULL REFERENCES security_findings(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    CONSTRAINT uq_scan_finding UNIQUE (scan_id, finding_id)
);

CREATE INDEX IF NOT EXISTS idx_ssf_scan_id    ON security_scan_findings(scan_id);
CREATE INDEX IF NOT EXISTS idx_ssf_finding_id ON security_scan_findings(finding_id);
