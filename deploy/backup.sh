#!/bin/bash
# Backup LifeWise H2 data and server config.

set -euo pipefail

APP_DIR="/opt/lifewise"
BACKUP_DIR="$APP_DIR/backups"
TS="$(date +%Y%m%d_%H%M%S)"

cd "$APP_DIR"
mkdir -p "$BACKUP_DIR"

echo "================================================"
echo "  LifeWise backup: $TS"
echo "================================================"

if [ -f data/lifewise.mv.db ]; then
  cp data/lifewise.mv.db "$BACKUP_DIR/lifewise-$TS.mv.db"
  echo "Data backup: $BACKUP_DIR/lifewise-$TS.mv.db"
else
  echo "WARNING: data/lifewise.mv.db not found"
fi

if [ -f application-cloud.properties ]; then
  cp application-cloud.properties "$BACKUP_DIR/application-cloud-$TS.properties"
  echo "Config backup: $BACKUP_DIR/application-cloud-$TS.properties"
fi

find "$BACKUP_DIR" -type f -name 'lifewise-*.mv.db' -mtime +14 -delete
find "$BACKUP_DIR" -type f -name 'application-cloud-*.properties' -mtime +14 -delete

echo "Backup complete"
