#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [ -f .env ]; then
    set -a
    . ./.env
    set +a
fi

API_ORIGIN="${API_ORIGIN:-${CORS_ORIGIN:-https://api.linux-de.me}}"

echo "== docker compose ps =="
docker compose ps

echo
echo "== local API health =="
curl -fsS http://127.0.0.1:8080/healthz
echo

echo
echo "== public API health =="
curl -fsS "$API_ORIGIN/healthz"
echo

echo
echo "== public collections =="
curl -fsS "$API_ORIGIN/v1/collections/public"
echo

echo
echo "== listening ports =="
ss -tulpn | grep -E '(:5432|:8080)' || true

if ss -tulpn | grep -E '0\.0\.0\.0:5432|\[::\]:5432' >/dev/null; then
    echo "unsafe: postgres is exposed publicly" >&2
    exit 1
fi

if ! ss -tulpn | grep '127.0.0.1:8080' >/dev/null; then
    echo "unsafe: api is not bound to 127.0.0.1:8080" >&2
    exit 1
fi

echo "security check passed"
