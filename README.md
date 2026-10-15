# LifeWise 🌿

AI 生活常识助手 — 帮你解决做饭、买菜、修理、家务等日常问题。

## 📋 项目简介

LifeWise 是一个基于 AI 的日常生活助手应用，专为缺乏生活经验的人群设计。通过自然语言交互，提供做饭指导、买菜技巧、家庭修理、家务方法等实用生活知识。

**个人项目亮点：**
- 完整的全栈 AI 应用开发实践
- 多场景知识库 + 语义相似度匹配（Jaccard 算法）
- 多模态 AI 集成（文本 + 图片识别）
- 前端流式渲染 + 打字动画效果
- 结构化 JSON Schema 驱动的 AI 输出解析
- 完整的 JWT 认证 + 手机号注册登录

## 🧭 两种运行形态（先看这里）

仓库里同时存在两套形态，改代码前务必确认改的是哪一套：

| | 本地优先（**当前主线**，Android APK） | 服务端（历史形态，Web 部署） |
|---|---|---|
| 入口 | `frontend/src/local/` | `backend/` + `frontend/src/api/index.js` |
| 数据 | WebView / 浏览器 `localStorage` | MySQL / H2 + JPA |
| AI | 用户在「我的」页自配 OpenAI 兼容接口 | 服务端持有 Key 调 Qwen |
| 认证 | 本地校验 | JWT + 手机号 |
| 产物 | `release/LifeWise-*.apk` | `deploy/` + nginx |

`frontend/src/api/index.js` 已完全本地化，**不再请求后端**（文件内已无 axios 调用）；`backend/` 目前只服务于 Web 部署形态。

## 🚀 快速启动

### 构建 Android APK（当前主线）
```bat
build-apk.bat
```
产物：`release/LifeWise-local-debug.apk`

### Web 前端
```bash
cd frontend
npm run dev
```
启动后访问 http://localhost:5173

### 后端（仅 Web 部署形态需要）
```bash
cd backend
mvn spring-boot:run
```
> ⚠️ 后端占用 8080；启动前先确认该端口没有被其他进程占用（`netstat -ano | findstr :8080`）。

### MySQL 模式
```bash
docker-compose up -d mysql
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

### API 文档（Swagger）
启动后端后访问：http://localhost:8080/swagger-ui/index.html

## 🛠️ 技术栈

| 层级 | 技术 | 用途 |
|------|------|------|
| **后端** | Java 17 + Spring Boot 3.5 | REST API |
| **ORM** | Spring Data JPA + H2/MySQL | 数据库 |
| **安全** | JWT (jjwt) + BCrypt | 认证加密 |
| **AI** | 千问 Qwen + DeepSeek 可选 + MiMo Vision | 文本生成 + 图片识别 |
| **构建** | Maven 3.9 | 项目构建 |
| **前端** | Vue 3 + Vite | SPA 框架 |
| **UI** | Element Plus | 组件库 |
| **状态管理** | Pinia | 前端状态 |
| **路由** | Vue Router | 前端路由 |
| **容器** | Docker | MySQL 容器化 |

## ✨ 功能特性

### 核心功能
- 🤖 **AI 智能对话** — 默认使用千问 Qwen，DeepSeek 仅在手动选择并启用后调用，多场景问答
- 📷 **图片识别** — 拍照识别食材、物品（小米 MiMo Vision）
- 📚 **常识库缓存** — Jaccard 相似度匹配，相同问题秒回无需 API
- 💬 **多场景支持** — 做饭、买菜、修理、家务、健康、穿搭、社交、宠物、食谱、写作

### 页面功能
- **首页** — 场景快捷入口、最近问答
- **对话** — AI 聊天、图片上传、复制/收藏、打字动画、关联推荐
- **历史** — 按日期分组、搜索高亮、重命名、删除
- **收藏** — 收藏管理
- **搜索** — 跨对话/消息/知识库全局搜索
- **仪表盘** — 使用统计、场景分布、活跃趋势
- **常识库** — 知识管理、有用评分
- **写作助手** — 日记、周记、待办清单、学习笔记等
- **个人中心** — 手机号注册登录、密码重置

## 🏗️ 项目结构

```
LifeWise/
├── backend/                  # Spring Boot 后端（Web 部署形态）
│   ├── src/main/java/com/lifewise/
│   │   ├── config/           # JWT、CORS、场景提示词配置、异常处理
│   │   ├── controller/       # REST API
│   │   ├── dto/              # 数据传输对象
│   │   ├── entity/           # JPA 实体
│   │   ├── repository/       # 数据访问
│   │   └── service/          # 业务逻辑
│   └── src/test/             # 单元测试（50 项）
├── frontend/                 # Vue 3 前端
│   ├── src/
│   │   ├── views/            # 页面组件（Chat.vue 为聊天主界面）
│   │   ├── local/            # ★ 本地优先核心：AI 客户端 / 本地数据层 / 场景提示词
│   │   ├── api/              # 已本地化，不再请求后端
│   │   ├── stores/           # Pinia 状态
│   │   └── router/           # 路由配置（hash 模式，兼容 Capacitor）
│   ├── android/              # Capacitor 工程 + 原生插件（流式 AI / 语音）
│   ├── tests/                # 单元 / 组件 / e2e 测试
│   └── public/               # 静态资源（favicon、manifest）
├── deploy/                   # 服务器部署脚本、nginx、systemd watchdog
├── docs/                     # 项目文档（含交接文档与开发计划）
├── uploads/                  # 预置菜品图片（部署包会带上）
├── release/                  # APK 产物（不进 git）
└── build-apk.bat             # 一键构建 APK
```

## 🧹 仓库卫生

构建产物、运行时数据和本机调试残留**一律不进 git**（详见 `.gitignore` 末尾的「Repo hygiene」段）：

| 不进 git 的内容 | 原因 |
|---|---|
| `deploy/assets/`、`deploy/frontend/`、`deploy/index.html` | CI 每次 push 都会重新下载 artifact 覆盖，提交副本只会造成**过期 bundle** |
| `deploy/lifewise-backend.jar` | CI 在 `backend` job 里重新打包（见 `deploy.yml`） |
| `docs/generated-images/`、`docs/food-image-*.jpg` | 生图 QC 接触表，可重新生成 |
| `data/`、`backend/uploads/` | 运行时数据库与**用户私有上传** |
| `release/`、`*.apk`、`*.zip`、`*.jar`、`*.log` | 构建产物与日志 |

> ⚠️ `deploy/lifewise-backend.jar` 已从 git 移除，因此 `deploy-latest.sh` 里 `wget .../master/deploy/lifewise-backend.jar` 这条**后备路径会 404**；主路径（下载 CI artifact）不受影响。

## 📊 API 概览

> 下表**仅适用于服务端（Web 部署）形态**。本地优先的 APK 不经过这些接口。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/users/login | 手机号登录 |
| POST | /api/users/register | 注册 |
| POST | /api/users/forgot-password | 发送验证码 |
| POST | /api/users/reset-password | 重置密码 |
| POST | /api/chat/send | 发送消息 |
| GET | /api/chat/conversations | 对话列表 |
| GET | /api/chat/conversations/{id} | 对话详情 |
| PUT | /api/chat/conversations/{id}/rename | 重命名 |
| DELETE | /api/chat/conversations/{id} | 删除对话 |
| GET | /api/favorites | 收藏列表 |
| POST | /api/favorites | 添加收藏 |
| DELETE | /api/favorites | 取消收藏 |
| GET | /api/search?q= | 全局搜索 |
| GET | /api/stats/dashboard | 统计数据 |
| GET | /api/kb/search | 知识库搜索 |
| POST | /api/kb/{id}/helpful | 有用反馈 |
| POST | /api/upload | 上传图片 |

查看完整 API 文档：http://localhost:8080/swagger-ui/index.html

## 🔄 AI 对话流程

1. 用户提问 → 检查常识库缓存（语义相似度匹配）
2. 缓存命中 → 直接返回（毫秒级响应）
3. 缓存未命中 → 调用 AI API（默认 Qwen；图片问题使用 MiMo Vision；DeepSeek 需显式启用）
4. AI 返回 → 按场景 Schema 解析为结构化数据
5. 前端渲染 → 卡片/列表/图文等结构化展示
6. 自动缓存 → 入库常识库，下次同类问题秒回

## 🧪 测试

```bash
# 后端单元测试（50 项）
cd backend && mvn test

# 前端单元 / 组件测试
cd frontend && npm test

# 浏览器端到端验收
cd frontend && npm run test:e2e

# 十场景真实模型验收（会消耗 API 额度）
cd frontend && npm run test:ai
```

## 🔧 自定义配置

> 本地优先形态下，AI 接口地址 / Key / 模型在 App 内「我的」页面配置，存于本地 `localStorage`，不读下面的服务端配置。
> 下面这些配置项**仅对服务端（Web 部署）形态生效**。

编辑 `backend/src/main/resources/application.yml`，或通过环境变量覆盖：
- `ai.dashscope-api-key` / `AI_DASHSCOPE_KEY` — 千问 Qwen API Key（默认文本模型）
- `ai.dashscope-model` / `AI_DASHSCOPE_MODEL` — 千问模型，默认 `qwen-plus`
- `ai.deepseek-enabled` / `AI_DEEPSEEK_ENABLED` — DeepSeek 成本保护开关，默认 `false`
- `ai.api-key` / `AI_API_KEY` — DeepSeek API Key，仅在手动选择 DeepSeek 且开关启用时使用
- `ai.vision-api-url` / `AI_VISION_API_URL` — 图片识别 API 地址
- `ai.vision-api-key` / `AI_VISION_API_KEY` — 图片识别 API Key（小米 MiMo）

### AI 成本保护

- 默认文本服务商是千问 Qwen，不会在 Qwen 未配置时自动回退到 DeepSeek。
- DeepSeek 默认禁用，必须设置 `AI_DEEPSEEK_ENABLED=true` 并在前端手动选择 DeepSeek 才会调用。
- 配置 API Key 后要重新编译、重启后端，并确认运行中的进程使用的是新 JAR。
