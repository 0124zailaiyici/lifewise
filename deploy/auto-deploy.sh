#!/bin/bash
INTERVAL=30
APP_DIR="/opt/lifewise"
LOG_FILE="$APP_DIR/auto-deploy.log"
echo "[$(date)] ========================================" >> "$LOG_FILE"
echo "[$(date)] LifeWise auto-deploy watchdog started" >> "$LOG_FILE"
echo "[$(date)] ========================================" >> "$LOG_FILE"
LAST_SHA=""
while true; do
  NEW_SHA=$(curl -s "https://api.github.com/repos/0124zailaiyici/lifewise/commits/master" 2>/dev/null | python3 -c "import sys,json;data=json.load(sys.stdin);print(data['sha'][:7])" 2>/dev/null)
  if [ -n "$NEW_SHA" ] && [ "$NEW_SHA" != "$LAST_SHA" ]; then
    if [ -n "$LAST_SHA" ]; then
      echo "[$(date)] New commit: $NEW_SHA" >> "$LOG_FILE"
      cd "$APP_DIR" && MODE=frontend bash deploy-latest.sh >> "$LOG_FILE" 2>&1
      echo "[$(date)] Deploy done: $NEW_SHA" >> "$LOG_FILE"
    fi
    LAST_SHA="$NEW_SHA"
  fi
  sleep $INTERVAL
done