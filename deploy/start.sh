#!/bin/bash
cd "$(dirname "$0")"

# 读取外部配置文件（如果存在）
if [ -f application-cloud.properties ]; then
    while IFS="=" read -r key value; do
        # 去除两端空格
        key=$(echo "$key" | xargs)
        value=$(echo "$value" | xargs)
        case "$key" in
            ai.api-key) export AI_API_KEY="$value" ;;
            ai.model) export AI_MODEL="$value" ;;
            ai.api-url) export AI_API_URL="$value" ;;
            ai.vision-api-key) export AI_VISION_API_KEY="$value" ;;
            ai.vision-model) export AI_VISION_MODEL="$value" ;;
            app.vision-enabled) export APP_VISION_ENABLED="$value" ;;
            app.upload-dir) UPLOAD_DIR="$value" ;;
            server.port) PORT="$value" ;;
            gl-image.api-key) export GL_IMAGE_API_KEY="$value" ;;
            gl-image.api-url) export GL_IMAGE_API_URL="$value" ;;
        esac
    done < application-cloud.properties
fi

PORT="${PORT:-8082}"
UPLOAD_DIR="${UPLOAD_DIR:-/opt/lifewise/uploads}"

echo "Starting LifeWise backend (port: $PORT)..."
echo "AI_API_KEY: ${AI_API_KEY:0:10}... (configured: $([ -n "$AI_API_KEY" ] && echo yes || echo no))"

nohup java -jar lifewise-backend.jar \
    --spring.profiles.active=cloud \
    --server.port=$PORT \
    --app.upload-dir=$UPLOAD_DIR > backend.log 2>&1 &
echo $! > backend.pid
echo "Backend PID: $(cat backend.pid)"
