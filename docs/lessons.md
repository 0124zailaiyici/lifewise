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