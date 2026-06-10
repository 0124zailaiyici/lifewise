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
.page-header { display: flex; align-items: center; justify-content: space-between; padding: 18px 20px; border-bottom: 1px solid #e0e0e0; background: linear-gradient(180deg, var(--paper) 0%, #fff 100%); }
.page-header h3 { font-size: 17px; font-weight: 600; color: #111; margin: 0; }
/* 右上角悬浮分类按钮 */
.fab-filter { display: flex; align-items: center; gap: 4px; background: var(--paper); border: 1px solid var(--line); border-radius: 20px; padding: 6px 12px; cursor: pointer; transition: .15s; }
.fab-filter:hover { background: var(--paper-deep); transform: scale(1.05); }
.fab-filter:active { transform: scale(.95); }
.fab-filter-icon { font-size: 16px; line-height: 1; }
.fab-filter-active { font-size: 11px; color: var(--accent-deep); font-weight: 500; max-width: 60px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 展开卡片 */

/* 右上角分类展开卡片 */
.fab-wrap { position: absolute; right: 16px; top: 14px; z-index: 30; }

.fab-dropdown { position: absolute; right: 0; top: calc(100% + 6px); background: rgba(255,255,255,0.97); backdrop-filter: blur(12px); border-radius: 14px; box-shadow: 0 4px 20px rgba(0,0,0,0.12); border: 1px solid #e5e7eb; padding: 6px; min-width: 110px; }
.fab-drop-item { display: flex; align-items: center; gap: 8px; padding: 7px 12px; border-radius: 10px; cursor: pointer; font-size: 13px; color: #444; white-space: nowrap; transition: .1s; }
.fab-drop-item:hover { background: var(--paper); }
.fab-drop-item.active { background: var(--paper); color: var(--accent-deep); font-weight: 600; }
.fd-icon { font-size: 16px; }
.fd-label { font-size: 12px; }

/* 展开收起动画 */
.fab-drop-enter-active, .fab-drop-leave-active { transition: all .15s ease; }
.fab-drop-enter-from, .fab-drop-leave-to { opacity: 0; transform: translateY(-4px) scale(.96); }
/* 分类弹窗网格 */

.content { padding: 0 20px calc(80px + env(safe-area-inset-bottom, 0px)); }
.fav-item { display: flex; align-items: center; padding: 14px; background: var(--card); border-radius: 14px; border: 1px solid #f0f0f0; margin-bottom: 10px; cursor: pointer; transition: .15s; margin-top: 12px; }
.fav-item:active { transform: scale(.98); }
.fav-icon { font-size: 28px; margin-right: 14px; }
.fav-info { flex: 1; min-width: 0; }
.fav-title { font-size: 14px; font-weight: 500; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fav-meta { display: flex; align-items: center; gap: 8px; margin-top: 4px; }
.fav-cat-tag { background: var(--paper); color: var(--accent-deep); font-size: 11px; padding: 1px 8px; border-radius: 8px; }
.fav-date { font-size: 11px; color: #aaa; }
.fav-actions { display: flex; align-items: center; gap: 2px; flex-shrink: 0; }
.cat-btn { font-size: 14px; color: #888; }

.empty-state { text-align: center; padding: 80px 20px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-text { font-size: 14px; color: #777; }
.empty-hint { font-size: 13px; color: #999; margin-top: 6px; }
.loading-state { text-align: center; padding: 80px 20px; }
.loading-text { font-size: 48px; color: #777; }

.bottom-tabs { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 480px; height: calc(64px + env(safe-area-inset-bottom, 0px)); background: #fff; border-top: 1px solid #e5e7eb; display: flex; padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px)); box-shadow: 0 -2px 12px rgba(0,0,0,0.06); }
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: #777; cursor: pointer; gap: 2px; }
.tab.active { color: var(--accent); }
.tab .el-icon { font-size: 20px; }

:deep(.el-dropdown-menu__item.active) { color: var(--accent); font-weight: 600; background: var(--paper); }
</style>
