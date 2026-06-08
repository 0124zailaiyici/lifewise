#!/bin/bash
# Install a daily LifeWise backup cron job.

set -euo pipefail

APP_DIR="/opt/lifewise"
CRON_LINE="15 3 * * * cd $APP_DIR && bash backup.sh >> $APP_DIR/backups/backup.log 2>&1"

cd "$APP_DIR"
chmod +x backup.sh
mkdir -p backups

tmp="$(mktemp)"
crontab -l 2>/dev/null | grep -v 'bash backup.sh' > "$tmp" || true
echo "$CRON_LINE" >> "$tmp"
crontab "$tmp"
rm -f "$tmp"

echo "Installed daily backup cron:"
echo "$CRON_LINE"
