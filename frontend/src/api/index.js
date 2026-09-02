// 本地独立模式：不再请求后端服务器
import {
  getAiConfig,
  saveAiConfig,
  listConversations,
  getConversation as dbGetConversation,
  createConversation,
  renameConversation as dbRename,
  deleteConversation as dbDelete,
  addMessage,
  listFavorites,
  addFavorite as dbAddFavorite,
  removeFavorite as dbRemoveFavorite,
  updateFavoriteCategory as dbUpdateFavCat,
  searchAll,
  listKnowledge,
  addKnowledge as dbAddKb,
  updateKnowledge as dbUpdateKb,
  deleteKnowledge as dbDeleteKb,
  markKnowledgeHelpful,
  exportKnowledge as dbExportKb,
  importKnowledge as dbImportKb,
  getDashboardStats,
  getAiAudit,
  clearAiAudit as dbClearAudit
} from '../local/db'
import { callLocalChat, testAiConnection as testAiConnectionRaw, resolveOpenAIEndpoints, generateLocalImage } from '../local/aiClient'
import router from '../router'

function ok(data) {
  return { code: 200, message: 'ok', data }
}

function delay(ms = 0) {
  return new Promise(r => setTimeout(r, ms))
}

export function login(phone, password) {
  const p = String(phone || '').trim()
  const pw = String(password || '')
  if (!p || !pw) return Promise.reject({ response: { data: { message: '请输入手机号和密码' } } })
  // 本地自用：任意非空即可；同手机号复用用户名
  const users = JSON.parse(localStorage.getItem('lw_local_users') || '{}')
  const existed = users[p]
  if (existed && existed.password && existed.password !== pw) {
    return Promise.reject({ response: { data: { message: '密码不正确' } } })
  }
  if (!existed) {
    users[p] = { id: Date.now(), username: '本地用户' + p.slice(-4), phone: p, password: pw }
    localStorage.setItem('lw_local_users', JSON.stringify(users))
  }
  const u = users[p]
  return Promise.resolve(ok({
    id: u.id,
    username: u.username,
    phone: u.phone,
    token: 'local-' + u.id
  }))
}

export function register(username, phone, password) {
  const p = String(phone || '').trim()
  const pw = String(password || '')
  const name = String(username || '').trim() || ('本地用户' + p.slice(-4))
  if (!p || !pw) return Promise.reject({ response: { data: { message: '请填写完整信息' } } })
  if (pw.length < 6) return Promise.reject({ response: { data: { message: '密码至少6位' } } })
  const users = JSON.parse(localStorage.getItem('lw_local_users') || '{}')
  if (users[p]) return Promise.reject({ response: { data: { message: '该手机号已注册' } } })
  users[p] = { id: Date.now(), username: name, phone: p, password: pw }
  localStorage.setItem('lw_local_users', JSON.stringify(users))
  return Promise.resolve(ok(true))
}

export function forgotPassword(phone) {
  return Promise.resolve(ok({ code: '123456' }))
}

export function resetPassword(phone, code, newPassword) {
  if (String(code) !== '123456') return Promise.reject({ response: { data: { message: '验证码错误（本地固定 123456）' } } })
  const users = JSON.parse(localStorage.getItem('lw_local_users') || '{}')
  const p = String(phone || '').trim()
  if (!users[p]) return Promise.reject({ response: { data: { message: '用户不存在' } } })
  users[p].password = String(newPassword || '')
  localStorage.setItem('lw_local_users', JSON.stringify(users))
  return Promise.resolve(ok(true))
}

export async function sendChat(message, scene, conversationId, imageUrl, options = {}) {
  await delay(10)
  let convId = conversationId || null
  let createdConversation = false
  const persistUser = options.persistUser !== false
  if (!convId) {
    const title = (message || '新对话').slice(0, 50)
    const conv = createConversation(title, scene || 'other')
    convId = conv.id
    createdConversation = true
  }
  // Persist the question before the network request so a WebView recreation never restores an empty chat.
  if (persistUser) addMessage(convId, 'user', message || '', imageUrl || '')
  if (createdConversation) options.onConversationCreated?.(convId)
  try {
    const ai = await callLocalChat({
      message,
      scene: scene || 'other',
      conversationId: convId,
      imageUrl,
      followUp: !!options.followUp,
      excludeLatestUser: persistUser,
      onChunk: typeof options.onChunk === 'function' ? options.onChunk : null
    })
    const aiMsg = addMessage(convId, 'assistant', ai.content, '')
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('lifewise-conversation-updated', { detail: { conversationId: convId } }))
    }
    return ok({
      id: aiMsg.id,
      conversationId: convId,
      role: 'assistant',
      content: ai.content,
      source: ai.source,
      externalCall: ai.externalCall,
      sourceLabel: ai.sourceLabel,
      createdAt: aiMsg.createdAt
    })
  } catch (e) {
    const msg = e?.message || 'AI 调用失败'
    throw { response: { data: { message: msg } }, message: msg }
  }
}

export function getConversations(scene) {
  return Promise.resolve(ok(listConversations(scene)))
}

export function getConversation(id) {
  const data = dbGetConversation(id)
  if (!data) return Promise.reject({ response: { data: { message: '会话不存在' } } })
  return Promise.resolve(ok(data))
}

export function getFavorites(category) {
  return Promise.resolve(ok(listFavorites(category)))
}

export function addFavorite(messageId, note, category) {
  try {
    return Promise.resolve(ok(dbAddFavorite(messageId, note, category)))
  } catch (e) {
    return Promise.reject({ response: { data: { message: e.message } } })
  }
}

export function removeFavorite(messageId) {
  return Promise.resolve(ok(dbRemoveFavorite(messageId)))
}

export function updateFavoriteCategory(id, category) {
  try {
    return Promise.resolve(ok(dbUpdateFavCat(id, category)))
  } catch (e) {
    return Promise.reject({ response: { data: { message: e.message } } })
  }
}

export function search(q) {
  return Promise.resolve(ok(searchAll(q)))
}

export function getDashboard() {
  return Promise.resolve(ok(getDashboardStats()))
}

export function renameConversation(id, title) {
  try {
    return Promise.resolve(ok(dbRename(id, title)))
  } catch (e) {
    return Promise.reject({ response: { data: { message: e.message } } })
  }
}

export function deleteConversation(id) {
  return Promise.resolve(ok(dbDelete(id)))
}

export function repairConversationTitles() {
  // 本地无脏标题问题，直接返回列表
  return Promise.resolve(ok(listConversations()))
}

export function searchKnowledge(keyword, scene) {
  return Promise.resolve(ok(listKnowledge(keyword, scene)))
}

export function addKnowledge(data) {
  return Promise.resolve(ok(dbAddKb(data)))
}

export function deleteKnowledge(id) {
  return Promise.resolve(ok(dbDeleteKb(id)))
}

export function updateKnowledge(id, data) {
  try {
    return Promise.resolve(ok(dbUpdateKb(id, data)))
  } catch (e) {
    return Promise.reject({ response: { data: { message: e.message } } })
  }
}

export function markHelpful(id) {
  try {
    return Promise.resolve(ok(markKnowledgeHelpful(id)))
  } catch (e) {
    return Promise.reject({ response: { data: { message: e.message } } })
  }
}

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('图片读取失败'))
    reader.readAsDataURL(file)
  })
}

async function compressImage(file, maxEdge = 1600, quality = 0.84) {
  const original = await readFileAsDataUrl(file)
  if (file.type === 'image/gif' || file.type === 'image/svg+xml') return original

  const img = await new Promise((resolve, reject) => {
    const node = new Image()
    node.onload = () => resolve(node)
    node.onerror = () => reject(new Error('图片解码失败'))
    node.src = original
  })

  const scale = Math.min(1, maxEdge / Math.max(img.naturalWidth || img.width, img.naturalHeight || img.height))
  const width = Math.max(1, Math.round((img.naturalWidth || img.width) * scale))
  const height = Math.max(1, Math.round((img.naturalHeight || img.height) * scale))
  if (scale === 1 && file.size <= 2 * 1024 * 1024) return original

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) return original
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, width, height)
  ctx.drawImage(img, 0, 0, width, height)
  return canvas.toDataURL('image/jpeg', quality)
}

export async function uploadImage(file, onProgress) {
  onProgress?.({ loaded: 1, total: 4 })
  const dataUrl = await compressImage(file)
  onProgress?.({ loaded: 4, total: 4 })
  return ok({ url: dataUrl })
}

export async function generateFoodImage(promptOrName, scene) {
  const raw = String(promptOrName || '').trim()
  if (!raw) {
    return { code: 400, message: '生图内容为空', data: null }
  }
  const prompt = scene
    ? raw
    : ('Professional food photography of ' + raw + ', appetizing plating, natural light, high detail, warm atmosphere, close-up')
  try {
    const r = await generateLocalImage(prompt)
    if (!scene) {
      try { localStorage.setItem('food_img_' + raw, r.imageUrl) } catch {}
    }
    return ok({
      imageUrl: r.imageUrl,
      model: r.model,
      source: r.source || 'openai-compatible-image',
      provider: r.model
    })
  } catch (e) {
    return { code: 500, message: e?.message || '生图失败', data: null }
  }
}

export function lookupFoodImage(dishName) {
  const name = String(dishName || '').trim()
  if (!name) return Promise.resolve(ok({ found: false }))
  try {
    const url = localStorage.getItem('food_img_' + name)
    if (url) return Promise.resolve(ok({ found: true, imageUrl: url, source: 'local-cache' }))
  } catch {}
  return Promise.resolve(ok({ found: false }))
}

export function getFoodImageStatus() {
  return Promise.resolve(ok({ status: 'disabled' }))
}

export function exportKnowledge() {
  const blob = new Blob([JSON.stringify(dbExportKb(), null, 2)], { type: 'application/json' })
  return Promise.resolve(blob)
}

export function importKnowledge(items) {
  return Promise.resolve(ok(dbImportKb(items)))
}

export function getAiConfigStatus() {
  const cfg = getAiConfig()
  const recentCalls = getAiAudit()
  const today = new Date().toISOString().slice(0, 10)
  const todayList = recentCalls.filter(i => String(i.time || '').startsWith(today))
  return Promise.resolve(ok({
    mode: 'local',
    configured: !!(cfg.apiKey && cfg.baseUrl),
    baseUrl: cfg.baseUrl,
    model: cfg.model,
    hasKey: !!cfg.apiKey,
    warnings: [
      ...(cfg.apiKey ? [] : ['尚未填写 API Key']),
      ...(cfg.baseUrl ? [] : ['尚未填写接口地址'])
    ],
    costGuard: '本地直连模式：费用由你的 API Key 所属平台结算，App 不中转。',
    todayStats: {
      totalCalls: todayList.length,
      externalCalls: todayList.filter(i => i.status === 'ok' || i.status === 'calling').length,
      cacheHits: 0
    },
    recentCalls
  }))
}

export function clearAiAudit() {
  dbClearAudit()
  return Promise.resolve(ok(true))
}

export function exportConversationToFile(text) {
  // 浏览器下载
  const blob = new Blob([text || ''], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'lifewise-chat.txt'
  a.click()
  URL.revokeObjectURL(url)
  return Promise.resolve(ok(true))
}

export function saveFoodImageCache() {
  return Promise.resolve(ok(true))
}

// 给设置页用
export function loadLocalAiConfig() {
  return getAiConfig()
}

export function saveLocalAiConfig(cfg) {
  return saveAiConfig(cfg)
}

export async function testAiConnection(cfg) {
  return testAiConnectionRaw(cfg || null)
}

export function previewAiEndpoints(baseUrl) {
  try { return resolveOpenAIEndpoints(baseUrl) } catch (e) { return { error: e.message } }
}

// 兼容旧代码：若有人 import api 默认实例
export default {
  get: () => Promise.reject(new Error('local mode')),
  post: () => Promise.reject(new Error('local mode'))
}

// 未登录时路由守卫仍依赖 localStorage user；这里无需 axios 401 跳转
export function _localGuardRedirectLogin() {
  if (router.currentRoute.value.path !== '/login') router.replace('/login')
}
