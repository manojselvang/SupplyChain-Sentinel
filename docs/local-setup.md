# Local Setup Guide

Detailed instructions for running SupplyChain Sentinel locally.

## Prerequisites

- **JDK 21** — [Adoptium Temurin 21](https://adoptium.net/)
- **Maven 3.9+** — or use the included Maven wrapper (`./mvnw`)
- **Node.js 22+** and **npm**
- **PostgreSQL 17** — running locally or via Docker

## 1. Database Setup

```bash
# Main development database
createdb supplychain_sentinel

# Dedicated test database (isolated from dev data)
createdb supplychain_sentinel_test
```

## 2. Backend Setup

```bash
cd backend
cp .env.example .env
```

Edit `.env`:
```
DB_HOST=localhost
DB_PORT=5432
DB_NAME=supplychain_sentinel
DB_USERNAME=postgres
DB_PASSWORD=<your-password>

JWT_SECRET=<generate-a-256-bit-secret>

INITIAL_ADMIN_USERNAME=admin
INITIAL_ADMIN_PASSWORD=<strong-password>
INITIAL_ADMIN_EMAIL=admin@example.com
```

Build and run:
```bash
mvn clean package -DskipTests
java -jar target/supplychain-sentinel-backend-0.1.0.jar
```

Or with Maven directly:
```bash
mvn spring-boot:run
```

Verify:
- Health: http://localhost:8080/actuator/health → `{"status":"UP"}`
- Ping: http://localhost:8080/api/v1/ping
- Swagger UI: http://localhost:8080/swagger-ui.html

## 3. Frontend Setup

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

The dev server starts on http://localhost:5173 and proxies `/api` to the backend.

## 4. Running Tests

```bash
# Backend tests (uses supplychain_sentinel_test database)
cd backend
mvn test

# Frontend production build
cd frontend
npm run build
```

## 5. Docker Compose (requires Docker)

```bash
cp .env.example .env
# Edit .env with secure values
docker compose up --build
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| Backend | http://localhost:8080 |
| PostgreSQL | localhost:5432 |
