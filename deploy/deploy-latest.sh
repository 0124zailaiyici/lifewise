#!/bin/bash
# LifeWise standardized production deploy script.
# Run on server: cd /opt/lifewise && bash deploy-latest.sh
# Fast modes:
#   MODE=frontend bash deploy-latest.sh  # update frontend only, no backend restart
#   MODE=backend bash deploy-latest.sh   # update backend/scripts and restart backend
#   MODE=full bash deploy-latest.sh      # update everything and restart backend (default)

set -euo pipefail

APP_DIR="${APP_DIR:-/opt/lifewise}"
PORT="${PORT:-8082}"
MODE="${MODE:-full}"
PACKAGE_URL="${PACKAGE_URL:-https://nightly.link/0124zailaiyici/lifewise/workflows/deploy/master/lifewise-deploy.zip}"
FRONTEND_PACKAGE_URL="${FRONTEND_PACKAGE_URL:-https://nightly.link/0124zailaiyici/lifewise/workflows/deploy/master/lifewise-frontend.zip}"
BACKEND_PACKAGE_URL="${BACKEND_PACKAGE_URL:-https://nightly.link/0124zailaiyici/lifewise/workflows/deploy/master/lifewise-backend.zip}"
JAR_URL="${JAR_URL:-https://raw.githubusercontent.com/0124zailaiyici/lifewise/master/deploy/lifewise-backend.jar}"
TS="$(date +%Y%m%d_%H%M%S)"
TMP_DIR="${TMP_DIR:-/tmp/lifewise-deploy-$TS}"

cd "$APP_DIR"

case "$MODE" in
  full|backend|frontend|check) ;;
  *) echo "ERROR: unsupported MODE=$MODE (use full, backend, frontend, check)"; exit 1 ;;
esac

echo "================================================"
echo " LifeWise deploy latest - $TS"
echo " App dir: $APP_DIR"
echo " Backend port: $PORT"
echo " Mode: $MODE"
echo "================================================"

runtime_check() {
  if [ -f "$APP_DIR/check-runtime.sh" ]; then
    echo ">>> Runtime safety check"
    APP_DIR="$APP_DIR" PORT="$PORT" bash "$APP_DIR/check-runtime.sh"
  else
    echo "WARNING: check-runtime.sh not found, skipped runtime safety check"
  fi
}

stop_legacy_service() {
  echo ">>> Stop legacy systemd service if it exists"
  if systemctl list-unit-files 2>/dev/null | grep -q '^lifewise.service'; then
    systemctl stop lifewise 2>/dev/null || true
    systemctl disable lifewise 2>/dev/null || true
  fi
}

stop_backend() {
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
}

backup_runtime_files() {
  echo ">>> Backup database and config"
  mkdir -p backups
  [ -f data/lifewise.mv.db ] && cp data/lifewise.mv.db "backups/lifewise-$TS.mv.db"
  [ -f application-cloud.properties ] && cp application-cloud.properties "backups/application-cloud-$TS.properties"
}

ensure_config() {
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
}

chmod_scripts() {
  chmod +x start.sh stop.sh update.sh backup.sh install-backup-cron.sh deploy-latest.sh check-runtime.sh 2>/dev/null || true
}

download_zip() {
  local url="$1"
  local dest="$2"
  mkdir -p "$(dirname "$dest")"
  echo ">>> Download: $url"
  wget -q -O "$dest" "$url"
}

install_frontend_from_dir() {
  local src="$1"
  if [ ! -f "$src/index.html" ]; then
    echo "ERROR: frontend index.html not found in $src"
    find "$src" -maxdepth 2 -type f | head -20 || true
    exit 1
  fi
  echo ">>> Install frontend only"
  mkdir -p "$APP_DIR/frontend"
  # Delete old hashed assets so mobile/desktop cannot load stale bundles.
  rm -rf "$APP_DIR/frontend/assets"
  cp -a "$src/." "$APP_DIR/frontend/"
  if command -v nginx >/dev/null 2>&1; then
    nginx -s reload 2>/dev/null || true
  fi
}

install_full_package() {
  echo ">>> Download full deploy package"
  download_zip "$PACKAGE_URL" "$TMP_DIR/lifewise-deploy.zip"
  unzip -q -o "$TMP_DIR/lifewise-deploy.zip" -d "$TMP_DIR/full"

  echo ">>> Install files"
  mkdir -p frontend uploads data
  [ -d "$TMP_DIR/full/frontend" ] && install_frontend_from_dir "$TMP_DIR/full/frontend"
  [ -f "$TMP_DIR/full/lifewise-backend.jar" ] && cp -f "$TMP_DIR/full/lifewise-backend.jar" "$APP_DIR/lifewise-backend.jar"
  for f in start.sh stop.sh update.sh backup.sh install-backup-cron.sh deploy-latest.sh check-runtime.sh; do
    [ -f "$TMP_DIR/full/$f" ] && cp -f "$TMP_DIR/full/$f" "$APP_DIR/$f"
  done
  chmod_scripts
}

install_frontend_fast() {
  echo ">>> Fast frontend deploy: no backend stop/restart"
  download_zip "$FRONTEND_PACKAGE_URL" "$TMP_DIR/lifewise-frontend.zip"
  unzip -q -o "$TMP_DIR/lifewise-frontend.zip" -d "$TMP_DIR/frontend"

  # GitHub artifact may contain dist files at zip root, or under dist/frontend depending on workflow changes.
  if [ -f "$TMP_DIR/frontend/index.html" ]; then
    install_frontend_from_dir "$TMP_DIR/frontend"
  elif [ -f "$TMP_DIR/frontend/dist/index.html" ]; then
    install_frontend_from_dir "$TMP_DIR/frontend/dist"
  elif [ -f "$TMP_DIR/frontend/frontend/index.html" ]; then
    install_frontend_from_dir "$TMP_DIR/frontend/frontend"
  else
    echo "ERROR: cannot locate frontend artifact index.html"
    find "$TMP_DIR/frontend" -maxdepth 3 -type f | head -30 || true
    exit 1
  fi

  echo ">>> Frontend deploy complete (backend was not restarted)"
}

install_backend_fast() {
  echo ">>> Fast backend deploy"
  download_zip "$BACKEND_PACKAGE_URL" "$TMP_DIR/lifewise-backend.zip" || true
  if [ -f "$TMP_DIR/lifewise-backend.zip" ] && unzip -q -o "$TMP_DIR/lifewise-backend.zip" -d "$TMP_DIR/backend"; then
    jar="$(find "$TMP_DIR/backend" -maxdepth 2 -name 'lifewise-backend.jar' -type f | head -1 || true)"
    if [ -n "$jar" ]; then
      cp -f "$jar" "$APP_DIR/lifewise-backend.jar"
    else
      echo "WARNING: backend artifact has no lifewise-backend.jar; falling back to raw JAR"
      wget -q -O "$APP_DIR/lifewise-backend.jar" "$JAR_URL"
    fi
  else
    echo "WARNING: backend artifact unavailable; falling back to raw JAR"
    wget -q -O "$APP_DIR/lifewise-backend.jar" "$JAR_URL"
  fi
}

start_and_verify_backend() {
  echo ">>> Start backend"
  bash start.sh
  sleep 8

  echo ">>> Verify backend"
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
}

if [ "$MODE" = "check" ]; then
  runtime_check
  exit 0
fi

mkdir -p "$TMP_DIR"
trap 'rm -rf "$TMP_DIR"' EXIT

if [ "$MODE" = "frontend" ]; then
  install_frontend_fast
  runtime_check || true
  echo "================================================"
  echo " Frontend deploy complete"
  echo "================================================"
  exit 0
fi

stop_legacy_service
stop_backend
backup_runtime_files
ensure_config

if [ "$MODE" = "backend" ]; then
  install_backend_fast
  chmod_scripts
else
  install_full_package
  ensure_config
fi

start_and_verify_backend
runtime_check

echo "================================================"
echo " Deploy complete"
echo " PID: $(cat backend.pid 2>/dev/null || echo unknown)"
echo " Recent log:"
tail -8 backend.log || true
echo "================================================"
