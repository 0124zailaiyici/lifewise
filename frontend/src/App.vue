<template>
  <router-view v-slot="{ Component, route }">
    <transition name="page" mode="out-in">
      <component :is="Component" :key="route.path" />
    </transition>
  </router-view>
</template>

<script setup>
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; -webkit-font-smoothing: antialiased; }
.page-container { max-width: 480px; margin: 0 auto; min-height: 100vh; background: #fff; position: relative; }

/* 页面切换动画 */
.page-enter-active, .page-leave-active { transition: opacity .2s ease, transform .2s ease; }
.page-enter-from { opacity: 0; transform: translateX(12px); }
.page-leave-to { opacity: 0; transform: translateX(-12px); }

/* 滚动条美化 */
::-webkit-scrollbar { width: 4px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #ddd; border-radius: 4px; }

/* Element Plus 覆盖 */
.el-button--success { --el-button-success-bg-color: #22c55e; --el-button-success-border-color: #22c55e; --el-button-success-hover-bg-color: #16a34a; }
.el-message { border-radius: 12px !important; }
</style>
