#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [ -f .env ]; then
    set -a
    . ./.env
    set +a
fi

BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_FILE="$BACKUP_DIR/nicetv-$STAMP.dump"

mkdir -p "$BACKUP_DIR"
umask 077

docker compose exec -T postgres sh -c \
    'pg_dump -U "${POSTGRES_USER:-nicetv}" -d "${POSTGRES_DB:-nicetv}" --format=custom --no-owner --no-privileges' \
    > "$OUT_FILE"

if [ ! -s "$OUT_FILE" ]; then
    rm -f "$OUT_FILE"
    echo "backup failed: empty output" >&2
    exit 1
fi

find "$BACKUP_DIR" -type f -name 'nicetv-*.dump' -mtime +"$BACKUP_RETENTION_DAYS" -delete

echo "$OUT_FILE"
