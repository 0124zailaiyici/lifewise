<template>
  <div class="page-container">
    <div class="page-header">
      <h3>⭐ 我的收藏</h3>
    </div>

    <div class="content">
      <div v-if="loading" class="loading-state">
        <div class="loading-text">加载中...</div>
      </div>
      <div v-else-if="favorites.length === 0" class="empty-state">
        <div class="empty-icon">📌</div>
        <div class="empty-text">还没有收藏</div>
        <div class="empty-hint">在对话中点「收藏」保存有用的回答</div>
      </div>

      <div v-for="fav in favorites" :key="fav.id" class="fav-item"
           @click="goToConversation(fav)">
        <div class="fav-icon">{{ sceneIcon(fav.scene) }}</div>
        <div class="fav-info">
          <div class="fav-title">{{ fav.summary || '收藏内容' }}</div>
          <div class="fav-meta">{{ sceneLabel(fav.scene) }} · {{ formatTime(fav.createdAt) }}</div>
        </div>
        <el-button text type="danger" size="small" @click.stop="remove(fav.id, fav.messageId)">删除</el-button>
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
import { getFavorites, removeFavorite } from '../api'
import { HomeFilled, Timer, Star, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(true)
const favorites = ref([])
const scenes = { cooking: { icon: '🍳', label: '做饭助手' }, shopping: { icon: '🛒', label: '买菜指南' }, repair: { icon: '🔧', label: '修理指南' }, housework: { icon: '🏠', label: '家务技巧' }, health: { icon: '🌞', label: '健康常识' }, fashion: { icon: '👔', label: '穿搭指南' }, etiquette: { icon: '🎂', label: '社交礼仪' }, pet: { icon: '🐥', label: '宠物照顾' }, mealplan: { icon: '📮', label: '食谱推荐' }, writing: { icon: '✍️', label: '写作助手' } }

onMounted(async () => {
  try { const res = await getFavorites(); favorites.value = res.data || [] } catch (e) { console.error(e) }
  finally { loading.value = false }
})

function goToConversation(fav) { router.push(fav.conversationId ? '/chat/' + fav.conversationId + '?highlight=' + fav.messageId : '/chat') }
async function remove(id, messageId) {
  try { await removeFavorite(messageId); favorites.value = favorites.value.filter(f => f.id !== id); ElMessage.success('已删除') }
  catch { ElMessage.error('删除失败') }
}
function sceneIcon(s) { return scenes[s]?.icon || '💬' }
function sceneLabel(s) { return scenes[s]?.label || '其他' }
function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.page-header { text-align: center; padding: 18px 20px; border-bottom: 1px solid #e0e0e0; background: linear-gradient(180deg, #f0fdf4 0%, #fff 100%); }
.page-header h3 { font-size: 17px; font-weight: 600; color: #111; }
.content { padding: 16px 20px 80px; }
.fav-item { display: flex; align-items: center; padding: 14px; background: #fafcfa; border-radius: 14px; border: 1px solid #f0f0f0; margin-bottom: 10px; cursor: pointer; transition: .15s; }
.fav-item:active { transform: scale(.98); }
.fav-icon { font-size: 28px; margin-right: 14px; }
.fav-info { flex: 1; }
.fav-title { font-size: 14px; font-weight: 500; color: #333; }
.fav-meta { font-size: 12px; color: #888; margin-top: 3px; }
.empty-state { text-align: center; padding: 80px 20px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-text { font-size: 14px; color: #777; }
.empty-hint { font-size: 13px; color: #999; margin-top: 6px; }
.loading-state { text-align: center; padding: 80px 20px; }
.loading-text { font-size: 48px; color: #777; }
.bottom-tabs { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 480px; height: 64px; background: #fff; border-top: 1px solid #e5e7eb; display: flex; padding-bottom: 8px; box-shadow: 0 -2px 12px rgba(0,0,0,0.06); }
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: #777; cursor: pointer; gap: 2px; }
.tab.active { color: #22c55e; }
.tab .el-icon { font-size: 20px; }
</style>
