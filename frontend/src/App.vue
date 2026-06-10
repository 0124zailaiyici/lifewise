<template>
  <router-view />
</template>

<style>
/* ===== Claude 暖色主题 ===== */
:root {
  --paper: #f7f1e8;
  --paper-deep: #eee4d6;
  --card: rgba(255, 252, 246, 0.86);
  --card-solid: #fffaf1;
  --ink: #2d2924;
  --muted: #7b7064;
  --soft: #a69380;
  --line: rgba(87, 68, 49, 0.12);
  --accent: #8d5f3f;
  --accent-deep: #68432b;
  --sage: #7d8b6f;
  --warm-shadow: 0 18px 55px rgba(80, 58, 38, 0.14);
  --small-shadow: 0 8px 24px rgba(80, 58, 38, 0.10);
  --radius-xl: 28px;
  --radius-lg: 22px;
  --radius-md: 16px;
  color-scheme: light;
}

* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { width: 100%; max-width: 100%; overflow-x: hidden; }
body {
  font-family: "Avenir Next", -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: var(--paper);
  -webkit-font-smoothing: antialiased;
}
.page-container {
  width: 100%; max-width: 480px; margin: 0 auto; min-height: 100vh;
  background: var(--paper);
  position: relative; overflow-x: hidden;
}

/* 移动端安全区域 */
@supports (padding-top: env(safe-area-inset-top)) {
  .page-container { padding-top: env(safe-area-inset-top); }
  .bottom-tabs { padding-bottom: calc(8px + env(safe-area-inset-bottom)) !important; }
  .input-area { padding-bottom: calc(12px + env(safe-area-inset-bottom)) !important; }
}

/* 移动端点击高亮去除 */
* { -webkit-tap-highlight-color: transparent; }

/* 页面切换动画 */
.page-enter-active, .page-leave-active { transition: opacity .2s ease, transform .2s ease; }
.page-enter-from { opacity: 0; transform: translateX(12px); }
.page-leave-to { opacity: 0; transform: translateX(-12px); }

/* 滚动条 */
::-webkit-scrollbar { width: 4px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: var(--soft); border-radius: 4px; }

/* 移动端优化 */
html { -webkit-text-size-adjust: 100%; }
body { -webkit-overflow-scrolling: touch; overscroll-behavior: none; }
.page-container { overscroll-behavior: contain; }
/* 触摸目标最小尺寸 */
button, .el-button, .chip, .scene-card, .conv-item, .tab { min-height: 44px; }
/* Element Plus 覆盖 — 暖色主题 */
.el-button--success {
  --el-button-success-bg-color: var(--accent);
  --el-button-success-border-color: var(--accent);
  --el-button-success-hover-bg-color: var(--accent-deep);
  border-radius: 16px;
}
.el-button--primary {
  --el-button-primary-bg-color: var(--accent);
  --el-button-primary-border-color: var(--accent);
  --el-button-primary-hover-bg-color: var(--accent-deep);
}
.el-message { border-radius: 12px !important; }
.el-input__wrapper { border-radius: 14px !important; background: var(--card-solid) !important; border: 1px solid var(--line) !important; box-shadow: none !important; }
.el-input__wrapper:hover { border-color: var(--accent) !important; }
.el-input__wrapper.is-focus { border-color: var(--accent) !important; box-shadow: 0 0 0 2px rgba(141,95,63,0.12) !important; }
.el-select .el-input__wrapper { border-radius: 12px !important; }
.el-dialog { border-radius: var(--radius-lg) !important; }
.el-dialog__header { padding: 20px 24px 8px !important; }
.el-dialog__body { padding: 12px 24px 20px !important; }
.el-dialog__footer { padding: 12px 24px 20px !important; }
</style>