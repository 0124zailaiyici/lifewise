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

## 🚀 快速启动

### 后端
```bash
cd backend
mvn spring-boot:run
```

### 前端
```bash
cd frontend
npm run dev
```

启动后访问 http://localhost:5173

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
| **AI** | DeepSeek V4 + MiMo Vision | 文本生成 + 图片识别 |
| **构建** | Maven 3.9 | 项目构建 |
| **前端** | Vue 3 + Vite | SPA 框架 |
| **UI** | Element Plus | 组件库 |
| **状态管理** | Pinia | 前端状态 |
| **路由** | Vue Router | 前端路由 |
| **容器** | Docker | MySQL 容器化 |

## ✨ 功能特性

### 核心功能
- 🤖 **AI 智能对话** — 基于 DeepSeek，多场景问答
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
├── backend/                  # Spring Boot 后端
│   ├── src/main/java/
│   │   └── com/lifewise/
│   │       ├── config/       # JWT、CORS、异常处理
│   │       ├── controller/   # REST API
│   │       ├── dto/          # 数据传输对象
│   │       ├── entity/       # JPA 实体
│   │       ├── repository/   # 数据访问
│   │       ├── service/      # 业务逻辑
│   │       └── common/       # 公共类
│   └── src/test/             # 单元测试
├── frontend/                 # Vue 3 前端
│   └── src/
│       ├── views/            # 页面组件
│       ├── api/              # API 封装
│       ├── stores/           # Pinia 状态
│       └── router/           # 路由配置
└── docs/                     # 项目文档
```

## 📊 API 概览

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
3. 缓存未命中 → 调用 AI API（DeepSeek/MiMo）
4. AI 返回 → 按场景 Schema 解析为结构化数据
5. 前端渲染 → 卡片/列表/图文等结构化展示
6. 自动缓存 → 入库常识库，下次同类问题秒回

## 🧪 测试

```bash
cd backend
mvn test
```

## 🔧 自定义配置

编辑 `backend/src/main/resources/application.yml`：
- `ai.api-key` — DeepSeek API Key
- `ai.model` — AI 模型
- `ai.vision-api-key` — 图片识别 API Key（小米 MiMo）
