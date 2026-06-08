<template>
  <div class="page-container">
    <div class="page-header">
      <h3>&#x1f4f0; &#x5386;&#x53f2;&#x8bb0;&#x5f55;</h3>
      <div v-if="!batchMode" class="header-actions">
        <el-button text size="small" @click="batchMode = true" style="color:#666">
          &#x6279;&#x91cf;&#x5220;&#x9664;
        </el-button>
        <div class="more-wrap">
          <button class="more-btn" @click="showToolMenu = !showToolMenu">&#x22ef;</button>
          <Transition name="fab-drop">
            <div v-if="showToolMenu" class="tool-menu" @click.stop>
              <div class="tool-menu-item" @click="repairTitles(); showToolMenu = false">
                <span>&#x1f9f9;</span><span>&#x4fee;&#x590d;&#x65e7;&#x6807;&#x9898;</span>
              </div>
            </div>
          </Transition>
        </div>
      </div>
      <template v-else>
        <el-button text size="small" @click="batchMode = false" style="color:#666">&#x53d6;&#x6d88;</el-button>
        <el-button text size="small" type="danger" @click="batchDelete" :disabled="selectedIds.size === 0">
          &#x5220;&#x9664; ({{ selectedIds.size }})
        </el-button>
      </template>
    </div>

    <div class="search-bar">
      <div class="search-row">
        <el-input v-model="keyword" placeholder="&#x641c;&#x7d22;&#x5386;&#x53f2;&#x5bf9;&#x8bdd;..." size="default" clearable
                  :prefix-icon="Search" @input="onSearch" />
        <div class="fab-wrap inline-filter scene-filter-inline">
          <div class="fab-filter" @click="showScenePanel = !showScenePanel">
            <span class="fab-filter-icon">&#x1f3f7;&#xfe0f;</span>
            <span v-if="sceneFilter" class="fab-filter-active">{{ sceneIconLabel(sceneFilter) }}</span>
            <span v-else class="fab-filter-placeholder">&#x573a;&#x666f;</span>
          </div>
          <Transition name="fab-drop">
            <div v-if="showScenePanel" class="fab-dropdown" @click.stop>
              <div v-for="s in [{key:'',icon:'&#x1f4cb;',label:'&#x5168;&#x90e8;'}, ...scenes]" :key="s.key"
                   class="fab-drop-item" :class="{ active: sceneFilter === s.key }"
                   @click="sceneFilter = s.key; showScenePanel = false">
                <span class="fd-icon">{{ s.icon }}</span>
                <span class="fd-label">{{ s.label }}</span>
              </div>
            </div>
          </Transition>
        </div>
      </div>
    </div>

    <div class="content">
      <div v-if="loading" class="loading-state">
        <div class="loading-icon">⏳</div>
        <div class="loading-text">加载中...</div>
      </div>
      <div v-else-if="filteredGroups.length === 0" class="empty-state">
        <div class="empty-icon">{{ keyword || sceneFilter ? '🔍' : '📰' }}</div>
        <div class="empty-text">{{ keyword || sceneFilter ? '没有找到匹配的对话' : '暂无历史记录' }}</div>
      </div>

      <div v-for="group in filteredGroups" :key="group.date" class="group">
        <div class="date-label">{{ group.date }}</div>
        <div v-for="conv in group.items" :key="conv.id" class="history-item"
             :class="{ 'batch-mode': batchMode, 'selected': selectedIds.has(conv.id) }"
             @click="batchMode ? toggleSelect(conv.id) : $router.push('/chat/' + conv.id)">
          <el-checkbox v-if="batchMode" @click.stop :model-value="selectedIds.has(conv.id)"
                       @change="toggleSelect(conv.id)" style="margin-right:8px" />
          <span class="icon">{{ conv.sceneIcon || '💬' }}</span>
          <div class="info">
            <div class="title" v-html="highlight(conv.title)"></div>
            <div class="meta">{{ conv.sceneLabel }} · {{ formatTime(conv.createdAt) }}</div>
          </div>
          <el-button v-if="!batchMode" text size="small" class="rename-btn" @click.stop="handleRename(conv.id, conv.title)">
            <el-icon><Edit /></el-icon>
          </el-button>
          <el-button v-if="!batchMode" text type="danger" size="small" class="del-btn" @click.stop="handleDelete(conv.id)">
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
import { getConversations, deleteConversation, renameConversation, repairConversationTitles } from '../api'
import { HomeFilled, Timer, Star, User, Delete, Edit, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(true)
const groups = ref([])
const keyword = ref('')
const sceneFilter = ref('')
const batchMode = ref(false)
const showScenePanel = ref(false)
const showToolMenu = ref(false)
const selectedIds = ref(new Set())
const repairing = ref(false)

const scenes = [
  { key: 'cooking', icon: '🍳', label: '做饭' },
  { key: 'shopping', icon: '🛒', label: '买菜' },
  { key: 'repair', icon: '🔧', label: '修理' },
  { key: 'housework', icon: '🏠', label: '家务' },
  { key: 'health', icon: '🌞', label: '健康' },
  { key: 'fashion', icon: '👔', label: '穿搭' },
  { key: 'pet', icon: '🐾', label: '宠物' },
  { key: 'writing', icon: '✍️', label: '写作' },
]

onMounted(loadConversations)

async function loadConversations() {
  loading.value = true
  try {
    const res = await getConversations()
    const list = res.data || []
    const map = {}
    list.forEach(conv => {
      const date = conv.createdAt ? new Date(conv.createdAt).toLocaleDateString('zh-CN') : '\u5176\u4ed6'
      if (!map[date]) map[date] = { date, items: [] }
      map[date].items.push(conv)
    })
    groups.value = Object.values(map)
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

const filteredGroups = computed(() => {
  let result = groups.value
  const kw = keyword.value.trim().toLowerCase()
  const sf = sceneFilter.value

  if (sf) {
    result = result.map(g => ({
      ...g,
      items: g.items.filter(c => c.scene === sf)
    })).filter(g => g.items.length > 0)
  }

  if (kw) {
    result = result.map(g => ({
      ...g,
      items: g.items.filter(c => (c.title || '').toLowerCase().includes(kw))
    })).filter(g => g.items.length > 0)
  }

  return result
})

function sceneIconLabel(key) {
  const s = scenes.find(x => x.key === key)
  return s ? s.icon + ' ' + s.label : ''
}

function onSearch() {}

function highlight(text) {
  const kw = keyword.value.trim()
  if (!kw || !text) return text
  const idx = text.toLowerCase().indexOf(kw.toLowerCase())
  if (idx === -1) return text
  return text.substring(0, idx) + '<mark>' + text.substring(idx, idx + kw.length) + '</mark>' + text.substring(idx + kw.length)
}

function toggleSelect(id) {
  const s = new Set(selectedIds.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selectedIds.value = s
}

async function repairTitles() {
  if (repairing.value) return
  repairing.value = true
  try {
    const res = await repairConversationTitles()
    const count = res.data?.repaired || 0
    ElMessage.success(count > 0 ? `\u5df2\u4fee\u590d ${count} \u4e2a\u65e7\u6807\u9898` : '\u6ca1\u6709\u9700\u8981\u4fee\u590d\u7684\u65e7\u6807\u9898')
    await loadConversations()
  } catch (e) {
    console.error(e)
    const msg = e?.response?.data?.message || e?.message || '\u4fee\u590d\u5931\u8d25'
    ElMessage.error(msg)
  } finally {
    repairing.value = false
  }
}

async function batchDelete() {
  if (selectedIds.value.size === 0) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.size} 条对话吗？`, '批量删除', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
    for (const id of selectedIds.value) {
      try { await deleteConversation(id) } catch {}
    }
    ElMessage.success(`已删除 ${selectedIds.value.size} 条对话`)
    groups.value = groups.value.map(g => ({
      ...g, items: g.items.filter(c => !selectedIds.value.has(c.id))
    })).filter(g => g.items.length > 0)
    selectedIds.value = new Set()
    batchMode.value = false
  } catch {}
}

async function handleRename(id, currentTitle) {
  const { value: newTitle } = await ElMessageBox.prompt('输入新标题', '重命名对话', {
    inputValue: currentTitle,
    confirmButtonText: '确定', cancelButtonText: '取消',
    inputValidator: v => v?.trim() ? true : '标题不能为空'
  })
  if (newTitle?.trim()) {
    try {
      await renameConversation(id, newTitle.trim())
      ElMessage.success('已重命名')
      const conv = groups.value.flatMap(g => g.items).find(c => c.id === id)
      if (conv) conv.title = newTitle.trim()
    } catch (e) { ElMessage.error('重命名失败') }
  }
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定删除此对话？', '删除', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
    await deleteConversation(id)
    ElMessage.success('已删除')
    groups.value = groups.value.map(g => ({
      ...g, items: g.items.filter(c => c.id !== id)
    })).filter(g => g.items.length > 0)
  } catch {}
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.page-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 12px;
  border-bottom: 1px solid #e0e0e0;
  background: linear-gradient(180deg, #f0fdf4 0%, #fff 100%);
}
.page-header h3 { font-size: 17px; font-weight: 600; color: #111; margin: 0; }
.header-actions { display: flex; align-items: center; gap: 8px; position: relative; }
.more-wrap { position: relative; }
.more-btn {
  width: 32px; height: 32px; border-radius: 50%;
  border: 1px solid #dcfce7; background: #f0fdf4; color: #16a34a;
  display: grid; place-items: center; font-size: 18px; line-height: 1;
  cursor: pointer;
}
.more-btn:active { transform: scale(.94); }
.tool-menu {
  position: absolute; right: 0; top: calc(100% + 8px); z-index: 120;
  width: 150px; padding: 8px;
  background: rgba(255,255,255,.98); border: 1px solid #e5e7eb; border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0,0,0,.12);
}
.tool-menu-item {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 12px; border-radius: 12px;
  font-size: 13px; color: #0f766e; cursor: pointer;
}
.tool-menu-item:hover { background: #f0fdf4; color: #16a34a; }

.search-bar { padding: 0 20px 12px; }
.search-row { display: flex; align-items: center; gap: 8px; }
.search-row .el-input { flex: 1; min-width: 0; }
.search-bar :deep(.el-input__wrapper) { border-radius: 20px; }

.scene-filter-inline { position: relative; z-index: 40; flex-shrink: 0; }
.fab-filter {
  height: 36px; min-width: 78px; box-sizing: border-box;
  display: flex; align-items: center; justify-content: center; gap: 4px;
  padding: 0 12px; border-radius: 18px;
  background: #f0fdf4; border: 1px solid #bbf7d0; color: #16a34a;
  cursor: pointer; transition: .15s;
}
.fab-filter:hover { background: #dcfce7; border-color: #86efac; }
.fab-filter:active { transform: scale(.95); }
.fab-filter-icon { font-size: 14px; line-height: 1; }
.fab-filter-active { font-size: 11px; color: #16a34a; font-weight: 600; max-width: 68px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fab-filter-placeholder { font-size: 12px; color: #16a34a; font-weight: 600; }
.fab-dropdown {
  position: absolute; right: 0; top: calc(100% + 6px); z-index: 60;
  min-width: 110px; padding: 6px;
  background: rgba(255,255,255,.97); backdrop-filter: blur(12px);
  border: 1px solid #e5e7eb; border-radius: 14px;
  box-shadow: 0 4px 20px rgba(0,0,0,.12);
}
.fab-drop-item {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 12px; border-radius: 10px;
  cursor: pointer; font-size: 13px; color: #444; white-space: nowrap; transition: .1s;
}
.fab-drop-item:hover { background: #f0fdf4; }
.fab-drop-item.active { background: #f0fdf4; color: #16a34a; font-weight: 600; }
.fd-icon { font-size: 16px; }
.fd-label { font-size: 12px; }
.fab-drop-enter-active, .fab-drop-leave-active { transition: all .15s ease; }
.fab-drop-enter-from, .fab-drop-leave-to { opacity: 0; transform: translateY(-4px) scale(.96); }

.content { padding: 0 20px calc(80px + env(safe-area-inset-bottom, 0px)); }
.group { margin-bottom: 24px; }
.date-label { font-size: 12px; color: #777; margin-bottom: 10px; font-weight: 600; padding-top: 4px; }
.history-item {
  display: flex; align-items: center; padding: 14px;
  background: #fafcfa; border-radius: 14px;
  border: 1px solid #f0f0f0; margin-bottom: 10px;
  cursor: pointer; transition: .15s;
}
.history-item:active { transform: scale(.98); }
.history-item.batch-mode { cursor: default; }
.history-item.batch-mode:active { transform: none; }
.history-item.selected { background: #f0fdf4; border-color: #bbf7d0; }
.icon { font-size: 26px; margin-right: 14px; }
.info { flex: 1; min-width: 0; }
.title { font-size: 14px; font-weight: 500; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.title :deep(mark) { background: #fde68a; color: #333; padding: 0 2px; border-radius: 2px; }
.meta { font-size: 12px; color: #888; margin-top: 3px; }
.del-btn { flex-shrink: 0; margin-left: 4px; }
.rename-btn { flex-shrink: 0; color: #999; }
.rename-btn:hover { color: #22c55e; }
.loading-state, .empty-state { text-align: center; padding: 80px 20px; }
.loading-icon { font-size: 48px; margin-bottom: 12px; }
.loading-text { font-size: 14px; color: #777; }

.bottom-tabs {
  position: fixed; bottom: 0; left: 50%; transform: translateX(-50%);
  width: 100%; max-width: 480px; height: calc(64px + env(safe-area-inset-bottom, 0px));
  background: #fff; border-top: 1px solid #e5e7eb; display: flex;
  padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px));
  box-shadow: 0 -2px 12px rgba(0,0,0,0.06); z-index: 100;
}
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: #999; cursor: pointer; gap: 2px; }
.tab.active { color: #22c55e; }
.tab .el-icon { font-size: 20px; }

@media (max-width: 380px) {
  .search-row { flex-wrap: wrap; }
  .scene-filter-inline { margin-left: auto; }
}
</style>

