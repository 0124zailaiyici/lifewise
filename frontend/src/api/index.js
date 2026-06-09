import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  return config
})

api.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
      return Promise.reject(error)
    }
    const msg = error.response?.data?.message || '网络错误'
    console.error(msg)
    return Promise.reject(error)
  }
)

export function login(phone, password) {
  return api.post('/users/login', { phone, password })
}

export function register(username, phone, password) {
  return api.post('/users/register', { username, phone, password })
}

export function forgotPassword(phone) {
  return api.post('/users/forgot-password', { phone })
}

export function resetPassword(phone, code, newPassword) {
  return api.post('/users/reset-password', { phone, code, newPassword })
}

export function sendChat(message, scene, conversationId, imageUrl) {
  const body = { message, scene, conversationId }
  if (imageUrl) body.imageUrl = imageUrl
  // 从 localStorage 读取 AI provider 设置
  body.provider = localStorage.getItem('setting_aiProvider') || 'qwen'
  return api.post('/chat/send', body)
}

export function getConversations(scene) {
  const params = scene ? { scene } : {}
  return api.get('/chat/conversations', { params })
}

export function getConversation(id) {
  return api.get('/chat/conversations/' + id)
}

export function getFavorites(category) {
  const params = category ? { category } : {}
  return api.get('/favorites', { params })
}

export function addFavorite(messageId, note, category) {
  const params = { messageId, note }
  if (category) params.category = category
  return api.post('/favorites', null, { params })
}

export function removeFavorite(messageId) {
  return api.delete('/favorites', { params: { messageId } })
}

export function updateFavoriteCategory(id, category) {
  return api.put('/favorites/' + id + '/category', { category })
}

export function search(q) {
  return api.get('/search', { params: { q } })
}

export function getDashboard() {
  return api.get('/stats/dashboard')
}

export function renameConversation(id, title) {
  return api.put('/chat/conversations/' + id + '/rename', { title })
}

export function deleteConversation(id) {
  return api.delete('/chat/conversations/' + id)
}

export function repairConversationTitles() {
  return api.post('/chat/conversations/repair-titles')
}

export function searchKnowledge(keyword, scene) {
  const params = {}
  if (keyword) params.keyword = keyword
  if (scene) params.scene = scene
  return api.get('/kb/search', { params })
}

export function addKnowledge(data) {
  return api.post('/kb', data)
}

export function deleteKnowledge(id) {
  return api.delete('/kb/' + id)
}

export function updateKnowledge(id, data) {
  return api.put('/kb/' + id, data)
}

export function markHelpful(id) {
  return api.post('/kb/' + id + '/helpful')
}

export function uploadImage(file, onProgress) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  })
}

export function generateFoodImage(dishName) {
  return api.post('/food-image/generate', { dishName })
}

export function lookupFoodImage(dishName) {
  return api.post('/food-image/lookup', { dishName })
}

export function getFoodImageStatus(taskId) {
  return api.get('/food-image/status', { params: { taskId } })
}

export function exportKnowledge() {
  return api.get("/kb/export", { responseType: "blob" })
}

export function importKnowledge(items) {
  return api.post("/kb/import", items)
}

export function getAiConfigStatus() {
  return api.get('/ai-config/status')
}

export function clearAiAudit() {
  return api.delete('/ai-config/audit')
}

export function exportConversationToFile(text) {
  return api.post('/chat/export', { text })
}

export function saveFoodImageCache(dishName, imageUrl) {
  return api.post('/food-image/cache', { dishName, imageUrl })
}


