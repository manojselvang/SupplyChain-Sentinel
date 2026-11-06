# Syft SBOM Integration

Generate CycloneDX SBOMs with Syft and submit them to SupplyChain Sentinel.

## Prerequisites

- SupplyChain Sentinel backend running and accessible from CI
- `CI_INGESTION_API_KEY` configured on the server (minimum 32 bytes)
- An `applicationKey` matching a registered application

## API Contract

**Endpoint:** `POST /api/v1/integrations/sboms`
**Auth header:** `X-CI-INGESTION-KEY: <key>`
**Content-Type:** `application/json`

## Generating an SBOM with Syft

```bash
# Scan a container image (pinned by digest)
syft scan registry.example.com/app:1.0@sha256:abc123... -o cyclonedx-json@1.7=sbom.cdx.json

# Scan a local directory
syft scan dir:./ -o cyclonedx-json@1.7=sbom.cdx.json

# Scan a tarball
syft scan app.tar -o cyclonedx-json@1.7=sbom.cdx.json
```

## Submitting to SupplyChain Sentinel

```bash
SCS_URL="http://supplychain-sentinel:8080"
SCS_KEY="${CI_INGESTION_API_KEY}"
APP_KEY="payment-service"
ARTIFACT_DIGEST=$(docker inspect --format='{{index .RepoDigests 0}}' app:latest | grep -oP 'sha256:\K.*')

# Build payload using jq — embed the SBOM as a JSON object
jq -n \
  --arg appKey   "$APP_KEY" \
  --arg digest   "$ARTIFACT_DIGEST" \
  --slurpfile sbom sbom.cdx.json \
  '{
    applicationKey: $appKey,
    artifactDigest: $digest,
    sbom: $sbom[0]
  }' > payload.json

# Submit — never echo the key
curl -sf -X POST "${SCS_URL}/api/v1/integrations/sboms" \
  -H 'Content-Type: application/json' \
  -H "X-CI-INGESTION-KEY: ${SCS_KEY}" \
  -d @payload.json
```

## Supported Formats

| Format | Spec Version | Status |
|--------|-------------|--------|
| CycloneDX JSON | 1.7 | ✅ Supported |
| CycloneDX JSON | 1.6 | ✅ Supported (backward compat) |
| SPDX | any | ❌ Not supported in this phase |

## Server-Side Validation

SupplyChain Sentinel validates:
- `bomFormat` must be `CycloneDX`
- `specVersion` must be `1.6` or `1.7`
- Document SHA-256 is calculated server-side (never trusts caller)
- Duplicate submissions (same app + pipeline + document hash) are idempotent

## GitHub Actions Example

```yaml
- name: Generate SBOM
  run: syft scan dir:./ -o cyclonedx-json@1.7=sbom.cdx.json

- name: Submit SBOM to SupplyChain Sentinel
  env:
    SCS_KEY: ${{ secrets.SCS_INGESTION_KEY }}
  run: |
    jq -n \
      --arg appKey "payment-service" \
      --arg digest "" \
      --slurpfile sbom sbom.cdx.json \
      '{applicationKey:$appKey, sbom:$sbom[0]}' \
    | curl -sf -X POST "${{ vars.SCS_URL }}/api/v1/integrations/sboms" \
      -H 'Content-Type: application/json' \
      -H "X-CI-INGESTION-KEY: ${SCS_KEY}" \
      -d @-
```

## Notes

- The `sbom` field must contain the complete CycloneDX JSON object.
- Components are extracted and normalized into `sbom_components` for search and reporting.
- The raw document is stored in PostgreSQL as JSONB for audit purposes.
- SBOM history is preserved — uploading a new SBOM does not overwrite previous versions.
