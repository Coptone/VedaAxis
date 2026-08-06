<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowUpRight, CalendarClock, Cloud, Copy, Grid2X2, Grid3X3, Plus, RefreshCw, ShieldAlert } from 'lucide-vue-next'
import { api, ApiError } from '../api/client'
import type { PlanSummary, TrackMode } from '../types/domain'
import { DMU_ENCOUNTER_ID, DMU_P1_P2_STRATEGY, DMU_TERRITORY_ID } from '../data/dmuP1P2Default'
import { O8S_ENCOUNTER_ID, O8S_POC_STRATEGY, O8S_TERRITORY_ID } from '../data/o8sPocDefault'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const plans = ref<PlanSummary[]>([])
const loading = ref(true)
const creating = ref<'FOUR' | 'EIGHT' | 'O8S' | null>(null)
const copyingId = ref('')
const error = ref('')
const lastSyncedAt = ref<Date | null>(null)
let refreshTimer: ReturnType<typeof window.setInterval> | undefined

onMounted(() => {
  void load(true)
  window.addEventListener('focus', refreshWhenVisible)
  document.addEventListener('visibilitychange', refreshWhenVisible)
  refreshTimer = window.setInterval(() => {
    if (!document.hidden) void load(false)
  }, 30_000)
})

onUnmounted(() => {
  window.removeEventListener('focus', refreshWhenVisible)
  document.removeEventListener('visibilitychange', refreshWhenVisible)
  if (refreshTimer) window.clearInterval(refreshTimer)
})

function refreshWhenVisible() {
  if (!document.hidden) void load(false)
}

async function load(showLoading: boolean) {
  if (showLoading) loading.value = true
  try {
    plans.value = await api.plans()
    lastSyncedAt.value = new Date()
    error.value = ''
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '计划列表加载失败'
  } finally {
    if (showLoading) loading.value = false
  }
}

async function copyPlan(planId: string) {
  copyingId.value = planId
  try {
    const copied = await api.copyPlan(planId)
    await router.push(`/plans/${copied.plan.id}`)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '计划复制失败'
  } finally {
    copyingId.value = ''
  }
}

async function create(mode: TrackMode) {
  creating.value = mode
  try {
    const created = await api.createPlan({
      name: mode === 'EIGHT' ? '妖星乱舞 P1/P2 默认减伤表' : '妖星乱舞四轨扩展草稿',
      encounterId: DMU_ENCOUNTER_ID,
      territoryId: DMU_TERRITORY_ID,
      strategyTag: mode === 'EIGHT' ? DMU_P1_P2_STRATEGY : 'DMU-P1P2-FOUR',
      trackMode: mode,
    })
    await router.push(`/plans/${created.plan.id}`)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '计划创建失败'
  } finally {
    creating.value = null
  }
}

async function createO8sPoc() {
  creating.value = 'O8S'
  try {
    const created = await api.createPlan({
      name: 'O8S 游戏与网页联调计划',
      encounterId: O8S_ENCOUNTER_ID,
      territoryId: O8S_TERRITORY_ID,
      strategyTag: O8S_POC_STRATEGY,
      trackMode: 'EIGHT',
    })
    await router.push(`/plans/${created.plan.id}`)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : 'O8S 联调计划创建失败'
  } finally {
    creating.value = null
  }
}
</script>

<template>
  <section class="content-page plans-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">PLANNING WORKSPACE</p>
        <h1>减伤计划</h1>
        <p>编辑整队安排，发布不可变版本，再由插件匹配个人轨道。</p>
      </div>
      <div class="create-actions">
        <button class="secondary-button" type="button" :disabled="Boolean(creating)" @click="createO8sPoc">
          <ShieldAlert :size="17" />{{ creating === 'O8S' ? '创建中…' : '新建 O8S 联调计划' }}
        </button>
        <button class="secondary-button" type="button" :disabled="Boolean(creating)" @click="create('FOUR')">
          <Grid2X2 :size="17" />{{ creating === 'FOUR' ? '创建中…' : '新建 4 轨' }}
        </button>
        <button class="primary-button" type="button" :disabled="Boolean(creating)" @click="create('EIGHT')">
          <Plus :size="17" />{{ creating === 'EIGHT' ? '创建中…' : '新建 8 轨' }}
        </button>
      </div>
    </div>

    <div class="readiness-strip">
      <div><ShieldAlert :size="19" /><span><b>核心能力状态</b> 原生热键栏高亮等待国服实机 PoC</span></div>
      <span class="status-badge warning">POC_PENDING</span>
    </div>

    <div class="cloud-sync-strip">
      <Cloud :size="19" />
      <div>
        <b>云端计划空间：{{ auth.accountLabel }}</b>
        <small>计划按登录账户同步；切换到同一账户后，公司与家中的计划会显示一致。</small>
      </div>
      <span v-if="lastSyncedAt">刚刚拉取 {{ plans.length }} 个计划</span>
      <button class="secondary-button compact-button" type="button" :disabled="loading" @click="load(false)">
        <RefreshCw :size="15" />刷新云端
      </button>
    </div>

    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="loading-panel">正在读取计划…</div>
    <div v-else-if="plans.length === 0" class="empty-plans">
      <span><Grid3X3 :size="31" /></span>
      <h2>从第一条可验证的轴开始</h2>
      <p>八轨适用于高难团队副本；四轨为后续四人内容和通用模型预留。</p>
      <button class="primary-button" type="button" @click="create('EIGHT')"><Plus :size="17" />创建妖星乱舞 P1/P2 八轨计划</button>
    </div>
    <div v-else class="plan-grid">
      <article v-for="plan in plans" :key="plan.id" class="plan-card" role="button" tabindex="0" @click="router.push(`/plans/${plan.id}`)" @keyup.enter="router.push(`/plans/${plan.id}`)">
        <div class="plan-card-top">
          <span :class="['mode-icon', plan.trackMode.toLowerCase()]">
            <Grid2X2 v-if="plan.trackMode === 'FOUR'" :size="20" />
            <Grid3X3 v-else :size="20" />
          </span>
          <span class="plan-card-tools">
            <button class="icon-button" type="button" :title="copyingId === plan.id ? '复制中' : '复制计划'" :disabled="Boolean(copyingId)" @click.stop="copyPlan(plan.id)"><Copy :size="15" /></button>
            <ArrowUpRight :size="18" />
          </span>
        </div>
        <p class="eyebrow">{{ plan.trackMode === 'FOUR' ? '4 TRACKS' : '8 TRACKS' }}</p>
        <h2>{{ plan.name }}</h2>
        <p>{{ plan.strategyTag }} · Territory {{ plan.territoryId }}</p>
        <div class="plan-meta">
          <span>v{{ plan.latestVersion || '草稿' }}</span>
          <span><CalendarClock :size="14" />{{ new Date(plan.updatedAt).toLocaleDateString('zh-CN') }}</span>
        </div>
      </article>
    </div>
  </section>
</template>
