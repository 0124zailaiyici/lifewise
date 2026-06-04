import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

  const isLogin = computed(() => user.value !== null)

  function setUser(u) {
    user.value = {
      id: u.id,
      username: u.username,
      email: u.email,
      avatar: u.avatar
    }
    localStorage.setItem('user', JSON.stringify(user.value))
    if (u.token) {
      localStorage.setItem('token', u.token)
    }
  }

  function logout() {
    user.value = null
    localStorage.removeItem('user')
    localStorage.removeItem('token')
  }

  return { user, isLogin, setUser, logout }
})
