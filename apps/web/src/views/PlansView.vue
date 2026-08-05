<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowUpRight, CalendarClock, Copy, Grid2X2, Grid3X3, Plus, ShieldAlert } from 'lucide-vue-next'
import { api, ApiError } from '../api/client'
import type { PlanSummary, TrackMode } from '../types/domain'

const DMU_ENCOUNTER_ID = 'c97e8840-1697-476f-a4ac-8c7996df277b'
const router = useRouter()
const plans = ref<PlanSummary[]>([])
const loading = ref(true)
const creating = ref<TrackMode | null>(null)
const copyingId = ref('')
const error = ref('')

onMounted(load)

async function load() {
  loading.value = true
  try {
    plans.value = await api.plans()
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '计划列表加载失败'
  } finally {
    loading.value = false
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
      name: mode === 'EIGHT' ? 'DMU 八人减伤计划' : '四人减伤计划',
      encounterId: DMU_ENCOUNTER_ID,
      strategyTag: mode === 'EIGHT' ? 'DMU-LPDU' : 'FOUR-PLAYER',
      trackMode: mode,
    })
    await router.push(`/plans/${created.plan.id}`)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '计划创建失败'
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

    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="loading-panel">正在读取计划…</div>
    <div v-else-if="plans.length === 0" class="empty-plans">
      <span><Grid3X3 :size="31" /></span>
      <h2>从第一条可验证的轴开始</h2>
      <p>八轨适用于高难团队副本；四轨为后续四人内容和通用模型预留。</p>
      <button class="primary-button" type="button" @click="create('EIGHT')"><Plus :size="17" />创建 DMU 八轨计划</button>
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
        <p>{{ plan.strategyTag }}</p>
        <div class="plan-meta">
          <span>v{{ plan.latestVersion || '草稿' }}</span>
          <span><CalendarClock :size="14" />{{ new Date(plan.updatedAt).toLocaleDateString('zh-CN') }}</span>
        </div>
      </article>
    </div>
  </section>
</template>
