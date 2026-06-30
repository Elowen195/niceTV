# NiceTV Security Plan

This document is the production hardening checklist for the NiceTV backend on a small VPS.

## Current Target Architecture

```text
Internet
  |
  | 80/443
  v
nginx on VPS
  |
  | http://127.0.0.1:8080
  v
NiceTV API container
  |
  | docker internal network
  v
PostgreSQL container
```

PostgreSQL must not be exposed on the VPS host network. The API should only bind to `127.0.0.1:8080`; public traffic enters through nginx.

## Implemented Controls

- Docker Compose reads secrets from `backend/.env`.
- PostgreSQL has no public `ports` mapping.
- API is mapped as `127.0.0.1:8080:8080`.
- Containers use `restart: unless-stopped`.
- PostgreSQL has a healthcheck before API startup.
- nginx templates include:
  - HTTPS redirect.
  - reverse proxy to `127.0.0.1:8080`.
  - rate limits for auth, write, and read paths.
  - basic security headers.
  - 1 MB request body limit.
- API has in-memory per-IP rate limiting as defense in depth.
- Backup and restore scripts are in `backend/scripts/`.
- `backend/backups/` is ignored by Git.

## VPS Deployment

Create `backend/.env`:

```bash
cd ~/Projects/niceTV/backend
cat > .env <<'EOF'
POSTGRES_PASSWORD=replace-with-a-new-random-password
JWT_SECRET=replace-with-a-new-random-secret
CORS_ORIGIN=https://api.linux-de.me
API_ORIGIN=https://api.linux-de.me
BACKUP_DIR=./backups
BACKUP_RETENTION_DAYS=7
EOF
chmod 600 .env
```

For an existing database volume, update the real PostgreSQL user password before restarting the API:

```bash
set -a
. ./.env
set +a
docker compose exec postgres psql -U nicetv -d nicetv -c "ALTER USER nicetv WITH PASSWORD '$POSTGRES_PASSWORD';"
```

Apply migrations and restart:

```bash
docker compose exec -T postgres psql -U nicetv -d nicetv < migrations/002_collections.sql
docker compose up -d --build
```

## nginx Install

Install the http-level config:

```bash
sudo cp deploy/nginx/nicetv-api-http.conf /etc/nginx/conf.d/nicetv-api-http.conf
```

Install the site config:

```bash
sudo cp deploy/nginx/nicetv-api-site.conf /etc/nginx/sites-available/nicetv-api
sudo ln -sf /etc/nginx/sites-available/nicetv-api /etc/nginx/sites-enabled/nicetv-api
```

If the domain or certificate path differs, edit `/etc/nginx/sites-available/nicetv-api`.

Validate and reload:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

## Verification

Run from the VPS:

```bash
scripts/security-check.sh
```

Manual checks:

```bash
docker compose ps
curl -i http://127.0.0.1:8080/healthz
curl -i https://api.linux-de.me/healthz
curl -i https://api.linux-de.me/v1/collections/public
ss -tulpn | grep -E '(:5432|:8080)'
```

Expected port state:

```text
127.0.0.1:8080 -> API
no 0.0.0.0:5432
no [::]:5432
```

## Backups

Create a backup:

```bash
scripts/backup-postgres.sh
```

Suggested cron entry:

```cron
17 3 * * * cd /home/claw/Projects/niceTV/backend && scripts/backup-postgres.sh >> backups/backup.log 2>&1
```

Restore a backup only after confirming the target:

```bash
CONFIRM_RESTORE=yes scripts/restore-postgres.sh backups/nicetv-YYYYmmddTHHMMSSZ.dump
```

For real production use, copy backups off the VPS as well. Local-only backups do not protect against disk loss or account compromise.

## Ongoing Operations

- Rotate `POSTGRES_PASSWORD` and `JWT_SECRET` if they are exposed.
- Keep `backend/.env` permission at `600`.
- Keep only ports `80` and `443` publicly open unless SSH is required.
- Check `docker compose logs --tail=100 api` after deployment.
- Run `scripts/security-check.sh` after every Compose or nginx change.
- Test backup restore periodically on a disposable server or local database.

## Next Security Improvements

- Add moderation tools for public comments and public collections.
- Add account lockout or CAPTCHA if auth abuse appears.
- Move backups to encrypted object storage.
- Add monitoring for API 5xx, nginx 429, disk usage, and backup failures.
