# API Documentation

Base URL: `/api/v1`

Interactive docs available at `/swagger-ui.html` when the backend is running.

## Authentication

### POST /auth/login
Authenticate and receive an access token. A refresh token is set as an HttpOnly cookie.

**Request:**
```json
{
  "username": "admin",
  "password": "your-password"
}
```

**Response (200):**
```json
{
  "accessToken": "eyJhbGci...",
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "username": "admin",
    "email": "admin@example.com",
    "displayName": "System Administrator",
    "roles": ["ADMIN"],
    "enabled": true,
    "createdAt": "2026-11-03T..."
  }
}
```

**Set-Cookie:** `scs_refresh_token=<uuid>; Path=/api/v1/auth; Max-Age=604800; HttpOnly; SameSite=Lax`

**Errors:**
- 401 — Invalid username or password
- 401 — Account is disabled (only when password is correct)
- 423 — Account temporarily locked (brute-force protection)

### POST /auth/refresh
Exchange the refresh-token cookie for a new access token. The old refresh token is revoked (rotation).

**Cookie:** `scs_refresh_token=<uuid>`

**Response (200):** Same as login response body. New cookie is set.

**Errors:**
- 401 — Invalid, expired, or revoked refresh token

### POST /auth/logout
Revoke the refresh token and clear the cookie.

**Auth:** Bearer token required.
**Cookie:** `scs_refresh_token=<uuid>`

**Response:** 204 No Content. Cookie is cleared (`Max-Age=0`).

### GET /auth/me
Get the currently authenticated user.

**Auth:** Bearer token required.

**Response (200):**
```json
{
  "id": "uuid",
  "username": "admin",
  "email": "admin@example.com",
  "displayName": "System Administrator",
  "roles": ["ADMIN"],
  "enabled": true,
  "createdAt": "2026-11-03T..."
}
```

## User Management

All endpoints require authentication (Bearer token). Write operations require the `ADMIN` role.

### GET /users
List all users. **Any authenticated role.**

**Response (200):** Array of user objects.

### GET /users/{id}
Get a single user by UUID. **Any authenticated role.**

### POST /users
Create a new user. **ADMIN only.**

**Request:**
```json
{
  "username": "newuser",
  "email": "newuser@example.com",
  "password": "StrongPass123!",
  "displayName": "New User",
  "roles": ["DEVELOPER"]
}
```

**Response (201):** Created user object.

### PUT /users/{id}
Update a user. **ADMIN only.** All fields optional.

**Request:**
```json
{
  "email": "updated@example.com",
  "displayName": "Updated Name",
  "roles": ["SECURITY_ENGINEER"],
  "enabled": false,
  "password": "NewStrongPass123!"
}
```

**Response (200):** Updated user object.

### DELETE /users/{id}
Delete a user. **ADMIN only.** Cascades to `user_roles` and `refresh_tokens`.

**Response:** 204 No Content.

## Health

### GET /ping
Public liveness check. No auth required.

**Response (200):**
```json
{
  "status": "UP",
  "service": "supplychain-sentinel",
  "version": "0.1.0",
  "timestamp": "2026-11-03T..."
}
```

### GET /actuator/health
Spring Boot Actuator health. No auth required.

**Response (200):** `{"status":"UP"}`

## Error Responses

All errors return a consistent `ApiError` shape:

```json
{
  "timestamp": "2026-11-03T...",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication is required to access this resource",
  "path": "/api/v1/users",
  "details": []
}
```

| Status | Error | When |
|--------|-------|------|
| 400 | Bad Request | Validation failure |
| 401 | Unauthorized | Missing/invalid token, bad credentials |
| 403 | Forbidden | Authenticated but insufficient role |
| 404 | Not Found | Resource not found |
| 409 | Conflict | Duplicate username/email |
| 423 | Locked | Brute-force lockout active |
| 500 | Internal Server Error | Unexpected failure |
