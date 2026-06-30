# NiceTV Backend

Go + Chi + PostgreSQL backend for NiceTV accounts, cloud favorites sync, comments, and shared collections.

## Features

- Register, login, refresh token, logout.
- JWT access tokens and hashed refresh tokens.
- User profile read/update.
- Video page references.
- Cloud favorites with incremental sync and tombstones.
- Comments, replies, soft delete, likes.
- Shared video collections with private, unlisted, and public visibility.

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

## Docker Compose Deploy

On the VPS, pull the latest code and create a local `.env` from the example:

```bash
git pull
cp .env.example .env
nano .env
docker compose up -d --build
```

The production Compose file does not expose PostgreSQL publicly, and binds the API to `127.0.0.1:8080` for nginx reverse proxying.

For an existing PostgreSQL volume, changing `POSTGRES_PASSWORD` in `.env` does not update the database user automatically. Change the real database password first, then restart the API:

```bash
docker compose exec postgres psql -U nicetv -d nicetv -c "ALTER USER nicetv WITH PASSWORD 'your-new-postgres-password';"
docker compose up -d --build
```

If the PostgreSQL volume already exists, apply new migrations before rebuilding the API:

```bash
docker compose exec -T postgres psql -U nicetv -d nicetv < migrations/002_collections.sql
docker compose up -d --build
```

Keep production secrets in a local `.env` file or in the shell environment, not in Git:

```bash
DATABASE_URL=postgres://nicetv:nicetv@postgres:5432/nicetv?sslmode=disable
JWT_SECRET=replace-with-a-long-random-secret
```

GitHub Actions only checks that tests pass and the Docker image can build.

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
GET  /v1/collections/public
GET  /v1/collections/{idOrSlug}
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
GET    /v1/collections/mine
GET    /v1/collections/mine/{idOrSlug}
POST   /v1/collections
PATCH  /v1/collections/{collectionId}
DELETE /v1/collections/{collectionId}
POST   /v1/collections/{collectionId}/items
DELETE /v1/collections/{collectionId}/items/{itemId}
POST   /v1/collections/{idOrSlug}/copy
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
