<template>
  <div class="page-container">
    <div class="page-header">
      <h3>📋 历史记录</h3>
    </div>

    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索历史对话..." size="default" clearable
                prefix-icon="Search" @input="filterList" />
    </div>

    <div class="content">
      <div v-if="loading" class="loading-state">
        <div class="loading-text">加载中...</div>
      </div>
      <div v-else-if="filteredGroups.length === 0" class="empty-state">
        <div class="empty-icon">{{ keyword ? '🔍' : '📥' }}</div>
        <div class="empty-text">{{ keyword ? '没有找到匹配的对话' : '暂无历史记录' }}</div>
      </div>

      <div v-for="group in filteredGroups" :key="group.date" class="group">
        <div class="date-label">{{ group.date }}</div>
        <div v-for="conv in group.items" :key="conv.id" class="history-item"
             @click="$router.push('/chat/' + conv.id)">
          <span class="icon">{{ conv.sceneIcon || '💬' }}</span>
          <div class="info">
            <div class="title" v-html="highlight(conv.title)"></div>
            <div class="meta">{{ conv.sceneLabel }} · {{ formatTime(conv.createdAt) }}</div>
          </div>
          <el-button text size="small" class="rename-btn" @click.stop="handleRename(conv.id, conv.title)"><el-icon><Edit /></el-icon></el-button>
          <el-button text type="danger" size="small" class="del-btn" @click.stop="handleDelete(conv.id)">
            <el-icon><Delete /></el-icon>
          </el-button>
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
import { ref, computed, onMounted } from 'vue'
import { getConversations, deleteConversation, renameConversation } from '../api'
import { HomeFilled, Timer, Star, User, Delete, Edit } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(true)
const groups = ref([])
const keyword = ref('')

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
  } catch (e) { console.error(e) }
  finally { loading.value = false }
})

const filteredGroups = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return groups.value
  return groups.value.map(g => ({
    ...g,
    items: g.items.filter(c => (c.title || '').toLowerCase().includes(kw))
  })).filter(g => g.items.length > 0)
})

function filterList() {} // reactive via computed

function highlight(text) {
  const kw = keyword.value.trim()
  if (!kw || !text) return text
  const idx = text.toLowerCase().indexOf(kw.toLowerCase())
  if (idx === -1) return text
  return text.substring(0, idx) + '<mark>' + text.substring(idx, idx + kw.length) + '</mark>' + text.substring(idx + kw.length)
}

async function handleRename(id, currentTitle) {
  const { value: newTitle } = await ElMessageBox.prompt('输入新标题', '重命名对话', {
    inputValue: currentTitle,
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValidator: v => v?.trim() ? true : '标题不能为空'
  })
  if (newTitle?.trim()) {
    try {
      await renameConversation(id, newTitle.trim())
      ElMessage.success('已重命名')
      const conv = groups.value.flatMap(g => g.items).find(c => c.id === id)
      if (conv) conv.title = newTitle.trim()
    } catch (e) {
      ElMessage.error('重命名失败')
    }
  }
}

async function handleDelete(id) {
  try {
    await deleteConversation(id)
    ElMessage.success('已删除')
    groups.value = groups.value.map(g => ({
      ...g,
      items: g.items.filter(c => c.id !== id)
    })).filter(g => g.items.length > 0)
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.page-header { text-align: center; padding: 18px 20px 12px; border-bottom: 1px solid #e0e0e0; }
.page-header h3 { font-size: 17px; font-weight: 600; color: #111; }

.search-bar { padding: 12px 20px; }
.search-bar :deep(.el-input__wrapper) { border-radius: 20px; }

.content { padding: 0 20px 80px; }

.group { margin-bottom: 24px; }
.date-label { font-size: 12px; color: #777; margin-bottom: 10px; font-weight: 600; padding-top: 4px; }
.history-item { display: flex; align-items: center; padding: 14px; background: #f5f5f5; border-radius: 14px; border: 1px solid #eee; margin-bottom: 10px; cursor: pointer; transition: .15s; }
.history-item:active { transform: scale(.98); }
.icon { font-size: 26px; margin-right: 14px; }
.info { flex: 1; }
.title { font-size: 14px; font-weight: 500; color: #333; }
.title :deep(mark) { background: #fde68a; color: #333; padding: 0 2px; border-radius: 2px; }
.meta { font-size: 12px; color: #888; margin-top: 3px; }
.del-btn { flex-shrink: 0; margin-left: 4px; }
.rename-btn { flex-shrink: 0; color: #999; }
.rename-btn:hover { color: #22c55e; }

.loading-state, .empty-state { text-align: center; padding: 80px 20px; }
.loading-text { font-size: 48px; margin-bottom: 12px; }
.empty-text { font-size: 14px; color: #777; }

.bottom-tabs { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 480px; height: 60px; background: #fff; border-top: 1px solid #f0f0f0; display: flex; padding-bottom: 4px; }
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: #999; cursor: pointer; gap: 2px; }
.tab.active { color: #22c55e; }
.tab .el-icon { font-size: 20px; }
</style>