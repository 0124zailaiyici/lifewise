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
        <div v-if="activityData.length === 0" class="empty-state" style="padding:30px 0">暂无活动数据</div>
        <template v-else>
          <div class="week-list">
            <div v-for="(week, wi) in weeklyData" :key="wi" class="week-row">
              <div class="week-label">{{ week.label }}</div>
              <div class="week-bar-wrap">
                <div class="week-bar" :style="{ width: weekBarWidth(week.count) }"></div>
              </div>
              <span class="week-count">{{ week.count }}次</span>
            </div>
          </div>
          <div class="week-foot">近{{ activityData.length }}天共计{{ totalActivity }}次活动</div>
        </template>
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
const weeklyData = ref([])
const totalActivity = ref(0)

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
    computeWeeklyData()
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

function computeWeeklyData() {
  const data = activityData.value
  if (!data.length) { weeklyData.value = []; totalActivity.value = 0; return }
  // Group into weeks (Mon-Sun)
  const weeks = []
  let weekStart = new Date(data[0].date)
  weekStart.setDate(weekStart.getDate() - weekStart.getDay() + 1) // Monday
  let currentWeek = { start: new Date(weekStart), count: 0 }
  totalActivity.value = 0
  data.forEach(item => {
    const d = new Date(item.date)
    totalActivity.value += item.count || 0
    if (d >= new Date(currentWeek.start.getTime() + 7*86400000)) {
      weeks.push(currentWeek)
      currentWeek = { start: new Date(d.getTime() - (d.getDay() - 1)*86400000 || d.getTime()), count: 0 }
    }
    currentWeek.count += item.count || 0
  })
  if (currentWeek.count > 0 || weeks.length === 0) weeks.push(currentWeek)
  weeklyData.value = weeks.map(w => {
    const end = new Date(w.start)
    end.setDate(end.getDate() + 6)
    const now = new Date()
    const label = (w.start <= now && end >= now) ? '本周' : 
                  (w.start.getMonth()+1) + '.' + w.start.getDate() + '-' + (end.getMonth()+1) + '.' + end.getDate()
    return { label, count: w.count }
  })
}

function weekBarWidth(count) {
  const max = Math.max(...weeklyData.value.map(w => w.count || 0), 1)
  return Math.max(count / max * 100, 2) + '%'
}
</script>

<style scoped>
.page-container { background: var(--paper); min-height: 100vh; padding-bottom: 80px; }

/* Header bar */
.dash-header {
  background: linear-gradient(135deg, var(--accent-deep), var(--accent));
  padding: 20px 18px 16px; color: #fff; border-radius: 0 0 28px 28px;
}
.header-top { display: flex; align-items: center; justify-content: space-between; }
.header-top h2 { margin: 0; font-size: 20px; font-weight: 700; }

/* Content area */
.content { padding: 0 18px; margin-top: 0; position: relative; z-index: 1; }

/* Hero card */
.hero-card {
  background: var(--card); border-radius: 20px; border: 1px solid var(--line);
  padding: 18px 12px; box-shadow: var(--small-shadow);
}
.hero-row { display: flex; align-items: center; }
.hero-item { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.hero-icon { font-size: 24px; }
.hero-num { font-size: 22px; font-weight: 700; color: var(--ink); }
.hero-label { font-size: 12px; color: var(--muted); }
.hero-divider { width: 1px; height: 36px; background: var(--line); }

/* Today card */
.today-card {
  margin-top: 12px; background: var(--card); border-radius: 20px;
  border: 1px solid var(--line); padding: 16px; display: flex;
  align-items: center; justify-content: space-between;
}
.today-left { display: flex; align-items: center; gap: 12px; }
.today-icon { font-size: 28px; }
.today-label { font-size: 14px; font-weight: 600; color: var(--ink); }
.today-sub { font-size: 12px; color: var(--muted); margin-top: 2px; }
.today-right { text-align: right; }
.today-num { font-size: 24px; font-weight: 700; color: var(--accent); }
.today-unit { font-size: 12px; color: var(--muted); }

/* Compare card */
.compare-card {
  margin-top: 12px; background: var(--card); border-radius: 20px;
  border: 1px solid var(--line); padding: 16px 12px;
  display: flex; align-items: center;
}
.compare-item { flex: 1; text-align: center; }
.compare-num { font-size: 20px; font-weight: 700; color: var(--ink); }
.compare-label { font-size: 12px; color: var(--muted); margin-top: 2px; }

/* Section card */
.section-card {
  margin-top: 12px; background: var(--card); border-radius: 20px;
  border: 1px solid var(--line); padding: 16px;
}
.section-title-wrap { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.section-icon { font-size: 18px; }
.section-title { font-size: 15px; font-weight: 600; color: var(--ink); }

/* Scene distribution */
.scene-row { font-size: 14px;
  display: flex; align-items: center; gap: 10px; padding: 8px 0;
  border-bottom: 1px solid var(--line);
}
.scene-row:last-child { border-bottom: none; }
.scene-emoji { font-size: 18px; width: 28px; text-align: center; }
.scene-bar-wrap { flex: 1; }
.scene-bar-bg { height: 8px; background: var(--paper); border-radius: 4px; overflow: hidden; }
.scene-bar-fill { height: 100%; background: var(--accent); border-radius: 4px; transition: width .3s; }
.scene-num { font-size: 14px; font-weight: 500; color: var(--ink); min-width: 28px; text-align: right; }
.scene-label-text { font-size: 12px; color: var(--muted); white-space: nowrap; }

/* Weekly summary */
.week-list { display: flex; flex-direction: column; gap: 10px; padding: 4px 0; }
.week-row { display: flex; align-items: center; gap: 10px; }
.week-label { font-size: 12px; color: var(--ink); font-weight: 500; min-width: 60px; }
.week-bar-wrap { flex: 1; height: 10px; background: var(--paper); border-radius: 5px; overflow: hidden; }
.week-bar { height: 100%; background: linear-gradient(90deg, var(--accent), var(--accent-deep)); border-radius: 5px; transition: width .5s ease; min-width: 4px; }
.week-count { font-size: 13px; color: var(--accent-deep); font-weight: 600; min-width: 36px; text-align: right; }
.week-foot { font-size: 11px; color: var(--muted); text-align: center; margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--line); }

/* Empty */
.empty-state { text-align: center; padding: 40px 20px; color: var(--muted); font-size: 14px; }

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