# 开发教训记录

## 2026-06-05 烹饪步骤配图功能

### 问题
给 AI 回复的菜谱步骤添加配图，让用户更直观地理解每个烹饪步骤。

### 尝试过的方案
1. **Pixabay API** — 需要注册 API Key，国内可能无法访问
2. **Unsplash API** — 需要 API Key
3. **Picsum.photos** — 无需 API Key，但图片随机且与烹饪无关
4. **Loremflickr** — 免费，返回 Flickr 真实照片，但关键词匹配不准
5. **小米 MiMo Omni** — 纯文本模型，不能生成图片（TTS/ASR 等模型也不行）
6. **SVG 插画** — 最终方案，Java 后端根据关键词生成 SVG 场景插画

### 关键教训

#### 1. 国内可用的免费图源有限
- Pixabay/Unsplash/Pexels 都需要注册
- 服务器能访问这些网站但用户浏览器可能打不开
- 建议：优先使用无需外部 API 的自生成方案

#### 2. 小米 MiMo 模型家族是纯文本/语音模型
- mimo-v2-omni 是多模态理解（看图），不是生图
- mimo-v2.5-tts 是语音合成，不是图像生成
- 没有图像生成能力

#### 3. Java 后端编码问题
- **BOM 陷阱**：PowerShell 的 Set-Content -Encoding UTF8 会加 BOM（\ufeff），Java 编译器不识别
- 解决方案：用 `[System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))` 去掉 BOM
- 每次修改 Java 文件后都必须检查 BOM

#### 4. Vue SFC 的 style 标签坑
- 两个 `<style>` 块（scoped + unscoped）必须严格配对
- `</style>` 不能跟 CSS 内容在同一行，否则 Vue 编译器报 "Invalid end tag"
- 修改文件时要小心标签配对，不要误删

#### 5. 前端图片 fallback 策略
- `<img onerror>` 触发条件：图片真的加载失败（404/网络错误）
- 如果图片加载成功但内容不对，onerror 不会触发
- 先用真实图片，失败时降级到本地生成内容

#### 6. 文件编码一致性
- PowerShell 脚本修改文件时，UTF8 编码可能引入 BOM
- Python 脚本修改文件时也会遇到编码问题
- 建议所有手动修改用专门的编码工具或 IDE

### 当前最佳实践
- 烹饪步骤配图：Java 后端动态生成 SVG 插画（切菜/炒制/炖煮等场景）
- 每种操作对应特定的 SVG 场景画面（食材 + 工具 + 动作）
- 前端先用 `<img>` 加载，失败则显示 emoji 降级
- 如果后续需要真实图片，配置 Pixabay API Key 即可切换

## 2026-06-06 语音输入优化

### 问题
语音识别频繁报 "aborted" 错误，用户无法手动停止录音，权限被拒后引导不清晰。

### 解决方案
1. **手动停止** — 再次点击麦克风按钮调用 `recognition.stop()`，并显示"已停止"
2. **自动重试** — `no-speech` 错误自动重试 2 次，每次间隔 500ms
3. **权限引导** — 明确的文字提示用户到地址栏左侧 🔒 图标开启麦克风
4. **冲突预防** — 创建新实例前先 `abort()` 旧的，防止多个实例冲突
5. **continuous 改 false** — 非连续模式，说完自动结束，体验更自然

### 关键教训

#### 1. 跨文件编辑要用 Python
- PowerShell 处理多行字符串替换很麻烦（引号嵌套、转义字符）
- 方案：保存 Python 脚本到临时文件执行，用 Python 的字符串处理能力

#### 2. JavaScript 模板字面量 vs Python f-string
- Python 的三引号字符串内如果包含 `f'...'`，Python 会把它当 f-string 解析
- 实际 JS 应该用 `` `...${var}...` `` 而不是 `f'...{var}...'`
- 在 Python 的 `"""..."""` 字符串中，`f'...'` 只是普通字符，但输出的 JS 代码是错的
- 修复：直接替换文件中的 `f'` 为 `` ` `` 并去掉 `f` 前缀

#### 3. SpeechRecognition API 特性
- `continuous: true` → 用户不说话时不会自动结束，需要手动 stop
- `continuous: false` → 说完停顿自动结束，更自然
- `interimResults: true` → 可以显示实时识别中间结果
- `webkitSpeechRecognition` 需要全局引用防止 GC 回收
- `onerror` 中的 `aborted` 错误可能由多种原因触发（用户切换标签、另一个实例启动等）

#### 4. 权限检查注意事项
- `navigator.permissions.query({ name: 'microphone' })` 是异步的，不能阻塞
- 在 https 或 localhost 下才能使用语音识别
- 一旦用户拒绝权限，需要手动在地址栏重新开启

## 2026-06-06 移动端适配 — 底部导航一致性

### 问题
- Dashboard 页没有底部导航，用户无法导航到其他页面
- KnowledgeBase 页底部导航项与其他页面不一致（常识库替代收藏）

### 修复方案
- Dashboard：添加返回按钮 + 标准4tab底部导航
- KnowledgeBase：改为标准4tab底部导航，选中"我的"（从该页面跳入）

### 教训
- 所有内部页面都应统一底部导航结构
- 标准结构：首页/历史/收藏/我的
- 新增页面时先参考现有页面结构


## ????
- ????????????????task_id?taskId, is_final?isFinal?
- ????????????
- ?????????
- ??????????? + aborted ???? + ???????
- ?? API ??????? 502 / 401 / ????
- ????????? action ???? fallback ???
- ????? previewImage ??

### ??
- ??? API ?????? camelCase??????????????????
- ?? Chat.vue ?????????????
- ??????? localStorage ???????? token
- ?????? git stash ???????? git checkout ??

## 2026-06-07 步骤配图正则匹配修复 + 全场景支持

### 问题
步骤配图的关键词基于正则表达式匹配场景，但存在以下问题：
1. **pan 在 pants 中**：穿搭场景关键词 "pants" 包含 "pan"（炒锅），导致穿搭配图显示"炒制"
2. **hat 误匹配**：	hat、chat、what 等常见词含 "hat"，可能被误识别为穿搭
3. **heat 误匹配**：sweater、wheat 等词含 "heat"，可能被误识别为加热场景
4. **关键词不全**：hoodie、chino、cardigan 等常见服饰词未收录
5. **缺少中文支持**：后端 SVG 生成器只能匹配英文关键词，中文服饰名（卫衣、针织等）落到了默认的"通用"场景

### 修复方案

#### 后端（ImageController.java）
- 添加 \b 单词边界：\bpan\b、\bhat\b、\bheat\b
- 补充服饰关键词：hoodie、sweater、knit、chino、cardigan、blouse、vest 等
- 添加中文匹配：卫衣、针织、衬衫、西装、夹克、裤、裙、鞋、帽 等

#### 前端（Chat.vue）
- 提取 makeStepImg(kw) 公共函数，消除 IIFE 重复代码
- stepEmoji() / stepGradient() 同步添加 \b 单词边界和新关键词
- 添加新场景渐变色 CSS：grad-repair、grad-housework、grad-fashion、grad-health、grad-pet、grad-shop
- suggestions（健康）和 outfits（穿搭）增加 step_image 渲染

### 关键教训

#### 1. 正则匹配优先级和顺序很重要
- Java 的 keyword.matches() 从上到下匹配，先匹配到的先返回
- 短关键词（pan、hat、heat）容易误匹配长词中的子串
- 所有短关键词都必须加 \b 单词边界

#### 2. 中文关键词需要额外处理
- AI 返回的 step_image 字段可能是中文（如"灰色卫衣"）
- 后端 SVG 生成器需要同时支持中英文关键词匹配
- 中文匹配不用加 \b（中文天然没有单词边界问题）

#### 3. 前端 JS 和后端 Java 的正则一致性
- 前后端关键词列表要保持同步，否则 emoji 和 SVG 场景可能不一致
- JS 用 RegExp.test()，Java 用 String.matches()，行为有差异
- JS 的 .test() 是部分匹配，Java 的 .matches() 是全串匹配，要用 .*pattern.*

### 当前最佳实践
- 所有场景关键词：在 stepEmoji、stepGradient、generateStepSvg 三处同步维护
- 新增场景时：前端加 emoji + 渐变色 + CSS 样式，后端加 SVG 插画
- 涉及正则表达式修改时：必须检查是否有其他词包含该子串

## 2026-06-07 常识库缓存不生效 — 对话创建时序问题

### 问题
新对话的问题和回答不会自动缓存到常识库。第一次问某问题时没有命中缓存（正常），但 AI 回复后也没有保存到常识库，导致每次相同问题都需要调 API。

### 原因
ChatController.sendMessage() 中，处理新对话的逻辑顺序是：
1. **先**创建 Conversation（生成 convId）
2. **再**调用 iService.chat(..., convId, ...)

而 AiServiceImpl.chat() 中的缓存逻辑依赖 conversationId == null 来判断是否为"新对话的首个问题"：
`java
if (conversationId == null && ...) {  // 永远为 false！
    knowledgeBaseService.saveAnswer(...);  // 不会执行
}
`

因为 Controller 在调用前已创建了对话，convId 永远不为 null，导致：
- 缓存查询（indAnswer）被跳过（以为是追问）
- 缓存保存（saveAnswer）被跳过（同上）

### 修复
将对话创建移到 AI 调用之后：

`
// 修复前
convId = createConversation(...)    // convId = 123
aiService.chat(..., convId=123)    // 服务端永远看不到 null

// 修复后  
aiService.chat(..., convId=null)   // 服务端看到 null → 可以查缓存+保存
convId = createConversation(...)   // AI 回复后才创建对话
`

### 关键教训

#### 1. 控制器的前置处理可能破坏服务层的状态判断
- Controller 为了先把数据准备好，可能提前创建了实体
- 但 Service 层依赖某些字段为 null 来做逻辑判断
- **原则**：先调 Service 执行业务逻辑，再处理副作用（创建实体、保存记录）

#### 2. 常识库缓存的生命周期
- 缓存只存 **新对话的第一个问题**（conversationId == null）
- 追问（已有 convId）不会存，避免冗余
- 这需要在 Controller 和 Service 之间正确传递 null

#### 3. 调试方法
- 从日志搜索 "知识库新增"、"cache hit"、"Skipping cache" 可以判断缓存是否生效
- 如果从来没出现过 "知识库新增"，说明保存逻辑从未执行
- 此时检查调用链中 conversationId 是否被提前赋值
## 2026-06-07 追问场景自动检测 + 对话导出

### 问题
1. Python 脚本修改 Java 文件时错误地匹配了 switch 的关闭大括号，误删了 baseRule 和 schema 变量声明
2. Vite 没有配置 @ 路径别名，导致 import from "@/api/index.js" 编译失败
3. 浏览器下载文件到默认下载文件夹，用户希望保存到项目目录

### 修复方案

#### 追问场景自动检测
- 在 if(hasHistory) 块中添加场景判断 switch（cooking/repair/fashion 等8个场景）
- 追问时返回场景相关的自然语言提示，保持对话风格一致
- 使用 Node.js 脚本（而非 Python）从 git 历史恢复被删除的代码块

#### 对话导出
- 前端 Chat.vue 头部添加 📥 按钮
- 新增后端 POST /api/chat/export 接口，保存到 backend/exports/ 目录
- 文件名格式：对话记录_yyyyMMdd_HHmmss.txt

### 关键教训

#### 1. Python 字符串匹配 Java 大括号的风险
- Python 脚本按关键字搜索 Java 文件时，如果两个结构用同一个关键字（如 "}"），可能匹配错误
- switch 的关闭 `}` 和 if 块的关闭 `}` 在字符串中看起来一样
- 修复方法：从 git 历史提取原文，用 Node.js/Python 做精确字符串替换
- **建议**：修改 Java 文件时用行号定位替代关键字搜索

#### 2. Vite @ 路径别名需要显式配置
- Vite 默认不支持 `@/path` 的 import 写法
- 需要在 vite.config.js 中添加 `resolve.alias` 配置：
  ```js
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } }
  ```
- 没有配置的话，必须用相对路径 `../api/index.js`
- **建议**：使用相对路径，减少配置依赖

#### 3. 浏览器无法保存到指定目录
- 网页的 download API 只能下载到浏览器的默认下载文件夹
- 无法指定任意保存路径
- 解决方案：通过后端 API 保存到服务器本地目录
- **建议**：文件保存类功能优先走后端 API

#### 4. Spring Boot 构建问题
- 如果之前的 java 进程还在运行，mvn package 会因 jar 文件被占用而失败
- 必须先 taskkill /f /im java.exe 再重建

## 2026-06-07 阿里云服务器部署 + GitHub Actions CI

### 问题
1. 需要在手机使用 LifeWise，但项目只跑在本地 localhost
2. 阿里云 ECS 已有另一个项目（Pose Guide）占用 8080 端口
3. SSH 端口 22 连通但 SSH 协议超时（疑似服务器防火墙/sshd 配置问题）
4. 手机访问需要开放安全组端口

### 解决方案

#### 部署方案
- 使用 GitHub Actions 自动构建项目（后端 JAR + 前端 dist）
- 通过阿里云 Workbench（网页控制台）连服务器手动操作
- LifeWise 后端部署到 8082 端口（与 Pose Guide 的 8080 不冲突）
- Nginx 监听 8081 端口，代理前端静态文件 + 后端 API
- 编写 deploy/update.sh 一键更新脚本

#### GitHub Actions 教训
- YAML 的 heredoc（<< 'EOF'）可能导致解析错误
- npm ci 比 npm install 更严格（lock 文件校验），改用 npm install --legacy-peer-deps
- actions/upload-artifact@v4 用于保存构建产物供下载
- workflow 文件不要超过一个 jobs（多 job 需要 secrets 配置复杂）

### 关键教训

#### 1. SSH 不通就用 Workbench
- 阿里云 ECS 的 Workbench 远程连接（网页终端）比 SSH 更可靠
- 无需配置安全组 22 端口
- 支持文件上传/下载、复制粘贴

#### 2. 端口规划很重要
- 同一台服务器跑多个项目时，提前规划端口：
  - 80/443：主站（通常是 Nginx）
  - 8080：项目 A 后端
  - 8081：项目 B 前端
  - 8082：项目 B 后端
- 每个项目互不干扰

#### 3. 外网访问记得配安全组
- 阿里云安全组入方向规则：端口开放 + 授权对象 0.0.0.0/0
- 修改安全组后立即生效，不需重启服务器

#### 4. 数据库文件锁问题
- kill -9 无法正确释放 H2 数据库锁
- 需要等待操作系统释放文件锁，或用 pgrep java 杀干净
- 数据库文件备份很重要（更新脚本自动备份）

#### 5. 一键更新脚本
- 更新流程：下载构建包 → 停旧后端 → 备份数据库 → 解压覆盖 → 启动新后端
- 配置文件（application-cloud.properties）单独保存不被覆盖
- 脚本放在 deploy/update.sh，服务器下载后可直接执行

## Scene image gen lessons (2026-06-12)
- generateScenePrompt() must return non-empty prompt for ALL scenes, not just shopping
- Backend FoodImageController's isScene check must include ALL scene types, not just a subset
- Scene image generation should respect the same setting_foodImage toggle as food images
- Local food cache lookup (attachLocalFoodImage) should always run regardless of toggle; only auto-gen respects toggle
- Manual gen button ("�ֶ�����") should show when no cache found, regardless of scene image state or toggle

## Dual image system clarification
- There are TWO separate image systems: "��Ʒͼ" (food recipe images via attachLocalFoodImage) and "������ͼ" (scene images via attachSceneImage)
- They can fire simultaneously for cooking queries; ensure display doesn't duplicate
- Food-actions template condition: show when _foodDishName set AND no _foodImageUrl AND no _foodImageLoading (no _sceneImage check needed)
- Scene image gen condition: respect setting_foodImage via localStorage check

## Deployment lessons (2026-06-12)
- GitHub Actions auto-deploy job failed due to missing SSH secrets; removed it since server watchdog handles it
- Watchdog polls GitHub API every 30s, runs MODE=frontend deploy-latest.sh
- Nightly.link artifacts only update on SUCCESSFUL workflow runs; failed auto-deploy step caused stale artifacts
