#!/bin/bash
# LifeWise 服务器初始化脚本 (Alibaba Cloud Linux / CentOS)
echo "=== 安装 Java 17 ==="
if ! command -v java &> /dev/null; then
    yum install -y java-17-openjdk-headless
fi
java -version

echo "=== 安装 Nginx ==="
if ! command -v nginx &> /dev/null; then
    yum install -y nginx
fi

echo "=== 创建部署目录 ==="
mkdir -p /opt/lifewise
mkdir -p /opt/lifewise/frontend
mkdir -p /opt/lifewise/exports
mkdir -p /opt/lifewise/uploads

echo "=== 配置 Nginx ==="
cat > /etc/nginx/conf.d/lifewise.conf << 'NGINX'
server {
    listen 8081;
    server_name _;
    client_max_body_size 20M;

    location / {
        root /opt/lifewise/frontend;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8082;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
    }

    location /uploads/ {
        alias /opt/lifewise/uploads/;
    }
}
NGINX

echo "=== 重启 Nginx ==="
nginx -t && systemctl restart nginx

echo ""
echo "=== 初始化完成 ==="
echo "请将 frontend/dist 文件放入 /opt/lifewise/frontend/"
echo "将 lifewise-backend.jar 放入 /opt/lifewise/"
echo "Backend start: cd /opt/lifewise && bash start.sh"
echo "访问地址: http://SERVER_IP:8081"
