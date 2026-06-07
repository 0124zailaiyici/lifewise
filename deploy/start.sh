#!/bin/bash
cd "$(dirname "$0")"

# 读取外部配置文件（如果存在）
API_KEY=""
MODEL=""
PORT="8082"
if [ -f application-cloud.properties ]; then
    while IFS="=" read -r key value; do
        case "$key" in
            ai.api-key) API_KEY="$value" ;;
            ai.model) MODEL="$value" ;;
            server.port) PORT="$value" ;;
        esac
    done < application-cloud.properties
fi

# 构建命令行参数
ARGS="--spring.profiles.active=cloud --server.port=$PORT"
if [ -n "$API_KEY" ]; then
    ARGS="$ARGS --ai.api-key=$API_KEY"
fi
if [ -n "$MODEL" ]; then
    ARGS="$ARGS --ai.model=$MODEL"
fi

echo "Starting LifeWise backend (port: $PORT)..."
nohup java -jar lifewise-backend.jar $ARGS > backend.log 2>&1 &
echo $! > backend.pid
echo "Backend PID: $(cat backend.pid)"
