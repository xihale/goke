#!/usr/bin/env bash
# 端到端同步测试：启动临时的 Go 服务端，用 Robolectric 里的两台「设备」对着它同步。
# 在有 Go 与 Android SDK 的机器上运行：bash scripts/sync-e2e.sh
set -euo pipefail
cd "$(dirname "$0")/.."
PORT="${PORT:-18080}"
TMP="$(mktemp -d)"
trap 'kill "$SRV_PID" 2>/dev/null || true; rm -rf "$TMP"' EXIT

(cd server && go build -o "$TMP/mintcal-server" ./cmd/mintcal-server)
MINTCAL_ADDR="127.0.0.1:$PORT" MINTCAL_DB="$TMP/e2e.db" MINTCAL_ADMIN_USER=root MINTCAL_ADMIN_PASSWORD=rootpassword \
  "$TMP/mintcal-server" > "$TMP/server.log" 2>&1 &
SRV_PID=$!
for _ in $(seq 1 50); do curl -sf "http://127.0.0.1:$PORT/healthz" >/dev/null && break; sleep 0.2; done

TOKEN=$(curl -sf -X POST "http://127.0.0.1:$PORT/api/v1/login" -H 'Content-Type: application/json' \
  -d '{"username":"root","password":"rootpassword","deviceName":"e2e"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -sf -X POST "http://127.0.0.1:$PORT/api/v1/admin/users" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"alicepassword"}' >/dev/null

export MINTCAL_TEST_SERVER="http://127.0.0.1:$PORT" MINTCAL_TEST_USER=alice MINTCAL_TEST_PASS=alicepassword
./gradlew --console=plain :app:testDebugUnitTest --tests 'app.mintcal.SyncIntegrationTest' --rerun-tasks
