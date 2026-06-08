# LifeWise 开发记录 & 注意事项

## ⚠️ 教训与注意事项

### 1. 后端启动时要检查端口占用 (2026-06-07)
- 服务器上有两个后端进程同时在 8082 端口，旧的没 kill 掉会导致新进程启动异常
- 每次部署前先 `kill -9 $(pgrep -f "lifewise-backend")` 清理旧进程

### 2. 用户数据会随数据库重置而丢失 (2026-06-07)
- 每次 `data/lifewise.mv.db` 被覆盖或重建，所有用户、对话、常识库都会丢失
- 部署新 JAR 前注意备份数据库文件

### 3. 常识库按用户隔离 (2026-06-07)
- 之前所有用户的常识库混在一起，修改后每个用户只能看到自己的知识和缓存
- 用户知识不共享，避免浪费 token

### 4. 导出菜单不显示 (2026-06-07)
**现象**：📥 按钮点击后直接下载 TXT，没有弹出 TXT/图片/PDF 菜单

**原因**：
1. 代码改了但 `dist/` 没重新构建，服务器跑的还是旧版本
2. GitHub Actions 构建全失败，nightly.link 永远提供旧产物
3. 本地 Vite 开发服务器频繁崩溃导致无法测试

**解决方案**：
- 建一个独立的 `deploy-temp` 分支，把 `frontend/dist/` 文件直接推上去
- 服务器通过 `wget` 从 raw.githubusercontent.com 拉取

### 5. 缓存机制导致答非所问 (2026-06-06)
- 之前对话缓存命中旧答案，明明是新问题却返回旧回答
- 修复后每个页面都独立处理缓存

### 6. 前端/后端不同步部署 (多次)
- 修改前端后必须重新 `vite build`，再部署 dist 到服务器
- 本地 Vite 开发服务器跟服务器上的 nginx 是不同的环境
- 测试必须在两个环境都验证

### 7. SVG 步骤图场景不对应 (2026-06-06)
- 所有场景都用做饭助手的图标
- 修复后每个场景（做饭/穿搭/修理等）都有对应的 SVG 插画

### 8. API 消耗控制 (2026-06-07)
- ✅ 生图默认关闭（设置页可手动开启）
- ✅ 上下文轮数改为 5 轮（减少输入 token）
- ✅ 默认 AI 模型改为千问 Qwen（比 DeepSeek 便宜）
- ⚠️ DeepSeek 按 token 计费，测试期间注意用量
- ⚠️ 一次生图调用消耗不小，非必要不开

### 9. 部署注意事项
- 服务器 SSH 端口 22 可能不通，备好阿里云 Web 终端
- Nginx 配置在 `/etc/nginx/sites-enabled/lifewise`
- 前端文件在 `/opt/lifewise/frontend/`
- 后端配置文件在 `/opt/lifewise/application-cloud.properties`

### 10. GitHub Actions 构建失败
- 最近多次构建（#14~#18）后端 Maven 编译全失败
- 构建失败不影响本地 `vite build`
- 可以直接推 `dist/` 到独立分支来绕过 CI

### 11. 编码问题导致编译失败
- Chat.vue 中出现乱码字符（如 `��`、`data.Ʒ��`）导致 Vite 编译错误
- 修改代码后务必本地 `npm run build` 验证能通过再提交

### 12. GitHub Actions 前后端分离 (2026-06-08)
- 之前前后端在同一个 job，后端编译失败导致整个构建作废，前端也出不来
- 改为独立 job 后，前端构建不再依赖后端编译结果
- 前端产物可通过 nightly.link 获取：
  - 完整包: \https://nightly.link/0124zailaiyici/lifewise/workflows/deploy/master/lifewise-deploy.zip\
  - 仅前端: \https://nightly.link/0124zailaiyici/lifewise/workflows/deploy/master/lifewise-frontend.zip\

### 13. 服务器 Node.js 升级 (2026-06-08)
- 阿里云服务器 Ubuntu 自带 Node v12，Vite 8 需要 Node 18+
- 用 nodesource 的 setup_20.x 脚本升级到 v20
- 升级时如果报 \	rying to overwrite ... libnode-dev\ 错误，先 \pt remove -y libnode-dev\ 再装
