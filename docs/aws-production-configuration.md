# AWS Production Configuration Guide — SupplyChain Sentinel

This document covers all configuration required to run SupplyChain Sentinel on AWS
(EKS + RDS + CloudFront + S3). It is a companion to `AWS_MIGRATION_READINESS.md`.

---

## 1. Required Environment Variables

All production secrets and configuration are injected as environment variables into
the EKS pod. No insecure defaults are accepted in the `prod` Spring profile. The
application will refuse to start if any required value is missing or is a known
placeholder.

### 1.1 Mandatory — must be set in every production pod

| Variable | Description | Source |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Must be `prod` | Pod spec literal value |
| `APP_COOKIE_SECURE` | Must be `true` | Pod spec literal value |
| `DB_HOST` | RDS cluster endpoint | Secrets Manager |
| `DB_NAME` | Database name (`supplychain_sentinel`) | Secrets Manager or config |
| `DB_USERNAME` | Application database user | Secrets Manager |
| `DB_PASSWORD` | Application database password | Secrets Manager |
| `JWT_SECRET` | JWT signing key — min 32 bytes, strong random | Secrets Manager |
| `CI_INGESTION_API_KEY` | CI/CD webhook authentication key — min 32 bytes | Secrets Manager |
| `APP_CORS_ALLOWED_ORIGINS` | CloudFront distribution hostname, e.g. `https://sentinel.example.com` | Deployment config |

### 1.2 Optional — sensible production defaults apply

| Variable | Default | Description |
|---|---|---|
| `DB_PORT` | `5432` | RDS port |
| `DB_POOL_MAX` | `10` | HikariCP maximum connections |
| `DB_POOL_MIN` | `2` | HikariCP minimum idle connections |
| `SERVER_PORT` | `8080` | Backend listen port |
| `JWT_ISSUER` | `supplychain-sentinel` | JWT `iss` claim |
| `JWT_ACCESS_TOKEN_TTL_MINUTES` | `15` | Access token lifetime |
| `JWT_REFRESH_TOKEN_TTL_DAYS` | `7` | Refresh token lifetime |
| `APP_MAX_LOGIN_ATTEMPTS` | `5` | Brute-force lockout threshold |
| `APP_LOCKOUT_DURATION_MINUTES` | `15` | Lockout window |

### 1.3 First-deploy only — initial admin bootstrap

These are only needed on the very first deployment. After the admin user has been
created, they should be removed from the pod spec or set to empty strings. The
`AdminProvisioningRunner` skips provisioning if the user already exists.

| Variable | Description | Source |
|---|---|---|
| `INITIAL_ADMIN_USERNAME` | Admin account username | Secrets Manager |
| `INITIAL_ADMIN_PASSWORD` | Admin account password — strong random | Secrets Manager |
| `INITIAL_ADMIN_EMAIL` | Admin account email | Deployment config |

> **Important:** After first login, change the admin password through the application
> UI and remove `INITIAL_ADMIN_PASSWORD` from Secrets Manager or rotate it to an empty
> value. The bootstrap runner will not modify an existing account.

---

## 2. AWS Secrets Manager Mapping

Recommended secret structure (one secret per logical credential):

| Secret name | Fields | Maps to env vars |
|---|---|---|
| `scs/prod/db` | `host`, `name`, `username`, `password` | `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` |
| `scs/prod/jwt` | `secret` | `JWT_SECRET` |
| `scs/prod/ingestion` | `api-key` | `CI_INGESTION_API_KEY` |
| `scs/prod/admin-bootstrap` | `username`, `password`, `email` | `INITIAL_ADMIN_USERNAME`, `INITIAL_ADMIN_PASSWORD`, `INITIAL_ADMIN_EMAIL` |

### 2.1 Injecting secrets into EKS pods

**Option A — External Secrets Operator (recommended):**

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: scs-backend-secrets
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: ClusterSecretStore
  target:
    name: scs-backend-env
  data:
    - secretKey: DB_PASSWORD
      remoteRef:
        key: scs/prod/db
        property: password
    - secretKey: JWT_SECRET
      remoteRef:
        key: scs/prod/jwt
        property: secret
    - secretKey: CI_INGESTION_API_KEY
      remoteRef:
        key: scs/prod/ingestion
        property: api-key
```

**Option B — EKS Pod Identity + AWS Secrets Manager Addon:**

Mount the secrets directly as environment variables using the AWS Secrets and
Configuration Provider (ASCP) with a `SecretProviderClass`.

### 2.2 Non-secret config in pod spec

Set these directly as literal values in the Kubernetes Deployment — they are not
secrets and must not be overridable by Secrets Manager entries:

```yaml
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "prod"
  - name: APP_COOKIE_SECURE
    value: "true"
  - name: APP_CORS_ALLOWED_ORIGINS
    value: "https://sentinel.example.com"
```

---

## 3. Production Configuration Validation

The `ProductionConfigValidator` component (active only in the `prod` profile) runs at
startup and throws `IllegalStateException` if any of the following conditions are
detected:

| Check | What fails |
|---|---|
| `DB_HOST` blank | Missing |
| `DB_HOST` contains `localhost` or `127.0.0.1` | Localhost address in prod |
| `DB_PASSWORD` matches known placeholders | `scsentinel`, `changeme123`, `password`, `postgres` |
| `JWT_SECRET` < 32 bytes | Too short |
| `JWT_SECRET` contains `dev-only` or `change-me` | Development placeholder |
| `CI_INGESTION_API_KEY` blank or < 32 bytes | Missing or too short |
| `CI_INGESTION_API_KEY` contains `dev-only` or `change-me` | Development placeholder |
| `APP_CORS_ALLOWED_ORIGINS` blank | Missing |
| `APP_CORS_ALLOWED_ORIGINS` contains `localhost` | Localhost in prod |
| `APP_COOKIE_SECURE=false` | Insecure cookie in HTTPS environment |

If validation fails, the pod will not start and all errors are written to the
startup log. EKS will restart the pod, which will fail again, triggering an alert
via the `startupProbe` failure.

---

## 4. Swagger / OpenAPI Access

Swagger UI and the OpenAPI JSON endpoint are **disabled in production**
(`application-prod.yml` sets `springdoc.api-docs.enabled=false` and
`springdoc.swagger-ui.enabled=false`).

The `OpenApiConfig` bean is conditional on `springdoc.api-docs.enabled=true` and
will not be registered in the prod profile. Any request to `/swagger-ui/**`,
`/api-docs/**`, or `/v3/api-docs/**` will return `404 Not Found` in production.

Swagger remains enabled in `dev` and `docker` profiles for local development.

---

## 5. Kubernetes Health Probes

Spring Boot health probe groups are enabled in the `prod` profile:
- `/actuator/health/liveness` — JVM is alive; does **not** include the database check
- `/actuator/health/readiness` — application is ready; **includes** the database check
- `/actuator/health` — combined health (for ALB target group health check)

Both endpoints are `permitAll()` in Spring Security.

### 5.1 Recommended Kubernetes probe configuration

```yaml
# In the EKS Deployment or Helm values
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60     # allow Flyway migrations to complete on first boot
  periodSeconds: 15
  failureThreshold: 3
  successThreshold: 1

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 5
  successThreshold: 1

startupProbe:
  httpGet:
    path: /actuator/health     # always available without probe group config
    port: 8080
  failureThreshold: 20         # 20 × 10s = 200s maximum startup time
  periodSeconds: 10
```

### 5.2 Why liveness excludes the database

If the database becomes temporarily unreachable (network blip, RDS failover), the
liveness probe must not kill the pod. The pod is still alive — it simply cannot serve
requests. The readiness probe will fail, removing the pod from the ALB target group,
which is the correct response. Kubernetes should not restart the pod during a DB
failover.

---

## 6. Initial Admin Bootstrap Procedure

The `AdminProvisioningRunner` creates one admin user at startup from the
`INITIAL_ADMIN_*` environment variables. It is safe to run on every startup because
it checks `userRepository.existsByUsername(adminUsername)` and skips creation if the
user already exists.

### 6.1 First deployment procedure

1. Set `INITIAL_ADMIN_USERNAME`, `INITIAL_ADMIN_PASSWORD`, and `INITIAL_ADMIN_EMAIL`
   in Secrets Manager (`scs/prod/admin-bootstrap`).
2. Deploy the EKS pod. The admin user is created at startup.
3. Log in to the application UI with the provisioned credentials.
4. Create a personal administrator account with a strong password.
5. Disable or delete the bootstrap admin account from the UI (ADMIN role required).
6. Update the Secrets Manager secret to clear `INITIAL_ADMIN_PASSWORD` (set to empty
   string). On the next pod restart, the runner will find the user already exists and
   skip creation.

### 6.2 Password requirements for the bootstrap admin

- Must not match any of: `changeme123`, `scsentinel`, `password`, `admin`, `postgres`
  (the `ProductionConfigValidator` does not check admin password, but the same
  discipline applies)
- Recommend a random 20+ character password stored only in Secrets Manager

---

## 7. Local Docker Compose vs. AWS Production

| Concern | Local Docker Compose | AWS EKS |
|---|---|---|
| Spring profile | `docker` | `prod` |
| Credentials source | `backend/.env` (local file, gitignored) | AWS Secrets Manager |
| `APP_COOKIE_SECURE` | `false` (HTTP) | `true` (HTTPS via CloudFront) |
| `APP_CORS_ALLOWED_ORIGINS` | `localhost:5173`, `localhost:4173` | CloudFront hostname |
| Database | PostgreSQL 17 container | RDS PostgreSQL 17 (private subnet) |
| Swagger UI | Enabled at `/swagger-ui.html` | Disabled (404) |
| Actuator health groups | Enabled (mirrored from prod config in dev profile) | Enabled |
| JWT secret fallback | Dev placeholder accepted | No fallback; `@NotBlank` enforced |
| Prod config validation | Not active (`@Profile("prod")` excluded) | Active; fails fast |
| Frontend served by | Nginx container (Docker Compose) | S3 + CloudFront |
| API routing | Nginx `proxy_pass http://backend:8080` | CloudFront `/api/*` → ALB → EKS |

---

## 8. Security Checklist Before First AWS Deployment

- [ ] `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false`
      present in `application-prod.yml` ✓ (implemented)
- [ ] `ProductionConfigValidator` active in `prod` profile ✓ (implemented)
- [ ] `SPRING_PROFILES_ACTIVE=prod` set as a literal pod spec value (not from Secrets Manager)
- [ ] `APP_COOKIE_SECURE=true` set as a literal pod spec value
- [ ] All Secrets Manager secrets created and ARNs noted for Terraform
- [ ] RDS master user and app user passwords generated (not Docker Compose defaults)
- [ ] `JWT_SECRET` generated: `openssl rand -hex 32`
- [ ] `CI_INGESTION_API_KEY` generated: `openssl rand -hex 32`
- [ ] CloudFront hostname confirmed and set in `APP_CORS_ALLOWED_ORIGINS`
- [ ] ALB security group restricts inbound to CloudFront managed prefix list only
- [ ] Bootstrap admin password documented in Secrets Manager, not in source code
- [ ] After first login: rotate bootstrap admin password or disable bootstrap account

---

*Last updated: 2026-11-09. This document reflects the state of the codebase after
Phase 0 production readiness hardening. No AWS resources have been provisioned.*
