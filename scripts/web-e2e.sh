#!/usr/bin/env bash
# Web 端端到端测试：启动临时的 Go 服务端，让 webtest/sync.test.mjs 里的两台「网页设备」对着它同步，
# 并检查 / 与 /app/ 静态资源可访问。需要 Go 与 Node（≥ 20）：bash scripts/web-e2e.sh
set -euo pipefail
cd "$(dirname "$0")/.."
PORT="${PORT:-18081}"
TMP="$(mktemp -d)"
SRV_PID=""
trap '[ -n "$SRV_PID" ] && kill "$SRV_PID" 2>/dev/null || true; rm -rf "$TMP"' EXIT

(cd server && go build -o "$TMP/mintcal-server" ./cmd/mintcal-server)
MINTCAL_ADDR="127.0.0.1:$PORT" MINTCAL_DB="$TMP/e2e.db" MINTCAL_ADMIN_USER=root MINTCAL_ADMIN_PASSWORD=rootpassword \
  "$TMP/mintcal-server" > "$TMP/server.log" 2>&1 &
SRV_PID=$!
for _ in $(seq 1 50); do curl -sf "http://127.0.0.1:$PORT/healthz" >/dev/null && break; sleep 0.2; done

BASE="http://127.0.0.1:$PORT"
TOKEN=$(curl -sf -X POST "$BASE/api/v1/login" -H 'Content-Type: application/json' \
  -d '{"username":"root","password":"rootpassword","deviceName":"web-e2e"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -sf -X POST "$BASE/api/v1/admin/users" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"username":"webuser","password":"webuserpassword"}' >/dev/null

for p in / /app/app.css /app/js/main.js /app/manifest.webmanifest /admin/; do
  curl -sf -o /dev/null "$BASE$p" || { echo "FAIL: GET $p"; exit 1; }
done
echo "static assets OK"

cd server
MINTCAL_URL="$BASE" MINTCAL_USER=webuser MINTCAL_PASSWORD=webuserpassword node --test webtest/*.test.mjs
