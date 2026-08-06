<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AlertTriangle,
  CheckCircle2,
  ChevronLeft,
  CircleDashed,
  Clock3,
  FileDown,
  Grid2X2,
  Grid3X3,
  Lock,
  LockOpen,
  Plus,
  Save,
  Send,
  Shield,
  Sparkles,
  Trash2,
} from 'lucide-vue-next'
import { api, ApiError } from '../api/client'
import { createTracks, formatTime } from '../lib/tracks'
import { newId } from '../lib/ids'
import { cloneData } from '../lib/cloneData'
import { applyTimelineImport } from '../lib/timelineImports'
import {
  DMU_ENCOUNTER_ID,
  DMU_P1_P2_STRATEGY,
  DMU_TERRITORY_ID,
  dmuP1P2DefaultPlan,
} from '../data/dmuP1P2Default'
import type {
  AbilityDefinition,
  AiCandidate,
  Assignment,
  PlanSnapshot,
  RuleValidationResult,
  TimelineImportCandidate,
  TimelineMechanic,
  TimelinePhase,
  TrackMode,
} from '../types/domain'

const route = useRoute()
const router = useRouter()
const planId = ref(typeof route.params.planId === 'string' ? route.params.planId : '')
const name = ref('妖星乱舞 P1/P2 默认减伤表')
const abilities = ref<AbilityDefinition[]>([])
const defaultPlan = dmuP1P2DefaultPlan()

function firstAssignedMechanicId(plan: PlanSnapshot): string {
  const assignedMechanics = new Set(plan.assignments.map((assignment) => assignment.mechanicId))
  return plan.mechanics.find((mechanic) => assignedMechanics.has(mechanic.mechanicId))?.mechanicId
    ?? plan.mechanics[0]?.mechanicId
    ?? ''
}

const selectedMechanicId = ref(firstAssignedMechanicId(defaultPlan))
const selectedTrackId = ref('')
const selectedTargetTrackId = ref('')
const selectedAbilityId = ref<number | null>(null)
const validation = ref<RuleValidationResult | null>(null)
const aiCandidate = ref<AiCandidate | null>(null)
const busy = ref(false)
const message = ref('')
const error = ref('')
const importOpen = ref(false)
const importUrl = ref('https://raalm.com/m-spec/timelinev2.html?boss=dancing-mad&spec=sage-sage&buddy=0')
const includeRecommendations = ref(true)
const importCandidate = ref<TimelineImportCandidate | null>(null)

const DEFAULT_PHASES: TimelinePhase[] = cloneData(defaultPlan.phases)
const DEFAULT_MECHANICS: TimelineMechanic[] = cloneData(defaultPlan.mechanics)
const snapshot = ref<PlanSnapshot>(makeSnapshot('EIGHT'))

const mechanics = computed(() => snapshot.value.mechanics)
const assignmentCountByMechanic = computed(() => {
  const counts = new Map<string, number>()
  for (const assignment of snapshot.value.assignments) {
    counts.set(assignment.mechanicId, (counts.get(assignment.mechanicId) ?? 0) + 1)
  }
  return counts
})
const timelineTitle = computed(() => {
  if (snapshot.value.source.kind === 'IMPORTED') return 'M-Spec 候选'
  const encounterName = snapshot.value.strategyTag.startsWith('DMU-P1P2') ? '妖星乱舞' : snapshot.value.strategyTag
  const phaseNames = snapshot.value.phases.map((phase) => phase.name.trim()).filter(Boolean).join('/')
  return phaseNames ? `${encounterName} · ${phaseNames}` : encounterName
})
const selectedMechanic = computed(() => mechanics.value.find((item) => item.mechanicId === selectedMechanicId.value) ?? mechanics.value[0] ?? DEFAULT_MECHANICS[0]!)
const assignmentsForMechanic = computed(() => snapshot.value.assignments.filter((item) => item.mechanicId === selectedMechanicId.value))
const abilityMap = computed(() => new Map(abilities.value.map((ability) => [ability.actionId, ability])))
const aiDiff = computed(() => {
  if (!aiCandidate.value) return { added: 0, removed: 0, changed: 0 }
  const current = new Map(snapshot.value.assignments.map((item) => [item.assignmentId, item]))
  const candidate = new Map(aiCandidate.value.assignments.map((item) => [item.assignmentId, item]))
  return {
    added: [...candidate.keys()].filter((id) => !current.has(id)).length,
    removed: [...current.keys()].filter((id) => !candidate.has(id)).length,
    changed: [...candidate.entries()].filter(([id, item]) => current.has(id) && JSON.stringify(current.get(id)) !== JSON.stringify(item)).length,
  }
})
const selectedAssignment = ref<Assignment | null>(null)

onMounted(async () => {
  try {
    abilities.value = await api.abilities()
    selectedAbilityId.value = abilities.value[0]?.actionId ?? null
  } catch {
    abilities.value = fallbackAbilities()
    selectedAbilityId.value = abilities.value[0].actionId
  }
  if (planId.value) await loadPlan()
  selectedTrackId.value = snapshot.value.tracks[0]?.trackId ?? ''
})

async function loadPlan() {
  busy.value = true
  try {
    const details = await api.plan(planId.value)
    name.value = details.plan.name
    snapshot.value = {
      ...details.snapshot,
      phases: details.snapshot.phases?.length ? details.snapshot.phases : cloneData(DEFAULT_PHASES),
      mechanics: details.snapshot.mechanics?.length ? details.snapshot.mechanics : cloneData(DEFAULT_MECHANICS),
    }
    selectedMechanicId.value = firstAssignedMechanicId(snapshot.value)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '计划加载失败'
  } finally {
    busy.value = false
  }
}

function makeSnapshot(mode: TrackMode): PlanSnapshot {
  if (mode === 'EIGHT') {
    const plan = dmuP1P2DefaultPlan()
    plan.planId = newId()
    plan.timelineId = newId()
    return plan
  }
  return {
    schemaVersion: '1.3',
    minimumPluginVersion: '0.1.7',
    planId: newId(),
    planVersion: 1,
    timelineId: newId(),
    timelineVersion: 1,
    encounterId: DMU_ENCOUNTER_ID,
    territoryId: DMU_TERRITORY_ID,
    strategyTag: 'DMU-P1P2-FOUR',
    trackMode: mode,
    source: { kind: 'PERSONAL', reference: null, confidence: 'UNVERIFIED' },
    phases: cloneData(DEFAULT_PHASES),
    mechanics: cloneData(DEFAULT_MECHANICS),
    anchors: [],
    tracks: createTracks(mode),
    assignments: [],
  }
}

function changeMode(mode: TrackMode) {
  if (planId.value || snapshot.value.trackMode === mode) return
  snapshot.value = makeSnapshot(mode)
  name.value = mode === 'EIGHT' ? '妖星乱舞 P1/P2 默认减伤表' : '妖星乱舞四轨扩展草稿'
  selectedTrackId.value = snapshot.value.tracks[0]?.trackId ?? ''
}

function addAssignment() {
  if (!selectedAbilityId.value || !selectedTrackId.value) return
  const mechanic = selectedMechanic.value
  const assignment: Assignment = {
    assignmentId: newId(),
    mechanicId: mechanic.mechanicId,
    trackId: selectedTrackId.value,
    actionId: selectedAbilityId.value,
    anchorId: null,
    targetTrackId: selectedTargetTrackId.value || null,
    highlightAtMs: Math.max(0, mechanic.plannedAtMs - 12_000),
    earliestUseAtMs: Math.max(0, mechanic.plannedAtMs - 8_000),
    latestUseAtMs: Math.max(0, mechanic.plannedAtMs - 1_000),
    impactAtMs: mechanic.plannedAtMs,
    locked: false,
    confirmationStrategy: abilityMap.value.get(selectedAbilityId.value)?.confirmationStrategy ?? 'ACTION_EFFECT',
    fallbacks: [],
  }
  snapshot.value.assignments.push(assignment)
  selectedAssignment.value = assignment
  validation.value = null
}

function removeAssignment(assignmentId: string) {
  snapshot.value.assignments = snapshot.value.assignments.filter((item) => item.assignmentId !== assignmentId)
  if (selectedAssignment.value?.assignmentId === assignmentId) selectedAssignment.value = null
  validation.value = null
}

async function save() {
  busy.value = true
  message.value = ''
  error.value = ''
  try {
    if (!planId.value) {
      const created = await api.createPlan({
        name: name.value,
        encounterId: snapshot.value.encounterId,
        territoryId: snapshot.value.territoryId,
        strategyTag: snapshot.value.strategyTag,
        trackMode: snapshot.value.trackMode,
      })
      planId.value = created.plan.id
      snapshot.value.planId = created.plan.id
      const createdTrackBySlot = new Map(created.snapshot.tracks.map((track) => [track.slot, track.trackId]))
      const localSlotByTrackId = new Map(snapshot.value.tracks.map((track) => [track.trackId, track.slot]))
      snapshot.value.assignments = snapshot.value.assignments.map((assignment) => ({
        ...assignment,
        trackId: createdTrackBySlot.get(localSlotByTrackId.get(assignment.trackId)!) ?? assignment.trackId,
        fallbacks: assignment.fallbacks.map((fallback) => ({
          ...fallback,
          trackId: createdTrackBySlot.get(localSlotByTrackId.get(fallback.trackId)!) ?? fallback.trackId,
        })),
      }))
      snapshot.value.tracks = created.snapshot.tracks
      await router.replace(`/plans/${created.plan.id}`)
    }
    const updated = await api.updatePlan(planId.value, name.value, snapshot.value)
    snapshot.value = updated.snapshot
    message.value = '草稿已保存'
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '保存失败'
  } finally {
    busy.value = false
  }
}

async function validate() {
  await save()
  if (!planId.value || error.value) return
  validation.value = await api.validatePlan(planId.value)
  message.value = validation.value.valid ? '规则校验通过' : '发现需要处理的规则问题'
}

async function publish() {
  await save()
  if (!planId.value || error.value) return
  busy.value = true
  try {
    const published = await api.publishPlan(planId.value)
    snapshot.value = published.snapshot
    validation.value = published.validation
    message.value = `已发布 v${published.snapshot.planVersion}，分享码 ${published.shareCode}`
  } catch (reason) {
    if (reason instanceof ApiError && reason.status === 422) {
      validation.value = reason.body as RuleValidationResult
      error.value = '规则校验未通过，计划没有发布'
    } else {
      error.value = reason instanceof ApiError ? reason.message : '发布失败'
    }
  } finally {
    busy.value = false
  }
}

async function generateAiCandidate() {
  await save()
  if (!planId.value || error.value) return
  busy.value = true
  try {
    aiCandidate.value = await api.generateAiCandidate(planId.value)
    message.value = `已生成 ${aiCandidate.value.confidence} 候选，尚未应用`
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : 'AI 候选生成失败'
  } finally {
    busy.value = false
  }
}

function applyAiCandidate() {
  if (!aiCandidate.value) return
  snapshot.value.assignments = cloneData(aiCandidate.value.assignments)
  snapshot.value.source = {
    kind: 'AI_CANDIDATE',
    reference: `${aiCandidate.value.provider} ${aiCandidate.value.model} · ${aiCandidate.value.candidateId}`,
    confidence: 'UNVERIFIED',
  }
  validation.value = aiCandidate.value.validation
  aiCandidate.value = null
  message.value = 'AI 候选已应用到本地草稿，需再次保存或发布'
}

async function previewMSpecImport() {
  busy.value = true
  error.value = ''
  message.value = ''
  importCandidate.value = null
  try {
    importCandidate.value = await api.importMSpecTimeline(importUrl.value, includeRecommendations.value)
    message.value = `已获取 ${importCandidate.value.stats.mechanicCount} 个机制，尚未应用`
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : 'M-Spec 候选获取失败'
  } finally {
    busy.value = false
  }
}

function applyMSpecImport() {
  if (!importCandidate.value) return
  const removedAssignments = snapshot.value.assignments.length
  snapshot.value = applyTimelineImport(snapshot.value, importCandidate.value, newId())
  selectedMechanicId.value = firstAssignedMechanicId(snapshot.value)
  selectedAssignment.value = null
  validation.value = null
  importCandidate.value = null
  importOpen.value = false
  message.value = `M-Spec 候选已应用到本地草稿${removedAssignments ? `，已清除 ${removedAssignments} 个旧任务` : ''}；尚未保存或发布`
}

function damageTypeLabel(type: TimelineMechanic['damageType']) {
  return ({ UNKNOWN: '未知', MAGICAL: '魔法', PHYSICAL: '物理', SPECIAL: '特殊' } as const)[type]
}

function fallbackAbilities(): AbilityDefinition[] {
  return [
    { actionId: 7535, name: '雪仇 / Reprisal', jobIds: [19, 21, 32, 37], cooldownMs: 60_000, maxCharges: 1, durationMs: 15_000, confirmationStrategy: 'STATUS_APPLY', source: 'Local fallback', confidence: 'REVIEWED' },
    { actionId: 24298, name: 'Kerachole', jobIds: [40], cooldownMs: 30_000, maxCharges: 1, durationMs: 15_000, confirmationStrategy: 'STATUS_APPLY', source: 'Local fallback', confidence: 'UNVERIFIED' },
    { actionId: 24310, name: 'Holos', jobIds: [40], cooldownMs: 120_000, maxCharges: 1, durationMs: 20_000, confirmationStrategy: 'STATUS_APPLY', source: 'Local fallback', confidence: 'UNVERIFIED' },
  ]
}
</script>

<template>
  <section class="editor-page">
    <div class="editor-header">
      <div class="editor-title">
        <button class="icon-button" type="button" title="返回计划列表" @click="router.push('/plans')"><ChevronLeft :size="19" /></button>
        <div>
          <p class="eyebrow">PLAN EDITOR · {{ snapshot.strategyTag }}</p>
          <input v-model="name" aria-label="计划名称" />
        </div>
      </div>
      <div class="editor-actions">
        <span v-if="message" class="inline-message"><CheckCircle2 :size="15" />{{ message }}</span>
        <button class="secondary-button" type="button" :disabled="busy" @click="save"><Save :size="16" />保存草稿</button>
        <button class="secondary-button" type="button" :disabled="busy" @click="validate"><Shield :size="16" />规则校验</button>
        <button class="secondary-button" type="button" :disabled="busy" @click="importOpen = !importOpen"><FileDown :size="16" />导入时间轴</button>
        <button class="secondary-button" type="button" :disabled="busy" @click="generateAiCandidate"><Sparkles :size="16" />AI 候选</button>
        <button class="primary-button" type="button" :disabled="busy" @click="publish"><Send :size="16" />发布版本</button>
      </div>
    </div>

    <div class="editor-subbar">
      <div class="mode-switch" aria-label="轨道模式">
        <button :class="{ active: snapshot.trackMode === 'FOUR' }" :disabled="Boolean(planId)" type="button" @click="changeMode('FOUR')"><Grid2X2 :size="15" />4 轨</button>
        <button :class="{ active: snapshot.trackMode === 'EIGHT' }" :disabled="Boolean(planId)" type="button" @click="changeMode('EIGHT')"><Grid3X3 :size="15" />8 轨</button>
      </div>
      <span><Clock3 :size="14" />P1/P2 绝对时间＋Action 锚点</span>
      <span>Territory {{ snapshot.territoryId }}</span>
      <span class="status-badge warning"><CircleDashed :size="13" />{{ snapshot.source.confidence }}</span>
      <span>计划 v{{ snapshot.planVersion }} · 时间轴 v{{ snapshot.timelineVersion }}</span>
    </div>

    <p v-if="error" class="editor-error"><AlertTriangle :size="16" />{{ error }}</p>

    <section v-if="importOpen" class="timeline-import-panel">
      <header>
        <div><p class="eyebrow">M-SPEC REFERENCE IMPORT</p><h2>导入外部时间轴候选</h2></div>
        <span class="status-badge warning">不会自动保存或发布</span>
      </header>
      <div class="timeline-import-form">
        <label>M-Spec 时间轴 URL
          <input v-model.trim="importUrl" type="url" placeholder="https://raalm.com/m-spec/timelinev2.html?boss=...&spec=..." />
        </label>
        <label class="import-checkbox"><input v-model="includeRecommendations" type="checkbox" />匿名聚合减伤使用窗口（会额外读取公开样本）</label>
        <button class="primary-button" type="button" :disabled="busy || !importUrl" @click="previewMSpecImport"><FileDown :size="16" />获取候选</button>
      </div>
      <div v-if="importCandidate" class="timeline-import-preview">
        <div class="import-stats">
          <span><b>{{ importCandidate.stats.phaseCount }}</b>阶段</span>
          <span><b>{{ importCandidate.stats.mechanicCount }}</b>机制</span>
          <span><b>{{ importCandidate.stats.actionIdCount }}</b>Action 锚点</span>
          <span><b>{{ importCandidate.stats.recommendationCount }}</b>匿名窗口</span>
        </div>
        <p v-for="warning in importCandidate.warnings" :key="warning" class="import-warning"><AlertTriangle :size="14" />{{ warning }}</p>
        <div v-if="importCandidate.recommendations.length" class="recommendation-preview">
          <header><b>减伤窗口预览</b><small>显示前 12 项；相对各样本阶段起点</small></header>
          <div v-for="window in importCandidate.recommendations.slice(0, 12)" :key="`${window.spellId}-${window.phase}-${window.occurrence}`">
            <b>{{ window.spellName }}</b>
            <span>{{ window.phase }} 第 {{ window.occurrence }} 次</span>
            <time>{{ formatTime(window.medianPhaseTimeMs) }}</time>
            <small>{{ formatTime(window.p25PhaseTimeMs) }}–{{ formatTime(window.p75PhaseTimeMs) }} · n={{ window.sampleCount }}</small>
          </div>
        </div>
        <div class="candidate-actions">
          <button class="primary-button" type="button" @click="applyMSpecImport">应用候选并替换当前时间轴</button>
          <button class="secondary-button" type="button" @click="importCandidate = null">放弃候选</button>
          <small>应用会清空 {{ snapshot.assignments.length }} 个旧任务和 {{ snapshot.anchors.length }} 个旧锚点。</small>
        </div>
      </div>
    </section>

    <div class="editor-workspace">
      <aside class="mechanic-panel">
        <header><div><p class="eyebrow">TIMELINE</p><h2>{{ timelineTitle }}</h2></div><span>{{ mechanics.length }} 项 · {{ snapshot.assignments.length }} 个减伤安排</span></header>
        <button
          v-for="mechanic in mechanics"
          :key="mechanic.mechanicId"
          :class="['mechanic-item', { active: selectedMechanicId === mechanic.mechanicId }]"
          type="button"
          @click="selectedMechanicId = mechanic.mechanicId"
        >
          <time>{{ formatTime(mechanic.plannedAtMs) }}</time>
          <span><b>{{ mechanic.name }}</b><small>{{ damageTypeLabel(mechanic.damageType) }} · {{ mechanic.target }}</small></span>
          <em v-if="assignmentCountByMechanic.get(mechanic.mechanicId)">{{ assignmentCountByMechanic.get(mechanic.mechanicId) }}</em>
          <i :class="mechanic.confidence.toLowerCase()"></i>
        </button>
        <div class="timeline-legend"><span><i class="reviewed"></i>已复核</span><span><i class="unverified"></i>待实证</span></div>
      </aside>

      <section class="assignment-board">
        <header class="board-header">
          <div>
            <p class="eyebrow">{{ selectedMechanic.phase }} · {{ formatTime(selectedMechanic.plannedAtMs) }}</p>
            <h2>{{ selectedMechanic.name }}</h2>
            <p>{{ damageTypeLabel(selectedMechanic.damageType) }}伤害 · {{ selectedMechanic.target }} · 命中 {{ formatTime(selectedMechanic.plannedAtMs) }}</p>
          </div>
          <span class="confidence-chip">{{ selectedMechanic.confidence }}</span>
        </header>

        <div class="quick-assign">
          <label>执行轨道
            <select v-model="selectedTrackId">
              <option v-for="track in snapshot.tracks" :key="track.trackId" :value="track.trackId">{{ track.slot }} · {{ track.displayName }}</option>
            </select>
          </label>
          <label>减伤技能
            <select v-model.number="selectedAbilityId">
              <option v-for="ability in abilities" :key="ability.actionId" :value="ability.actionId">{{ ability.name }} · {{ ability.durationMs / 1000 }}s</option>
            </select>
          </label>
          <label>单体目标（可选）
            <select v-model="selectedTargetTrackId">
              <option value="">无</option>
              <option v-for="track in snapshot.tracks" :key="track.trackId" :value="track.trackId">{{ track.slot }} · {{ track.displayName }}</option>
            </select>
          </label>
          <button class="primary-button" type="button" @click="addAssignment"><Plus :size="16" />安排技能</button>
        </div>

        <div class="track-grid">
          <article v-for="track in snapshot.tracks" :key="track.trackId" class="track-column">
            <header><span>{{ track.slot }}</span><div><b>{{ track.displayName }}</b><small>{{ track.allowedJobIds.length ? `${track.allowedJobIds.length} 个职业` : '未限定职业' }}</small></div></header>
            <button
              v-for="assignment in assignmentsForMechanic.filter((item) => item.trackId === track.trackId)"
              :key="assignment.assignmentId"
              :class="['assignment-card', { selected: selectedAssignment?.assignmentId === assignment.assignmentId }]"
              type="button"
              @click="selectedAssignment = assignment"
            >
              <span class="ability-dot"></span>
              <div><b>{{ abilityMap.get(assignment.actionId)?.name ?? `Action ${assignment.actionId}` }}</b><small>{{ formatTime(assignment.earliestUseAtMs) }}–{{ formatTime(assignment.latestUseAtMs) }}</small></div>
              <Lock v-if="assignment.locked" :size="13" />
            </button>
            <p v-if="!assignmentsForMechanic.some((item) => item.trackId === track.trackId)" class="track-empty">未安排</p>
          </article>
        </div>

        <div v-if="validation" :class="['validation-panel', { valid: validation.valid }]">
          <header>
            <CheckCircle2 v-if="validation.valid" :size="18" />
            <AlertTriangle v-else :size="18" />
            <b>{{ validation.valid ? '规则校验通过' : `${validation.issues.length} 个规则问题` }}</b>
          </header>
          <p v-for="issue in validation.issues" :key="`${issue.code}-${issue.reference}`"><span>{{ issue.code }}</span>{{ issue.message }}</p>
        </div>

        <div v-if="aiCandidate" class="validation-panel ai-candidate-panel">
          <header><Sparkles :size="18" /><b>AI 候选 · {{ aiCandidate.confidence }} · +{{ aiDiff.added }} / -{{ aiDiff.removed }} / ~{{ aiDiff.changed }}</b></header>
          <p v-for="reason in aiCandidate.reasons" :key="reason"><span>原因</span>{{ reason }}</p>
          <p v-for="warning in aiCandidate.warnings" :key="warning"><span>提醒</span>{{ warning }}</p>
          <div class="candidate-actions">
            <button class="primary-button" type="button" @click="applyAiCandidate">应用到草稿</button>
            <button class="secondary-button" type="button" @click="aiCandidate = null">拒绝候选</button>
          </div>
        </div>
      </section>

      <aside class="inspector-panel">
        <template v-if="selectedAssignment">
          <header><div><p class="eyebrow">ASSIGNMENT</p><h2>任务窗口</h2></div><button class="icon-button danger" type="button" title="删除任务" @click="removeAssignment(selectedAssignment.assignmentId)"><Trash2 :size="16" /></button></header>
          <div class="inspector-ability"><Sparkles :size="20" /><span><b>{{ abilityMap.get(selectedAssignment.actionId)?.name }}</b><small>Action {{ selectedAssignment.actionId }}</small></span></div>
          <label>开始高亮（毫秒）<input v-model.number="selectedAssignment.highlightAtMs" type="number" step="100" /></label>
          <label>允许起点（毫秒）<input v-model.number="selectedAssignment.earliestUseAtMs" type="number" step="100" /></label>
          <label>允许终点（毫秒）<input v-model.number="selectedAssignment.latestUseAtMs" type="number" step="100" /></label>
          <label>机制命中（毫秒）<input v-model.number="selectedAssignment.impactAtMs" type="number" step="100" /></label>
          <label>单体目标轨道
            <select v-model="selectedAssignment.targetTrackId">
              <option :value="null">无</option>
              <option v-for="track in snapshot.tracks" :key="track.trackId" :value="track.trackId">{{ track.slot }} · {{ track.displayName }}</option>
            </select>
          </label>
          <button class="lock-toggle" type="button" @click="selectedAssignment.locked = !selectedAssignment.locked">
            <Lock v-if="selectedAssignment.locked" :size="16" /><LockOpen v-else :size="16" />
            <span><b>{{ selectedAssignment.locked ? '已锁定' : '允许优化' }}</b><small>AI 不会移动锁定项</small></span>
          </button>
        </template>
        <div v-else class="inspector-empty"><Shield :size="28" /><h3>选择一个任务</h3><p>在此调整时间窗口、确认方式和锁定状态。</p></div>
      </aside>
    </div>
  </section>
</template>
