<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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
import { actionIconUrl } from '../lib/actionIcons'
import { attackClass, attackClassLabel, damageEstimateLabel, damageTypeLabel, hasDirectDamage } from '../lib/combatPresentation'
import { abilityEffectSummary } from '../lib/abilityEffects'
import {
  ABILITY_CATEGORY_LABELS,
  ABILITY_CATEGORY_ORDER,
  abilityPlanningCategory,
  abilityPlanningCategoryLabel,
  type AbilityPlanningCategory,
} from '../lib/abilityCategories'
import {
  abilityFitsTrack,
  assignmentsCoveringMechanic,
  localCooldownConflicts,
  previewDamageEstimatesLocally,
  type AssignmentCoverage,
} from '../lib/damageEstimates'
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
  DamageEstimate,
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
  return plan.mechanics.find((mechanic) => mechanic.damageProfile && assignedMechanics.has(mechanic.mechanicId))?.mechanicId
    ?? plan.mechanics.find((mechanic) => mechanic.damageProfile)?.mechanicId
    ?? plan.mechanics.find((mechanic) => assignedMechanics.has(mechanic.mechanicId))?.mechanicId
    ?? plan.mechanics[0]?.mechanicId
    ?? ''
}

const selectedMechanicId = ref(firstAssignedMechanicId(defaultPlan))
const selectedTrackId = ref('')
const selectedTargetTrackId = ref('')
const selectedAbilityId = ref<number | null>(null)
const showAllAbilities = ref(false)
const validation = ref<RuleValidationResult | null>(null)
const aiCandidate = ref<AiCandidate | null>(null)
const aiOpen = ref(false)
const aiInstruction = ref('')
const busy = ref(false)
const message = ref('')
const error = ref('')
const importOpen = ref(false)
const importUrl = ref('https://raalm.com/m-spec/timelinev2.html?boss=dancing-mad&spec=sage-sage&buddy=0')
const includeRecommendations = ref(true)
const importCandidate = ref<TimelineImportCandidate | null>(null)
const damageEstimates = ref<Record<string, DamageEstimate>>({})
const damageEstimateBusy = ref(false)
const damageEstimateError = ref('')
const timelinePage = ref(0)
let damageEstimateRequest = 0
let damageEstimateTimer: ReturnType<typeof setTimeout> | undefined
const TIMELINE_PAGE_SIZE = 12

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
const timelinePageCount = computed(() => Math.max(1, Math.ceil(mechanics.value.length / TIMELINE_PAGE_SIZE)))
const selectedMechanicIndex = computed(() => Math.max(0, mechanics.value.findIndex((item) => item.mechanicId === selectedMechanicId.value)))
const pagedMechanics = computed(() => {
  const start = timelinePage.value * TIMELINE_PAGE_SIZE
  return mechanics.value.slice(start, start + TIMELINE_PAGE_SIZE)
})
const timelinePageAssignmentCount = computed(() =>
  pagedMechanics.value.reduce((total, mechanic) => total + (assignmentCountByMechanic.value.get(mechanic.mechanicId) ?? 0), 0),
)
const timelinePageRangeLabel = computed(() => {
  const first = pagedMechanics.value[0]
  const last = pagedMechanics.value[pagedMechanics.value.length - 1]
  if (!first || !last) return '暂无机制'
  return `${formatTime(first.plannedAtMs)}–${formatTime(last.plannedAtMs)}`
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
const selectedTrack = computed(() => snapshot.value.tracks.find((track) => track.trackId === selectedTrackId.value) ?? null)
const filteredAbilities = computed(() => {
  if (showAllAbilities.value) return abilities.value
  return abilities.value.filter((ability) => abilityFitsTrack(ability, selectedTrack.value))
})
const groupedFilteredAbilities = computed(() => {
  const groups = new Map<AbilityPlanningCategory, AbilityDefinition[]>()
  for (const ability of filteredAbilities.value) {
    const category = abilityPlanningCategory(ability)
    groups.set(category, [...(groups.get(category) ?? []), ability])
  }
  return ABILITY_CATEGORY_ORDER
    .map((category) => ({
      category,
      label: ABILITY_CATEGORY_LABELS[category],
      abilities: groups.get(category) ?? [],
    }))
    .filter((group) => group.abilities.length > 0)
})
const abilityFilterSummary = computed(() => {
  if (showAllAbilities.value) return `显示全部 ${abilities.value.length} 个技能`
  const slot = selectedTrack.value?.slot ?? '当前轨道'
  const categorySummary = groupedFilteredAbilities.value
    .map((group) => `${group.label} ${group.abilities.length}`)
    .join('，')
  return `${slot} 可用 ${filteredAbilities.value.length} / ${abilities.value.length} 个技能${categorySummary ? ` · ${categorySummary}` : ''}`
})
const selectedAbility = computed(() => selectedAbilityId.value === null ? undefined : abilityMap.value.get(selectedAbilityId.value))
const selectedDamageEstimate = computed(() => damageEstimates.value[selectedMechanicId.value])
const selectedCoverage = computed(() => assignmentsCoveringMechanic(snapshot.value, abilities.value, selectedMechanic.value))
const carriedCoverage = computed(() => selectedCoverage.value.filter((coverage) => coverage.carriedFromAnotherMechanic))
const cooldownConflicts = computed(() => localCooldownConflicts(snapshot.value, abilities.value))
const cooldownIssueByAssignmentId = computed(() => new Set(cooldownConflicts.value.map((issue) => issue.assignmentId)))
const directDamageMechanicCount = computed(() => mechanics.value.filter((mechanic) => hasDirectDamage(mechanic)).length)
const unassignedDamageMechanicCount = computed(() => mechanics.value
  .filter((mechanic) => hasDirectDamage(mechanic) && !assignmentCountByMechanic.value.has(mechanic.mechanicId)).length)
const timelineScopeSummary = computed(() => {
  const phases = snapshot.value.phases.map((phase) => phase.name).filter(Boolean).join('/')
  return `${phases || '未分阶段'} · ${mechanics.value.length} 项机制 · ${directDamageMechanicCount.value} 项直接伤害`
})
const selectedDamageRatio = computed(() => damageRatio(selectedDamageEstimate.value))
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
  await refreshDamageEstimates()
})

function ensureSelectedAbilityVisible() {
  if (selectedAbilityId.value !== null && filteredAbilities.value.some((ability) => ability.actionId === selectedAbilityId.value)) {
    return
  }
  selectedAbilityId.value = filteredAbilities.value[0]?.actionId ?? null
}

async function loadPlan() {
  busy.value = true
  try {
    const details = await api.plan(planId.value)
    name.value = details.plan.name
    snapshot.value = withDefaultDamageProfiles({
      ...details.snapshot,
      phases: details.snapshot.phases?.length ? details.snapshot.phases : cloneData(DEFAULT_PHASES),
      mechanics: details.snapshot.mechanics?.length ? details.snapshot.mechanics : cloneData(DEFAULT_MECHANICS),
    })
    selectedMechanicId.value = firstAssignedMechanicId(snapshot.value)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '计划加载失败'
  } finally {
    busy.value = false
  }
}

function withDefaultDamageProfiles(plan: PlanSnapshot): PlanSnapshot {
  if (!plan.strategyTag.startsWith(DMU_P1_P2_STRATEGY)) return plan
  const defaultsById = new Map(DEFAULT_MECHANICS.map((mechanic) => [mechanic.mechanicId, mechanic]))
  return {
    ...plan,
    mechanics: plan.mechanics.map((mechanic) => {
      const fallback = defaultsById.get(mechanic.mechanicId)
      if (mechanic.damageProfile || !fallback?.damageProfile) return mechanic
      return {
        ...mechanic,
        actionId: mechanic.actionId ?? fallback.actionId,
        damageType: mechanic.damageType === 'UNKNOWN' ? fallback.damageType : mechanic.damageType,
        damageProfile: cloneData(fallback.damageProfile),
      }
    }),
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

function selectMechanic(mechanicId: string) {
  selectedMechanicId.value = mechanicId
  selectedAssignment.value = null
}

function setTimelinePage(page: number) {
  timelinePage.value = Math.min(Math.max(page, 0), timelinePageCount.value - 1)
  const firstMechanic = pagedMechanics.value[0]
  if (firstMechanic && !pagedMechanics.value.some((mechanic) => mechanic.mechanicId === selectedMechanicId.value)) {
    selectMechanic(firstMechanic.mechanicId)
  }
}

function syncTimelinePageToSelection() {
  if (!mechanics.value.length) {
    timelinePage.value = 0
    return
  }
  timelinePage.value = Math.min(
    Math.floor(selectedMechanicIndex.value / TIMELINE_PAGE_SIZE),
    timelinePageCount.value - 1,
  )
}

function displayInteger(value: number | null): string {
  return value === null ? '—' : Math.round(value).toLocaleString('zh-CN')
}

function damageRiskLabel(estimate: DamageEstimate | undefined, mechanic: TimelineMechanic = selectedMechanic.value): string {
  if (!hasDirectDamage(mechanic)) return '无直接伤害'
  if (!estimate || estimate.status === 'CALIBRATION_REQUIRED') return '伤害值待校准'
  if (estimate.status === 'SPECIAL_CASE_REVIEW_REQUIRED') return '需要无敌特判'
  return ({ GREEN: '绿色区间', YELLOW: '黄色区间', RED: '红色区间', UNCLASSIFIED: '未设色带', CALIBRATION_REQUIRED: '伤害值待校准' } as const)[estimate.riskLevel]
}

function damageRiskClass(estimate: DamageEstimate | undefined, mechanic: TimelineMechanic = selectedMechanic.value): string {
  if (!hasDirectDamage(mechanic)) return 'damage-risk-unclassified'
  return estimate ? `damage-risk-${estimate.riskLevel.toLowerCase()}` : 'damage-risk-calibration_required'
}

function damageRatio(estimate: DamageEstimate | undefined) {
  const baseline = estimate?.baselineDamage ?? 0
  const after = estimate?.damageAfterMitigation
  if (!baseline || after === null || after === undefined) return null
  const remaining = Math.max(0, after / baseline)
  return {
    mitigated: Math.max(0, 1 - Math.min(remaining, 1)),
    remaining,
    remainingBar: Math.min(remaining, 1),
  }
}

function percentLabel(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}

async function refreshDamageEstimates() {
  const requestId = ++damageEstimateRequest
  damageEstimateBusy.value = true
  damageEstimateError.value = ''
  try {
    const estimates = await api.previewDamageEstimates(snapshot.value)
    if (requestId !== damageEstimateRequest) return
    damageEstimates.value = Object.fromEntries(estimates.map((estimate) => [estimate.mechanicId, estimate]))
  } catch (reason) {
    if (requestId !== damageEstimateRequest) return
    const message = reason instanceof ApiError ? reason.message : '预计伤害计算失败'
    const estimates = previewDamageEstimatesLocally(snapshot.value, abilities.value, `服务端预计伤害请求失败，已使用本地参考计算：${message}`)
    damageEstimates.value = Object.fromEntries(estimates.map((estimate) => [estimate.mechanicId, estimate]))
    damageEstimateError.value = `服务端预计伤害请求失败，已使用本地参考计算：${message}`
  } finally {
    if (requestId === damageEstimateRequest) damageEstimateBusy.value = false
  }
}

function scheduleDamageEstimateRefresh() {
  if (damageEstimateTimer) clearTimeout(damageEstimateTimer)
  damageEstimateTimer = setTimeout(refreshDamageEstimates, 250)
}

watch(
  () => [snapshot.value.mechanics, snapshot.value.assignments],
  scheduleDamageEstimateRefresh,
  { deep: true },
)
watch([selectedTrackId, showAllAbilities, abilities], ensureSelectedAbilityVisible, { deep: true })
watch([selectedMechanicId, mechanics], syncTimelinePageToSelection, { deep: true, immediate: true })

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
      const remapTrackId = (trackId: string | null | undefined) => {
        if (!trackId) return null
        return createdTrackBySlot.get(localSlotByTrackId.get(trackId)!) ?? trackId
      }
      snapshot.value.assignments = snapshot.value.assignments.map((assignment) => ({
        ...assignment,
        trackId: remapTrackId(assignment.trackId) ?? assignment.trackId,
        targetTrackId: remapTrackId(assignment.targetTrackId),
        fallbacks: assignment.fallbacks.map((fallback) => ({
          ...fallback,
          trackId: remapTrackId(fallback.trackId) ?? fallback.trackId,
        })),
      }))
      selectedTrackId.value = remapTrackId(selectedTrackId.value) ?? ''
      selectedTargetTrackId.value = remapTrackId(selectedTargetTrackId.value) ?? ''
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
  error.value = ''
  try {
    aiCandidate.value = await api.generateAiCandidate(planId.value, aiInstruction.value)
    message.value = `已生成 ${aiCandidate.value.confidence} 候选，尚未应用`
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : 'AI 候选生成失败'
  } finally {
    busy.value = false
  }
}

function openFullTimelineImport() {
  importOpen.value = true
  importUrl.value = 'https://raalm.com/m-spec/timelinev2.html?boss=dancing-mad&spec=sage-sage&buddy=0'
  message.value = '已打开 DMU 完整机制候选导入；获取后可先只加载机制，不预制减伤'
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

function hideBrokenIcon(event: Event) {
  ;(event.currentTarget as HTMLImageElement).hidden = true
}

function seconds(milliseconds: number | undefined): string {
  if (!milliseconds) return '0s'
  return `${Math.round(milliseconds / 1000)}s`
}

function abilityOptionLabel(ability: AbilityDefinition): string {
  return `${ability.name} · 持续 ${seconds(ability.durationMs)} · CD ${seconds(ability.cooldownMs)}`
}

function coverageSourceLabel(coverage: AssignmentCoverage): string {
  const sourceMechanic = coverage.sourceMechanic
  const source = sourceMechanic ? `${formatTime(sourceMechanic.plannedAtMs)} ${sourceMechanic.name}` : '未知来源机制'
  const track = coverage.sourceTrack?.slot ?? '未知轨道'
  const until = coverage.coversUntilMs === null ? '持续时间未知' : `保守覆盖至 ${formatTime(coverage.coversUntilMs)}`
  return `${track} · ${source} · ${until}`
}

function fallbackAbilities(): AbilityDefinition[] {
  return [
    { actionId: 7535, name: '雪仇 / Reprisal', iconPath: 'ui/icon/000000/000806.tex', jobIds: [19, 21, 32, 37], cooldownMs: 60_000, maxCharges: 1, durationMs: 15_000, confirmationStrategy: 'STATUS_APPLY', source: 'Local fallback', confidence: 'REVIEWED', effect: { scope: 'ENEMY_AREA', allDamageReductionPercent: 10, physicalDamageReductionPercent: 0, magicalDamageReductionPercent: 0, maximumHpIncreasePercent: 0, maximumHpBarrierPercent: 0, barrierCurePotency: 0, invulnerability: false, stackingGroup: '', calculationReadiness: 'DIRECT_REDUCTION', conditions: [], source: 'Local fallback', confidence: 'REVIEWED' } },
    { actionId: 24298, name: 'Kerachole', iconPath: 'ui/icon/003000/003666.tex', jobIds: [40], cooldownMs: 30_000, maxCharges: 1, durationMs: 15_000, confirmationStrategy: 'STATUS_APPLY', source: 'Local fallback', confidence: 'UNVERIFIED', effect: { scope: 'PARTY', allDamageReductionPercent: 10, physicalDamageReductionPercent: 0, magicalDamageReductionPercent: 0, maximumHpIncreasePercent: 0, maximumHpBarrierPercent: 0, barrierCurePotency: 0, invulnerability: false, stackingGroup: 'SGE_KERACHOLE_TAUROCHOLE', calculationReadiness: 'DIRECT_REDUCTION', conditions: [], source: 'Local fallback', confidence: 'REVIEWED' } },
    { actionId: 24310, name: 'Holos', iconPath: 'ui/icon/003000/003678.tex', jobIds: [40], cooldownMs: 120_000, maxCharges: 1, durationMs: 20_000, confirmationStrategy: 'STATUS_APPLY', source: 'Local fallback', confidence: 'UNVERIFIED', effect: { scope: 'PARTY', allDamageReductionPercent: 10, physicalDamageReductionPercent: 0, magicalDamageReductionPercent: 0, maximumHpIncreasePercent: 0, maximumHpBarrierPercent: 0, barrierCurePotency: 300, invulnerability: false, stackingGroup: '', calculationReadiness: 'REQUIRES_HEALING_STATS', conditions: [], source: 'Local fallback', confidence: 'REVIEWED' } },
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
        <button class="secondary-button" type="button" :disabled="busy" @click="aiOpen = !aiOpen"><Sparkles :size="16" />AI 候选</button>
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
    <div class="timeline-scope-strip">
      <span>{{ timelineScopeSummary }}</span>
      <span>{{ unassignedDamageMechanicCount }} 项直接伤害暂未安排减伤</span>
      <button class="secondary-button compact" type="button" :disabled="busy" @click="openFullTimelineImport">
        <FileDown :size="14" />加载完整机制候选
      </button>
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

    <section v-if="aiOpen" class="ai-request-panel">
      <header>
        <div><p class="eyebrow">AI CANDIDATE</p><h2>生成可审阅的减伤候选</h2></div>
        <span class="status-badge warning">不会自动保存或发布</span>
      </header>
      <label>调整要求
        <textarea
          v-model.trim="aiInstruction"
          maxlength="2000"
          placeholder="例如：优先减少 H2 压力，锁定项不动；避免同一轨道连续两个 90 秒技能冲突。"
        />
      </label>
      <div class="candidate-actions">
        <button class="primary-button" type="button" :disabled="busy" @click="generateAiCandidate"><Sparkles :size="16" />生成候选</button>
        <small>服务端需要配置 VEDAAXIS_AI_API_KEY。返回结果只会作为候选，经规则校验后由你手动应用。</small>
      </div>
    </section>

    <div class="editor-workspace">
      <aside class="mechanic-panel">
        <header><div><p class="eyebrow">TIMELINE</p><h2>{{ timelineTitle }}</h2></div><span>{{ mechanics.length }} 项 · {{ snapshot.assignments.length }} 个减伤安排</span></header>
        <div class="timeline-pager">
          <button class="secondary-button compact" type="button" :disabled="timelinePage === 0" @click="setTimelinePage(timelinePage - 1)">上一页</button>
          <span>第 {{ timelinePage + 1 }} / {{ timelinePageCount }} 页</span>
          <button class="secondary-button compact" type="button" :disabled="timelinePage + 1 >= timelinePageCount" @click="setTimelinePage(timelinePage + 1)">下一页</button>
          <small>{{ timelinePageRangeLabel }} · 本页 {{ timelinePageAssignmentCount }} 个安排</small>
        </div>
        <button
          v-for="mechanic in pagedMechanics"
          :key="mechanic.mechanicId"
          :class="['mechanic-item', { active: selectedMechanicId === mechanic.mechanicId }]"
          type="button"
          @click="selectMechanic(mechanic.mechanicId)"
        >
          <time>{{ formatTime(mechanic.plannedAtMs) }}</time>
          <span>
            <b>{{ mechanic.name }}</b>
            <small class="mechanic-classification">
              <strong :class="['attack-class-chip', `attack-class-${attackClass(mechanic).toLowerCase()}`]">{{ attackClassLabel(mechanic) }}</strong>
              <span>{{ damageTypeLabel(mechanic.damageType) }}</span>
              <span>{{ mechanic.target }}</span>
            </small>
            <small class="damage-estimate-note">{{ damageEstimateLabel(mechanic) }}</small>
            <small
              v-if="damageEstimates[mechanic.mechanicId]?.damageAfterMitigation != null"
              :class="['post-mitigation-damage', damageRiskClass(damageEstimates[mechanic.mechanicId], mechanic)]"
            >
              减伤后 {{ displayInteger(damageEstimates[mechanic.mechanicId]!.damageAfterMitigation) }}
              <template v-if="damageEstimates[mechanic.mechanicId]?.worstTrackSlot"> · 最危险 {{ damageEstimates[mechanic.mechanicId]!.worstTrackSlot }}</template>
            </small>
          </span>
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
            <p>
              <strong :class="['attack-class-chip', `attack-class-${attackClass(selectedMechanic).toLowerCase()}`]">{{ attackClassLabel(selectedMechanic) }}</strong>
              {{ damageTypeLabel(selectedMechanic.damageType) }}伤害 · {{ selectedMechanic.target }} · 命中 {{ formatTime(selectedMechanic.plannedAtMs) }} · {{ damageEstimateLabel(selectedMechanic) }}
            </p>
          </div>
          <span class="confidence-chip">{{ selectedMechanic.confidence }}</span>
        </header>

        <div class="quick-assign">
          <label>执行轨道
            <select v-model="selectedTrackId">
              <option v-for="track in snapshot.tracks" :key="track.trackId" :value="track.trackId">{{ track.slot }} · {{ track.displayName }}</option>
            </select>
          </label>
          <label class="ability-picker">减伤技能
            <span class="ability-select">
              <img
                v-if="actionIconUrl(selectedAbility)"
                class="action-icon action-icon-select"
                :src="actionIconUrl(selectedAbility)!"
                alt=""
                decoding="async"
                referrerpolicy="no-referrer"
                @error="hideBrokenIcon"
              />
              <select v-model.number="selectedAbilityId">
                <optgroup
                  v-for="group in groupedFilteredAbilities"
                  :key="group.category"
                  :label="`${group.label}（${group.abilities.length}）`"
                >
                  <option v-for="ability in group.abilities" :key="ability.actionId" :value="ability.actionId">
                    {{ abilityOptionLabel(ability) }}
                  </option>
                </optgroup>
              </select>
            </span>
            <small class="ability-filter-note">{{ abilityFilterSummary }}</small>
            <small v-if="selectedAbility" class="ability-category-note">{{ abilityPlanningCategoryLabel(selectedAbility) }} · {{ abilityEffectSummary(selectedAbility) }}</small>
          </label>
          <label>单体目标（可选）
            <select v-model="selectedTargetTrackId">
              <option value="">无</option>
              <option v-for="track in snapshot.tracks" :key="track.trackId" :value="track.trackId">{{ track.slot }} · {{ track.displayName }}</option>
            </select>
          </label>
          <label class="show-all-abilities"><input v-model="showAllAbilities" type="checkbox" />显示全部技能</label>
          <button class="primary-button" type="button" :disabled="!selectedAbilityId" @click="addAssignment"><Plus :size="16" />安排技能</button>
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
              <img
                v-if="actionIconUrl(abilityMap.get(assignment.actionId))"
                class="action-icon action-icon-card"
                :src="actionIconUrl(abilityMap.get(assignment.actionId))!"
                alt=""
                loading="lazy"
                decoding="async"
                referrerpolicy="no-referrer"
                @error="hideBrokenIcon"
              />
              <div>
                <b>{{ abilityMap.get(assignment.actionId)?.name ?? `Action ${assignment.actionId}` }}</b>
                <small>施放 {{ formatTime(assignment.earliestUseAtMs) }}–{{ formatTime(assignment.latestUseAtMs) }} · 判定 {{ formatTime(assignment.impactAtMs) }}</small>
                <small v-if="abilityMap.get(assignment.actionId)">持续 {{ seconds(abilityMap.get(assignment.actionId)?.durationMs) }} · CD {{ seconds(abilityMap.get(assignment.actionId)?.cooldownMs) }}</small>
                <small v-if="abilityMap.get(assignment.actionId)" class="ability-category-note">{{ abilityPlanningCategoryLabel(abilityMap.get(assignment.actionId)) }}</small>
                <small class="ability-effect-summary">{{ abilityEffectSummary(abilityMap.get(assignment.actionId)) }}</small>
                <small v-if="cooldownIssueByAssignmentId.has(assignment.assignmentId)" class="cooldown-inline-warning">冷却冲突</small>
              </div>
              <Lock v-if="assignment.locked" :size="13" />
            </button>
            <p v-if="!assignmentsForMechanic.some((item) => item.trackId === track.trackId)" class="track-empty">未安排</p>
          </article>
        </div>

        <section class="damage-analysis-panel">
          <header>
            <div>
              <p class="eyebrow">POST-MITIGATION DAMAGE</p>
              <h3>当前减伤后预计伤害</h3>
            </div>
            <span :class="['damage-analysis-status', damageRiskClass(selectedDamageEstimate)]">
              {{ damageEstimateBusy ? '计算中…' : damageRiskLabel(selectedDamageEstimate, selectedMechanic) }}
            </span>
          </header>
          <div class="coverage-summary">
            <span><b>{{ assignmentsForMechanic.length }}</b>本机制安排</span>
            <span><b>{{ carriedCoverage.length }}</b>提前覆盖</span>
            <span><b>{{ cooldownConflicts.length }}</b>冷却冲突</span>
          </div>
          <p class="damage-thresholds">
            AOE：≤10万绿色，10万以上至19万黄色，&gt;19万红色；死刑：≤20万绿色，20万以上至29万前黄色，≥29万红色。
          </p>
          <div v-if="selectedDamageEstimate?.damageAfterMitigation != null" class="damage-analysis-metrics">
            <span><small>机制原始总伤害</small><b>{{ displayInteger(selectedDamageEstimate.baselineDamage) }}</b></span>
            <span><small>已建模减伤</small><b>{{ selectedDamageEstimate.modeledReduction === null ? '—' : `${(selectedDamageEstimate.modeledReduction * 100).toFixed(1)}%` }}</b></span>
            <span><small>减伤后预计伤害</small><b :class="damageRiskClass(selectedDamageEstimate)">{{ displayInteger(selectedDamageEstimate.damageAfterMitigation) }}</b></span>
            <span><small>最危险轨道</small><b>{{ selectedDamageEstimate.worstTrackSlot ?? '—' }}</b></span>
          </div>
          <div v-if="selectedDamageRatio" class="damage-ratio-panel">
            <div class="damage-ratio-bar" aria-label="减伤比例">
              <span class="damage-ratio-mitigated" :style="{ width: percentLabel(selectedDamageRatio.mitigated) }"></span>
              <span class="damage-ratio-remaining" :style="{ width: percentLabel(selectedDamageRatio.remainingBar) }"></span>
            </div>
            <div class="damage-ratio-labels">
              <span><i class="mitigated"></i>减掉 {{ percentLabel(selectedDamageRatio.mitigated) }}</span>
              <span><i class="remaining"></i>承受 {{ percentLabel(selectedDamageRatio.remaining) }}</span>
            </div>
          </div>
          <p v-if="selectedDamageEstimate?.damageAfterMitigation == null" class="damage-analysis-empty">
            {{ hasDirectDamage(selectedMechanic)
              ? '当前机制没有足够的 FFLogs 样本，暂不显示猜测伤害；可继续编排减伤，待校准后会自动出现结果。'
              : '该行用于阶段或机制定位，不对应一次需要计算承伤的直接伤害事件。' }}
          </p>
          <p class="damage-analysis-boundary">
            预览按当前安排计算：AOE 取全队中减伤后伤害最高的轨道，死刑取坦克轨道中的最高值。护盾、治疗和无敌不从这一个伤害数字中扣除，并会单独提示复核。
          </p>
          <div v-if="carriedCoverage.length" class="coverage-panel">
            <header><b>提前覆盖到本机制</b><small>这些技能不是本机制行创建的，但持续时间覆盖当前命中</small></header>
            <p v-for="coverage in carriedCoverage" :key="coverage.assignment.assignmentId">
              <span>{{ coverage.ability?.name ?? `Action ${coverage.assignment.actionId}` }}</span>
              {{ coverageSourceLabel(coverage) }}
            </p>
          </div>
          <div v-if="cooldownConflicts.length" class="cooldown-panel">
            <header><b>本地冷却预警</b><small>保存/发布时服务端也会校验</small></header>
            <p v-for="issue in cooldownConflicts.slice(0, 5)" :key="issue.assignmentId">
              <span>{{ issue.trackSlot }}</span>{{ issue.abilityName }} 最早要到 {{ formatTime(issue.availableAtMs) }}，当前窗口最晚 {{ formatTime(issue.latestUseAtMs) }}
            </p>
          </div>
          <p v-if="damageEstimateError" class="damage-analysis-error">
            <AlertTriangle :size="13" />{{ damageEstimateError }}
          </p>
          <div v-if="selectedDamageEstimate?.notices.length" class="damage-analysis-notices">
            <p v-for="notice in selectedDamageEstimate.notices" :key="notice"><AlertTriangle :size="13" />{{ notice }}</p>
          </div>
        </section>

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
          <div class="inspector-ability">
            <img
              v-if="actionIconUrl(abilityMap.get(selectedAssignment.actionId))"
              class="action-icon action-icon-inspector"
              :src="actionIconUrl(abilityMap.get(selectedAssignment.actionId))!"
              alt=""
              decoding="async"
              referrerpolicy="no-referrer"
              @error="hideBrokenIcon"
            />
            <span>
              <b>{{ abilityMap.get(selectedAssignment.actionId)?.name }}</b>
              <small>Action {{ selectedAssignment.actionId }}</small>
              <small class="ability-effect-summary">{{ abilityEffectSummary(abilityMap.get(selectedAssignment.actionId)) }}</small>
            </span>
          </div>
          <label>开始高亮（毫秒）<input v-model.number="selectedAssignment.highlightAtMs" type="number" step="100" /></label>
          <label>允许起点（毫秒）<input v-model.number="selectedAssignment.earliestUseAtMs" type="number" step="100" /></label>
          <label>允许终点（毫秒）<input v-model.number="selectedAssignment.latestUseAtMs" type="number" step="100" /></label>
          <label>机制命中（毫秒）<input v-model.number="selectedAssignment.impactAtMs" type="number" step="100" /></label>
          <p v-if="abilityMap.get(selectedAssignment.actionId)" class="inspector-hint">
            {{ abilityMap.get(selectedAssignment.actionId)?.name }}：持续 {{ seconds(abilityMap.get(selectedAssignment.actionId)?.durationMs) }}，
            冷却 {{ seconds(abilityMap.get(selectedAssignment.actionId)?.cooldownMs) }}。
            若提前释放且持续时间覆盖后续命中，后续机制会把它显示为“提前覆盖”。
          </p>
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
