<template>
  <div class="page-container">
    <div class="page-header">
      <h3>👤 我的</h3>
    </div>

    <div class="content">
      <div class="user-card">
        <div class="avatar">{{ (user?.username || 'U')[0].toUpperCase() }}</div>
        <div class="user-info">
          <div class="username">{{ user?.username || '未登录' }}</div>
          <div class="phone">{{ user?.phone || '' }}</div>
        </div>
      </div>

      <!-- ===== 设置区域 ===== -->
      <div class="menu-section">
        <div class="menu-label">⚙️ 设置</div>
        <div class="menu-list">
          <div class="menu-item setting-item">
            <div class="si-left">
              <el-icon><Picture /></el-icon>
              <div class="si-text">
                <div class="si-title">🖼️ 菜品成品图</div>
                <div class="si-desc">对话时自动生成菜品图片（消耗 Token）</div>
              </div>
            </div>
            <el-switch v-model="setting_foodImage" @change="saveFoodImageSetting" />
          </div>
          <div class="menu-item setting-item">
            <div class="si-left">
              <el-icon><Cpu /></el-icon>
              <div class="si-text">
                <div class="si-title">🤖 AI 模型</div>
                <div class="si-desc">默认千问；不会自动回退到 DeepSeek</div>
              </div>
            </div>
            <el-select v-model="setting_aiProvider" @change="saveAiProvider" style="width:110px" size="small">
              <el-option label="千问 (Qwen)" value="qwen" />
              <el-option label="DeepSeek" value="deepseek" />
              <el-option label="Ollama 本地" value="ollama" />
            </el-select>
          </div>
        </div>
      </div>


      <div class="menu-section ai-section">
        <div class="section-heading">
          <div>
            <div class="section-title">🛡️ AI 安全中心</div>
            <div class="section-sub">配置、扣费保护和最近调用统一看这里</div>
          </div>
          <div class="section-actions">
            <el-button size="small" round :loading="aiStatusLoading" @click="loadAiStatus">刷新</el-button>
          </div>
        </div>

        <div class="ai-status-card standalone" v-loading="aiStatusLoading">
          <div v-if="aiStatus" class="ai-status-body">
            <div class="guard-hero" :class="{ ok: !aiStatus.autoFallbackToDeepSeek }">
              <div class="guard-icon">{{ aiStatus.autoFallbackToDeepSeek ? '⚠️' : '✅' }}</div>
              <div>
                <div class="guard-title">{{ aiStatus.autoFallbackToDeepSeek ? '存在 DeepSeek 自动回退风险' : 'DeepSeek 默认禁用' }}</div>
                <div class="guard-desc">不会从 Qwen / Ollama 自动回退到 DeepSeek</div>
              </div>
            </div>
            <div class="provider-grid">
              <div class="provider-card" :class="{ active: setting_aiProvider === 'qwen' }">
                <span class="provider-name">千问 Qwen</span>
                <span class="provider-badge" :class="providerConfigured('qwen') ? 'ok' : 'bad'">{{ providerConfigured('qwen') ? '已配置' : '未配置' }}</span>
                <small>{{ aiStatus.qwen?.model || '-' }}</small>
              </div>
              <div class="provider-card" :class="{ active: setting_aiProvider === 'deepseek', danger: aiStatus.deepseekEnabled }">
                <span class="provider-name">DeepSeek</span>
                <span class="provider-badge" :class="aiStatus.deepseekEnabled ? 'warn' : 'ok'">{{ aiStatus.deepseekEnabled ? (providerConfigured('deepseek') ? '已启用' : '缺 Key') : '已禁用' }}</span>
                <small>{{ aiStatus.deepseek?.model || '-' }}</small>
              </div>
              <div class="provider-card" :class="{ active: setting_aiProvider === 'ollama' }">
                <span class="provider-name">Ollama</span>
                <span class="provider-badge ok">本地接口</span>
                <small>{{ aiStatus.ollama?.model || '-' }}</small>
              </div>
            </div>
            <div class="audit-summary">
              <div class="summary-tile"><b>{{ auditStats.total }}</b><span>近期记录</span></div>
              <div class="summary-tile free"><b>{{ auditStats.free }}</b><span>不扣费</span></div>
              <div class="summary-tile paid"><b>{{ auditStats.paid }}</b><span>外部调用</span></div>
              <div class="summary-tile blocked"><b>{{ auditStats.blocked }}</b><span>已拦截</span></div>
            </div>

            <div class="detail-toggle-row" @click="aiDetailExpanded = !aiDetailExpanded">
              <span class="detail-toggle-text">{{ aiDetailExpanded ? '收起调用详情' : '查看调用明细和成本说明' }}</span>
              <span class="detail-toggle-pill" :class="{ open: aiDetailExpanded }" aria-hidden="true"></span>
            </div>

            <transition name="fold">
              <div v-show="aiDetailExpanded" class="ai-detail-panel">
                <div v-if="aiStatus.warnings?.length" class="warn-box">
                  <div v-for="w in aiStatus.warnings" :key="w">⚠️ {{ w }}</div>
                </div>
                <div class="cost-guard">{{ aiStatus.costGuard }}</div>
                <div class="audit-box">
                  <div class="audit-head">
                    <div><span>最近 AI 调用记录</span><small>只展示状态，不展示对话内容</small></div>
                    <el-button v-if="recentCalls.length" size="small" text type="danger" @click="clearAuditRecords">清空</el-button>
                  </div>
                  <div v-if="recentCalls.length" class="audit-filters">
                    <button v-for="item in auditFilterOptions" :key="item.value" class="audit-chip" :class="{ active: auditFilter === item.value }" @click="auditFilter = item.value">{{ item.label }}</button>
                  </div>
                  <div v-if="filteredRecentCalls.length" class="audit-list">
                    <div v-for="(item, idx) in filteredRecentCalls" :key="idx" class="audit-item">
                      <div class="audit-main"><span class="audit-provider">{{ providerName(item.provider) }}</span><span class="audit-status" :class="item.status">{{ statusName(item.status) }}</span><span class="audit-time">{{ formatAuditTime(item.time) }}</span></div>
                      <div class="audit-detail">{{ item.model || '-' }} · {{ item.detail || '-' }}</div>
                    </div>
                  </div>
                  <div v-else class="audit-empty">{{ recentCalls.length ? '当前筛选下暂无记录' : '暂无 AI 调用记录' }}</div>
                </div>
              </div>
            </transition>
          </div>
          <div v-else class="ai-empty">点“刷新”检查当前服务器配置，不会调用 AI</div>
        </div>
      </div>
      <div class="menu-section">
        <div class="menu-label">功能</div>
        <div class="menu-list">
          <div class="menu-item" @click="$router.push('/history')">
            <el-icon><Timer /></el-icon><span>历史记录</span><el-icon class="menu-arrow"><ArrowRight /></el-icon>
          </div>
          <div class="menu-item" @click="$router.push('/favorites')">
            <el-icon><Star /></el-icon><span>我的收藏</span><el-icon class="menu-arrow"><ArrowRight /></el-icon>
          </div>
          <div class="menu-item" @click="$router.push('/dashboard')">
            <el-icon><DataAnalysis /></el-icon><span>数据统计</span><el-icon class="menu-arrow"><ArrowRight /></el-icon>
          </div>
          <div class="menu-item" @click="$router.push('/knowledge')">
            <el-icon><Notebook /></el-icon><span>常识库</span><el-icon class="menu-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <div class="menu-section">
        <div class="menu-label">账号</div>
        <div class="menu-list">
          <div class="menu-item logout" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon><span>退出登录</span>
          </div>
        </div>
      </div>

      <div class="footer-info">
        <div class="app-name">LifeWise v1.0</div>
        <div class="app-desc">AI 生活常识助手</div>
      </div>
    </div>

    <div class="bottom-tabs">
      <div class="tab" @click="$router.push('/home')"><el-icon><HomeFilled /></el-icon><span>首页</span></div>
      <div class="tab" @click="$router.push('/history')"><el-icon><Timer /></el-icon><span>历史</span></div>
      <div class="tab" @click="$router.push('/favorites')"><el-icon><Star /></el-icon><span>收藏</span></div>
      <div class="tab active"><el-icon><User /></el-icon><span>我的</span></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { HomeFilled, Timer, Star, User, ArrowRight, DataAnalysis, Notebook, SwitchButton, Picture, Cpu } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { clearAiAudit, getAiConfigStatus } from '../api'

const router = useRouter()
const userStore = useUserStore()
const user = computed(() => userStore.user)

// ===== 设置状态 =====
const setting_foodImage = ref(localStorage.getItem('setting_foodImage') !== 'off')
const setting_aiProvider = ref(localStorage.getItem('setting_aiProvider') || 'qwen')
const previousAiProvider = ref(setting_aiProvider.value)
const aiStatus = ref(null)
const aiStatusLoading = ref(false)
const aiDetailExpanded = ref(false)
const auditFilter = ref('all')
const auditFilterOptions = [
  { label: '全部', value: 'all' },
  { label: '扣费', value: 'paid' },
  { label: '不扣费', value: 'free' },
  { label: '已拦截', value: 'blocked' }
]
const recentCalls = computed(() => aiStatus.value?.recentCalls || [])
const filteredRecentCalls = computed(() => recentCalls.value.filter(item => {
  if (auditFilter.value === 'all') return true
  if (auditFilter.value === 'paid') return item.status === 'calling' && !['cache', 'ollama'].includes(item.provider)
  if (auditFilter.value === 'free') return item.provider === 'cache' || item.provider === 'ollama'
  if (auditFilter.value === 'blocked') return item.status === 'blocked'
  return true
}))
const auditStats = computed(() => {
  const list = recentCalls.value
  return {
    total: list.length,
    free: list.filter(item => item.provider === 'cache' || item.provider === 'ollama').length,
    paid: list.filter(item => item.status === 'calling' && !['cache', 'ollama'].includes(item.provider)).length,
    blocked: list.filter(item => item.status === 'blocked').length
  }
})

function saveFoodImageSetting(val) {
  localStorage.setItem('setting_foodImage', val ? 'on' : 'off')
  ElMessage.success(val ? '菜品图已开启' : '菜品图已关闭')
}

async function saveAiProvider(val) {
  if (val === 'deepseek') {
    if (!aiStatus.value?.deepseekEnabled) {
      setting_aiProvider.value = previousAiProvider.value || 'qwen'
      localStorage.setItem('setting_aiProvider', setting_aiProvider.value)
      ElMessage.warning('DeepSeek 已被服务端禁用，已保持为当前模型，避免误扣费')
      return
    }
    try {
      await ElMessageBox.confirm(
        'DeepSeek 会产生 DeepSeek 费用，且不会命中千问缓存。确定要临时切换吗？',
        '确认使用 DeepSeek',
        { confirmButtonText: '确认切换', cancelButtonText: '取消', type: 'warning' }
      )
    } catch (e) {
      setting_aiProvider.value = previousAiProvider.value || 'qwen'
      localStorage.setItem('setting_aiProvider', setting_aiProvider.value)
      ElMessage.info('已取消切换 DeepSeek')
      return
    }
    localStorage.setItem('allow_deepseek_until', String(Date.now() + 30 * 60 * 1000))
  } else {
    localStorage.removeItem('allow_deepseek_until')
  }
  localStorage.setItem('setting_aiProvider', val)
  previousAiProvider.value = val
  const names = { qwen: '千问 (Qwen)', deepseek: 'DeepSeek', ollama: 'Ollama 本地' }
  ElMessage.success('AI 模型已切换为 ' + (names[val] || val))
}

function providerConfigured(key) {
  return !!aiStatus.value?.[key]?.configured
}

function providerName(provider) {
  const names = { qwen: '千问', deepseek: 'DeepSeek', ollama: 'Ollama', cache: '常识库', vision: '视觉' }
  return names[provider] || provider || '-'
}

function statusName(status) {
  const names = { hit: '命中缓存', calling: '已发起', blocked: '已拦截' }
  return names[status] || status || '-'
}

function formatAuditTime(time) {
  if (!time) return ''
  const d = new Date(time)
  if (Number.isNaN(d.getTime())) return String(time).slice(11, 19)
  return d.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

async function loadAiStatus() {
  aiStatusLoading.value = true
  try {
    const res = await getAiConfigStatus()
    aiStatus.value = res.data
  } catch (e) {
    console.error(e)
    ElMessage.error('AI 配置诊断读取失败')
  } finally {
    aiStatusLoading.value = false
  }
}

async function clearAuditRecords() {
  try {
    await ElMessageBox.confirm('只清空 AI 调用记录，不会删除聊天、常识库或收藏。确定清空吗？', '清空记录', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await clearAiAudit()
    ElMessage.success('AI 调用记录已清空')
    await loadAiStatus()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error(e)
      ElMessage.error('清空失败')
    }
  }
}

onMounted(loadAiStatus)

function handleLogout() {
  ElMessageBox.confirm('确定退出登录吗？', '提示', {
    confirmButtonText: '退出', cancelButtonText: '取消', type: 'warning'
  }).then(() => { userStore.logout(); router.push('/login') }).catch(() => {})
}
</script>

<style scoped>
.content { padding: 20px 20px calc(80px + env(safe-area-inset-bottom, 0px)); }
.user-card {
  display: flex; align-items: center; gap: 16px; padding: 20px;
  background: linear-gradient(135deg, #f0fdf4, #dcfce7);
  border-radius: 18px; margin-bottom: 28px;
}
.avatar {
  width: 54px; height: 54px; border-radius: 50%;
  background: linear-gradient(135deg, #22c55e, #16a34a);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 24px; font-weight: 700; flex-shrink: 0;
}
.username { font-size: 18px; font-weight: 600; color: #111; }
.phone { font-size: 13px; color: #666; margin-top: 4px; }

.menu-section { margin-bottom: 24px; }
.menu-label { font-size: 12px; color: #999; margin-bottom: 8px; padding-left: 4px; font-weight: 500; }
.menu-list { background: #fff; border-radius: 14px; border: 1px solid #f0f0f0; overflow: hidden; }
.menu-item {
  display: flex; align-items: center; padding: 16px;
  border-bottom: 1px solid #f5f5f5; cursor: pointer; gap: 12px;
  color: #333; font-size: 14px;
}
.menu-item:last-child { border: none; }
.menu-item:active { background: #fafafa; }
.menu-item .el-icon:first-child { font-size: 18px; color: #666; }
.menu-arrow { margin-left: auto; color: #ccc; font-size: 14px; }
.menu-item.logout { color: #ef4444; }
.menu-item.logout .el-icon:first-child { color: #ef4444; }

/* 设置项样式 */
.setting-item { cursor: default; padding: 14px 16px; }
.setting-item:active { background: inherit; }
.setting-item .si-left { display: flex; align-items: center; gap: 12px; flex: 1; }
.setting-item .si-text { flex: 1; }
.setting-item .si-title { font-size: 14px; font-weight: 500; color: #1a1a1a; }
.setting-item .si-desc { font-size: 11px; color: #999; margin-top: 2px; line-height: 1.4; }
.ai-status-card { padding: 14px 16px 16px; background: #fafafa; border-top: 1px solid #f5f5f5; }
.ai-section { margin-top: 2px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; padding: 0 2px; }
.section-title { font-size: 15px; font-weight: 800; color: #111827; }
.section-sub { font-size: 11px; color: #9ca3af; margin-top: 3px; }
.section-actions { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.fold-btn { border: 1px solid #d1fae5; background: #ecfdf5; color: #15803d; border-radius: 999px; padding: 5px 10px; font-size: 12px; cursor: pointer; font-weight: 700; }
.fold-btn:active { transform: scale(.96); }
.ai-status-card.standalone { background: linear-gradient(180deg, #ffffff 0%, #fbfefc 100%); border: 1px solid #e5f3e9; border-radius: 18px; padding: 14px; box-shadow: 0 8px 24px rgba(22, 163, 74, .06); }
.ai-status-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 12px; }
.ai-status-title { font-size: 14px; font-weight: 700; color: #111827; }
.ai-status-sub { font-size: 11px; color: #9ca3af; margin-top: 2px; }
.guard-line { padding: 9px 10px; border-radius: 10px; font-size: 12px; background: #fff7ed; color: #c2410c; margin-bottom: 10px; }
.guard-line.ok { background: #ecfdf5; color: #15803d; }
.guard-hero { display: flex; align-items: center; gap: 10px; padding: 12px; border-radius: 14px; background: #fff7ed; color: #c2410c; border: 1px solid #fed7aa; margin-bottom: 12px; }
.guard-hero.ok { background: #ecfdf5; color: #15803d; border-color: #bbf7d0; }
.guard-icon { width: 30px; height: 30px; border-radius: 10px; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.75); flex-shrink: 0; }
.guard-title { font-size: 13px; font-weight: 800; }
.guard-desc { font-size: 11px; margin-top: 2px; opacity: .78; }
.provider-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.provider-card { border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; padding: 10px 8px; display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.provider-card.active { border-color: #22c55e; box-shadow: 0 0 0 2px rgba(34,197,94,.08); }
.provider-card.danger { border-color: #fed7aa; }
.provider-name { font-size: 12px; font-weight: 700; color: #111827; }
.provider-badge { width: fit-content; padding: 2px 6px; border-radius: 999px; font-size: 10px; }
.provider-badge.ok { background: #dcfce7; color: #15803d; }
.provider-badge.warn { background: #ffedd5; color: #c2410c; }
.provider-badge.bad { background: #fee2e2; color: #b91c1c; }
.provider-card small { color: #9ca3af; font-size: 10px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.audit-summary { display: grid; grid-template-columns: repeat(4, 1fr); gap: 7px; margin-top: 10px; }
.summary-tile { min-width: 0; padding: 9px 6px; border-radius: 12px; background: #f9fafb; border: 1px solid #eef2f7; text-align: center; }
.summary-tile b { display: block; font-size: 16px; line-height: 1; color: #111827; }
.summary-tile span { display: block; margin-top: 5px; font-size: 10px; color: #6b7280; white-space: nowrap; }
.summary-tile.free { background: #ecfdf5; border-color: #bbf7d0; }
.summary-tile.free b, .summary-tile.free span { color: #15803d; }
.summary-tile.paid { background: #eff6ff; border-color: #bfdbfe; }
.summary-tile.paid b, .summary-tile.paid span { color: #1d4ed8; }
.summary-tile.blocked { background: #fef2f2; border-color: #fecaca; }
.summary-tile.blocked b, .summary-tile.blocked span { color: #b91c1c; }
.detail-toggle-row { margin-top: 12px; padding: 8px 8px 8px 12px; border-radius: 999px; background: linear-gradient(135deg, #f8fafc, #f0fdf4); color: #64748b; font-size: 12px; font-weight: 700; display: flex; align-items: center; justify-content: space-between; cursor: pointer; border: 1px solid #eef7f0; box-shadow: inset 0 1px 0 rgba(255,255,255,.9); }
.detail-toggle-row:active { transform: scale(.99); background: #f0fdf4; }
.detail-toggle-text { display: inline-flex; align-items: center; gap: 6px; }
.detail-toggle-text::before { content: '账'; display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 50%; background: #ecfdf5; color: #16a34a; font-size: 11px; font-weight: 800; }
.detail-toggle-pill { width: 20px; height: 20px; border-radius: 50%; background: #fff; border: 1px solid #e2e8f0; display: inline-flex; align-items: center; justify-content: center; transition: transform .2s ease, border-color .2s ease, background .2s ease; position: relative; flex-shrink: 0; }
.detail-toggle-pill::before { content: ''; width: 6px; height: 6px; border-right: 1.8px solid #64748b; border-bottom: 1.8px solid #64748b; transform: rotate(45deg); margin-top: -3px; transition: border-color .2s ease; }
.detail-toggle-pill.open { transform: rotate(180deg); border-color: #bbf7d0; background: #f0fdf4; }
.detail-toggle-pill.open::before { border-color: #16a34a; }
.ai-detail-panel { overflow: hidden; }
.fold-enter-active, .fold-leave-active { transition: opacity .18s ease, transform .18s ease; }
.fold-enter-from, .fold-leave-to { opacity: 0; transform: translateY(-4px); }
.warn-box { margin-top: 10px; padding: 8px 10px; border-radius: 10px; background: #fff7ed; color: #c2410c; font-size: 11px; line-height: 1.5; }
.cost-guard { margin-top: 10px; color: #6b7280; font-size: 11px; line-height: 1.5; }
.audit-box { margin-top: 12px; padding: 10px; border-radius: 12px; background: #fff; border: 1px solid #e5e7eb; }
.audit-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.audit-head > div { display: flex; flex-direction: column; gap: 2px; }
.audit-head span { font-size: 12px; font-weight: 700; color: #111827; }
.audit-head small { font-size: 10px; color: #9ca3af; }
.audit-filters { display: flex; gap: 6px; margin: 8px 0; overflow-x: auto; padding-bottom: 2px; }
.audit-chip { border: 1px solid #e5e7eb; background: #fff; color: #6b7280; border-radius: 999px; padding: 4px 9px; font-size: 11px; white-space: nowrap; cursor: pointer; }
.audit-chip.active { border-color: #22c55e; background: #ecfdf5; color: #15803d; font-weight: 700; }
.audit-list { display: flex; flex-direction: column; gap: 8px; max-height: 190px; overflow-y: auto; }
.audit-item { padding: 8px; border-radius: 10px; background: #f9fafb; }
.audit-main { display: flex; align-items: center; gap: 6px; min-width: 0; }
.audit-provider { font-size: 12px; font-weight: 700; color: #111827; }
.audit-status { padding: 2px 6px; border-radius: 999px; font-size: 10px; background: #e5e7eb; color: #4b5563; }
.audit-status.hit { background: #dcfce7; color: #15803d; }
.audit-status.calling { background: #dbeafe; color: #1d4ed8; }
.audit-status.blocked { background: #fee2e2; color: #b91c1c; }
.audit-time { margin-left: auto; color: #9ca3af; font-size: 10px; }
.audit-detail { margin-top: 4px; color: #6b7280; font-size: 10px; line-height: 1.4; word-break: break-word; }
.audit-empty { color: #9ca3af; font-size: 11px; padding: 4px 0; }
.ai-empty { color: #999; font-size: 12px; padding: 10px 0; }

.footer-info { text-align: center; margin-top: 48px; }
.app-name { font-size: 13px; color: #ccc; font-weight: 500; }
.app-desc { font-size: 11px; color: #bbb; margin-top: 4px; }

.bottom-tabs { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 480px; height: calc(64px + env(safe-area-inset-bottom, 0px)); background: #fff; border-top: 1px solid #e5e7eb; display: flex; padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px)); z-index: 100; box-shadow: 0 -2px 12px rgba(0,0,0,0.06); }
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: #999; cursor: pointer; gap: 2px; }
.tab.active { color: #22c55e; }
.tab .el-icon { font-size: 20px; }
</style>


