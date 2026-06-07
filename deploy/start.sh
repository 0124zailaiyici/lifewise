#!/bin/bash
cd "$(dirname "$0")"
echo "Starting LifeWise backend..."
nohup java -jar lifewise-backend.jar --spring.profiles.active=cloud > backend.log 2>&1 &
echo $! > backend.pid
echo "Backend PID: $(cat backend.pid)"
