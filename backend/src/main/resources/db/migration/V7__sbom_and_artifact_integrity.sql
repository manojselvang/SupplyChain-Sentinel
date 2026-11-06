-- ============================================================
-- V7: SBOM management and artifact integrity verification
-- ============================================================

-- ── SBOM documents ──────────────────────────────────────────

CREATE TABLE IF NOT EXISTS sbom_documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id    UUID NOT NULL REFERENCES applications(id) ON DELETE RESTRICT,
    pipeline_run_id   UUID REFERENCES pipeline_runs(id) ON DELETE SET NULL,
    artifact_digest   VARCHAR(64),
    format            VARCHAR(30) NOT NULL DEFAULT 'CYCLONEDX_JSON',
    spec_version      VARCHAR(10),
    serial_number     VARCHAR(255),
    document_version  INTEGER,
    document_sha256   VARCHAR(64) NOT NULL,
    component_count   INTEGER NOT NULL DEFAULT 0,
    generated_at      TIMESTAMPTZ,
    ingested_at       TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    raw_document      JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

ALTER TABLE sbom_documents ADD CONSTRAINT chk_sbom_format
    CHECK (format IN ('CYCLONEDX_JSON'));

-- Idempotency: same app + pipeline + document hash → same record
ALTER TABLE sbom_documents ADD CONSTRAINT uq_sbom_doc
    UNIQUE (application_id, pipeline_run_id, document_sha256);

CREATE INDEX IF NOT EXISTS idx_sbom_app_id     ON sbom_documents(application_id);
CREATE INDEX IF NOT EXISTS idx_sbom_digest      ON sbom_documents(artifact_digest);
CREATE INDEX IF NOT EXISTS idx_sbom_doc_sha256  ON sbom_documents(document_sha256);

-- ── SBOM components ──────────────────────────────────────────

CREATE TABLE IF NOT EXISTS sbom_components (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sbom_id         UUID NOT NULL REFERENCES sbom_documents(id) ON DELETE CASCADE,
    bom_ref         VARCHAR(255),
    component_type  VARCHAR(50),
    group_name      VARCHAR(255),
    name            VARCHAR(500) NOT NULL,
    version         VARCHAR(100),
    purl            VARCHAR(500),
    cpe             VARCHAR(500),
    scope           VARCHAR(50),
    supplier        VARCHAR(500),
    licenses        JSONB,
    hashes          JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

-- No duplicates within the same SBOM document
ALTER TABLE sbom_components ADD CONSTRAINT uq_sbom_component
    UNIQUE (sbom_id, name, version, purl);

CREATE INDEX IF NOT EXISTS idx_sbc_sbom_id   ON sbom_components(sbom_id);
CREATE INDEX IF NOT EXISTS idx_sbc_name      ON sbom_components(name);
CREATE INDEX IF NOT EXISTS idx_sbc_purl      ON sbom_components(purl);

-- ── Artifact verifications (Cosign) ─────────────────────────

CREATE TABLE IF NOT EXISTS artifact_verifications (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id          UUID NOT NULL REFERENCES applications(id) ON DELETE RESTRICT,
    pipeline_run_id         UUID REFERENCES pipeline_runs(id) ON DELETE SET NULL,
    artifact_reference      VARCHAR(1000) NOT NULL,
    artifact_digest         VARCHAR(64),
    verification_tool       VARCHAR(30) NOT NULL DEFAULT 'COSIGN',
    verification_mode       VARCHAR(10) NOT NULL,
    verification_status     VARCHAR(20) NOT NULL,
    signer_identity         VARCHAR(500),
    oidc_issuer             VARCHAR(500),
    public_key_fingerprint  VARCHAR(64),
    cosign_version          VARCHAR(50),
    verified_at             TIMESTAMPTZ,
    reported_at             TIMESTAMPTZ NOT NULL,
    verification_evidence   JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

ALTER TABLE artifact_verifications ADD CONSTRAINT chk_av_tool
    CHECK (verification_tool IN ('COSIGN'));
ALTER TABLE artifact_verifications ADD CONSTRAINT chk_av_mode
    CHECK (verification_mode IN ('KEY', 'KEYLESS'));
ALTER TABLE artifact_verifications ADD CONSTRAINT chk_av_status
    CHECK (verification_status IN ('VERIFIED', 'FAILED', 'MISSING_SIGNATURE', 'ERROR'));

CREATE INDEX IF NOT EXISTS idx_av_app_id     ON artifact_verifications(application_id);
CREATE INDEX IF NOT EXISTS idx_av_pipeline_id ON artifact_verifications(pipeline_run_id);
CREATE INDEX IF NOT EXISTS idx_av_digest      ON artifact_verifications(artifact_digest);
CREATE INDEX IF NOT EXISTS idx_av_status      ON artifact_verifications(verification_status);
