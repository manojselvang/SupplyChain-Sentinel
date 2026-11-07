# GitHub Actions Integration

Complete CI/CD pipeline integration with SupplyChain Sentinel — pipeline runs, security scans, SBOM, Cosign verification, and release gate evaluation.

## Prerequisites

- SupplyChain Sentinel backend running
- Repository secrets: `SCS_INGESTION_KEY`, `SCS_URL` (variable)
- Syft, Trivy, and Cosign available on runners

## Complete Workflow

`.github/workflows/ci-release.yml`

```yaml
name: CI + Release Gate

on:
  push:
    branches: [main]

jobs:
  build-and-gate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build and Test
        run: |
          mvn clean package -DskipTests -B
          mvn test -B

      - name: Run Trivy Scan
        run: trivy fs --scanners vuln --format json --output trivy-report.json ./backend

      - name: Generate SBOM
        run: syft scan dir:./backend -o cyclonedx-json@1.7=sbom.cdx.json

      - name: Build and Push Image
        id: build
        run: |
          docker build -t ghcr.io/${{ github.repository }}:latest .
          docker push ghcr.io/${{ github.repository }}:latest
          DIGEST=$(docker inspect --format='{{index .RepoDigests 0}}' ghcr.io/${{ github.repository }}:latest | grep -oP 'sha256:\K.*')
          echo "digest=$DIGEST" >> "$GITHUB_OUTPUT"

      - name: Cosign Verification
        env:
          SCS_KEY: ${{ secrets.SCS_INGESTION_KEY }}
        run: |
          REF="ghcr.io/${{ github.repository }}@sha256:${{ steps.build.outputs.digest }}"
          COSIGN_VER=$(cosign version | head -1 | awk '{print $NF}')
          NOW=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

          if cosign verify "$REF" \
            --certificate-identity "https://github.com/${{ github.repository }}/.github/workflows/ci-release.yml@refs/heads/main" \
            --certificate-oidc-issuer "https://token.actions.githubusercontent.com"; then
            STATUS="VERIFIED"
          else
            STATUS="FAILED"
          fi

          jq -n \
            --arg appKey "${{ vars.SCS_APP_KEY }}" \
            --arg ref "$REF" \
            --arg digest "${{ steps.build.outputs.digest }}" \
            --arg cosign "$COSIGN_VER" \
            --arg ts "$NOW" \
            --arg status "$STATUS" \
            --arg signer "https://github.com/${{ github.repository }}/.github/workflows/ci-release.yml@refs/heads/main" \
            --arg issuer "https://token.actions.githubusercontent.com" \
            '{applicationKey:$appKey, artifactReference:$ref, artifactDigest:$digest,
              verificationMode:"KEYLESS", verificationStatus:$status,
              signerIdentity:$signer, oidcIssuer:$issuer,
              cosignVersion:$cosign, verifiedAt:$ts, reportedAt:$ts}' \
          | curl -sf -X POST "${{ vars.SCS_URL }}/api/v1/integrations/artifact-verifications" \
            -H 'Content-Type: application/json' \
            -H "X-CI-INGESTION-KEY: ${SCS_KEY}" \
            -d @-

      - name: Submit Trivy Report
        env:
          SCS_KEY: ${{ secrets.SCS_INGESTION_KEY }}
        run: |
          jq -n --arg appKey "${{ vars.SCS_APP_KEY }}" \
            --slurpfile report trivy-report.json \
            '{applicationKey:$appKey, scanner:"trivy", reportFormat:"TRIVY_JSON", scanTimestamp:"$(date -u +%Y-%m-%dT%H:%M:%SZ)", report:$report[0]}' \
          | curl -sf -X POST "${{ vars.SCS_URL }}/api/v1/integrations/security-scans" \
            -H 'Content-Type: application/json' \
            -H "X-CI-INGESTION-KEY: ${SCS_KEY}" \
            -d @-

      - name: Submit SBOM
        env:
          SCS_KEY: ${{ secrets.SCS_INGESTION_KEY }}
        run: |
          jq -n --arg appKey "${{ vars.SCS_APP_KEY }}" \
            --arg digest "${{ steps.build.outputs.digest }}" \
            --slurpfile sbom sbom.cdx.json \
            '{applicationKey:$appKey, artifactDigest:$digest, sbom:$sbom[0]}' \
          | curl -sf -X POST "${{ vars.SCS_URL }}/api/v1/integrations/sboms" \
            -H 'Content-Type: application/json' \
            -H "X-CI-INGESTION-KEY: ${SCS_KEY}" \
            -d @-

      - name: Notify Pipeline — SUCCESS
        env:
          SCS_KEY: ${{ secrets.SCS_INGESTION_KEY }}
        run: |
          curl -sf -X POST "${{ vars.SCS_URL }}/api/v1/integrations/pipeline-runs" \
            -H 'Content-Type: application/json' \
            -H "X-CI-INGESTION-KEY: ${SCS_KEY}" \
            -d "{\"applicationKey\":\"${{ vars.SCS_APP_KEY }}\",\"provider\":\"GITHUB_ACTIONS\",\"externalRunId\":\"${{ github.run_id }}\",\"workflowName\":\"CI\",\"repositoryUrl\":\"https://github.com/${{ github.repository }}\",\"branchName\":\"main\",\"commitSha\":\"${{ github.sha }}\",\"status\":\"SUCCESS\",\"startedAt\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"completedAt\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"artifactDigest\":\"${{ steps.build.outputs.digest }}\"}"

      - name: Evaluate Release Gate
        env:
          SCS_KEY: ${{ secrets.SCS_INGESTION_KEY }}
        run: |
          GATE=$(curl -sf -X POST "${{ vars.SCS_URL }}/api/v1/integrations/release-gates/evaluate" \
            -H 'Content-Type: application/json' \
            -H "X-CI-INGESTION-KEY: ${SCS_KEY}" \
            -d "{\"applicationKey\":\"${{ vars.SCS_APP_KEY }}\",\"pipelineRunId\":\"${{ github.run_id }}\"}")

          DECISION=$(echo "$GATE" | jq -r '.decision')
          echo "Release gate: $DECISION"
          echo "$GATE" | jq '.'

          if [ "$DECISION" = "BLOCK" ]; then
            echo "::error::Release gate BLOCKED — security requirements not met"
            exit 1
          fi
          echo "Release gate $DECISION — continuing"
```

## CI Status Design

The `pipeline_runs.status = SUCCESS` stage submits after build, tests, Trivy, SBOM, and Cosign verification are all complete. The release gate evaluation reads the accumulated evidence and makes an independent decision.

## Release Gate Handling

| Decision | GitHub Actions Behavior |
|----------|------------------------|
| PASS | Continue to deployment |
| WARN | Require documented manual approval (environment protection rule) |
| BLOCK | Fail the workflow step — do not deploy |
