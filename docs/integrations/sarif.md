# SARIF Integration

This guide shows how to submit SARIF (Static Analysis Results Interchange Format) reports to SupplyChain Sentinel.

## Prerequisites

- SupplyChain Sentinel backend running and accessible from your CI/CD pipeline
- An `applicationKey` matching a registered application in the inventory
- The `CI_INGESTION_API_KEY` configured on the server

## API Contract

**Endpoint:** `POST /api/v1/integrations/security-scans`  
**Auth header:** `X-CI-INGESTION-KEY: <key>`  
**Content-Type:** `application/json`

## Tools That Produce SARIF

- GitHub CodeQL
- Semgrep
- ESLint (with @microsoft/eslint-formatter-sarif)
- Checkov
- Bandit (with bandit-sarif-formatter)
- Many other SAST tools

## Generating a SARIF Report

```bash
# CodeQL (via GitHub Actions or CLI)
codeql database create db --source-root .
codeql database analyze db --format=sarif-latest --output=results.sarif

# Semgrep
semgrep --config auto --sarif --output results.sarif .

# ESLint
npx eslint . --format @microsoft/eslint-formatter-sarif --output-file results.sarif
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
  --slurpfile report results.sarif \
  '{
    applicationKey: $appKey,
    scanner:       "codeql",
    reportFormat:  "SARIF",
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
- name: Run CodeQL analysis
  run: |
    codeql database create db --source-root .
    codeql database analyze db --format=sarif-latest --output=results.sarif

- name: Submit to SupplyChain Sentinel
  env:
    SCS_KEY: ${{ secrets.SCS_INGESTION_KEY }}
  run: |
    jq -n \
      --arg appKey "my-application" \
      --arg scanTime "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
      --slurpfile report results.sarif \
      '{applicationKey:$appKey, scanner:"codeql", reportFormat:"SARIF", scanTimestamp:$scanTime, report:$report[0]}' \
    | curl -sf -X POST "${{ vars.SCS_URL }}/api/v1/integrations/security-scans" \
      -H 'Content-Type: application/json' \
      -H "X-CI-INGESTION-KEY: ${SCS_KEY}" \
      -d @-
```

## SARIF Severity Mapping

SupplyChain Sentinel normalizes SARIF severity levels as follows:

| SARIF Level | SARIF `security-severity` (numeric) | Normalized Severity |
|-------------|-------------------------------------|---------------------|
| error       | ≥ 9.0                               | CRITICAL            |
| error       | 7.0–8.9                             | HIGH                |
| warning     | 4.0–6.9                             | MEDIUM              |
| note        | 0.1–3.9                             | LOW                 |
| note        | 0.0 or absent                       | INFO                |

When the `properties.security-severity` field is present (a CVSS-like 0–10 score), it takes precedence over the SARIF `level` field.

## Notes

- The `report` field must contain the complete SARIF JSON object (version 2.1.0).
- Findings are deduplicated using a fingerprint of `(applicationId, scanner, ruleId, location)`.
- The `scanner` field is a free-text label (e.g. "codeql", "semgrep", "eslint") — it does not need to match any predefined enum.
- The `X-CI-INGESTION-KEY` header value is never logged or echoed.
