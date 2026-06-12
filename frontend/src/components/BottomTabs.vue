<template>
  <nav class="bottom-tabs">
    <div class="tab" :class="{ active: active === 'home' }" @click="go('home')">
      <el-icon><HomeFilled /></el-icon><span>首页</span>
    </div>
    <div class="tab" :class="{ active: active === 'history' }" @click="go('history')">
      <el-icon><Timer /></el-icon><span>历史</span>
    </div>
    <div class="tab" :class="{ active: active === 'favorites' }" @click="go('favorites')">
      <el-icon><Star /></el-icon><span>收藏</span>
    </div>
    <div class="tab" :class="{ active: active === 'profile' }" @click="go('profile')">
      <el-icon><User /></el-icon><span>我的</span>
    </div>
  </nav>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { HomeFilled, Timer, Star, User } from '@element-plus/icons-vue'

const props = defineProps({
  active: { type: String, default: 'home' }
})

const router = useRouter()

function go(page) {
  if (props.active === page) return
  router.push('/' + page)
}
</script>

<style scoped>
.bottom-tabs {
  position: fixed; bottom: 0; left: 50%; transform: translateX(-50%);
  width: 100%; max-width: 480px;
  height: calc(56px + env(safe-area-inset-bottom, 0px));
  background: var(--card-solid);
  border-top: 1px solid var(--line);
  display: flex;
  padding-bottom: calc(4px + env(safe-area-inset-bottom, 0px));
  box-shadow: 0 -2px 12px rgba(80,58,38,0.08);
  z-index: 100;
}
.tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--muted);
  cursor: pointer;
  gap: 1px;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
  transition: color .15s;
}
.tab:active {
  transform: scale(.95);
}
.tab.active {
  color: var(--accent);
}
.tab.active .el-icon {
  filter: drop-shadow(0 0 4px rgba(141,95,63,0.3));
}
.tab .el-icon {
  font-size: 20px;
  transition: transform .15s;
}
</style>
