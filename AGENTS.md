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

### 12. DeepSeek 偷跑烧钱：Qwen 未配 Key 时静默回退 (2026-06-08)
**现象**：明明在设置里选了千问 Qwen，后台却一直在用 DeepSeek，一天烧十几块

**根本原因**：
1. 后端 AiServiceImpl.java 中，旧方法 chat(String, String, Long, Long, String) 硬编码 
eq.setProvider("deepseek")
2. **最关键**：callAI() 方法中，当 provider = "qwen" 但 dashscopeApiKey 为空时，会**静默回退到 DeepSeek**，用户完全不知道
3. 服务器 start.sh 没有导出 AI_DASHSCOPE_KEY 环境变量，导致 DashScope Key 虽然写入了 pplication-cloud.properties，但后端读取不到

**教训**：
- 任何时候都不要在代码中硬编码 provider！新旧方法都要统一默认值
- AI 服务降级时绝对不能静默切换到更贵的服务商
- 检查整个调用链路：前端发送 → 后端路由 → 环境变量读取 → 实际 API 调用
- 服务器上的 start.sh 必须同步更新以支持新配置项

**修复措施**：
- 旧方法 
eq.setProvider("deepseek") → 
eq.setProvider("qwen")
- Qwen Key 缺失时返回清晰错误提示，而不是回退到 DeepSeek
- deploy/start.sh 添加 i.dashscope-api-key → AI_DASHSCOPE_KEY 的导出

**验证方法**：
1. 检查后端日志是否有 "DashScope API key not configured for Qwen" 警告
2. 检查服务器环境变量 echo \ 是否有值
3. 在 DeepSeek 控制台查看是否还有请求产生

### 14. 改了代码必须验证"新代码是否真的在跑" (2026-06-08)
**现象**：改了 3 层 DeepSeek 默认值，但 DeepSeek 依然在疯狂扣费

**根本原因**：
1. 代码改了，但 Maven 没有重新编译 → JAR 还是旧的
2. JAR 重新编译了，但 Java 进程没重启 → 跑的依然是旧 JAR
3. 本地测试通过后，服务器没部署 → 手机访问的还是旧代码
4. 改代码时只关注了"代码对不对"，没验证"运行的是哪份代码"

**教训**：
- 改代码流程必须是：**改源码 → 编译 → 停旧进程 → 启动新进程 → 验证新代码在运行**
- 每次改完 AI/网络相关代码，一定要从源码到运行全链路检查
- 检查运行中的进程启动参数：wmic process where "name='java.exe'" get commandline (Windows) 或 ps aux | grep java (Linux)
- 部署服务器后必须实际发请求测试，不能用"好像可以了"代替验证
- 可以用 git log --oneline -1 结合 JAR 编译时间确认版本匹配

**验证新代码在运行的快速方法**：
- Windows: 
etstat -ano | findstr :8080 看 PID，再用 wmic process where "processid=PID" get commandline 确认启动参数
- Linux: ps aux | grep java | grep lifewise 看启动命令和 JAR 路径
- 后端日志第一条会显示启动时间，确认是最新的

**简单记忆**：改完代码四步走 → 编译 → 重启 → 验证 → 部署

### 15. 服务器有隐藏的 systemd 服务在后台跑旧代码 (2026-06-08)
**现象**：明明停掉了 /opt/lifewise/ 的后端，DeepSeek 依然在疯狂扣费

**根本原因**：
- 服务器上有一个 lifewise.service 系统服务（/etc/systemd/system/lifewise.service）
- 它在 /app/lifewise/ 目录跑另一个旧 JAR，完全独立于手动管理的 /opt/lifewise/
- 配置了 Restart=always，kill 掉后 5 秒自动复活
- 硬编码了 DeepSeek API Key 和环境变量，没有 Qwen 配置
- 跑在 8080 端口（而主后端跑在 8082），所以 Nginx 的 8081 端口同时代理两个后端

**教训**：
- 停进程不能只靠 kill，要检查是否有 systemd 服务在自动重启
- 命令：systemctl list-units --type=service | grep lifewise
- 查看服务详情：systemctl status lifewise
- 彻底停用：systemctl stop lifewise && systemctl disable lifewise
- 部署时两个目录（/opt/lifewise/ 和 /app/lifewise/）都要检查

### 16. API Key 泄露导致持续扣费 (2026-06-08)
**现象**：后端全停了 DeepSeek 账单还在涨

**根本原因**：
- API Key 多次在聊天记录、配置文件、GitHub 中明文暴露
- 恶意爬虫扫到后盗用 key 调接口
- DeepSeek 按 token 计费，被盗用后账单持续上涨

**教训**：
- API Key 绝对不能出现在聊天记录、代码仓库、配置文件等任何可能被爬虫扫到的地方
- 服务器上的 key 要放在环境变量中，不要硬编码在文件里
- 怀疑泄露后立即在平台删除/重置 key
- 给 key 设置用量上限和额度预警
- 考虑用千问（DashScope）作为默认模型，其 key 更便宜，被盗损失也更小

**最终改造结果**：
- ✅ 默认 AI 模型改为千问 Qwen（DashScope）
- ✅ DeepSeek 仅保留为可手动切换的选项
- ✅ 本地和服务器都使用新 JAR，旧 systemd 服务已禁用
- ✅ 所有明文暴露的 DeepSeek Key 已删除

### 17. 认证白名单不能包含需要 userId 的接口 (2026-06-08)
**现象**：常识库页面看不到数据，接口返回 500，提示缺少 `userId`

**根本原因**：
- `JwtAuthFilter` 把 `/api/kb` 放进了免登录白名单
- 请求被放行后没有解析 JWT，也就没有写入 `requestAttribute userId`
- `KnowledgeBaseController.search()` 又要求 `@RequestAttribute Long userId`，最终触发 500

**教训**：
- 只要接口需要用户隔离或 `userId`，就不能放进认证白名单
- 修改认证过滤器后必须至少测一次登录态接口
- 看到 `Missing request attribute 'userId'`，优先检查 JWT 过滤器是否提前放行
- 常识库、收藏、历史记录这类用户私有数据接口必须始终走认证

### 18. Nginx 413 上传限制要和后端 multipart 同步 (2026-06-09)
**现象**：上传图片时页面返回 `413 Request Entity Too Large`，错误页显示 `nginx`。

**根本原因**：
- Spring Boot 后端允许 `max-file-size=10MB`、`max-request-size=20MB`
- 前端也允许选择 10MB 内图片
- 但实际生效的 Nginx 配置没有设置 `client_max_body_size`，Nginx 默认通常约 1MB
- 请求在进入后端前就被 Nginx 拦截，所以后端日志里可能完全看不到请求

**教训**：
- 上传大小限制必须三层一致：前端限制、Spring multipart、Nginx `client_max_body_size`
- 看到错误页明确写 `nginx`，先查 Nginx 配置，不要只改后端
- 改仓库模板不等于服务器已生效，必须确认 `/etc/nginx/sites-enabled/lifewise` 或 `nginx -T` 输出
- 修改 Nginx 后必须执行 `nginx -t && systemctl reload nginx`
- 避免用递归搜索扫 `node_modules`、`dist`、数据库和大日志，否则也可能触发工具/代理层 413

**验证方法**：
1. `nginx -T | grep -n "client_max_body_size"` 确认生效配置包含 `20m` 或更高
2. `bash /opt/lifewise/check-runtime.sh` 检查运行时配置
3. 实际上传 1MB+ 图片确认不再返回 413

