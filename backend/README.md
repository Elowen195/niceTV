# NiceTV Backend

Go + Chi + PostgreSQL backend for NiceTV accounts, cloud favorites sync, and comments.

## Features

- Register, login, refresh token, logout.
- JWT access tokens and hashed refresh tokens.
- User profile read/update.
- Video page references.
- Cloud favorites with incremental sync and tombstones.
- Comments, replies, soft delete, likes.

## Requirements

- Go 1.22+
- PostgreSQL 16+
- Docker Compose, optional

## Quick Start

Start PostgreSQL and API:

```powershell
docker compose up --build
```

The API listens on `http://127.0.0.1:8080`.

Health check:

```powershell
curl http://127.0.0.1:8080/healthz
```

## Docker Image

GitHub Actions publishes the backend image to:

```text
ghcr.io/elowen195/nicetv-backend
```

Tags:

- Push to `main`: `latest`, `main`, and `sha-<commit>`.
- Push a version tag like `v1.0.0`: `v1.0.0`, `1.0.0`, and `1.0`.

## Local Run

Start only PostgreSQL:

```powershell
docker compose up -d postgres
```

Run the API:

```powershell
$env:DATABASE_URL="postgres://nicetv:nicetv@127.0.0.1:5432/nicetv?sslmode=disable"
$env:JWT_SECRET="dev-secret"
go run ./cmd/server
```

If Go dependency download is blocked, use the local proxy only for the command:

```powershell
$env:HTTP_PROXY="http://127.0.0.1:2080"
$env:HTTPS_PROXY="http://127.0.0.1:2080"
go mod tidy
```

## Environment

| Name | Default | Required | Description |
| --- | --- | --- | --- |
| `ADDR` | `:8080` | No | HTTP listen address. |
| `DATABASE_URL` | | Yes | PostgreSQL connection string. |
| `JWT_SECRET` | `dev-only-secret` in dev | Production yes | HMAC secret for access tokens. |
| `ACCESS_TOKEN_TTL` | `15m` | No | Access token lifetime. |
| `REFRESH_TOKEN_TTL` | `720h` | No | Refresh token lifetime. |
| `CORS_ORIGIN` | `*` | No | CORS allow origin. |

## API Overview

Public:

```http
GET  /healthz
POST /v1/auth/register
POST /v1/auth/login
POST /v1/auth/refresh
POST /v1/auth/logout
POST /v1/video-refs
GET  /v1/video-refs/{videoRefId}/comments
```

Authenticated:

```http
GET    /v1/me
PATCH  /v1/me
GET    /v1/favorites
PUT    /v1/favorites/{videoRefId}
DELETE /v1/favorites/{videoRefId}
POST   /v1/favorites/sync
POST   /v1/video-refs/{videoRefId}/comments
PATCH  /v1/comments/{commentId}
DELETE /v1/comments/{commentId}
PUT    /v1/comments/{commentId}/like
DELETE /v1/comments/{commentId}/like
```

Use authenticated endpoints with:

```http
Authorization: Bearer <accessToken>
```

## Example

Register:

```powershell
curl -X POST http://127.0.0.1:8080/v1/auth/register `
  -H "Content-Type: application/json" `
  -d "{\"username\":\"furi01\",\"password\":\"111111\",\"deviceName\":\"android\"}"
```

Create or update a video ref:

```powershell
curl -X POST http://127.0.0.1:8080/v1/video-refs `
  -H "Content-Type: application/json" `
  -d "{\"source\":\"supjav\",\"sourceUrl\":\"https://supjav.com/438815.html\",\"title\":\"Demo\"}"
```

## Development Checks

```powershell
go fmt ./...
go test ./...
go build ./cmd/server
```

## Notes

- MVP does not require Redis.
- The service stores page URLs and user-generated data only.
- PostgreSQL migrations live in `migrations/`. Docker Compose applies them when the database volume is first created.
