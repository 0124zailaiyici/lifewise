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
  cooking: '#f97316', shopping: '#22c55e', repair: '#3b82f6',
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
.dash-header {
  background: linear-gradient(135deg, #22c55e, #059669);
  color: #fff; padding: 14px 16px 24px;
  border-radius: 0 0 24px 24px;
}

.header-subtitle { font-size: 13px; opacity: .75; margin-left: 4px; }

.content { padding: 16px 16px 80px; margin-top: -12px; }

.hero-card { background: #fff; border-radius: 16px; padding: 20px; margin-bottom: 12px; box-shadow: 0 2px 12px rgba(0,0,0,.05); }
.hero-row { display: flex; align-items: center; justify-content: space-around; }
.hero-item { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.hero-icon { font-size: 24px; }
.hero-num { font-size: 28px; font-weight: 800; color: #1a1a1a; }
.hero-label { font-size: 12px; color: #999; }
.hero-divider { width: 1px; height: 44px; background: #eee; }

.today-card { background: linear-gradient(135deg, #22c55e, #16a34a); border-radius: 16px; padding: 18px 20px; margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center; color: #fff; box-shadow: 0 2px 12px rgba(34,197,94,.2); }
.today-left { display: flex; align-items: center; gap: 12px; }
.today-icon { font-size: 32px; }
.today-label { font-size: 15px; font-weight: 600; }
.today-sub { font-size: 12px; opacity: .7; }
.today-right { text-align: right; }
.today-num { font-size: 36px; font-weight: 800; line-height: 1; }
.today-unit { font-size: 12px; opacity: .7; }

.compare-card { background: #fff; border-radius: 16px; padding: 16px; margin-bottom: 12px; display: flex; justify-content: space-around; box-shadow: 0 2px 12px rgba(0,0,0,.05); }
.compare-item { text-align: center; }
.compare-num { font-size: 22px; font-weight: 700; color: #22c55e; }
.compare-label { font-size: 12px; color: #999; margin-top: 2px; }

.section-card { background: #fff; border-radius: 16px; padding: 16px; margin-bottom: 12px; box-shadow: 0 2px 12px rgba(0,0,0,.05); }
.section-title-wrap { display: flex; align-items: center; gap: 6px; margin-bottom: 14px; }
.section-icon { font-size: 18px; }
.section-title { font-size: 15px; font-weight: 700; color: #1a1a1a; }
.empty-state { text-align: center; padding: 20px; color: #ccc; font-size: 13px; }

.scene-row { display: flex; align-items: center; gap: 10px; padding: 6px 0; }
.scene-emoji { font-size: 18px; width: 28px; text-align: center; }
.scene-bar-wrap { flex: 1; }
.scene-bar-bg { height: 8px; background: #f0f0f0; border-radius: 4px; overflow: hidden; }
.scene-bar-fill { height: 100%; border-radius: 4px; transition: width .6s ease; }
.scene-num { font-size: 13px; font-weight: 700; color: #333; width: 28px; text-align: right; }
.scene-label-text { font-size: 12px; color: #888; width: 52px; }

.chart-container { display: flex; align-items: flex-end; gap: 2px; height: 84px; padding: 4px 0; }
.chart-bar-wrap { flex: 1; display: flex; align-items: flex-end; justify-content: center; }
.chart-bar { width: 100%; max-width: 10px; background: linear-gradient(180deg, #22c55e, #16a34a); border-radius: 3px 3px 0 0; transition: height .3s; min-height: 3px; }
.chart-foot { display: flex; justify-content: space-between; font-size: 10px; color: #999; margin-top: 6px; }
.chart-foot-high { color: #22c55e; font-weight: 600; }

/* 底部导航 */
.bottom-tabs {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 480px;
  height: 64px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
  display: flex;
  padding-bottom: 8px;
  z-index: 100;
  box-shadow: 0 -2px 12px rgba(0,0,0,0.06);
}
.tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #999;
  cursor: pointer;
  gap: 2px;
}
.tab.active { color: #22c55e; }
.tab .el-icon { font-size: 20px; }
</style>