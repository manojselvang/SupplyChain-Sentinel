# SupplyChain Sentinel — Local Setup Guide

This guide provides step-by-step instructions for running SupplyChain Sentinel on your development machine without Docker.

For Docker Compose setup, see the [Quick Start in README.md](../README.md#quick-start).

---

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Java JDK | 21 | [Temurin 21](https://adoptium.net/) recommended |
| PostgreSQL | 17 | System install or Docker-only for the database |
| Node.js | 22+ | npm 11+ included |
| Git Bash (Windows) | Any | Needed for `./mvnw` and `source` on Windows |

**Maven is not required globally.** The repository includes a Maven wrapper (`mvnw` / `mvnw.cmd`) that downloads the correct version automatically on first use.

---

## Step 1 — Clone the Repository

```bash
git clone <repository-url>
cd kiro_ai
```

---

## Step 2 — Create PostgreSQL Databases

### Development database

```bash
# Connect to PostgreSQL
psql -U postgres -h localhost

# Create the development database
CREATE DATABASE supplychain_sentinel;
\q
```

### Test database (required for `./mvnw test`)

The test suite uses an isolated database to avoid contaminating development data.

```bash
psql -U postgres -h localhost -c "CREATE DATABASE supplychain_sentinel_test;"
```

> **Important:** Never run `./mvnw test` against your development or shared database. The integration tests use `@Transactional` rollback for isolation, but the V1–V8 schema is applied by Flyway on first run and only once.

If your PostgreSQL installation uses a non-default port (e.g., a trust-auth instance on 5433 for local development), adjust `DB_PORT` accordingly.

---

## Step 3 — Configure the Backend

```bash
cd backend
cp .env.example .env
```

Open `backend/.env` in a text editor and fill in:

```bash
# Required — must match your PostgreSQL installation
DB_HOST=localhost
DB_PORT=5432          # adjust if using a custom port
DB_NAME=supplychain_sentinel
DB_USERNAME=postgres
DB_PASSWORD=your-postgres-password

# Required — must be at least 32 characters
JWT_SECRET=generate-a-256-bit-random-key-here-minimum-32-chars

# Required — must be at least 32 characters; used by Jenkins/GitHub Actions
CI_INGESTION_API_KEY=generate-a-strong-random-key-here

# Optional — admin account provisioned on first startup
INITIAL_ADMIN_USERNAME=admin
INITIAL_ADMIN_PASSWORD=choose-a-strong-password
INITIAL_ADMIN_EMAIL=admin@example.com
```

### How Spring Boot loads configuration

Spring Boot does **not** automatically load `.env` files. You must export the variables into the shell environment before starting the backend.

**Using `run.sh` (recommended on Git Bash / Linux / macOS):**

```bash
# run.sh automatically sources .env before launching the JAR
./run.sh
```

**Manually in Git Bash:**

```bash
set -a && source .env && set +a
java -jar target/supplychain-sentinel-backend-0.1.0.jar
```

**Or using Maven directly:**

```bash
set -a && source .env && set +a
./mvnw spring-boot:run
```

---

## Step 4 — Build the Backend

```bash
cd backend
./mvnw clean package -DskipTests
```

This compiles the application and produces `target/supplychain-sentinel-backend-0.1.0.jar`.

On the first run, the Maven wrapper downloads Maven 3.9. Subsequent runs use the cached download.

To compile and run tests in the same step (requires the test database):

```bash
./mvnw clean test
```

---

## Step 5 — Start the Backend

```bash
# Option A — Using run.sh (loads .env automatically)
cd backend
./run.sh

# Option B — Load .env manually then run
cd backend
set -a && source .env && set +a
./mvnw spring-boot:run
```

On first startup Flyway applies all migrations (V1–V8). Watch the log for:

```
Flyway ... Successfully applied 9 migrations
```

If `INITIAL_ADMIN_USERNAME`, `INITIAL_ADMIN_PASSWORD`, and `INITIAL_ADMIN_EMAIL` are set and no matching user exists, the admin account is provisioned:

```
INFO c.s.config.AdminProvisioningRunner : Initial admin user 'admin' provisioned successfully
```

---

## Step 6 — Verify the Backend

```bash
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

curl http://localhost:8080/api/v1/ping
# Expected: {"status":"UP","service":"supplychain-sentinel","version":"0.1.0",...}
```

Swagger UI: http://localhost:8080/swagger-ui.html

---

## Step 7 — Configure the Frontend

```bash
cd frontend
cp .env.example .env
```

The default `.env` content:

```
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

In development mode, Vite proxies all `/api/v1` requests to `http://localhost:8080`, so this value is only used in production builds. The default works without changes.

---

## Step 8 — Start the Frontend

In a **separate terminal**:

```bash
cd frontend
npm ci        # reproducible install from package-lock.json
npm run dev   # starts Vite dev server
```

Access the application: **http://localhost:5173**

The Vite dev server provides hot-module replacement. API calls are proxied to the backend on port 8080.

---

## Step 9 — Log In

Navigate to http://localhost:5173 and log in with the admin credentials you configured in `INITIAL_ADMIN_*`.

---

## Stopping the Application

Press **Ctrl+C** in each terminal to stop the backend and frontend.

---

## Running the Test Suite

```bash
cd backend
./mvnw clean test
```

Tests run against `supplychain_sentinel_test` (configured in `src/test/resources/application-test.yml`).

The test profile uses a hardcoded ingestion key (`test-ingestion-key-for-ci-pipeline-integration`). You do not need `CI_INGESTION_API_KEY` set for the test run.

Latest result: **184 tests, 0 failures**.

---

## Building for Production

```bash
# Frontend
cd frontend
npm run build
# Output: frontend/dist/

# Backend
cd backend
./mvnw clean package -DskipTests
# Output: backend/target/supplychain-sentinel-backend-0.1.0.jar
```

---

## Troubleshooting

### PostgreSQL: Connection refused

**Symptom:** `Connection refused` or `FATAL: password authentication failed`

**Check:**
```bash
# Is PostgreSQL running?
pg_ctl status -D /path/to/data

# Can you connect?
psql -U postgres -h localhost -p 5432 -c "SELECT 1;"
```

- Verify `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD` in `.env`
- If using a non-standard port, update `DB_PORT`
- Check `pg_hba.conf` authentication method (needs `md5` or `scram-sha-256`, not `ident`)

### JWT secret not set

**Symptom:** Application exits with `JWT secret must be set` or similar

**Fix:** Ensure `JWT_SECRET` is set in your environment before starting. It must be at least 32 bytes. If using `run.sh`, verify `.env` contains the variable.

### Backend port 8080 in use

```bash
# Windows
netstat -ano | findstr :8080

# Kill the process using the PID shown
taskkill /PID <pid> /F
```

Or change `SERVER_PORT` in `.env`.

### Frontend port 5173 in use

Vite will automatically try 5174, 5175, etc. Check the terminal output for the actual URL.

### Flyway migration failure

**Symptom:** `Schema-validation: missing column` or `Flyway checksum mismatch`

This usually means the test database has a partial migration from a previous failed run.

```bash
# Drop and recreate the test database
psql -U postgres -h localhost -c "DROP DATABASE supplychain_sentinel_test;"
psql -U postgres -h localhost -c "CREATE DATABASE supplychain_sentinel_test;"
```

**Never modify V1–V8 migration files.** Flyway verifies checksums on startup.

### CORS errors in browser

**Symptom:** `Access to XMLHttpRequest... has been blocked by CORS`

Ensure `APP_CORS_ALLOWED_ORIGINS` includes your frontend origin. For local dev the default `http://localhost:5173,http://localhost:4173` covers Vite's default ports.

### Missing initial admin

If `INITIAL_ADMIN_*` was not set on first startup, create the admin via API after setting the variables and restarting:

```bash
# The runner checks on every startup if the username already exists.
# Set the env vars and restart the backend — it will provision the user.
```

### `./mvnw` permission denied (Linux/macOS/Git Bash)

```bash
chmod +x mvnw
./mvnw clean package
```

### Docker Compose: daemon not running

```
Cannot connect to the Docker daemon at unix:///var/run/docker.sock
```

Start Docker Desktop and wait for it to report "Engine running" before retrying.

---

## Additional Resources

- [API Documentation](api-documentation.md)
- [Jenkins Integration](integrations/jenkins.md)
- [GitHub Actions Integration](integrations/github-actions.md)
- [Trivy Integration](integrations/trivy.md)
- [Syft SBOM Integration](integrations/syft.md)
- [Cosign Artifact Integrity](integrations/cosign.md)
- [SARIF Integration](integrations/sarif.md)
