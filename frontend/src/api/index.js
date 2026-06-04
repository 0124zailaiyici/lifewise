import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 30000
})

// 自动带上 token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  return config
})

// 处理 401 未登录
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

export function login(email, password) {
  return api.post('/users/login', { email, password })
}

export function register(username, email, password) {
  return api.post('/users/register', { username, email, password })
}

export function sendChat(message, scene, conversationId) {
  return api.post('/chat/send', { message, scene, conversationId })
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
