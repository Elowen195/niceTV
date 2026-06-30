#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT_DIR"

BACKUP_FILE="${1:-}"
if [ -z "$BACKUP_FILE" ]; then
    echo "usage: scripts/restore-postgres.sh backups/nicetv-YYYYmmddTHHMMSSZ.dump" >&2
    exit 2
fi

if [ ! -f "$BACKUP_FILE" ]; then
    echo "backup file not found: $BACKUP_FILE" >&2
    exit 2
fi

if [ "${CONFIRM_RESTORE:-}" != "yes" ]; then
    echo "restore is destructive. rerun with CONFIRM_RESTORE=yes" >&2
    exit 2
fi

cat "$BACKUP_FILE" | docker compose exec -T postgres sh -c \
    'pg_restore -U "${POSTGRES_USER:-nicetv}" -d "${POSTGRES_DB:-nicetv}" --clean --if-exists --no-owner --no-privileges'

echo "restore complete"
