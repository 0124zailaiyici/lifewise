# LifeWise 部署 / 扣费安全检查清单

目标：不保存真实 Key，只确保服务器不会因为旧进程、旧 systemd 或端口混乱导致误扣费。

## 服务器配置位置

服务器真实配置放在：

```bash
/opt/lifewise/application-cloud.properties
```

必要项：

```properties
server.port=8082
ai.deepseek-enabled=false
ai.dashscope-api-key=你的 DashScope Key
app.upload-dir=/opt/lifewise/uploads
```

不要把真实 Key 提交到 Git。

## 每次部署前检查

```bash
cd /opt/lifewise
bash check-runtime.sh
```

重点看：

- Qwen DashScope key: configured
- DeepSeek disabled
- Exactly 1 LifeWise backend process
- No legacy `/app/lifewise` backend process
- legacy `lifewise.service` is not enabled/active
- port `8082` is listening
- No Java backend on `8080`

## 标准部署

```bash
cd /opt/lifewise
bash deploy-latest.sh
```

部署脚本会：

1. 停掉并禁用旧 `lifewise.service`
2. 停掉所有 `lifewise-backend.jar` 进程
3. 备份数据库和配置
4. 下载最新包
5. 确保 `ai.deepseek-enabled=false`
6. 启动新后端
7. 运行安全检查

## 如果发现旧服务还在

```bash
systemctl stop lifewise
systemctl disable lifewise
pkill -f lifewise-backend.jar
```

然后重新：

```bash
cd /opt/lifewise
bash start.sh
bash check-runtime.sh
```

## 本地开发

本地不要把 Key 写进仓库。可以复制：

```powershell
Copy-Item start-local.example.ps1 start-local.ps1
```

然后只在 `start-local.ps1` 填自己的 Key。`start-local.ps1` 不要提交。


## ??????

??????? UI?????????????

```bash
cd /opt/lifewise
MODE=frontend bash deploy-latest.sh
```

???

- ????? artifact
- ?????
- ??? Java
- ???? hashed assets?????/??????
- ?? UI????????????

???????????

```bash
cd /opt/lifewise
MODE=backend bash deploy-latest.sh
```

????????/???????????

```bash
cd /opt/lifewise
MODE=check bash deploy-latest.sh
```

????????

```bash
cd /opt/lifewise
MODE=full bash deploy-latest.sh
```

???? `MODE` ??? `MODE=full`?
