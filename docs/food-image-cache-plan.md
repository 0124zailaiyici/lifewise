# LifeWise 常见菜品图片预生成/本地缓存方案

> 调研时间：2026-06-09  
> 目标：常见菜品（如“鱼香肉丝”）优先使用预生成/本地静态图片，避免运行时调用外部生图接口浪费费用与 token，并提升响应速度。  
> 本文只给落地方案，不包含图片生成与代码修改。

## 1. 当前实现梳理

### 前端

- `frontend/src/views/Chat.vue`
  - AI 回复后，如果 `localStorage.setting_foodImage !== 'off'`，尝试将回复内容解析为 JSON。
  - 当解析结果包含 `title` 和 `steps` 时，使用 `parsed.title.trim()` 作为 `dishName`。
  - 命中顺序目前是：
    1. 浏览器 `localStorage`：key 为 `food_img_${dishName}`。
    2. 调用 `generateFoodImage(dishName)`，即 `POST /api/food-image/generate`。
    3. 如果后端返回 `cached=true`，直接使用 `imageUrl` 并写入 `localStorage`。
    4. 如果后端返回 `taskId`，前端轮询 `/api/food-image/status`。
    5. 轮询拿到远端 `resultUrl` 后，写入 `localStorage`，并调用 `/api/food-image/cache` 保存到后端数据库。
  - 问题：`/generate` 同时承担“查缓存”和“发起生图”两个职责，前端只想查缓存时也可能触发外部生图。
- `frontend/src/api/index.js`
  - 相关 API：
    - `generateFoodImage(dishName)` → `POST /food-image/generate`
    - `getFoodImageStatus(taskId)` → `GET /food-image/status`
    - `saveFoodImageCache(dishName, imageUrl)` → `POST /food-image/cache`
- `frontend/vite.config.js`
  - 本地开发代理了 `/api`、`/uploads`、`/food-image` 到 `localhost:8080`。
  - 由于前端 API baseURL 是 `/api`，`/food-image` 这个代理目前不是主路径；真正请求是 `/api/food-image/...`。

### 后端

- `backend/src/main/java/com/lifewise/controller/FoodImageController.java`
  - `POST /api/food-image/generate`
    - 先用 `FoodImageCacheRepository.findByDishName(dishName.trim())` 查库。
    - 命中后返回 `{ cached: true, imageUrl }`，并增加 `hitCount`。
    - 未命中后立即调用外部 `gl-image` 接口提交 `gpt-image-2` 生图任务。
  - `GET /api/food-image/status`
    - 通过外部任务 ID 查询生成状态，返回 `resultUrl`。
  - `POST /api/food-image/cache`
    - 前端拿到远端 `resultUrl` 后回写数据库。
  - 当前后端只缓存“图片 URL”，没有把远端图片下载成本地文件。
- `backend/src/main/java/com/lifewise/entity/FoodImageCache.java`
  - 表：`food_image_cache`
  - 字段：
    - `dishName`
    - `imageUrl`
    - `createdAt`
    - `hitCount`
  - `dishName` 唯一，但没有归一化 key、别名、来源、状态、本地文件路径、宽高、版权/审核状态等字段。
- `backend/src/main/java/com/lifewise/config/WebConfig.java`
  - 将 `/uploads/**` 映射到 `app.upload-dir`。
  - 默认 `app.upload-dir=./uploads`，云端为 `/opt/lifewise/uploads`。
- `backend/src/main/java/com/lifewise/config/JwtAuthFilter.java`
  - `/api/food-image` 在免登录白名单中，任何人可请求生图接口。
  - 如果继续保留外部生图能力，需要增加限流、鉴权或只允许管理员/服务端任务触发。

### 部署与静态资源

- 云端脚本：
  - `deploy/setup-server.sh` 创建 `/opt/lifewise/uploads`，Nginx 对 `/uploads/` 使用 `alias /opt/lifewise/uploads/`。
  - `deploy/deploy-latest.sh` 确保 `uploads` 和 `data` 目录存在，且部署前备份数据库。
  - `deploy/start.sh` 使用 `app.upload-dir` 或默认 `/opt/lifewise/uploads`。
- Docker：
  - `docker-compose.yml` 将 `uploads` volume 挂载到后端 `/app/uploads`。
  - `frontend/nginx.conf` 目前只代理 `/api/`，没有代理 `/uploads/`。如果 Docker 环境前端展示 `/uploads/...` 图片，需补充 `/uploads/` 代理或由后端返回 `/api/...` 图片地址。

## 2. 首批建议缓存菜品（45 个）

优先选择高频家常菜、做法稳定、用户容易询问、生成图复用价值高的菜品：

1. 鱼香肉丝
2. 宫保鸡丁
3. 红烧肉
4. 糖醋里脊
5. 麻婆豆腐
6. 西红柿炒鸡蛋
7. 青椒肉丝
8. 土豆丝
9. 酸辣土豆丝
10. 回锅肉
11. 可乐鸡翅
12. 红烧排骨
13. 糖醋排骨
14. 蒜蓉西兰花
15. 地三鲜
16. 鱼香茄子
17. 肉末茄子
18. 黄焖鸡
19. 辣子鸡
20. 清蒸鱼
21. 红烧鱼
22. 水煮鱼
23. 酸菜鱼
24. 水煮肉片
25. 京酱肉丝
26. 蚂蚁上树
27. 干煸豆角
28. 蒜苔炒肉
29. 木须肉
30. 葱爆羊肉
31. 番茄牛腩
32. 土豆炖牛肉
33. 小炒黄牛肉
34. 农家小炒肉
35. 香菇青菜
36. 炒青菜
37. 手撕包菜
38. 干锅花菜
39. 韭菜炒鸡蛋
40. 虾仁炒蛋
41. 油焖大虾
42. 蒜蓉粉丝虾
43. 蛋炒饭
44. 扬州炒饭
45. 鸡蛋面

第二批可扩展：粥、汤、早餐、减脂餐、儿童餐、地方菜、空气炸锅菜品等。

## 3. 本地资源目录与命名规范

### 推荐目录

优先使用后端可直接静态服务的上传目录：

```text
uploads/
  food-images/
    v1/
      index.json
      yuxiang-rousi.webp
      gongbao-jiding.webp
      hongshao-rou.webp
      ...
```

云端对应：

```text
/opt/lifewise/uploads/food-images/v1/
```

访问 URL：

```text
/uploads/food-images/v1/yuxiang-rousi.webp
```

选择 `uploads/food-images` 而不是 `frontend/src/assets` 的原因：

- 不需要重新构建前端即可追加/替换菜品图。
- 后端数据库中缓存 URL 可直接指向同一静态路径。
- 与当前 `/uploads/**` 静态映射、云端 Nginx、部署脚本最兼容。
- 后续可通过管理脚本或后台功能更新图片。

### 文件格式

- 首选：`.webp`
- 兜底：`.jpg`
- 建议尺寸：`1024x1024` 或 `768x768`
- 单图大小：尽量控制在 `100KB ~ 300KB`
- 图片内容：成品菜图，不要带水印、品牌、人物脸、菜单文字。

### 命名规范

推荐稳定 slug：

```text
{pinyin-with-hyphen}.webp
```

示例：

```text
鱼香肉丝 -> yuxiang-rousi.webp
宫保鸡丁 -> gongbao-jiding.webp
西红柿炒鸡蛋 -> xihongshi-chao-jidan.webp
```

不要直接用中文文件名，避免 Linux/Nginx/压缩包/URL 编码差异。

### 索引文件

建议新增：

```text
uploads/food-images/v1/index.json
```

示例结构：

```json
{
  "version": "v1",
  "items": [
    {
      "dishName": "鱼香肉丝",
      "dishKey": "yuxiang_rousi",
      "aliases": ["鱼香肉丝盖饭", "鱼香肉丝家常版"],
      "imageUrl": "/uploads/food-images/v1/yuxiang-rousi.webp",
      "source": "local-prebuilt",
      "width": 1024,
      "height": 1024
    }
  ]
}
```

`dishKey` 用下划线或小写拼音均可，但必须稳定；`imageUrl` 继续保持相对路径，便于不同域名/部署环境复用。

## 4. 前端/后端如何优先命中本地图

### 推荐命中链路

```text
AI 回复 title
  -> normalizeDishName(title)
  -> 前端 localStorage / manifest 内置映射
  -> 后端 food_image_cache 表
  -> 后端本地 manifest/index.json
  -> 远端 URL 缓存（兼容旧数据）
  -> 外部生图（最后兜底，且受开关/限流控制）
```

### 后端优先方案

新增一个“只查不生成”的接口，避免误触发外部生图：

```http
GET /api/food-image/lookup?dishName=鱼香肉丝
```

建议响应：

```json
{
  "code": 200,
  "data": {
    "found": true,
    "cached": true,
    "source": "local-prebuilt",
    "dishName": "鱼香肉丝",
    "dishKey": "yuxiang_rousi",
    "imageUrl": "/uploads/food-images/v1/yuxiang-rousi.webp"
  }
}
```

后端查找顺序：

1. 归一化 `dishName`，生成 `dishKey`。
2. 查 `food_image_cache` 表，优先返回 `source=local-prebuilt` 或本地 `/uploads/food-images/...`。
3. 查 `uploads/food-images/v1/index.json`。
4. 兼容旧记录：如果 `food_image_cache.imageUrl` 是远端 URL，也可返回，但标记 `source=remote-cache`。
5. 不命中时只返回 `found=false`，不要自动调用外部生图。

`POST /api/food-image/generate` 保留，但只作为“明确允许生成”的兜底接口。

### 前端改造建议

目前 `Chat.vue` 的自动逻辑会在服务端缓存未命中时触发生图。建议改为：

1. 先查前端本地缓存：
   - `localStorage.getItem('food_img_' + dishKey)`。
   - key 不再使用原始中文 `dishName`，改用归一化 `dishKey`。
2. 再调用 `lookupFoodImage(dishName)`。
3. `lookup` 命中后展示图片并写入 `localStorage`。
4. `lookup` 未命中时：
   - 默认不自动生图。
   - 如果用户开启“允许为未收录菜品生成图片”，才显示“生成成品图”按钮或低频自动触发。

建议保留旧 `food_img_${dishName}` 的读取逻辑一段时间，用于迁移老用户缓存。

## 5. 什么时候才调用外部图像生成

默认原则：**运行时自动生图应从默认开启改为默认关闭或强约束触发**。

可以调用外部生图的场景：

1. 用户明确点击“生成成品图”。
2. 管理员/离线脚本批量预生成菜品图。
3. 菜品不在本地首批库、不在数据库缓存、不是相似别名，且满足：
   - `setting_foodImage === 'on'`
   - 后端配置了 `GL_IMAGE_API_KEY`
   - 后端额度/日限额未超
   - 用户已登录或接口有其他防滥用机制
   - 同一 `dishKey` 没有进行中的任务

不建议调用外部生图的场景：

- AI 回复标题过泛：如“一周晚餐计划”“健康晚餐”“家常菜推荐”。
- 标题不是具体菜名：如“冰箱剩菜怎么搭配”。
- 已命中本地图片或旧远端缓存。
- 游客/未登录请求。
- 用户关闭菜品图开关。
- GL 接口未配置、余额不足或近期失败率高。

## 6. 数据结构/API 兼容建议

### 数据库字段扩展

当前 `food_image_cache` 可继续兼容，但建议后续扩展：

```text
dish_name        原始标准菜名，如 鱼香肉丝
dish_key         归一化 key，如 yuxiang_rousi；唯一索引
aliases          JSON 字符串，别名列表
image_url        当前使用的图片 URL
local_path       本地文件路径，如 food-images/v1/yuxiang-rousi.webp
source           local-prebuilt | remote-generated | remote-cache | manual-upload
status           active | pending | failed | disabled
hit_count        命中次数
last_hit_at      最近命中时间
created_at       创建时间
updated_at       更新时间
```

最小改造可先只新增：

- `dishKey`
- `source`
- `updatedAt`

如果暂不改表，也可以先将预置数据写入现有表：

```text
dishName = 鱼香肉丝
imageUrl = /uploads/food-images/v1/yuxiang-rousi.webp
hitCount = 0
```

这样无需前端大改，当前 `/generate` 在查库阶段就能命中；但仍建议新增 `lookup`，避免未命中即生图。

### API 兼容

保留旧接口：

- `POST /api/food-image/generate`
- `GET /api/food-image/status`
- `POST /api/food-image/cache`

新增接口：

- `GET /api/food-image/lookup?dishName=...`
- 可选：`GET /api/food-image/manifest` 返回本地菜品图索引。

旧 `generate` 响应继续兼容：

```json
{ "cached": true, "imageUrl": "..." }
```

新字段向后兼容：

```json
{
  "cached": true,
  "found": true,
  "source": "local-prebuilt",
  "dishKey": "yuxiang_rousi",
  "imageUrl": "/uploads/food-images/v1/yuxiang-rousi.webp"
}
```

### 离线预热方式

推荐两种落地路径：

1. **纯数据库预热**
   - 将 45 个菜名和 `/uploads/food-images/v1/*.webp` 插入 `food_image_cache`。
   - 最小侵入，当前代码即可在 `/generate` 查库阶段命中。
2. **manifest + 数据库同步**
   - 维护 `uploads/food-images/v1/index.json` 作为源清单。
   - 启动时或管理脚本将 manifest 同步到 `food_image_cache`。
   - 便于后续替换图片、扩展别名、迁移 CDN。

## 7. 风险和注意事项

1. **避免误触发付费生图**
   - 当前 `/generate` 未命中就生图，是最大风险。
   - 必须增加只查缓存接口，或给 `/generate` 增加 `allowGenerate` 参数，默认 `false`。
2. **接口滥用风险**
   - `/api/food-image` 当前免登录，外部可直接打生图接口。
   - 建议至少对 `generate/status/cache` 做鉴权、限流、来源校验；`lookup` 可视情况开放。
3. **远端 URL 不稳定**
   - 当前缓存的是外部 `resultUrl`，可能过期、被防盗链、加载慢或跨域失败。
   - 预生成后应下载为本地 `/uploads/food-images/...`。
4. **菜名归一化**
   - “西红柿炒鸡蛋/番茄炒蛋”“土豆丝/酸辣土豆丝”不能简单完全匹配。
   - 首期可维护别名表，避免过度模糊匹配导致错图。
5. **部署一致性**
   - 云端 `/uploads/` 已配置；Docker 前端 Nginx 尚未代理 `/uploads/`，需补齐。
   - 前端构建产物更新不应删除 `/opt/lifewise/uploads/food-images`。
6. **缓存版本**
   - 图片替换后浏览器可能继续使用旧 `localStorage`。
   - 建议 localStorage key 带版本，如 `food_img_v1_${dishKey}`；升级图片时改为 `v2`。
7. **版权与内容安全**
   - 预置图片必须来源可控：自生成、可商用素材、或自有拍摄。
   - 禁止使用带水印、平台版权不明、真人肖像、品牌包装明显的图片。
8. **图片质量与用户预期**
   - 同名菜品地域差异大，图片只做“示意成品图”，不要承诺完全一致。
   - 可在 alt/title 中标注“示意图”。
9. **备份**
   - 数据库中预热的 `food_image_cache` 随 H2/MySQL 数据走；部署前仍需备份。
   - `/uploads/food-images` 应加入服务器备份范围。

## 8. 建议实施顺序

1. 创建 `/opt/lifewise/uploads/food-images/v1/`，放入首批 45 张 `.webp`。
2. 维护 `index.json`，包含菜名、slug、别名、URL。
3. 将首批菜品 URL 写入 `food_image_cache`，先实现无代码或低代码命中。
4. 新增 `GET /api/food-image/lookup`，只查不生成。
5. 前端把自动流程改成“localStorage → lookup → 用户明确触发 generate”。
6. 给 `generate/status/cache` 加鉴权、限流和 `allowGenerate` 防误调用。
7. 修复 Docker/Nginx `/uploads/` 代理一致性。
8. 上线后观察 `hitCount`，按命中率扩展第二批菜品。
