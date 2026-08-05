<script setup lang="ts">
import { Activity, ChartNoAxesCombined, LogOut, RadioTower, ShieldCheck } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()

function logout() {
  auth.logout()
  void router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/plans" aria-label="VedaAxis 首页">
        <span class="brand-mark"><ShieldCheck :size="21" /></span>
        <span>
          <strong>VedaAxis</strong>
          <small>MITIGATION CONTROL</small>
        </span>
      </RouterLink>

      <nav v-if="auth.authenticated" class="main-nav" aria-label="主导航">
        <RouterLink to="/plans"><Activity :size="16" />计划</RouterLink>
        <RouterLink to="/device"><RadioTower :size="16" />插件绑定</RouterLink>
        <RouterLink to="/executions"><ChartNoAxesCombined :size="16" />个人复盘</RouterLink>
      </nav>

      <div class="topbar-status">
        <span class="connection-pill"><i></i>PoC 环境</span>
        <button v-if="auth.authenticated" class="icon-button" type="button" title="退出登录" @click="logout">
          <LogOut :size="18" />
        </button>
      </div>
    </header>
    <main class="page-shell">
      <RouterView />
    </main>
  </div>
</template>
