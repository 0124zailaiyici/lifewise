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
      <el-input v-model="keyword" placeholder="搜索常识库..." size="default" clearable
                prefix-icon="Search" @keyup.enter="doSearch" />
      <el-select v-model="sceneFilter" placeholder="场景" size="default" clearable @change="doSearch" style="width:90px;margin-left:8px">
        <el-option v-for="s in scenes" :key="s.key" :label="s.label" :value="s.key" />
      </el-select>
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

    <div class="bottom-tabs">
      <div class="tab" @click="$router.push('/home')"><el-icon><HomeFilled /></el-icon><span>首页</span></div>
      <div class="tab" @click="$router.push('/history')"><el-icon><Timer /></el-icon><span>历史</span></div>
      <div class="tab" @click="$router.push('/favorites')"><el-icon><Star /></el-icon><span>收藏</span></div>
      <div class="tab active" @click="$router.push('/profile')"><el-icon><User /></el-icon><span>我的</span></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue"
import { HomeFilled, Timer, User, Star } from "@element-plus/icons-vue"
import { ElMessage } from "element-plus"
import { searchKnowledge, markHelpful as markHelpfulApi, deleteKnowledge, updateKnowledge, exportKnowledge, importKnowledge } from "../api"

const keyword = ref("")
const sceneFilter = ref("")
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
/* ===== Claude warm theme for KnowledgeBase ===== */
.page-container { background: var(--paper); min-height: 100vh; padding-bottom: 80px; }
.page-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 18px 8px; }
.page-header h3 { font-size: 17px; font-weight: 600; color: var(--ink); margin: 0; }
.header-actions { display: flex; align-items: center; gap: 8px; }

/* Search */
.search-bar { padding: 0 18px 12px; }
.search-bar :deep(.el-input__wrapper) { border-radius: 24px; box-shadow: 0 1px 4px rgba(80,58,38,0.08); }

/* Knowledge list */
.kb-list { padding: 0 18px; }
.kb-item {
  background: var(--card); border-radius: 20px;
  border: 1px solid var(--line); margin-bottom: 10px;
  padding: 16px; cursor: pointer; transition: .15s;
}
.kb-item:hover { border-color: var(--accent); box-shadow: 0 4px 16px rgba(141,95,63,0.1); }
.kb-item-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.kb-item-title { font-size: 15px; font-weight: 600; color: var(--ink); flex: 1; line-height: 1.4; }
.kb-item-title :deep(mark) { background: #fde68a; color: var(--ink); padding: 0 2px; border-radius: 2px; }
.kb-item-desc { font-size: 13px; color: var(--ink-light); margin-top: 6px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.kb-item-meta { display: flex; align-items: center; gap: 12px; margin-top: 10px; font-size: 12px; color: var(--ink-light); }
.kb-item-actions { display: flex; gap: 6px; flex-shrink: 0; }

/* Add dialog */
.add-btn-wrap { padding: 12px 18px; }

/* Categories */
.kb-categories { display: flex; gap: 6px; padding: 0 18px 12px; overflow-x: auto; }
.kb-cat-tag {
  padding: 6px 14px; border-radius: 20px; font-size: 12px;
  background: var(--paper); border: 1px solid var(--line);
  color: var(--ink); cursor: pointer; white-space: nowrap; transition: .15s;
}
.kb-cat-tag.active { background: var(--accent); color: #fff; border-color: var(--accent); }
.kb-cat-tag:hover:not(.active) { border-color: var(--accent); }

/* Empty */
.empty-state { text-align: center; padding: 60px 20px; color: var(--ink-light); font-size: 14px; }
.empty-state .empty-icon { font-size: 48px; margin-bottom: 12px; }

/* Dialog overrides */
.kb-dialog :deep(.el-dialog__body) { padding: 20px; }
.kb-dialog :deep(.el-form-item__label) { font-weight: 600; color: var(--ink); }

/* Bottom tabs */
.bottom-tabs {
  position: fixed; bottom: 0; left: 50%; transform: translateX(-50%);
  width: 100%; max-width: 480px; height: calc(64px + env(safe-area-inset-bottom, 0px));
  background: var(--card-solid); border-top: 1px solid var(--line); display: flex;
  padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px));
  box-shadow: 0 -2px 12px rgba(80,58,38,0.08); z-index: 100;
}
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: var(--muted); cursor: pointer; gap: 2px; }
.tab.active { color: var(--accent); }
.tab .el-icon { font-size: 20px; }

</style>



