#!/bin/bash
# LifeWise one-command server update.

set -euo pipefail

APP_DIR="/opt/lifewise"
PACKAGE_URL="https://nightly.link/0124zailaiyici/lifewise/workflows/deploy/master/lifewise-deploy.zip"
JAR_URL="https://raw.githubusercontent.com/0124zailaiyici/lifewise/master/deploy/lifewise-backend.jar"
BACKUP_DIR="$APP_DIR/backups"
TS="$(date +%Y%m%d_%H%M%S)"

cd "$APP_DIR"

echo "================================================"
echo "  LifeWise update: $TS"
echo "================================================"

echo ">>> Checking legacy systemd service..."
if systemctl list-unit-files 2>/dev/null | grep -q '^lifewise.service'; then
  systemctl stop lifewise 2>/dev/null || true
  systemctl disable lifewise 2>/dev/null || true
fi

echo ">>> Stopping current backend..."
bash stop.sh 2>/dev/null || true
sleep 2

if ps aux | grep -v grep | grep -q '/app/lifewise/lifewise-backend.jar'; then
  echo "ERROR: legacy /app/lifewise backend is still running."
  ps aux | grep '/app/lifewise/lifewise-backend.jar' | grep -v grep
  exit 1
fi

echo ">>> Backing up data and config..."
mkdir -p "$BACKUP_DIR"
if [ -f data/lifewise.mv.db ]; then
  cp data/lifewise.mv.db "$BACKUP_DIR/lifewise-$TS.mv.db"
fi
if [ -f application-cloud.properties ]; then
  cp application-cloud.properties "$BACKUP_DIR/application-cloud-$TS.properties"
fi

echo ">>> Downloading latest deploy package..."
if wget -O lifewise-new.zip "$PACKAGE_URL"; then
  unzip -q -o lifewise-new.zip
  rm -f lifewise-new.zip
else
  echo "Deploy package unavailable, downloading backend JAR directly..."
  wget -O lifewise-backend.jar "$JAR_URL"
fi

chmod +x start.sh stop.sh update.sh backup.sh install-backup-cron.sh 2>/dev/null || true

echo ">>> Hardening local config..."
if [ -f application-cloud.properties ]; then
  sed -i '/^ai.api-key=/d' application-cloud.properties
  sed -i '/^ai.api-url=.*deepseek/d' application-cloud.properties
  sed -i '/^ai.model=.*deepseek/d' application-cloud.properties
fi

echo ">>> Starting backend..."
bash start.sh
sleep 8

echo ">>> Verifying backend..."
if ! ss -tlnp | grep -q ':8082'; then
  echo "ERROR: port 8082 is not listening."
  tail -40 backend.log || true
  exit 1
fi

if ps aux | grep -v grep | grep -q '/app/lifewise/lifewise-backend.jar'; then
  echo "ERROR: legacy /app/lifewise backend restarted."
  ps aux | grep '/app/lifewise/lifewise-backend.jar' | grep -v grep
  exit 1
fi

if tail -80 backend.log | grep -q "Tomcat started on port 8082"; then
  echo "================================================"
  echo "  Update complete"
  echo "  PID: $(cat backend.pid 2>/dev/null || echo unknown)"
  echo "  URL: http://47.106.126.50:8081"
  echo "================================================"
else
  echo "WARNING: startup log did not contain expected success line."
  tail -40 backend.log || true
fi
