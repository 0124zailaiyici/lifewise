# LifeWise 🌿 — AI 生活常识助手

> 一个面向年轻人的 AI 生活常识助手，帮你解决做饭、买菜、修理、家务等日常生活中的 "不知道怎么办"。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17 + Spring Boot 3.5 + JPA |
| 前端 | Vue 3 + Vite + Element Plus |
| 数据库 | H2 (开发) / MySQL (生产) |
| 缓存 | Redis |
| AI | DeepSeek API / OpenAI 兼容接口 |

## 功能

- 🍳 **做饭助手** — 菜谱查询、新手友好的步骤说明
- 🛒 **买菜指南** — 水果蔬菜挑选技巧、季节性食材推荐
- 🔧 **修理指南** — 家庭常见问题排查与维修步骤
- 🏠 **家务技巧** — 清洁、收纳、污渍处理等实用技巧
- ⭐ **收藏夹** — 保存有用的回答，方便回看
- 📋 **历史记录** — 查看过往所有对话

## 快速启动

### 1. 后端

```bash
cd backend

# 配置 AI API Key（可选，不配的话使用模拟回复）
# 支持 DeepSeek / OpenAI / 通义千问
set AI_API_KEY=你的API_KEY

# 启动
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`

### 2. 前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`

### 3. 访问

打开浏览器访问 `http://localhost:5173`，注册账号即可使用。

## 项目结构

```
LifeWise/
├── backend/                  # Spring Boot 后端
│   └── src/main/java/com/lifewise/
│       ├── controller/       # API 控制器
│       ├── service/          # 业务逻辑
│       ├── repository/       # 数据访问
│       ├── entity/           # 数据实体
│       ├── dto/              # 数据传输对象
│       ├── config/           # 配置（CORS、异常处理）
│       └── common/           # 公共类（ApiResponse、枚举）
├── frontend/                 # Vue 3 前端
│   └── src/
│       ├── views/            # 页面组件
│       ├── api/              # API 封装
│       ├── router/           # 路由配置
│       └── stores/           # 状态管理
└── docs/                     # 设计文档
    ├── prototype.html        # 页面原型
    └── database-design.md    # 数据库设计
```

## 设计文档

- [数据库设计](docs/database-design.md)
- [页面原型](docs/prototype.html)（直接浏览器打开）

## 环境要求

- Java 17+
- Node.js 18+
- Maven 3.6+
