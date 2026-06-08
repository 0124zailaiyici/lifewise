#!/bin/bash
# LifeWise standardized production deploy script.
# Run on server: cd /opt/lifewise && bash deploy-latest.sh

set -euo pipefail

APP_DIR="${APP_DIR:-/opt/lifewise}"
PORT="${PORT:-8082}"
PACKAGE_URL="${PACKAGE_URL:-https://nightly.link/0124zailaiyici/lifewise/workflows/deploy/master/lifewise-deploy.zip}"
TS="$(date +%Y%m%d_%H%M%S)"

cd "$APP_DIR"

echo "================================================"
echo " LifeWise deploy latest - $TS"
echo " App dir: $APP_DIR"
echo " Backend port: $PORT"
echo "================================================"

echo ">>> Stop legacy systemd service if it exists"
if systemctl list-unit-files 2>/dev/null | grep -q '^lifewise.service'; then
  systemctl stop lifewise 2>/dev/null || true
  systemctl disable lifewise 2>/dev/null || true
fi

echo ">>> Stop all LifeWise backend processes"
bash stop.sh 2>/dev/null || true
pkill -f "lifewise-backend.jar" 2>/dev/null || true
sleep 2

echo ">>> Verify no old backend remains"
if pgrep -af "lifewise-backend.jar" >/tmp/lifewise-running.txt 2>/dev/null; then
  cat /tmp/lifewise-running.txt
  echo "ERROR: backend process still running, abort."
  exit 1
fi

echo ">>> Backup database and config"
mkdir -p backups
[ -f data/lifewise.mv.db ] && cp data/lifewise.mv.db "backups/lifewise-$TS.mv.db"
[ -f application-cloud.properties ] && cp application-cloud.properties "backups/application-cloud-$TS.properties"

echo ">>> Download deploy package"
wget -q -O lifewise-new.zip "$PACKAGE_URL"
unzip -q -o lifewise-new.zip
rm -f lifewise-new.zip

echo ">>> Install files"
mkdir -p frontend uploads data
if [ -d "$APP_DIR/frontend" ] && [ -f frontend/index.html ]; then
  # Package is normally unzipped directly into /opt/lifewise, so this is often a no-op.
  true
fi
chmod +x start.sh stop.sh update.sh backup.sh install-backup-cron.sh deploy-latest.sh 2>/dev/null || true

echo ">>> Config safety checks"
if [ ! -f application-cloud.properties ]; then
  cat > application-cloud.properties <<EOF
server.port=$PORT
ai.deepseek-enabled=false
app.upload-dir=$APP_DIR/uploads
EOF
fi
grep -q '^server.port=' application-cloud.properties || echo "server.port=$PORT" >> application-cloud.properties
grep -q '^ai.deepseek-enabled=' application-cloud.properties || echo "ai.deepseek-enabled=false" >> application-cloud.properties
grep -q '^app.upload-dir=' application-cloud.properties || echo "app.upload-dir=$APP_DIR/uploads" >> application-cloud.properties

if ! grep -q '^ai.dashscope-api-key=' application-cloud.properties; then
  echo "WARNING: ai.dashscope-api-key is not configured. Qwen will be unavailable."
fi

echo ">>> Start backend"
bash start.sh
sleep 8

echo ">>> Verify"
if ! ss -tlnp | grep -q ":$PORT"; then
  echo "ERROR: backend port $PORT is not listening"
  tail -60 backend.log || true
  exit 1
fi

if pgrep -af "/app/lifewise/lifewise-backend.jar" >/tmp/lifewise-legacy.txt 2>/dev/null; then
  cat /tmp/lifewise-legacy.txt
  echo "ERROR: legacy /app/lifewise backend is running. Stop it before continuing."
  exit 1
fi

echo "================================================"
echo " Deploy complete"
echo " PID: $(cat backend.pid 2>/dev/null || echo unknown)"
echo " Recent log:"
tail -8 backend.log || true
echo "================================================"
