#!/bin/bash
cd "$(dirname "$0")"

if [ -f backend.pid ]; then
  kill "$(cat backend.pid)" 2>/dev/null || true
  rm backend.pid
  echo "Backend stopped"
else
  echo "No PID file found"
fi

# Clean up only LifeWise processes started from /opt/lifewise.
# Do not touch other Java projects on the same server.
pkill -f "java -jar lifewise-backend.jar" 2>/dev/null || true

if systemctl is-active --quiet lifewise 2>/dev/null; then
  echo "Stopping legacy systemd lifewise.service..."
  systemctl stop lifewise 2>/dev/null || true
fi
