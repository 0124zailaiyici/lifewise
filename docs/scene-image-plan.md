# Scene image asset plan

目标：把首页场景卡片右下角的外链图替换为本地自生成、统一风格图片。本文只提供可集成方案，不修改 `frontend/src/views/Home.vue`。

## 统一视觉规范

- 风格关键词：warm, clean, light background, real-life household objects, slight top-down angle, soft natural daylight, no people, no text.
- 构图：主体放在画面右下 60% 区域，左上留出浅色负空间，方便卡片上叠加文字/图标。
- 背景：米白、浅灰、浅木色或淡彩桌面；低对比度、无复杂纹理。
- 光线：柔和自然光，轻微阴影，避免强反光。
- 禁止：人物、手、脸、品牌 logo、包装文字、可读文字、水印、过暗背景、杂乱场景。
- 建议生成比例：4:3 或 16:10；最终裁切成卡片使用的小图。

## 建议资源路径与格式

建议新增目录：

```text
frontend/src/assets/scenes/
```

建议命名：

```text
scene-cooking.webp
scene-shopping.webp
scene-repair.webp
scene-housework.webp
scene-health.webp
scene-fashion.webp
scene-etiquette.webp
scene-pet.webp
scene-writing.webp
scene-mealplan.webp
```

建议尺寸与压缩：

- 生成源图：`1024x768` 或 `1280x960`，便于统一裁切。
- 交付图：`360x260` WebP，quality `72-82`。
- 如果只用于当前 `.scene-photo`（CSS 中约 `92x78` 展示）：可再额外输出 `240x180` WebP；但为了高 DPR 屏幕和后续复用，推荐保留 `360x260`。
- 色彩：sRGB。
- 单文件目标体积：约 `12-35 KB`。

## 统一基础 prompt 模板

每张图都在该基础模板上替换场景物品：

```text
A warm clean lifestyle product photo for a mobile app UI card, [SCENE OBJECTS]. Light cream background, real everyday objects, slight top-down angle, soft natural daylight from upper left, gentle shadows, airy minimal composition, subject arranged mostly in the lower-right area with empty negative space in the upper-left, pastel neutral color palette, cozy and practical mood. No people, no hands, no faces, no text, no letters, no numbers, no logos, no watermark, no brand packaging, no clutter, no dark background. 4:3 aspect ratio.
```

## 10 个场景 prompts

### cooking

文件名：`frontend/src/assets/scenes/scene-cooking.webp`

```text
A warm clean lifestyle product photo for a mobile app UI card, a small ceramic bowl, wooden spoon, folded linen napkin, a few fresh herbs, a lemon wedge, and simple cooking ingredients on a light cream kitchen surface. Light cream background, real everyday objects, slight top-down angle, soft natural daylight from upper left, gentle shadows, airy minimal composition, subject arranged mostly in the lower-right area with empty negative space in the upper-left, pastel neutral color palette, cozy and practical mood. No people, no hands, no faces, no text, no letters, no numbers, no logos, no watermark, no brand packaging, no clutter, no dark background. 4:3 aspect ratio.
```

### shopping

文件名：`frontend/src/assets/scenes/scene-shopping.webp`

```text
A warm clean lifestyle product photo for a mobile app UI card, a reusable cotton grocery bag partly filled with fresh vegetables, a small pear, leafy greens, and a simple woven basket edge on a pale tabletop. Light cream background, real everyday objects, slight top-down angle, soft natural daylight from upper left, gentle shadows, airy minimal composition, subject arranged mostly in the lower-right area with empty negative space in the upper-left, pastel neutral color palette, cozy and practical mood. No people, no hands, no faces, no text, no letters, no numbers, no logos, no watermark, no brand packaging, no clutter, no dark background. 4:3 aspect ratio.
```

### repair

文件名：`frontend/src/assets/scenes/scene-repair.webp`

```text
A warm clean lifestyle product photo for a mobile app UI card, a neat set of home repair tools including a small screwdriver, adjustable wrench, measuring tape without visible markings, a few screws, and a soft cloth on a pale wooden surface. Light cream background, real everyday objects, slight top-down angle, soft natural daylight from upper left, gentle shadows, airy minimal composition, subject arranged mostly in the lower-right area with empty negative space in the upper-left, pastel neutral color palette, cozy and practical mood. No people, no hands, no faces, no text, no letters, no numbers, no logos, no watermark, no brand packaging, no clutter, no dark background. 4:3 aspect ratio.
```

### housework

文件名：`frontend/src/assets/scenes/scene-housework.webp`

```text
A warm clean lifestyle product photo for a mobile app UI card, folded microfiber cloths, a simple spray bottle with no label, a soft sponge, and a small ceramic dish on a bright clean countertop. Light cream background, real everyday objects, slight top-down angle, soft natural daylight from upper left, gentle shadows, airy minimal composition, subject arranged mostly in the lower-right area with empty negative space in the upper-left, pastel neutral color palette, cozy and practical mood. No people, no hands, no faces, no text, no letters, no numbers, no logos, no watermark, no brand packaging, no clutter, no dark background. 4:3 aspect ratio.
```

### health

文件名：`frontend/src/assets/scenes/scene-health.webp`

```text
A warm clean lifestyle product photo for a mobile app UI card, a clear glass of water, a small bowl of fruit, a folded towel, and a simple white pill organizer with no markings on a soft beige surface. Light cream background, real everyday objects, slight top-down angle, soft natural daylight from upper left, gentle shadows, airy minimal composition, subject arranged mostly in the lower-right area with empty negative space in the upper-left, pastel neutral color palette, cozy and practical mood. No people, no hands, no faces, no text, no letters, no numbers, no logos, no watermark, no brand packaging, no clutter, no dark background. 4:3 aspect ratio.
```

### fashion

文件名：`frontend/src/assets/scenes/scene-fashion.webp`

```text
A warm clean lifestyle product photo for a mobile app UI card, neatly folded neutral clothing fabrics, a soft scarf, simple sunglasses with no logo, and a small accessory tray on a light linen background. Light cream background, real everyday objects, slight top-down angle, soft natural daylight from upper left, gentle shadows, airy minimal composition, subject arranged mostly in the lower-right area with empty negative space in the upper-left, pastel neutral color palette, cozy and practical mood. No people, no hands, no faces, no text, no letters, no numbers, no logos, no watermark, no brand packaging, no clutter, no dark background. 4:3 aspect ratio.
```

### etiquette

文件名：`frontend/src/assets/scenes/scene-etiquette.webp`

```text
A warm clean lifestyle product photo for a mobile app UI card, a tasteful wrapped gift box with plain paper and ribbon, a small empty greeting card with no writing, a teacup, and a delicate flower stem on a pale tabletop. Light cream background, real everyday objects, slight top-down angle, soft natural daylight from upper left, gentle shadows, airy minimal composition, subject arranged mostly in the lower-right area with empty negative space in the upper-left, pastel neutral color palette, cozy and practical mood. No people, no hands, no faces, no text, no letters, no numbers, no logos, no watermark, no brand packaging, no clutter, no dark background. 4:3 aspect ratio.
```

### pet

文件名：`frontend/src/assets/scenes/scene-pet.webp`

```text
A warm clean lifestyle product photo for a mobile app UI card, a ceramic pet bowl, a small rope toy, a soft pet brush, and a folded beige blanket on a light floor surface. Light cream background, real everyday objects, slight top-down angle, soft natural daylight from upper left, gentle shadows, airy minimal composition, subject arranged mostly in the lower-right area with empty negative space in the upper-left, pastel neutral color palette, cozy and practical mood. No animals, no people, no hands, no faces, no text, no letters, no numbers, no logos, no watermark, no brand packaging, no clutter, no dark background. 4:3 aspect ratio.
```

### writing

文件名：`frontend/src/assets/scenes/scene-writing.webp`

```text
A warm clean lifestyle product photo for a mobile app UI card, a blank notebook with no visible lines or writing, a simple pencil, a ceramic mug, and a small desk accessory on a pale wooden desk. Light cream background, real everyday objects, slight top-down angle, soft natural daylight from upper left, gentle shadows, airy minimal composition, subject arranged mostly in the lower-right area with empty negative space in the upper-left, pastel neutral color palette, cozy and practical mood. No people, no hands, no faces, no text, no letters, no numbers, no logos, no watermark, no brand packaging, no clutter, no dark background. 4:3 aspect ratio.
```

### mealplan

文件名：`frontend/src/assets/scenes/scene-mealplan.webp`

```text
A warm clean lifestyle product photo for a mobile app UI card, small meal-prep containers with colorful simple ingredients, a wooden spoon, a few vegetables, and a folded napkin on a bright kitchen surface. Light cream background, real everyday objects, slight top-down angle, soft natural daylight from upper left, gentle shadows, airy minimal composition, subject arranged mostly in the lower-right area with empty negative space in the upper-left, pastel neutral color palette, cozy and practical mood. No people, no hands, no faces, no text, no letters, no numbers, no logos, no watermark, no brand packaging, no clutter, no dark background. 4:3 aspect ratio.
```

## 生成后质检清单

每张图导入前检查：

1. 无人物、手、脸、文字、logo、水印。
2. 主体在右下区域，左上有足够浅色留白。
3. 十张图光线、背景、饱和度统一。
4. 缩小到 `.scene-photo` 约 `92x78` 后仍能识别场景。
5. WebP 文件名与场景 key 一一对应。

## 后续集成建议（不在本次执行）

在 `Home.vue` 中可采用 Vite 静态资源导入，避免运行时路径问题：

```js
import sceneCooking from '../assets/scenes/scene-cooking.webp'
import sceneShopping from '../assets/scenes/scene-shopping.webp'
import sceneRepair from '../assets/scenes/scene-repair.webp'
import sceneHousework from '../assets/scenes/scene-housework.webp'
import sceneHealth from '../assets/scenes/scene-health.webp'
import sceneFashion from '../assets/scenes/scene-fashion.webp'
import sceneEtiquette from '../assets/scenes/scene-etiquette.webp'
import scenePet from '../assets/scenes/scene-pet.webp'
import sceneWriting from '../assets/scenes/scene-writing.webp'
import sceneMealplan from '../assets/scenes/scene-mealplan.webp'
```

然后把 `scenes[].photo` 从 Unsplash URL 替换为对应变量。当前组件已有 `preloadScenePhotos()` 和 `brokenScenePhotos` 兜底逻辑，本地图片也可继续复用该逻辑。
