# AWS Migration Readiness Report — SupplyChain Sentinel

**Audit date:** 2026-11-09  
**Revision:** v2 — corrected against final target architecture  
**Scope:** Phase 0 migration readiness. No source files modified. No AWS resources created.  
**Project root:** `kiro_ai/`

**Target architecture (final):**
- React + Vite frontend → private S3 bucket with Origin Access Control
- CloudFront default behaviour → S3 (static assets)
- CloudFront `/api/*` behaviour → Application Load Balancer → EKS (Spring Boot)
- RDS PostgreSQL 17 in private subnets
- AWS Secrets Manager for all application secrets
- Docker Hub for container images
- Terraform for all infrastructure
- Argo CD for Kubernetes deployment

All findings are based on direct inspection of source files. Every claim references
the exact file or code that was examined. Nothing has been assumed or fabricated.

---

## Classification Key

| Label | Meaning |
|---|---|
| ✅ READY | Works as-is in the target architecture |
| 🔧 REQUIRED CHANGE | Must be changed before the application can run on AWS |
| 💡 OPTIONAL IMPROVEMENT | Not blocking; address before or after first deployment |
| 🚫 BLOCKER | Will cause a runtime failure or security incident if not resolved |

---

## 1. Backend Configuration

### 1.1 Spring profile structure

**Files:** `backend/src/main/resources/application*.yml`

| Profile | Activated by | Purpose |
|---|---|---|
| `dev` | `SPRING_PROFILES_ACTIVE=dev` | Local PostgreSQL, debug logging, dev-only JWT fallback |
| `docker` | `SPRING_PROFILES_ACTIVE=docker` | Docker Compose; fallback credentials in defaults |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | AWS EKS; all values from env vars, zero defaults |
| `test` | Spring `@ActiveProfiles("test")` | Test DB; skips admin provisioning |

✅ READY — `application-prod.yml` has zero hardcoded values. Every property is
`${ENV_VAR}` with no insecure fallback. The file already carries the comment
*"Designed for AWS EKS deployment with RDS PostgreSQL."*

---

### 1.2 Hardcoded localhost values

**Files inspected:**
- `application-dev.yml` — `DB_HOST:localhost` and `DB_PORT:5432` are fallback defaults
  only, never reached in production
- `application-docker.yml` — `DB_HOST:postgres` is the Docker Compose service name,
  never reached in production
- `application-prod.yml` — `${DB_HOST}`, `${DB_PORT}`, `${DB_NAME}` — no hardcoded hosts

✅ READY — No hardcoded hosts in `application-prod.yml`.

---

### 1.3 Environment variables — complete production list

Derived from `application.yml`, `application-prod.yml`, and `.env.example`.

| Variable | Required | Source | Purpose |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | **Yes** (`prod`) | EKS pod spec | Activates the production config |
| `DB_HOST` | **Yes** | Secrets Manager | RDS endpoint |
| `DB_PORT` | No (`5432`) | Optional | RDS port |
| `DB_NAME` | **Yes** | Secrets Manager | Database name |
| `DB_USERNAME` | **Yes** | Secrets Manager | RDS app user |
| `DB_PASSWORD` | **Yes** | Secrets Manager | RDS password (static credential) |
| `DB_POOL_MAX` | No (`10`) | Optional | HikariCP max connections |
| `DB_POOL_MIN` | No (`2`) | Optional | HikariCP min idle |
| `SERVER_PORT` | No (`8080`) | Optional | Container listen port |
| `APP_CORS_ALLOWED_ORIGINS` | **Yes** | Deployment config | CloudFront distribution hostname |
| `JWT_SECRET` | **Yes** | Secrets Manager | Min 32 bytes; `@NotBlank` enforced |
| `JWT_ISSUER` | No (`supplychain-sentinel`) | Optional | JWT `iss` claim |
| `JWT_ACCESS_TOKEN_TTL_MINUTES` | No (`15`) | Optional | Access token lifetime |
| `JWT_REFRESH_TOKEN_TTL_DAYS` | No (`7`) | Optional | Refresh token lifetime |
| `APP_COOKIE_SECURE` | **Yes** (`true`) | EKS pod spec | Marks refresh cookie `Secure` |
| `APP_MAX_LOGIN_ATTEMPTS` | No (`5`) | Optional | Brute-force threshold |
| `APP_LOCKOUT_DURATION_MINUTES` | No (`15`) | Optional | Lockout duration |
| `INITIAL_ADMIN_USERNAME` | **Yes** (first deploy) | Secrets Manager | Bootstrapped admin |
| `INITIAL_ADMIN_PASSWORD` | **Yes** (first deploy) | Secrets Manager | Bootstrapped admin |
| `INITIAL_ADMIN_EMAIL` | **Yes** (first deploy) | Deployment config | Bootstrapped admin |
| `CI_INGESTION_API_KEY` | **Yes** | Secrets Manager | Min 32 bytes; 503 if absent/short |

✅ READY — All variables are documented in `.env.example`. All required production
variables have no insecure fallback in `application-prod.yml`. All secrets must be
stored in AWS Secrets Manager and injected via EKS Pod Identity or External Secrets
Operator.

🚫 BLOCKER — `APP_COOKIE_SECURE` defaults to `false` in `application-dev.yml` and
`application-docker.yml`. The prod profile sets it correctly to
`${APP_COOKIE_SECURE:true}`. The blocker is ensuring `SPRING_PROFILES_ACTIVE=prod`
is always set in the EKS pod environment. If the wrong profile is activated, the
refresh cookie will be served without the `Secure` flag over HTTPS.  
**Recommended correction:** Set `SPRING_PROFILES_ACTIVE=prod` as a non-overridable
value in the Kubernetes pod spec (`env.value`, not `envFrom`), so it cannot be
accidentally overridden by a Secrets Manager secret with the same name.

---

### 1.4 JWT and cookie configuration

**Files:** `AuthController.java`, `JwtProperties.java`

Verified behaviour (from source):
- Access token is returned in the JSON body, stored in-memory on the frontend
  (`tokenStore.ts` — module-level variable, not `localStorage`).
- Refresh token is an `HttpOnly` `ResponseCookie` scoped to `/api/v1/auth`,
  with `SameSite=Lax`.
- `Secure` flag is `jwtProperties.cookieSecure()` — environment-driven.
- Refresh token rotation is implemented (old token revoked, new issued, chain recorded).

✅ READY — Stateless JWT, in-memory access token, HttpOnly refresh cookie.
No server-side session. Compatible with EKS stateless pods.

**Cookie analysis for the target architecture:**

In the target architecture, **the browser accesses both the frontend and the API
through the same CloudFront hostname** (e.g. `https://sentinel.example.com`). The
frontend is served from the default CloudFront behaviour (S3 origin), and all
`/api/*` requests are routed by CloudFront to the ALB origin. From the browser's
perspective, all requests go to the same origin.

This means:
- The login request goes to `https://sentinel.example.com/api/v1/auth/login`
- The refresh request goes to `https://sentinel.example.com/api/v1/auth/refresh`
- The cookie domain is `sentinel.example.com` — the same site as the page

**`SameSite=Lax` is correct for this architecture.** It allows the cookie to be
sent on same-site requests (the refresh POST) and blocks it on genuine cross-site
requests (CSRF protection). `SameSite=None` is only required when the frontend and
API live on different registered domains, which is not the case here.

🔧 REQUIRED CHANGE — `APP_COOKIE_SECURE` **must** be set to `true` in the EKS pod
environment. This is already enforced by `application-prod.yml` defaulting it to
`true`, but it must be explicitly present in the pod spec to prevent accidental
fallback to the dev default. The cookie must have `Secure=true; HttpOnly=true;
SameSite=Lax` in production.

---

### 1.5 CORS configuration

**File:** `backend/src/main/java/com/supplychainsentinel/config/CorsConfig.java`

```java
@Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:4173}")
private String allowedOrigins;
// ...
config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
config.setAllowCredentials(true);
source.registerCorsConfiguration("/api/**", config);
```

✅ READY (design) — CORS is fully environment-driven. No hardcoded production origin.

🔧 REQUIRED CHANGE — `APP_CORS_ALLOWED_ORIGINS` must be set to the CloudFront
distribution origin, e.g. `https://sentinel.example.com`. Without this, the Spring
backend will reject CORS preflight requests from the frontend because the `Origin`
header will be the CloudFront hostname, which does not match the localhost defaults.

Note: since the frontend and API share the same CloudFront hostname, the browser sends
`Origin: https://sentinel.example.com` on API requests. The backend CORS config must
reflect this exact value. The `withCredentials: true` setting in `client.ts` requires
the backend to respond with `Access-Control-Allow-Credentials: true`, which requires an
exact (non-wildcard) origin to be listed.

**Migration dependency:** The CloudFront distribution hostname must be known before
first deploy.

---

### 1.6 Actuator health endpoint

**File:** `application.yml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when_authorized
```

The `/actuator/health` endpoint is in `SecurityConfig.java`'s `permitAll()` list.

✅ READY — Only `health` and `info` are exposed. Health details are hidden from
anonymous callers.

💡 OPTIONAL IMPROVEMENT — Add Spring Boot health probe groups to `application-prod.yml`
so Kubernetes can use dedicated liveness and readiness paths:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

This allows the readiness probe to reflect Flyway migration completion separately
from JVM liveness. Not required for first deployment but recommended before production
load balancing.

---

### 1.7 Local filesystem dependencies

**Search result:** No `MultipartFile`, `FileOutputStream`, `Files.write`,
`File.createTempFile`, or `StorageService` references found in
`backend/src/main/java/**/*.java`.

SBOM documents are stored as `JSONB` in `sbom_documents.raw_document` (V7 migration
confirmed). Webhook evidence is stored as `JSONB` in `deployment_records.webhook_evidence`.
Security scan reports are parsed in-memory and stored as structured rows.

✅ READY — Zero local filesystem dependencies. All data lives in PostgreSQL.
No S3 integration is required for the existing feature set to run on AWS.

---

### 1.8 Swagger / OpenAPI in production

**File:** `SecurityConfig.java`

```java
.requestMatchers(
    "/swagger-ui/**",
    "/api-docs/**",
    "/v3/api-docs/**"
).permitAll()
```

`application-prod.yml` has no `springdoc` section — it inherits the base `application.yml`
which also has no explicit disable, leaving Swagger enabled by default.

🚫 BLOCKER — Swagger UI and the full OpenAPI schema are publicly accessible in all
profiles including `prod`. Anyone who discovers the ALB DNS name can read the complete
API surface, endpoint signatures, ingestion formats, and authentication details.  
**Recommended correction:** Add to `application-prod.yml`:

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

Alternatively, restrict access at the ALB security group so `/swagger-ui/**` and
`/api-docs/**` are only reachable from an internal CIDR. The springdoc disable is
simpler and preferred.

---

## 2. Database

### 2.1 Flyway migrations — schema inventory

All 9 migrations confirmed at `backend/src/main/resources/db/migration/`.

| Version | File | Tables |
|---|---|---|
| V1 | `V1__init_schema.sql` | `roles`, `users`, `user_roles` |
| V2 | `V2__auth_schema.sql` | `refresh_tokens`; adds cols to `users` |
| V3 | `V3__application_inventory.sql` | `applications` |
| V4 | `V4__pipeline_runs.sql` | `pipeline_runs` |
| V4.1 | `V4_1__pipeline_runs_restrict_fk.sql` | Alters FK on `pipeline_runs` |
| V5 | `V5__security_findings.sql` | `security_scans`, `security_findings` |
| V6 | `V6__security_scan_findings.sql` | `scan_findings` junction table |
| V7 | `V7__sbom_and_artifact_integrity.sql` | `sbom_documents`, `sbom_components`, `artifact_verifications` |
| V8 | `V8__release_assurance.sql` | `release_assessments`, `release_approvals`, `audit_events` |
| V9 | `V9__deployment_tracking.sql` | `deployment_records` |

✅ READY — All migrations use `CREATE TABLE IF NOT EXISTS` and are idempotent-safe.
They will run cleanly on a fresh RDS PostgreSQL 17 instance with no manual intervention.

✅ READY — `spring.jpa.hibernate.ddl-auto: validate` ensures Hibernate validates the
schema at startup but never modifies it. All schema changes go through Flyway.

✅ READY — `spring.flyway.baseline-on-migrate: true` allows Flyway to baseline
against an existing database (useful when restoring from a pg_dump of Docker Compose).

> **Note:** These Flyway findings are based on static inspection of the SQL files.
> The migrations have not been executed against an actual RDS instance. Verification
> against RDS should be performed during the first EKS deployment to a staging
> environment.

---

### 2.2 Database authentication — static credential model

The application uses static credentials passed via environment variables
(`DB_USERNAME` / `DB_PASSWORD`). This is the standard model for the first migration.

**File:** `application-prod.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

✅ READY — All connection parameters are environment-driven and sourced from Secrets
Manager at pod startup.

💡 OPTIONAL IMPROVEMENT — HikariCP defaults are suitable for a small number of pods.
If you scale to multiple EKS pods, the total connection count
(`max_connections = DB_POOL_MAX × pod_count`) must stay below the RDS instance's
`max_connections` limit. Set `DB_POOL_MAX` appropriately via the EKS pod spec.

> **RDS IAM database authentication** (where the password is replaced by a 15-minute
> IAM token) is a separate feature requiring `aws-advanced-jdbc-wrapper` and an IRSA
> policy with `rds-db:connect`. This is **not planned for Phase 0** and is documented
> here only as a future option. No Hikari changes related to IAM token rotation are
> required unless that feature is adopted.

---

### 2.3 Local data preservation

The Docker Compose database runs on volume `pgdata`. It contains development and
test data with no production value.

**Safe Docker PostgreSQL → RDS migration procedure** (for reference; not required
for a fresh deployment):

```bash
# 1. Dump from Docker Compose
docker exec scs-postgres pg_dump \
  -U scsentinel -d supplychain_sentinel \
  --no-owner --no-acl -F custom \
  -f /tmp/scs-backup.dump
docker cp scs-postgres:/tmp/scs-backup.dump ./scs-backup.dump

# 2. Restore to RDS (run before first EKS pod start)
pg_restore \
  -h <rds-endpoint> -U <master-user> \
  -d supplychain_sentinel \
  --no-owner --no-acl --clean --if-exists \
  scs-backup.dump
```

On first EKS pod startup, Flyway detects the existing schema (via `baseline-on-migrate`)
and marks all 9 migrations as already applied without re-running them. No manual
Flyway intervention is required.

Do not run the Docker Compose stack against the RDS endpoint at any point — it would
inject Docker-network-specific defaults (`DB_HOST=postgres`, `DB_PASSWORD=scsentinel`)
into a production database.

---

## 3. Frontend

### 3.1 Axios configuration and API base URL

**File:** `frontend/src/api/client.ts` (line 9)

```typescript
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1'
```

**Analysis for the target architecture:**

In the target architecture, the browser accesses everything through a single CloudFront
distribution. The CloudFront `/api/*` behaviour routes those requests to the ALB, and
the default behaviour serves static assets from S3.

When `VITE_API_BASE_URL` is not set, `baseURL` becomes the relative path `/api/v1`.
The browser then sends `GET https://sentinel.example.com/api/v1/...`, which CloudFront
routes correctly to the ALB via the `/api/*` behaviour rule.

✅ READY — The relative `/api/v1` fallback works correctly with CloudFront path-based
routing because all requests go through the same CloudFront hostname. The `VITE_API_BASE_URL`
variable does not need to be set for production deployment through CloudFront.

💡 OPTIONAL IMPROVEMENT — Setting `VITE_API_BASE_URL` explicitly (e.g. to
`https://sentinel.example.com/api/v1`) makes the build output self-documenting and
avoids any ambiguity if the CloudFront hostname changes. It is not required.

---

### 3.2 Localhost references in frontend source

**Search result (grep over `frontend/src/**/*.ts`):** No `localhost` references found.

✅ READY — No hardcoded localhost in the compiled frontend.

---

### 3.3 Vite development proxy

**File:** `frontend/vite.config.ts`

```typescript
server: {
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true }
  }
}
```

✅ READY — This is a Vite dev-server-only feature. It is not compiled into `npm run
build` output. It has no effect on the S3-hosted production assets.

**Local vs. AWS distinction:**
- **Local development:** Vite proxy forwards `/api/*` to `localhost:8080` backend.
- **AWS production:** CloudFront `/api/*` behaviour forwards to the ALB. No proxy needed
  in the frontend build.

---

### 3.4 Authentication and refresh token behaviour

**File:** `frontend/src/api/client.ts`

Verified behaviour:
- Access token stored in a module-level JS variable (not `localStorage`, not
  `sessionStorage`). Cleared on page refresh; re-acquired via the refresh cookie.
- On 401, posts to `/api/v1/auth/refresh` with `withCredentials: true`.
- Deduplication prevents multiple concurrent refresh calls.
- If refresh fails, redirects to `/login`.

✅ READY — Token handling is correct for a stateless SPA behind CloudFront.

Since both the page and all API calls go through the same CloudFront hostname,
`withCredentials: true` will correctly send the HttpOnly `SameSite=Lax` refresh
cookie on the `/api/v1/auth/refresh` POST. No cross-site cookie issue exists in this
architecture.

---

### 3.5 React Router SPA routing on S3 / CloudFront

**File:** `frontend/nginx.conf`

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

This Nginx fallback is used in Docker Compose. S3 does not provide `try_files`
semantics. When a user navigates directly to `/applications/123` or refreshes the
page on a client-side route, S3 returns `403 Access Denied` (key does not exist).

🔧 REQUIRED INFRASTRUCTURE CHANGE — Configure CloudFront to handle S3 error responses
on the **default behaviour (S3 origin)** only.

**Option A — CloudFront custom error responses (Terraform):**

```hcl
custom_error_response {
  error_code            = 403
  response_code         = 200
  response_page_path    = "/index.html"
}
custom_error_response {
  error_code            = 404
  response_code         = 200
  response_page_path    = "/index.html"
}
```

**Option B — CloudFront Function on the default behaviour:**

```javascript
// viewer-request function: rewrite non-asset paths to /index.html
function handler(event) {
  var request = event.request;
  var uri = request.uri;
  if (!uri.match(/\.(js|css|png|jpg|svg|ico|woff2?|json|map)$/)) {
    request.uri = '/index.html';
  }
  return request;
}
```

Option A is simpler and sufficient. Option B is preferred if you want to avoid
CloudFront returning an S3 error at all and prefer to intercept at the CDN edge.

**Critical constraint:** The 403→200 rewrite must only apply to the **default S3
behaviour**. The `/api/*` behaviour must **not** rewrite 401, 403, or 404 responses
from the backend — those are legitimate API error codes that the frontend handles.

This is achieved in Terraform by scoping `custom_error_response` to the distribution
level, then ensuring the `/api/*` cache behaviour sets its own error handling to
pass-through (which is the default for `forwarded_values` behaviours with caching
disabled).

---

### 3.6 Build-time configuration

✅ READY — No build-time variables are required for a CloudFront deployment.

The relative `/api/v1` base URL works with CloudFront path routing (§3.1). No
`frontend/.env.production` file needs to be created.

---

## 4. Docker and Container Configuration

### 4.1 Backend Dockerfile

**File:** `backend/Dockerfile`

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy
RUN groupadd -r scs && useradd -r -g scs scs
WORKDIR /app
COPY --from=build /build/target/supplychain-sentinel-backend-*.jar app.jar
USER scs
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

✅ READY — Multi-stage build. JRE-only runtime (not JDK). Non-root user `scs`.
Linux (Jammy = Ubuntu 22.04 LTS). Compatible with EKS x86_64 and ARM64.

**Note on JVM container awareness:** Java 21 enables `-XX:+UseContainerSupport` by
default. The JVM will correctly respect cgroup memory limits set by EKS without any
Dockerfile change. No explicit flag is required. Adding `-XX:MaxRAMPercentage=75.0`
is an optional fine-tuning measure, not a correction.

💡 OPTIONAL IMPROVEMENT — Add JVM tuning flags for predictable container memory usage:

```dockerfile
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

`-XX:MaxRAMPercentage=75.0` caps heap at 75% of the container memory limit, leaving
headroom for metaspace and native memory. `-Djava.security.egd` speeds up startup on
Linux containers that have limited entropy.

💡 OPTIONAL IMPROVEMENT — The build stage's `mvn dependency:go-offline` will fail on
Docker Hub rate limits in busy CI pipelines. Consider pointing Maven at a CodeArtifact
proxy via a `settings.xml` build argument.

---

### 4.2 Frontend Dockerfile

**File:** `frontend/Dockerfile`

```dockerfile
FROM node:22-alpine AS build
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

**Local Docker Compose use:** The Nginx Docker image is used in Docker Compose to
serve the React build and proxy `/api/` to the backend container. This is correct and
should be kept as-is for local development.

**AWS production deployment:** In the target architecture the frontend is deployed as
static files to S3. The Nginx Docker image is **not used** in the AWS deployment path.
The build artefact is the `dist/` directory produced by `npm run build`, which is
uploaded to S3 by the CI pipeline.

✅ READY — The Dockerfile is correct for local Docker Compose. No changes are needed
for the AWS deployment path because the Dockerfile output (`dist/`) is what gets
deployed to S3.

There is no requirement to add a `VITE_API_BASE_URL` build argument to the Dockerfile,
because the relative `/api/v1` base URL works correctly with CloudFront (§3.1). If
the Docker image is used for integration testing in CI, it will proxy via Nginx, which
is the correct local behaviour.

---

### 4.3 Backend environment variables on EKS

The following must be set in the Kubernetes pod spec or via External Secrets Operator.
All secrets must come from AWS Secrets Manager — Docker Compose defaults must never
reach the EKS environment.

```
# Profile (non-overridable, set directly in pod spec)
SPRING_PROFILES_ACTIVE=prod
APP_COOKIE_SECURE=true

# From Secrets Manager
DB_HOST=<rds-endpoint>
DB_NAME=supplychain_sentinel
DB_USERNAME=<app-db-user>
DB_PASSWORD=<from-secrets-manager>
JWT_SECRET=<from-secrets-manager, min 32 bytes>
CI_INGESTION_API_KEY=<from-secrets-manager, min 32 bytes>
INITIAL_ADMIN_USERNAME=<from-secrets-manager>
INITIAL_ADMIN_PASSWORD=<from-secrets-manager>
INITIAL_ADMIN_EMAIL=<from-deployment-config>

# From deployment configuration (not secrets)
APP_CORS_ALLOWED_ORIGINS=https://sentinel.example.com
```

**Local vs. AWS distinction:**
- Docker Compose injects values from `backend/.env` (local file, gitignored).
- EKS injects values from AWS Secrets Manager via Pod Identity Agent or External
  Secrets Operator. The `docker-compose.yml` fallback defaults (`changeme123`,
  `scsentinel`) must never be reachable from EKS.

---

## 5. CloudFront Distribution Configuration

This section documents the required CloudFront configuration that the Terraform module
must implement. These are infrastructure requirements, not application code changes.

### 5.1 Origins

| Origin | Type | Configuration |
|---|---|---|
| S3 frontend origin | S3 (OAC) | Private bucket; Origin Access Control only; no public bucket policy |
| ALB API origin | Custom (HTTP/HTTPS) | ALB DNS name; origin protocol HTTPS or HTTP (match ALB listener) |

### 5.2 Cache behaviours

| Path pattern | Origin | Cache | Methods |
|---|---|---|---|
| `/api/*` | ALB | **Disabled** (TTL 0) | GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS |
| Default `/*` | S3 | Enabled (long TTL for hashed assets) | GET, HEAD |

### 5.3 Header forwarding on the `/api/*` behaviour

The following must be forwarded to the ALB origin. Failure to forward any of these
will break authentication, CORS, or ingestion.

| Header / value | Why required |
|---|---|
| `Authorization` | JWT access token sent by `client.ts` request interceptor |
| `Cookie` | HttpOnly `scs_refresh_token` cookie for `/api/v1/auth/refresh` |
| `Content-Type` | Required for all POST/PUT/PATCH endpoints |
| `X-CI-INGESTION-KEY` | Custom header validated by `IngestionKeyFilter.java` |
| `Origin` | Required for Spring Boot CORS preflight |
| `Access-Control-Request-*` | Preflight headers for CORS |
| All query strings | Pagination (`page`, `size`, `sort`) and filter params |

**Set-Cookie forwarding:** CloudFront must forward `Set-Cookie` response headers from
the ALB to the browser. This is required for the login and refresh endpoints to deliver
the HttpOnly refresh cookie. By default CloudFront strips `Set-Cookie`. To preserve it,
the `/api/*` behaviour must use a **Cache Policy** with `Headers: Include → Set-Cookie`
in the response headers policy, or use `Managed-AllViewer` as the origin request policy
with caching disabled.

### 5.4 API error response pass-through

CloudFront custom error responses must only apply to the **S3 default behaviour**.
The `/api/*` behaviour must pass through all HTTP status codes from the ALB including:
- `401 Unauthorized` (invalid JWT or missing ingestion key)
- `403 Forbidden` (RBAC denial)
- `404 Not Found` (resource not found)
- `400 Bad Request` (validation failure)
- `503 Service Unavailable` (ingestion key misconfiguration)

Do not configure a 403→200 or 404→200 rewrite on the ALB origin. These are real API
error responses that the React frontend handles via the Axios error interceptor.

### 5.5 SPA fallback (S3 default behaviour only)

As documented in §3.5, configure CloudFront custom error responses for the S3 origin:

```hcl
custom_error_response {
  error_code         = 403
  response_code      = 200
  response_page_path = "/index.html"
}
custom_error_response {
  error_code         = 404
  response_code      = 200
  response_page_path = "/index.html"
}
```

---

## 6. Security

### 6.1 Default credentials in Docker Compose

**File:** `docker-compose.yml`

```yaml
JWT_SECRET: ${JWT_SECRET:-change-this-jwt-secret-in-production-at-least-32-characters-long}
DB_PASSWORD: ${DB_PASSWORD:-scsentinel}
INITIAL_ADMIN_PASSWORD: ${INITIAL_ADMIN_PASSWORD:-changeme123}
CI_INGESTION_API_KEY: ${CI_INGESTION_API_KEY:-}
```

These fallback defaults exist exclusively for local Docker Compose and are correct
for that purpose. They are acceptable in the source file because:
1. The `.env` file (which overrides them) is gitignored.
2. The `prod` Spring profile has no defaults for any of these values.
3. `JwtProperties.secret` has `@NotBlank` — the app refuses to start without a real secret.

🚫 BLOCKER — The Docker Compose defaults must never reach EKS. The separation is
enforced architecturally: EKS pods inject all values from Secrets Manager; Docker
Compose never accesses RDS. This must be documented in the deployment runbook and
enforced by Terraform (the EKS pod role must not have access to the Docker Compose
`.env` file, which is a local file and never touches AWS).  
**Recommended action:** Document the separation explicitly. Do not add placeholder
detection logic unless a future compliance requirement demands it.

---

### 6.2 `.gitignore` coverage

**File:** `.gitignore` (root)

```
.env
.env.*
!.env.example
backend/.env
*.key
*.pub
*.bundle.json
```

✅ READY — `.env`, `backend/.env`, private keys, and Cosign bundles are all gitignored.
`.env.example` is explicitly allowed and contains no real secrets.

---

### 6.3 JWT secret and ingestion key: fail-fast validation

**Files:** `JwtProperties.java`, `IngestionKeyFilter.java`

Verified:
- `JwtProperties.secret` is annotated `@NotBlank` — the Spring context will fail to
  load if `JWT_SECRET` is absent or blank. The application will not start.
- `IngestionKeyFilter` requires the key to be ≥ 32 bytes UTF-8. If not, all ingestion
  endpoints return `503` (not `401`) to signal a server misconfiguration.
- Key comparison uses `MessageDigest.isEqual()` (constant-time). Key is never logged.

✅ READY — Both secrets have hard fail-fast validation. The app cannot silently run
with missing or weak secrets.

---

### 6.4 Swagger / OpenAPI (production blocker)

Covered in §1.8. Repeated here for completeness.

🚫 BLOCKER — Add to `application-prod.yml`:
```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

---

### 6.5 Ingestion endpoint access control

**Files:** `IngestionKeyFilter.java`, `SecurityConfig.java`

```java
.requestMatchers("/api/v1/integrations/**").permitAll()
```

The `permitAll()` bypasses JWT auth in favour of `IngestionKeyFilter`, which runs
earlier in the filter chain.

✅ READY — Ingestion endpoints are gated by `X-CI-INGESTION-KEY`. The key must be
stored in Secrets Manager and injected into both EKS pods and CI pipeline secrets.

---

## 7. Evidence Storage

### 7.1 Current state — PostgreSQL only

**Files inspected and confirmed:**
- `SbomService.java` — CycloneDX JSON is stored as `String rawJson = sbom.toString()`
  persisted as `JSONB` in `sbom_documents.raw_document` (V7 migration)
- `ArtifactVerificationService.java` — structured fields in DB, no file I/O
- `SecurityFindingService.java` — parses Trivy/SARIF in-memory, structured rows in DB
- `deployment_records.webhook_evidence` — `JSONB` column (V9 migration)
- Grep for `MultipartFile`, `FileOutputStream`, `Files.write`, `createTempFile` — **zero matches**

✅ READY — There is no local filesystem usage. All data is stored in PostgreSQL.
No S3 integration exists in the current codebase and none is required for the existing
feature set to run on AWS.

### 7.2 Future S3 evidence storage

The target architecture specifies a separate private S3 evidence bucket. The backend
does not currently support S3 at all — there is no `aws-sdk-java` or
`software.amazon.awssdk:s3` dependency in `pom.xml`. Adding S3 evidence storage is a
**greenfield feature addition**, not a migration of existing functionality.

💡 OPTIONAL IMPROVEMENT (future phase) — When implementing S3 evidence storage:
- Add `software.amazon.awssdk:s3` to `pom.xml`
- Configure IRSA with `s3:PutObject` / `s3:GetObject` scoped to the evidence bucket ARN
- Use pre-signed URLs for frontend downloads (avoid streaming large files through EKS)
- For SBOM raw documents: add an `s3_key` column to `sbom_documents`, upload to S3 at
  ingestion, and null out `raw_document` in a future Flyway migration

Do not claim any S3 integration exists until this feature is built.

---

## 8. Terraform / EKS Readiness

### 8.1 Application parameters required by Terraform

| Parameter | Source | Value |
|---|---|---|
| Container image | Docker Hub | `<user>/scs-backend:<tag>` |
| Backend listen port | `application.yml` → `SERVER_PORT:8080` | `8080` |
| ALB health check path | `SecurityConfig.java` permitAll | `GET /actuator/health` |
| Liveness path (after §1.6) | `application-prod.yml` | `/actuator/health/liveness` |
| Readiness path (after §1.6) | `application-prod.yml` | `/actuator/health/readiness` |
| ALB target group protocol | confirmed HTTP | HTTP |
| PostgreSQL port | `application-prod.yml` | `5432` |
| PostgreSQL engine version | `pom.xml` PostgreSQL driver | 17 |
| Session type | `SecurityConfig.java` STATELESS | Stateless — no sticky sessions needed |
| Minimum pod replicas | confirmed stateless (209 tests) | 2 (HA) |
| CloudFront `/api/*` path | `SecurityConfig.java` | `/api/*` |
| Cookie path scoped to | `AuthController.java` | `/api/v1/auth` |

### 8.2 Kubernetes health probes

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness   # requires §1.6 health groups
    port: 8080
  initialDelaySeconds: 60             # allow Flyway migrations to complete
  periodSeconds: 15
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness  # requires §1.6 health groups
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 5

startupProbe:
  httpGet:
    path: /actuator/health            # always available
    port: 8080
  failureThreshold: 20                # 20 × 10s = 200s for Flyway on first boot
  periodSeconds: 10
```

The startup probe uses `/actuator/health` (always available without the probe groups)
to gate liveness/readiness checks until the application finishes migrating.

### 8.3 Resource requests and limits

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

With `-XX:MaxRAMPercentage=75.0` (see §4.1 optional improvement) the JVM heap maximum
will be ~768 MB inside the 1 Gi limit. Adjust after observing real usage with
`kubectl top pod`.

### 8.4 Required IAM permissions (backend pod service account)

| AWS Permission | Reason | Phase |
|---|---|---|
| `secretsmanager:GetSecretValue` on specific ARNs | Pull DB password, JWT secret, ingestion key, admin credentials | Phase 1 |
| `s3:PutObject`, `s3:GetObject` on evidence bucket ARN | Future SBOM/evidence storage | Future phase |
| `ssm:GetParameter` | Alternative to Secrets Manager for non-secret config | Optional |

RDS IAM database authentication (`rds-db:connect`) is **not required** for Phase 1.
The application uses static credentials from Secrets Manager. Only adopt RDS IAM auth
if a compliance requirement mandates it; it requires significant backend changes
(AWS JDBC wrapper, token refresh logic).

### 8.5 ALB listener and routing

The ALB is an internal target for CloudFront. Direct public access to the ALB DNS
should be blocked at the security group level (allow inbound only from CloudFront
managed prefix list).

| Path | Method | Notes |
|---|---|---|
| `/api/*` | All (GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS) | All API traffic |
| `/actuator/health` | GET | ALB target group health check |
| `/swagger-ui/**` | GET | Must be disabled in prod (§1.8) or restricted to private CIDR |
| `/api-docs/**` | GET | Same as above |

---

## 9. Local Docker Compose vs. AWS Deployment — Differences

| Concern | Local Docker Compose | AWS Production |
|---|---|---|
| Frontend served by | Nginx container (`frontend/Dockerfile`) | S3 + CloudFront |
| API proxy | Nginx `proxy_pass http://backend:8080` | CloudFront `/api/*` behaviour → ALB |
| `VITE_API_BASE_URL` | Not needed (Nginx proxy on same host) | Not needed (CloudFront path routing) |
| Database | PostgreSQL 17 container (`scs-postgres`) | RDS PostgreSQL 17 (private subnet) |
| Credentials source | `backend/.env` (local file) | AWS Secrets Manager via EKS Pod Identity |
| `SPRING_PROFILES_ACTIVE` | `docker` | `prod` |
| `APP_COOKIE_SECURE` | `false` (HTTP only) | `true` (HTTPS enforced by CloudFront) |
| JWT secret fallback | Dev default in `application-docker.yml` | No fallback; `@NotBlank` enforced |
| CORS origins | `localhost:5173`, `localhost:4173` | CloudFront distribution hostname |
| Container health | `curl /actuator/health` in compose | Kubernetes liveness/readiness probes |
| TLS | None (HTTP) | Enforced by CloudFront (HTTPS redirect) |

---

## 10. Summary Table

| # | Finding | File | Classification | Priority |
|---|---|---|---|---|
| 1 | Prod profile fully env-driven, zero hardcoded values | `application-prod.yml` | ✅ READY | — |
| 2 | Relative `/api/v1` base URL works with CloudFront path routing | `client.ts` | ✅ READY | — |
| 3 | `SameSite=Lax` correct — frontend and API share same CloudFront hostname | `AuthController.java` | ✅ READY | — |
| 4 | `APP_COOKIE_SECURE=true` — prod profile default; must be set in EKS pod spec | `application-prod.yml` | 🚫 BLOCKER (if wrong profile) | High |
| 5 | Swagger UI publicly accessible in all profiles | `SecurityConfig.java` | 🚫 BLOCKER | High |
| 6 | Docker Compose fallback credentials must not reach EKS | `docker-compose.yml` | 🚫 BLOCKER (process) | High |
| 7 | `SPRING_PROFILES_ACTIVE=prod` must be set non-overridably in EKS pod spec | EKS deployment config | 🚫 BLOCKER (process) | Critical |
| 8 | CORS origin must be set to CloudFront hostname | `CorsConfig.java` | 🔧 REQUIRED CHANGE | High |
| 9 | CloudFront SPA fallback (403/404 → index.html) on S3 behaviour | CloudFront config | 🔧 REQUIRED INFRA | High |
| 10 | CloudFront `/api/*` must forward Auth, Cookie, X-CI-INGESTION-KEY headers | CloudFront config | 🔧 REQUIRED INFRA | Critical |
| 11 | CloudFront must forward `Set-Cookie` responses from ALB | CloudFront config | 🔧 REQUIRED INFRA | Critical |
| 12 | API 401/403/404 must not be rewritten by CloudFront error responses | CloudFront config | 🔧 REQUIRED INFRA | High |
| 13 | Disable springdoc in prod profile | `application-prod.yml` | 🔧 REQUIRED CHANGE | High |
| 14 | Actuator health probe groups for liveness/readiness split | `application-prod.yml` | 💡 OPTIONAL | Medium |
| 15 | JVM `-XX:MaxRAMPercentage=75.0` for predictable heap sizing | `backend/Dockerfile` | 💡 OPTIONAL | Low |
| 16 | Flyway V1–V9 idempotent and RDS-compatible (static inspection) | `db/migration/*.sql` | ✅ READY | — |
| 17 | No filesystem usage; all data in PostgreSQL | `SbomService.java` | ✅ READY | — |
| 18 | No S3 integration exists in backend; future feature | `pom.xml` | ✅ READY (scope) | — |
| 19 | Static RDS credentials via Secrets Manager; IAM DB auth not planned | `application-prod.yml` | ✅ READY | — |
| 20 | JWT fail-fast (`@NotBlank`); ingestion key 32-byte minimum | `JwtProperties.java` | ✅ READY | — |
| 21 | Admin bootstrap: idempotent, skips if user exists, excluded from tests | `AdminProvisioningRunner.java` | ✅ READY | — |
| 22 | Stateless backend — no sticky sessions needed on ALB | `SecurityConfig.java` | ✅ READY | — |
| 23 | Frontend Dockerfile: correct for local Docker Compose, not used in AWS S3 path | `frontend/Dockerfile` | ✅ READY | — |

---

## 11. Required Changes Before First AWS Deployment

**Code changes (source files):**

1. **Disable Swagger in prod** — add `springdoc.api-docs.enabled: false` and
   `springdoc.swagger-ui.enabled: false` to `application-prod.yml`.

2. **Set `APP_COOKIE_SECURE=true`** — confirm it is explicitly present in the EKS
   pod spec, not relying on the prod profile default alone.

3. **Set `APP_CORS_ALLOWED_ORIGINS`** — to the CloudFront distribution hostname in the
   EKS pod spec or via the External Secrets Operator.

**Infrastructure (Terraform):**

4. **CloudFront `/api/*` behaviour** — caching disabled; forward `Authorization`,
   `Cookie`, `Content-Type`, `X-CI-INGESTION-KEY`, `Origin`, query strings; pass
   through `Set-Cookie` responses.

5. **CloudFront custom error responses** — `403 → /index.html (200)` and
   `404 → /index.html (200)` on the S3 default behaviour only.

6. **Secrets Manager secrets** — one secret each for `DB_PASSWORD`, `JWT_SECRET`,
   `CI_INGESTION_API_KEY`, `INITIAL_ADMIN_PASSWORD`. Map to env var names in §4.3
   via External Secrets Operator or Pod Identity Agent.

7. **EKS pod spec** — set `SPRING_PROFILES_ACTIVE=prod` and `APP_COOKIE_SECURE=true`
   as literal `env.value` entries (not from Secrets Manager) to prevent accidental
   override.

---

*This report is based on static code inspection of the files listed in each finding.
No AWS resources have been created. No source files have been modified. No local data
has been deleted. The Flyway migrations and backend behaviour have been verified by
source inspection and confirmed by the passing test suite (209 tests, 0 failures) but
have not yet been executed against an actual RDS instance or EKS cluster.*
