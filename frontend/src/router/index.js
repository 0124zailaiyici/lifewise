import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Chat from '../views/Chat.vue'
import History from '../views/History.vue'
import Favorites from '../views/Favorites.vue'
import Profile from '../views/Profile.vue'
import Search from '../views/Search.vue'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/login', component: Login },
  { path: '/home', component: Home },
  { path: '/chat', component: Chat },
  { path: '/chat/:id', component: Chat },
  { path: '/history', component: History },
  { path: '/favorites', component: Favorites },
  { path: '/profile', component: Profile },
  { path: '/dashboard', component: Dashboard },
  { path: '/search', component: Search }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const user = localStorage.getItem('user')
  if (to.path !== '/login' && !user) {
    next('/login')
  } else {
    next()
  }
})

export default router
