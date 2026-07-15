// 本地数据层：会话/消息/收藏/知识库/AI配置，全部 localStorage
const KEYS = {
  conversations: 'lw_local_conversations',
  messages: 'lw_local_messages',
  favorites: 'lw_local_favorites',
  kb: 'lw_local_kb',
  ai: 'lw_local_ai_config',
  audit: 'lw_local_ai_audit',
  seq: 'lw_local_seq'
}

function read(key, fallback) {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return fallback
    return JSON.parse(raw)
  } catch {
    return fallback
  }
}

function write(key, value) {
  localStorage.setItem(key, JSON.stringify(value))
}

function nextId(kind) {
  const seq = read(KEYS.seq, {})
  const n = (seq[kind] || 1000) + 1
  seq[kind] = n
  write(KEYS.seq, seq)
  return n
}

function nowIso() {
  return new Date().toISOString()
}

export function getAiConfig() {
  const defaults = {
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    apiKey: '',
    model: 'qwen-plus',
    imageModel: '',
    contextRounds: 5
  }
  const cfg = read(KEYS.ai, null)
  return cfg ? { ...defaults, ...cfg } : defaults
}

export function saveAiConfig(partial) {
  const next = { ...getAiConfig(), ...partial }
  write(KEYS.ai, next)
  return next
}

export function listConversations(scene) {
  let list = read(KEYS.conversations, [])
  if (scene) list = list.filter(c => c.scene === scene)
  return list.slice().sort((a, b) => String(b.updatedAt || b.createdAt).localeCompare(String(a.updatedAt || a.createdAt)))
}

export function getConversation(id) {
  const conv = read(KEYS.conversations, []).find(c => String(c.id) === String(id))
  if (!conv) return null
  const messages = read(KEYS.messages, [])
    .filter(m => String(m.conversationId) === String(id))
    .sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt)))
  return { ...conv, messages }
}

export function createConversation(title, scene = 'other') {
  const list = read(KEYS.conversations, [])
  const item = {
    id: nextId('conv'),
    title: title || '新对话',
    scene,
    createdAt: nowIso(),
    updatedAt: nowIso()
  }
  list.unshift(item)
  write(KEYS.conversations, list)
  return item
}

export function renameConversation(id, title) {
  const list = read(KEYS.conversations, [])
  const idx = list.findIndex(c => String(c.id) === String(id))
  if (idx < 0) throw new Error('会话不存在')
  list[idx] = { ...list[idx], title, updatedAt: nowIso() }
  write(KEYS.conversations, list)
  return list[idx]
}

export function deleteConversation(id) {
  write(KEYS.conversations, read(KEYS.conversations, []).filter(c => String(c.id) !== String(id)))
  write(KEYS.messages, read(KEYS.messages, []).filter(m => String(m.conversationId) !== String(id)))
  write(KEYS.favorites, read(KEYS.favorites, []).filter(f => String(f.conversationId) !== String(id)))
  return true
}

export function addMessage(conversationId, role, content, imageUrl = '') {
  const messages = read(KEYS.messages, [])
  const item = {
    id: nextId('msg'),
    conversationId,
    role,
    content: content || '',
    imageUrl: imageUrl || '',
    createdAt: nowIso()
  }
  messages.push(item)
  write(KEYS.messages, messages)
  // bump conversation
  const list = read(KEYS.conversations, [])
  const idx = list.findIndex(c => String(c.id) === String(conversationId))
  if (idx >= 0) {
    list[idx].updatedAt = nowIso()
    if (role === 'user' && (!list[idx].title || list[idx].title === '新对话')) {
      list[idx].title = (content || '新对话').slice(0, 50)
    }
    write(KEYS.conversations, list)
  }
  return item
}

export function getMessages(conversationId, limit = 0) {
  let list = read(KEYS.messages, [])
    .filter(m => String(m.conversationId) === String(conversationId))
    .sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt)))
  if (limit > 0 && list.length > limit) list = list.slice(-limit)
  return list
}

export function listFavorites(category) {
  let list = read(KEYS.favorites, [])
  if (category) list = list.filter(f => f.category === category)
  return list.slice().sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
}

export function addFavorite(messageId, note = '', category = '默认') {
  const msg = read(KEYS.messages, []).find(m => String(m.id) === String(messageId))
  if (!msg) throw new Error('消息不存在')
  const list = read(KEYS.favorites, [])
  if (list.some(f => String(f.messageId) === String(messageId))) {
    return list.find(f => String(f.messageId) === String(messageId))
  }
  const item = {
    id: nextId('fav'),
    messageId: msg.id,
    conversationId: msg.conversationId,
    content: msg.content,
    note: note || '',
    category: category || '默认',
    createdAt: nowIso()
  }
  list.unshift(item)
  write(KEYS.favorites, list)
  return item
}

export function removeFavorite(messageId) {
  write(KEYS.favorites, read(KEYS.favorites, []).filter(f => String(f.messageId) !== String(messageId)))
  return true
}

export function updateFavoriteCategory(id, category) {
  const list = read(KEYS.favorites, [])
  const idx = list.findIndex(f => String(f.id) === String(id))
  if (idx < 0) throw new Error('收藏不存在')
  list[idx] = { ...list[idx], category }
  write(KEYS.favorites, list)
  return list[idx]
}

export function searchAll(q) {
  const keyword = (q || '').trim().toLowerCase()
  if (!keyword) return { conversations: [], messages: [], favorites: [] }
  const conversations = listConversations().filter(c => String(c.title || '').toLowerCase().includes(keyword))
  const messages = read(KEYS.messages, []).filter(m => String(m.content || '').toLowerCase().includes(keyword)).slice(-50)
  const favorites = listFavorites().filter(f => String(f.content || '').toLowerCase().includes(keyword) || String(f.note || '').toLowerCase().includes(keyword))
  return { conversations, messages, favorites }
}

export function listKnowledge(keyword, scene) {
  let list = read(KEYS.kb, [])
  if (scene) list = list.filter(k => k.scene === scene)
  if (keyword) {
    const q = keyword.toLowerCase()
    list = list.filter(k =>
      String(k.title || '').toLowerCase().includes(q) ||
      String(k.content || '').toLowerCase().includes(q) ||
      String(k.tags || '').toLowerCase().includes(q)
    )
  }
  return list.slice().sort((a, b) => String(b.updatedAt || b.createdAt).localeCompare(String(a.updatedAt || a.createdAt)))
}

export function addKnowledge(data) {
  const list = read(KEYS.kb, [])
  const item = {
    id: nextId('kb'),
    title: data.title || '未命名',
    content: data.content || '',
    scene: data.scene || 'other',
    tags: data.tags || '',
    helpful: 0,
    createdAt: nowIso(),
    updatedAt: nowIso()
  }
  list.unshift(item)
  write(KEYS.kb, list)
  return item
}

export function updateKnowledge(id, data) {
  const list = read(KEYS.kb, [])
  const idx = list.findIndex(k => String(k.id) === String(id))
  if (idx < 0) throw new Error('条目不存在')
  list[idx] = { ...list[idx], ...data, updatedAt: nowIso() }
  write(KEYS.kb, list)
  return list[idx]
}

export function deleteKnowledge(id) {
  write(KEYS.kb, read(KEYS.kb, []).filter(k => String(k.id) !== String(id)))
  return true
}

export function markKnowledgeHelpful(id) {
  const list = read(KEYS.kb, [])
  const idx = list.findIndex(k => String(k.id) === String(id))
  if (idx < 0) throw new Error('条目不存在')
  list[idx].helpful = (list[idx].helpful || 0) + 1
  list[idx].updatedAt = nowIso()
  write(KEYS.kb, list)
  return list[idx]
}

export function exportKnowledge() {
  return listKnowledge()
}

export function importKnowledge(items) {
  if (!Array.isArray(items)) throw new Error('导入数据格式错误')
  const list = read(KEYS.kb, [])
  for (const raw of items) {
    list.unshift({
      id: nextId('kb'),
      title: raw.title || '未命名',
      content: raw.content || '',
      scene: raw.scene || 'other',
      tags: raw.tags || '',
      helpful: raw.helpful || 0,
      createdAt: nowIso(),
      updatedAt: nowIso()
    })
  }
  write(KEYS.kb, list)
  return list.length
}

export function getDashboardStats() {
  const conversations = listConversations()
  const messages = read(KEYS.messages, [])
  const favorites = listFavorites()
  const kb = listKnowledge()
  const audit = read(KEYS.audit, [])
  const today = new Date().toISOString().slice(0, 10)
  const todayMsgs = messages.filter(m => String(m.createdAt || '').startsWith(today))
  const sceneMap = {}
  for (const c of conversations) {
    const s = c.scene || 'other'
    sceneMap[s] = (sceneMap[s] || 0) + 1
  }
  const sceneLabel = {
    cooking: '做饭', shopping: '买菜', repair: '修理', housework: '家务',
    health: '健康', fashion: '穿搭', etiquette: '礼仪', pet: '宠物',
    mealplan: '食谱规划', writing: '写作', other: '其他'
  }
  const sceneIcon = {
    cooking: '🍳', shopping: '🛒', repair: '🔧', housework: '🏠',
    health: '💊', fashion: '👗', etiquette: '🤝', pet: '🐾',
    mealplan: '📅', writing: '✍️', other: '💬'
  }
  const sceneDistribution = Object.keys(sceneMap).map(scene => ({
    scene,
    count: sceneMap[scene],
    label: sceneLabel[scene] || scene,
    icon: sceneIcon[scene] || '💬'
  })).sort((a, b) => b.count - a.count)

  // last 30 days activity by conversation updates
  const activityMap = {}
  for (let i = 29; i >= 0; i--) {
    const d = new Date(); d.setDate(d.getDate() - i)
    const key = d.toISOString().slice(0, 10)
    activityMap[key] = 0
  }
  for (const m of messages) {
    const key = String(m.createdAt || '').slice(0, 10)
    if (key in activityMap) activityMap[key] += 1
  }
  const activityData = Object.keys(activityMap).map(date => ({ date, count: activityMap[date] }))

  const weekAgo = new Date(); weekAgo.setDate(weekAgo.getDate() - 7)
  const thisWeekMsgs = messages.filter(m => new Date(m.createdAt) >= weekAgo)
  const thisWeekConvs = conversations.filter(c => new Date(c.updatedAt || c.createdAt) >= weekAgo)

  return {
    dailyStats: {
      total: {
        conversations: conversations.length,
        messages: messages.length,
        favorites: favorites.length,
        knowledge: kb.length
      },
      today: { messages: todayMsgs.length, conversations: conversations.filter(c => String(c.updatedAt || '').startsWith(today)).length },
      thisWeek: { messages: thisWeekMsgs.length, conversations: thisWeekConvs.length }
    },
    sceneDistribution,
    activityData,
    conversationCount: conversations.length,
    messageCount: messages.length,
    favoriteCount: favorites.length,
    knowledgeCount: kb.length,
    todayAiCalls: audit.filter(a => String(a.time || '').startsWith(today)).length,
    recentConversations: conversations.slice(0, 5),
    weekSummary: []
  }
}

export function recordAiAudit(entry) {
  const list = read(KEYS.audit, [])
  list.unshift({
    time: nowIso(),
    provider: entry.provider || 'openai-compatible',
    model: entry.model || '',
    status: entry.status || 'ok',
    detail: entry.detail || ''
  })
  write(KEYS.audit, list.slice(0, 100))
}

export function getAiAudit() {
  return read(KEYS.audit, [])
}

export function clearAiAudit() {
  write(KEYS.audit, [])
}
