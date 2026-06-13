<template>
  <div class="page-container">
    <div class="page-header">
      <div style="display:flex;align-items:center;gap:8px">
      <h3>📚 常识库</h3>
      <el-button text size="small" @click="handleExport" style="color:var(--accent)">⬇ 导出</el-button>
      <el-button text size="small" @click="triggerImport" style="color:var(--accent)">⬆ 导入</el-button>
      <input ref="importInput" type="file" accept=".json" style="display:none" @change="handleImport" />
    </div>
    </div>

    <div class="search-bar">
      <div class="search-row">
        <el-input v-model="keyword" placeholder="搜索常识库..." size="default" clearable
                  prefix-icon="Search" @keyup.enter="doSearch" />
        <div class="fab-wrap inline-filter">
          <div class="fab-filter" @click="sceneFilterPanel = !sceneFilterPanel">
            <span class="fab-filter-icon">🏷️</span>
            <span v-if="sceneFilter" class="fab-filter-active">{{ sceneLabel(sceneFilter) }}</span>
            <span v-else class="fab-filter-placeholder">场景</span>
          </div>
          <Transition name="fab-drop">
            <div v-if="sceneFilterPanel" class="fab-dropdown" @click.stop>
              <div v-for="s in [{key:'',icon:'📋',label:'全部'}, ...scenes]" :key="s.key"
                   class="fab-drop-item" :class="{ active: sceneFilter === s.key }"
                   @click="sceneFilter = s.key; sceneFilterPanel = false; fetchData()">
                <span class="fd-icon">{{ s.icon }}</span>
                <span class="fd-label">{{ s.label }}</span>
              </div>
            </div>
          </Transition>
        </div>
      </div>
    </div>

    <div class="stats-row" v-if="total > 0">
      <span>共 {{ total }} 条常识</span>
      <span class="hit-badge">⚡ 命中缓存即答，无需调 API</span>
    </div>

    <div class="content">
      <div v-if="loading" class="loading-state">加载中...</div>
      <div v-else-if="list.length === 0" class="empty-state">
        <div class="empty-icon">{{ keyword ? '🔍' : '📚' }}</div>
        <div class="empty-text">{{ keyword ? '没有找到匹配的常识' : '常识库为空，开始对话后会自动积累' }}</div>
      </div>

      <div v-for="item in list" :key="item.id" class="kb-item">
        <div class="kb-header">
          <span class="kb-scene-tag">{{ sceneLabel(item.scene) }}</span>
          <span class="kb-q">{{ item.question }}</span>
        </div>
        <div class="kb-answer">{{ item.answer.substring(0, 120) }}{{ item.answer.length > 120 ? '...' : '' }}</div>
        <div class="kb-footer">
          <span class="kb-helpful">👍 有用 {{ item.helpfulCount || 0 }} 次</span>
          <el-button text size="small" type="primary" @click="markHelpful(item.id)">有用</el-button>
          <el-button text size="small" type="primary" @click="openEdit(item)">编辑</el-button>
          <el-button text size="small" type="danger" @click="handleDelete(item.id)">删除</el-button>
          <span class="kb-date">{{ formatTime(item.createdAt) }}</span>
        </div>
      </div>
    </div>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editDialog.visible" title="编辑常识" width="90%" :close-on-click-modal="false">
      <el-form label-width="60px">
        <el-form-item label="问题">
          <el-input v-model="editDialog.question" placeholder="问题" />
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="editDialog.scene" placeholder="场景" style="width:100%">
            <el-option v-for="s in scenes" :key="s.key" :label="s.label" :value="s.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="回答">
          <el-input v-model="editDialog.answer" type="textarea" :rows="6" placeholder="回答内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <BottomTabs active="profile" />
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue"
import { ElMessage } from "element-plus"
import { searchKnowledge, markHelpful as markHelpfulApi, deleteKnowledge, updateKnowledge, exportKnowledge, importKnowledge } from "../api"
import BottomTabs from '../components/BottomTabs.vue'

const keyword = ref("")
const sceneFilter = ref("")
const sceneFilterPanel = ref(false)
const list = ref([])
const total = ref(0)
const loading = ref(true)
const editDialog = ref({ visible: false, id: null, question: '', answer: '', scene: '' })

function openEdit(item) {
  editDialog.value = { visible: true, id: item.id, question: item.question, answer: item.answer, scene: item.scene || '' }
}

async function saveEdit() {
  const d = editDialog.value
  if (!d.question.trim() || !d.answer.trim()) { ElMessage.warning('问题和回答不能为空'); return }
  try {
    await updateKnowledge(d.id, { question: d.question, answer: d.answer, scene: d.scene })
    ElMessage.success('保存成功')
    d.visible = false
    fetchData()
  } catch { ElMessage.error('保存失败') }
}

const scenes = [
  { key: "cooking", label: "做饭助手" }, { key: "shopping", label: "买菜指南" },
  { key: "repair", label: "修理指南" }, { key: "housework", label: "家务技巧" },
  { key: "health", label: "健康常识" }, { key: "fashion", label: "穿搭指南" },
  { key: "etiquette", label: "社交礼仪" }, { key: "pet", label: "宠物照顾" },
  { key: "writing", label: "写作助手" }, { key: "mealplan", label: "食谱推荐" }
]

function sceneLabel(s) { return scenes.find(x => x.key === s)?.label || s || "其他" }
function sceneIconLabel(key) {
  const s = scenes.find(x => x.key === key)
  return s ? s.icon + ' ' + s.label : ''
}

async function fetchData() {
  loading.value = true
  try {
    const res = await searchKnowledge(keyword.value.trim(), sceneFilter.value)
    list.value = res.data || []
    total.value = list.value.length
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

function doSearch() { fetchData() }

async function markHelpful(id) {
  try {
    await markHelpfulApi(id)
    const item = list.value.find(x => x.id === id)
    if (item) item.helpfulCount = (item.helpfulCount || 0) + 1
    ElMessage.success("感谢反馈")
  } catch (e) { ElMessage.error("操作失败") }
}

function formatTime(t) {
  if (!t) return ""
  return new Date(t).toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" })
}

async function handleExport() {
    try {
      const res = await exportKnowledge()
      const blob = new Blob([res.data], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'lifewise-knowledge-' + new Date().toISOString().slice(0,10) + '.json'
      document.body.appendChild(a); a.click(); document.body.removeChild(a)
      URL.revokeObjectURL(url)
      ElMessage.success('导出成功')
    } catch(e) { ElMessage.error('导出失败') }
  }

  function triggerImport() { importInput.value?.click() }

  async function handleImport(e) {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const text = await file.text()
      const data = JSON.parse(text)
      if (!Array.isArray(data)) { ElMessage.error('文件格式不对'); return }
      const res = await importKnowledge(data)
      ElMessage.success(res.data?.message || '导入成功')
      fetchData()
    } catch { ElMessage.error('导入失败，请检查文件格式') }
    e.target.value = ''
  }

  async function handleDelete(id) {
  try {
    await deleteKnowledge(id)
    ElMessage.success("已删除")
    fetchData()
  } catch (e) {
    ElMessage.error("删除失败")
  }
}

onMounted(fetchData)
</script>

<style scoped>
.page-container { background: var(--paper); min-height: 100vh; padding-bottom: 80px; }
.page-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 18px 8px; position: relative; }
.page-header h3 { font-size: 17px; font-weight: 600; color: var(--ink); margin: 0; }
.header-actions { display: flex; align-items: center; gap: 8px; }

/* Search */
.search-bar { padding: 0 18px 8px; }
.search-row { display: flex; gap: 8px; align-items: center; }
.search-row .el-input { flex: 1; }
.search-bar :deep(.el-input__wrapper) { border-radius: 24px; box-shadow: 0 1px 4px rgba(80,58,38,0.08); }
.inline-filter .fab-dropdown { right: 0; left: auto; }

/* Stats */
.fab-wrap { position: relative; }
.fab-filter {
  display: flex; align-items: center; gap: 4px;
  padding: 0 14px; height: 36px; border-radius: 20px;
  background: var(--paper); border: 1px solid var(--line); color: var(--accent-deep);
  cursor: pointer; transition: .15s; white-space: nowrap;
}
.fab-filter:hover { background: var(--paper-deep); }
.fab-filter:active { transform: scale(.95); }
.fab-filter-icon { font-size: 13px; }
.fab-filter-active { font-size: 11px; color: var(--accent-deep); font-weight: 600; max-width: 68px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fab-filter-placeholder { font-size: 12px; color: var(--accent-deep); font-weight: 600; }
.fab-dropdown {
  position: absolute; top: 44px; right: 0; z-index: 50;
  background: rgba(255,252,246,0.97); backdrop-filter: blur(12px);
  border: 1px solid var(--line); border-radius: 14px;
  box-shadow: 0 4px 20px rgba(80,58,38,0.14); padding: 6px;
}
.fab-drop-item {
  padding: 7px 12px; border-radius: 10px;
  cursor: pointer; font-size: 13px; color: var(--ink); white-space: nowrap; transition: .1s;
}
.fab-drop-item:hover { background: var(--paper); }
.fab-drop-item.active { background: var(--paper); color: var(--accent-deep); font-weight: 600; }
.fd-icon { font-size: 16px; }
.fab-drop-enter-active, .fab-drop-leave-active { transition: .15s ease; }
.fab-drop-enter-from, .fab-drop-leave-to { opacity: 0; transform: translateY(-6px); }

.stats-row { display: flex; align-items: center; gap: 8px; padding: 0 18px 10px; }
.hit-badge { font-size: 12px; color: var(--accent-deep); }

/* Content */
.content { padding: 0 18px; }
.loading-state { text-align: center; padding: 40px; color: var(--muted); font-size: 14px; }

/* Knowledge items */
.kb-item {
  background: var(--card); border-radius: 20px;
  border: 1px solid var(--line); padding: 16px; margin-bottom: 10px;
  transition: .15s;
}
.kb-item:hover { border-color: var(--accent); box-shadow: 0 4px 16px rgba(141,95,63,0.1); }
.kb-header { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 8px; }
.kb-scene-tag {
  padding: 2px 8px; border-radius: 12px; font-size: 11px;
  background: var(--paper); color: var(--accent-deep); flex-shrink: 0;
}
.kb-q { font-size: 14px; font-weight: 600; color: var(--ink); flex: 1; }
.kb-answer { font-size: 13px; color: var(--muted); line-height: 1.5; }
.kb-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; }
.kb-helpful { font-size: 12px; color: var(--muted); }
.kb-date { font-size: 12px; color: var(--muted); }

/* Empty */
.empty-state { text-align: center; padding: 60px 20px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-text { font-size: 14px; color: var(--muted); }
.empty-hint { font-size: 13px; color: var(--muted); margin-top: 4px; }

/* Bottom tabs */
</style>



