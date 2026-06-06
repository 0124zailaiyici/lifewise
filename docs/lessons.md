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
