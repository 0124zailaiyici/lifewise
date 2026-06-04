<template>
  <div class="page-container">
    <div class="page-header">
      <h3>📋 历史记录</h3>
    </div>

    <div class="content">
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="groups.length === 0" class="empty">暂无历史记录</div>

      <div v-for="group in groups" :key="group.date" class="group">
        <div class="date-label">{{ group.date }}</div>
        <div v-for="conv in group.items" :key="conv.id" class="history-item"
             @click="$router.push('/chat/' + conv.id)">
          <span class="icon">{{ conv.sceneIcon || '💬' }}</span>
          <div class="info">
            <div class="title">{{ conv.title }}</div>
            <div class="meta">{{ conv.sceneLabel }} · {{ formatTime(conv.createdAt) }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="bottom-tabs">
      <div class="tab" @click="$router.push('/home')"><el-icon><HomeFilled /></el-icon><span>首页</span></div>
      <div class="tab active"><el-icon><Timer /></el-icon><span>历史</span></div>
      <div class="tab" @click="$router.push('/favorites')"><el-icon><Star /></el-icon><span>收藏</span></div>
      <div class="tab" @click="$router.push('/profile')"><el-icon><User /></el-icon><span>我的</span></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getConversations } from '../api'
import { HomeFilled, Timer, Star, User } from '@element-plus/icons-vue'

const loading = ref(true)
const groups = ref([])

onMounted(async () => {
  try {
    const res = await getConversations()
    const list = res.data || []
    const map = {}
    list.forEach(conv => {
      const date = conv.createdAt ? new Date(conv.createdAt).toLocaleDateString('zh-CN') : '其他'
      if (!map[date]) map[date] = { date, items: [] }
      map[date].items.push(conv)
    })
    groups.value = Object.values(map)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
})

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.page-header { text-align: center; padding: 16px 20px; border-bottom: 1px solid #eee; }
.page-header h3 { font-size: 17px; font-weight: 600; color: #333; }
.content { padding: 16px 20px 80px; }
.group { margin-bottom: 20px; }
.date-label { font-size: 12px; color: #999; margin-bottom: 8px; }
.history-item { display: flex; align-items: center; padding: 12px; background: #fafafa; border-radius: 12px; margin-bottom: 8px; cursor: pointer; }
.icon { font-size: 24px; margin-right: 12px; }
.info { flex: 1; }
.title { font-size: 14px; font-weight: 500; color: #333; }
.meta { font-size: 12px; color: #999; margin-top: 2px; }
.loading, .empty { text-align: center; padding: 60px 0; color: #999; font-size: 14px; }
.bottom-tabs { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 480px; height: 56px; background: #fff; border-top: 1px solid #eee; display: flex; }
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: #999; cursor: pointer; gap: 2px; }
.tab.active { color: #22c55e; }
.tab .el-icon { font-size: 20px; }
</style>
