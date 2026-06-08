# LifeWise 开发记录 & 注意事项

## ⚠️ 记住的教训

### 导出菜单不显示  (2026-06-07)
**现象**：📥 按钮点击后直接下载 TXT，没有弹出 TXT/图片/PDF 菜单

**原因**：
1. 代码改了但 `dist/` 没重新构建，服务器跑的还是旧版本
2. GitHub Actions 构建全失败，nightly.link 永远提供旧产物
3. 本地 Vite 开发服务器频繁崩溃导致无法测试

**解决方案**：
- 建一个独立的 `deploy-temp` 分支，把 `frontend/dist/` 文件直接推上去
- 服务器通过 `wget` 从 raw.githubusercontent.com 拉取

### API 消耗控制
- ✅ 生图默认关闭（设置页可手动开启）
- ✅ 上下文轮数改为 5 轮（减少输入 token）
- ✅ 默认 AI 模型改为千问 Qwen（比 DeepSeek 便宜）
- ⚠️ DeepSeek 按 token 计费，测试期间注意用量

### 部署注意事项
- 服务器 SSH 端口 22 可能不通，备好阿里云 Web 终端
- Nginx 配置在 `/etc/nginx/sites-enabled/lifewise`
- 前端文件在 `/opt/lifewise/frontend/`
- 后端配置文件在 `/opt/lifewise/application-cloud.properties`
- 两个后端进程同时跑在 8082 端口时要 kill 掉旧的

### GitHub Actions
- 最近几次构建（#14~#18）后端 Maven 编译全失败
- 构建失败不影响本地 `vite build`
- 可以直接推 `dist/` 到独立分支来绕过
