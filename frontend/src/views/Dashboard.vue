<template>
  <div class="page-container">
    <div class="dash-header">
      <div class="header-top">
        <el-button text @click="$router.push('/profile')" style="color:#fff;font-size:14px">← 返回</el-button>
        <h2 style="margin:0;font-size:20px;font-weight:700">📊 数据统计</h2>
        <div style="width:50px"></div>
      </div>
    </div>

    <div class="content">
      <div class="hero-card">
        <div class="hero-row">
          <div class="hero-item">
            <span class="hero-icon">💬</span>
            <span class="hero-num">{{ stats.total?.conversations || 0 }}</span>
            <span class="hero-label">总对话</span>
          </div>
          <div class="hero-divider"></div>
          <div class="hero-item">
            <span class="hero-icon">📝</span>
            <span class="hero-num">{{ stats.total?.messages || 0 }}</span>
            <span class="hero-label">总消息</span>
          </div>
          <div class="hero-divider"></div>
          <div class="hero-item">
            <span class="hero-icon">⭐</span>
            <span class="hero-num">{{ stats.total?.favorites || 0 }}</span>
            <span class="hero-label">收藏</span>
          </div>
        </div>
      </div>

      <div class="today-card" v-if="stats.today">
        <div class="today-left">
          <div class="today-icon">📅</div>
          <div>
            <div class="today-label">今日活跃</div>
            <div class="today-sub">相比昨天</div>
          </div>
        </div>
        <div class="today-right">
          <div class="today-num">{{ stats.today?.messages || 0 }}</div>
          <div class="today-unit">条消息</div>
        </div>
      </div>

      <div class="compare-card">
        <div class="compare-item">
          <div class="compare-num">{{ stats.thisWeek?.conversations || 0 }}</div>
          <div class="compare-label">本周对话</div>
        </div>
        <div class="compare-item">
          <div class="compare-num">{{ stats.thisWeek?.messages || 0 }}</div>
          <div class="compare-label">本周消息</div>
        </div>
        <div class="compare-item">
          <div class="compare-num">{{ stats.total?.messages || 0 }}</div>
          <div class="compare-label">累计消息</div>
        </div>
      </div>

      <div class="section-card">
        <div class="section-title-wrap">
          <span class="section-icon">🎯</span>
          <span class="section-title">场景分布</span>
        </div>
        <div v-if="sceneDistribution.length === 0" class="empty-state">暂无数据</div>
        <div v-for="item in sceneDistribution" :key="item.scene" class="scene-row">
          <span class="scene-emoji">{{ item.icon }}</span>
          <div class="scene-bar-wrap">
            <div class="scene-bar-bg">
              <div class="scene-bar-fill" :style="{ width: barWidth(item.count), background: sceneColor(item.scene) }"></div>
            </div>
          </div>
          <span class="scene-num">{{ item.count }}</span>
          <span class="scene-label-text">{{ item.label }}</span>
        </div>
      </div>

      <div class="section-card">
        <div class="section-title-wrap">
          <span class="section-icon">📆</span>
          <span class="section-title">最近30天</span>
        </div>
        <div class="chart-container">
          <div v-for="(item, i) in activityData" :key="i" class="chart-bar-wrap" :title="item.date + ': ' + item.count + '次'">
            <div class="chart-bar" :style="{ height: chartHeight(item.count) }"></div>
          </div>
        </div>
        <div class="chart-foot">
          <span>{{ activityData[0]?.date?.substring(5) || '' }}</span>
          <span class="chart-foot-high">最高 {{ maxActivity }} 次</span>
          <span>{{ activityData[activityData.length-1]?.date?.substring(5) || '' }}</span>
        </div>
      </div>
    </div>
    <div class="bottom-tabs">
      <div class="tab" @click="$router.push('/home')"><el-icon><HomeFilled /></el-icon><span>首页</span></div>
      <div class="tab" @click="$router.push('/history')"><el-icon><Timer /></el-icon><span>历史</span></div>
      <div class="tab" @click="$router.push('/favorites')"><el-icon><Star /></el-icon><span>收藏</span></div>
      <div class="tab active"><el-icon><Collection /></el-icon><span>统计</span></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { HomeFilled, Timer, Star, Collection } from '@element-plus/icons-vue'
import { getDashboard } from '../api'

const stats = ref({ total: {}, today: {}, thisWeek: {} })
const sceneDistribution = ref([])
const activityData = ref([])
const maxActivity = ref(1)

const sceneColors = {
  cooking: '#f97316', shopping: 'var(--accent)', repair: '#3b82f6',
  housework: '#a855f7', health: '#eab308', fashion: '#ec4899',
  etiquette: '#14b8a6', pet: '#f59e0b', writing: '#0ea5e9',
  mealplan: '#f97316', other: '#94a3b8'
}

onMounted(async () => {
  try {
    const res = await getDashboard()
    const data = res.data
    stats.value = data.dailyStats || {}
    sceneDistribution.value = data.sceneDistribution || []
    activityData.value = data.activityData || []
    const counts = activityData.value.map(d => d.count || 0)
    maxActivity.value = Math.max(...counts, 1)
  } catch (e) { console.error(e) }
})

function barWidth(count) {
  const max = Math.max(...sceneDistribution.value.map(s => s.count || 0), 1)
  return (count / max * 100) + '%'
}
function chartHeight(count) {
  return Math.max(3, count / maxActivity.value * 80) + 'px'
}
function sceneColor(key) { return sceneColors[key] || '#94a3b8' }
</script>

<style scoped>
.page-container { background: var(--paper); min-height: 100vh; padding-bottom: 80px; }
.page-header { padding: 16px 18px 8px; }
.page-header h3 { font-size: 17px; font-weight: 600; color: var(--ink); margin: 0; }

/* Stats cards */
.stats-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; padding: 0 18px 12px; }
.stat-card {
  background: var(--card); border-radius: 20px;
  border: 1px solid var(--line); padding: 16px; text-align: center;
}
.stat-number { font-size: 28px; font-weight: 700; color: var(--accent); }
.stat-label { font-size: 12px; color: var(--ink-light); margin-top: 4px; }

/* Recent activity */
.section-title { font-size: 15px; font-weight: 600; color: var(--ink); padding: 12px 18px 8px; }
.activity-list { padding: 0 18px; }
.activity-item {
  background: var(--card); border-radius: 20px;
  border: 1px solid var(--line); padding: 14px 16px; margin-bottom: 8px;
  display: flex; align-items: center; gap: 12px;
}
.activity-icon { font-size: 24px; flex-shrink: 0; }
.activity-content { flex: 1; min-width: 0; }
.activity-title { font-size: 14px; font-weight: 500; color: var(--ink); }
.activity-meta { font-size: 12px; color: var(--ink-light); margin-top: 2px; }

/* Charts placeholder */
.chart-area { padding: 0 18px; }
.chart-card {
  background: var(--card); border-radius: 20px;
  border: 1px solid var(--line); padding: 16px; text-align: center;
  color: var(--ink-light); font-size: 13px;
}

.empty-state { text-align: center; padding: 60px 20px; color: var(--ink-light); font-size: 14px; }
.empty-state .empty-icon { font-size: 48px; margin-bottom: 12px; }

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