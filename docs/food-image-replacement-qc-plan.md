# 常见菜品图真实摄影风格替换：质检与计划

> 日期：2026-06-09  
> 范围：`uploads/food-images/v1/`  
> 约束：不调用项目后端外部生图接口；不修改 AiMaMi/本地代理配置；不提交；文件名与 `index.json.imageUrl` 保持一致。

## 1. 当前质检结果

### 文件与索引

- 目录存在：`uploads/food-images/v1/`
- 索引存在：`uploads/food-images/v1/index.json`
- `index.json` 可用 UTF-8 正常解析，中文菜名正常。
- `index.json` 当前包含 44 个菜品条目。
- 44 个 `imageUrl` 指向的图片文件全部存在。
- 图片格式均为 WebP。
- 图片尺寸均为 `512x512`，与 `index.json` 的 `width/height` 一致。
- 文件大小约 `12KB ~ 15KB`，明显偏小，符合“简单插画/SVG 风格栅格化”的特征，不像真实摄影图。

### 视觉质量

已生成质检拼图：

```text
docs/food-image-contact-sheet-v1.jpg
```

肉眼检查结果：

- 当前图片是统一模板化卡通/插画风格，不是真实食物摄影。
- 每张图中都有类似标题区域，但中文显示为 `????`，用户看到的“问号/乱码”来自图片像素内容本身，不是 `index.json` 编码问题。
- 菜品之间区分度低，多数只是彩色圆点/抽象食材组合，无法准确表达具体菜品。
- 不建议继续上线使用这批图作为“常见菜品成品图”。

## 2. 当前 44 个待替换文件

替换时必须保留以下文件名和 URL：

| 菜品 | 文件 |
|---|---|
| 鱼香肉丝 | `yuxiang-rousi.webp` |
| 宫保鸡丁 | `gongbao-jiding.webp` |
| 红烧肉 | `hongshao-rou.webp` |
| 糖醋里脊 | `tangcu-liji.webp` |
| 麻婆豆腐 | `mapo-doufu.webp` |
| 西红柿炒鸡蛋 | `xihongshi-chao-jidan.webp` |
| 青椒肉丝 | `qingjiao-rousi.webp` |
| 土豆丝 | `tudousi.webp` |
| 酸辣土豆丝 | `suanla-tudousi.webp` |
| 回锅肉 | `huiguo-rou.webp` |
| 可乐鸡翅 | `kele-jichi.webp` |
| 红烧排骨 | `hongshao-paigu.webp` |
| 糖醋排骨 | `tangcu-paigu.webp` |
| 蒜蓉西兰花 | `suanrong-xilanhua.webp` |
| 地三鲜 | `disanxian.webp` |
| 鱼香茄子 | `yuxiang-qiezi.webp` |
| 肉末茄子 | `roumo-qiezi.webp` |
| 黄焖鸡 | `huangmen-ji.webp` |
| 辣子鸡 | `lazi-ji.webp` |
| 清蒸鱼 | `qingzheng-yu.webp` |
| 红烧鱼 | `hongshao-yu.webp` |
| 水煮鱼 | `shuizhu-yu.webp` |
| 酸菜鱼 | `suancai-yu.webp` |
| 水煮肉片 | `shuizhu-roupian.webp` |
| 京酱肉丝 | `jingjiang-rousi.webp` |
| 蚂蚁上树 | `mayi-shangshu.webp` |
| 干煸豆角 | `ganbian-doujiao.webp` |
| 蒜苔炒肉 | `suantai-chaorou.webp` |
| 木须肉 | `muxu-rou.webp` |
| 葱爆羊肉 | `congbao-yangrou.webp` |
| 番茄牛腩 | `fanqie-niunan.webp` |
| 土豆炖牛肉 | `tudou-dun-niurou.webp` |
| 小炒黄牛肉 | `xiaochao-huangniurou.webp` |
| 农家小炒肉 | `nongjia-xiaochao-rou.webp` |
| 香菇青菜 | `xianggu-qingcai.webp` |
| 炒青菜 | `chao-qingcai.webp` |
| 手撕包菜 | `shousi-baocai.webp` |
| 干锅花菜 | `ganguo-huacai.webp` |
| 虾仁炒蛋 | `xiaren-chaodan.webp` |
| 油焖大虾 | `youmen-daxia.webp` |
| 蒜蓉粉丝虾 | `suanrong-fensi-xia.webp` |
| 扬州炒饭 | `yangzhou-chaofan.webp` |
| 蛋炒饭 | `dan-chaofan.webp` |
| 鸡蛋面 | `jidan-mian.webp` |

## 3. 替换目标

每张图应满足：

- 真实食物摄影风格。
- 不要卡通、不要插画、不要图标、不要水彩、不要 3D 渲染感。
- 不要文字、标题、问号、水印、Logo、菜单边框。
- 中式家常菜成品图，尽量贴合菜名。
- 构图统一：单盘/单碗成品菜，俯拍或 45 度视角。
- 背景简洁：餐桌、木桌、浅色桌面均可。
- 光线自然，食物清晰，避免过度油腻或夸张滤镜。
- 输出 WebP，建议 `512x512` 或 `768x768`；若保持当前索引不变，优先继续用 `512x512`。
- 单图建议控制在 `80KB ~ 250KB`。真实摄影通常会比当前 12KB 插画大很多，这是正常的。

## 4. 安全替换流程

### 阶段 A：备份当前插画图

替换前先复制当前版本，避免覆盖后无法回退：

```text
uploads/food-images/v1/_backup-cartoon-YYYYMMDD-HHMMSS/
```

只备份图片，不修改业务代码。

### 阶段 B：小批量替换

建议每批 5~8 张，先替换高频菜：

第一批建议：

1. 鱼香肉丝
2. 宫保鸡丁
3. 红烧肉
4. 麻婆豆腐
5. 西红柿炒鸡蛋
6. 回锅肉
7. 可乐鸡翅
8. 糖醋排骨

每批完成后生成新的 contact sheet，人工确认“真实摄影、无文字乱码、菜品可辨识”。

### 阶段 C：原文件覆盖

生成结果临时放在：

```text
uploads/food-images/v1/_generated-realistic/
```

确认后再覆盖原文件：

```text
uploads/food-images/v1/yuxiang-rousi.webp
```

保持 `index.json.imageUrl` 不变。

### 阶段 D：必要时更新尺寸

如果新图仍输出为 `512x512`，`index.json` 不需要改尺寸。

如果输出为 `768x768` 或 `1024x1024`，有两种选择：

1. 推荐：统一压到 `512x512`，避免改 `index.json`。
2. 或者更新 `index.json` 中对应 `width/height`，但需要再次做 JSON 编码和路径一致性检查。

## 5. 生成方式建议

允许方式：

- 使用本地可控图片生成工具或当前对话环境的图片生成能力，前提是不调用项目后端 `/api/food-image/generate`。
- 使用自有拍摄图或明确可商用素材，裁剪压缩成同名 WebP。

禁止方式：

- 不调用 LifeWise 后端的 `POST /api/food-image/generate`。
- 不使用已有带水印/版权不明图片。
- 不批量爬取搜索引擎图片直接入库。

单张通用提示词建议：

```text
真实食物摄影，一盘{菜名}，中式家常菜，45度俯拍，自然光，真实餐桌背景，清晰细节，色泽自然，无文字，无水印，无Logo，无人物，不要卡通，不要插画，不要3D渲染
```

负面约束：

```text
cartoon, illustration, icon, vector art, anime, watercolor, 3d render, text, watermark, logo, question marks, garbled characters, menu label, frame
```

## 6. 基本检查脚本思路

替换后检查：

1. `index.json` UTF-8 可解析。
2. 每个 `imageUrl` 文件存在。
3. 每个文件可被 Pillow 打开。
4. 格式为 WebP。
5. 尺寸与 `index.json.width/height` 一致。
6. 文件大小不为异常值：过小（如 `<30KB`）需人工复查是否仍是插画；过大（如 `>500KB`）需压缩。
7. 重新生成 contact sheet，人工检查无文字/乱码。

## 7. 当前不改动项

本计划阶段不修改：

- `Home.vue`
- 前端业务逻辑
- 后端业务逻辑
- AiMaMi/本地代理配置
- Git 提交历史

## 8. 待确认点

1. 是否接受使用当前对话环境的图片生成能力，而不是项目后端接口？
2. 新图尺寸是否强制保持 `512x512`？
3. 是否需要把第 45 个“韭菜炒鸡蛋”补回？当前 `index.json` 没有该条，但之前方案首批清单包含它。
4. 是否需要统一做“无辣/少辣”视觉版本？例如水煮鱼、辣子鸡、麻婆豆腐默认会偏红辣。
