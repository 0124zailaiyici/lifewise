# LifeWise 数据库设计

## 用户表 `users`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO | 主键 |
| username | VARCHAR(50) UNIQUE | 用户名 |
| email | VARCHAR(100) UNIQUE | 邮箱 |
| password | VARCHAR(255) | 加密密码 |
| avatar | VARCHAR(255) | 头像URL |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

## 对话表 `conversations`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO | 主键 |
| user_id | BIGINT FK→users | 用户ID |
| title | VARCHAR(100) | 对话标题（自动生成） |
| scene | VARCHAR(20) | 场景：cooking/shopping/repair/housework/other |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

## 消息表 `messages`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO | 主键 |
| conversation_id | BIGINT FK→conversations | 对话ID |
| role | VARCHAR(10) | user / assistant |
| content | TEXT | 消息内容（JSON格式，含结构化数据） |
| created_at | DATETIME | 发送时间 |

## 收藏表 `favorites`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO | 主键 |
| user_id | BIGINT FK→users | 用户ID |
| message_id | BIGINT FK→messages | 收藏的消息ID |
| note | VARCHAR(200) | 用户备注 |
| created_at | DATETIME | 收藏时间 |

## 知识库表 `knowledge_base`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO | 主键 |
| scene | VARCHAR(20) | 场景 |
| question | VARCHAR(200) | 问题摘要 |
| answer | TEXT | 优质回答 |
| tags | VARCHAR(200) | 标签（逗号分隔） |
| helpful_count | INT | 有用次数 |
| created_at | DATETIME | 创建时间 |

## 索引设计

- users: (email) UNIQUE
- conversations: (user_id, created_at DESC)
- messages: (conversation_id, created_at)
- favorites: (user_id, message_id) UNIQUE
- knowledge_base: (scene, helpful_count DESC)
