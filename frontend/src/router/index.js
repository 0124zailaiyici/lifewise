import { createRouter, createWebHistory } from 'vue-router'

const Home = () => import('../views/Home.vue')
const Chat = () => import('../views/Chat.vue')
const History = () => import('../views/History.vue')
const Favorites = () => import('../views/Favorites.vue')
const Profile = () => import('../views/Profile.vue')
const Search = () => import('../views/Search.vue')
const Login = () => import('../views/Login.vue')
const Dashboard = () => import('../views/Dashboard.vue')
const KnowledgeBase = () => import('../views/KnowledgeBase.vue')

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
  { path: '/search', component: Search },
  { path: '/knowledge', component: KnowledgeBase }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
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
