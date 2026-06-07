#!/bin/bash
# LifeWise 一键更新脚本
# 从 GitHub Actions 下载最新构建包并部署

set -e

echo "================================================"
echo "  LifeWise 一键更新"
echo "================================================"

cd /opt/lifewise

# 1. 下载最新构建包
echo ">>> 下载最新构建包..."
wget -q -O lifewise-new.zip "https://nightly.link/0124zailaiyici/lifewise/workflows/deploy/master/lifewise-deploy.zip" || {
    echo "下载失败，改用 GitHub API..."
    ARTIFACT_ID=$(curl -s "https://api.github.com/repos/0124zailaiyici/lifewise/actions/artifacts?per_page=1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
    curl -sL -o lifewise-new.zip "https://api.github.com/repos/0124zailaiyici/lifewise/actions/artifacts/$ARTIFACT_ID/zip" -H "Authorization: Bearer"
}

# 2. 停掉旧后端
echo ">>> 停止旧后端..."
if [ -f backend.pid ]; then
    kill $(cat backend.pid) 2>/dev/null || true
    rm -f backend.pid
fi
sleep 2

# 3. 备份数据库
echo ">>> 备份数据库..."
mkdir -p data
if [ -f data/lifewise.mv.db ]; then
    cp data/lifewise.mv.db data/lifewise.mv.db.bak
    echo "    已备份到 data/lifewise.mv.db.bak"
fi

# 4. 解压覆盖（保留配置和数据）
echo ">>> 解压更新文件..."
unzip -q -o lifewise-new.zip
rm -f lifewise-new.zip

# 5. 恢复配置（如存在）
if [ -f application-cloud.properties ]; then
    cp application-cloud.properties application-cloud.properties
fi

chmod +x start.sh stop.sh

# 6. 启动新后端
echo ">>> 启动新后端..."
bash start.sh
sleep 3

# 7. 检查是否启动成功
if tail -5 backend.log | grep -q "Started"; then
    echo ""
    echo "================================================"
    echo "  ✅ 更新成功！"
    echo "  后端 PID: $(cat backend.pid)"
    echo "  访问地址: http://47.106.126.50:8081"
    echo "================================================"
else
    echo ""
    echo "  ⚠️ 后端可能未正常启动，查看日志:"
    echo "  tail -30 /opt/lifewise/backend.log"
    echo "================================================"
fi
