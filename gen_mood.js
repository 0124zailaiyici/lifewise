const fs = require('fs');
let html = '<!DOCTYPE html>' +
'<html lang="zh-CN"><head><meta charset="UTF-8">' +
'<meta name="viewport" content="width=device-width,initial-scale=1.0">' +
'<title>穿搭 Mood Board 预览</title>' +
'<style>' +
'*{margin:0;padding:0;box-sizing:border-box}' +
'body{background:#f5f5f7;font-family:-apple-system,BlinkMacSystemFont,sans-serif;display:flex;justify-content:center;padding:40px 20px}' +
'.container{max-width:420px;width:100%;display:flex;flex-direction:column;gap:24px}' +
'.card{background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,.06)}' +
'.mb{padding:20px}' +
'.mb-hd{display:flex;align-items:center;gap:8px;margin-bottom:16px}' +
'.mb-ic{width:36px;height:36px;border-radius:10px;background:linear-gradient(135deg,#f093fb,#f5576c);display:flex;align-items:center;justify-content:center;font-size:18px}' +
'.mb-tl{font-size:16px;font-weight:600;color:#1d1d1f}' +
'.mb-sub{font-size:13px;color:#86868b;margin-top:1px}' +
'.tag{display:inline-block;padding:6px 14px;border-radius:20px;font-size:13px;font-weight:500;margin-bottom:16px}' +
'.tag-w{background:#e8f0fe;color:#1967d2}' +
'.tag-c{background:#fef7e0;color:#e37400}' +
'.sec-t{font-size:13px;color:#666;margin-bottom:6px}' +
'.cr{display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap}' +
'.cd{width:36px;height:36px;border-radius:50%;border:2px solid rgba(0,0,0,.06);flex-shrink:0;position:relative}' +
'.cd span{position:absolute;bottom:-16px;left:50%;transform:translateX(-50%);font-size:9px;color:#86868b;white-space:nowrap}' +
'.og{display:flex;flex-direction:column;gap:8px;margin-bottom:16px}' +
'.oi{display:flex;align-items:center;gap:12px;padding:12px;background:#f8f8fa;border-radius:12px}' +
'.oi-ic{width:40px;height:40px;border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:18px;flex-shrink:0}' +
'.oi-if{flex:1}' +
'.oi-n{font-size:14px;font-weight:500;color:#1d1d1f}' +
'.oi-d{font-size:12px;color:#86868b;margin-top:1px}' +
'.oi-c{width:14px;height:14px;border-radius:50%;border:1px solid rgba(0,0,0,.08);flex-shrink:0}' +
'.tip{padding:12px;background:#f0f7ff;border-radius:10px;font-size:13px;color:#444;line-height:1.6;border-left:3px solid #5b9ef4}' +
'.cmp{display:grid;grid-template-columns:1fr 1fr;gap:12px}' +
'.cmp-c{padding:16px;border-radius:12px;background:#f8f8fa;text-align:center}' +
'.badge{display:inline-block;padding:3px 10px;border-radius:10px;font-size:11px;margin-bottom:8px}' +
'.bad-r{background:#fee;color:#c00}' +
'.bad-g{background:#efe;color:#090}' +
'.pre{height:60px;border-radius:8px;display:flex;align-items:center;justify-content:center;font-size:12px;color:#666;background:#eee;margin-bottom:6px}' +
'.sm{font-size:11px;color:#999}' +
'</style></head><body><div class="container">' +
// Card 1
'<div class="card"><div class="mb"><div class="mb-hd"><div class="mb-ic">👔</div><div><div class="mb-tl">穿搭指南</div><div class="mb-sub">求职面试 · 专业得体</div></div></div>' +
'<span class="tag tag-w">💼 职场面试</span>' +
'<div class="sec-t">🎨 推荐配色</div><div class="cr">' +
'<div class="cd" style="background:#1a237e"><span>深蓝</span></div>' +
'<div class="cd" style="background:#424242"><span>炭灰</span></div>' +
'<div class="cd" style="background:#212121"><span>黑色</span></div>' +
'<div class="cd" style="background:#f5f5f5"><span>白色</span></div>' +
'<div class="cd" style="background:#bdbdbd"><span>浅灰</span></div>' +
'<div class="cd" style="background:#fff8e1"><span>米白</span></div></div>' +
'<div class="sec-t">👔 推荐穿搭</div><div class="og">' +
'<div class="oi"><div class="oi-ic" style="background:#e3f2fd">👕</div><div class="oi-if"><div class="oi-n">合身衬衫</div><div class="oi-d">白色或浅蓝，无褶皱</div></div><div class="oi-c" style="background:#f5f5f5"></div></div>' +
'<div class="oi"><div class="oi-ic" style="background:#e8eaf6">👖</div><div class="oi-if"><div class="oi-n">直筒西裤</div><div class="oi-d">深蓝或炭灰色</div></div><div class="oi-c" style="background:#1a237e"></div></div>' +
'<div class="oi"><div class="oi-ic" style="background:#f3e5f5">🧥</div><div class="oi-if"><div class="oi-n">修身西装外套</div><div class="oi-d">单粒扣，盖住臀部</div></div><div class="oi-c" style="background:#424242"></div></div>' +
'<div class="oi"><div class="oi-ic" style="background:#fce4ec">👞</div><div class="oi-if"><div class="oi-n">牛津皮鞋</div><div class="oi-d">黑色或深棕色</div></div><div class="oi-c" style="background:#212121"></div></div>' +
'<div class="oi"><div class="oi-ic" style="background:#fff3e0">⌚</div><div class="oi-if"><div class="oi-n">简约手表</div><div class="oi-d">银色，无logo</div></div><div class="oi-c" style="background:#9e9e9e"></div></div></div>' +
'<div class="tip">💡 提前一晚熨烫衣物，袜子与裤子同色，指甲干净、胡须修剪整齐</div></div></div>' +
// Card 2
'<div class="card"><div class="mb"><div class="mb-hd"><div class="mb-ic">👔</div><div><div class="mb-tl">穿搭指南</div><div class="mb-sub">周末出游 · 舒适休闲</div></div></div>' +
'<span class="tag tag-c">🌿 休闲出游</span>' +
'<div class="sec-t">🎨 推荐配色</div><div class="cr">' +
'<div class="cd" style="background:#bbdefb"><span>浅蓝</span></div>' +
'<div class="cd" style="background:#fff8e1"><span>米白</span></div>' +
'<div class="cd" style="background:#c8e6c9"><span>橄榄绿</span></div>' +
'<div class="cd" style="background:#f5f0e1"><span>燕麦色</span></div>' +
'<div class="cd" style="background:#fce4ec"><span>柔粉</span></div></div>' +
'<div class="sec-t">👔 推荐穿搭</div><div class="og">' +
'<div class="oi"><div class="oi-ic" style="background:#e3f2fd">👕</div><div class="oi-if"><div class="oi-n">宽松纯棉T恤</div><div class="oi-d">浅蓝或米白，可卷袖</div></div><div class="oi-c" style="background:#bbdefb"></div></div>' +
'<div class="oi"><div class="oi-ic" style="background:#f0f0e8">👖</div><div class="oi-if"><div class="oi-n">棉麻九分阔腿裤</div><div class="oi-d">燕麦色</div></div><div class="oi-c" style="background:#d7ccc8"></div></div>' +
'<div class="oi"><div class="oi-ic" style="background:#fce4ec">🧥</div><div class="oi-if"><div class="oi-n">防晒开衫</div><div class="oi-d">柔粉色</div></div><div class="oi-c" style="background:#f8bbd0"></div></div>' +
'<div class="oi"><div class="oi-ic" style="background:#f5f5f5">👟</div><div class="oi-if"><div class="oi-n">软底小白鞋</div><div class="oi-d">防滑鞋底</div></div><div class="oi-c" style="background:#f5f5f5"></div></div></div>' +
'<div class="tip">💡 裤子腰围留一指空隙更舒适；随身带小方巾可当头巾、垫坐、擦汗</div></div></div>' +
// Comparison
'<div class="card" style="padding:20px"><div style="font-size:15px;font-weight:600;margin-bottom:12px">📊 方案对比</div><div class="cmp">' +
'<div class="cmp-c"><div class="badge badge-r">❌ 原来生图</div><div class="pre">🌄 AI随机生成</div><div class="sm">跟穿搭关系不大，不确定性强</div></div>' +
'<div class="cmp-c"><div class="badge badge-g">✅ Mood Board</div><div class="pre">🎨 色块+单品卡片</div><div class="sm">信息一目了然，确定性强</div></div>' +
'</div></div></div></body></html>';
fs.writeFileSync('docs/prototypes/fashion-moodboard.html', html, 'utf-8');
console.log('Created!');
