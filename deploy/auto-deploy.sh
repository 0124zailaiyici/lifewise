#!/bin/bash
# LifeWise auto-deploy watchdog
INTERVAL=30

echo "[$(date)] LifeWise auto-deploy watchdog started (interval: ${INTERVAL}s)"

while true; do
  NEW_SHA=$(curl -s "https://api.github.com/repos/0124zailaiyici/lifewise/commits/master" 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin)['sha'][:7])" 2>/dev/null)
  
  if [ -n "$NEW_SHA" ] && [ "$NEW_SHA" != "$LAST_SHA" ]; then
    echo "[$(date)] New commit detected: $NEW_SHA"
    cd /opt/lifewise && MODE=frontend bash deploy-latest.sh
    echo "[$(date)] Deploy complete: $NEW_SHA"
    LAST_SHA="$NEW_SHA"
  fi
  
  sleep $INTERVAL
done