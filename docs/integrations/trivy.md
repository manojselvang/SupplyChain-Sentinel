# Trivy Integration

This guide shows how to generate Trivy scanner reports and submit them to SupplyChain Sentinel.

## Prerequisites

- SupplyChain Sentinel backend running and accessible from your CI/CD pipeline
- An `applicationKey` matching a registered application in the inventory
- The `CI_INGESTION_API_KEY` configured on the server

## API Contract

**Endpoint:** `POST /api/v1/integrations/security-scans`  
**Auth header:** `X-CI-INGESTION-KEY: <key>`  
**Content-Type:** `application/json`

## Generating a Trivy Report

Trivy can scan container images, filesystems, and repositories. Generate a JSON report:

```bash
# Scan a container image
trivy image --format json --output trivy-report.json my-registry/my-app:latest

# Scan a filesystem
trivy fs --format json --output trivy-report.json /path/to/project

# Scan a repository
trivy repo --format json --output trivy-report.json https://github.com/org/repo
```

## Submitting to SupplyChain Sentinel

```bash
SCS_URL="http://supplychain-sentinel:8080"
SCS_KEY="${CI_INGESTION_API_KEY}"
APP_KEY="my-application"
SCAN_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Build the ingestion payload using jq
jq -n \
  --arg appKey   "$APP_KEY" \
  --arg scanTime "$SCAN_TIME" \
  --slurpfile report trivy-report.json \
  '{
    applicationKey: $appKey,
    scanner:       "trivy",
    reportFormat:  "TRIVY_JSON",
    scanTimestamp: $scanTime,
    report:        $report[0]
  }' > payload.json

# Submit — never echo the key
curl -sf -X POST "${SCS_URL}/api/v1/integrations/security-scans" \
  -H 'Content-Type: application/json' \
  -H "X-CI-INGESTION-KEY: ${SCS_KEY}" \
  -d @payload.json
```

## GitHub Actions Example

```yaml
- name: Run Trivy scan
  run: trivy image --format json --output trivy-report.json my-app:latest

- name: Submit to SupplyChain Sentinel
  env:
    SCS_KEY: ${{ secrets.SCS_INGESTION_KEY }}
  run: |
    jq -n \
      --arg appKey "my-application" \
      --arg scanTime "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
      --slurpfile report trivy-report.json \
      '{applicationKey:$appKey, scanner:"trivy", reportFormat:"TRIVY_JSON", scanTimestamp:$scanTime, report:$report[0]}' \
    | curl -sf -X POST "${{ vars.SCS_URL }}/api/v1/integrations/security-scans" \
      -H 'Content-Type: application/json' \
      -H "X-CI-INGESTION-KEY: ${SCS_KEY}" \
      -d @-
```

## Jenkins Example

```groovy
sh 'trivy image --format json --output trivy-report.json my-app:latest'
withCredentials([string(credentialsId: 'scs-ingestion-key', variable: 'SCS_KEY')]) {
    sh """
        jq -n --arg appKey "my-application" --arg scanTime "\$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
          --slurpfile report trivy-report.json \
          '{applicationKey:\$appKey, scanner:"trivy", reportFormat:"TRIVY_JSON", scanTimestamp:\$scanTime, report:\$report[0]}' \
        | curl -sf -X POST '${SCS_URL}/api/v1/integrations/security-scans' \
          -H 'Content-Type: application/json' \
          -H "X-CI-INGESTION-KEY: \${SCS_KEY}" \
          -d @-
    """
}
```

## Notes

- The `report` field must contain the raw Trivy JSON output (the entire JSON object, not just the `Results` array).
- Findings are deduplicated using a fingerprint of `(applicationId, scanner, CVE, package, target)`. Repeated scans update `lastSeen` without creating duplicate records.
- The `X-CI-INGESTION-KEY` header value is never logged or echoed.
