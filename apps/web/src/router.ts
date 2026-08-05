import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from './stores/auth'
import DeviceAuthorizeView from './views/DeviceAuthorizeView.vue'
import LoginView from './views/LoginView.vue'
import PlanEditorView from './views/PlanEditorView.vue'
import PlansView from './views/PlansView.vue'
import SharedPlanView from './views/SharedPlanView.vue'
import ExecutionsView from './views/ExecutionsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/plans' },
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/plans', component: PlansView },
    { path: '/plans/new', component: PlanEditorView },
    { path: '/plans/:planId', component: PlanEditorView },
    { path: '/device', component: DeviceAuthorizeView },
    { path: '/executions', component: ExecutionsView },
    { path: '/share/:shareCode', component: SharedPlanView, meta: { public: true } },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.authenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && auth.authenticated) return '/plans'
  return true
})

export default router
