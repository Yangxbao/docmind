import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

// 路由表：登录页独立，其余页面共用 MainLayout（左侧菜单 + 顶栏）
const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../views/MainLayout.vue'),
    redirect: '/docs',
    children: [
      { path: 'docs', component: () => import('../views/DocList.vue') },
      { path: 'upload', component: () => import('../views/DocUpload.vue') },
      { path: 'search', component: () => import('../views/DocSearch.vue') },
      { path: 'preview/:id', component: () => import('../views/DocPreview.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：没有 token 一律踢回登录页
router.beforeEach((to) => {
  const store = useUserStore()
  if (to.path !== '/login' && !store.token) return '/login'
  if (to.path === '/login' && store.token) return '/'
  return true
})

export default router
