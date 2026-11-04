# GitHub Actions Integration

This guide shows how to submit build metadata from GitHub Actions to SupplyChain Sentinel.

## Prerequisites

- SupplyChain Sentinel backend running and accessible from GitHub Actions runners
- An `applicationKey` matching a registered application in the inventory
- A GitHub Secret storing the ingestion API key

## API contract

**Endpoint:** `POST /api/v1/integrations/pipeline-runs`  
**Auth header:** `X-CI-INGESTION-KEY: <key>`  
**Content-Type:** `application/json`

The same endpoint is used by GitHub Actions and Jenkins — there is no separate backend per provider.

## GitHub Secret setup

1. In your repository → **Settings → Secrets and variables → Actions**
2. Click **New repository secret**
3. Set **Name** to `SCS_INGESTION_KEY`
4. Paste the value of `CI_INGESTION_API_KEY` from the backend configuration

## Reusable workflow

`.github/workflows/sentinel-notify.yml`

```yaml
name: SupplyChain Sentinel — Notify

on:
  workflow_call:
    inputs:
      scs_url:
        required: true
        type: string
      application_key:
        required: true
        type: string
      status:
        required: true
        type: string         # RUNNING | SUCCESS | FAILED | CANCELLED
      started_at:
        required: false
        type: string
      completed_at:
        required: false
        type: string
      artifact_path:
        required: false
        type: string
    secrets:
      scs_ingestion_key:
        required: true

jobs:
  notify:
    runs-on: ubuntu-latest
    steps:
      - name: Calculate artifact digest
        id: digest
        if: inputs.artifact_path != ''
        run: |
          if [ -f "${{ inputs.artifact_path }}" ]; then
            SHA=$(sha256sum "${{ inputs.artifact_path }}" | awk '{print $1}')
            echo "value=$SHA" >> "$GITHUB_OUTPUT"
          fi

      - name: Submit pipeline run to SupplyChain Sentinel
        env:
          SCS_KEY: ${{ secrets.scs_ingestion_key }}
        run: |
          ARTIFACT_DIGEST="${{ steps.digest.outputs.value }}"
          ARTIFACT_NAME=$(basename "${{ inputs.artifact_path }}" 2>/dev/null || echo "")

          COMPLETED="${{ inputs.completed_at }}"
          [ -z "$COMPLETED" ] && COMPLETED=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

          STARTED="${{ inputs.started_at }}"
          [ -z "$STARTED" ] && STARTED=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

          jq -n \
            --arg appKey     "${{ inputs.application_key }}" \
            --arg repo       "https://github.com/${{ github.repository }}" \
            --arg branch     "${{ github.ref_name }}" \
            --arg sha        "${{ github.sha }}" \
            --arg runId      "${{ github.run_id }}" \
            --arg runNum     "${{ github.run_number }}" \
            --arg workflow   "${{ github.workflow }}" \
            --arg trigger    "${{ github.event_name }}" \
            --arg status     "${{ inputs.status }}" \
            --arg startedAt  "$STARTED" \
            --arg completedAt "$COMPLETED" \
            --arg artName    "$ARTIFACT_NAME" \
            --arg artDigest  "$ARTIFACT_DIGEST" \
            '{
              applicationKey: $appKey,
              provider:        "GITHUB_ACTIONS",
              externalRunId:   $runId,
              workflowName:    $workflow,
              repositoryUrl:   $repo,
              branchName:      $branch,
              commitSha:       $sha,
              runNumber:       $runNum,
              triggerEvent:    $trigger,
              status:          $status,
              startedAt:       $startedAt,
              completedAt:     ($status == "RUNNING" | not | if . then $completedAt else null end),
              artifactName:    (if $artName != "" then $artName else null end),
              artifactDigest:  (if $artDigest != "" then $artDigest else null end)
            }' > payload.json

          # Never print the key — use --silent and check exit code separately
          HTTP_CODE=$(curl -s -o response.json -w "%{http_code}" \
            -X POST "${{ inputs.scs_url }}/api/v1/integrations/pipeline-runs" \
            -H 'Content-Type: application/json' \
            -H "X-CI-INGESTION-KEY: ${SCS_KEY}" \
            -d @payload.json)

          echo "SupplyChain Sentinel response: HTTP $HTTP_CODE"
          if [ "$HTTP_CODE" -ge 400 ]; then
            cat response.json
          fi
```

## Main workflow using the reusable workflow

`.github/workflows/ci.yml`

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    outputs:
      started_at: ${{ steps.time.outputs.started_at }}

    steps:
      - name: Record start time
        id: time
        run: echo "started_at=$(date -u +"%Y-%m-%dT%H:%M:%SZ")" >> "$GITHUB_OUTPUT"

      - uses: actions/checkout@v4

      - name: Notify SCS — RUNNING
        uses: ./.github/workflows/sentinel-notify.yml
        with:
          scs_url:         ${{ vars.SCS_URL }}
          application_key: my-application
          status:          RUNNING
          started_at:      ${{ steps.time.outputs.started_at }}
        secrets:
          scs_ingestion_key: ${{ secrets.SCS_INGESTION_KEY }}

      - name: Build
        run: mvn clean package -DskipTests -B

      - name: Test
        run: mvn test -B

  notify-result:
    needs: build
    runs-on: ubuntu-latest
    if: always()

    steps:
      - name: Determine status
        id: status
        run: |
          if [ "${{ needs.build.result }}" = "success" ]; then
            echo "value=SUCCESS" >> "$GITHUB_OUTPUT"
          elif [ "${{ needs.build.result }}" = "cancelled" ]; then
            echo "value=CANCELLED" >> "$GITHUB_OUTPUT"
          else
            echo "value=FAILED" >> "$GITHUB_OUTPUT"
          fi

      - name: Notify SCS — final result
        uses: ./.github/workflows/sentinel-notify.yml
        with:
          scs_url:         ${{ vars.SCS_URL }}
          application_key: my-application
          status:          ${{ steps.status.outputs.value }}
          started_at:      ${{ needs.build.outputs.started_at }}
          completed_at:    $(date -u +"%Y-%m-%dT%H:%M:%SZ")
          artifact_path:   target/app.jar
        secrets:
          scs_ingestion_key: ${{ secrets.SCS_INGESTION_KEY }}
```

## Notes

- `externalRunId` uses `github.run_id`, which is globally unique per repository. This enables idempotent ingestion — the RUNNING and final-result calls update the same record.
- The secret is never interpolated into a shell string that gets echoed or printed. `curl` receives it via the `-H` flag from the environment variable.
- `jq` is pre-installed on GitHub-hosted runners. For self-hosted runners, install it or replace with a Python/node script.
- Set `SCS_URL` as a repository **variable** (not a secret) since it contains no sensitive data.
