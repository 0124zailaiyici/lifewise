<template>
  <div class="page-container">
    <div class="page-header">
      <h3>📚 常识库</h3>
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
import { searchKnowledge, markHelpful as markHelpfulApi, deleteKnowledge, updateKnowledge } from "../api"

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
.page-header { text-align: center; padding: 18px 20px 12px; border-bottom: 1px solid #e0e0e0; background: linear-gradient(180deg, #f0fdf4 0%, #fff 100%); }
.page-header h3 { font-size: 17px; font-weight: 600; color: #111; }
.search-bar { display: flex; padding: 12px 20px; align-items: center; }
.search-bar :deep(.el-input__wrapper) { border-radius: 20px; }
.stats-row { display: flex; justify-content: space-between; align-items: center; padding: 0 20px 8px; font-size: 12px; color: #888; }
.hit-badge { background: #f0fdf4; color: #16a34a; padding: 2px 10px; border-radius: 10px; font-size: 11px; }
.content { padding: 0 20px 80px; }
.kb-item { background: #fff; border: 1px solid #eee; border-radius: 14px; padding: 14px; margin-bottom: 12px; box-shadow: 0 1px 4px rgba(0,0,0,.04); }
.kb-header { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 8px; }
.kb-scene-tag { background: #f0fdf4; color: #16a34a; font-size: 11px; padding: 1px 8px; border-radius: 8px; white-space: nowrap; font-weight: 500; }
.kb-q { font-size: 14px; font-weight: 600; color: #222; line-height: 1.4; }
.kb-answer { font-size: 13px; color: #555; line-height: 1.6; padding-left: 4px; }
.kb-footer { display: flex; align-items: center; gap: 8px; margin-top: 10px; font-size: 12px; color: #999; }
.kb-helpful { color: #888; }
.kb-date { margin-left: auto; }
.loading-state, .empty-state { text-align: center; padding: 60px 20px; color: #999; font-size: 14px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.bottom-tabs { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 480px; height: 64px; background: #fff; border-top: 1px solid #e5e7eb; display: flex; padding-bottom: 8px; box-shadow: 0 -2px 12px rgba(0,0,0,0.06); }
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: #999; cursor: pointer; gap: 2px; }
.tab.active { color: #22c55e; }
.tab .el-icon { font-size: 20px; }
</style>



