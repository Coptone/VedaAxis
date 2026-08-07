<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Activity, ChartNoAxesCombined, LogOut, RadioTower, ShieldCheck, Text } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()
const DISPLAY_SCALE_STORAGE_KEY = 'vedaaxis.displayScale'
const displayScaleOptions = [
  { value: 'auto', label: '自动' },
  { value: '1', label: '100%' },
  { value: '1.12', label: '112%' },
  { value: '1.25', label: '125%' },
  { value: '1.37', label: '137%' },
] as const
type DisplayScaleSetting = typeof displayScaleOptions[number]['value']

const displayScale = ref<DisplayScaleSetting>(readDisplayScale())
const appShellStyle = computed(() => displayScale.value === 'auto'
  ? {}
  : { '--display-scale': displayScale.value })

watch(displayScale, (value) => {
  try {
    if (value === 'auto') localStorage.removeItem(DISPLAY_SCALE_STORAGE_KEY)
    else localStorage.setItem(DISPLAY_SCALE_STORAGE_KEY, value)
  } catch {
    // Storage may be unavailable in embedded browsers; the current session value still applies.
  }
})

function logout() {
  auth.logout()
  void router.push('/login')
}

function readDisplayScale(): DisplayScaleSetting {
  try {
    const saved = localStorage.getItem(DISPLAY_SCALE_STORAGE_KEY)
    if (displayScaleOptions.some((option) => option.value === saved)) return saved as DisplayScaleSetting
  } catch {
    // Ignore storage failures and keep the automatic responsive scale.
  }
  return 'auto'
}
</script>

<template>
  <div class="app-shell" :style="appShellStyle">
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
        <label class="display-scale-control" title="调整网页端整体显示大小">
          <Text :size="15" />
          <span>显示</span>
          <select v-model="displayScale" aria-label="整体显示大小">
            <option v-for="option in displayScaleOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
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
