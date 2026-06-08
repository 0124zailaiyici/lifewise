#!/bin/bash
# LifeWise one-command server update wrapper.
# Usage:
#   bash update.sh                 # full deploy (default)
#   MODE=frontend bash update.sh   # frontend only, no backend restart
#   MODE=backend bash update.sh    # backend only, restart backend
#   MODE=check bash update.sh      # runtime safety check only

set -euo pipefail

APP_DIR="${APP_DIR:-/opt/lifewise}"
cd "$APP_DIR"

if [ ! -f deploy-latest.sh ]; then
  echo "ERROR: deploy-latest.sh not found in $APP_DIR"
  exit 1
fi

bash deploy-latest.sh
