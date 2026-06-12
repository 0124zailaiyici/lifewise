<!-- VERSION: 20260610-1 Claude Style -->
<template>
  <div class="page-container chat-page">
    <!-- 顶部栏 -->
    <header class="chat-header">
      <div class="header-left">
        <button class="header-back" @click="goBack">←</button>
        <div>
          <div class="header-title" @click="handleRenameTitle">{{ currentLabel }}</div>
          <div class="header-sub">LifeWise</div>
        </div>
      </div>
      <div class="header-right">
        <button class="header-btn" @click="toggleExportMenu" title="导出对话">⋯</button>
        <div v-if="showExportMenu" class="export-menu" @mouseleave="showExportMenu = false">
          <div class="export-item" @click="exportConversation(); showExportMenu = false">📄 导出文本</div>
          <div class="export-item" @click="exportConversationAsImage(); showExportMenu = false">🖼️ 导出图片</div>
          <div class="export-item" @click="exportConversationAsPdf(); showExportMenu = false">📑 导出PDF</div>
        </div>
      </div>
    </header>

    <!-- 消息区 -->
    <div class="messages" ref="msgBox">
      <!-- 欢迎 -->
      <div v-if="messages.length === 0 && !loading" class="welcome">
        <div class="welcome-icon">⌂</div>
        <div class="welcome-text">问我关于 {{ currentLabel }} 的问题吧</div>
        <div v-if="welcomePrompts.length" class="welcome-prompts">
          <button v-for="p in welcomePrompts" :key="p" class="wp-chip" @click="fillPrompt(p)">{{ p }}</button>
        </div>
        <div v-if="isWritingScene" class="writing-modes">
          <div class="wm-title">📝 写作类型</div>
          <div class="wm-grid">
            <div v-for="wm in writingModes" :key="wm.key" class="wm-chip" @click="fillPrompt(wm.prompt)">
              <span class="wm-icon">{{ wm.icon }}</span>
              <span class="wm-label">{{ wm.label }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-for="(msg, i) in messages" :key="msg._id || i" :class="['msg', msg.role === 'user' ? 'msg-user' : 'msg-assistant']" :data-msg-id="msg._id || ''">
        <!-- 用户消息 -->
        <template v-if="msg.role === 'user'">
          <div class="bubble user-bubble">
            <div v-if="msg.imageUrl" class="msg-image" @click="previewImage(msg.imageUrl)">
              <img :src="imgUrl(msg.imageUrl)" alt="图片" loading="lazy" @error="onImgError($event)" />
            </div>
            <div v-html="msg._displayHtml || renderContent(msg.content)"></div>
          </div>
        </template>

        <!-- 助手消息 — Claude 卡片风格 -->
        <template v-if="msg.role === 'assistant'">
          <article class="assistant-card">
            <span class="confidence kb-badge" v-if="!msg._typing && (msg._source === 'knowledge-base' || msg._externalCall === false) && !isJsonContent(msg.content)">✅ 常识库</span>

            <div class="card-body">
              <div v-if="msg._typing">
                <span v-html="msg._displayHtml"></span><span class="cursor">|</span>
              </div>
              <div v-else>
                <div v-html="msg._displayHtml || renderContent(msg.content)"></div>

                <!-- 场景配图 -->
                <div v-if="msg._sceneImageUrl" class="scene-image" @click="previewImage(msg._sceneImageUrl)">
                  <img :src="msg._sceneImageUrl" alt="场景图" loading="lazy" />
                  <span class="scene-badge">AI 配图</span>
                </div>
                <div v-if="msg._sceneImageLoading" class="food-loading">
                  <span class="fl-spinner"></span>
                  <span class="fl-text">{{ msg._sceneImageText || '🎨 正在生成场景配图…' }}</span>
                </div>
                <!-- 菜品图 -->
                <div v-if="msg._foodImageLoading" class="food-loading">
                  <span class="fl-spinner"></span>
                  <span class="fl-text">{{ msg._foodImageLoadingText || '查找本地成品图…' }}</span>
                  <button class="fl-cancel" @click="cancelFoodImage(msg)">取消</button>
                </div>
                <div v-if="msg._foodImageUrl" class="food-image" @click="previewImage(msg._foodImageUrl)">
                  <img :src="msg._foodImageUrl" alt="成品图" loading="lazy" />
                  <span v-if="msg._foodImageSource" class="food-badge" :class="{ paid: msg._foodImageSource === 'generated' }">{{ msg._foodImageSource === 'generated' ? 'AI 生成' : '本地图' }}</span>
                </div>
                <div v-if="msg.role === 'assistant' && msg._foodDishName && !msg._foodImageUrl && !msg._foodImageLoading" class="food-actions">
                  <span>未找到 "{{ msg._foodDishName }}" 本地图</span>
                  <button class="food-gen-btn" @click="confirmGenerateFoodImage(msg)">手动生成</button>
                </div>
              </div>
            </div>

            <!-- 消息操作按钮 -->
            <div v-if="!msg._typing" class="card-actions">
              <button @click="copyMsg(i)">📋 复制</button>
              <button @click="shareMsg(i)">📤 分享</button>
              <button @click="addMsgToKnowledge(i)" :disabled="msg._kbSaving">{{ msg._kbSaving ? '入库中…' : msg._kbSaved ? '✅ 已入库' : '📚 入库' }}</button>
              <button @click="toggleFavorite(i)" :class="{ faved: msg._faved }">{{ msg._favProcessing ? '⏳…' : msg._faved ? '⭐ 已收藏' : '☆ 收藏' }}</button>
            </div>
          </article>
        </template>
      </div>

      <!-- 加载中 -->
      <div v-if="loading && !currentTyping" class="msg msg-assistant">
        <article class="assistant-card">
          <div class="card-head">
            <div class="card-title"><span class="avatar">✦</span><span>{{ currentLabel }}</span></div>
          </div>
          <div class="card-body thinking-dots">
            <span class="dot">.</span><span class="dot">.</span><span class="dot">.</span>
          </div>
        </article>
      </div>
    </div>

    <!-- 收藏分类弹窗 -->
    <el-dialog v-model="favDialog.show" title="选择分类" width="320px" :close-on-click-modal="false">
      <div class="fav-cat-grid">
        <div v-for="c in favCategories" :key="c.key" :class="['fav-cat-opt', { active: favDialog.selected === c.key }]" @click="favDialog.selected = c.key">
          <span class="fco-icon">{{ c.icon }}</span>
          <span class="fco-label">{{ c.label }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="favDialog.show = false">取消</el-button>
        <el-button type="primary" @click="confirmFavorite">确定收藏</el-button>
      </template>
    </el-dialog>

    <!-- 图片预览 -->
    <div v-if="previewImg" class="overlay" @click="previewImg = null">
      <img :src="imgUrl(previewImg)" class="preview-img" />
      <div class="overlay-close" @click="previewImg = null">✕</div>
    </div>

    <!-- 分享卡片弹窗 -->
    <el-dialog v-model="shareDialog.show" title="分享卡片" width="360px" :close-on-click-modal="true" top="5vh">
      <div class="share-card" ref="shareCardRef">
        <div class="sc-header">
          <span class="sc-logo">⌂</span>
          <span class="sc-brand">LifeWise · AI 生活助手</span>
        </div>
        <div class="sc-body">
          <div class="sc-q" v-if="shareDialog.question">💬 {{ shareDialog.question }}</div>
          <div class="sc-divider"></div>
          <div class="sc-a" v-html="shareDialog.html"></div>
        </div>
        <div class="sc-footer">
          <span class="sc-date">{{ shareDialog.date }}</span>
          <span class="sc-wm">via LifeWise</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="shareDialog.show = false">取消</el-button>
        <el-button type="success" @click="downloadShareCard" :loading="shareDialog.downloading">📋 复制图片</el-button>
        <el-button type="primary" @click="shareToSocial" :loading="shareDialog.sharing">📱 分享</el-button>
        <el-button @click="copyShareText">📋 复制文本</el-button>
      </template>
    </el-dialog>

    <!-- 图片快捷操作 -->
    <div v-if="uploadProgress > 0 && uploadProgress < 100" class="upload-bar">
      <div class="ub-fill" :style="{ width: uploadProgress + '%' }"></div>
      <span class="ub-text">{{ uploadProgress }}%</span>
    </div>
    <div v-if="pendingImage" class="image-bar">
      <div class="ib-preview">
        <img :src="pendingImage" />
        <span class="ib-remove" @click="pendingImage = null; pendingFile = null">✕</span>
      </div>
      <div class="ib-actions">
        <span class="ib-chip" @click="fillImagePrompt('请分析这张图片', 'cooking')">🔍 分析图片</span>
        <span class="ib-chip" @click="fillImagePrompt('请识别这张图片的内容', 'other')">👀 识别内容</span>
        <span class="ib-chip" @click="fillImagePrompt('请描述这张图片', 'writing')">📝 描述图片</span>
      </div>
    </div>

    <!-- 输入区 (Composer) -->
    <footer class="composer-wrap">
      <div v-if="promptPrefilled && inputText.trim() && !loading" class="prefill-hint">已填入示例问题，确认后发送</div>
      <div class="cost-hint" :class="costHint.type">
        <span class="ch-main">{{ costHint.text }}</span>
        <span class="ch-sub">{{ costHint.sub }}</span>
      </div>
      <div v-if="currentConvId" class="mode-row">
        <button class="mode-chip" :class="{ active: !pendingFollowUp }" @click="setAskMode(false)" :disabled="loading">正常提问</button>
        <button class="mode-chip follow" :class="{ active: pendingFollowUp }" @click="setAskMode(true)" :disabled="loading">追问</button>
        <span class="mode-tip">{{ pendingFollowUp ? '结合上文补充' : '按新问题回答' }}</span>
      </div>
      <div class="composer">
        <button class="composer-btn upload-btn" @click="triggerUpload">＋</button>
        <button class="composer-btn mic-btn" :class="{ listening: isListening }" @click="startVoice" :disabled="loading">🎤</button>
        <textarea ref="inputRef" v-model="inputText" placeholder="继续问 LifeWise，或上传菜品图片…" rows="1" @keyup.enter="send" :disabled="loading" @input="onUserInput"></textarea>
        <button class="composer-btn send-btn" :disabled="loading || !inputText.trim() && !pendingFile" @click="send">➜</button>
      </div>
    </footer>
  </div>
</template>
<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getConversation, sendChat, addFavorite, removeFavorite, uploadImage, updateFavoriteCategory, lookupFoodImage, generateFoodImage, getFoodImageStatus, saveFoodImageCache, addKnowledge, getAiConfigStatus } from '../api'
import { ArrowLeft, DocumentCopy, Microphone, Picture, Promotion, Collection, Share } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const msgBox = ref(null)
const inputRef = ref(null)
const inputText = ref('')
const fileInput = ref(null)
const pendingFile = ref(null)
const pendingImage = ref(null)
const loading = ref(false)
const messages = ref([])
const promptPrefilled = ref(false)
const pendingFollowUp = ref(false)
const currentTyping = ref(false)
const previewImg = ref(null)
const uploadProgress = ref(0)
const currentConvId = ref(null)
const foodImageEnabled = ref(localStorage.getItem('setting_foodImage') !== 'off')
const aiConfigStatus = ref(null)
function previewImage(url) { previewImg.value = url }
function onImgError(e) {
  e.target.style.display = "none"
  const parent = e.target.parentElement
  if (parent) {
    const fallback = document.createElement("div")
    fallback.style.cssText = "padding:20px;text-align:center;color:#999;font-size:13px"
    fallback.textContent = "🖼️ 图片加载失败"
    parent.appendChild(fallback)
  }
}
function cancelFoodImage(m) {
  if (m._foodImagePoll) { clearInterval(m._foodImagePoll); m._foodImagePoll = null }
  m._foodImageLoading = false
  ElMessage.info("已取消生成成品图")
}
const tempScene = ref('')
const shareCardRef = ref(null)
const isListening = ref(false)
const favDialog = ref({ show: false, selected: 'other', msgIndex: -1 })
const shareDialog = ref({ show: false, question: '', html: '', date: '', downloading: false, sharing: false })
const showExportMenu = ref(false)
function toggleExportMenu() { showExportMenu.value = !showExportMenu.value }
const favCategories = [
  { key: 'cooking', icon: '🍳', label: '做饭' },
  { key: 'shopping', icon: '🛒', label: '买菜' },
  { key: 'repair', icon: '🔧', label: '修理' },
  { key: 'housework', icon: '🏠', label: '家务' },
  { key: 'health', icon: '🌞', label: '健康' },
  { key: 'fashion', icon: '👔', label: '穿搭' },
  { key: 'etiquette', icon: '🎂', label: '礼仪' },
  { key: 'pet', icon: '🐥', label: '宠物' },
  { key: 'mealplan', icon: '📮', label: '食谱' },
  { key: 'writing', icon: '✍️', label: '写作' },
  { key: 'other', icon: '💬', label: '其他' }
]

const prebuiltFoodNames = [
  '鱼香肉丝', '宫保鸡丁', '红烧肉', '糖醋里脊', '麻婆豆腐', '西红柿炒鸡蛋', '番茄炒蛋', '番茄炒鸡蛋',
  '青椒肉丝', '土豆丝', '酸辣土豆丝', '回锅肉', '可乐鸡翅', '红烧排骨', '糖醋排骨', '蒜蓉西兰花',
  '地三鲜', '鱼香茄子', '肉末茄子', '黄焖鸡', '辣子鸡', '清蒸鱼', '红烧鱼', '水煮鱼',
  '酸菜鱼', '水煮肉片', '京酱肉丝', '蚂蚁上树', '干煸豆角', '蒜苔炒肉', '木须肉', '葱爆羊肉',
  '番茄牛腩', '土豆炖牛肉', '小炒黄牛肉', '农家小炒肉', '香菇青菜', '炒青菜', '手撕包菜', '干锅花菜',
  '韭菜炒鸡蛋', '虾仁炒蛋', '油焖大虾', '蒜蓉粉丝虾', '蛋炒饭', '扬州炒饭', '鸡蛋面', '米饭',
  '馒头', '包子', '饺子', '白粥', '汤面', '豆浆', '油条', '馄饨',
  '牛肉面', '烧麦', '粽子', '小笼包', '煎饼果子', '皮蛋瘦肉粥', '炒面', '炸酱面',
  '酸辣粉', '辣椒炒肉', '红烧茄子', '凉拌黄瓜', '紫菜蛋花汤', '醋溜白菜'
]

const writingModes = [
  { key: 'diary', icon: '📔', label: '日记', prompt: '帮我写一篇今天的日记' },
  { key: 'todo', icon: '✅', label: '待办清单', prompt: '帮我整理一份待办清单' },
  { key: 'memo', icon: '📌', label: '备忘录', prompt: '帮我写一个备忘录' },
  { key: 'note', icon: '📝', label: '学习笔记', prompt: '帮我整理学习笔记' },
  { key: 'shopping', icon: '🛍️', label: '购物清单', prompt: '帮我列一个购物清单' },
  { key: 'idea', icon: '💡', label: '灵感记录', prompt: '帮我记录一个想法' }
]

const isWritingScene = computed(() => {
  const scene = localStorage.getItem('currentScene')
  return scene === 'writing'
})

const currentLabel = computed(() => localStorage.getItem('sceneLabel') || '生活常识')
const welcomePools = {
  cooking: [
    '今晚吃什么比较简单？', '红烧排骨怎么做？', '冰箱剩菜怎么搭配？',
    '番茄炒蛋怎么做才好吃？', '青菜怎么炒才脆嫩？', '煮粥水和米的比例多少？',
    '新手学做菜先学什么？', '瘦肉怎么炒不柴？', '蒸鱼要多长时间？',
    '一个人做饭怎么省事？', '煲汤用什么锅好？', '鸡蛋有哪些简单做法？'
  ],
  shopping: [
    '怎么挑西瓜？', '买牛肉怎么选？', '哪些水果适合囤？',
    '买菜怎么挑新鲜的？', '超市买肉有什么技巧？', '怎么选好的大米？',
    '买海鲜怎么看新不新鲜？', '水果买回来怎么保存？', '菜市场砍价技巧？',
    '哪种油比较健康？', '怎么辨别注水肉？', '买鸡蛋怎么挑？'
  ],
  repair: [
    '水龙头滴水怎么办？', '灯泡不亮怎么排查？', '马桶堵了先怎么处理？',
    '墙皮脱落怎么修补？', '插座没电怎么查？', '门锁不好拧怎么办？',
    '下水道反味怎么解决？', '窗户漏风怎么处理？', '瓷砖缝隙发霉怎么办？',
    '电饭煲不加热怎么修？', '螺丝滑丝了怎么取？', '墙面钉子洞怎么补？'
  ],
  housework: [
    '衣服染色怎么办？', '厨房油污怎么清理？', '冰箱异味怎么去除？',
    '白衣服发黄怎么洗？', '油烟机怎么拆洗？', '地板拖完总是粘脚怎么办？',
    '抹布发臭怎么处理？', '床单被套多久洗一次？', '浴室水垢怎么去除？',
    '衣服起球怎么处理？', '毛绒玩具怎么清洁？', '不锈钢锅烧黑了怎么刷？'
  ],
  health: [
    '熬夜后怎么恢复？', '久坐腰酸怎么办？', '感冒时饮食注意什么？',
    '眼睛干涩疲劳怎么办？', '失眠有什么改善方法？', '换季容易生病怎么预防？',
    '腿抽筋是什么原因？', '口臭是什么原因？', '饭后百步走有哪些讲究？',
    '打呼噜怎么办？', '湿气重有什么表现？', '颈椎不舒服怎么缓解？'
  ],
  fashion: [
    '面试穿什么合适？', '黑色裤子怎么搭配？', '矮个子怎么显高？',
    '男生衣柜必备哪些单品？', '上班通勤怎么穿得体？', '约会穿什么比较加分？',
    '胖人穿什么显瘦？', '小白鞋怎么搭配？', '大衣里面穿什么好看？',
    '怎么判断衣服是否合身？', '全身颜色不超过几种？', '夏天穿什么面料凉快？'
  ],
  etiquette: [
    '第一次见家长带什么？', '怎么委婉拒绝别人？', '送礼怎么避免尴尬？',
    '饭局上怎么敬酒？', '加好友后第一句说什么？', '怎么夸人显得真诚？',
    '跟长辈聊天的话题？', '同事借钱怎么处理？', '聚会上怎么不冷场？',
    '怎么自然地结束对话？', '道歉怎么说才诚恳？', '被催婚怎么应对？'
  ],
  pet: [
    '猫不爱喝水怎么办？', '狗狗掉毛严重怎么办？', '新手养猫要准备什么？',
    '猫咪晚上一直叫怎么办？', '狗拉肚子可以吃什么？', '猫抓沙发怎么纠正？',
    '宠物驱虫多久一次？', '猫狗能一起养吗？', '宠物生病有哪些征兆？',
    '怎么教狗狗定点大小便？', '猫咪需要洗澡吗？', '宠物疫苗要打哪些？'
  ],
  mealplan: [
    '帮我安排一周晚餐', '今晚吃什么比较健康？', '两个人做饭怎么搭配？',
    '上班族怎么带饭？', '减脂期三餐怎么安排？', '夏天吃什么开胃？',
    '适合招待朋友的菜？', '孩子不爱吃饭怎么办？', '周末在家做什么好吃的？',
    '吃不完的食材怎么处理？', '早餐做什么又快又营养？', '低脂晚餐推荐？'
  ],
  writing: [],
  general: [
    '家里临时有问题怎么处理？', '帮我整理一个解决步骤', '这个生活问题有什么注意事项？',
    '有什么实用的生活小技巧？', '怎么提高做事效率？', '哪些生活习惯值得坚持？',
    '遇到突发情况怎么应对？', '这件事从哪开始着手？', '有什么常见误区？',
    '有没有更简单的办法？', '怎么判断问题严重程度？', '紧急情况怎么处理？'
  ]
}

const welcomePrompts = computed(() => {
  const scene = localStorage.getItem('currentScene') || 'other'
  const pool = welcomePools[scene] || welcomePools.general
  if (!pool.length) return []
  return [...pool].sort(() => Math.random() - 0.5).slice(0, 3)
})
const costHint = computed(() => {
  if (pendingFile.value) {
    const vision = aiConfigStatus.value?.vision
    if (vision?.configured) {
      return {
        type: 'warn',
        text: '图片识别：将调用小米 MiMo 视觉模型',
        sub: '图片会跳过常识库缓存，发送前请确认问题'
      }
    }
    return {
      type: 'danger',
      text: '图片识别未配置完整',
      sub: '请检查 ai.vision-api-url / ai.vision-api-key'
    }
  }
  return {
    type: 'normal',
    text: '当前模型：千问 Qwen',
    sub: '聊天会消耗千问；命中常识库则不扣费'
  }
})

onMounted(async () => {

  msgBox.value?.addEventListener('click', handleFollowUpClick)
  refreshLocalSettings()
  loadAiConfigStatus()
  window.addEventListener('focus', refreshLocalSettings)
  window.addEventListener('storage', refreshLocalSettings)
  await nextTick()
  inputRef.value?.focus()
  if (route.params.id) { currentConvId.value = Number(route.params.id); await loadConversation(route.params.id) }
  else {
    const pendingPrompt = localStorage.getItem('pendingPrompt')
    if (pendingPrompt) {
      inputText.value = pendingPrompt
      promptPrefilled.value = true
      localStorage.removeItem('pendingPrompt')
      await nextTick()
      inputRef.value?.focus()
    }
  }
})
onUnmounted(() => {
  msgBox.value?.removeEventListener('click', handleFollowUpClick)
  window.removeEventListener('focus', refreshLocalSettings)
  window.removeEventListener('storage', refreshLocalSettings)
  messages.value.forEach(m => {
    if (m._foodImagePoll) {
      clearInterval(m._foodImagePoll)
      m._foodImagePoll = null
    }
  })
})

function refreshLocalSettings() {
  foodImageEnabled.value = localStorage.getItem('setting_foodImage') !== 'off'
}

async function loadAiConfigStatus() {
  try {
    const res = await getAiConfigStatus()
    aiConfigStatus.value = res.data || null
  } catch {
    aiConfigStatus.value = null
  }
}

async function loadConversation(id) {
  loading.value = true
  try {
    const res = await getConversation(id)
    const data = res.data
    if (data?.scene) {
      localStorage.setItem('currentScene', data.scene)
      localStorage.setItem('sceneLabel', data.sceneLabel || '')
    }
    messages.value = (data?.messages || []).map((m, i) => ({
      _id: m.id || 'msg-' + i, role: m.role, content: m.content || '', imageUrl: m.imageUrl || '',
      _typing: false, _displayHtml: '', _faved: m.faved || false
    }))
    hydrateFoodImagesForLoadedMessages()
    await nextTick(); scrollBottom()
  } catch(e) { ElMessage.error('加载消息失败') }
  finally { loading.value = false }
}

async function send() {
  const msg = inputText.value.trim()
  if (!msg && !pendingFile.value) return
  const isFollowUp = pendingFollowUp.value
  pendingFollowUp.value = false

  refreshLocalSettings()


  const scene = tempScene.value || localStorage.getItem('currentScene') || 'other'; tempScene.value = ''
  let convId = currentConvId.value
  let imageUrl = ''

  if (pendingFile.value) {
    try {
      const uploadRes = await uploadImage(pendingFile.value, (e) => { uploadProgress.value = Math.round((e.loaded / e.total) * 100) })
      imageUrl = uploadRes.data?.url || ""
      pendingFile.value = null; pendingImage.value = null
    } catch(e) {
      uploadProgress.value = 0
      if (e?.response?.status === 401) {
        ElMessage.error('登录已过期，请重新登录后再上传图片')
      } else if (e?.response?.status === 413) {
        ElMessage.error('图片太大，请压缩到 10MB 以内')
      } else if (e?.response?.data?.message) {
        ElMessage.error(e.response.data.message)
      } else if (e?.message?.includes('Network')) {
        ElMessage.error('无法连接服务器，请检查后端是否启动')
      } else {
        ElMessage.error('图片上传失败，请换一张图片重试')
      }
      return
    }
  }

  messages.value.push({ _id: 'user-' + Date.now(), role: 'user', content: msg, imageUrl, _typing: false, _displayHtml: '', _faved: false })
  inputText.value = ''
  promptPrefilled.value = false
  await nextTick(); scrollBottom()

  loading.value = true; currentTyping.value = true
  const aiIdx = messages.value.length
  messages.value.push({ _id: 'ai-' + Date.now(), role: 'assistant', content: '', imageUrl: '', _typing: true, _displayHtml: '', _faved: false })

  try {
    const res = await sendChat(msg, scene, convId, imageUrl, { followUp: isFollowUp })
    const m = messages.value[aiIdx]
    if (res.data?.id && m) { m._id = res.data.id }
    if (res.data?.conversationId) currentConvId.value = res.data.conversationId
    const fullContent = typeof res.data === "string" ? res.data : (res.data?.content || res.data?.answer || JSON.stringify(res.data))
    if (m) {
      m._typing = false; m.content = fullContent
      m._source = res.data?.source || ''
      m._externalCall = res.data?.externalCall
      m._sourceLabel = res.data?.sourceLabel || ''
      m._displayHtml = renderContent(fullContent)
      currentTyping.value = false; scrollBottom()
      // Recipe image: only lookup local/server cache automatically; never auto-submit paid generation.
      const foodImageEnabled = localStorage.getItem('setting_foodImage') !== 'off'
      if (foodImageEnabled) try {
        const dishName = detectDishName(fullContent, msg)
        attachLocalFoodImage(m, dishName)
      } catch {}
            // Scene image generation: all supported scenes
      try {
        const parsed = tryParseJsonSafe(fullContent)
        if (parsed) {
          var detectedScene = null
          if (parsed.ingredients && parsed.steps) detectedScene = 'cooking'
          else if (parsed.occasion || parsed.outfits || parsed.color_palette || parsed.style) detectedScene = 'fashion'
          else if (parsed.selection_steps || parsed.storage_tip || parsed.summary_slogan) detectedScene = 'shopping'
          else if (parsed.problem && (parsed.tools || parsed.severity)) detectedScene = 'repair'
          else if (parsed.problem && (parsed.materials || parsed.difficulty)) detectedScene = 'housework'
          else if (parsed.weekly_plan || parsed.shopping_list) detectedScene = 'mealplan'
          else if (parsed.do_list || parsed.dont_list || parsed.key_principles) detectedScene = 'etiquette'
          else if (parsed.pet_type || parsed.when_to_see_vet) detectedScene = 'pet'
          if (detectedScene && detectedScene !== 'health') {
            var imgPrompt = generateScenePrompt(parsed, detectedScene)
            if (imgPrompt) attachSceneImage(m, imgPrompt, detectedScene)
          }
        }
      } catch {}
    }
  } catch(e) {
    const m = messages.value[aiIdx]
    if (m) {
      let errMsg = "请求出错了，请稍后再试"
      if (e?.response?.status === 502) errMsg = "AI 服务暂时不可用，请稍后再试"
      else if (e?.response?.status === 401) errMsg = "登录已过期，请重新登录"
      else if (e?.message?.includes("Network")) errMsg = "无法连接服务器，请检查后端是否启动"
      m._typing = false; m._displayHtml = '<div style="color:#ef4444;padding:8px">' + errMsg + '</div>'; currentTyping.value = false
    }
  } finally { loading.value = false }
}


function attachLocalFoodImage(message, dishName) {
  if (!message || !dishName || message._foodImageUrl || message._foodImageLoading) return
  message._foodDishName = dishName
  const dishKey = 'food_img_' + dishName
  const cached = localStorage.getItem(dishKey)
  if (cached) {
    message._foodImageUrl = cached
    message._foodImageSource = 'local-prebuilt'
    return
  }
  message._foodImageLoading = true
  message._foodImageLoadingText = '🖼️ 正在查找本地/缓存成品图…'
  lookupFoodImage(dishName).then(res => {
    const data = res.data || {}
    if (data.found && data.imageUrl) {
      message._foodImageUrl = data.imageUrl
      message._foodImageSource = data.source || 'local-prebuilt'
      localStorage.setItem(dishKey, data.imageUrl)
      nextTick(scrollBottom)
    }
    message._foodImageLoading = false
    message._foodImageLoadingText = ''
  }).catch(() => {
    message._foodImageLoading = false
    message._foodImageLoadingText = ''
  })
}

function foodImageBadge(message) {
  if (message._foodImageSource === 'generated') return 'AI生成 · 已扣费'
  if (message._foodImageSource === 'remote-cache') return '服务器缓存 · 不重复扣费'
  return '本地缓存 · 不扣费'
}

async function confirmGenerateFoodImage(message) {
  const dishName = message?._foodDishName
  if (!dishName || message._foodImageLoading) return
  try {
    await ElMessageBox.confirm(
      `将为“${dishName}”调用外部生图接口，可能产生费用。是否继续？`,
      '手动生成菜品图',
      { confirmButtonText: '确认生成', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  await startFoodImageGeneration(message, dishName)
}

async function startFoodImageGeneration(message, dishName) {
  try {
    message._foodImageLoading = true
    message._foodImageLoadingText = '🎨 正在提交生图任务…'
    const res = await generateFoodImage(dishName)
    if (res.code && res.code !== 200) throw new Error(res.message || '生图提交失败')
    const data = res.data || {}
    if (data.imageUrl) {
      applyFoodImage(message, dishName, data.imageUrl, data.source || 'remote-cache')
      return
    }
    if (!data.taskId) throw new Error('生图服务未返回任务 ID')
    message._foodImageTaskId = data.taskId
    pollFoodImageStatus(message, dishName, data.taskId, data.provider)
  } catch (e) {
    message._foodImageLoading = false
    message._foodImageLoadingText = ''
    ElMessage.error(e?.message || '生图失败，请稍后再试')
  }
}

function pollFoodImageStatus(message, dishName, taskId, provider) {
  let attempts = 0
  if (message._foodImagePoll) clearInterval(message._foodImagePoll)
  message._foodImageLoadingText = '🎨 正在生成成品图…'
  message._foodImagePoll = setInterval(async () => {
    attempts += 1
    try {
      const res = await getFoodImageStatus(taskId, provider)
      if (res.code && res.code !== 200) throw new Error(res.message || '查询生图状态失败')
      const data = res.data || {}
      if (data.progress) message._foodImageLoadingText = `🎨 正在生成成品图…${data.progress}`
      if (data.isFinal) {
        clearInterval(message._foodImagePoll)
        message._foodImagePoll = null
        if (data.resultUrl) {
          applyFoodImage(message, dishName, data.resultUrl, 'generated')
          saveFoodImageCache(dishName, data.resultUrl).catch(() => {})
          ElMessage.success('成品图已生成')
        } else {
          message._foodImageLoading = false
          message._foodImageLoadingText = ''
          ElMessage.warning('生图已结束，但没有返回图片')
        }
      }
      if (attempts >= 60) {
        clearInterval(message._foodImagePoll)
        message._foodImagePoll = null
        message._foodImageLoading = false
        message._foodImageLoadingText = ''
        ElMessage.warning('生图时间较长，请稍后再试')
      }
    } catch (e) {
      clearInterval(message._foodImagePoll)
      message._foodImagePoll = null
      message._foodImageLoading = false
      message._foodImageLoadingText = ''
      ElMessage.error(e?.message || '查询生图状态失败')
    }
  }, 2500)
}

function applyFoodImage(message, dishName, imageUrl, source) {
  message._foodImageUrl = imageUrl
  message._foodImageSource = source
  message._foodImageLoading = false
  message._foodImageLoadingText = ''
  localStorage.setItem('food_img_' + dishName, imageUrl)
  nextTick(scrollBottom)
}

function hydrateFoodImagesForLoadedMessages() {
  if (localStorage.getItem('setting_foodImage') === 'off') return
  messages.value.forEach((m, i) => {
    if (m.role !== 'assistant') return
    const prev = [...messages.value].slice(0, i).reverse().find(item => item.role === 'user')
    const dishName = detectDishName(m.content, prev?.content || '')
    attachLocalFoodImage(m, dishName)
  })
}

function copyMsg(i) {
  const text = messages.value[i]?.content || ''
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success({ message: '✅ 已复制到剪贴板', duration: 1200, offset: 80 })
  }).catch(() => {
    const ta = document.createElement('textarea'); ta.value = text
    document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta)
    ElMessage.success({ message: '✅ 已复制到剪贴板', duration: 1200, offset: 80 })
  })
}

async function exportConversation() {
  const msgs = messages.value
  if (!msgs.length) { ElMessage.info('没有可导出的消息'); return }
  
  let text = 'LifeWise AI \u751f\u6d3b\u52a9\u624b - \u5bf9\u8bdd\u8bb0\u5f55\n'
  text += '\u573a\u666f\uff1a' + (localStorage.getItem('sceneLabel') || '\u751f\u6d3b\u5e38\u8bc6') + '\n'
  text += '\u5bfc\u51fa\u65f6\u95f4\uff1a' + new Date().toLocaleString('zh-CN') + '\n'
  text += '='.repeat(40) + '\n\n'
  
  for (const msg of msgs) {
    if (msg.role === 'user') {
      text += '\ud83d\ude4b \u6211\uff1a' + (msg.content || '(\u56fe\u7247)') + '\n\n'
    } else if (msg.role === 'assistant' && msg.content) {
      let answer = msg.content
      try {
        const parsed = JSON.parse(answer)
        if (parsed.title) answer = parsed.title + '\n' + (parsed.answer || '')
        if (parsed.question) answer = parsed.question + '\n' + answer
      } catch(e) {}
      answer = answer.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim()
      text += '\ud83e\udd16 AI\uff1a' + answer + '\n\n'
    }
  }
  
  text += '='.repeat(40) + '\n'
  text += '\u7531 LifeWise AI \u751f\u6d3b\u52a9\u624b\u751f\u6210\n'
  
   try {
    // 直接下载到本地，不经过后端
    const blob = new Blob([text], { type: "text/plain;charset=utf-8" })
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    const now = new Date()
    const ts2 = now.getFullYear() + String(now.getMonth()+1).padStart(2,"0") + String(now.getDate()).padStart(2,"0") + "_" + String(now.getHours()).padStart(2,"0") + String(now.getMinutes()).padStart(2,"0")
    a.download = "LifeWise对话_" + ts2 + ".txt"
    document.body.appendChild(a); a.click(); document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success("对话已下载到本地")
  } catch(e) {
    ElMessage.error("导出失败: " + (e.message || "未知错误"))
  }}

/**
 * 导出对话为图片（长截图）
 */
async function exportConversationAsImage() {
  const msgs = messages.value
  if (!msgs.length) { ElMessage.info('没有可导出的消息'); return }
  
  try {
    const html2canvas = (await import('html2canvas')).default
    
    // Build a temporary offscreen container with the rendered messages
    const wrapper = document.createElement('div')
    wrapper.style.cssText = 'position:fixed;left:-9999px;top:0;width:420px;padding:20px;background:#fff;font-family:-apple-system,system-ui,sans-serif;font-size:14px;line-height:1.6;z-index:999999'
    document.body.appendChild(wrapper)
    
    // Header
    const hdr = document.createElement('div')
    hdr.style.cssText = 'text-align:center;padding:16px 0;border-bottom:2px solid #22c55e;margin-bottom:16px'
    hdr.innerHTML = '<div style="font-size:20px;font-weight:700;color:#16a34a">🌿 LifeWise · AI 生活助手</div>' +
      '<div style="font-size:12px;color:#999;margin-top:4px">' + (localStorage.getItem('sceneLabel') || '生活常识') + ' · ' + new Date().toLocaleString('zh-CN') + '</div>'
    wrapper.appendChild(hdr)
    
    // Messages
    for (const msg of msgs) {
      const div = document.createElement('div')
      div.style.cssText = 'margin-bottom:16px;padding:12px 14px;border-radius:12px;max-width:95%'
      
      if (msg.role === 'user') {
        div.style.cssText += ';background:#f0fdf4;margin-left:auto;text-align:right;border:1px solid #bbf7d0'
        div.innerHTML = '<div style="font-size:13px;color:#666;margin-bottom:4px">🙋 我</div>' +
          '<div style="color:#1a1a1a">' + (msg.content || '(图片)') + '</div>'
      } else if (msg.role === 'assistant' && msg.content) {
        div.style.cssText += ';background:#fff;border:1px solid #e5e7eb;margin-right:auto'
        // Use the rendered HTML if available, or the content directly
        let html = msg._displayHtml || msg.content
        // Remove action parts that shouldn't render in export
        const t = document.createElement('div'); t.innerHTML = html
        t.querySelectorAll('.rc-followups, .msg-actions, .food-image-loading')?.forEach(el => el.remove())
        html = t.innerHTML
        div.innerHTML = '<div style="font-size:13px;color:#666;margin-bottom:4px">🤖 AI</div>' + html
        // Show food image if available
        if (msg._foodImageUrl) {
          div.innerHTML += '<div style="margin-top:8px"><img src="' + msg._foodImageUrl + '" style="max-width:100%;border-radius:8px" /><div style="font-size:11px;color:#16a34a;margin-top:4px">本地缓存 · 不扣费</div></div>'
        }
      }
      wrapper.appendChild(div)
    }
    
    // Footer
    const ftr = document.createElement('div')
    ftr.style.cssText = 'text-align:center;padding:12px 0;border-top:1px solid #e5e7eb;margin-top:8px;color:#999;font-size:12px'
    ftr.textContent = '由 LifeWise AI 生活助手生成'
    wrapper.appendChild(ftr)
    
    await nextTick()
    
    const canvas = await html2canvas(wrapper, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
      logging: false,
      width: wrapper.scrollWidth,
      height: wrapper.scrollHeight
    })
    
    document.body.removeChild(wrapper)
    
    // Download the image
    const link = document.createElement('a')
    link.download = 'lifewise-conversation-' + Date.now() + '.png'
    link.href = canvas.toDataURL('image/png')
    link.click()
    ElMessage.success('对话图片已下载')
  } catch (e) {
    console.error('[exportImage] err:', e)
    ElMessage.error('导出图片失败: ' + (e.message || '未知错误'))
  }
}

/**
 * 导出对话为 PDF
 */
async function exportConversationAsPdf() {
  const msgs = messages.value
  if (!msgs.length) { ElMessage.info('没有可导出的消息'); return }
  
  try {
    const html2canvas = (await import('html2canvas')).default
    const { jsPDF } = await import('jspdf')
    
    // Build same offscreen container as image export
    const wrapper = document.createElement('div')
    wrapper.style.cssText = 'position:fixed;left:-9999px;top:0;width:420px;padding:20px;background:#fff;font-family:-apple-system,system-ui,sans-serif;font-size:14px;line-height:1.6;z-index:999999'
    document.body.appendChild(wrapper)
    
    // Header
    const hdr = document.createElement('div')
    hdr.style.cssText = 'text-align:center;padding:16px 0;border-bottom:2px solid #22c55e;margin-bottom:16px'
    hdr.innerHTML = '<div style="font-size:20px;font-weight:700;color:#16a34a">🌿 LifeWise · AI 生活助手</div>' +
      '<div style="font-size:12px;color:#999;margin-top:4px">' + (localStorage.getItem('sceneLabel') || '生活常识') + ' · ' + new Date().toLocaleString('zh-CN') + '</div>'
    wrapper.appendChild(hdr)
    
    for (const msg of msgs) {
      const div = document.createElement('div')
      div.style.cssText = 'margin-bottom:16px;padding:12px 14px;border-radius:12px;max-width:95%'
      
      if (msg.role === 'user') {
        div.style.cssText += ';background:#f0fdf4;margin-left:auto;text-align:right;border:1px solid #bbf7d0'
        div.innerHTML = '<div style="font-size:13px;color:#666;margin-bottom:4px">🙋 我</div>' +
          '<div style="color:#1a1a1a">' + (msg.content || '(图片)') + '</div>'
      } else if (msg.role === 'assistant' && msg.content) {
        div.style.cssText += ';background:#fff;border:1px solid #e5e7eb;margin-right:auto'
        let html = msg._displayHtml || msg.content
        const t = document.createElement('div'); t.innerHTML = html
        t.querySelectorAll('.rc-followups, .msg-actions, .food-image-loading')?.forEach(el => el.remove())
        html = t.innerHTML
        div.innerHTML = '<div style="font-size:13px;color:#666;margin-bottom:4px">🤖 AI</div>' + html
        if (msg._foodImageUrl) {
          div.innerHTML += '<div style="margin-top:8px"><img src="' + msg._foodImageUrl + '" style="max-width:100%;border-radius:8px" /><div style="font-size:11px;color:#16a34a;margin-top:4px">本地缓存 · 不扣费</div></div>'
        }
      }
      wrapper.appendChild(div)
    }
    
    const ftr = document.createElement('div')
    ftr.style.cssText = 'text-align:center;padding:12px 0;border-top:1px solid #e5e7eb;margin-top:8px;color:#999;font-size:12px'
    ftr.textContent = '由 LifeWise AI 生活助手生成'
    wrapper.appendChild(ftr)
    
    await nextTick()
    
    const canvas = await html2canvas(wrapper, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
      logging: false,
      width: wrapper.scrollWidth,
      height: wrapper.scrollHeight
    })
    
    document.body.removeChild(wrapper)
    
    // Convert canvas to image and add to PDF
    const imgData = canvas.toDataURL('image/png')
    const imgWidth = 210 // A4 width in mm
    const imgHeight = (canvas.height / canvas.width) * imgWidth
    
    const pdf = new jsPDF('p', 'mm', 'a4')
    let heightLeft = imgHeight
    let position = 0
    const pageHeight = 297 // A4 height in mm
    
    // If content fits in one page
    if (imgHeight <= pageHeight) {
      pdf.addImage(imgData, 'PNG', 0, 0, imgWidth, imgHeight)
    } else {
      // Split across multiple pages
      pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
      heightLeft -= pageHeight
      while (heightLeft > 0) {
        position -= pageHeight
        pdf.addPage()
        pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
        heightLeft -= pageHeight
      }
    }
    
    pdf.save('lifewise-conversation-' + Date.now() + '.pdf')
    ElMessage.success('PDF 已下载')
  } catch (e) {
    console.error('[exportPdf] err:', e)
    ElMessage.error('导出PDF失败: ' + (e.message || '未知错误'))
  }
}

async function shareMsg(i) {
  const msg = messages.value[i]; if (!msg) return
  const prevMsg = i > 0 ? messages.value[i-1] : null
  const question = prevMsg?.role === 'user' ? prevMsg.content : ''
  
  // Get scene label for context
  const sceneLabel = localStorage.getItem('sceneLabel') || '生活常识'
  
  // Prepare HTML for card (strip tags for plain text view)
  let html = msg._displayHtml || renderContent(msg.content)
  
  // Clean up the HTML - remove action buttons, follow-ups etc
  const tempDiv = document.createElement('div')
  tempDiv.innerHTML = html
  // Remove follow-up sections
  tempDiv.querySelectorAll('.rc-followups')?.forEach(el => el.remove())
  html = tempDiv.innerHTML
  
  shareDialog.value = {
    show: true,
    question: question,
    html: html,
    date: new Date().toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }),
    downloading: false
  }
}

async function downloadShareCard() {
  const html2canvas = (await import('html2canvas')).default
  shareDialog.value.downloading = true
  await nextTick()
  try {
    const el = shareCardRef.value
    if (!el) return
    const canvas = await html2canvas(el, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
      logging: false,
      width: el.scrollWidth,
      height: el.scrollHeight
    })
    // 尝试复制图片到剪贴板
    if (typeof ClipboardItem !== 'undefined') {
      try {
        const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
        await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
        ElMessage.success('图片已复制到剪贴板')
        shareDialog.value.downloading = false
        return
      } catch {}
    }
    // 降级1：下载图片
    try {
      const link = document.createElement('a')
      link.download = 'lifewise-card-' + Date.now() + '.png'
      link.href = canvas.toDataURL('image/png')
      link.click()
      ElMessage.success('图片已下载')
      shareDialog.value.downloading = false
      return
    } catch {}
    // 降级2：新标签打开（内置浏览器最稳妥）
    const dataUrl = canvas.toDataURL('image/png')
    window.open(dataUrl, '_blank')
    ElMessage.success('图片已在新标签打开，可右键保存')
  } catch (e) {
    ElMessage.error('生成分享图片失败')
    console.error(e)
  } finally {
    shareDialog.value.downloading = false
  }
}

function copyShareText() {
  const d = shareDialog.value
  let text = '🌿 LifeWise · AI 生活助手\n'
  if (d.question) text += '💬 ' + d.question + '\n'
  text += '━━━━━━━━━━━━━━\n'
  // Strip HTML tags for plain text
  const temp = document.createElement('div')
  temp.innerHTML = d.html
  text += temp.textContent || temp.innerText || ''
  text += '\n━━━━━━━━━━━━━━\n'
  text += 'via LifeWise - ' + d.date
  
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('文本已复制，可以粘贴分享')
  }).catch(() => {
    const ta = document.createElement('textarea'); ta.value = text
    document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta)
    ElMessage.success('文本已复制')
  })
}
async function toggleFavorite(i) {
  const msg = messages.value[i]; if (!msg) return
  if (msg._faved) {
    msg._favProcessing = true
    try { await removeFavorite(msg._id); msg._faved = false; msg._favProcessing = false; ElMessage.success('已取消收藏') }
    catch(e) { msg._favProcessing = false; ElMessage.error(e?.response?.data?.message || '操作失败，请重试') }
  } else {
    favDialog.value.selected = 'other'
    favDialog.value.msgIndex = i
    favDialog.value.show = true
  }
}

async function confirmFavorite() {
  const i = favDialog.value.msgIndex
  const msg = messages.value[i]; if (!msg) return
  favDialog.value.show = false
  try {
    await addFavorite(msg._id, '', favDialog.value.selected)
    msg._faved = true
    ElMessage.success('已收藏')
  } catch(e) { console.error('[favorite] err:',e); ElMessage.error('收藏失败，请重试') }
}

async function addMsgToKnowledge(i) {
  const msg = messages.value[i]
  if (!msg || msg.role !== 'assistant') return
  const prev = [...messages.value].slice(0, i).reverse().find(m => m.role === 'user')
  if (!prev?.content) {
    ElMessage.warning('没有找到对应的问题')
    return
  }
  msg._kbSaving = true
  try {
    await addKnowledge({
      question: prev.content,
      answer: msg.content || '',
      scene: localStorage.getItem('scene') || tempScene.value || 'other'
    })
    msg._kbSaved = true
    ElMessage.success('已加入常识库')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '入库失败')
  } finally {
    msg._kbSaving = false
  }
}

async function shareToSocial() {
  const d = shareDialog.value
  if (!d) return
  shareDialog.value.sharing = true
  
  try {
    // Build formatted share text
    let text = '?? LifeWise \u00b7 AI \u751f\u6d3b\u52a9\u624b\n'
    if (d.question) text += '\ud83d\udcac ' + d.question + '\n'
    text += '\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n'
    const temp = document.createElement('div')
    temp.innerHTML = d.html
    text += (temp.textContent || temp.innerText || '').trim()
    text += '\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n'
    text += 'via LifeWise'
    
    // Try Web Share API first (mobile native share - supports WeChat, WhatsApp, etc.)
    if (navigator.share) {
      const shareData = { title: 'LifeWise', text }
      if (route.params.id) {
        shareData.url = window.location.origin + '/chat/' + route.params.id
      }
      
      // Try to include image
      try {
        const el = shareCardRef.value
        if (el) {
          const html2canvas = (await import('html2canvas')).default
          const canvas = await html2canvas(el, { scale: 2, backgroundColor: '#ffffff', useCORS: true })
          const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
          if (blob && navigator.canShare && navigator.canShare({ files: [new File([blob], 'lifewise.png', { type: 'image/png' })] })) {
            shareData.files = [new File([blob], 'lifewise.png', { type: 'image/png' })]
          }
        }
      } catch {}
      
      try {
        await navigator.share(shareData)
        ElMessage.success('\u5df2\u6253\u5f00\u5206\u4eab\u9762\u677f')
        shareDialog.value.show = false
        return
      } catch (e) {
        if (e.name !== 'AbortError') {
          // Web Share failed, fall through to fallback
        } else {
          shareDialog.value.sharing = false
          return // User cancelled
        }
      }
    }
    
    // Fallback: copy text and notify
    navigator.clipboard.writeText(text).then(() => {
      ElMessage.success('\u6587\u672c\u5df2\u590d\u5236\uff0c\u53ef\u4ee5\u7c98\u8d34\u5230\u5fae\u4fe1\u3001\u5fae\u535a\u7b49\u793e\u4ea4\u5e73\u53f0')
    }).catch(() => {
      const ta = document.createElement('textarea'); ta.value = text
      document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta)
      ElMessage.success('\u6587\u672c\u5df2\u590d\u5236')
    })
  } finally {
    shareDialog.value.sharing = false
  }
}


async function fillPrompt(prompt) {
  inputText.value = prompt
  promptPrefilled.value = true
  await nextTick()
  inputRef.value?.focus()
}

async function fillImagePrompt(prompt, scene) {
  tempScene.value = scene
  await fillPrompt(prompt)
}

function onUserInput() {
  promptPrefilled.value = false
}

function setAskMode(isFollowUp) {
  pendingFollowUp.value = isFollowUp
}

function handleFollowUpClick(e) {
  const chip = e.target.closest('.rc-followup-chip')
  if (chip) {
    pendingFollowUp.value = true
    inputText.value = chip.textContent
    send()
  }
}

function triggerUpload() { fileInput.value?.click() }
function handleFileSelect(e) {
  const file = e.target.files?.[0]; if (!file) return
  const maxSize = 10 * 1024 * 1024
  if (!file.type?.startsWith('image/')) {
    ElMessage.warning('只能上传图片文件')
    e.target.value = ''
    return
  }
  if (file.size > maxSize) {
    ElMessage.warning('图片不能超过 10MB，请压缩后再上传')
    e.target.value = ''
    return
  }
  pendingFile.value = file
  if (!inputText.value.trim()) {
    inputText.value = '请分析这张图片'
  }
  const reader = new FileReader()
  reader.onload = (ev) => { pendingImage.value = ev.target.result }
  reader.onerror = () => {
    pendingFile.value = null
    pendingImage.value = null
    ElMessage.error('图片读取失败，请换一张图片')
  }
  reader.readAsDataURL(file); e.target.value = ''
}
function imgUrl(url) {
  if (!url) return ''
  if (url.startsWith('http') || url.startsWith('data:')) return url
  if (url.startsWith('/')) return url
  return '/uploads/' + url
}
function startVoice() {
  // If already listening, stop manually
  if (isListening.value) {
    if (window._voiceRecognition) {
      try { window._voiceRecognition.stop() } catch {}
      window._voiceRecognition = null
    }
    isListening.value = false
    ElMessage.info('⏹️ 已停止语音输入')
    return
  }

  if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {
    ElMessage.warning('当前浏览器不支持语音识别，建议用 Chrome 浏览器打开 http://localhost:5173')
    return
  }

  // Check permission first
  if (navigator.permissions) {
    navigator.permissions.query({ name: 'microphone' }).then(result => {
      if (result.state === 'denied') {
        ElMessage.error('❌ 麦克风权限已被禁用，请在浏览器地址栏左侧点击 🔒 或 ⓘ 图标，开启"麦克风"权限后刷新页面重试')
        return
      }
    }).catch(() => {})
  }

  // Stop any previous instance first
  if (window._voiceRecognition) {
    try { window._voiceRecognition.abort() } catch {}
    window._voiceRecognition = null
  }

  // Create new instance
  const r = new (window.webkitSpeechRecognition || window.SpeechRecognition)()
  window._voiceRecognition = r
  r.lang = 'zh-CN'
  r.continuous = true  // continuous mode — 说完自动结束更自然
  r.interimResults = true

  isListening.value = true
  let retryCount = 0
  const maxRetries = 2

  ElMessage.info('🎤 请说话... (点击麦克风可手动停止)')

  r.onresult = (e) => {
    let t = ''
    for (let i = e.resultIndex; i < e.results.length; i++) {
      t += e.results[i][0].transcript
    }
    inputText.value = t
  }

  r.onerror = (e) => {
    if (e.error === 'not-allowed') {
      isListening.value = false; window._voiceRecognition = null
      ElMessage.error('❌ 麦克风被拒绝，请在浏览器地址栏左侧点击 🔒 开启麦克风权限后刷新页面')
    } else if (e.error === 'no-speech') {
      if (retryCount < maxRetries) {
        retryCount++
        ElMessage.info(`🔁 没听到，第${retryCount}次重试...`)
        setTimeout(() => { try { r.start() } catch {} }, 500)
      } else {
        isListening.value = false; window._voiceRecognition = null
        ElMessage.warning('没听到说话，请检查麦克风是否正常后点击麦克风重试')
      }
    } else if (e.error === 'aborted') {
      // Only show error if we didn't intentionally abort
      if (window._voiceRecognition) {
        isListening.value = false; window._voiceRecognition = null
        ElMessage.warning('⚠️ 语音被中断，点击麦克风重试')
      }
    } else {
      isListening.value = false; window._voiceRecognition = null
      ElMessage.error('语音识别失败: ' + e.error)
    }
  }

  r.onend = () => {
    isListening.value = false
    if (window._voiceRecognition) {
      window._voiceRecognition = null
      if (inputText.value.trim()) {
        setTimeout(() => send(), 300)
      }
    }
  }

  try {
    r.start()
  } catch (e) {
    isListening.value = false; window._voiceRecognition = null
    ElMessage.error('启动语音识别失败: ' + e.message)
  }
}

function goBack() { router.back() }
function scrollBottom() { nextTick(() => { if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight }) }

// ===== 渲染 =====
function renderContent(content) {
  if (!content) return ''
  const data = tryParseJsonSafe(content)
  if (data) return renderStructured(data)
  return renderMarkdown(content)
}

function detectDishName(aiContent, userQuestion = '') {
  const parsed = tryParseJsonSafe(aiContent)
  if (parsed && parsed.title && (parsed.steps || parsed.ingredients)) {
    return String(parsed.title).trim()
  }
  const text = `${userQuestion || ''}\n${aiContent || ''}`
  return prebuiltFoodNames.find(name => text.includes(name)) || ''
}

function renderMarkdown(text) {
  const sceneLabel = localStorage.getItem("sceneLabel") || ""
  let header = sceneLabel ? '<div class="md-scene-label">✦ ' + esc(sceneLabel) + '</div>' : ''
  let html = (text || "");
  html = html.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;")
  // Code blocks (must be before inline code)
  html = html.replace(/```(\w*)\n?([\s\S]*?)```/g, '<pre class="md-code-block"><code class="lang-$1">$2</code></pre>')
  // Inline code
  html = html.replace(/`([^`]+)`/g, "<code>$1</code>")
  // Headings
  html = html.replace(/^#### (.+)$/gm, "<h5>$1</h5>")
    .replace(/^### (.+)$/gm, "<h4>$1</h4>")
    .replace(/^## (.+)$/gm, "<h3>$1</h3>")
    .replace(/^# (.+)$/gm, "<h2>$1</h2>")
  // Tables: | col1 | col2 | ... | (need to handle before line breaks)
  html = html.replace(/^\|(.+)\|$/gm, function(m){ return '<tr>' + m.slice(1,-1).split('|').map(function(c){ return '<td>' + c.trim() + '</td>' }).join('') + '</tr>' })
  html = html.replace(/<tr>\s*<td>[-:\s]+<\/td>(?:\s*<td>[-:\s]+<\/td>)+\s*<\/tr>/g, '')
  html = html.replace(/(<tr>.*?<\/tr>\n?)+/g, '<table class="md-table">$1</table>')
  // Links: [text](url)
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
  // Bold & italic
  html = html.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/\*(.+?)\*/g, "<em>$1</em>")
  // Blockquote
  html = html.replace(/^> (.+)$/gm, "<blockquote>$1</blockquote>")
  // Horizontal rule
  html = html.replace(/^(?:---|\*\*\*|___)\s*$/gm, "<hr>")
  // Unordered list
  html = html.replace(/^[\s]*[-*+]\s+(.+)$/gm, "<li>$1</li>")
  // Ordered list
  html = html.replace(/^[\s]*\d+\.\s+(.+)$/gm, "<li>$1</li>")
  // Wrap consecutive <li> in <ul>
  html = html.replace(/((?:<li>.*?<\/li>\n?)+)/g, '<ul class="md-list">$1</ul>')
  // Convert line breaks (but not inside pre/code/table)
  html = html.replace(/\n/g, "<br>")
  // Cleanup: remove <br> before/after block elements
  html = html.replace(/<br><\/(h[2345]|ul|ol|blockquote|table|pre)>/g, "</$1>")
    .replace(/<(h[2345]|ul|ol|blockquote|table|pre)><br>/g, "<$1>")
    .replace(/<\/li><br>/g, "</li>")
    .replace(/<br><li>/g, "<li>")
  return header + '<div class="md-content writing-content">' + html + "</div>"
}

function tryParseJsonSafe(text) {
  if (!text) return null
  // Helper: try parse with trailing comma fix
  function tryParse(s) {
    try { const obj = JSON.parse(s); if (obj && typeof obj === 'object') return obj } catch {}
    try { const fixed = s.replace(/,([\s\n\r]*[}\]])/g, '$1'); const obj = JSON.parse(fixed); if (obj && typeof obj === 'object') return obj } catch {}
    return null
  }
  // Step 1: direct parse (trim first)
  let r = tryParse(text.trim()); if (r) return r
  // Step 2: remove code fences
  let clean = text.replace(/```(?:json)?\s*/gi, '').replace(/```/g, '').trim()
  r = tryParse(clean); if (r) return r
  // Step 3: regex extract JSON
  const m = clean.match(/\{[\s\S]*\}/)
  if (m) {
    r = tryParse(m[0]); if (r) return r
    r = tryParse(m[0].replace(/[\x00-\x1f\x7f-\x9f]/g, '')); if (r) return r
  }
  return null
}
function isJsonContent(text) {
  return text && (text.trim().startsWith("{")
  || text.trim().startsWith("["))
}


function renderStructured(data) {
  const parts = []
  // ===== 1. Detect scene =====
  let scene = "general", sceneIcon = "\u{1f4ac}", sceneLabel = "\u751f\u6d3b\u5e38\u8bc6"
  let title = data.title || ""
  if (data.ingredients && data.steps) { scene = "cooking"; sceneIcon = "\u{1f373}"; sceneLabel = "\u505a\u996d\u52a9\u624b" }
  else if (data.occasion || data.outfits || data.color_palette) { scene = "fashion"; sceneIcon = "\u{1f454}"; sceneLabel = "\u7a7f\u642d\u6307\u5357" }
  else if (data.disclaimer || data.when_to_see_doctor || data.category === "\u75c7\u72b6\u5904\u7406" || data.category === "\u7528\u836f\u5e38\u8bc6" || data.category === "\u8425\u517b\u5efa\u8bae" || data.category === "\u6025\u6551\u77e5\u8bc6" || (data.prevention && !data.ingredients && !data.outfits && !data.selection_steps && !data.tools)) { scene = "health"; sceneIcon = "\u{1f49a}"; sceneLabel = "\u5065\u5eb7\u5e38\u8bc6" }
  else if (data.selection_steps || data.category || data.\u54c1\u7c7b) { scene = "shopping"; sceneIcon = "\u{1f6d2}"; sceneLabel = "\u8d2d\u7269\u6311\u9009" }
  else if (data.problem && (data.tools || (data.steps && data.severity))) { scene = "repair"; sceneIcon = "\u{1f527}"; sceneLabel = "\u4fee\u7406\u6307\u5357" }
  else if (data.problem && (data.materials || data.difficulty)) { scene = "housework"; sceneIcon = "\u{1f9f9}"; sceneLabel = "\u5bb6\u52a1\u6280\u5de7" }
  else if (data.do_list || data.dont_list || data.key_principles || (data.occasion && (data.occasion.includes("\u793c") || data.occasion.includes("\u5e94\u916c") || data.occasion.includes("\u5bb4") || data.occasion.includes("\u89c1\u5bb6\u957f")))) { scene = "etiquette"; sceneIcon = "\u{1f91d}"; sceneLabel = "\u793e\u4ea4\u793c\u4eea" }
  else if (data.pet_type || data.when_to_see_vet || data.topic === "\u5ba0\u7269") { scene = "pet"; sceneIcon = "\u{1f431}"; sceneLabel = "\u5ba0\u7269\u7167\u987e" }
  else if (data.weekly_plan || data.shopping_list) { scene = "mealplan"; sceneIcon = "\u{1f4c5}"; sceneLabel = "\u98df\u8c31\u89c4\u5212" }
  // For fashion, dont use occasion as title (shown as tag)
  if (!title) title = data.\u54c1\u7c7b || data.problem || data.question || data.topic || ""
  if (!title && scene !== "fashion") title = data.occasion || ""
  // ===== 2. Card header =====
  let iconGrad = "s-icon-" + scene
  parts.push(`<div class="s-hd"><div class="s-hd-icon ${iconGrad}">${sceneIcon}</div><div class="s-hd-meta"><div class="s-hd-label">${sceneLabel}</div><div class="s-hd-title">${esc(title)}</div></div></div>`)
  parts.push('<div class="s-bd">')
  // ===== 3. Tags row =====
  if (data.difficulty || data.time || data.servings) {
    let tags = ""
    if (data.difficulty) tags += `<span class="s-tag">\u{1f4ca} ${esc(data.difficulty)}</span>`
    if (data.time) tags += `<span class="s-tag s-tag-time">\u23f1\ufe0f ${esc(data.time)}</span>`
    if (data.servings) tags += `<span class="s-tag">\u{1f465} ${esc(data.servings)}</span>`
    if (tags) parts.push(`<div class="s-tags">${tags}</div>`)
  }
  // ===== 4. Scene sections =====
  if (data.ingredients && data.ingredients.length) {
    parts.push('<div class="s-sec">\u{1f958} \u98df\u6750</div><div class="s-ing-list">')
    data.ingredients.forEach(item => {
      const note = item.note ? ` <span class="s-ing-note">\u{1f4a1} ${esc(item.note)}</span>` : ""
      const alt = item.alternative ? ` <span class="s-ing-note">\uff08\u53ef\u7528${esc(item.alternative)}\u66ff\u4ee3\uff09</span>` : ""
      parts.push(`<div class="s-ing-item"><span class="s-ing-cb"></span><span>${esc(item.name)}</span>${item.amount ? `<span class="s-ing-amt">${esc(item.amount)}</span>` : ""}${note}${alt}</div>`)
    })
    parts.push("</div>")
  }
  if (data.materials && data.materials.length) {
    parts.push('<div class="s-sec">\u{1f4e6} \u6240\u9700\u6750\u6599</div><div class="s-ing-list">')
    data.materials.forEach(m => {
      if (typeof m === "string") parts.push(`<div class="s-ing-item"><span class="s-ing-cb"></span><span>${esc(m)}</span></div>`)
      else {
        const alt = m.alternative ? ` <span class="s-ing-note">\uff08\u53ef\u7528${esc(m.alternative)}\u66ff\u4ee3\uff09</span>` : ""
        parts.push(`<div class="s-ing-item"><span class="s-ing-cb"></span><span>${esc(m.name)}</span>${alt}</div>`)
      }
    })
    parts.push("</div>")
  }
  if (data.steps && data.steps.length) {
    const stepIcon = scene === "cooking" ? "\u{1f468}\u200d\u{1f373}" : scene === "repair" ? "\u{1f527}" : scene === "pet" ? "\u{1f431}" : "\u{1f4cb}"
    const stepLabel = scene === "pet" ? "\u7167\u6599\u6b65\u9aa4" : scene === "cooking" ? "\u505a\u6cd5\u6b65\u9aa4" : "\u6b65\u9aa4"
    parts.push(`<div class="s-sec">${stepIcon} ${stepLabel}</div><div class="s-step-list">`)
    data.steps.forEach(s => {
      const stepNum = s.step || ""
      const stepAction = esc(s.action || s || "")
      const stepTime = s.time ? `<span class="s-step-time">\u23f1 ${esc(s.time)}</span>` : ""
      const stepTip = s.tip ? `<span class="s-step-tip">\u{1f4a1} ${esc(s.tip)}</span>` : ""
      const stepWarn = s.warning ? `<span class="s-step-warn">\u26a0\ufe0f ${esc(s.warning)}</span>` : ""
      parts.push(`<div class="s-step-item"><div class="s-step-num">${stepNum}</div><div class="s-step-body">${stepAction}${stepTime}${stepTip}${stepWarn}</div></div>`)
    })
    parts.push("</div>")
  }
  if (data.selection_steps && data.selection_steps.length) {
    parts.push('<div class="s-sec">\u{1f50d} \u6311\u9009\u6b65\u9aa4</div><div class="s-step-list">')
    data.selection_steps.forEach(s => {
      const fc = s.step_name ? s.step_name.trim().charAt(0) : "\u00b7"
      const nm = s.step_name ? `<strong>${esc(s.step_name)}</strong> \u2014 ` : ""
      parts.push(`<div class="s-select-item"><div class="s-select-num">${fc}</div><div class="s-step-body">${nm}${esc(s.action)}</div></div>`)
    })
    parts.push("</div>")
  }
  if (data.tools && data.tools.length) {
    parts.push('<div class="s-sec">\u{1f527} \u6240\u9700\u5de5\u5177</div><div class="s-tool-list">')
    data.tools.forEach(t => {
      if (typeof t === "string") parts.push(`<span class="s-tool-tag">${esc(t)}</span>`)
      else {
        let label = esc(t.name || t)
        if (t.alternative) label += ' <span class="s-ing-note">(\u53ef\u7528' + esc(t.alternative) + '\u66ff\u4ee3)</span>'
        parts.push(`<span class="s-tool-tag">${label}</span>`)
      }
    })
    parts.push("</div>")
  }
    // ===== Fashion mood board =====
  if (data.occasion || data.style) {
    const tagText = data.occasion || data.style
    let tagBg = "#f0e6ff"
    let tagColor = "#7c3aed"
    if (tagText.includes("休闲") || tagText.includes("出游")) { tagBg = "#fef7e0"; tagColor = "#e37400" }
    else if (tagText.includes("职场") || tagText.includes("面试") || tagText.includes("商务")) { tagBg = "#e8f0fe"; tagColor = "#1967d2" }
    else if (tagText.includes("运动") || tagText.includes("健身")) { tagBg = "#e6f4ea"; tagColor = "#1e8e3e" }
    else if (tagText.includes("约会") || tagText.includes("宴会") || tagText.includes("晚宴")) { tagBg = "#fce8e6"; tagColor = "#c5221f" }
    parts.push(`<div class="s-fashion-occasion" style="background:${tagBg};color:${tagColor}">${esc(tagText)}</div>`)
  }
  if (data.color_palette && data.color_palette.length) {
    parts.push('<div class="s-sec">\u{1f3a8} \u63a8\u8350\u914d\u8272</div><div class="s-color-row">')
    data.color_palette.forEach(c => {
      const cn = c.name || c.color || (typeof c === "string" ? c : "")
      const ch = nameToHex(cn) || "#ccc"
      parts.push(`<div class="s-color-dot-lg" style="background:${ch}"><span class="s-color-label">${esc(cn)}</span></div>`)
    })
    parts.push("</div>")
  }
  if (data.outfits && data.outfits.length) {
    parts.push('<div class="s-sec">\u{1f454} \u63a8\u8350\u7a7f\u642d</div><div class="s-mb-list">')
    data.outfits.forEach(o => {
      const on = o.piece || o.name || ""
      const od = o.description || o.detail || ""
      const oc = o.color || ""
      const oi = outfitIcon(on)
      const colorDot = oc ? `<span class="s-mb-c" style="background:${nameToHex(oc)}"></span>` : ""
      let iconBg = "#e3f2fd"
      if (on.includes("裤") || on.includes("裙") || on.includes("半身")) iconBg = "#e8eaf6"
      else if (on.includes("外套") || on.includes("夹克") || on.includes("西装") || on.includes("大衣")) iconBg = "#f3e5f5"
      else if (on.includes("鞋") || on.includes("靴") || on.includes("运动鞋")) iconBg = "#fce4ec"
      else if (on.includes("配饰") || on.includes("表") || on.includes("项链") || on.includes("耳")) iconBg = "#fff3e0"
      else if (on.includes("包") || on.includes("袋")) iconBg = "#e0f2f1"
      parts.push(`<div class="s-mb-item"><div class="s-mb-icon" style="background:${iconBg}">${oi}</div><div class="s-mb-ifo"><div class="s-mb-n">${esc(on)}</div>${od ? `<div class="s-mb-d">${esc(od)}${colorDot}</div>` : ""}</div></div>`)
    })
    parts.push("</div>")
  }
  if (data.items && data.items.length) {
    parts.push('<div class="s-sec">\u{1f9e5} \u5355\u54c1\u63a8\u8350</div><div class="s-mb-list">')
    data.items.forEach(it => {
      const in_ = it.name || it.item || ""
      const id = it.description || it.recommendation || it.detail || it.note || ""
      const ic = it.color || ""
      const ii = outfitIcon(in_)
      const colorDot = ic ? `<span class="s-mb-c" style="background:${nameToHex(ic)}"></span>` : ""
      let iconBg = "#e3f2fd"
      if (in_.includes("裤") || in_.includes("裙")) iconBg = "#e8eaf6"
      else if (in_.includes("外套") || in_.includes("夹克")) iconBg = "#f3e5f5"
      else if (in_.includes("鞋")) iconBg = "#fce4ec"
      else if (in_.includes("包")) iconBg = "#e0f2f1"
      parts.push(`<div class="s-mb-item"><div class="s-mb-icon" style="background:${iconBg}">${ii}</div><div class="s-mb-ifo"><div class="s-mb-n">${esc(in_)}</div>${id ? `<div class="s-mb-d">${esc(id)}${colorDot}</div>` : ""}</div></div>`)
    })
    parts.push("</div>")
  }
  if (data.accessories && data.accessories.length) {
    parts.push('<div class="s-sec">\u{1f48d} \u914d\u9970\u63a8\u8350</div><div class="s-mb-list">')
    data.accessories.forEach(a => {
      const an = a.name || a.item || ""
      const ad = a.description || a.recommendation || a.note || ""
      const ac = a.color || ""
      const colorDot = ac ? `<span class="s-mb-c" style="background:${nameToHex(ac)}"></span>` : ""
      parts.push(`<div class="s-mb-item"><div class="s-mb-icon" style="background:#fff3e0">\u{1f48d}</div><div class="s-mb-ifo"><div class="s-mb-n">${esc(an)}</div>${ad ? `<div class="s-mb-d">${esc(ad)}${colorDot}</div>` : ""}</div></div>`)
    })
    parts.push("</div>")
  }
  
  // ===== Health: category tag (from AI field: category) =====
  if (scene === "health" && data.category) {
    let catColors = {"\u75c7\u72b6\u5904\u7406":"#e3f2fd","\u7528\u836f\u5e38\u8bc6":"#fce4ec","\u8425\u517b\u5efa\u8bae":"#e8f5e9","\u6025\u6551\u77e5\u8bc6":"#fff3e0"}
    let catBg = catColors[data.category] || "#f5f5f5"
    parts.push(`<span class="s-health-cat" style="background:${catBg}">\u{1f3f7}\ufe0f ${esc(data.category)}</span>`)
  }
  // ===== Health: disclaimer (from AI field: disclaimer) =====
  if (data.disclaimer) parts.push(`<div class="s-disclaimer">\u26a0\ufe0f ${esc(data.disclaimer)}</div>`)
  // ===== Health: symptoms list (from AI field: symptoms) =====
  if (data.symptoms && data.symptoms.length) {
    parts.push('<div class="s-sec">\u{1f9a0} \u5e38\u89c1\u75c7\u72b6</div><div class="s-mistake-list">')
    data.symptoms.forEach(s => parts.push(`<div class="s-mistake-item">${esc(s)}</div>`))
    parts.push("</div>")
  }
  // ===== Health: causes list (from AI field: causes) =====
  if (data.causes && data.causes.length) {
    parts.push('<div class="s-sec">\u{1f50d} \u53ef\u80fd\u539f\u56e0</div><div class="s-mistake-list">')
    data.causes.forEach(c => parts.push(`<div class="s-mistake-item">${esc(c)}</div>`))
    parts.push("</div>")
  }
  // ===== Health: advice steps (from AI field: advice) =====
  if (data.advice && data.advice.length) {
    parts.push('<div class="s-sec">\u{1f4a1} \u5efa\u8bae\u6b65\u9aa4</div><div class="s-step-list">')
    data.advice.forEach((a, ai) => {
      const aItem = esc(a.item || a.action || a.name || "")
      const aDetail = a.detail ? ` <span class="s-ing-note">${esc(a.detail)}</span>` : ""
      parts.push(`<div class="s-step-item"><div class="s-step-num">${ai+1}</div><div class="s-step-body">${aItem}${aDetail}</div></div>`)
    })
    parts.push("</div>")
  }
  // ===== Health: when_to_see_doctor (from AI field: when_to_see_doctor) =====
  if (data.when_to_see_doctor) parts.push(`<div class="s-sec">\u{1f3e5} \u4ec0\u4e48\u60c5\u51b5\u8981\u770b\u533b\u751f</div><div class="s-text">${esc(data.when_to_see_doctor)}</div>`)
  // ===== Health: prevention is handled generically below =====

  // ===== Etiquette: do_list =====
  if (data.do_list && data.do_list.length) {
    parts.push('<div class="s-sec">\u2705 \u5e94\u8be5\u505a</div><div class="s-mb-list">')
    data.do_list.forEach(d => {
      const da = esc(d.action || d)
      const dr = d.reason ? `<div class="s-mb-d">${esc(d.reason)}</div>` : ""
      parts.push(`<div class="s-et-item"><div class="s-et-do">\u2705</div><div><div class="s-mb-n">${da}</div>${dr}</div></div>`)
    })
    parts.push("</div>")
  }
  // ===== Etiquette: dont_list =====
  if (data.dont_list && data.dont_list.length) {
    parts.push('<div class="s-sec">\u274c \u4e0d\u5e94\u8be5\u505a</div><div class="s-mb-list">')
    data.dont_list.forEach(d => {
      const da = esc(typeof d === "string" ? d : d.action || d.item || "")
      parts.push(`<div class="s-et-item"><div class="s-et-dont">\u274c</div><div class="s-mb-n">${da}</div></div>`)
    })
    parts.push("</div>")
  }
  // ===== Etiquette: key_principles =====
  if (data.key_principles && data.key_principles.length) {
    parts.push('<div class="s-sec">\u{1f4d6} \u5173\u952e\u539f\u5219</div><div class="s-mistake-list">')
    data.key_principles.forEach(p => parts.push(`<div class="s-mistake-item">${esc(p)}</div>`))
    parts.push("</div>")
  }
  // ===== Etiquette: cultural_notes =====
  if (data.cultural_notes) parts.push(`<div class="s-sec">\u{1f30d} \u6587\u5316\u8bf4\u660e</div><div class="s-text">${esc(data.cultural_notes)}</div>`)
  // ===== Mealplan: weekly_plan =====
  if (data.weekly_plan && data.weekly_plan.length) {
    parts.push('<div class="s-sec">\u{1f4c5} \u6bcf\u5468\u8ba1\u5212</div>')
    data.weekly_plan.forEach(day => {
      parts.push(`<div class="s-plan-day"><div class="s-plan-day-label">${esc(day.day || "")}</div>`)
      if (day.meals && day.meals.length) {
        day.meals.forEach(m => {
          const mt = m.type ? `<span class="s-plan-meal-type">${esc(m.type)}</span>` : ""
          const mn = esc(m.name || "")
          const time = m.time ? ` \u23f1 ${esc(m.time)}` : ""
          const diff = m.difficulty ? ` \u{1f4ca} ${esc(m.difficulty)}` : ""
          parts.push(`<div class="s-plan-meal">${mt}${mn}${time}${diff}</div>`)
        })
      }
      parts.push("</div>")
    })
  }
  // ===== Mealplan: shopping_list =====
  if (data.shopping_list && data.shopping_list.length) {
    parts.push('<div class="s-sec">\u{1f6d2} \u8d2d\u7269\u6e05\u5355</div>')
    data.shopping_list.forEach(item => {
      if (typeof item === "string") parts.push(`<div class="s-text-line">\u00b7 ${esc(item)}</div>`)
      else {
        const cat = item.category ? `<div class="s-shop-cat">${esc(item.category)}</div>` : ""
        const items_html = item.items ? item.items.map(i => `<div class="s-text-line">\u00b7 ${esc(i)}</div>`).join("") : ""
        parts.push(cat + items_html)
      }
    })
  }
if (data.common_mistakes && data.common_mistakes.length) {
    parts.push('<div class="s-sec">\u26a0\ufe0f \u5e38\u89c1\u8bef\u533a</div><div class="s-mistake-list">')
    data.common_mistakes.forEach(m => parts.push(`<div class="s-mistake-item">${esc(m)}</div>`))
    parts.push("</div>")
  }
  if (data.common_causes && data.common_causes.length) {
    parts.push('<div class="s-sec">\u{1f50d} \u5e38\u89c1\u539f\u56e0</div><div class="s-mistake-list">')
    data.common_causes.forEach(m => parts.push(`<div class="s-mistake-item">${esc(m)}</div>`))
    parts.push("</div>")
  }
  if (data.season) parts.push(`<span class="s-season-tag">\u{1f33f} ${esc(data.season)}</span>`)
  if (data.storage_tip) parts.push(`<div class="s-sec">\u{1f4e6} \u4fdd\u5b58\u65b9\u6cd5</div><div class="s-text">${esc(data.storage_tip)}</div>`)
  if (data.summary_slogan) parts.push(`<div class="s-slogan">${esc(data.summary_slogan)}</div>`)
  if (data.severity) {
    const sv = data.severity.includes("\u8f7b\u5fae") ? "s-sev-low" : data.severity.includes("\u4e25\u91cd") ? "s-sev-high" : "s-sev-med"
    parts.push(`<div class="s-sec">\u26a1 \u4e25\u91cd\u7a0b\u5ea6</div><span class="${sv}">${esc(data.severity)}</span>`)
  }
  if (data.professional_advice) parts.push(`<div class="s-sec">\u{1f3e5} \u9700\u8981\u627e\u4e13\u4e1a\u4eba\u5458\u7684\u60c5\u51b5</div><div class="s-text">${esc(data.professional_advice)}</div>`)
  if (data.prevention && typeof data.prevention === "string" && data.prevention.trim()) parts.push(`<div class="s-sec">\u{1f6e1}\ufe0f \u5982\u4f55\u9884\u9632</div><div class="s-text">${esc(data.prevention)}</div>`)
  if (data.principle) parts.push(`<div class="s-sec">\u{1f4a1} \u539f\u7406\u8bf4\u660e</div><div class="s-text">${esc(data.principle)}</div>`)
  if (data.key_point) parts.push(`<div class="s-key">\u{1f525} ${esc(data.key_point)}</div>`)
  if (data.safety_tip) parts.push(`<div class="s-safety">\u26a0\ufe0f ${esc(data.safety_tip)}</div>`)
  if (data.answer) parts.push(`<div class="s-answer">${esc(data.answer).replace(/\n/g, "<br>")}</div>`)
  if (data.suggestions) {
    parts.push('<div class="s-sec">\u{1f4a1} \u5efa\u8bae</div>')
    const sa = Array.isArray(data.suggestions) ? data.suggestions : [data.suggestions]
    sa.forEach(s => {
      if (typeof s === "string") parts.push(`<div class="s-text-line">\u00b7 ${esc(s)}</div>`)
      else parts.push(`<div class="s-text-line">\u00b7 <strong>${esc(s.item || s.name || "")}</strong>${(s.detail || s.description) ? "\uff1a" + esc(s.detail || s.description) : ""}</div>`)
    })
  }
  if (data.tips) {
    parts.push('<div class="s-sec">\u{1f4a1} \u5c0f\u8d34\u58eb</div>')
    const ta = Array.isArray(data.tips) ? data.tips : [data.tips]
    ta.forEach(t => parts.push(`<div class="s-text-line">\u00b7 ${esc(t)}</div>`))
  }
  const knownKeys = ["title","difficulty","time","servings","id","_id","__v","createdAt","updatedAt","problem","question","occasion","ingredients","steps","selection_steps","tools","tips","key_point","safety_tip","answer","suggestions","followUps","style","items","outfits","color_palette","accessories","materials","recommendations","category","tags","season","common_mistakes","storage_tip","summary_slogan","severity","need_professional","professional_advice","prevention","difficulty","servings","avoid","\u54c1\u7c7b","common_causes","principle","estimated_time","disclaimer","symptoms","causes","advice","when_to_see_doctor","do_list","dont_list","key_principles","cultural_notes","pet_type","topic","when_to_see_vet","weekly_plan","shopping_list","preference"]
  Object.keys(data).forEach(k => {
    if (knownKeys.includes(k)) return
    const v = data[k]
    if (v == null) return
    if (typeof v === "string" && v.trim()) parts.push(`<div class="s-sec s-fb">\u{1f4cc} ${k}</div><div class="s-text-line">${esc(v).replace(/\n/g, "<br>")}</div>`)
    else if (Array.isArray(v) && v.length) {
      parts.push(`<div class="s-sec s-fb">\u{1f4cc} ${k}</div>`)
      v.forEach(item => {
        if (typeof item === "string") parts.push(`<div class="s-text-line">\u00b7 ${esc(item)}</div>`)
        else if (item) {
          const txts = Object.keys(item).filter(kk => item[kk]).map(kk => esc(item[kk]))
          if (txts.length) parts.push(`<div class="s-text-line">\u00b7 ${txts.join(" \u2014 ")}</div>`)
        }
      })
    }
  })
  parts.push("</div>")
  let recQ = data.followUps || []
  if (!recQ.length) {
    const kw = (data.title || data.question || data.problem || data.\u54c1\u7c7b || "").replace(/[\u3001\uff0c\u3002]/g, " ").trim()
    if (kw && kw.length > 1) {
      const pools = {
        cooking: [
          `${kw}\u6ca1\u6709\u67d0\u98df\u6750\u7528\u4ec0\u4e48\u4ee3\u66ff`, `${kw}\u6709\u4ec0\u4e48\u6280\u5de7`, `${kw}\u53ef\u4ee5\u52a0\u4ec0\u4e48\u914d\u83dc`,
          `${kw}\u600e\u4e48\u505a\u66f4\u597d\u5403`, `${kw}\u7684\u70b9\u91cf\u662f\u591a\u5c11`, `${kw}\u6709\u4ec0\u4e48\u8425\u517b`,
          `${kw}\u53ef\u4ee5\u63d0\u524d\u51c6\u5907\u5417`, `${kw}\u600e\u4e48\u4fdd\u5b58`, `${kw}\u9002\u5408\u4ec0\u4e48\u4eba\u7fa4`,
          `${kw}\u4e0d\u540c\u53e3\u5473\u505a\u6cd5`
        ],
        fashion: [
          `${kw}\u9002\u5408\u4ec0\u4e48\u573a\u5408\u7a7f`, `${kw}\u600e\u4e48\u642d\u914d\u66f4\u597d\u770b`, `${kw}\u63a8\u8350\u4ec0\u4e48\u989c\u8272`,
          `${kw}\u9002\u5408\u4ec0\u4e48\u8eab\u6750`, `${kw}\u6709\u4ec0\u4e48\u914d\u9970\u63a8\u8350`, `${kw}\u4ec0\u4e48\u5b63\u8282\u7a7f`,
          `${kw}\u600e\u4e48\u6e05\u6d17\u4fdd\u62a4`, `${kw}\u53ef\u4ee5\u642d\u914d\u4ec0\u4e48\u978b\u5b50`, `${kw}\u6709\u4ec0\u4e48\u907f\u96f7\u6307\u5357`,
          `${kw}\u9002\u5408\u4ec0\u4e48\u98ce\u683c`
        ],
        shopping: [
          `${kw}\u600e\u4e48\u4fdd\u5b58`, `${kw}\u4ec0\u4e48\u5b63\u8282\u6700\u597d`, `${kw}\u6709\u4ec0\u4e48\u6ce8\u610f\u4e8b\u9879`,
          `${kw}\u600e\u4e48\u6311\u9009`, `${kw}\u6709\u4ec0\u4e48\u54c1\u724c\u63a8\u8350`, `${kw}\u4ef7\u683c\u591a\u5c11\u5408\u9002`,
          `${kw}\u600e\u4e48\u533a\u5206\u597d\u574f`, `${kw}\u53ef\u4ee5\u7f51\u8d2d\u5417`, `${kw}\u6709\u4ec0\u4e48\u5e38\u89c1\u9677\u9631`,
          `${kw}\u7528\u4ec0\u4e48\u88c5\u5907`
        ],
        repair: [
          `${kw}\u9700\u8981\u4ec0\u4e48\u5de5\u5177`, `${kw}\u6709\u4ec0\u4e48\u6ce8\u610f\u4e8b\u9879`, `${kw}\u4ec0\u4e48\u60c5\u51b5\u8981\u627e\u4e13\u4e1a\u4eba\u5458`,
          `${kw}\u600e\u4e48\u9884\u9632`, `${kw}\u5e38\u89c1\u95ee\u9898\u89e3\u51b3`, `${kw}\u8981\u591a\u4e45\u4fee\u7406\u4e00\u6b21`,
          `${kw}\u6709\u4ec0\u4e48\u66ff\u4ee3\u65b9\u6848`, `${kw}\u5b89\u5168\u6ce8\u610f\u4e8b\u9879`, `${kw}\u54ea\u4e9b\u90e8\u4f4d\u5bb9\u6613\u574f`,
          `${kw}\u81ea\u5df1\u80fd\u4fee\u5417`
        ],
        housework: [
          `${kw}\u6709\u4ec0\u4e48\u6280\u5de7`, `${kw}\u9700\u8981\u6ce8\u610f\u4ec0\u4e48`, `${kw}\u7528\u4ec0\u4e48\u6e05\u6d01\u5242\u597d`,
          `${kw}\u591a\u4e45\u505a\u4e00\u6b21`, `${kw}\u6709\u4ec0\u4e48\u5de5\u5177\u63a8\u8350`, `${kw}\u600e\u4e48\u505a\u66f4\u7701\u529b`,
          `${kw}\u6709\u4ec0\u4e48\u9ad8\u6548\u65b9\u6cd5`, `${kw}\u5bb3\u7269\u8d28\u5982\u4f55\u5904\u7406`, `${kw}\u9002\u5408\u4ec0\u4e48\u7ea7\u522b`,
          `${kw}\u6709\u4ec0\u4e48\u5e38\u89c1\u8bef\u533a`
        ],
        health: [
          `${kw}\u4ec0\u4e48\u60c5\u51b5\u8981\u770b\u533b\u751f`, `${kw}\u6709\u4ec0\u4e48\u9884\u9632\u63aa\u65bd`, `${kw}\u5e38\u89c1\u539f\u56e0\u6709\u54ea\u4e9b`,
          `${kw}\u75c7\u72b6\u600e\u4e48\u7f13\u89e3`, `${kw}\u6709\u4ec0\u4e48\u8b66\u53f7`, `${kw}\u53ef\u4ee5\u81ea\u5df1\u5904\u7406\u5417`,
          `${kw}\u54ea\u4e9b\u98df\u7269\u5e2e\u52a9\u6062\u590d`, `${kw}\u5e38\u89c1\u8bef\u533a`, `${kw}\u8fd0\u52a8\u6709\u5e2e\u52a9\u5417`,
          `${kw}\u4ec0\u4e48\u65f6\u5019\u53ef\u4ee5\u5403\u836f`
        ],
        etiquette: [
          `${kw}\u573a\u5408\u6709\u4ec0\u4e48\u533a\u522b`, `${kw}\u6709\u4ec0\u4e48\u5173\u952e\u539f\u5219`, `${kw}\u5e38\u89c1\u5931\u8bef`,
          `${kw}\u600e\u4e48\u81ea\u7136\u8868\u8fbe`, `${kw}\u6709\u4ec0\u4e48\u6587\u5316\u5dee\u5f02`, `${kw}\u600e\u4e48\u907f\u514d\u5c34\u5c2c`,
          `${kw}\u9002\u5408\u4ec0\u4e48\u793c\u7269`, `${kw}\u7528\u8bcd\u6709\u4ec0\u4e48\u8bb2\u7a76`, `${kw}\u600e\u4e48\u8868\u793a\u5c0a\u91cd`,
          `${kw}\u6709\u4ec0\u4e48\u5199\u4fe1\u89c4\u8303`
        ],
        pet: [
          `${kw}\u6709\u4ec0\u4e48\u5e38\u89c1\u95ee\u9898`, `${kw}\u600e\u4e48\u9884\u9632`, `${kw}\u4ec0\u4e48\u60c5\u51b5\u8981\u770b\u517d\u533b`,
          `${kw}\u996e\u98df\u6709\u4ec0\u4e48\u8981\u6c42`, `${kw}\u9002\u5408\u4ec0\u4e48\u54c1\u79cd`, `${kw}\u5e38\u89c1\u75c5\u600e\u4e48\u5904\u7406`,
          `${kw}\u7528\u4ec0\u4e48\u7528\u54c1`, `${kw}\u600e\u4e48\u57f9\u517b\u4e60\u60ef`, `${kw}\u82b1\u8d39\u591a\u5c11`,
          `${kw}\u521d\u6b21\u517b\u8981\u6ce8\u610f\u4ec0\u4e48`
        ],
        mealplan: [
          `${kw}\u6709\u4ec0\u4e48\u7701\u65f6\u6280\u5de7`, `${kw}\u8d2d\u7269\u6e05\u5355`, `${kw}\u8425\u517b\u600e\u4e48\u6446\u62ec`,
          `${kw}\u9002\u5408\u4ec0\u4e48\u4eba\u7fa4`, `${kw}\u6709\u4ec0\u4e48\u66ff\u4ee3\u65b9\u6848`, `${kw}\u505a\u6cd5\u6709\u591a\u96be`,
          `${kw}\u600e\u4e48\u63d0\u524d\u51c6\u5907`, `${kw}\u6709\u4ec0\u4e48\u654f\u6377`, `${kw}\u5b58\u50a8\u600e\u4e48\u5b89\u6392`,
          `${kw}\u53e3\u5473\u600e\u4e48\u53d8\u5316`
        ]
      }
      const pool = pools[scene]
      if (pool) recQ = pool.sort(() => Math.random() - 0.5).slice(0, 3)
      else recQ = [
        `${kw}\u600e\u4e48\u505a`, `${kw}\u9700\u8981\u4ec0\u4e48`, `${kw}\u6709\u4ec0\u4e48\u6280\u5de7`,
        `${kw}\u6709\u4ec0\u4e48\u6ce8\u610f\u4e8b\u9879`, `${kw}\u5e38\u89c1\u95ee\u9898`, `${kw}\u4e0d\u540c\u60c5\u51b5\u600e\u4e48\u529e`,
        `${kw}\u6709\u4ec0\u4e48\u63a8\u8350`, `${kw}\u600e\u4e48\u6331\u9009`
      ].sort(() => Math.random() - 0.5).slice(0, 3)
    }
  }
  if (recQ.length) parts.push(`<div class="s-followups"><div class="s-followup-title">\u{1f4a1} \u4f60\u53ef\u80fd\u8fd8\u60f3\u95ee</div>${recQ.map(q => `<span class="s-followup-chip">${esc(q)}</span>`).join(" ")}</div>`)
  return '<div class="s-card">' + parts.join("") + "</div>"
}
const COLOR_MAP = {"\u7ea2":"#e74c3c","\u9152\u7ea2":"#8e1b1b","\u6697\u7ea2":"#8b0000","\u7c89":"#f0a8c4","\u73ab\u7ea2":"#d81b60","\u6a59":"#f39c12","\u6a58":"#f39c12","\u674f":"#f7cba0","\u6a59\u7ea2":"#e67e22","\u9ec4":"#f1c40f","\u7c73":"#f5f0e1","\u7c73\u767d":"#f5f0e1","\u5976\u6cb9":"#fff8dc","\u8c61\u7259":"#fffff0","\u6d45\u9ec4":"#fff9c4","\u7eff":"#27ae60","\u58a8\u7eff":"#1e5631","\u519b\u7eff":"#4a5d23","\u8349\u7eff":"#7cb342","\u6d45\u7eff":"#a5d6a7","\u7fe0\u7eff":"#00a86b","\u84dd":"#2980b9","\u6df1\u84dd":"#1a237e","\u6d45\u84dd":"#bbdefb","\u85cf\u84dd":"#13264d","\u7070\u84dd":"#5b7b9a","\u5929\u84dd":"#87ceeb","\u7d2b":"#8e44ad","\u6de1\u7d2b":"#d1b3e0","\u6df1\u7d2b":"#4a148c","\u68d5":"#8d6e63","\u5496\u5561":"#6d4c2e","\u9a7c":"#c49a6c","\u5361\u5176":"#c3a66b","\u68d5\u8910":"#5d4037","\u7070":"#9e9e9e","\u6d45\u7070":"#cfd8dc","\u6df1\u7070":"#424242","\u94f6":"#bdc3c7","\u70ad\u7070":"#36454f","\u9ed1":"#2c2c2c","\u767d":"#ffffff","\u7c73\u8272":"#f5f0e1","\u88f8\u8272":"#e8c4a0","\u91d1":"#d4a017","\u53e4\u94dc":"#cd7f32"}
function nameToHex(name) {
  if (!name || typeof name !== "string") return "#ccc"
  const n = name.trim()
  for (const [kw, hex] of Object.entries(COLOR_MAP)) {
    if (n.includes(kw)) return hex
  }
  return "#ccc"
}
const OUTFIT_ICONS = {"\u897f\u88dd":"\u{1f454}","\u897f\u88c5":"\u{1f454}","\u5916\u5957":"\u{1f9e5}","\u5927\u8863":"\u{1f9e5}","\u5939\u514b":"\u{1f9e5}","jacket":"\u{1f9e5}","coat":"\u{1f9e5}","blazer":"\u{1f9e5}","\u886c\u886b":"\u{1f454}","shirt":"\u{1f454}","blouse":"\u{1f454}","\u886c\u8863":"\u{1f454}","T\u6064":"\u{1f455}","t\u6064":"\u{1f455}","tee":"\u{1f455}","tshirt":"\u{1f455}","polo":"\u{1f455}","\u88e4\u5b50":"\u{1f456}","\u88e4":"\u{1f456}","pants":"\u{1f456}","jeans":"\u{1f456}","\u725b\u4ed4\u88e4":"\u{1f456}","\u897f\u88e4":"\u{1f456}","\u77ed\u88e4":"\u{1f973}","\u88d9\u5b50":"\u{1f457}","\u88d9":"\u{1f457}","dress":"\u{1f457}","skirt":"\u{1f457}","\u978b":"\u{1f45f}","sneaker":"\u{1f45f}","\u8fd0\u52a8\u978b":"\u{1f45f}","\u9774":"\u{1f97e}","boots":"\u{1f97e}","\u9ad8\u8ddf\u978b":"\u{1f460}","heels":"\u{1f460}","\u5305":"\u{1f45c}","bag":"\u{1f45c}","\u624b\u63d0\u5305":"\u{1f45c}","\u80cc\u5305":"\u{1f390}","\u5e3d\u5b50":"\u{1f9e2}","hat":"\u{1f9e2}","cap":"\u{1f9e2}","\u56f4\u5dfe":"\u{1f9e3}","scarf":"\u{1f9e3}","\u4e1d\u5dfe":"\u{1f9e3}","\u624b\u8868":"\u231a","watch":"\u231a","\u624b\u9336":"\u231a","\u9879\u94fe":"\u{1f4ff}","necklace":"\u{1f4ff}","\u6212\u6307":"\u{1f48d}","ring":"\u{1f48d}","\u8033\u73af":"\u{1f48e}","earring":"\u{1f48e}","\u76ae\u5e26":"\u{1f517}","belt":"\u{1f517}","\u8170\u5e26":"\u{1f517}","\u5185\u8863":"\u{1f971}","\u5185\u88e4":"\u{1f971}","\u889c\u5b50":"\u{1f9e6}","socks":"\u{1f9e6}","\u7761\u8863":"\u{1f6cc}","pajama":"\u{1f6cc}","\u6cf3\u8863":"\u{1f971}","swimsuit":"\u{1f971}","\u6bdb\u8863":"\u{1f9f6}","sweater":"\u{1f9f6}","knit":"\u{1f9f6}","\u9488\u7ec7":"\u{1f9f6}","cardigan":"\u{1f9f6}","\u8fde\u8863":"\u{1f457}","\u5957\u88c5":"\u{1f454}","suit":"\u{1f454}"}
function outfitIcon(name) {
  if (!name || typeof name !== "string") return "\u{1f454}"
  const n = name.trim().toLowerCase()
  for (const [kw, icon] of Object.entries(OUTFIT_ICONS)) {
    if (n.includes(kw.toLowerCase())) return icon
  }
  return "\u{1f454}"
}

function makeStepImg(kw) {
  if (!kw) kw = "cooking"
  const emoji = stepEmoji(kw)
  const cls = stepGradient(kw)
  return '<div class="rc-step-img"><img class="rc-step-photo" src="/api/images/step-img?q=' + encodeURIComponent(kw) + '" alt="' + esc(kw) + '" loading="lazy" onerror="this.style.display=\'none\';this.nextElementSibling.style.display=\'flex\'"/><div class="rc-step-illustration ' + cls + '" style="display:none"><span>' + emoji + '</span></div></div>'
}

function stepEmoji(keyword) {
  if (!keyword) return '🍳'
  const kw = keyword.toLowerCase()
  if (/cut|chop|dice|slice|mince/.test(kw)) return '🔪'
  if (/wash|rinse|clean|peel/.test(kw)) return '🚿'
  if (/fry|stir.?fry|saute|\\bpan\\b/.test(kw)) return '🍳'
  if (/boil|cook|simmer|stew|braise|blanch/.test(kw)) return '🥘'
  if (/steam/.test(kw)) return '♨️'
  if (/bake|roast|oven/.test(kw)) return '🔥'
  if (/season|marinate|salt|sugar|sauce|soy/.test(kw)) return '🧂'
  if (/mix|stir|whisk|beat|blend/.test(kw)) return '🥄'
  if (/egg/.test(kw)) return '🥚'
  if (/meat|ribs|chicken|pork|beef|fish/.test(kw)) return '🥩'
  if (/vegetable|tomato|onion|garlic|ginger/.test(kw)) return '🥬'
  if (/oil|\\bheat\\b/.test(kw)) return '🔥'
  if (/serve|plate|dish|bowl/.test(kw)) return '🍽️'
  if (/garnish|green.?onion|herb/.test(kw)) return '🌿'
  if (/pour|add|drizzle/.test(kw)) return '🫗'
  if (/repair|wrench|screwdriver|pliers|hammer|drill|valve|pipe|faucet|leak|clog|fix|tighten|loosen/.test(kw)) return '🔧'
  if (/clean(?! )|scrub|wipe|mop|sweep|vacuum|dust|polish|organize|tidy|stain|laundry|fold|iron/.test(kw)) return '🧼'
  if (/shirt|tshirt|pants|jeans|shoes|dress|skirt|jacket|coat|\\bhat\\b|belt|tie|scarf|outfit|wear|fashion|style|sneakers|boots|suit|blazer|top|bottom|hoodie|sweater|knit|chino|cardigan|blouse|vest|leather|cotton|linen|collar|pocket|sleeve|cuff|button|zip/.test(kw)) return '👔'
  if (/medicine|pill|tablet|capsule|thermometer|bandage|fever|cough|cold|symptom|health|exercise|vitamin|firstaid/.test(kw)) return '💊'
  if (/dog|cat|pet|puppy|kitten|feed|brush|bath|walk|leash|collar|bone|vet|groom|treat/.test(kw)) return '🐶'
  if (/fruit|apple|banana|orange|grape|vegetable|tomato|fresh|ripe|choose|pick|select|buy|shop|market/.test(kw)) return '🍎'
  return '🍳'
}

function stepGradient(keyword) {
  if (!keyword) return 'grad-cook'
  const kw = keyword.toLowerCase()
  if (/cut|chop|dice|slice|mince/.test(kw)) return 'grad-cut'
  if (/wash|rinse|clean|peel/.test(kw)) return 'grad-wash'
  if (/fry|stir.?fry|saute|\\bpan\\b/.test(kw)) return 'grad-fry'
  if (/boil|cook|simmer|stew|braise|blanch/.test(kw)) return 'grad-boil'
  if (/steam/.test(kw)) return 'grad-steam'
  if (/bake|roast|oven/.test(kw)) return 'grad-bake'
  if (/season|marinate|salt|sugar|sauce|soy/.test(kw)) return 'grad-season'
  if (/mix|stir|whisk|beat|blend/.test(kw)) return 'grad-mix'
  if (/egg/.test(kw)) return 'grad-egg'
  if (/meat|ribs|chicken|pork|beef|fish/.test(kw)) return 'grad-meat'
  if (/vegetable|tomato|onion|garlic|ginger/.test(kw)) return 'grad-veg'
  if (/oil|\\bheat\\b/.test(kw)) return 'grad-oil'
  if (/serve|plate|dish|bowl/.test(kw)) return 'grad-serve'
  if (/garnish|green.?onion|herb/.test(kw)) return 'grad-garnish'
  if (/pour|add|drizzle/.test(kw)) return 'grad-pour'
  if (/repair|wrench|screwdriver|pliers|hammer|drill|valve|pipe|faucet|leak|clog|fix|tighten|loosen/.test(kw)) return 'grad-repair'
  if (/clean(?! )|scrub|wipe|mop|sweep|vacuum|dust|polish|organize|tidy|stain|laundry|fold|iron/.test(kw)) return 'grad-housework'
  if (/shirt|tshirt|pants|jeans|shoes|dress|skirt|jacket|coat|\\bhat\\b|belt|tie|scarf|outfit|wear|fashion|style|sneakers|boots|suit|blazer|top|bottom|hoodie|sweater|knit|chino|cardigan|blouse|vest|leather|cotton|linen|collar|pocket|sleeve|cuff|button|zip/.test(kw)) return 'grad-fashion'
  if (/medicine|pill|tablet|capsule|thermometer|bandage|fever|cough|cold|symptom|health|exercise|vitamin|firstaid/.test(kw)) return 'grad-health'
  if (/dog|cat|pet|puppy|kitten|feed|brush|bath|walk|leash|collar|bone|vet|groom|treat/.test(kw)) return 'grad-pet'
  if (/fruit|apple|banana|orange|grape|vegetable|tomato|fresh|ripe|choose|pick|select|buy|shop|market/.test(kw)) return 'grad-shop'
  return 'grad-cook'
}

function generateScenePrompt(data, scene) {
  // Health scene: no image generation
  if (scene === "health") return ""
  if (scene === "cooking") {
    const name = data.name || data.dishName || data.菜名 || '美食'
    return '专业美食摄影，' + name + '，精致摆盘，自然光线，暖色调，微距拍摄，诱人色泽，高清画质，温暖氛围'
  }
  if (scene === "fashion") {
    const oc = data.occasion || data.场合 || '日常穿搭'
    return '时尚穿搭摄影，' + oc + '风格，全身搭配展示，干净背景，自然光线，高清质感，模特实穿，简约高级'
  }
  if (scene === "shopping") {
    const cat = data.category || data.品类 || '商品'
    return '商品实拍展示，' + cat + '，干净背景，自然日光，高清细节，真实质感，不是食物不是菜品不是菜肴'
  }
  if (scene === "repair") {
    const prob = data.problem || '维修'
    return '实用工具展示，' + prob + '，工具和材料实拍，工作台场景，清晰细节，操作步骤示意，干净明亮'
  }
  if (scene === "housework") {
    const prob = data.problem || '家务'
    return '家居清洁场景，' + prob + '，整洁家居环境，工具材料展示，自然光线，温馨氛围，真实生活场景'
  }
  if (scene === "mealplan") {
    const plan = data.plan_name || data.planName || '食谱规划'
    return '健康美食摆盘，' + plan + '，丰富食材展示，餐桌布置，自然光，诱人色彩，精致生活风格'
  }
  if (scene === "etiquette") {
    const oc = data.occasion || data.场合 || '社交礼仪'
    return '社交场景摄影，' + oc + '，优雅环境，得体着装，温馨氛围，自然光线，真实场景'
  }
  if (scene === "pet") {
    const type = data.pet_type || data.petType || data.宠物类型 || '宠物'
    return '可爱宠物摄影，' + type + '，温馨家庭环境，自然光线，高清毛绒细节，治愈风格，真实抓拍'
  }
  return ""
}
function attachSceneImage(message, prompt, scene) {
  if (!prompt || message._sceneImageUrl || message._sceneImageLoading) return
  message._sceneImageLoading = true
  message._sceneImageText = "\u{1f3a8} \u6b63\u5728\u751f\u6210\u573a\u666f\u914d\u56fe\u2026"
          generateFoodImage(prompt, scene).then(function(res) {
    var d = res
    if (d && d.code === 200 && d.data && d.data.imageUrl) {
      message._sceneImageUrl = d.data.imageUrl
      message._sceneImageLoading = false
      message._sceneImageText = ""
    } else if (d && d.data && d.data.taskId) {
      pollSceneImage(message, prompt, d.data.taskId, d.data.provider)
    } else {
      message._sceneImageLoading = false
      message._sceneImageText = ""
    }
  }).catch(function() {
    message._sceneImageLoading = false
    message._sceneImageText = ""
  })
}
function pollSceneImage(message, prompt, taskId, provider) {
  if (message._sceneImagePoll) clearInterval(message._sceneImagePoll)
  message._sceneImagePoll = setInterval(function() {
    getFoodImageStatus(taskId, provider).then(function(res) {
      var d = res
      if (d && d.code === 200 && d.data && d.data.resultUrl) {
        clearInterval(message._sceneImagePoll)
        message._sceneImagePoll = null
        message._sceneImageUrl = d.data.resultUrl
        message._sceneImageLoading = false
        message._sceneImageText = ""
      } else if (d && d.code !== 200) {
        clearInterval(message._sceneImagePoll)
        message._sceneImagePoll = null
        message._sceneImageLoading = false
        message._sceneImageText = ""
      }
    }).catch(function() {
      clearInterval(message._sceneImagePoll)
      message._sceneImagePoll = null
      message._sceneImageLoading = false
      message._sceneImageText = ""
    })
  }, 3000)
}

function esc(s) { if (typeof s !== 'string') return ''; return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') }
</script>
<style scoped>
/* ===== Claude 风格聊天页 ===== */
.chat-page {
  display: flex; flex-direction: column; height: 100vh;
  background: var(--paper);
  overflow: hidden;
}

/* ===== 顶部栏 ===== */
.chat-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px 10px;
  background: linear-gradient(180deg, var(--paper) 70%, transparent);
  backdrop-filter: blur(16px);
  z-index: 5; flex-shrink: 0;
}
.header-left { display: flex; align-items: center; gap: 10px; }
.header-back {
  width: 34px; height: 34px; border: 1px solid var(--line);
  border-radius: 12px; background: rgba(255,250,241,.68);
  color: var(--accent-deep); font-size: 18px; cursor: pointer;
  display: grid; place-items: center;
}
.header-back:active { transform: scale(.94); }
.header-title { font-size: 16px; font-weight: 700; color: var(--ink); cursor: pointer; }
.header-sub { font-size: 11px; color: var(--muted); margin-top: 1px; }
.header-right { position: relative; display: flex; align-items: center; }
.header-btn {
  width: 34px; height: 34px; border: 1px solid var(--line);
  border-radius: 12px; background: rgba(255,250,241,.68);
  color: var(--muted); font-size: 20px; cursor: pointer;
  display: grid; place-items: center; letter-spacing: 2px;
}
.header-btn:active { transform: scale(.94); }
.export-menu {
  position: absolute; top: 40px; right: 0; z-index: 100;
  background: var(--card-solid); border: 1px solid var(--line);
  border-radius: 16px; box-shadow: var(--small-shadow); min-width: 130px;
  overflow: hidden;
}
.export-item { padding: 10px 16px; cursor: pointer; font-size: 13px; color: var(--ink); }
.export-item:hover { background: rgba(141,95,63,.06); }

/* ===== 消息区 ===== */
.messages {
  flex: 1; overflow-y: auto;
  padding: 10px 16px 20px;
  scroll-behavior: smooth;
}

/* 欢迎 */
.welcome { text-align: center; padding: 30px 10px; }
.welcome-icon {
  width: 48px; height: 48px; border-radius: 20px;
  background: linear-gradient(145deg, #9b714f, #6f4a31);
  color: #fff7ea; font-size: 24px; display: grid; place-items: center;
  margin: 0 auto 14px;
}
.welcome-text { font-size: 14px; color: var(--muted); margin-bottom: 16px; }
.welcome-prompts { display: flex; flex-direction: column; gap: 8px; align-items: center; }
.wp-chip {
  border: 1px solid var(--line); background: rgba(255,250,241,.68);
  color: var(--ink); border-radius: 999px; padding: 8px 14px;
  font-size: 13px; cursor: pointer; width: fit-content;
}
.wp-chip:active { transform: scale(.97); }

/* 写作模式 */
.writing-modes { margin-top: 20px; text-align: left; }
.wm-title { font-size: 13px; color: var(--muted); margin-bottom: 10px; }
.wm-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.wm-chip {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 12px 6px; border-radius: 14px; cursor: pointer;
  background: rgba(255,250,241,.48); border: 1px solid var(--line);
}
.wm-chip:active { transform: scale(.96); }
.wm-icon { font-size: 24px; }
.wm-label { font-size: 12px; color: var(--muted); }

/* ===== 消息 ===== */
.msg { margin: 16px 0; animation: msg-rise .45s both cubic-bezier(.2,.8,.2,1); }
.msg-user { display: flex; justify-content: flex-end; }
@keyframes msg-rise {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 用户气泡 */
.user-bubble {
  max-width: 86%;
  padding: 14px 16px; border-radius: 24px;
  border-bottom-right-radius: 9px;
  color: #fff9ef;
  background: linear-gradient(145deg, #875b3e, #69452e);
  box-shadow: 0 16px 34px rgba(100,66,42,0.24);
  line-height: 1.58; font-size: 15px;
}

/* 助手卡片 */
.assistant-card {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  background: var(--card);
  box-shadow: var(--warm-shadow), inset 0 1px 0 rgba(255,255,255,.86);
  backdrop-filter: blur(18px);
  overflow: hidden;
}
.card-head {
  display: flex; align-items: center; justify-content: space-between;
  gap: 12px; padding: 16px 18px 12px;
  border-bottom: 1px solid var(--line);
}
.card-title { display: flex; align-items: center; gap: 10px; font-weight: 700; color: var(--ink); }
.avatar {
  width: 28px; height: 28px; border-radius: 10px;
  display: grid; place-items: center;
  background: #efe2d1; color: var(--accent-deep);
  font-size: 14px;
}
.confidence {
  color: var(--sage); font-size: 11px;
  background: rgba(125,139,111,.10);
  border: 1px solid rgba(125,139,111,.18);
  border-radius: 999px; padding: 4px 8px; white-space: nowrap;
}
.card-body {
  padding: 14px 18px 10px;
  color: var(--ink); font-size: 15px; line-height: 1.65;
}
.card-body :deep(.rc-card) { border: none; padding: 0; margin: 0; background: none; }
.card-body :deep(.rc-card-title) { font-size: 16px; color: var(--accent-deep); }
.card-body :deep(.rc-sec) { color: var(--accent-deep); font-size: 14px; margin: 14px 0 6px; }
.card-body :deep(.rc-item) { color: var(--ink); font-size: 14px; }
.card-body :deep(.rc-tip) {
  background: linear-gradient(135deg, rgba(226,204,170,.50), rgba(255,250,241,.42));
  color: #5f513f; border: 1px solid var(--line); border-radius: 14px;
}
.card-body :deep(.rc-step) { gap: 10px; margin: 12px 0; }
.card-body :deep(.rc-step-badge) {
  background: var(--accent); box-shadow: 0 4px 12px rgba(141,95,63,.25);
}
.card-body :deep(.rc-followup-chip) {
  background: rgba(141,95,63,.08); color: var(--accent-deep);
  border: 1px solid rgba(141,95,63,.18);
}
.card-body :deep(.rc-followup-chip:hover) { background: rgba(141,95,63,.14); }
.card-body :deep(.md-content) { color: var(--ink); }
.card-body :deep(.md-content strong) { color: var(--accent-deep); }
.card-body :deep(.md-content code) { background: var(--paper-deep); color: var(--accent-deep); }
.card-body :deep(.md-code-block) { background: #2d2924; color: #f0ebe2; border-radius: 14px; }

/* 卡片操作按钮 */
.card-actions {
  display: flex; gap: 6px; flex-wrap: wrap;
  padding: 10px 18px 16px;
  border-top: 1px solid var(--line);
}
.card-actions button {
  border: 1px solid var(--line);
  background: rgba(255,255,255,.32);
  color: var(--accent-deep);
  border-radius: 999px; padding: 6px 10px;
  font-size: 12px; cursor: pointer;
}
.card-actions button:active { transform: scale(.96); }
.card-actions button.faved { color: #d97706; }
.card-actions button:disabled { opacity: .5; cursor: default; }

/* 菜品图 */
.food-image { margin-top: 8px; cursor: pointer; border-radius: 14px; overflow: hidden; position: relative; }
.food-image img { width: 100%; border-radius: 14px; display: block; }
.food-badge {
  position: absolute; left: 10px; bottom: 10px;
  padding: 3px 8px; border-radius: 999px;
  background: rgba(104,67,43,.85); color: #fff7ea;
  font-size: 11px; line-height: 1;
}
.food-badge.paid { background: rgba(180,83,9,.85); }
.food-loading {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 12px; margin-top: 8px;
  border: 1px dashed var(--line); border-radius: 14px;
  background: rgba(255,250,241,.48);
}
.fl-spinner {
  width: 14px; height: 14px; border: 2px solid var(--line);
  border-top-color: var(--accent); border-radius: 50%;
  animation: fl-spin .8s linear infinite; flex-shrink: 0;
}
@keyframes fl-spin { to { transform: rotate(360deg); } }
.fl-text { font-size: 13px; color: var(--muted); }
.fl-cancel { margin-left: auto; border: none; background: none; color: var(--muted); font-size: 12px; cursor: pointer; }
.food-actions {
  display: flex; align-items: center; gap: 8px; justify-content: space-between;
  padding: 10px 12px; margin-top: 8px;
  border: 1px dashed var(--line); border-radius: 14px;
  background: rgba(255,250,241,.48); color: var(--muted); font-size: 13px;
}
.food-gen-btn {
  border: 1px solid var(--accent); background: rgba(141,95,63,.08);
  color: var(--accent); border-radius: 999px; padding: 5px 10px;
  font-size: 12px; cursor: pointer; white-space: nowrap;
}

/* 消息图片 */
.msg-image { margin-bottom: 8px; cursor: pointer; }
.msg-image img { max-width: 200px; max-height: 180px; border-radius: 12px; display: block; }

/* 加载中 */
.thinking-dots { padding: 8px 0; text-align: center; font-size: 20px; letter-spacing: 4px; color: var(--muted); }
.dot { animation: dot-pulse 1.4s infinite; }
.dot:nth-child(2) { animation-delay: .2s; }
.dot:nth-child(3) { animation-delay: .4s; }
@keyframes dot-pulse {
  0%, 80%, 100% { opacity: .3; }
  40% { opacity: 1; }
}
.cursor { animation: blink 1s step-end infinite; color: var(--accent); }
@keyframes blink { 50% { opacity: 0; } }

/* ===== 图片预览覆盖层 ===== */
.overlay {
  position: fixed; top: 0; left: 0; width: 100%; height: 100%;
  background: rgba(0,0,0,.85); z-index: 9999;
  display: flex; align-items: center; justify-content: center;
}
.preview-img { max-width: 90vw; max-height: 90vh; border-radius: 8px; object-fit: contain; }
.overlay-close {
  position: fixed; top: 20px; right: 24px; color: #fff; font-size: 28px; cursor: pointer;
  width: 44px; height: 44px; display: flex; align-items: center; justify-content: center;
  border-radius: 50%; background: rgba(255,255,255,.15);
}

/* ===== 图片预览栏 ===== */
.image-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 16px;
  border-top: 1px solid var(--line);
  background: var(--paper); flex-shrink: 0;
}
.ib-preview { position: relative; flex-shrink: 0; }
.ib-preview img { height: 38px; width: 38px; border-radius: 8px; border: 1px solid var(--line); object-fit: cover; }
.ib-remove {
  position: absolute; top: -5px; right: -5px;
  width: 16px; height: 16px; background: #b91c1c; color: #fff;
  border-radius: 50%; font-size: 10px; display: flex; align-items: center; justify-content: center; cursor: pointer;
}
.ib-actions { display: flex; gap: 6px; }
.ib-chip {
  background: rgba(255,250,241,.68); color: var(--accent-deep);
  font-size: 12px; padding: 5px 10px; border-radius: 8px;
  cursor: pointer; border: 1px solid var(--line); white-space: nowrap;
}
.ib-chip:active { transform: scale(.96); }

/* 上传进度 */
.upload-bar {
  flex-shrink: 0; height: 4px; background: var(--line);
  border-radius: 2px; margin: 0 16px; position: relative; overflow: hidden;
}
.ub-fill { height: 100%; background: var(--accent); border-radius: 2px; transition: width .3s ease; }
.ub-text { position: absolute; right: 0; top: -18px; font-size: 11px; color: var(--accent); font-weight: 600; }

/* ===== 输入区 ===== */
.composer-wrap {
  flex-shrink: 0;
  padding: 8px 16px calc(12px + env(safe-area-inset-bottom));
  background: linear-gradient(0deg, var(--paper) 74%, transparent);
  z-index: 10;
}
.prefill-hint {
  padding: 6px 10px; border-radius: 10px;
  background: rgba(141,95,63,.08); color: var(--accent-deep);
  font-size: 11px; font-weight: 600; text-align: center; margin-bottom: 6px;
}
.cost-hint {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 6px 10px; border-radius: 10px;
  font-size: 11px; line-height: 1.3;
  border: 1px solid var(--line); background: rgba(255,250,241,.48); color: var(--muted);
  margin-bottom: 6px;
}
.ch-main { font-weight: 600; white-space: nowrap; }
.ch-sub { color: var(--soft); text-align: right; }
.cost-hint.normal { background: rgba(141,95,63,.06); border-color: var(--line); color: var(--accent-deep); }
.cost-hint.free { background: rgba(125,139,111,.10); border-color: rgba(125,139,111,.18); color: var(--sage); }
.cost-hint.warn { background: rgba(217,119,6,.08); border-color: rgba(217,119,6,.18); color: #92400e; }
.cost-hint.danger { background: rgba(185,28,28,.06); border-color: rgba(185,28,28,.14); color: #b91c1c; }

.mode-row {
  display: flex; align-items: center; gap: 8px;
  padding: 2px 2px 6px;
}
.mode-chip {
  border: 1px solid var(--line); background: rgba(255,250,241,.48);
  color: var(--muted); border-radius: 999px; padding: 5px 10px;
  font-size: 12px; font-weight: 600; cursor: pointer;
}
.mode-chip.active { border-color: var(--accent); background: rgba(141,95,63,.08); color: var(--accent-deep); }
.mode-chip.follow.active { border-color: var(--sage); background: rgba(125,139,111,.10); color: var(--sage); }
.mode-chip:disabled { opacity: .5; cursor: default; }
.mode-tip { margin-left: auto; color: var(--soft); font-size: 11px; }

.composer {
  display: flex; align-items: flex-end; gap: 8px;
  padding: 8px;
  border: 1px solid rgba(87,68,49,.16);
  border-radius: 27px;
  background: rgba(255,252,246,.88);
  box-shadow: 0 20px 48px rgba(80,58,38,.18), inset 0 1px 0 rgba(255,255,255,.88);
  backdrop-filter: blur(18px);
}
.composer-btn {
  flex: 0 0 auto; width: 38px; height: 38px;
  border: 0; border-radius: 16px;
  display: grid; place-items: center; cursor: pointer;
  transition: transform .18s ease;
}
.composer-btn:active { transform: scale(.92); }
.upload-btn {
  color: var(--accent-deep); background: #efe3d3;
  border: 1px solid rgba(104,67,43,.10); font-size: 20px;
}
.send-btn {
  color: #fff8ed;
  background: linear-gradient(145deg, #8d5f3f, #68432b);
  box-shadow: 0 10px 20px rgba(104,67,43,.24); font-size: 18px;
}
.send-btn:disabled { opacity: .4; cursor: default; box-shadow: none; }

.composer textarea {
  flex: 1; min-height: 38px; max-height: 88px;
  resize: none; border: 0; outline: 0; background: transparent;
  color: var(--ink); padding: 8px 2px 6px;
  font: inherit; font-size: 14px; line-height: 1.35;
}
.composer textarea::placeholder { color: var(--soft); }

/* 收藏分类弹窗 */
.fav-cat-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.fav-cat-opt {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 12px 8px; border-radius: 12px; cursor: pointer;
  background: var(--paper); border: 2px solid transparent;
}
.fav-cat-opt:hover { background: rgba(141,95,63,.06); }
.fav-cat-opt.active { background: rgba(141,95,63,.08); border-color: var(--accent); }
.fco-icon { font-size: 24px; }
.fco-label { font-size: 12px; color: var(--muted); }

/* 分享卡片 */
.share-card {
  background: #fffaf1; border-radius: 18px; overflow: hidden;
  max-height: 70vh; overflow-y: auto;
}
.sc-header {
  display: flex; align-items: center; gap: 8px;
  padding: 16px 20px;
  background: linear-gradient(135deg, #f7f1e8, #eee4d6);
  border-bottom: 1px solid var(--line);
}
.sc-logo {
  width: 30px; height: 30px; border-radius: 10px;
  background: linear-gradient(145deg, #9b714f, #6f4a31);
  color: #fff7ea; display: grid; place-items: center; font-size: 16px;
}
.sc-brand { font-size: 14px; font-weight: 600; color: var(--accent-deep); }
.sc-body { padding: 16px 20px; }
.sc-q { font-size: 14px; font-weight: 600; color: var(--ink); margin-bottom: 8px; padding: 10px 14px; background: rgba(141,95,63,.06); border-radius: 10px; border-left: 3px solid var(--accent); }
.sc-divider { height: 1px; background: var(--line); margin: 12px 0; }
.sc-a { font-size: 14px; color: var(--ink); line-height: 1.7; }
.sc-a .rc-followups { display: none; }
.sc-footer { display: flex; justify-content: space-between; align-items: center; padding: 12px 20px; border-top: 1px solid var(--line); font-size: 11px; color: var(--soft); }
.sc-wm { color: var(--accent); font-weight: 500; }

/* ===== 全局 v-html 内容覆盖 ===== */
:deep(.rc-card) { border: none; padding: 0; margin: 0; background: none; }
:deep(.rc-card-title) { font-size: 16px; color: var(--accent-deep); font-weight: 700; }
:deep(.rc-sec) { color: var(--accent-deep); font-size: 14px; font-weight: 600; margin: 14px 0 6px; }
:deep(.rc-item) { color: var(--ink); font-size: 14px; }
:deep(.rc-tip) {
  background: linear-gradient(135deg, rgba(226,204,170,.50), rgba(255,250,241,.42));
  color: #5f513f; border: 1px solid var(--line); border-radius: 14px;
}
:deep(.rc-followup-chip) {
  background: rgba(141,95,63,.08); color: var(--accent-deep);
  border: 1px solid rgba(141,95,63,.18);
}
:deep(.md-content) { color: var(--ink); }
:deep(.md-content strong) { color: var(--accent-deep); }
:deep(.md-content code) { background: var(--paper-deep); color: var(--accent-deep); }
:deep(.md-code-block) { background: #2d2924; color: #f0ebe2; border-radius: 14px; }
:deep(.writing-content) { line-height: 1.9; font-size: 14.5px; color: var(--ink); }
:deep(.writing-content h2) { font-size: 18px; margin: 20px 0 10px; color: var(--accent-deep); border-bottom: 2px solid var(--accent); }
:deep(.writing-content strong) { color: var(--accent-deep); }
.listening { animation: mic-pulse 1.2s ease-in-out infinite; box-shadow: 0 0 0 0 rgba(104,67,43,0.5); }
@keyframes mic-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(104,67,43,0.5); }
  50% { box-shadow: 0 0 0 8px rgba(104,67,43,0); }
}
.mic-btn { color: var(--accent-deep); background: #efe3d3; border: 1px solid rgba(104,67,43,.10); font-size: 16px; }
/* ===== Scene Cards Redesign ===== */
.s-card { border:none; padding:0; margin:0; background:none; }
.s-hd { display:flex; align-items:center; gap:10px; padding:14px 18px 10px; border-bottom:1px solid var(--line); }
.s-hd-icon { width:34px; height:34px; border-radius:12px; display:grid; place-items:center; font-size:20px; flex-shrink:0; }
.s-icon-cooking { background:linear-gradient(145deg,#fce4d6,#f5cba7); }
.s-icon-fashion { background:linear-gradient(145deg,#e8d5f5,#d4b5e8); }
.s-icon-shopping { background:linear-gradient(145deg,#d5f5e3,#a9dfbf); }
.s-icon-repair { background:linear-gradient(145deg,#fdebd0,#f0d5a0); }
.s-icon-housework { background:linear-gradient(145deg,#d6eaf8,#aed6f1); }
.s-icon-general { background:linear-gradient(145deg,#fdebd0,#fadbd8); }
.s-icon-health { background:linear-gradient(145deg,#d5f5e3,#a9dfbf); }
.s-icon-etiquette { background:linear-gradient(145deg,#e8daf5,#d4b5e8); }
.s-icon-pet { background:linear-gradient(145deg,#fdebd0,#f9e79f); }
.s-icon-mealplan { background:linear-gradient(145deg,#d6eaf8,#a3e4d7); }
.s-hd-label { font-size:11px; color:var(--muted); line-height:1.2; }
.s-hd-title { font-size:15px; font-weight:700; color:var(--ink); line-height:1.3; }
.s-bd { padding:14px 18px 12px; }
.s-tags { display:flex; gap:6px; flex-wrap:wrap; margin-bottom:12px; }
.s-tag { font-size:11px; padding:3px 10px; border-radius:999px; background:rgba(141,95,63,0.08); color:var(--accent-deep); border:1px solid rgba(141,95,63,0.14); }
.s-tag-time { background:rgba(125,139,111,0.10); color:var(--sage); border-color:rgba(125,139,111,0.18); }
.s-sec { font-size:13px; font-weight:600; color:var(--accent-deep); margin:14px 0 8px; }
.s-sec:first-child { margin-top:0; }
.s-sec.s-fb { color:var(--muted); font-weight:500; }
.s-ing-list { display:flex; flex-direction:column; gap:5px; margin-bottom:2px; }
.s-ing-item { display:flex; align-items:center; gap:8px; padding:6px 10px; border-radius:10px; background:rgba(255,255,255,0.4); border:1px solid var(--line); font-size:13px; }
.s-ing-cb { width:16px; height:16px; border:2px solid var(--accent); border-radius:5px; flex-shrink:0; }
.s-ing-amt { color:var(--muted); font-size:12px; margin-left:auto; }
.s-ing-note { font-size:11px; color:var(--soft); font-style:italic; }
.s-step-list { display:flex; flex-direction:column; gap:7px; margin-bottom:2px; }
.s-step-item { display:flex; gap:10px; padding:8px 12px; border-radius:10px; background:rgba(255,255,255,0.3); border:1px solid var(--line); }
.s-step-num { width:22px; height:22px; border-radius:50%; background:var(--accent); color:#fff; font-size:11px; font-weight:600; display:grid; place-items:center; flex-shrink:0; margin-top:1px; }
.s-step-body { flex:1; font-size:13px; line-height:1.5; color:var(--ink); }
.s-step-time { display:inline-block; font-size:11px; color:var(--sage); background:rgba(125,139,111,0.10); padding:1px 7px; border-radius:999px; margin-left:5px; border:1px solid rgba(125,139,111,0.14); }
.s-step-tip { display:block; margin-top:3px; font-size:12px; color:var(--soft); font-style:italic; }
.s-step-warn { display:block; margin-top:3px; font-size:12px; color:#b91c1c; }
.s-select-item { display:flex; gap:10px; padding:8px 12px; border-radius:10px; background:rgba(255,255,255,0.3); border:1px solid var(--line); font-size:13px; align-items:flex-start; }
.s-select-num { width:22px; height:22px; border-radius:50%; background:var(--sage); color:#fff; font-size:11px; font-weight:600; display:grid; place-items:center; flex-shrink:0; margin-top:1px; }
.s-tool-list { display:flex; gap:5px; flex-wrap:wrap; margin-bottom:4px; }
.s-tool-tag { font-size:11px; padding:3px 10px; border-radius:999px; background:var(--paper-deep); color:var(--ink); border:1px solid var(--line); }
.s-fashion-style { font-size:14px; font-weight:500; color:var(--ink); padding:2px 0 4px; }
.s-color-list { display:flex; gap:8px; flex-wrap:wrap; margin:6px 0 8px; }
.s-color-item { display:flex; align-items:center; gap:6px; font-size:12px; color:var(--ink); }
.s-color-dot { width:20px; height:20px; border-radius:50%; border:2px solid rgba(255,255,255,0.8); box-shadow:0 1px 4px rgba(0,0,0,0.12); flex-shrink:0; }


.s-color-desc { color:var(--muted); font-size:11px; }
.s-outfit-list { display:flex; flex-direction:column; gap:6px; margin-bottom:2px; }
.s-outfit-item { display:flex; gap:8px; padding:7px 10px; border-radius:10px; background:rgba(255,255,255,0.3); border:1px solid var(--line); font-size:13px; align-items:center; }
.s-outfit-ico { font-size:16px; flex-shrink:0; width:22px; text-align:center; }
.s-outfit-body { flex:1; }
.s-outfit-name { font-weight:600; color:var(--ink); }
.s-outfit-desc { color:var(--muted); font-size:12px; margin-left:4px; }
.s-outfit-color { margin-left:auto; font-size:11px; padding:1px 8px; border-radius:999px; background:rgba(141,95,63,0.08); color:var(--accent-deep); border:1px solid rgba(141,95,63,0.12); flex-shrink:0; }
.s-mistake-list { display:flex; flex-direction:column; gap:3px; margin-bottom:4px; }
.s-mistake-item { font-size:12px; color:#92400e; padding:5px 10px; background:rgba(217,119,6,0.06); border-radius:8px; border-left:3px solid #d97706; }
.s-season-tag { display:inline-block; font-size:11px; padding:2px 10px; border-radius:999px; background:rgba(125,139,111,0.10); color:var(--sage); border:1px solid rgba(125,139,111,0.16); margin-bottom:10px; }
.s-slogan { margin-top:10px; padding:9px 14px; background:rgba(141,95,63,0.08); border-radius:10px; border:1px solid rgba(141,95,63,0.14); font-size:13px; font-weight:500; color:var(--accent-deep); text-align:center; }
.s-sev-low { display:inline-block; font-size:11px; padding:2px 10px; border-radius:999px; background:rgba(125,139,111,0.10); color:var(--sage); border:1px solid rgba(125,139,111,0.16); margin-bottom:8px; }
.s-sev-med { display:inline-block; font-size:11px; padding:2px 10px; border-radius:999px; background:rgba(217,119,6,0.08); color:#92400e; border:1px solid rgba(217,119,6,0.16); margin-bottom:8px; }
.s-sev-high { display:inline-block; font-size:11px; padding:2px 10px; border-radius:999px; background:rgba(185,28,28,0.06); color:#b91c1c; border:1px solid rgba(185,28,28,0.12); margin-bottom:8px; }
.s-key { margin-top:12px; padding:8px 12px; border-radius:10px; background:linear-gradient(135deg,rgba(226,204,170,0.50),rgba(255,250,241,0.42)); border:1px solid var(--line); font-size:13px; color:#5f513f; display:flex; align-items:center; gap:6px; }
.s-safety { margin-top:10px; padding:8px 12px; border-radius:10px; background:rgba(185,28,28,0.05); border:1px solid rgba(185,28,28,0.10); font-size:12px; color:#b91c1c; display:flex; align-items:center; gap:6px; }
.s-text { font-size:13px; color:var(--muted); line-height:1.6; margin-bottom:4px; }
.s-text-line { font-size:13px; color:var(--ink); line-height:1.6; padding:2px 0; }
.s-answer { font-size:14px; line-height:1.7; color:var(--ink); }
.s-followups { display:flex; flex-wrap:wrap; gap:6px; margin-top:14px; padding-top:12px; border-top:1px solid var(--line); }
.s-followup-title { width:100%; font-size:12px; color:var(--muted); margin-bottom:2px; }
.s-followup-chip { font-size:12px; padding:4px 12px; border-radius:999px; background:rgba(141,95,63,0.08); color:var(--accent-deep); border:1px solid rgba(141,95,63,0.14); cursor:pointer; transition:all 0.12s; }
.s-followup-chip:hover { background:rgba(141,95,63,0.15); }
.s-followup-chip:active { transform:scale(.96); }
.md-scene-label { font-size:12px; color:var(--muted); padding:0 0 8px; border-bottom:1px solid var(--line); margin-bottom:10px; }
.scene-image { margin-top: 0; cursor: pointer; border-radius: 14px 14px 0 0; overflow: hidden; position: relative; }
.scene-image img { width: 100%; max-height: 240px; object-fit: cover; display: block; border-bottom: 1px solid var(--line); }
.scene-badge { position: absolute; left: 10px; bottom: 10px; padding: 3px 8px; border-radius: 999px; background: rgba(104,67,43,.85); color: #fff7ea; font-size: 11px; line-height: 1; }
.kb-badge { display:inline-block; font-size:11px; padding:2px 8px; border-radius:999px; background:rgba(125,139,111,0.10); color:var(--sage); border:1px solid rgba(125,139,111,0.16); margin:10px 18px 0; }
/* deep compat with card-body */
.card-body :deep(.s-card) { border:none; padding:0; margin:0; background:none; }
.card-body :deep(.s-hd-title) { font-size:15px; font-weight:700; color:var(--ink); }
.card-body :deep(.s-sec) { font-size:13px; font-weight:600; color:var(--accent-deep); margin:14px 0 8px; }
.card-body :deep(.s-key) { margin-top:12px; padding:8px 12px; border-radius:10px; background:linear-gradient(135deg,rgba(226,204,170,0.50),rgba(255,250,241,0.42)); border:1px solid var(--line); font-size:13px; color:#5f513f; }
.card-body :deep(.s-safety) { margin-top:10px; padding:8px 12px; border-radius:10px; background:rgba(185,28,28,0.05); border:1px solid rgba(185,28,28,0.10); font-size:12px; color:#b91c1c; }
.card-body :deep(.s-followup-chip) { font-size:12px; padding:4px 12px; border-radius:999px; background:rgba(141,95,63,0.08); color:var(--accent-deep); border:1px solid rgba(141,95,63,0.14); cursor:pointer; }
.card-body :deep(.s-followup-chip:hover) { background:rgba(141,95,63,0.15); }
.card-body :deep(.md-scene-label) { font-size:12px; color:var(--muted); padding:0 0 8px; border-bottom:1px solid var(--line); margin-bottom:10px; }
</style>
<style>
/* ===== Scene Cards Redesign ===== */
.s-card { border:none; padding:0; margin:0; background:none; }
.s-hd { display:flex; align-items:center; gap:10px; padding:14px 18px 10px; border-bottom:1px solid var(--line); }
.s-hd-icon { width:34px; height:34px; border-radius:12px; display:grid; place-items:center; font-size:20px; flex-shrink:0; }
.s-icon-cooking { background:linear-gradient(145deg,#fce4d6,#f5cba7); }
.s-icon-fashion { background:linear-gradient(145deg,#e8d5f5,#d4b5e8); }
.s-icon-shopping { background:linear-gradient(145deg,#d5f5e3,#a9dfbf); }
.s-icon-repair { background:linear-gradient(145deg,#fdebd0,#f0d5a0); }
.s-icon-housework { background:linear-gradient(145deg,#d6eaf8,#aed6f1); }
.s-icon-general { background:linear-gradient(145deg,#fdebd0,#fadbd8); }
.s-icon-health { background:linear-gradient(145deg,#d5f5e3,#a9dfbf); }
.s-icon-etiquette { background:linear-gradient(145deg,#e8daf5,#d4b5e8); }
.s-icon-pet { background:linear-gradient(145deg,#fdebd0,#f9e79f); }
.s-icon-mealplan { background:linear-gradient(145deg,#d6eaf8,#a3e4d7); }
.s-hd-label { font-size:11px; color:var(--muted); line-height:1.2; }
.s-hd-title { font-size:15px; font-weight:700; color:var(--ink); line-height:1.3; }
.s-bd { padding:14px 18px 12px; }
.s-tags { display:flex; gap:6px; flex-wrap:wrap; margin-bottom:12px; }
.s-tag { font-size:11px; padding:3px 10px; border-radius:999px; background:rgba(141,95,63,0.08); color:var(--accent-deep); border:1px solid rgba(141,95,63,0.14); }
.s-tag-time { background:rgba(125,139,111,0.10); color:var(--sage); border-color:rgba(125,139,111,0.18); }
.s-sec { font-size:13px; font-weight:600; color:var(--accent-deep); margin:14px 0 8px; }
.s-sec:first-child { margin-top:0; }
.s-sec.s-fb { color:var(--muted); font-weight:500; }
.s-ing-list { display:flex; flex-direction:column; gap:5px; margin-bottom:2px; }
.s-ing-item { display:flex; align-items:center; gap:8px; padding:6px 10px; border-radius:10px; background:rgba(255,255,255,0.4); border:1px solid var(--line); font-size:13px; }
.s-ing-cb { width:16px; height:16px; border:2px solid var(--accent); border-radius:5px; flex-shrink:0; }
.s-ing-amt { color:var(--muted); font-size:12px; margin-left:auto; }
.s-ing-note { font-size:11px; color:var(--soft); font-style:italic; }
.s-step-list { display:flex; flex-direction:column; gap:7px; margin-bottom:2px; }
.s-step-item { display:flex; gap:10px; padding:8px 12px; border-radius:10px; background:rgba(255,255,255,0.3); border:1px solid var(--line); }
.s-step-num { width:22px; height:22px; border-radius:50%; background:var(--accent); color:#fff; font-size:11px; font-weight:600; display:grid; place-items:center; flex-shrink:0; margin-top:1px; }
.s-step-body { flex:1; font-size:13px; line-height:1.5; color:var(--ink); }
.s-step-time { display:inline-block; font-size:11px; color:var(--sage); background:rgba(125,139,111,0.10); padding:1px 7px; border-radius:999px; margin-left:5px; border:1px solid rgba(125,139,111,0.14); }
.s-step-tip { display:block; margin-top:3px; font-size:12px; color:var(--soft); font-style:italic; }
.s-step-warn { display:block; margin-top:3px; font-size:12px; color:#b91c1c; }
.s-select-item { display:flex; gap:10px; padding:8px 12px; border-radius:10px; background:rgba(255,255,255,0.3); border:1px solid var(--line); font-size:13px; align-items:flex-start; }
.s-select-num { width:22px; height:22px; border-radius:50%; background:var(--sage); color:#fff; font-size:11px; font-weight:600; display:grid; place-items:center; flex-shrink:0; margin-top:1px; }
.s-tool-list { display:flex; gap:5px; flex-wrap:wrap; margin-bottom:4px; }
.s-tool-tag { font-size:11px; padding:3px 10px; border-radius:999px; background:var(--paper-deep); color:var(--ink); border:1px solid var(--line); }
.s-fashion-style { font-size:14px; font-weight:500; color:var(--ink); padding:2px 0 4px; }
.s-color-list { display:flex; gap:8px; flex-wrap:wrap; margin:6px 0 8px; }
.s-color-item { display:flex; align-items:center; gap:6px; font-size:12px; color:var(--ink); }
.s-color-dot { width:20px; height:20px; border-radius:50%; border:2px solid rgba(255,255,255,0.8); box-shadow:0 1px 4px rgba(0,0,0,0.12); flex-shrink:0; }
.s-color-desc { color:var(--muted); font-size:11px; }
.s-outfit-list { display:flex; flex-direction:column; gap:6px; margin-bottom:2px; }
.s-outfit-item { display:flex; gap:8px; padding:7px 10px; border-radius:10px; background:rgba(255,255,255,0.3); border:1px solid var(--line); font-size:13px; align-items:center; }
.s-outfit-ico { font-size:16px; flex-shrink:0; width:22px; text-align:center; }
.s-outfit-body { flex:1; }
.s-outfit-name { font-weight:600; color:var(--ink); }
.s-outfit-desc { color:var(--muted); font-size:12px; margin-left:4px; }
.s-outfit-color { margin-left:auto; font-size:11px; padding:1px 8px; border-radius:999px; background:rgba(141,95,63,0.08); color:var(--accent-deep); border:1px solid rgba(141,95,63,0.12); flex-shrink:0; }
.s-mistake-list { display:flex; flex-direction:column; gap:3px; margin-bottom:4px; }
.s-mistake-item { font-size:12px; color:#92400e; padding:5px 10px; background:rgba(217,119,6,0.06); border-radius:8px; border-left:3px solid #d97706; }
.s-season-tag { display:inline-block; font-size:11px; padding:2px 10px; border-radius:999px; background:rgba(125,139,111,0.10); color:var(--sage); border:1px solid rgba(125,139,111,0.16); margin-bottom:10px; }
.s-slogan { margin-top:10px; padding:9px 14px; background:rgba(141,95,63,0.08); border-radius:10px; border:1px solid rgba(141,95,63,0.14); font-size:13px; font-weight:500; color:var(--accent-deep); text-align:center; }
.s-sev-low { display:inline-block; font-size:11px; padding:2px 10px; border-radius:999px; background:rgba(125,139,111,0.10); color:var(--sage); border:1px solid rgba(125,139,111,0.16); margin-bottom:8px; }
.s-sev-med { display:inline-block; font-size:11px; padding:2px 10px; border-radius:999px; background:rgba(217,119,6,0.08); color:#92400e; border:1px solid rgba(217,119,6,0.16); margin-bottom:8px; }
.s-sev-high { display:inline-block; font-size:11px; padding:2px 10px; border-radius:999px; background:rgba(185,28,28,0.06); color:#b91c1c; border:1px solid rgba(185,28,28,0.12); margin-bottom:8px; }
.s-key { margin-top:12px; padding:8px 12px; border-radius:10px; background:linear-gradient(135deg,rgba(226,204,170,0.50),rgba(255,250,241,0.42)); border:1px solid var(--line); font-size:13px; color:#5f513f; display:flex; align-items:center; gap:6px; }
.s-safety { margin-top:10px; padding:8px 12px; border-radius:10px; background:rgba(185,28,28,0.05); border:1px solid rgba(185,28,28,0.10); font-size:12px; color:#b91c1c; display:flex; align-items:center; gap:6px; }
.s-text { font-size:13px; color:var(--muted); line-height:1.6; margin-bottom:4px; }
.s-text-line { font-size:13px; color:var(--ink); line-height:1.6; padding:2px 0; }
.s-answer { font-size:14px; line-height:1.7; color:var(--ink); }
.s-followups { display:flex; flex-wrap:wrap; gap:6px; margin-top:14px; padding-top:12px; border-top:1px solid var(--line); }
.s-followup-title { width:100%; font-size:12px; color:var(--muted); margin-bottom:2px; }
.s-followup-chip { font-size:12px; padding:4px 12px; border-radius:999px; background:rgba(141,95,63,0.08); color:var(--accent-deep); border:1px solid rgba(141,95,63,0.14); cursor:pointer; transition:all 0.12s; }
.s-followup-chip:hover { background:rgba(141,95,63,0.15); }
.s-followup-chip:active { transform:scale(.96); }
.md-scene-label { font-size:12px; color:var(--muted); padding:0 0 8px; border-bottom:1px solid var(--line); margin-bottom:10px; }
.scene-image { margin-top: 0; cursor: pointer; border-radius: 14px 14px 0 0; overflow: hidden; position: relative; }
.scene-image img { width: 100%; max-height: 240px; object-fit: cover; display: block; border-bottom: 1px solid var(--line); }
.scene-badge { position: absolute; left: 10px; bottom: 10px; padding: 3px 8px; border-radius: 999px; background: rgba(104,67,43,.85); color: #fff7ea; font-size: 11px; line-height: 1; }
.kb-badge { display:inline-block; font-size:11px; padding:2px 8px; border-radius:999px; background:rgba(125,139,111,0.10); color:var(--sage); border:1px solid rgba(125,139,111,0.16); margin:10px 18px 0; }
/* deep compat with card-body */









.s-fashion-occasion{display:inline-block;padding:6px 14px;border-radius:20px;font-size:13px;font-weight:500;margin-bottom:12px;background:#f0e6ff;color:#7c3aed}
.s-color-row{display:flex;gap:10px;margin-bottom:16px;flex-wrap:wrap}
.s-color-dot-lg{width:44px;height:44px;border-radius:50%;border:2px solid rgba(0,0,0,.06);flex-shrink:0;position:relative;display:flex;align-items:center;justify-content:center}
.s-color-label{position:absolute;bottom:-18px;left:50%;transform:translateX(-50%);font-size:10px;color:#888;white-space:nowrap}
.s-mb-list{display:flex;flex-direction:column;gap:8px;margin-bottom:16px}
.s-mb-item{display:flex;align-items:center;gap:12px;padding:10px 12px;background:#f8f8fa;border-radius:12px}
.s-mb-icon{width:40px;height:40px;border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:18px;flex-shrink:0}
.s-mb-ifo{flex:1;min-width:0}
.s-mb-n{font-size:14px;font-weight:500;color:#1d1d1f}
.s-mb-d{font-size:12px;color:#888;margin-top:1px;display:flex;align-items:center;gap:6px}
.s-mb-c{width:12px;height:12px;border-radius:50%;border:1px solid rgba(0,0,0,.08);flex-shrink:0;display:inline-block}
</style>

