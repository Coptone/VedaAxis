<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowUpRight,
  CalendarClock,
  ChevronDown,
  Cloud,
  Copy,
  Grid2X2,
  Grid3X3,
  Plus,
  RefreshCw,
  ShieldAlert,
  Sparkles,
  Trash2,
  X,
} from 'lucide-vue-next'
import { api, ApiError } from '../api/client'
import { TRACK_SLOTS, type PlanSummary, type TrackMode, type TrackSlot } from '../types/domain'
import { DMU_ENCOUNTER_ID, DMU_P1_P2_STRATEGY, DMU_TERRITORY_ID } from '../data/dmuP1P2Default'
import { O8S_ENCOUNTER_ID, O8S_POC_STRATEGY, O8S_TERRITORY_ID } from '../data/o8sPocDefault'
import { useAuthStore } from '../stores/auth'
import { jobIconUrl } from '../lib/jobIcons'

type EncounterOptionId = 'DMU_P1P2' | 'O8S_POC'

interface EncounterOption {
  id: EncounterOptionId
  name: string
  description: string
  encounterId: string
  territoryId: number
  supportedModes: TrackMode[]
  defaultTemplateModes: TrackMode[]
}

interface JobOption {
  id: number
  name: string
  role: '防护' | '治疗' | '近战' | '远敏' | '法系'
}

type PartyJobSelection = Partial<Record<TrackSlot, number>>

const ENCOUNTERS: EncounterOption[] = [
  {
    id: 'DMU_P1P2',
    name: '妖星乱舞 P1/P2',
    description: '绝欧米茄 P1/P2 固定时间轴；八轨包含默认减伤，四轨仅加载时间轴。',
    encounterId: DMU_ENCOUNTER_ID,
    territoryId: DMU_TERRITORY_ID,
    supportedModes: ['EIGHT', 'FOUR'],
    defaultTemplateModes: ['EIGHT', 'FOUR'],
  },
  {
    id: 'O8S_POC',
    name: 'O8S 游戏联调',
    description: '仅用于游戏与网页联动验证的八轨探针计划，不代表正式攻略轴。',
    encounterId: O8S_ENCOUNTER_ID,
    territoryId: O8S_TERRITORY_ID,
    supportedModes: ['EIGHT'],
    defaultTemplateModes: ['EIGHT'],
  },
]

const JOBS: JobOption[] = [
  { id: 19, name: '骑士', role: '防护' },
  { id: 21, name: '战士', role: '防护' },
  { id: 32, name: '暗黑骑士', role: '防护' },
  { id: 37, name: '绝枪战士', role: '防护' },
  { id: 24, name: '白魔法师', role: '治疗' },
  { id: 28, name: '学者', role: '治疗' },
  { id: 33, name: '占星术士', role: '治疗' },
  { id: 40, name: '贤者', role: '治疗' },
  { id: 20, name: '武僧', role: '近战' },
  { id: 22, name: '龙骑士', role: '近战' },
  { id: 30, name: '忍者', role: '近战' },
  { id: 34, name: '武士', role: '近战' },
  { id: 39, name: '钐镰客', role: '近战' },
  { id: 41, name: '蝰蛇剑士', role: '近战' },
  { id: 23, name: '吟游诗人', role: '远敏' },
  { id: 31, name: '机工士', role: '远敏' },
  { id: 38, name: '舞者', role: '远敏' },
  { id: 25, name: '黑魔法师', role: '法系' },
  { id: 27, name: '召唤师', role: '法系' },
  { id: 35, name: '赤魔法师', role: '法系' },
  { id: 42, name: '绘灵法师', role: '法系' },
]

const JOB_BY_ID = new Map(JOBS.map((job) => [job.id, job]))
const SLOT_LABELS: Record<TrackSlot, string> = {
  T1: '防护轨',
  MT: '主坦',
  ST: '副坦',
  H1: '治疗 1',
  H2: '治疗 2',
  D1: '输出 1',
  D2: '输出 2',
  D3: '输出 3',
  D4: '输出 4',
}
const SLOT_ROLES: Record<TrackSlot, JobOption['role'][]> = {
  T1: ['防护'],
  MT: ['防护'],
  ST: ['防护'],
  H1: ['治疗'],
  H2: ['治疗'],
  D1: ['近战', '远敏', '法系'],
  D2: ['近战', '远敏', '法系'],
  D3: ['近战', '远敏', '法系'],
  D4: ['近战', '远敏', '法系'],
}
const DEFAULT_PARTY_JOBS: Record<TrackMode, PartyJobSelection> = {
  FOUR: {
    T1: 21,
    H1: 40,
    D1: 34,
    D2: 38,
  },
  EIGHT: {
    MT: 21,
    ST: 37,
    H1: 24,
    H2: 40,
    D1: 34,
    D2: 41,
    D3: 38,
    D4: 42,
  },
}

const router = useRouter()
const auth = useAuthStore()
const plans = ref<PlanSummary[]>([])
const loading = ref(true)
const creating = ref(false)
const copyingId = ref('')
const deletingId = ref('')
const pendingDeleteId = ref('')
const createWizardOpen = ref(false)
const selectedEncounterId = ref<EncounterOptionId>('DMU_P1P2')
const selectedTrackMode = ref<TrackMode>('EIGHT')
const partyJobIds = ref<PartyJobSelection>({ ...DEFAULT_PARTY_JOBS.EIGHT })
const activeJobSlot = ref<TrackSlot | null>(null)
const planName = ref('')
const planNameTouched = ref(false)
const useDefaultTemplate = ref(true)
const error = ref('')
const lastSyncedAt = ref<Date | null>(null)
let refreshTimer: ReturnType<typeof window.setInterval> | undefined

const selectedEncounter = computed(() =>
  ENCOUNTERS.find((encounter) => encounter.id === selectedEncounterId.value) ?? ENCOUNTERS[0]!)
const canUseDefaultTemplate = computed(() =>
  selectedEncounter.value.defaultTemplateModes.includes(selectedTrackMode.value))
const effectiveUseDefaultTemplate = computed(() =>
  useDefaultTemplate.value && canUseDefaultTemplate.value)
const pendingDeletePlan = computed(() =>
  plans.value.find((plan) => plan.id === pendingDeleteId.value) ?? null)
const selectedSlots = computed(() => TRACK_SLOTS[selectedTrackMode.value])
const canCreatePlan = computed(() =>
  planName.value.trim().length > 0 && selectedSlots.value.every((slot) => Boolean(partyJobIds.value[slot])))
const partySummary = computed(() =>
  selectedSlots.value
    .map((slot) => `${slot} ${jobForSlot(slot)?.name ?? '未选'}`)
    .join(' / '))

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

function selectEncounter(encounterId: EncounterOptionId) {
  selectedEncounterId.value = encounterId
  if (!selectedEncounter.value.supportedModes.includes(selectedTrackMode.value)) {
    selectedTrackMode.value = selectedEncounter.value.supportedModes[0]!
    resetPartyJobsForMode()
  }
  if (!canUseDefaultTemplate.value) {
    useDefaultTemplate.value = false
  }
  syncPlanName()
}

function openCreateWizard() {
  error.value = ''
  planNameTouched.value = false
  syncPlanName(true)
  resetPartyJobsForMode()
  createWizardOpen.value = true
}

function closeCreateWizard() {
  if (!creating.value) createWizardOpen.value = false
}

function strategyTagFor(encounter: EncounterOption, mode: TrackMode): string {
  if (encounter.id === 'O8S_POC') return O8S_POC_STRATEGY
  return mode === 'FOUR' ? 'DMU-P1P2-FOUR' : DMU_P1_P2_STRATEGY
}

function createPlanName(encounter: EncounterOption, mode: TrackMode, templated: boolean): string {
  if (encounter.id === 'O8S_POC') return 'O8S 游戏与网页联调计划'
  if (mode === 'FOUR') return templated ? '妖星乱舞 P1/P2 四轨时间轴草稿' : '妖星乱舞四轨空白草稿'
  return templated ? '妖星乱舞 P1/P2 默认减伤表' : '妖星乱舞八轨空白草稿'
}

function syncPlanName(force = false) {
  if (force || !planNameTouched.value) {
    planName.value = createPlanName(selectedEncounter.value, selectedTrackMode.value, effectiveUseDefaultTemplate.value)
  }
}

function onPlanNameInput() {
  planNameTouched.value = true
}

function changeTrackMode(event: Event) {
  const nextMode = (event.target as HTMLSelectElement).value as TrackMode
  selectedTrackMode.value = nextMode
  resetPartyJobsForMode()
  if (!canUseDefaultTemplate.value) useDefaultTemplate.value = false
  syncPlanName()
}

function toggleDefaultTemplate() {
  syncPlanName()
}

function resetPartyJobsForMode() {
  partyJobIds.value = { ...DEFAULT_PARTY_JOBS[selectedTrackMode.value] }
  activeJobSlot.value = null
}

function jobForSlot(slot: TrackSlot): JobOption | null {
  const jobId = partyJobIds.value[slot]
  return jobId ? JOB_BY_ID.get(jobId) ?? null : null
}

function jobIconForSlot(slot: TrackSlot): string | null {
  return jobIconUrl(jobForSlot(slot)?.id)
}

function jobsForSlot(slot: TrackSlot): JobOption[] {
  const roles = SLOT_ROLES[slot]
  return JOBS.filter((job) => roles.includes(job.role))
}

function toggleJobPicker(slot: TrackSlot) {
  activeJobSlot.value = activeJobSlot.value === slot ? null : slot
}

function selectPartyJob(slot: TrackSlot, jobId: number) {
  partyJobIds.value = { ...partyJobIds.value, [slot]: jobId }
  activeJobSlot.value = null
}

function selectedPartyPayload(): Partial<Record<TrackSlot, number>> {
  return Object.fromEntries(
    selectedSlots.value.map((slot) => [slot, partyJobIds.value[slot]]),
  ) as Partial<Record<TrackSlot, number>>
}

function preferredEditorJobId(): number {
  return partyJobIds.value.H2 ?? partyJobIds.value.H1 ?? partyJobIds.value.MT ?? partyJobIds.value.T1 ?? partyJobIds.value[selectedSlots.value[0]!] ?? 0
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

async function createFromWizard() {
  const encounter = selectedEncounter.value
  const templated = effectiveUseDefaultTemplate.value
  if (!canCreatePlan.value) {
    error.value = '请填写计划名称，并为每个轨道选择职业'
    return
  }
  creating.value = true
  try {
    const created = await api.createPlan({
      name: planName.value.trim(),
      encounterId: encounter.encounterId,
      territoryId: encounter.territoryId,
      strategyTag: strategyTagFor(encounter, selectedTrackMode.value),
      trackMode: selectedTrackMode.value,
      useDefaultTemplate: templated,
      partyJobIds: selectedPartyPayload(),
    })
    createWizardOpen.value = false
    await router.push({
      path: `/plans/${created.plan.id}`,
      query: { jobId: String(preferredEditorJobId()) },
    })
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '计划创建失败'
  } finally {
    creating.value = false
  }
}

function requestDeletePlan(plan: PlanSummary) {
  pendingDeleteId.value = plan.id
}

function cancelDeletePlan() {
  if (!deletingId.value) pendingDeleteId.value = ''
}

async function deletePendingPlan() {
  const plan = pendingDeletePlan.value
  if (!plan) return
  deletingId.value = plan.id
  const previousPlans = plans.value
  try {
    await api.deletePlan(plan.id)
    plans.value = plans.value.filter((item) => item.id !== plan.id)
    pendingDeleteId.value = ''
    lastSyncedAt.value = new Date()
  } catch (reason) {
    plans.value = previousPlans
    error.value = reason instanceof ApiError ? reason.message : '计划删除失败'
  } finally {
    deletingId.value = ''
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
        <button class="primary-button" type="button" @click="openCreateWizard">
          <Plus :size="17" />新建计划
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
      <p>先选副本、整队职业与计划名称，再决定是否套用默认时间轴和减伤模板。</p>
      <button class="primary-button" type="button" @click="openCreateWizard"><Plus :size="17" />新建计划</button>
    </div>
    <div v-else class="plan-grid">
      <article
        v-for="plan in plans"
        :key="plan.id"
        class="plan-card"
        role="button"
        tabindex="0"
        @click="router.push(`/plans/${plan.id}`)"
        @keyup.enter="router.push(`/plans/${plan.id}`)"
      >
        <div class="plan-card-top">
          <span :class="['mode-icon', plan.trackMode.toLowerCase()]">
            <Grid2X2 v-if="plan.trackMode === 'FOUR'" :size="20" />
            <Grid3X3 v-else :size="20" />
          </span>
          <span class="plan-card-tools">
            <button
              class="icon-button"
              type="button"
              :title="copyingId === plan.id ? '复制中' : '复制计划'"
              :disabled="Boolean(copyingId) || Boolean(deletingId)"
              @click.stop="copyPlan(plan.id)"
            >
              <Copy :size="15" />
            </button>
            <button
              class="icon-button danger"
              type="button"
              :title="deletingId === plan.id ? '删除中' : '删除计划'"
              :disabled="Boolean(copyingId) || Boolean(deletingId)"
              @click.stop="requestDeletePlan(plan)"
            >
              <Trash2 :size="15" />
            </button>
            <ArrowUpRight :size="18" />
          </span>
        </div>
        <p class="eyebrow">{{ plan.trackMode === 'FOUR' ? '4 TRACKS' : '8 TRACKS' }}</p>
        <h2>{{ plan.name }}</h2>
        <p>{{ plan.strategyTag }} · Territory {{ plan.territoryId }}</p>
        <div class="plan-meta">
          <span>{{ plan.latestVersion ? `已发布 v${plan.latestVersion}` : '草稿未发布' }}</span>
          <span><CalendarClock :size="14" />{{ new Date(plan.updatedAt).toLocaleDateString('zh-CN') }}</span>
        </div>
      </article>
    </div>

    <div v-if="createWizardOpen" class="modal-backdrop" role="dialog" aria-modal="true" aria-label="新建计划" @click.self="closeCreateWizard">
      <div class="create-plan-dialog">
        <header>
          <div>
            <p class="eyebrow">CREATE PLAN</p>
            <h2>新建减伤计划</h2>
            <small>先确定整队阵容，系统会按每个轨道的职业过滤可用减伤。</small>
          </div>
          <button class="icon-button" type="button" :disabled="creating" @click="closeCreateWizard"><X :size="16" /></button>
        </header>

        <section>
          <p class="wizard-step">1 · 选择副本</p>
          <div class="encounter-choice-grid">
            <button
              v-for="encounter in ENCOUNTERS"
              :key="encounter.id"
              type="button"
              :class="['encounter-choice', { active: encounter.id === selectedEncounterId }]"
              @click="selectEncounter(encounter.id)"
            >
              <b>{{ encounter.name }}</b>
              <small>Territory {{ encounter.territoryId }}</small>
              <span>{{ encounter.description }}</span>
            </button>
          </div>
        </section>

        <section class="create-plan-grid">
          <label class="wide-field">
            <span>2 · 减伤计划名称</span>
            <input v-model="planName" maxlength="80" placeholder="例如：妖星乱舞 P1/P2 固定队减伤" @input="onPlanNameInput" />
          </label>
          <label>
            <span>3 · 轨道模式</span>
            <select :value="selectedTrackMode" @change="changeTrackMode">
              <option v-for="mode in selectedEncounter.supportedModes" :key="mode" :value="mode">
                {{ mode === 'EIGHT' ? '8 轨 · 完整队伍' : '4 轨 · 扩展预留' }}
              </option>
            </select>
          </label>
        </section>

        <section>
          <p class="wizard-step">4 · 选择整队职业</p>
          <div class="party-track-grid">
            <article v-for="slot in selectedSlots" :key="slot" class="party-track-card">
              <header>
                <b>{{ slot }}</b>
                <small>{{ SLOT_LABELS[slot] }}</small>
              </header>
              <button class="job-select-card" type="button" @click="toggleJobPicker(slot)">
                <img v-if="jobForSlot(slot)" :src="jobIconForSlot(slot) ?? ''" alt="" loading="lazy" />
                <span v-else class="job-icon-placeholder">?</span>
                <span>
                  <b>{{ jobForSlot(slot)?.name ?? '选择职业' }}</b>
                  <small>{{ jobForSlot(slot)?.role ?? SLOT_ROLES[slot].join('/') }}</small>
                </span>
                <ChevronDown :size="15" />
              </button>
              <div v-if="activeJobSlot === slot" class="job-option-popover">
                <button
                  v-for="job in jobsForSlot(slot)"
                  :key="job.id"
                  type="button"
                  :class="{ active: partyJobIds[slot] === job.id }"
                  @click="selectPartyJob(slot, job.id)"
                >
                  <img :src="jobIconUrl(job.id) ?? ''" alt="" loading="lazy" />
                  <span>{{ job.name }}</span>
                </button>
              </div>
            </article>
          </div>
        </section>

        <section>
          <p class="wizard-step">5 · 是否套用默认模板</p>
          <label class="template-toggle-card">
            <input v-model="useDefaultTemplate" type="checkbox" :disabled="!canUseDefaultTemplate" @change="toggleDefaultTemplate" />
            <span>
              <b>加载对应副本的时间轴和减伤模板</b>
              <small v-if="canUseDefaultTemplate">
                {{ effectiveUseDefaultTemplate ? '会自动带入当前可用的时间轴和默认安排。' : '关闭后只创建空白草稿。' }}
              </small>
              <small v-else>这个副本/轨道暂时没有默认模板，只能创建空白草稿。</small>
            </span>
          </label>
        </section>

        <footer>
          <div>
            <Sparkles :size="16" />
            <span>
              将创建：{{ planName || createPlanName(selectedEncounter, selectedTrackMode, effectiveUseDefaultTemplate) }}
              · {{ selectedTrackMode === 'EIGHT' ? '8人阵容' : '4轨阵容' }}
              · {{ partySummary }}
            </span>
          </div>
          <button class="secondary-button" type="button" :disabled="creating" @click="closeCreateWizard">取消</button>
          <button class="primary-button" type="button" :disabled="creating || !canCreatePlan" @click="createFromWizard">
            <Plus :size="17" />{{ creating ? '创建中…' : '创建并打开' }}
          </button>
        </footer>
      </div>
    </div>

    <div v-if="pendingDeletePlan" class="modal-backdrop" role="dialog" aria-modal="true" aria-label="删除计划确认" @click.self="cancelDeletePlan">
      <div class="delete-plan-dialog">
        <header>
          <span><Trash2 :size="20" /></span>
          <div>
            <p class="eyebrow">DELETE PLAN</p>
            <h2>删除这个计划？</h2>
          </div>
        </header>
        <p>
          将删除“{{ pendingDeletePlan.name }}”的草稿和已发布版本；已经上传的个人复盘记录不会删除。
        </p>
        <div class="delete-plan-target">
          <b>{{ pendingDeletePlan.strategyTag }}</b>
          <small>{{ pendingDeletePlan.trackMode === 'EIGHT' ? '8 轨' : '4 轨' }} · Territory {{ pendingDeletePlan.territoryId }}</small>
        </div>
        <footer>
          <button class="secondary-button" type="button" :disabled="Boolean(deletingId)" @click="cancelDeletePlan">取消</button>
          <button class="primary-button danger-button delete-plan-confirm" type="button" :disabled="Boolean(deletingId)" @click="deletePendingPlan">
            <Trash2 :size="16" />{{ deletingId ? '删除中…' : '确认删除' }}
          </button>
        </footer>
      </div>
    </div>
  </section>
</template>
