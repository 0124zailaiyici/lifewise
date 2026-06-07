#!/bin/bash
cd "$(dirname "$0")"
if [ -f backend.pid ]; then
  kill $(cat backend.pid) 2>/dev/null
  rm backend.pid
  echo "Backend stopped"
else
  echo "No PID file found"
fi
