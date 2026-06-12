<template>
  <div class="page-container">
    <div class="page-header">
      <h3>⭐ 我的收藏</h3>
    </div>

    <div class="fab-wrap">
    <!-- 右上角悬浮分类按钮 -->
      <div class="fab-filter" @click="showCatPanel = !showCatPanel">
        <span class="fab-filter-icon">🏷️</span>
        <span v-if="currentCat" class="fab-filter-active">{{ catIconLabel(currentCat) }}</span>
      </div>

      <!-- 分类选择弹窗 -->
      <Transition name="fab-drop">
        <div v-if="showCatPanel" class="fab-dropdown">
          <div v-for="c in [{key:'',icon:'📋',label:'全部'}, ...categories]" :key="c.key"
               class="fab-drop-item" :class="{ active: currentCat === c.key }"
               @click="selectCat(c.key)">
            <span class="fd-icon">{{ c.icon }}</span>
            <span class="fd-label">{{ c.label }}</span>
          </div>
        </div>
      </Transition>
    </div>

    <div class="content">
      <div v-if="loading" class="loading-state">
        <div class="loading-text">加载中...</div>
      </div>
      <div v-else-if="favorites.length === 0" class="empty-state">
        <div class="empty-icon">📌</div>
        <div class="empty-text">{{ currentCat ? '该分类还没有收藏' : '还没有收藏' }}</div>
        <div class="empty-hint">在对话中点「收藏」保存有用的回答</div>
      </div>

      <div v-for="fav in favorites" :key="fav.id" class="fav-item"
           @click="goToConversation(fav)">
        <div class="fav-icon">{{ sceneIcon(fav.scene) }}</div>
        <div class="fav-info">
          <div class="fav-title">{{ fav.summary || '暂无标题' }}</div>
          <div class="fav-meta">
            <span class="fav-cat-tag">{{ sceneIcon(fav.category) }} {{ catLabel(fav.category) || sceneLabel(fav.scene) }}</span>
            <span class="fav-date">{{ formatTime(fav.createdAt) }}</span>
          </div>
        </div>
        <div class="fav-actions" @click.stop>
          <el-dropdown trigger="click" @command="(val) => changeCategory(fav, val)">
            <el-button text size="small" class="cat-btn">
              <el-icon><Collection /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="c in categories" :key="c.key" :command="c.key"
                                  :class="{ selected: fav.category === c.key }">
                  {{ c.icon }} {{ c.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button text type="danger" size="small" @click="remove(fav.id, fav.messageId)">删除</el-button>
        </div>
      </div>
    </div>

    <div class="bottom-tabs">
      <div class="tab" @click="$router.push('/home')"><el-icon><HomeFilled /></el-icon><span>首页</span></div>
      <div class="tab" @click="$router.push('/history')"><el-icon><Timer /></el-icon><span>历史</span></div>
      <div class="tab active"><el-icon><Star /></el-icon><span>收藏</span></div>
      <div class="tab" @click="$router.push('/profile')"><el-icon><User /></el-icon><span>我的</span></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getFavorites, removeFavorite, updateFavoriteCategory } from '../api'
import { HomeFilled, Timer, Star, User, Collection, Filter } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(true)
const favorites = ref([])
const currentCat = ref('')
const showCatPanel = ref(false)

const categories = [
  { key: 'cooking', icon: '🍳', label: '做饭' },
  { key: 'shopping', icon: '🛒', label: '买菜' },
  { key: 'repair', icon: '🔧', label: '修理' },
  { key: 'housework', icon: '🏠', label: '家务' },
  { key: 'health', icon: '🌞', label: '健康' },
  { key: 'fashion', icon: '👔', label: '穿搭' },
  { key: 'etiquette', icon: '🎂', label: '礼仪' },
  { key: 'pet', icon: '🐥', label: '宠物' },
  { key: 'mealplan', icon: '📮', label: '食谱' },
  { key: 'writing', icon: '✍️', label: '写作' }
]

const scenes = {
  cooking: { icon: '🍳', label: '做饭助手' }, shopping: { icon: '🛒', label: '买菜指南' },
  repair: { icon: '🔧', label: '修理指南' }, housework: { icon: '🏠', label: '家务技巧' },
  health: { icon: '🌞', label: '健康常识' }, fashion: { icon: '👔', label: '穿搭指南' },
  etiquette: { icon: '🎂', label: '社交礼仪' }, pet: { icon: '🐥', label: '宠物照顾' },
  mealplan: { icon: '📮', label: '食谱推荐' }, writing: { icon: '✍️', label: '写作助手' }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getFavorites(currentCat.value || undefined)
    favorites.value = res.data || []
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

function filterBy(cat) {
  currentCat.value = cat
  fetchData()
}

function selectCat(key) {
  showCatPanel.value = false
  filterBy(key)
}

function catLabel(key) { return categories.find(c => c.key === key)?.label || '' }
function catIconLabel(key) {
  if (!key) return ''
  const c = categories.find(x => x.key === key)
  return c ? `${c.icon} ${c.label}` : ''
}
function sceneIcon(s) { return scenes[s]?.icon || '💬' }
function sceneLabel(s) { return scenes[s]?.label || '其他' }
function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function goToConversation(fav) {
  router.push(fav.conversationId ? '/chat/' + fav.conversationId + '?highlight=' + fav.messageId : '/chat')
}

async function remove(id, messageId) {
  try {
    await removeFavorite(messageId)
    favorites.value = favorites.value.filter(f => f.id !== id)
    ElMessage.success('已删除')
  } catch { ElMessage.error('删除失败') }
}

async function changeCategory(fav, newCat) {
  try {
    await updateFavoriteCategory(fav.id, newCat)
    fav.category = newCat
    ElMessage.success('分类已更新')
  } catch { ElMessage.error('更新失败') }
}

onMounted(fetchData)
</script>

<style scoped>
.page-container { background: var(--paper); min-height: 100vh; padding-bottom: 80px; }
.page-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 18px 8px; }
.page-header h3 { font-size: 17px; font-weight: 600; color: var(--ink); margin: 0; }

/* Filter bar */
.fab-wrap { position: relative; padding: 0 18px 10px; display: flex; justify-content: flex-end; }
.fab-filter {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 14px; border-radius: 20px; font-size: 12px;
  background: var(--paper); border: 1px solid var(--line); color: var(--accent-deep);
  cursor: pointer; transition: .15s;
}
.fab-filter:hover { background: var(--paper-deep); }
.fab-filter-icon { font-size: 14px; }
.fab-filter-active { font-weight: 600; }
.fab-dropdown {
  position: absolute; top: 44px; left: 18px; z-index: 50;
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
.fd-label { font-size: 13px; }

/* Content */
.content { padding: 0 18px; }

/* Loading */
.loading-state { text-align: center; padding: 40px; color: var(--muted); }
.loading-text { font-size: 14px; }

/* Favorites list */
.fav-item {
  display: flex; gap: 12px; padding: 14px 16px;
  background: var(--card); border-radius: 20px;
  border: 1px solid var(--line); margin-bottom: 10px;
  transition: .15s;
}
.fav-item:hover { border-color: var(--accent); box-shadow: 0 4px 16px rgba(141,95,63,0.1); }
.fav-icon { font-size: 26px; flex-shrink: 0; padding-top: 2px; }
.fav-title { font-size: 14px; font-weight: 500; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fav-meta { font-size: 12px; color: var(--muted); margin-top: 3px; display: flex; gap: 8px; align-items: center; }
.fav-cat-tag { font-size: 12px; padding: 1px 6px; background: var(--paper); border-radius: 8px; color: var(--accent-deep); }
.fav-date { font-size: 12px; color: var(--muted); }
.fav-info { flex: 1; min-width: 0; }
.fav-question { font-size: 14px; font-weight: 500; color: var(--ink); }
.fav-answer { font-size: 12px; color: var(--muted); margin-top: 3px; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.fav-scene { font-size: 12px; color: var(--muted); margin-top: 3px; }
.fav-actions { display: flex; gap: 4px; flex-shrink: 0; align-items: flex-start; }

/* Category popover */
.cat-popover { padding: 6px; display: flex; flex-direction: column; gap: 4px; }
.cat-opt {
  padding: 6px 10px; border-radius: 8px; font-size: 12px; cursor: pointer; color: var(--ink);
}
.cat-opt:hover { background: var(--paper); }

/* Empty */
.empty-state { text-align: center; padding: 60px 20px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-text { font-size: 14px; color: var(--muted); }
.empty-hint { font-size: 13px; color: var(--muted); margin-top: 4px; }

/* Transition */
.fab-drop-enter-active, .fab-drop-leave-active { transition: .15s ease; }
.fab-drop-enter-from, .fab-drop-leave-to { opacity: 0; transform: translateY(-6px); }

/* Bottom tabs */
.bottom-tabs {
  position: sticky; bottom: 0;
  width: 100%; max-width: 480px; height: calc(64px + env(safe-area-inset-bottom, 0px));
  background: var(--card-solid); border-top: 1px solid var(--line); display: flex;
  padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px));
  box-shadow: 0 -2px 12px rgba(80,58,38,0.08); z-index: 100;
}
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: var(--muted); cursor: pointer; gap: 2px; }
.tab.active { color: var(--accent); }
.tab .el-icon { font-size: 20px; }
</style>
