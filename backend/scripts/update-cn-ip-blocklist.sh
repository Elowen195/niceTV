#!/usr/bin/env sh
set -eu

SOURCE_URL="${CN_ZONE_URL:-https://www.ipdeny.com/ipblocks/data/countries/cn.zone}"
OUT_FILE="${1:-/etc/nginx/geo/nicetv-cn.zone}"
TMP_FILE="$(mktemp)"
trap 'rm -f "$TMP_FILE"' EXIT

curl -fsSL "$SOURCE_URL" |
    awk '
        /^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+\/[0-9]+$/ { print $1 " 1;" }
    ' > "$TMP_FILE"

if [ ! -s "$TMP_FILE" ]; then
    echo "downloaded blocklist is empty: $SOURCE_URL" >&2
    exit 1
fi

install -d -m 0755 "$(dirname "$OUT_FILE")"
install -m 0644 "$TMP_FILE" "$OUT_FILE"

echo "updated $OUT_FILE"
echo "run: sudo nginx -t && sudo systemctl reload nginx"
