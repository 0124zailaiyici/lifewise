<template>
  <router-view v-slot="{ Component }">
    <transition name="page" mode="out-in"> <component :is="Component" />
    </transition>
  </router-view>
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
html, body {
  width: 100%; max-width: 100%;
  overflow-x: hidden;
  overscroll-behavior: none;
  -webkit-overflow-scrolling: touch;
}
body {
  font-family: "Avenir Next", -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: var(--paper);
  -webkit-font-smoothing: antialiased;
  /* 阻止 iOS 上拉弹性滚动暴露背景 */
  position: fixed;
  width: 100%;
  height: 100%;
  overflow: hidden;
}
#app {
  width: 100%;
  height: 100dvh;
  height: 100vh;
  overflow: hidden;
  position: relative;
}
.page-container {
  width: 100%; max-width: 480px; margin: 0 auto;
  height: 100dvh;
  height: 100vh;
  background: var(--paper);
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 移动端安全区域 */
@supports (padding-top: env(safe-area-inset-top)) {
  .page-container { padding-top: env(safe-area-inset-top); }
  .bottom-tabs { padding-bottom: calc(8px + env(safe-area-inset-bottom)) !important; }
  .input-area { padding-bottom: calc(12px + env(safe-area-inset-bottom)) !important; }
}
/* 新版 dvh 支持 */
@supports (height: 100dvh) {
  #app, .page-container { height: 100dvh; }
}

/* 移动端点击高亮去除 */
* { -webkit-tap-highlight-color: transparent; }

/* 页面切换动画 — 从右滑入 */
.page-enter-active { transition: all .28s cubic-bezier(.4, 0, .2, 1); }
.page-leave-active { transition: all .2s cubic-bezier(.4, 0, .2, 1); }
.page-enter-from { opacity: 0; transform: translateX(24px); }
.page-leave-to { opacity: 0; transform: translateX(-12px); }

/* iOS 回弹滚动支持 */
.messages, .content, .scroll-area {
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: none;
  overflow-x: hidden;
}
.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* 滚动条 — 移动端自动隐藏 */
@media (hover: none) and (pointer: coarse) {
  ::-webkit-scrollbar { width: 0; height: 0; }
}
@media (hover: hover) {
  ::-webkit-scrollbar { width: 4px; }
  ::-webkit-scrollbar-track { background: transparent; }
  ::-webkit-scrollbar-thumb { background: var(--soft); border-radius: 4px; }
}

/* 触摸目标最小尺寸 */
button, .el-button, .chip, .scene-card, .conv-item, .tab { min-height: 44px; }

/* 禁止选择文本（UI 元素） */
.no-select { -webkit-user-select: none; user-select: none; }

/* 阻止 iOS 长按弹出菜单 */
img, a, button { -webkit-touch-callout: none; }

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
.el-dialog { border-radius: var(--radius-lg) !important; max-width: 92vw !important; margin: 0 auto !important; }
.el-dialog__header { padding: 20px 24px 8px !important; }
.el-dialog__body { padding: 12px 24px 20px !important; }
.el-dialog__footer { padding: 12px 24px 20px !important; }

/* 移动端适配：小屏时 el-dialog 全宽 */
@media (max-width: 420px) {
  .el-dialog { --el-dialog-width: 94% !important; border-radius: var(--radius-md) !important; }
  .el-dialog__header { padding: 16px 18px 6px !important; }
  .el-dialog__body { padding: 8px 18px 16px !important; }
  .el-dialog__footer { padding: 8px 18px 16px !important; }
}
</style>

