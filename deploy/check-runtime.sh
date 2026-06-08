#!/bin/bash
# LifeWise runtime safety check. No external AI calls; no secrets are printed.
# Run on server: cd /opt/lifewise && bash check-runtime.sh

set -euo pipefail

APP_DIR="${APP_DIR:-/opt/lifewise}"
PORT="${PORT:-8082}"
CONFIG_FILE="${CONFIG_FILE:-$APP_DIR/application-cloud.properties}"

echo "================================================"
echo " LifeWise runtime safety check"
echo " App dir: $APP_DIR"
echo " Expected port: $PORT"
echo "================================================"

fail=0
warn=0

ok() { echo "[OK] $*"; }
warning() { echo "[WARN] $*"; warn=$((warn + 1)); }
error() { echo "[ERROR] $*"; fail=$((fail + 1)); }

prop() {
  local key="$1"
  if [ -f "$CONFIG_FILE" ]; then
    grep -E "^[[:space:]]*$key[[:space:]]*=" "$CONFIG_FILE" | tail -1 | sed 's/^[^=]*=//' | xargs || true
  fi
}

has_prop_value() {
  local key="$1"
  local value
  value="$(prop "$key")"
  [ -n "$value" ]
}

masked_state() {
  local key="$1"
  if has_prop_value "$key"; then
    echo "configured"
  else
    echo "missing"
  fi
}

echo ">>> Config file"
if [ -f "$CONFIG_FILE" ]; then
  ok "config exists: $CONFIG_FILE"
else
  error "config missing: $CONFIG_FILE"
fi

echo ">>> Provider config"
qwen_state="$(masked_state ai.dashscope-api-key)"
if [ "$qwen_state" = "configured" ]; then
  ok "Qwen DashScope key: configured (masked)"
else
  warning "Qwen DashScope key missing: ai.dashscope-api-key"
fi

deepseek_enabled="$(prop ai.deepseek-enabled)"
deepseek_enabled="${deepseek_enabled:-false}"
if [ "$deepseek_enabled" = "true" ]; then
  error "DeepSeek is enabled. Set ai.deepseek-enabled=false unless you intentionally want paid DeepSeek calls."
else
  ok "DeepSeek disabled: ai.deepseek-enabled=${deepseek_enabled}"
fi

conf_port="$(prop server.port)"
if [ -n "$conf_port" ] && [ "$conf_port" != "$PORT" ]; then
  warning "server.port in config is $conf_port, expected $PORT"
else
  ok "server.port: ${conf_port:-$PORT}"
fi

echo ">>> Process check"
running="$(pgrep -af 'lifewise-backend.jar' || true)"
if [ -n "$running" ]; then
  echo "$running"
  count="$(echo "$running" | wc -l | xargs)"
  if [ "$count" != "1" ]; then
    error "Expected exactly 1 LifeWise backend process, found $count"
  else
    ok "Exactly 1 LifeWise backend process"
  fi
else
  warning "No LifeWise backend process found"
fi

if echo "$running" | grep -q '/app/lifewise/lifewise-backend.jar'; then
  error "Legacy /app/lifewise backend is running. Stop/disable old systemd service."
else
  ok "No legacy /app/lifewise backend process"
fi

if command -v systemctl >/dev/null 2>&1 && systemctl list-unit-files 2>/dev/null | grep -q '^lifewise.service'; then
  svc_state="$(systemctl is-enabled lifewise 2>/dev/null || true)"
  active_state="$(systemctl is-active lifewise 2>/dev/null || true)"
  if [ "$svc_state" = "enabled" ] || [ "$active_state" = "active" ]; then
    error "legacy lifewise.service state: enabled=$svc_state active=$active_state"
  else
    ok "legacy lifewise.service is not enabled/active"
  fi
else
  ok "legacy lifewise.service not installed"
fi

echo ">>> Port check"
if command -v ss >/dev/null 2>&1; then
  port_lines="$(ss -tlnp | grep ":$PORT" || true)"
  if [ -n "$port_lines" ]; then
    echo "$port_lines"
    ok "port $PORT is listening"
  else
    warning "port $PORT is not listening"
  fi
  if ss -tlnp | grep ':8080' | grep -qi 'java'; then
    error "Java backend appears to be listening on 8080; possible old backend"
  else
    ok "No Java backend on 8080"
  fi
else
  warning "ss command not available; skipped port check"
fi

echo "================================================"
if [ "$fail" -gt 0 ]; then
  echo "Result: FAILED ($fail error(s), $warn warning(s))"
  exit 1
fi
if [ "$warn" -gt 0 ]; then
  echo "Result: OK with warnings ($warn warning(s))"
else
  echo "Result: OK"
fi
exit 0
