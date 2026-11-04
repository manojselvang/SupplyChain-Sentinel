-- ============================================================
-- V3: Application inventory
-- ============================================================

CREATE TABLE IF NOT EXISTS applications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_key     VARCHAR(100)  NOT NULL UNIQUE,
    name                VARCHAR(255)  NOT NULL,
    description         TEXT,
    business_unit       VARCHAR(255),
    business_owner      VARCHAR(255),
    technical_owner     VARCHAR(255),
    criticality         VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM',
    lifecycle_status    VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    data_classification VARCHAR(20)   NOT NULL DEFAULT 'INTERNAL',
    repository_url      VARCHAR(500),
    primary_language    VARCHAR(100),
    hosting_environment VARCHAR(20)   NOT NULL DEFAULT 'ON_PREMISES',
    internet_facing     BOOLEAN       NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

-- Enum check constraints
ALTER TABLE applications ADD CONSTRAINT chk_app_criticality
    CHECK (criticality IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'));
ALTER TABLE applications ADD CONSTRAINT chk_app_lifecycle_status
    CHECK (lifecycle_status IN ('PLANNING', 'ACTIVE', 'DEPRECATED', 'RETIRED'));
ALTER TABLE applications ADD CONSTRAINT chk_app_data_classification
    CHECK (data_classification IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'));
ALTER TABLE applications ADD CONSTRAINT chk_app_hosting_environment
    CHECK (hosting_environment IN ('ON_PREMISES', 'AWS', 'AZURE', 'GCP', 'HYBRID'));

-- Indexes for search and filtering
CREATE INDEX IF NOT EXISTS idx_applications_name            ON applications(name);
CREATE INDEX IF NOT EXISTS idx_applications_application_key  ON applications(application_key);
CREATE INDEX IF NOT EXISTS idx_applications_criticality      ON applications(criticality);
CREATE INDEX IF NOT EXISTS idx_applications_lifecycle_status ON applications(lifecycle_status);
