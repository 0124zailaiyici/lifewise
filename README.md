# LifeWise 🌿

AI 生活常识助手 — 帮你解决做饭、买菜、修理、家务等日常问题。

## 快速启动（开发模式 - H2）

### 后端
```bash
cd backend
mvn spring-boot:run
```
启动后访问 http://localhost:8080

### 前端
```bash
cd frontend
npm run dev
```
启动后访问 http://localhost:5173

## MySQL 模式（生产环境）

### 前置条件
- Docker（用于启动 MySQL）
- 或自行安装 MySQL 8.0+

### 启动 MySQL
```bash
docker-compose up -d mysql
```

### 启动后端（MySQL 模式）
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

或一键启动：
```bash
start-mysql.bat
```

### MySQL 配置
数据库配置在 `backend/src/main/resources/application-mysql.yml`：
- 地址：localhost:3306
- 数据库：lifewise
- 用户名：root
- 密码：root

## 技术栈
- **后端**: Spring Boot 3.5 + JPA + H2/MySQL + JWT + DeepSeek AI
- **前端**: Vue 3 + Element Plus + Pinia + Vue Router
- **构建**: Maven + Vite