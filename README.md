# SupplyChain Sentinel

**Software Supply Chain Risk & Release Assurance Platform**

An enterprise-grade platform that centralizes software supply-chain security information and manages security governance throughout the software development and release lifecycle.

> **Note:** This is a portfolio-grade engineering project. Data is seeded as simulated demonstration data and is clearly identified as such.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 19, Vite 6, TypeScript, Tailwind CSS, Apache ECharts, React Router 7, Axios |
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Hibernate, JWT (JJWT 0.12), Flyway |
| Database | PostgreSQL 17 |
| API Docs | springdoc-openapi (Swagger UI) |
| Container | Docker, Docker Compose, Nginx (frontend), JRE 21 (backend) |
| Future AWS | S3 + CloudFront (frontend), EKS (backend), RDS (database), Terraform, GitHub Actions |

---

## Project Structure

```
kiro_ai/
├── docker-compose.yml          # PostgreSQL + backend + frontend
├── .env.example                # Root env vars for docker-compose
├── README.md
├── docs/
│   ├── api-documentation.md    # API endpoint reference
│   └── local-setup.md          # Detailed local development guide
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── .env.example
│   ├── mvnw / mvnw.cmd          # Maven wrapper
│   └── src/
│       ├── main/
│       │   ├── java/com/supplychainsentinel/
│       │   │   ├── SupplyChainSentinelApplication.java
│       │   │   ├── config/       # CORS, OpenAPI, admin provisioning
│       │   │   ├── controller/   # REST endpoints (auth, users, health)
│       │   │   ├── dto/          # Data transfer objects
│       │   │   ├── entity/       # JPA entities (User, Role, RefreshToken)
│       │   │   ├── exception/    # Global exception handler, AuthException
│       │   │   ├── mapper/       # Entity <-> DTO mappers
│       │   │   ├── repository/   # Spring Data JPA repositories
│       │   │   ├── security/     # JWT, SecurityConfig, filters, UserDetailsService
│       │   │   └── service/      # AuthService, UserService (business logic)
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       ├── application-docker.yml
│       │       ├── application-prod.yml
│       │       └── db/migration/ # Flyway migrations (V1, V2)
│       └── test/
│           ├── java/.../AuthIntegrationTest.java
│           └── resources/application-test.yml
└── frontend/
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── tailwind.config.js
    ├── Dockerfile
    ├── nginx.conf
    ├── .env.example
    └── src/
        ├── api/          # Axios client, auth API, in-memory token store
        ├── components/   # Sidebar, TopBar, PageHeader
        ├── context/      # AuthContext (in-memory token, RBAC helpers)
        ├── layouts/       # MainLayout with sidebar
        ├── pages/         # Login, Dashboard, Applications, etc.
        ├── routes/        # ProtectedRoute
        └── types/         # TypeScript types
```

---

## Prerequisites

### Local development
- **Java 21** (JDK)
- **Maven 3.9+** (or use the included Maven wrapper)
- **Node.js 22+** and **npm**
- **PostgreSQL 17** (or use Docker Compose)

### Docker Compose
- **Docker** and **Docker Compose**

---

## Local Development

### 1. Database

Start a PostgreSQL instance and create the database:

```bash
createdb supplychain_sentinel
```

For running tests, create a separate test database:

```bash
createdb supplychain_sentinel_test
```

### 2. Backend

```bash
cd backend
cp .env.example .env
# Edit .env: set DB credentials, JWT_SECRET, and INITIAL_ADMIN_* vars

# Build
mvn clean package -DskipTests

# Run
java -jar target/supplychain-sentinel-backend-0.1.0.jar
# Or: mvn spring-boot:run
```

The backend starts on `http://localhost:8080`.

On first startup with `INITIAL_ADMIN_*` env vars set, an admin user is automatically provisioned.

Verify:
- Health: `GET http://localhost:8080/actuator/health`
- Ping: `GET http://localhost:8080/api/v1/ping`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- API Docs: `http://localhost:8080/api-docs`

### 3. Frontend

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

The frontend dev server starts on `http://localhost:5173`.

The Vite dev server proxies `/api` requests to the backend at `http://localhost:8080`.

### 4. Production builds

```bash
# Frontend
cd frontend
npm run build    # outputs to dist/

# Backend
cd backend
mvn clean package   # outputs executable JAR to target/
```

---

## Docker Compose

Run the complete stack with a single command:

```bash
cp .env.example .env
# Edit .env: set DB credentials, JWT_SECRET, INITIAL_ADMIN_* vars
docker compose up --build
```

Services:
- **Frontend:** `http://localhost:3000`
- **Backend:** `http://localhost:8080`
- **PostgreSQL:** `localhost:5432`

---

## Authentication Flow

The platform uses a stateless JWT authentication strategy with refresh-token rotation.

### Login
1. Client sends `POST /api/v1/auth/login` with `{ username, password }`.
2. Backend verifies credentials with BCrypt, checks account status.
3. Backend returns **access token** (JWT, 15 min TTL) in the JSON response body.
4. Backend sets **refresh token** (random UUID, 7 day TTL) as an `HttpOnly`, `SameSite=Lax` cookie.
5. Frontend stores the access token **in memory only** (never localStorage).
6. Frontend stores user info in React state.

### API Requests
1. Frontend attaches `Authorization: Bearer <access_token>` to every API request.
2. Backend `JwtAuthenticationFilter` validates the JWT signature, issuer, and expiration.
3. Filter loads the user from the database and checks account status (enabled, non-locked).
4. Security context is set; `@PreAuthorize` annotations enforce RBAC.

### Automatic Refresh
1. When the access token expires, the API returns `401 Unauthorized`.
2. Axios response interceptor calls `POST /api/v1/auth/refresh` (cookie sent automatically).
3. Backend validates the refresh token, revokes the old one, issues a new rotated token.
4. New access token is set in memory; the original request is retried.
5. If refresh fails, the user is redirected to `/login`.

### Logout
1. Client sends `POST /api/v1/auth/logout` (with cookie).
2. Backend revokes the refresh token in the database.
3. Backend clears the cookie (`Max-Age=0`).
4. Frontend clears the in-memory access token and user state.

### Security Measures
- **Access token in memory only** — never persisted to localStorage (XSS mitigation).
- **Refresh token in HttpOnly cookie** — not accessible via JavaScript.
- **SameSite=Lax** — prevents CSRF on the refresh endpoint.
- **Refresh token rotation** — each refresh revokes the old token and issues a new one.
- **Token reuse detection** — presenting a revoked refresh token is rejected.
- **Brute-force protection** — account lockout after 5 failed attempts (configurable).
- **Username enumeration prevention** — login errors are generic ("Invalid username or password").
- **JWT issuer validation** — tokens must contain the configured issuer claim.
- **JWT signature validation** — tokens signed with a different key are rejected.
- **Account status enforcement** — disabled/locked users are rejected on every request.
- **Passwords never logged** — BCrypt hashes are never included in log output.
- **JWT secrets from environment** — never hardcoded; minimum 256 bits (32 bytes).

---

## RBAC Matrix

| Endpoint | ADMIN | SECURITY_ENGINEER | DEVELOPER | RELEASE_MANAGER | AUDITOR | Unauthenticated |
|----------|:-----:|:-----------------:|:---------:|:---------------:|:-------:|:---------------:|
| `POST /auth/login` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `POST /auth/refresh` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `POST /auth/logout` | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| `GET /auth/me` | ✓ | ✓ | ✓ | ✓ | ✓ | 401 |
| `GET /users` | ✓ | ✓ | ✓ | ✓ | ✓ | 401 |
| `GET /users/{id}` | ✓ | ✓ | ✓ | ✓ | ✓ | 401 |
| `POST /users` | ✓ | 403 | 403 | 403 | 403 | 401 |
| `PUT /users/{id}` | ✓ | 403 | 403 | 403 | 403 | 401 |
| `DELETE /users/{id}` | ✓ | 403 | 403 | 403 | 403 | 401 |
| `GET /ping` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `GET /actuator/health` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

> **Separation of duties:** A developer cannot approve their own release. This business rule will be enforced in Phase 8 when approval endpoints are implemented.

---

## API Endpoints

### Authentication
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/auth/login` | Public | Authenticate, receive access token + refresh cookie |
| `POST` | `/api/v1/auth/refresh` | Cookie | Rotate refresh token, return new access token |
| `POST` | `/api/v1/auth/logout` | Bearer | Revoke refresh token, clear cookie |
| `GET` | `/api/v1/auth/me` | Bearer | Get current authenticated user |

### User Management
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/users` | Any role | List all users |
| `GET` | `/api/v1/users/{id}` | Any role | Get user by ID |
| `POST` | `/api/v1/users` | ADMIN | Create a new user |
| `PUT` | `/api/v1/users/{id}` | ADMIN | Update user (email, roles, enabled, password) |
| `DELETE` | `/api/v1/users/{id}` | ADMIN | Delete a user |

### Application Inventory
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/applications` | Any role | List applications (search, filter, paginate, sort) |
| `GET` | `/api/v1/applications/{id}` | Any role | Get application by ID |
| `POST` | `/api/v1/applications` | ADMIN, SECURITY_ENGINEER | Register a new application |
| `PUT` | `/api/v1/applications/{id}` | ADMIN, SECURITY_ENGINEER | Update an application |
| `DELETE` | `/api/v1/applications/{id}` | ADMIN | Delete an application |

### Dashboard
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/dashboard/summary` | Any role | KPI counts, distribution metrics, recent applications |

### Health
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/ping` | Public | Liveness check |
| `GET` | `/actuator/health` | Public | Spring Boot Actuator health |

---

## Environment Variables

All configuration is externalized via environment variables and Spring profiles.

### Database
| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile (`dev`, `docker`, `prod`) |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `supplychain_sentinel` | Database name |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `SERVER_PORT` | `8080` | Backend port |

### JWT & Security
| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | (required) | HMAC-SHA key, minimum 32 bytes |
| `JWT_ISSUER` | `supplychain-sentinel` | JWT issuer claim |
| `JWT_ACCESS_TOKEN_TTL_MINUTES` | `15` | Access token lifetime |
| `JWT_REFRESH_TOKEN_TTL_DAYS` | `7` | Refresh token lifetime |
| `APP_COOKIE_SECURE` | `false` | Set `true` for HTTPS (Secure cookie flag) |
| `APP_MAX_LOGIN_ATTEMPTS` | `5` | Failed attempts before lockout |
| `APP_LOCKOUT_DURATION_MINUTES` | `15` | Lockout duration |

### CORS
| Variable | Default | Description |
|----------|---------|-------------|
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:4173` | Allowed frontend origins |

### Admin Provisioning
| Variable | Default | Description |
|----------|---------|-------------|
| `INITIAL_ADMIN_USERNAME` | (empty) | Initial admin username |
| `INITIAL_ADMIN_PASSWORD` | (empty) | Initial admin password |
| `INITIAL_ADMIN_EMAIL` | (empty) | Initial admin email |

### Frontend
| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_API_BASE_URL` | `/api/v1` | Frontend API base URL |

Never hardcode credentials, JWT secrets, or API tokens. Use environment variables or `.env` files (which are gitignored).

---

## Database Migrations

Flyway manages schema evolution. Migrations are append-only and never modified once applied.

| Version | Description |
|---------|-------------|
| V1 | Initial schema: `users`, `roles`, `user_roles` + 5 seeded roles |
| V2 | Auth schema: `refresh_tokens` table, login tracking columns on `users` |
| V3 | Application inventory: `applications` table with CHECK constraints and indexes |

---

## Testing

### Backend Tests

```bash
cd backend
mvn test
```

Tests run against a **dedicated test database** (`supplychain_sentinel_test`) to avoid contaminating the development database. Each test uses `@Transactional` for data isolation (automatic rollback).

| Test Class | Tests | Covers |
|-----------|-------|--------|
| `AuthIntegrationTest` | 23 | Login, invalid credentials, disabled user, expired token, invalid signature, refresh rotation, revoked token, logout, RBAC (all 5 roles), cookie attributes |
| `ApplicationIntegrationTest` | 26 | Application CRUD, search, filter, pagination, sorting, validation, RBAC (all 5 roles) |
| `DashboardIntegrationTest` | 13 | KPI counts, distributions, recent applications, empty database, RBAC (5 roles), 401 |
| `HealthControllerTest` | 1 | Ping endpoint |
| `SupplyChainSentinelApplicationTests` | 1 | Context loads with Security + JPA + Flyway |

**Total: 64 tests, 0 failures.**

### Frontend Build

```bash
cd frontend
npm run build    # TypeScript type-check + Vite production build
```

---

## Implementation Phases

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Project foundation | ✅ Complete |
| 2 | Authentication, JWT and RBAC | ✅ Complete |
| 3 | Application Inventory | ✅ Complete |
| 4 | Executive Dashboard | ✅ Complete |
| 5 | CI/CD Pipeline Integration and Build Metadata | Planned |
| 6 | Security Findings and Vulnerability Management | Planned |
| 7 | SBOM Management and Artifact Integrity | Planned |
| 8 | Release Risk Assessment, Release Gates, Approvals, Audit Trail and Deployment Readiness | Planned |

---

## License

MIT
