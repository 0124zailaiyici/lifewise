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
