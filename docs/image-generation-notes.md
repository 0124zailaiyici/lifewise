# LifeWise 生图/图片链路说明

## 先分清 3 类“图片”
1. 用户上传图片问答：前端 `/api/upload` 上传 -> 后端保存到 `app.upload-dir` -> 聊天 `/api/chat/send` 带 `imageUrl` -> `AiServiceImpl` 转 base64 调 `ai.vision-*`。
2. 菜品成品图展示：前端 Chat.vue 检测菜名 -> 自动调用 `/api/food-image/lookup`，只查浏览器 localStorage、DB 缓存、本地 `uploads/food-images/v1/index.json`，不扣费。
3. 外部付费生图：后端 `/api/food-image/generate`，先查缓存/本地预置，未命中才调用 `gl-image` 外部接口。当前前端正常聊天不会自动调用 generate。

## 关键文件
- 前端 API：`frontend/src/api/index.js`
- 聊天页：`frontend/src/views/Chat.vue`
- 设置页：`frontend/src/views/Profile.vue`
- 菜品图后端：`backend/src/main/java/com/lifewise/controller/FoodImageController.java`
- 上传后端：`backend/src/main/java/com/lifewise/controller/UploadController.java`
- 视觉问答：`backend/src/main/java/com/lifewise/service/impl/AiServiceImpl.java`
- 静态上传映射：`backend/src/main/java/com/lifewise/config/WebConfig.java`
- 菜品图缓存表：`FoodImageCache.java` + `FoodImageCacheRepository.java`

## 配置项
- 上传目录：`app.upload-dir`
- 上传限制：Spring `spring.servlet.multipart.max-file-size=10MB`, `max-request-size=20MB`; Nginx `client_max_body_size 20m`+
- 视觉问答：`AI_VISION_API_URL`, `AI_VISION_API_KEY`, `AI_VISION_MODEL`, `APP_VISION_ENABLED`
- 菜品付费生图：`GL_IMAGE_API_KEY`, `GL_IMAGE_API_URL`
- 文本聊天默认：Qwen/DashScope；DeepSeek 默认禁用，避免误扣费。

## 成本安全规则
- 默认只自动查本地/缓存菜品图，不自动外部生图。
- `/food-image/generate` 是潜在扣费入口；改前端时不要误把 lookup 换成 generate。
- Profile 文案如果写“自动生成/消耗 Token”，要确认实际行为；当前代码是“自动查找本地/缓存成品图”。
- `GL_IMAGE_API_KEY` 为空时 generate 返回 503，不应回退到其它付费服务。

## 413 区分
- App 上传 413：页面/接口是 LifeWise 域名，HTML 写 nginx；查服务器 Nginx `client_max_body_size`。
- Codex/AiMaMi 413：URL 是 `127.0.0.1:*/codex/router/v1/responses`；这是上下文太大，不是项目上传问题。

## 生成源图清理
- 内置 image_gen 会先保存到 C:\Users\wx\.codex\generated_images\...。项目要用的图必须复制/转存到 uploads/food-images/v1/ 等项目目录。
- 转存、更新 index.json、查看/校验无误后，删除 C 盘本次用过的源 PNG，避免堆积。


## 2026-06-10 本轮变更记录

### 提交 541148b `feat: add safe manual food image generation`
- 修正设置页“菜品成品图”文案：自动流程只查本地/缓存图；手动生成才可能扣费。
- 聊天页新增“手动生成（会扣费）”按钮：未命中本地/服务器缓存时才显示。
- 点击手动生成前弹确认框，确认后才调用 `/api/food-image/generate`。
- 增加生图任务轮询 `/api/food-image/status`，生成完成后写入缓存 `/api/food-image/cache`。
- 新增 `韭菜炒鸡蛋` 项目图片：`uploads/food-images/v1/jiucai-chao-jidan.webp`。
- 更新 `uploads/food-images/v1/index.json`，菜品图从 62 增至 63。
- 新增 `.codexignore`，减少 Codex/AiMaMi 413 风险。
- 新增本文档，记录图片链路和成本安全规则。

### 提交 f6a1e28 `feat: add next batch of food images`
- 新增 5 张本地菜品图：
  - `辣椒炒肉` -> `uploads/food-images/v1/lajiao-chao-rou.webp`
  - `红烧茄子` -> `uploads/food-images/v1/hongshao-qiezi.webp`
  - `凉拌黄瓜` -> `uploads/food-images/v1/liangban-huanggua.webp`
  - `紫菜蛋花汤` -> `uploads/food-images/v1/zicai-danhuatang.webp`
  - `醋溜白菜` -> `uploads/food-images/v1/culiu-baicai.webp`
- 更新 `index.json`，菜品图从 63 增至 67。
- 更新 `Chat.vue` 的 `prebuiltFoodNames`，让前端能识别新增菜名和更多已有早餐/主食图。
- 生成接触表用于人工检查：`docs/food-image-batch-20260609-next5-contact-sheet.jpg`。
- 把“生图资产必须转存到项目目录，并清理 C 盘源图”的规则写入 `AGENTS.md` 和本文档。

### 验证结果
- `index.json` 校验：67 items，0 missing files，0 duplicate names，0 bad placeholder names。
- `npm run build`：Exit 0，Vite 构建成功。
- 仍有 Vite/Rolldown 第三方依赖 pure annotation warning 和 chunk size warning，非本次改动引起，不影响构建。

### 清理结果
- 已将本次 `image_gen` 源 PNG 转存为项目 WebP。
- 已删除本次使用的 `C:\Users\wx\.codex\generated_images\019eace6-da73-7d33-9b7e-e8a0cc66193a` 源目录，避免 C 盘堆积。

### 注意事项
- Windows PowerShell 管道/控制台显示中文可能乱码；写入中文 JSON/源码时优先用 UTF-8 文件或 Unicode escape 脚本，不要依赖控制台直接传中文。
- 删除 `C:\Users\wx\.codex\generated_images\...` 前必须先确认项目目录图片、`index.json`、前端识别表和构建都已通过。
