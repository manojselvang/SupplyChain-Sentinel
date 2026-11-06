# Cosign Artifact Integrity Integration

Sign and verify container artifacts with Cosign, then submit the CI-reported verification results to SupplyChain Sentinel.

## Important Trust Boundary

SupplyChain Sentinel **does not** independently verify cryptographic signatures. The Cosign verification is performed by your CI pipeline. The backend stores the CI-reported verification evidence for audit and release-decision purposes.

## Prerequisites

- SupplyChain Sentinel backend running and accessible from CI
- `CI_INGESTION_API_KEY` configured on the server (minimum 32 bytes)
- Cosign CLI installed in the CI environment
- An `applicationKey` matching a registered application
- A pipeline run with a known artifact digest

## API Contract

**Endpoint:** `POST /api/v1/integrations/artifact-verifications`
**Auth header:** `X-CI-INGESTION-KEY: <key>`
**Content-Type:** `application/json`

## Verification Modes

### Keyless Verification (OIDC)

```bash
# Verify using certificate identity and OIDC issuer
cosign verify registry.example.com/app@sha256:abc123... \
  --certificate-identity "https://github.com/org/repo/.github/workflows/ci.yml@refs/heads/main" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com"
```

### Key-Based Verification

```bash
# Verify using a trusted public key
cosign verify --key cosign.pub registry.example.com/app@sha256:abc123...
```

## Submitting Verification Results

```bash
SCS_URL="http://supplychain-sentinel:8080"
SCS_KEY="${CI_INGESTION_API_KEY}"
APP_KEY="payment-service"
IMAGE_REF="registry.example.com/app@sha256:abc123def456..."
ARTIFACT_DIGEST="abc123def456..."
COSIGN_VERSION=$(cosign version | head -1 | awk '{print $NF}')
VERIFIED_AT=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Keyless
jq -n \
  --arg appKey "$APP_KEY" \
  --arg ref    "$IMAGE_REF" \
  --arg digest "$ARTIFACT_DIGEST" \
  --arg cosign "$COSIGN_VERSION" \
  --arg ts     "$VERIFIED_AT" \
  '{
    applicationKey: $appKey,
    artifactReference: $ref,
    artifactDigest: $digest,
    verificationMode: "KEYLESS",
    verificationStatus: "VERIFIED",
    signerIdentity: "https://github.com/org/repo/.github/workflows/ci.yml@refs/heads/main",
    oidcIssuer: "https://token.actions.githubusercontent.com",
    cosignVersion: $cosign,
    verifiedAt: $ts,
    reportedAt: $ts
  }' | curl -sf -X POST "${SCS_URL}/api/v1/integrations/artifact-verifications" \
    -H 'Content-Type: application/json' \
    -H "X-CI-INGESTION-KEY: ${SCS_KEY}" \
    -d @-
```

## GitHub Actions Example

```yaml
- name: Verify artifact with Cosign (keyless)
  id: cosign-verify
  env:
    SCS_KEY: ${{ secrets.SCS_INGESTION_KEY }}
  run: |
    IMAGE_REF="ghcr.io/org/app@sha256:${{ steps.build.outputs.digest }}"

    if cosign verify "$IMAGE_REF" \
      --certificate-identity "https://github.com/org/repo/.github/workflows/ci.yml@refs/heads/main" \
      --certificate-oidc-issuer "https://token.actions.githubusercontent.com"; then

      STATUS="VERIFIED"
    else
      STATUS="FAILED"
    fi

    COSIGN_VER=$(cosign version | head -1 | awk '{print $NF}')
    NOW=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    jq -n \
      --arg appKey "payment-service" \
      --arg ref    "$IMAGE_REF" \
      --arg digest "${{ steps.build.outputs.digest }}" \
      --arg cosign "$COSIGN_VER" \
      --arg ts     "$NOW" \
      --arg status "$STATUS" \
      --arg signer "https://github.com/org/repo/.github/workflows/ci.yml@refs/heads/main" \
      --arg issuer "https://token.actions.githubusercontent.com" \
      '{applicationKey:$appKey, artifactReference:$ref, artifactDigest:$digest,
        verificationMode:"KEYLESS", verificationStatus:$status,
        signerIdentity:$signer, oidcIssuer:$issuer,
        cosignVersion:$cosign, verifiedAt:$ts, reportedAt:$ts}' \
    | curl -sf -X POST "${{ vars.SCS_URL }}/api/v1/integrations/artifact-verifications" \
      -H 'Content-Type: application/json' \
      -H "X-CI-INGESTION-KEY: ${SCS_KEY}" \
      -d @-
```

## Validation Rules

| Rule | Description |
|------|-------------|
| Immutable reference | `artifactReference` must contain `@sha256:` — mutable tags are rejected |
| Digest match | `artifactDigest` must match the digest in `artifactReference` |
| Pipeline match | If `pipelineRunId` is provided, it must belong to the same application |
| VERIFIED + KEYLESS | Requires `signerIdentity` and `oidcIssuer` |
| VERIFIED + KEY | Requires `signerIdentity` and `publicKeyFingerprint` |
| FAILED/ERROR | Signer metadata is optional |
| Historical retention | Each verification attempt creates a separate record — no overwrites |

## Verification Statuses

| Status | Meaning |
|--------|---------|
| VERIFIED | CI pipeline's Cosign verification succeeded with complete metadata |
| FAILED | Cosign verification command returned a non-zero exit code |
| MISSING_SIGNATURE | No signature found for the artifact |
| ERROR | Verification could not complete (network, config, tool error) |

## Notes

- Never log or print private signing keys, API keys, or Cosign private key material.
- Verification evidence stored as JSONB is capped at 10KB.
- All verification records are labelled as CI-reported in the UI.
