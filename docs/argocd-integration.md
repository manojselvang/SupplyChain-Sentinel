# Argo CD Integration

This guide shows how to configure Argo CD notifications to send deployment events to
SupplyChain Sentinel. Every section is derived from the actual backend implementation
(V9 migration, `DeploymentIngestionRequest`, `DeploymentRecordService`,
`IngestionController`).

---

## Architecture

```
Argo CD Notifications controller
    │
    ├── operationState.phase == Succeeded  → trigger.on-sync-succeeded
    ├── operationState.phase == Failed     → trigger.on-sync-failed
    ├── operationState.phase == Error      → trigger.on-sync-error
    └── app.status.health.status == Degraded → trigger.on-health-degraded
                                │
                          template.sentinel-deployment
                                │
                         HTTP POST webhook
                                │
                SupplyChain Sentinel
                POST /api/v1/integrations/deployments
                Header: X-CI-INGESTION-KEY
```

---

## Network Reachability

> **Important — Docker Compose deployment:**  
> SupplyChain Sentinel currently runs under Docker Compose. The Argo CD Notifications
> controller runs inside Kubernetes and **cannot** resolve `supplychain-sentinel` as a
> DNS name. You must use the host's externally reachable IP or hostname.
>
> Examples:
> - Local cluster with `NodePort`: `http://192.168.1.10:8080`
> - Port-forwarded via `kubectl port-forward`: `http://host.docker.internal:8080`  
>   (only works if Argo CD pods run in Docker Desktop Kubernetes)
> - Exposed via Ingress or `LoadBalancer`: use that external URL
>
> Replace `http://<SENTINEL_HOST>:8080` throughout this guide with the actual address.

---

## API Contract

| Property | Value |
|---|---|
| Endpoint | `POST /api/v1/integrations/deployments` |
| Auth | `X-CI-INGESTION-KEY: <key>` header |
| Content-Type | `application/json` |
| 201 Created | New deployment record ingested |
| 200 OK | Duplicate `externalEventId` — existing record updated |
| 401 Unauthorized | Missing or invalid ingestion key |
| 503 Service Unavailable | Ingestion key shorter than 32 bytes (server misconfiguration) |
| 400 Bad Request | Validation failure or unknown `applicationKey` / `pipelineRunId` |
| 404 Not Found | `applicationKey` not found in the application inventory |

---

## Required Application Labels and Annotations

Before Argo CD can populate the webhook body, each managed Application must carry
four annotations. These are the **only** way to supply values that Argo CD's
notification templates cannot derive from the Application manifest itself.

```yaml
# On every Argo CD Application object that should be tracked by Sentinel
metadata:
  labels:
    # Must match the applicationKey stored in Sentinel's application inventory
    sentinel.io/application-key: "payment-service"

  annotations:
    # Human-readable environment name sent to Sentinel (no enum restriction)
    sentinel.io/environment: "production"

    # Full image reference including registry and tag or digest
    # Argo CD does not expose container images from Helm/Kustomize at the
    # app-notification level; you must set this explicitly.
    sentinel.io/image-reference: "registry.example.com/payment-service:v1.4.2"

    # SHA-256 digest of the deployed image (hex, exactly 64 characters)
    # Used for digest-correlation with release assessments.
    # Set this in your GitOps pipeline after the image is pushed.
    sentinel.io/image-digest: "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
```

Apply to an existing Application:

```bash
kubectl annotate application payment-service-prod \
  sentinel.io/environment=production \
  sentinel.io/image-reference="registry.example.com/payment-service:v1.4.2" \
  sentinel.io/image-digest="a1b2c3d4e5f6..." \
  -n argocd

kubectl label application payment-service-prod \
  sentinel.io/application-key=payment-service \
  -n argocd
```

---

## Request Body Reference

All fields are derived directly from `DeploymentIngestionRequest.java`.

### Required fields (`@NotBlank`)

| Field | Type | Description |
|---|---|---|
| `applicationKey` | string | Must match an existing application in Sentinel's inventory |
| `argocdApplication` | string | Argo CD Application name (used as part of the idempotency key) |
| `environment` | string | Deployment environment (`production`, `staging`, etc.) |
| `imageReference` | string | Full image reference (tag or digest form) |
| `syncStatus` | string | See [Sync status values](#sync-status-values) |
| `healthStatus` | string | See [Health status values](#health-status-values) |
| `externalEventId` | string | Stable, unique event identifier — see [Idempotency](#idempotency) |

### Optional fields

| Field | Type | Description |
|---|---|---|
| `pipelineRunId` | UUID string | Links this deployment to an existing pipeline run |
| `releaseAssessmentId` | UUID string | Links to a release assessment; enables digest-correlation |
| `argocdProject` | string | Argo CD project name |
| `cluster` | string | Destination cluster URL |
| `namespace` | string | Destination namespace |
| `gitRepoUrl` | string | GitOps source repository URL |
| `gitRevision` | string | Git commit SHA or branch tip |
| `imageDigest` | string | SHA-256 hex digest, max 64 characters (see migration `VARCHAR(64)`) |
| `operationPhase` | string | Argo CD operation phase string (`Succeeded`, `Failed`, `Error`, `Running`) |
| `deployedAt` | ISO-8601 instant | Defaults to `now()` on the server if omitted |
| `completedAt` | ISO-8601 instant | When the sync operation finished |
| `webhookEvidence` | JSON object | Raw webhook context; capped at 10 000 bytes; stored as JSONB |

### Sync status values

Sentinel stores these exactly as listed. Argo CD uses PascalCase (`Synced`,
`OutOfSync`) — the template must translate them (see [Status translation](#status-translation)).

| Sentinel value | Meaning |
|---|---|
| `SYNCED` | Argo CD reports `Synced` and operation `Succeeded` |
| `OUT_OF_SYNC` | Argo CD reports `OutOfSync` |
| `PENDING` | Sync operation is `Running` |
| `ERROR` | Argo CD operation phase `Error` |
| `UNKNOWN` | Default when no value is available |

### Health status values

| Sentinel value | Argo CD native value |
|---|---|
| `HEALTHY` | `Healthy` |
| `DEGRADED` | `Degraded` |
| `PROGRESSING` | `Progressing` |
| `SUSPENDED` | `Suspended` |
| `MISSING` | `Missing` |
| `UNKNOWN` | `Unknown` / default |

---

## Idempotency and the one-operation-one-record rule

The backend enforces a `UNIQUE(argocd_application, external_event_id)` constraint.

- First delivery with a given `(argocdApplication, externalEventId)` pair → **201 Created**
- Subsequent deliveries with the same pair → **200 OK** (mutable status fields updated)

On a **200 OK** update, only `syncStatus`, `healthStatus`, `operationPhase`, and
`completedAt` (only if previously null) are changed. Immutable deployment facts
(`deployedAt`, `imageReference`, `imageDigest`, `gitRevision`, `cluster`, `namespace`)
are **never overwritten**.

### One sync operation = one DeploymentRecord

All four Argo CD triggers for the **same sync operation** must carry the **same**
`externalEventId`. The recommended construction is:

```
argocd-<app.metadata.uid>-<operationState.startedAt>
```

`app.metadata.uid` is the stable Kubernetes UUID of the Application object.
`operationState.startedAt` is the RFC3339 timestamp when the current sync operation
began; it changes on every new operation but stays constant across all notifications
for that operation.

This means:
- `on-sync-succeeded` fires first → **201 Created** (new record, `healthStatus: HEALTHY`)
- `on-health-degraded` fires minutes later for the same operation → **200 OK**
  (updates `healthStatus` to `DEGRADED` on the existing record)

A second sync operation at the same Git revision will have a different `startedAt`
timestamp, therefore a different `externalEventId`, and will produce a **new** record.

### `externalEventId` must never be blank

If `operationState` or `startedAt` is absent in the Argo CD notification context (e.g.
a pure health notification with no associated operation), the template expression will
render an empty string. The backend rejects a blank `externalEventId` with
`400 Bad Request`. Use the fallback pattern described in the template notes below to
ensure a stable, non-empty ID is always produced.

---

## Sample Payload

This payload matches the exact field names and types accepted by the backend.
It mirrors the shape used in `DeploymentIntegrationTest`.

```json
{
  "applicationKey": "payment-service",
  "argocdApplication": "payment-service-prod",
  "argocdProject": "default",
  "environment": "production",
  "cluster": "https://kubernetes.default.svc",
  "namespace": "payments",
  "gitRepoUrl": "https://github.com/org/gitops-repo",
  "gitRevision": "abc123def456abc123def456abc123def456abc123",
  "imageReference": "registry.example.com/payment-service@sha256:a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
  "imageDigest": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
  "syncStatus": "SYNCED",
  "healthStatus": "HEALTHY",
  "operationPhase": "Succeeded",
  "externalEventId": "argocd-3f8a1c2d-e456-7890-abcd-ef1234567890-2026-11-08T12:00:00Z",
  "deployedAt": "2026-11-08T12:00:00Z",
  "completedAt": "2026-11-08T12:01:30Z",
  "webhookEvidence": {
    "source": "argocd-notifications",
    "application": "payment-service-prod",
    "project": "default"
  }
}
```

Notes:
- `imageDigest` is `VARCHAR(64)` in the database — use the 64-character hex portion
  of the SHA-256 digest (the part after `sha256:`), not the full `sha256:...` string.
- `pipelineRunId` and `releaseAssessmentId` are omitted here (optional).
  If provided, they must be valid UUIDs of existing records that belong to the same
  application.
- `deployedAt` defaults to `Instant.now()` server-side if omitted.

---

## Argo CD Notification Configuration

### Status translation

Argo CD notification templates use Sprig functions. The following converts native Argo CD
status strings to the uppercase values Sentinel requires:

| Argo CD value | Sprig expression | Sentinel value |
|---|---|---|
| `Synced` | `upper .app.status.sync.status` | `SYNCED` |
| `OutOfSync` | needs manual map (see template below) | `OUT_OF_SYNC` |
| `Healthy` | `upper .app.status.health.status` | `HEALTHY` |
| `Degraded` | `upper .app.status.health.status` | `DEGRADED` |

For `OUT_OF_SYNC`, `upper` produces `OUTOFSYNC` which is invalid. Use a conditional:

```
{{- if eq .app.status.sync.status "OutOfSync" -}}OUT_OF_SYNC{{- else -}}{{upper .app.status.sync.status}}{{- end -}}
```

### Step 1 — Store the ingestion key in `argocd-notifications-secret`

Use the standard Argo CD notifications secret. Sentinel reads the key from the
`$SCS_KEY` variable referenced in the webhook service definition.

```bash
# Create or patch the secret
kubectl create secret generic argocd-notifications-secret \
  --from-literal=SCS_KEY='your-32-byte-or-longer-ingestion-api-key-here' \
  -n argocd \
  --dry-run=client -o yaml | kubectl apply -f -
```

> Do **not** create a separate `sentinel-webhook-secret`. The Argo CD Notifications
> controller only mounts `argocd-notifications-secret` automatically. Any other
> secret requires extra volume/env configuration that is not set up here.

Verify the key is present:

```bash
kubectl get secret argocd-notifications-secret -n argocd \
  -o jsonpath='{.data.SCS_KEY}' | base64 -d | wc -c
# Must print 32 or more
```

### Step 2 — Add the webhook service to `argocd-notifications-cm`

The `service.webhook.sentinel` key configures the HTTP transport only (URL + headers).
The request body is **not** defined here — it belongs in the template (Step 3).

```bash
kubectl patch configmap argocd-notifications-cm -n argocd --type merge -p '
{
  "data": {
    "service.webhook.sentinel": "url: http://<SENTINEL_HOST>:8080\nheaders:\n  - name: Content-Type\n    value: application/json\n  - name: X-CI-INGESTION-KEY\n    value: $SCS_KEY\n"
  }
}'
```

Or apply as a full ConfigMap patch file:

```yaml
# sentinel-service-patch.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: argocd-notifications-cm
  namespace: argocd
data:
  service.webhook.sentinel: |
    url: http://<SENTINEL_HOST>:8080
    headers:
      - name: Content-Type
        value: application/json
      - name: X-CI-INGESTION-KEY
        value: $SCS_KEY
```

```bash
kubectl apply -f sentinel-service-patch.yaml
```

### Step 3 — Define `template.sentinel-deployment`

The template constructs the JSON body. It is a **separate key** from
`service.webhook.sentinel`. Note the status translation and the annotation-based
image fields.

```yaml
# sentinel-template-patch.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: argocd-notifications-cm
  namespace: argocd
data:
  template.sentinel-deployment: |
    webhook:
      sentinel:
        method: POST
        body: |
          {
            "applicationKey": "{{index .app.metadata.labels "sentinel.io/application-key"}}",
            "argocdApplication": "{{.app.metadata.name}}",
            "argocdProject": "{{.app.spec.project}}",
            "environment": "{{index .app.metadata.annotations "sentinel.io/environment"}}",
            "cluster": "{{.app.spec.destination.server}}",
            "namespace": "{{.app.spec.destination.namespace}}",
            "gitRepoUrl": "{{.app.spec.source.repoURL}}",
            "gitRevision": "{{if .app.status.operationState.syncResult}}{{.app.status.operationState.syncResult.revision}}{{else}}{{.app.status.sync.revision}}{{end}}",
            "imageReference": "{{index .app.metadata.annotations "sentinel.io/image-reference"}}",
            "imageDigest": "{{index .app.metadata.annotations "sentinel.io/image-digest"}}",
            "syncStatus": "{{- if eq .app.status.sync.status "OutOfSync" -}}OUT_OF_SYNC{{- else -}}{{upper .app.status.sync.status}}{{- end -}}",
            "healthStatus": "{{upper .app.status.health.status}}",
            "operationPhase": "{{.app.status.operationState.phase}}",
            "externalEventId": "argocd-{{.app.metadata.uid}}-{{.app.status.operationState.startedAt}}",
            "deployedAt": "{{.app.status.operationState.startedAt}}",
            "completedAt": "{{.app.status.operationState.finishedAt}}",
            "webhookEvidence": {
              "source": "argocd-notifications",
              "application": "{{.app.metadata.name}}",
              "project": "{{.app.spec.project}}",
              "syncRevision": "{{.app.status.sync.revision}}",
              "operationPhase": "{{.app.status.operationState.phase}}"
            }
          }
```

```bash
kubectl apply -f sentinel-template-patch.yaml
```

#### Template field notes

- **Git revision — `operationState.syncResult.revision` vs `sync.revision`:**  
  `app.status.sync.revision` reflects the last *applied* revision and may lag during
  a running operation. `app.status.operationState.syncResult.revision` is the revision
  the current operation actually synced to and is the preferred source. However,
  `syncResult` may be absent on health-only notifications. The template uses a Sprig
  `default` to fall back gracefully:
  ```
  "gitRevision": "{{if .app.status.operationState.syncResult}}{{.app.status.operationState.syncResult.revision}}{{else}}{{.app.status.sync.revision}}{{end}}"
  ```

- **`sentinel.io/image-reference` and `sentinel.io/image-digest`**: Argo CD has no
  native way to read deployed container images at the Application level. These
  annotations are the required workaround — populate them in your GitOps pipeline
  alongside image updates.  
  **Important:** these are *declared* values from the GitOps repository. They are not
  independently observed from the running Kubernetes workload. Do not treat
  `sentinel.io/image-digest` as evidence that the running container image matches; it
  only reflects what the GitOps pipeline declared at the time of the annotation update.

- **`operationPhase`**: passes the raw Argo CD string (`Succeeded`, `Failed`, `Error`,
  `Running`). The field is stored as-is in `operation_phase VARCHAR(50)` — no enum
  check applies to it.

- **`externalEventId`**: constructed from `app.metadata.uid` (stable Kubernetes UUID)
  and `operationState.startedAt` (RFC3339 timestamp of when the sync operation began).
  This value is **identical** for all notifications fired by the same sync operation —
  sync-succeeded, sync-failed, sync-error, and health-degraded all share the same ID.
  As a result, a health-degraded notification updates the existing record rather than
  creating a new one. A forced re-sync will have a different `startedAt` and therefore
  produces a new record.  
  **If `operationState.startedAt` is absent** (e.g. a pure ambient health notification
  with no sync operation), the expression renders an empty string and the backend
  returns `400 Bad Request`. This prevents a blank ID from matching an unrelated record.

- **`imageDigest`**: the annotation should contain only the 64-character hex string
  (no `sha256:` prefix). Values that are not exactly 64 hexadecimal characters are
  rejected by the backend with `400 Bad Request`.

### Step 4 — Define triggers

Triggers use `operationState.phase` for sync outcomes (not `sync.status`, which
reflects the current diff state, not the outcome of the last operation).

```yaml
# sentinel-triggers-patch.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: argocd-notifications-cm
  namespace: argocd
data:
  trigger.on-sync-succeeded: |
    - when: app.status.operationState.phase in ['Succeeded']
      send: [sentinel-deployment]

  trigger.on-sync-failed: |
    - when: app.status.operationState.phase in ['Failed']
      send: [sentinel-deployment]

  trigger.on-sync-error: |
    - when: app.status.operationState.phase in ['Error']
      send: [sentinel-deployment]

  trigger.on-health-degraded: |
    - when: app.status.health.status == 'Degraded'
      oncePer: app.status.health.status
      send: [sentinel-deployment]
```

```bash
kubectl apply -f sentinel-triggers-patch.yaml
```

> **Why `operationState.phase` instead of `sync.status`?**  
> `app.status.sync.status` is `OutOfSync` when the live state differs from git — it
> changes continuously and does not indicate that a sync attempt failed. 
> `operationState.phase` is `Failed` or `Error` only after a sync operation actually
> ran and failed, which is the event worth recording.

### Step 5 — Subscribe Applications to the triggers

Add notification subscription annotations to each Argo CD Application you want tracked.
The annotation format is:
`notifications.argoproj.io/subscribe.<trigger-name>.<service-type>.<service-name>: ""`

```bash
kubectl annotate application payment-service-prod \
  "notifications.argoproj.io/subscribe.on-sync-succeeded.webhook.sentinel=" \
  "notifications.argoproj.io/subscribe.on-sync-failed.webhook.sentinel=" \
  "notifications.argoproj.io/subscribe.on-sync-error.webhook.sentinel=" \
  "notifications.argoproj.io/subscribe.on-health-degraded.webhook.sentinel=" \
  -n argocd
```

Or in the Application manifest:

```yaml
metadata:
  annotations:
    notifications.argoproj.io/subscribe.on-sync-succeeded.webhook.sentinel: ""
    notifications.argoproj.io/subscribe.on-sync-failed.webhook.sentinel: ""
    notifications.argoproj.io/subscribe.on-sync-error.webhook.sentinel: ""
    notifications.argoproj.io/subscribe.on-health-degraded.webhook.sentinel: ""
```

---

## Complete `argocd-notifications-cm` Example

This shows all required keys in one ConfigMap. Apply after substituting
`<SENTINEL_HOST>`.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: argocd-notifications-cm
  namespace: argocd
data:
  # ── Webhook transport ──────────────────────────────────────────────────────
  service.webhook.sentinel: |
    url: http://<SENTINEL_HOST>:8080
    headers:
      - name: Content-Type
        value: application/json
      - name: X-CI-INGESTION-KEY
        value: $SCS_KEY

  # ── Notification template ──────────────────────────────────────────────────
  template.sentinel-deployment: |
    webhook:
      sentinel:
        method: POST
        body: |
          {
            "applicationKey": "{{index .app.metadata.labels "sentinel.io/application-key"}}",
            "argocdApplication": "{{.app.metadata.name}}",
            "argocdProject": "{{.app.spec.project}}",
            "environment": "{{index .app.metadata.annotations "sentinel.io/environment"}}",
            "cluster": "{{.app.spec.destination.server}}",
            "namespace": "{{.app.spec.destination.namespace}}",
            "gitRepoUrl": "{{.app.spec.source.repoURL}}",
            "gitRevision": "{{if .app.status.operationState.syncResult}}{{.app.status.operationState.syncResult.revision}}{{else}}{{.app.status.sync.revision}}{{end}}",
            "imageReference": "{{index .app.metadata.annotations "sentinel.io/image-reference"}}",
            "imageDigest": "{{index .app.metadata.annotations "sentinel.io/image-digest"}}",
            "syncStatus": "{{- if eq .app.status.sync.status "OutOfSync" -}}OUT_OF_SYNC{{- else -}}{{upper .app.status.sync.status}}{{- end -}}",
            "healthStatus": "{{upper .app.status.health.status}}",
            "operationPhase": "{{.app.status.operationState.phase}}",
            "externalEventId": "argocd-{{.app.metadata.uid}}-{{.app.status.operationState.startedAt}}",
            "deployedAt": "{{.app.status.operationState.startedAt}}",
            "completedAt": "{{.app.status.operationState.finishedAt}}",
            "webhookEvidence": {
              "source": "argocd-notifications",
              "application": "{{.app.metadata.name}}",
              "project": "{{.app.spec.project}}",
              "syncRevision": "{{.app.status.sync.revision}}",
              "operationPhase": "{{.app.status.operationState.phase}}"
            }
          }

  # ── Triggers ───────────────────────────────────────────────────────────────
  trigger.on-sync-succeeded: |
    - when: app.status.operationState.phase in ['Succeeded']
      send: [sentinel-deployment]

  trigger.on-sync-failed: |
    - when: app.status.operationState.phase in ['Failed']
      send: [sentinel-deployment]

  trigger.on-sync-error: |
    - when: app.status.operationState.phase in ['Error']
      send: [sentinel-deployment]

  trigger.on-health-degraded: |
    - when: app.status.health.status == 'Degraded'
      oncePer: app.status.health.status
      send: [sentinel-deployment]
```

---

## Security Notes

- The ingestion key must be **at least 32 bytes**. A shorter key causes `503` on every
  request until corrected server-side.
- Store the key only in `argocd-notifications-secret`. Never place it in a ConfigMap
  or in source code.
- The `X-CI-INGESTION-KEY` mechanism is shared with the CI pipeline, SBOM, and
  Cosign ingestion endpoints — the same key works for all of them.
- Webhook evidence (`webhookEvidence`) is capped at 10 000 bytes and stored as JSONB.
  Do not embed secrets in that field.
- The read endpoints (`GET /api/v1/deployments`, `GET /api/v1/applications/{id}/deployments`)
  require a valid JWT (`Authorization: Bearer <token>`), not the ingestion key.

---

## Digest Correlation

Sentinel correlates a deployment's `imageDigest` with a release assessment's
`artifactDigest` to answer: *was the deployed image the one that was assessed?*

### Explicit correlation

Supply `releaseAssessmentId` in the request body. The server validates that the
assessment belongs to the same application, then compares digests at read time.

### Auto-correlation by digest

When `releaseAssessmentId` is absent but `imageDigest` is present, the service
automatically searches for the most recently assessed `ReleaseAssessment` for the same
application whose `artifactDigest` matches the deployment's `imageDigest`
(case-insensitive). If found, it is linked at ingestion time. If no match exists,
`releaseAssessmentId` remains null.

This means Argo CD notification templates **do not need to supply `releaseAssessmentId`**
as long as they supply `imageDigest` and a matching assessment already exists in Sentinel.

### `digestMatchesAssessment` response field

Both explicit and auto-correlated assessments are reflected in the response:

- `true` — `imageDigest` matches the linked assessment's `artifactDigest`
- `false` — they differ, or no assessment is linked

Rules:
- The comparison is case-insensitive.
- A mismatch means the deployed image was not the one evaluated for release.
- The `imageDigest` field must be exactly 64 hexadecimal characters (the SHA-256 hex
  portion only, no `sha256:` prefix). Values that fail this format check are rejected
  with `400 Bad Request` before any database write.

> **Image integrity disclaimer**: `imageDigest` is sourced from the
> `sentinel.io/image-digest` Application annotation, which is set by the GitOps
> pipeline at deploy time. It represents the *declared* digest, not an independently
> observed runtime image identity. Sentinel cannot verify that the container actually
> running in Kubernetes matches this digest — that requires a separate runtime
> admission or image scanning mechanism.

---

## Read API

Both endpoints require `Authorization: Bearer <jwt>`.

```
GET /api/v1/deployments
  ?applicationId=<uuid>    (optional filter)
  ?syncStatus=SYNCED       (optional filter — use Sentinel enum values)
  ?healthStatus=HEALTHY    (optional filter)
  ?environment=production  (optional filter)
  &page=0&size=20&sort=deployedAt,desc

GET /api/v1/applications/{applicationId}/deployments
  &page=0&size=20&sort=deployedAt,desc
```

Response is a Spring `Page<DeploymentRecordResponse>` with fields:
`id`, `applicationId`, `applicationName`, `pipelineRunId`, `releaseAssessmentId`,
`argocdApplication`, `argocdProject`, `environment`, `cluster`, `namespace`,
`gitRepoUrl`, `gitRevision`, `imageReference`, `imageDigest`, `syncStatus`,
`healthStatus`, `operationPhase`, `deployedAt`, `completedAt`, `externalEventId`,
`createdAt`, `updatedAt`, `digestMatchesAssessment`.

---

## Dashboard KPIs

`GET /api/v1/dashboard/summary` includes four deployment counters and a recent
deployments list:

| Field | Meaning |
|---|---|
| `totalDeployments` | Total `deployment_records` rows |
| `successfulDeployments` | Rows where `sync_status = 'SYNCED'` |
| `failedDeployments` | Rows where `sync_status = 'ERROR'` |
| `degradedDeployments` | Rows where `health_status = 'DEGRADED'` |
| `recentDeployments` | Last 5 records ordered by `deployed_at DESC` |

---

## Known Limitations

1. **Image reference requires manual annotation.** Argo CD's Application object does
   not expose the deployed container images. The `sentinel.io/image-reference` and
   `sentinel.io/image-digest` annotations must be set by the GitOps pipeline that
   updates the image tag/digest in the repository.

2. **Declared digest, not observed runtime identity.** The `imageDigest` annotation
   reflects what the GitOps pipeline declared. It is not independently verified against
   the running container in Kubernetes. A runtime admission controller (e.g. Kyverno,
   OPA/Gatekeeper) or image scanning integration is required for true runtime verification.

3. **No Kubernetes operator.** The integration is webhook-based. There is no Kubernetes
   operator or CRD that automatically discovers Sentinel application keys from Argo CD
   applications.

4. **Single source only.** The template uses `.app.spec.source.repoURL`. For
   Applications that use multiple sources (`spec.sources`), you must adjust the
   template to use `.app.spec.sources` and pick the correct index.

5. **`imageDigest` column limit.** `VARCHAR(64)` accepts exactly 64-character SHA-256
   hex digests. The backend validates the format and rejects non-hex or wrong-length
   values with `400 Bad Request` before any database write.

6. **Absent `operationState.startedAt` rejects the notification.** If Argo CD fires a
   health notification that has no associated sync operation (no `operationState`),
   the template renders an empty `externalEventId` and the backend returns `400 Bad
   Request`. This is intentional — it prevents a blank ID from matching an unrelated
   record. Ambient health monitoring without a triggering sync operation is not
   supported by this integration.

7. **No real Argo CD end-to-end test.** All tests in `DeploymentIntegrationTest` use
   direct HTTP calls with `MockMvc`. No actual Argo CD notification has been sent to the
   running Sentinel backend. The integration configuration in this doc has been derived
   from the argocd-notifications specification but has not been exercised against a live
   Argo CD cluster.

8. **Docker Compose networking.** Until Sentinel is deployed with an externally
   reachable address (Ingress, `LoadBalancer`, or `NodePort`), the Argo CD
   Notifications controller inside Kubernetes cannot reach it by the Docker Compose
   service name.
