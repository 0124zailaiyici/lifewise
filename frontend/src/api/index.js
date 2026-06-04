import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
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
    const msg = error.response?.data?.message || '缃戠粶閿欒'
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
  return api.post('/chat/send', body)
}

export function getConversations(scene) {
  const params = scene ? { scene } : {}
  return api.get('/chat/conversations', { params })
}

export function getConversation(id) {
  return api.get('/chat/conversations/' + id)
}

export function getFavorites() {
  return api.get('/favorites')
}

export function addFavorite(messageId, note) {
  return api.post('/favorites', null, { params: { messageId, note } })
}

export function removeFavorite(messageId) {
  return api.delete('/favorites', { params: { messageId } })
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

export function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
