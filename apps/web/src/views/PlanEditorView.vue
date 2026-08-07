<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AlertTriangle,
  CheckCircle2,
  ChevronLeft,
  CircleX,
  CircleDashed,
  Clock3,
  FileDown,
  Grid2X2,
  Grid3X3,
  Lock,
  LockOpen,
  Pencil,
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
  AiOptimizationMode,
  Assignment,
  DamageEstimate,
  PlanSnapshot,
  RuleIssue,
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
const abilityPickerOpen = ref(false)
const showTimelineMarkers = ref(false)
const healerHpReference = ref(180_000)
const tankHpReference = ref(250_000)
const validation = ref<RuleValidationResult | null>(null)
const aiCandidate = ref<AiCandidate | null>(null)
const aiOpen = ref(false)
const aiInstruction = ref('')
const aiOptimizationMode = ref<AiOptimizationMode>('GLOBAL')
const aiFocusTrackId = ref('')
const aiPreserveExistingAssignments = ref(true)
const aiAllowGcdActions = ref(false)
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
const assignmentEditorOpen = ref(false)
const assignmentEditorOriginal = ref<Assignment | null>(null)
const assignmentTimelineRef = ref<HTMLElement | null>(null)
const draggingAssignmentField = ref<AssignmentTimeField | null>(null)
let damageEstimateRequest = 0
let damageEstimateTimer: ReturnType<typeof setTimeout> | undefined
const TIMELINE_PAGE_SIZE = 12
const ASSIGNMENT_TIME_STEP_MS = 100
const READY_COOLDOWN: AbilityCooldownState = {
  blocked: false,
  remainingMs: 0,
  availableAtMs: null,
  label: '',
}

type AssignmentTimeField = 'highlightAtMs' | 'earliestUseAtMs' | 'latestUseAtMs' | 'impactAtMs'

interface AbilityCooldownState {
  blocked: boolean
  remainingMs: number
  availableAtMs: number | null
  label: string
}

interface AssignmentCoverageStatus {
  tone: 'green' | 'yellow' | 'red' | 'gray'
  title: string
  message: string
  farthestMechanic: TimelineMechanic | null
}

interface RuleIssueDisplay {
  issue: RuleIssue
  title: string
  location: string
  detail: string
  assignment: Assignment | null
  mechanic: TimelineMechanic | null
  track: PlanSnapshot['tracks'][number] | null
  ability: AbilityDefinition | null
}

const ASSIGNMENT_TIME_FIELDS: Array<{ key: AssignmentTimeField; label: string }> = [
  { key: 'highlightAtMs', label: '开始亮起' },
  { key: 'earliestUseAtMs', label: '最早释放' },
  { key: 'latestUseAtMs', label: '最晚释放' },
  { key: 'impactAtMs', label: '机制判定' },
]

const DEFAULT_PHASES: TimelinePhase[] = cloneData(defaultPlan.phases)
const DEFAULT_MECHANICS: TimelineMechanic[] = cloneData(defaultPlan.mechanics)
const snapshot = ref<PlanSnapshot>(makeSnapshot('EIGHT'))

const allMechanics = computed(() => snapshot.value.mechanics)
const mechanics = computed(() => showTimelineMarkers.value
  ? allMechanics.value
  : allMechanics.value.filter((mechanic) => hasDirectDamage(mechanic)))
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
const selectedMechanic = computed(() =>
  mechanics.value.find((item) => item.mechanicId === selectedMechanicId.value)
  ?? allMechanics.value.find((item) => item.mechanicId === selectedMechanicId.value)
  ?? mechanics.value[0]
  ?? DEFAULT_MECHANICS[0]!)
const assignmentsForMechanic = computed(() => snapshot.value.assignments.filter((item) => item.mechanicId === selectedMechanicId.value))
const abilityMap = computed(() => new Map(abilities.value.map((ability) => [ability.actionId, ability])))
const selectedTrack = computed(() => snapshot.value.tracks.find((track) => track.trackId === selectedTrackId.value) ?? null)
const aiFocusTrack = computed(() =>
  snapshot.value.tracks.find((track) => track.trackId === aiFocusTrackId.value)
  ?? selectedTrack.value
  ?? snapshot.value.tracks[0]
  ?? null)
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
const directDamageMechanicCount = computed(() => allMechanics.value.filter((mechanic) => hasDirectDamage(mechanic)).length)
const hiddenMarkerCount = computed(() => allMechanics.value.length - directDamageMechanicCount.value)
const unassignedDamageMechanicCount = computed(() => allMechanics.value
  .filter((mechanic) => hasDirectDamage(mechanic) && !assignmentCountByMechanic.value.has(mechanic.mechanicId)).length)
const timelineScopeSummary = computed(() => {
  const phases = snapshot.value.phases.map((phase) => phase.name).filter(Boolean).join('/')
  const markerSummary = showTimelineMarkers.value
    ? `${allMechanics.value.length} 项机制`
    : `${directDamageMechanicCount.value} 项直接伤害（隐藏 ${hiddenMarkerCount.value} 项标记）`
  return `${phases || '未分阶段'} · ${markerSummary}`
})
const selectedDamageHpAnalysis = computed(() => damageHpAnalysis(selectedDamageEstimate.value, selectedMechanic.value))
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
const validationErrorCount = computed(() => validation.value?.issues.filter((issue) => issue.severity === 'ERROR').length ?? 0)
const validationWarningCount = computed(() => validation.value?.issues.filter((issue) => issue.severity === 'WARNING').length ?? 0)
const validationIssueDisplays = computed(() => validation.value?.issues.map(describeRuleIssue) ?? [])
const selectedAssignment = ref<Assignment | null>(null)
const selectedAssignmentAbility = computed(() => selectedAssignment.value ? abilityMap.value.get(selectedAssignment.value.actionId) : undefined)
const selectedAssignmentMechanic = computed(() => selectedAssignment.value
  ? allMechanics.value.find((mechanic) => mechanic.mechanicId === selectedAssignment.value?.mechanicId) ?? null
  : null)
const selectedAssignmentTargetTrack = computed(() => selectedAssignment.value?.targetTrackId
  ? snapshot.value.tracks.find((track) => track.trackId === selectedAssignment.value?.targetTrackId) ?? null
  : null)
const abilityCooldownByActionId = computed(() => {
  const states = new Map<number, AbilityCooldownState>()
  for (const ability of filteredAbilities.value) {
    states.set(ability.actionId, candidateCooldownState(ability))
  }
  return states
})
const selectedAbilityCooldownState = computed(() => selectedAbility.value ? abilityCooldownState(selectedAbility.value) : READY_COOLDOWN)
const selectedAssignmentCoverageStatus = computed(() => selectedAssignment.value
  ? coverageStatusForAssignment(selectedAssignment.value)
  : null)
const selectedAssignmentTimelineStartMs = computed(() => {
  const assignment = selectedAssignment.value
  const ability = selectedAssignmentAbility.value
  if (!assignment) return 0
  const values = [
    assignment.highlightAtMs,
    assignment.earliestUseAtMs,
    assignment.latestUseAtMs,
    assignment.impactAtMs,
    ability && ability.durationMs > 0 ? assignment.earliestUseAtMs + ability.durationMs : assignment.impactAtMs,
  ].filter((value): value is number => Number.isFinite(value))
  return Math.max(0, Math.min(...values) - 10_000)
})
const selectedAssignmentTimelineEndMs = computed(() => {
  const assignment = selectedAssignment.value
  const ability = selectedAssignmentAbility.value
  if (!assignment) return 60_000
  const currentMechanic = selectedAssignmentMechanic.value
  const nextDamage = currentMechanic
    ? nextDirectDamageMechanics(currentMechanic).find((mechanic) => mechanic.plannedAtMs > currentMechanic.plannedAtMs)
    : null
  return Math.max(
    assignment.impactAtMs + 10_000,
    assignment.latestUseAtMs + 10_000,
    ability && ability.durationMs > 0 ? assignment.earliestUseAtMs + ability.durationMs + 6_000 : 0,
    nextDamage ? nextDamage.plannedAtMs + 6_000 : 0,
    selectedAssignmentTimelineStartMs.value + 20_000,
  )
})
const selectedAssignmentTimelineInputMaxMs = computed(() =>
  Math.max(
    selectedAssignmentTimelineEndMs.value,
    allMechanics.value[allMechanics.value.length - 1]?.plannedAtMs ?? 0,
  ) + 30_000)
const selectedAssignmentTimelineTicks = computed(() => allMechanics.value
  .filter((mechanic) => hasDirectDamage(mechanic))
  .filter((mechanic) => mechanic.plannedAtMs >= selectedAssignmentTimelineStartMs.value
    && mechanic.plannedAtMs <= selectedAssignmentTimelineEndMs.value))

onMounted(async () => {
  try {
    const loadedAbilities = await api.abilities()
    abilities.value = loadedAbilities.length ? loadedAbilities : fallbackAbilities()
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
  abilityPickerOpen.value = false
}

function ensureAiFocusTrackVisible() {
  if (!snapshot.value.tracks.length) {
    aiFocusTrackId.value = ''
    return
  }
  if (snapshot.value.tracks.some((track) => track.trackId === aiFocusTrackId.value)) return
  const selected = snapshot.value.tracks.find((track) => track.trackId === selectedTrackId.value)
  aiFocusTrackId.value = selected?.trackId ?? snapshot.value.tracks[0]!.trackId
}

function ensureSelectedMechanicVisible() {
  if (!mechanics.value.length) {
    selectedMechanicId.value = allMechanics.value[0]?.mechanicId ?? ''
    return
  }
  if (!mechanics.value.some((mechanic) => mechanic.mechanicId === selectedMechanicId.value)) {
    selectMechanic(mechanics.value[0]!.mechanicId)
  }
}

function selectAbility(ability: AbilityDefinition) {
  if (abilityCooldownState(ability).blocked) return
  selectedAbilityId.value = ability.actionId
  abilityPickerOpen.value = false
}

function defaultAssignmentWindow(mechanic: TimelineMechanic) {
  const impactAtMs = mechanic.plannedAtMs
  return {
    highlightAtMs: Math.max(0, impactAtMs - 12_000),
    earliestUseAtMs: Math.max(0, impactAtMs - 8_000),
    latestUseAtMs: Math.max(0, impactAtMs - 1_000),
    impactAtMs,
  }
}

function abilityCooldownState(ability: AbilityDefinition): AbilityCooldownState {
  return abilityCooldownByActionId.value.get(ability.actionId) ?? READY_COOLDOWN
}

function candidateCooldownState(ability: AbilityDefinition): AbilityCooldownState {
  if (!selectedTrackId.value) return READY_COOLDOWN
  const window = defaultAssignmentWindow(selectedMechanic.value)
  const recoveryMs = abilityRecoveryMs(ability)
  let nextAvailableAtMs = Number.NEGATIVE_INFINITY
  const previousAssignments = snapshot.value.assignments
    .filter((assignment) => assignment.trackId === selectedTrackId.value
      && assignment.actionId === ability.actionId
      && assignment.earliestUseAtMs <= window.latestUseAtMs)
    .sort((left, right) => left.earliestUseAtMs - right.earliestUseAtMs)

  for (const assignment of previousAssignments) {
    const scheduledAtMs = Math.max(assignment.earliestUseAtMs, nextAvailableAtMs)
    nextAvailableAtMs = scheduledAtMs + recoveryMs
  }

  const candidateAvailableAtMs = Math.max(window.earliestUseAtMs, nextAvailableAtMs)
  if (candidateAvailableAtMs <= window.latestUseAtMs) return READY_COOLDOWN
  const remainingMs = candidateAvailableAtMs - window.latestUseAtMs
  return {
    blocked: true,
    remainingMs,
    availableAtMs: candidateAvailableAtMs,
    label: compactDuration(remainingMs),
  }
}

function abilityRecoveryMs(ability: AbilityDefinition): number {
  return Math.max(1, ability.maxCharges) === 1
    ? ability.cooldownMs
    : ability.cooldownMs / Math.max(1, ability.maxCharges)
}

function coverageStatusForAssignment(assignment: Assignment): AssignmentCoverageStatus {
  const ability = abilityMap.value.get(assignment.actionId)
  const currentMechanic = allMechanics.value.find((mechanic) => mechanic.mechanicId === assignment.mechanicId)
  if (!ability || !currentMechanic || ability.durationMs <= 0) {
    return {
      tone: 'gray',
      title: '无法覆盖多轮',
      message: '该减伤无法覆盖多轮伤害。',
      farthestMechanic: null,
    }
  }

  if (!assignmentCoversMechanic(assignment, ability, currentMechanic)) {
    return {
      tone: 'red',
      title: '无法覆盖当前机制',
      message: '该减伤无法覆盖当前机制，请重新放置释放节点或放置在其他的技能上。',
      farthestMechanic: currentMechanic,
    }
  }

  const theoreticalFarthest = farthestCoverableMechanicFrom(currentMechanic, ability.durationMs, currentMechanic.plannedAtMs)
  if (!theoreticalFarthest) {
    return {
      tone: 'gray',
      title: '无法覆盖多轮',
      message: '该减伤无法覆盖多轮伤害。',
      farthestMechanic: null,
    }
  }

  const currentFarthest = farthestMechanicCoveredByAssignment(assignment, ability, currentMechanic)
  if (currentFarthest) {
    return {
      tone: 'green',
      title: '多轮覆盖有效',
      message: `该减伤能够覆盖多轮伤害（最远覆盖 ${mechanicInlineLabel(currentFarthest)}）。`,
      farthestMechanic: currentFarthest,
    }
  }

  return {
    tone: 'yellow',
    title: '存在多轮覆盖机会',
    message: `该减伤可以覆盖多轮（最远覆盖 ${mechanicInlineLabel(theoreticalFarthest)}），请谨慎选择。`,
    farthestMechanic: theoreticalFarthest,
  }
}

function nextDirectDamageMechanics(currentMechanic: TimelineMechanic): TimelineMechanic[] {
  return allMechanics.value
    .filter((mechanic) => hasDirectDamage(mechanic) && mechanic.plannedAtMs > currentMechanic.plannedAtMs)
    .sort((left, right) => left.plannedAtMs - right.plannedAtMs)
}

function farthestCoverableMechanicFrom(
  currentMechanic: TimelineMechanic,
  durationMs: number,
  releaseAtMs: number,
): TimelineMechanic | null {
  const coverUntilMs = releaseAtMs + durationMs
  return nextDirectDamageMechanics(currentMechanic)
    .filter((mechanic) => mechanic.plannedAtMs <= coverUntilMs)
    .at(-1) ?? null
}

function farthestMechanicCoveredByAssignment(
  assignment: Assignment,
  ability: AbilityDefinition,
  currentMechanic: TimelineMechanic,
): TimelineMechanic | null {
  return nextDirectDamageMechanics(currentMechanic)
    .filter((mechanic) => assignmentCoversMechanic(assignment, ability, mechanic))
    .at(-1) ?? null
}

function assignmentCoversMechanic(assignment: Assignment, ability: AbilityDefinition, mechanic: TimelineMechanic): boolean {
  if (ability.durationMs <= 0) return false
  if (assignment.earliestUseAtMs > mechanic.plannedAtMs || assignment.latestUseAtMs > mechanic.plannedAtMs) return false
  return assignment.earliestUseAtMs + ability.durationMs >= mechanic.plannedAtMs
}

function mechanicInlineLabel(mechanic: TimelineMechanic): string {
  return `${formatTime(mechanic.plannedAtMs)} ${mechanic.name}`
}

function describeRuleIssue(issue: RuleIssue): RuleIssueDisplay {
  const assignment = snapshot.value.assignments.find((item) => item.assignmentId === issue.reference) ?? null
  const mechanic = assignment
    ? allMechanics.value.find((item) => item.mechanicId === assignment.mechanicId) ?? null
    : allMechanics.value.find((item) => item.mechanicId === issue.reference) ?? null
  const track = assignment
    ? snapshot.value.tracks.find((item) => item.trackId === assignment.trackId) ?? null
    : snapshot.value.tracks.find((item) => item.trackId === issue.reference) ?? null
  const ability = assignment ? abilityMap.value.get(assignment.actionId) ?? null : null
  const location = mechanic
    ? mechanicInlineLabel(mechanic)
    : track ? trackDisplayLabel(track) : `引用 ${shortReference(issue.reference)}`
  const detailParts = [
    track ? trackDisplayLabel(track) : '',
    ability ? ability.name : '',
    assignment ? `窗口 ${formatTime(assignment.earliestUseAtMs)}–${formatTime(assignment.latestUseAtMs)}` : '',
  ].filter(Boolean)
  return {
    issue,
    title: ruleIssueTitle(issue),
    location,
    detail: detailParts.length ? detailParts.join(' · ') : issue.message,
    assignment,
    mechanic,
    track,
    ability,
  }
}

function ruleIssueTitle(issue: RuleIssue): string {
  const labels: Record<string, string> = {
    ABILITY_NOT_FOUND: '技能目录缺失',
    COOLDOWN_CONFLICT: '技能冷却冲突',
    COVERAGE_GAP: '持续时间覆盖不到机制',
    DUPLICATE_ANCHOR_ID: '时间轴锚点重复',
    DUPLICATE_ANCHOR_OCCURRENCE: '时间轴事件序号重复',
    DUPLICATE_ASSIGNMENT: '任务重复',
    DUPLICATE_MECHANIC_ID: '机制 ID 重复',
    DUPLICATE_TRACK_ID: '轨道 ID 重复',
    DUPLICATE_TRACK_SLOT: '轨道位置重复',
    HIGHLIGHT_AFTER_WINDOW: '亮起时间晚于释放窗口',
    INVALID_PHASE_DURATION: '阶段持续时间无效',
    INVALID_USE_WINDOW: '释放窗口无效',
    JOB_NOT_COMPATIBLE: '技能与轨道职业不兼容',
    PHASE_TIMING_MODE_REQUIRED: '阶段时间模式缺失',
    TRACK_MODE_MISMATCH: '轨道数量与模式不匹配',
    UNKNOWN_ANCHOR: '引用了不存在的锚点',
    UNKNOWN_MECHANIC: '引用了不存在的机制',
    UNKNOWN_TARGET_TRACK: '单体目标轨道不存在',
    UNKNOWN_TRACK: '执行轨道不存在',
    WINDOW_AFTER_IMPACT: '释放窗口晚于机制判定',
  }
  return labels[issue.code] ?? issue.code
}

function shortReference(reference: string): string {
  return reference.length > 12 ? `${reference.slice(0, 8)}…${reference.slice(-4)}` : reference
}

function focusRuleIssue(display: RuleIssueDisplay) {
  if (display.mechanic) {
    if (!mechanics.value.some((mechanic) => mechanic.mechanicId === display.mechanic?.mechanicId)) {
      showTimelineMarkers.value = true
    }
    selectedMechanicId.value = display.mechanic.mechanicId
    syncTimelinePageToSelection()
  }
  if (display.track) {
    selectedTrackId.value = display.track.trackId
  }
  if (display.assignment) {
    selectedAssignment.value = display.assignment
    selectedTargetTrackId.value = display.assignment.targetTrackId ?? ''
  }
}

function isRuleValidationResult(body: unknown): body is RuleValidationResult {
  if (!body || typeof body !== 'object') return false
  const candidate = body as Partial<RuleValidationResult>
  return typeof candidate.valid === 'boolean' && Array.isArray(candidate.issues)
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
  const hpAnalysis = damageHpAnalysis(estimate, mechanic)
  if (estimate.status === 'SPECIAL_CASE_REVIEW_REQUIRED') {
    return hpAnalysis ? `${hpAnalysis.label} · 无敌需复核` : '需要无敌特判'
  }
  if (hpAnalysis) return hpAnalysis.label
  return ({ GREEN: '绿色区间', YELLOW: '黄色区间', RED: '红色区间', UNCLASSIFIED: '未设色带', CALIBRATION_REQUIRED: '伤害值待校准' } as const)[estimate.riskLevel]
}

function damageRiskClass(estimate: DamageEstimate | undefined, mechanic: TimelineMechanic = selectedMechanic.value): string {
  if (!hasDirectDamage(mechanic)) return 'damage-risk-unclassified'
  const hpAnalysis = damageHpAnalysis(estimate, mechanic)
  if (hpAnalysis) return `damage-risk-${hpAnalysis.risk.toLowerCase()}`
  return estimate ? `damage-risk-${estimate.riskLevel.toLowerCase()}` : 'damage-risk-calibration_required'
}

function damageHpAnalysis(estimate: DamageEstimate | undefined, mechanic: TimelineMechanic) {
  const after = estimate?.damageAfterMitigation
  if (!hasDirectDamage(mechanic) || after === null || after === undefined) return null
  const referenceHp = hpReferenceForMechanic(mechanic)
  if (referenceHp <= 0) return null
  const damageRatio = Math.max(0, after / referenceHp)
  const remainingHp = referenceHp - after
  const remainingRatio = remainingHp / referenceHp
  const risk = damageRatio > 1 ? 'RED' : remainingRatio < 0.25 ? 'YELLOW' : 'GREEN'
  const label = risk === 'RED'
    ? '超过血量上限'
    : risk === 'YELLOW' ? '剩余HP不足25%' : '剩余HP安全'
  return {
    referenceHp,
    referenceRoleLabel: hpReferenceRoleLabel(mechanic),
    damage: after,
    damageRatio,
    damageBarRatio: Math.min(damageRatio, 1),
    overflowRatio: Math.max(0, damageRatio - 1),
    remainingHp,
    remainingRatio,
    risk,
    label,
  }
}

function percentLabel(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}

function hpReferenceForMechanic(mechanic: TimelineMechanic): number {
  const value = attackClass(mechanic) === 'TANK_BUSTER' || attackClass(mechanic) === 'AUTO_ATTACK'
    ? tankHpReference.value
    : healerHpReference.value
  return Math.max(1, Number(value) || 1)
}

function hpReferenceRoleLabel(mechanic: TimelineMechanic): string {
  return attackClass(mechanic) === 'TANK_BUSTER' || attackClass(mechanic) === 'AUTO_ATTACK'
    ? '防护HP基准'
    : '治疗HP基准'
}

function postMitigationDamageText(estimate: DamageEstimate | undefined, mechanic: TimelineMechanic): string {
  if (estimate?.damageAfterMitigation === null || estimate?.damageAfterMitigation === undefined) return '减伤后 —'
  const hpAnalysis = damageHpAnalysis(estimate, mechanic)
  return hpAnalysis
    ? `减伤后 ${displayInteger(estimate.damageAfterMitigation)} · ${percentLabel(hpAnalysis.damageRatio)}HP`
    : `减伤后 ${displayInteger(estimate.damageAfterMitigation)}`
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
watch([selectedTrackId, () => snapshot.value.tracks], ensureAiFocusTrackVisible, { deep: true, immediate: true })
watch([mechanics, showTimelineMarkers], ensureSelectedMechanicVisible, { deep: true, immediate: true })
watch([selectedMechanicId, mechanics], syncTimelinePageToSelection, { deep: true, immediate: true })
watch(selectedAssignment, (assignment) => {
  if (!assignment) closeAssignmentEditor()
})

onBeforeUnmount(() => {
  stopAssignmentTimelineDrag()
})

function addAssignment() {
  if (!selectedAbilityId.value || !selectedTrackId.value || selectedAbilityCooldownState.value.blocked) return
  const mechanic = selectedMechanic.value
  const window = defaultAssignmentWindow(mechanic)
  const assignment: Assignment = {
    assignmentId: newId(),
    mechanicId: mechanic.mechanicId,
    trackId: selectedTrackId.value,
    actionId: selectedAbilityId.value,
    anchorId: null,
    targetTrackId: selectedTargetTrackId.value || null,
    highlightAtMs: window.highlightAtMs,
    earliestUseAtMs: window.earliestUseAtMs,
    latestUseAtMs: window.latestUseAtMs,
    impactAtMs: window.impactAtMs,
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
      aiFocusTrackId.value = remapTrackId(aiFocusTrackId.value) ?? ''
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
  try {
    validation.value = await api.validatePlan(planId.value)
    message.value = validation.value.valid ? '规则校验通过' : `发现 ${validationErrorCount.value} 个阻止发布的问题`
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '规则校验失败'
  }
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
    if (reason instanceof ApiError && reason.status === 422 && isRuleValidationResult(reason.body)) {
      validation.value = reason.body
      error.value = `规则校验未通过，计划没有发布：${validationErrorCount.value} 个错误、${validationWarningCount.value} 个警告`
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
  const focusTrackId = aiOptimizationMode.value === 'FOCUSED' ? aiFocusTrack.value?.trackId : null
  if (aiOptimizationMode.value === 'FOCUSED' && !focusTrackId) {
    error.value = '指向优化需要先选择一个职业轨道'
    return
  }
  busy.value = true
  error.value = ''
  try {
    aiCandidate.value = await api.generateAiCandidate(planId.value, {
      instruction: aiInstruction.value,
      mode: aiOptimizationMode.value,
      focusTrackId,
      preserveExistingAssignments: aiPreserveExistingAssignments.value,
      allowGcdActions: aiAllowGcdActions.value,
    })
    const modeLabel = aiOptimizationMode.value === 'FOCUSED' ? `指向 ${trackDisplayLabel(aiFocusTrack.value)} ` : '全局 '
    message.value = `已生成 ${modeLabel}${aiCandidate.value.confidence} 候选，尚未应用`
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : 'AI 候选生成失败'
  } finally {
    busy.value = false
  }
}

function fillAiOptimizationInstruction() {
  const riskyLines = allMechanics.value
    .filter((mechanic) => hasDirectDamage(mechanic))
    .map((mechanic) => {
      const estimate = damageEstimates.value[mechanic.mechanicId]
      const hp = damageHpAnalysis(estimate, mechanic)
      const assigned = assignmentCountByMechanic.value.get(mechanic.mechanicId) ?? 0
      if (hp?.risk === 'GREEN' && assigned > 0) return null
      const damage = hp ? `${displayInteger(hp.damage)} / ${displayInteger(hp.referenceHp)} HP（${percentLabel(hp.damageRatio)}，${hp.label}）` : damageEstimateLabel(mechanic)
      return `${formatTime(mechanic.plannedAtMs)} ${mechanic.name}：${attackClassLabel(mechanic)} / ${damageTypeLabel(mechanic.damageType)}，${damage}，本机制安排 ${assigned} 个`
    })
    .filter((line): line is string => line !== null)
    .slice(0, 12)

  const modeLines = aiOptimizationMode.value === 'FOCUSED'
    ? [
        `优化模式：指向优化，只调整 ${trackDisplayLabel(aiFocusTrack.value)} 的未锁定任务；其它轨道只作为上下文参考，必须原样保留。`,
        '指向目标：结合其它人已经安排的团减、单减和治疗，减少本轨道重复交资源，优先补本轨道红/黄风险和冷却冲突。',
      ]
    : [
        '优化模式：全局优化，可调整所有未锁定任务；目标是全队总体风险下降和资源利用率提高。',
      ]

  aiInstruction.value = [
    ...modeLines,
    aiPreserveExistingAssignments.value
      ? '硬规则：只允许新增安排，不修改或删除任何当前已有减伤；已有安排即使未锁定也必须原样保留。'
      : '硬规则：可在服务端规则允许范围内调整未锁定安排；locked=true 仍必须原样保留。',
    aiAllowGcdActions.value
      ? '硬规则：允许使用 GCD 技能，但只有在 oGCD 不足以覆盖风险时才使用。'
      : '硬规则：不允许使用 GCD 技能；优先使用 oGCD 减伤、抬血、护盾、增疗和资源。',
    '目标：优化当前减伤与治疗利用率，但不要修改 locked=true 的任务，不要改变轨道/职业边界，不要输出计划外轨道。',
    `HP 风险口径：AOE 按治疗 HP ${displayInteger(healerHpReference.value)} 为 100%；死刑和平A按防护 HP ${displayInteger(tankHpReference.value)} 为 100%。超过 HP 上限为红色，命中后剩余 HP 低于 25% 为黄色，其余为绿色。`,
    '优先级：1）先补足红色/黄色承伤；2）优先利用可覆盖多个机制的长持续团减/团血，减少只覆盖一次的浪费；3）避免同一轨道冷却冲突；4）单减必须保留或补全 targetTrackId；5）治疗、增疗、护盾可作为复核提示，但不要当作百分比减伤直接扣伤害；6）不要为了绿色机制刷纯治疗、增疗资源或未建模盾。',
    '机制口径：AOE、死刑、平A不要混在一起按裸伤害排序；死刑优先看防护/单减/敌方减伤是否覆盖，AOE优先看团减与全队血线，平A只在连续坦克压力或非绿色风险时处理。',
    '重点机制：',
    riskyLines.length ? riskyLines.join('\n') : '当前没有明显红/黄承伤；请优先提高多机制覆盖率、减少冷却空转，并保留现有可用安排。',
  ].join('\n')
  aiOpen.value = true
}

function trackDisplayLabel(track: PlanSnapshot['tracks'][number] | null): string {
  if (!track) return '未选择轨道'
  return `${track.slot}${track.displayName ? ` · ${track.displayName}` : ''}`
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

function compactDuration(milliseconds: number | undefined): string {
  if (!milliseconds || milliseconds <= 0) return '0s'
  if (milliseconds < 1_000) return `${Math.ceil(milliseconds)}ms`
  if (milliseconds < 60_000) return `${Math.ceil(milliseconds / 1000)}s`
  const minutes = Math.floor(milliseconds / 60_000)
  const secondsPart = Math.ceil((milliseconds % 60_000) / 1000)
  return secondsPart ? `${minutes}m${secondsPart}s` : `${minutes}m`
}

function abilityOptionLabel(ability: AbilityDefinition): string {
  return `${ability.name} · 持续 ${seconds(ability.durationMs)} · CD ${seconds(ability.cooldownMs)}`
}

function abilityInlineSummary(ability: AbilityDefinition | undefined): string {
  if (!ability) return abilityFilterSummary.value
  const cooldown = selectedAbilityCooldownState.value.blocked
    ? ` · 冷却 ${selectedAbilityCooldownState.value.label}`
    : ''
  return `${abilityPlanningCategoryLabel(ability)} · ${abilityEffectSummary(ability)}${cooldown}`
}

function displayMs(milliseconds: number | null | undefined): string {
  return milliseconds === null || milliseconds === undefined ? '—' : `${Math.round(milliseconds).toLocaleString('zh-CN')} ms`
}

function selectedAssignmentCoverageEndMs(): number | null {
  if (!selectedAssignment.value || !selectedAssignmentAbility.value || selectedAssignmentAbility.value.durationMs <= 0) return null
  return selectedAssignment.value.earliestUseAtMs + selectedAssignmentAbility.value.durationMs
}

function assignmentTimelinePercent(milliseconds: number | null | undefined): string {
  if (milliseconds === null || milliseconds === undefined) return '0%'
  const start = selectedAssignmentTimelineStartMs.value
  const span = Math.max(1, selectedAssignmentTimelineEndMs.value - start)
  return `${Math.min(100, Math.max(0, ((milliseconds - start) / span) * 100)).toFixed(3)}%`
}

function cloneAssignmentForEditor(assignment: Assignment): Assignment {
  return {
    ...assignment,
    fallbacks: assignment.fallbacks.map((fallback) => ({ ...fallback })),
  }
}

function openAssignmentEditor() {
  if (!selectedAssignment.value) return
  assignmentEditorOriginal.value = cloneAssignmentForEditor(selectedAssignment.value)
  assignmentEditorOpen.value = true
}

function closeAssignmentEditor() {
  assignmentEditorOpen.value = false
  assignmentEditorOriginal.value = null
  stopAssignmentTimelineDrag()
}

function cancelAssignmentEditor() {
  if (selectedAssignment.value && assignmentEditorOriginal.value) {
    Object.assign(selectedAssignment.value, cloneAssignmentForEditor(assignmentEditorOriginal.value))
  }
  closeAssignmentEditor()
}

function assignmentTimeFromPointer(event: Pick<PointerEvent, 'clientX'>): number | null {
  const element = assignmentTimelineRef.value
  if (!element) return null
  const rect = element.getBoundingClientRect()
  if (rect.width <= 0) return null
  const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width))
  const start = selectedAssignmentTimelineStartMs.value
  const span = selectedAssignmentTimelineEndMs.value - start
  return start + ratio * span
}

function startAssignmentTimelineDrag(field: AssignmentTimeField, event: PointerEvent) {
  event.preventDefault()
  event.stopPropagation()
  draggingAssignmentField.value = field
  const target = event.currentTarget
  if (target instanceof HTMLElement && typeof target.setPointerCapture === 'function') {
    target.setPointerCapture(event.pointerId)
  }
  updateAssignmentTimeFromPointer(field, event)
  window.addEventListener('pointermove', handleAssignmentTimelinePointerMove)
  window.addEventListener('pointerup', stopAssignmentTimelineDrag, { once: true })
  window.addEventListener('pointercancel', stopAssignmentTimelineDrag, { once: true })
}

function handleAssignmentTimelinePointerMove(event: PointerEvent) {
  if (!draggingAssignmentField.value) return
  updateAssignmentTimeFromPointer(draggingAssignmentField.value, event)
}

function updateAssignmentTimeFromPointer(field: AssignmentTimeField, event: Pick<PointerEvent, 'clientX'>) {
  const time = assignmentTimeFromPointer(event)
  if (time === null) return
  setSelectedAssignmentTime(field, time)
}

function stopAssignmentTimelineDrag() {
  draggingAssignmentField.value = null
  window.removeEventListener('pointermove', handleAssignmentTimelinePointerMove)
  window.removeEventListener('pointerup', stopAssignmentTimelineDrag)
  window.removeEventListener('pointercancel', stopAssignmentTimelineDrag)
}

function setSelectedAssignmentTime(field: AssignmentTimeField, rawValue: unknown) {
  if (!selectedAssignment.value) return
  const value = typeof rawValue === 'number' ? rawValue : Number(rawValue)
  if (!Number.isFinite(value)) return
  selectedAssignment.value[field] = Math.max(0, Math.round(value / ASSIGNMENT_TIME_STEP_MS) * ASSIGNMENT_TIME_STEP_MS)
  normalizeSelectedAssignmentTimes(field)
  validation.value = null
}

function normalizeSelectedAssignmentTimes(changedField: AssignmentTimeField) {
  const assignment = selectedAssignment.value
  if (!assignment) return
  if (changedField === 'impactAtMs') {
    assignment.latestUseAtMs = Math.min(assignment.latestUseAtMs, assignment.impactAtMs)
    assignment.earliestUseAtMs = Math.min(assignment.earliestUseAtMs, assignment.latestUseAtMs)
    assignment.highlightAtMs = Math.min(assignment.highlightAtMs, assignment.earliestUseAtMs)
    return
  }
  if (changedField === 'latestUseAtMs') {
    assignment.latestUseAtMs = Math.min(assignment.latestUseAtMs, assignment.impactAtMs)
    assignment.earliestUseAtMs = Math.min(assignment.earliestUseAtMs, assignment.latestUseAtMs)
    assignment.highlightAtMs = Math.min(assignment.highlightAtMs, assignment.earliestUseAtMs)
    return
  }
  if (changedField === 'earliestUseAtMs') {
    assignment.earliestUseAtMs = Math.min(assignment.earliestUseAtMs, assignment.latestUseAtMs)
    assignment.highlightAtMs = Math.min(assignment.highlightAtMs, assignment.earliestUseAtMs)
    return
  }
  assignment.highlightAtMs = Math.min(assignment.highlightAtMs, assignment.earliestUseAtMs)
}

function setSelectedAssignmentTargetTrack(trackId: string) {
  if (!selectedAssignment.value) return
  selectedAssignment.value.targetTrackId = trackId || null
  validation.value = null
}

function eventValue(event: Event): string {
  return event.target instanceof HTMLInputElement || event.target instanceof HTMLSelectElement
    ? event.target.value
    : ''
}

function coverageSourceLabel(coverage: AssignmentCoverage): string {
  const sourceMechanic = coverage.sourceMechanic
  const source = sourceMechanic ? `${formatTime(sourceMechanic.plannedAtMs)} ${sourceMechanic.name}` : '未知来源机制'
  const track = coverage.sourceTrack?.slot ?? '未知轨道'
  return `${track} · 创建于 ${source}`
}

function coverageTimingLabel(coverage: AssignmentCoverage): string {
  const release = `释放 ${formatTime(coverage.assignment.earliestUseAtMs)}`
  const until = coverage.coversUntilMs === null ? '持续时间未知' : `保守覆盖至 ${formatTime(coverage.coversUntilMs)}`
  return `${release} · ${until}`
}

function fallbackAbilities(): AbilityDefinition[] {
  return [
    { actionId: 7535, name: '雪仇 / Reprisal', iconPath: 'ui/icon/000000/000806.tex', jobIds: [19, 21, 32, 37], cooldownMs: 60_000, maxCharges: 1, durationMs: 15_000, confirmationStrategy: 'STATUS_APPLY', source: 'Local fallback', confidence: 'REVIEWED', castCategory: 'OGCD', effect: { scope: 'ENEMY_AREA', allDamageReductionPercent: 10, physicalDamageReductionPercent: 0, magicalDamageReductionPercent: 0, maximumHpIncreasePercent: 0, maximumHpBarrierPercent: 0, barrierCurePotency: 0, invulnerability: false, stackingGroup: '', calculationReadiness: 'DIRECT_REDUCTION', conditions: [], source: 'Local fallback', confidence: 'REVIEWED' } },
    { actionId: 24298, name: 'Kerachole', iconPath: 'ui/icon/003000/003666.tex', jobIds: [40], cooldownMs: 30_000, maxCharges: 1, durationMs: 15_000, confirmationStrategy: 'STATUS_APPLY', source: 'Local fallback', confidence: 'UNVERIFIED', castCategory: 'OGCD', effect: { scope: 'PARTY', allDamageReductionPercent: 10, physicalDamageReductionPercent: 0, magicalDamageReductionPercent: 0, maximumHpIncreasePercent: 0, maximumHpBarrierPercent: 0, barrierCurePotency: 0, invulnerability: false, stackingGroup: 'SGE_KERACHOLE_TAUROCHOLE', calculationReadiness: 'DIRECT_REDUCTION', conditions: [], source: 'Local fallback', confidence: 'REVIEWED' } },
    { actionId: 24310, name: 'Holos', iconPath: 'ui/icon/003000/003678.tex', jobIds: [40], cooldownMs: 120_000, maxCharges: 1, durationMs: 20_000, confirmationStrategy: 'STATUS_APPLY', source: 'Local fallback', confidence: 'UNVERIFIED', castCategory: 'OGCD', effect: { scope: 'PARTY', allDamageReductionPercent: 10, physicalDamageReductionPercent: 0, magicalDamageReductionPercent: 0, maximumHpIncreasePercent: 0, maximumHpBarrierPercent: 0, barrierCurePotency: 300, invulnerability: false, stackingGroup: '', calculationReadiness: 'REQUIRES_HEALING_STATS', conditions: [], source: 'Local fallback', confidence: 'REVIEWED' } },
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

    <section v-if="validation && !validation.valid" class="validation-banner" aria-live="polite">
      <header>
        <div>
          <p class="eyebrow">PUBLICATION BLOCKED</p>
          <h2>规则校验未通过</h2>
        </div>
        <div class="validation-counts">
          <span><b>{{ validationErrorCount }}</b>错误</span>
          <span><b>{{ validationWarningCount }}</b>警告</span>
        </div>
      </header>
      <div class="validation-issue-grid">
        <button
          v-for="display in validationIssueDisplays.slice(0, 8)"
          :key="`${display.issue.code}-${display.issue.reference}`"
          :class="['validation-issue-card', display.issue.severity.toLowerCase()]"
          type="button"
          @click="focusRuleIssue(display)"
        >
          <span>{{ display.issue.code }}</span>
          <b>{{ display.title }}</b>
          <small>{{ display.location }}</small>
          <em>{{ display.detail }}</em>
          <p>{{ display.issue.message }}</p>
        </button>
      </div>
      <p v-if="validationIssueDisplays.length > 8" class="validation-more">
        还有 {{ validationIssueDisplays.length - 8 }} 条规则问题，完整列表在编辑区底部。
      </p>
    </section>

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
      <div class="ai-mode-grid" role="radiogroup" aria-label="AI 优化模式">
        <button
          :class="['ai-mode-option', { active: aiOptimizationMode === 'GLOBAL' }]"
          type="button"
          role="radio"
          :aria-checked="aiOptimizationMode === 'GLOBAL'"
          @click="aiOptimizationMode = 'GLOBAL'"
        >
          <b>全局优化</b>
          <span>调整所有未锁定任务，优先降低全队红/黄风险与资源浪费。</span>
        </button>
        <button
          :class="['ai-mode-option', { active: aiOptimizationMode === 'FOCUSED' }]"
          type="button"
          role="radio"
          :aria-checked="aiOptimizationMode === 'FOCUSED'"
          @click="aiOptimizationMode = 'FOCUSED'"
        >
          <b>指向优化</b>
          <span>只改选定职业轨道，其它人的安排作为只读上下文。</span>
        </button>
      </div>
      <label v-if="aiOptimizationMode === 'FOCUSED'" class="ai-focus-track">优化目标轨道
        <select v-model="aiFocusTrackId">
          <option v-for="track in snapshot.tracks" :key="track.trackId" :value="track.trackId">
            {{ trackDisplayLabel(track) }}
          </option>
        </select>
      </label>
      <p class="ai-policy-copy">
        服务端会拒绝计划外轨道/机制/技能、锁定项改动、指向模式下的非目标轨道改动，以及绿色机制上的纯治疗/未建模辅助刷屏；下面两个硬规则会随请求一起发送，并由服务端二次拦截。
      </p>
      <div class="ai-safety-grid">
        <label class="ai-safety-option">
          <input v-model="aiPreserveExistingAssignments" type="checkbox" />
          <span>
            <b>只新增，不改现有安排</b>
            <small>默认开启：AI 只能补空转资源，不能移动、替换或删除当前减伤。</small>
          </span>
        </label>
        <label class="ai-safety-option">
          <input v-model="aiAllowGcdActions" type="checkbox" />
          <span>
            <b>允许使用 GCD 技能</b>
            <small>默认关闭：均衡预后、鼓舞、士气等读条/占 GCD 技能不会被 AI 新增。</small>
          </span>
        </label>
      </div>
      <label>调整要求
        <textarea
          v-model.trim="aiInstruction"
          maxlength="2000"
          placeholder="例如：优先减少 H2 压力，锁定项不动；避免同一轨道连续两个 90 秒技能冲突。"
        />
      </label>
      <div class="candidate-actions">
        <button class="secondary-button" type="button" @click="fillAiOptimizationInstruction"><Sparkles :size="16" />填入优化指令</button>
        <button class="primary-button" type="button" :disabled="busy" @click="generateAiCandidate"><Sparkles :size="16" />生成候选</button>
        <small>服务端需要配置 VEDAAXIS_AI_API_KEY。返回结果只会作为候选，经规则校验后由你手动应用，不会自动发布。</small>
      </div>
    </section>

    <div class="editor-workspace">
      <aside class="mechanic-panel">
        <header><div><p class="eyebrow">TIMELINE</p><h2>{{ timelineTitle }}</h2></div><span>{{ mechanics.length }} / {{ allMechanics.length }} 项 · {{ snapshot.assignments.length }} 个减伤安排</span></header>
        <div class="timeline-pager">
          <button class="secondary-button compact" type="button" :disabled="timelinePage === 0" @click="setTimelinePage(timelinePage - 1)">上一页</button>
          <span>第 {{ timelinePage + 1 }} / {{ timelinePageCount }} 页</span>
          <button class="secondary-button compact" type="button" :disabled="timelinePage + 1 >= timelinePageCount" @click="setTimelinePage(timelinePage + 1)">下一页</button>
          <small>{{ timelinePageRangeLabel }} · 本页 {{ timelinePageAssignmentCount }} 个安排</small>
          <label class="timeline-marker-toggle">
            <input v-model="showTimelineMarkers" type="checkbox" />
            显示无伤害标记<span v-if="!showTimelineMarkers">（隐藏 {{ hiddenMarkerCount }}）</span>
          </label>
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
              {{ postMitigationDamageText(damageEstimates[mechanic.mechanicId], mechanic) }}
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
          <label>
            <span class="quick-label-row"><b>执行轨道</b></span>
            <select v-model="selectedTrackId">
              <option v-for="track in snapshot.tracks" :key="track.trackId" :value="track.trackId">{{ track.slot }} · {{ track.displayName }}</option>
            </select>
          </label>
          <label class="ability-picker">
            <span class="quick-label-row">
              <b>减伤技能</b>
              <em :class="{ warning: selectedAbilityCooldownState.blocked }">{{ abilityInlineSummary(selectedAbility) }}</em>
            </span>
            <div class="ability-combobox">
              <button
                class="ability-picker-trigger"
                type="button"
                :title="selectedAbility ? abilityOptionLabel(selectedAbility) : '选择技能'"
                @click="abilityPickerOpen = !abilityPickerOpen"
              >
                <span class="action-icon-shell action-icon-shell-select">
                  <img
                    v-if="actionIconUrl(selectedAbility)"
                    class="action-icon action-icon-select"
                    :src="actionIconUrl(selectedAbility)!"
                    alt=""
                    decoding="async"
                    referrerpolicy="no-referrer"
                    @error="hideBrokenIcon"
                  />
                  <span v-else class="action-icon action-icon-select action-icon-placeholder" aria-hidden="true">
                    {{ selectedAbility ? '技' : '—' }}
                  </span>
                  <span v-if="selectedAbilityCooldownState.blocked" class="cooldown-overlay">{{ selectedAbilityCooldownState.label }}</span>
                </span>
                <span>
                  <b>{{ selectedAbility?.name ?? '选择技能' }}</b>
                  <small>{{ selectedAbility ? `${abilityPlanningCategoryLabel(selectedAbility)} · CD ${seconds(selectedAbility.cooldownMs)}` : '当前轨道暂无可用技能' }}</small>
                </span>
                <em>▾</em>
              </button>
              <div
                v-if="abilityPickerOpen"
                class="ability-picker-modal"
                role="dialog"
                aria-modal="true"
                aria-label="选择减伤技能"
                @click.self="abilityPickerOpen = false"
              >
                <div class="ability-picker-popover">
                  <header class="ability-picker-modal-header">
                    <div>
                      <strong>选择减伤技能</strong>
                      <small>{{ abilityFilterSummary }}</small>
                    </div>
                    <button class="ability-picker-close" type="button" aria-label="关闭技能选择" @click="abilityPickerOpen = false">×</button>
                  </header>
                  <div class="ability-picker-groups">
                    <section v-for="group in groupedFilteredAbilities" :key="group.category" class="ability-picker-group">
                      <header>{{ group.label }}<span>{{ group.abilities.length }}</span></header>
                      <button
                        v-for="ability in group.abilities"
                        :key="ability.actionId"
                        :class="['ability-picker-option', { selected: selectedAbilityId === ability.actionId, blocked: abilityCooldownState(ability).blocked }]"
                        type="button"
                        :disabled="abilityCooldownState(ability).blocked"
                        :title="abilityCooldownState(ability).blocked ? `冷却中，预计 ${formatTime(abilityCooldownState(ability).availableAtMs ?? 0)} 可用` : abilityOptionLabel(ability)"
                        @click="selectAbility(ability)"
                      >
                        <span class="action-icon-shell">
                          <img
                            v-if="actionIconUrl(ability)"
                            class="action-icon action-icon-picker"
                            :src="actionIconUrl(ability)!"
                            alt=""
                            loading="lazy"
                            decoding="async"
                            referrerpolicy="no-referrer"
                            @error="hideBrokenIcon"
                          />
                          <span v-else class="action-icon action-icon-picker action-icon-placeholder" aria-hidden="true">技</span>
                          <span v-if="abilityCooldownState(ability).blocked" class="cooldown-overlay">{{ abilityCooldownState(ability).label }}</span>
                        </span>
                        <span>
                          <b>{{ ability.name }}</b>
                          <small>{{ abilityEffectSummary(ability) }}</small>
                          <small>
                            持续 {{ seconds(ability.durationMs) }} · CD {{ seconds(ability.cooldownMs) }}
                            <template v-if="abilityCooldownState(ability).blocked"> · {{ formatTime(abilityCooldownState(ability).availableAtMs ?? 0) }} 可用</template>
                          </small>
                        </span>
                      </button>
                    </section>
                  </div>
                  <p v-if="!groupedFilteredAbilities.length" class="ability-picker-empty">当前轨道没有可安排技能；可以勾选“显示全部技能”临时查看完整目录。</p>
                </div>
              </div>
            </div>
          </label>
          <label>
            <span class="quick-label-row"><b>单体目标（可选）</b></span>
            <select v-model="selectedTargetTrackId">
              <option value="">无</option>
              <option v-for="track in snapshot.tracks" :key="track.trackId" :value="track.trackId">{{ track.slot }} · {{ track.displayName }}</option>
            </select>
          </label>
          <div class="quick-assign-actions">
            <label class="show-all-abilities"><input v-model="showAllAbilities" type="checkbox" />显示全部技能</label>
            <button
              class="primary-button"
              type="button"
              :disabled="!selectedAbilityId || selectedAbilityCooldownState.blocked"
              :title="selectedAbilityCooldownState.blocked ? `技能冷却中，${formatTime(selectedAbilityCooldownState.availableAtMs ?? 0)} 可用` : '安排技能'"
              @click="addAssignment"
            ><Plus :size="16" />安排技能</button>
          </div>
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
              <span v-else class="action-icon action-icon-card action-icon-placeholder">?</span>
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
          <div class="hp-reference-controls">
            <label>治疗 HP 基准<input v-model.number="healerHpReference" type="number" min="1" step="1000" /></label>
            <label>防护 HP 基准<input v-model.number="tankHpReference" type="number" min="1" step="1000" /></label>
            <small>AOE 按治疗职业 HP 判定，死刑和平A按防护职业 HP 判定；超过 HP 上限红色，命中后剩余 HP &lt;25% 黄色。</small>
          </div>
          <div v-if="selectedDamageEstimate?.damageAfterMitigation != null" class="damage-analysis-metrics">
            <span><small>机制原始总伤害</small><b>{{ displayInteger(selectedDamageEstimate.baselineDamage) }}</b></span>
            <span><small>已建模减伤</small><b>{{ selectedDamageEstimate.modeledReduction === null ? '—' : `${(selectedDamageEstimate.modeledReduction * 100).toFixed(1)}%` }}</b></span>
            <span><small>减伤后预计伤害</small><b :class="damageRiskClass(selectedDamageEstimate)">{{ displayInteger(selectedDamageEstimate.damageAfterMitigation) }}</b></span>
            <span><small>{{ selectedDamageHpAnalysis?.referenceRoleLabel ?? 'HP 基准' }}</small><b>{{ displayInteger(selectedDamageHpAnalysis?.referenceHp ?? null) }}</b></span>
            <span><small>预计剩余 HP</small><b :class="damageRiskClass(selectedDamageEstimate)">{{ selectedDamageHpAnalysis ? `${displayInteger(selectedDamageHpAnalysis.remainingHp)}（${percentLabel(selectedDamageHpAnalysis.remainingRatio)}）` : '—' }}</b></span>
            <span><small>最危险轨道</small><b>{{ selectedDamageEstimate.worstTrackSlot ?? '—' }}</b></span>
          </div>
          <div v-if="selectedDamageHpAnalysis" class="hp-damage-panel">
            <div class="hp-damage-bar" aria-label="HP 承伤比例">
              <span
                :class="['hp-damage-fill', `damage-fill-${selectedDamageHpAnalysis.risk.toLowerCase()}`]"
                :style="{ width: percentLabel(selectedDamageHpAnalysis.damageBarRatio) }"
              ></span>
            </div>
            <div class="hp-damage-labels">
              <span><i class="incoming"></i>预计承伤 {{ percentLabel(selectedDamageHpAnalysis.damageRatio) }} HP</span>
              <span><i class="remaining"></i>剩余 {{ percentLabel(selectedDamageHpAnalysis.remainingRatio) }}</span>
              <span v-if="selectedDamageHpAnalysis.overflowRatio > 0" class="hp-overflow">溢出 {{ percentLabel(selectedDamageHpAnalysis.overflowRatio) }}</span>
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
            <div class="analysis-row-list">
              <article v-for="coverage in carriedCoverage" :key="coverage.assignment.assignmentId" class="analysis-row coverage-row">
                <span class="action-icon-shell analysis-row-icon">
                  <img
                    v-if="actionIconUrl(coverage.ability)"
                    class="action-icon"
                    :src="actionIconUrl(coverage.ability)!"
                    alt=""
                    loading="lazy"
                    decoding="async"
                    referrerpolicy="no-referrer"
                    @error="hideBrokenIcon"
                  />
                  <span v-else class="action-icon action-icon-placeholder" aria-hidden="true">技</span>
                </span>
                <span>
                  <b>{{ coverage.ability?.name ?? `Action ${coverage.assignment.actionId}` }}</b>
                  <small>{{ coverageSourceLabel(coverage) }}</small>
                  <em>{{ coverageTimingLabel(coverage) }}</em>
                </span>
              </article>
            </div>
          </div>
          <div v-if="cooldownConflicts.length" class="cooldown-panel">
            <header><b>本地冷却预警</b><small>保存/发布时服务端也会校验</small></header>
            <div class="analysis-row-list">
              <article v-for="issue in cooldownConflicts.slice(0, 5)" :key="issue.assignmentId" class="analysis-row cooldown-row">
                <span class="action-icon-shell analysis-row-icon">
                  <img
                    v-if="actionIconUrl(abilityMap.get(issue.actionId))"
                    class="action-icon"
                    :src="actionIconUrl(abilityMap.get(issue.actionId))!"
                    alt=""
                    loading="lazy"
                    decoding="async"
                    referrerpolicy="no-referrer"
                    @error="hideBrokenIcon"
                  />
                  <span v-else class="action-icon action-icon-placeholder" aria-hidden="true">CD</span>
                </span>
                <span>
                  <b>{{ issue.abilityName }}</b>
                  <small>{{ issue.trackSlot }} · 最早要到 {{ formatTime(issue.availableAtMs) }}，当前窗口最晚 {{ formatTime(issue.latestUseAtMs) }}</small>
                  <em>还差 {{ compactDuration(issue.availableAtMs - issue.latestUseAtMs) }}</em>
                </span>
              </article>
            </div>
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
            <b>{{ validation.valid ? '规则校验通过' : `${validationErrorCount} 个错误 · ${validationWarningCount} 个警告` }}</b>
          </header>
          <div v-if="validationIssueDisplays.length" class="validation-issue-list">
            <button
              v-for="display in validationIssueDisplays"
              :key="`${display.issue.code}-${display.issue.reference}`"
              :class="['validation-issue-row', display.issue.severity.toLowerCase()]"
              type="button"
              @click="focusRuleIssue(display)"
            >
              <span>{{ display.issue.code }}</span>
              <b>{{ display.title }}</b>
              <small>{{ display.location }}</small>
              <em>{{ display.detail }}</em>
              <p>{{ display.issue.message }}</p>
            </button>
          </div>
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
          <div v-if="selectedAssignmentCoverageStatus" :class="['assignment-status-card', selectedAssignmentCoverageStatus.tone]">
            <CheckCircle2 v-if="selectedAssignmentCoverageStatus.tone === 'green'" :size="16" />
            <AlertTriangle v-else-if="selectedAssignmentCoverageStatus.tone === 'yellow' || selectedAssignmentCoverageStatus.tone === 'red'" :size="16" />
            <CircleX v-else :size="16" />
            <span><b>{{ selectedAssignmentCoverageStatus.title }}</b><small>{{ selectedAssignmentCoverageStatus.message }}</small></span>
          </div>
          <div class="assignment-readonly-grid">
            <span><small>开始高亮</small><b>{{ displayMs(selectedAssignment.highlightAtMs) }}</b></span>
            <span><small>最早释放</small><b>{{ displayMs(selectedAssignment.earliestUseAtMs) }}</b></span>
            <span><small>最晚释放</small><b>{{ displayMs(selectedAssignment.latestUseAtMs) }}</b></span>
            <span><small>机制判定</small><b>{{ displayMs(selectedAssignment.impactAtMs) }}</b></span>
            <span class="wide"><small>单体目标轨道</small><b>{{ selectedAssignmentTargetTrack ? `${selectedAssignmentTargetTrack.slot} · ${selectedAssignmentTargetTrack.displayName}` : '无' }}</b></span>
          </div>
          <div class="assignment-mini-timeline" aria-label="任务窗口概览">
            <span class="mini-window" :style="{ left: assignmentTimelinePercent(selectedAssignment.earliestUseAtMs), width: `calc(${assignmentTimelinePercent(selectedAssignment.latestUseAtMs)} - ${assignmentTimelinePercent(selectedAssignment.earliestUseAtMs)})` }"></span>
            <span v-if="selectedAssignmentCoverageEndMs() !== null" class="mini-coverage" :style="{ left: assignmentTimelinePercent(selectedAssignment.earliestUseAtMs), width: `calc(${assignmentTimelinePercent(selectedAssignmentCoverageEndMs())} - ${assignmentTimelinePercent(selectedAssignment.earliestUseAtMs)})` }"></span>
            <i class="mini-impact" :style="{ left: assignmentTimelinePercent(selectedAssignment.impactAtMs) }"></i>
          </div>
          <button class="assignment-edit-button" type="button" @click="openAssignmentEditor">
            <Pencil :size="15" />编辑释放窗口
          </button>
          <button class="lock-toggle" type="button" @click="selectedAssignment.locked = !selectedAssignment.locked">
            <Lock v-if="selectedAssignment.locked" :size="16" /><LockOpen v-else :size="16" />
            <span><b>{{ selectedAssignment.locked ? '已锁定' : '允许优化' }}</b><small>AI 不会移动锁定项</small></span>
          </button>
        </template>
        <div v-else class="inspector-empty"><Shield :size="28" /><h3>选择一个任务</h3><p>在此调整时间窗口、确认方式和锁定状态。</p></div>
      </aside>

      <div
        v-if="assignmentEditorOpen && selectedAssignment"
        class="assignment-editor-modal"
        role="dialog"
        aria-modal="true"
        aria-label="编辑释放窗口"
        @click.self="cancelAssignmentEditor"
      >
        <div class="assignment-editor-panel">
          <header>
            <div>
              <p class="eyebrow">TIMING EDITOR</p>
              <h2>编辑释放窗口</h2>
              <small>{{ selectedAssignmentAbility?.name ?? `Action ${selectedAssignment.actionId}` }} · 当前机制 {{ mechanicInlineLabel(selectedAssignmentMechanic ?? selectedMechanic) }}</small>
            </div>
            <div v-if="selectedAssignmentCoverageStatus" :class="['assignment-editor-status', selectedAssignmentCoverageStatus.tone]">
              <CheckCircle2 v-if="selectedAssignmentCoverageStatus.tone === 'green'" :size="17" />
              <AlertTriangle v-else-if="selectedAssignmentCoverageStatus.tone === 'yellow' || selectedAssignmentCoverageStatus.tone === 'red'" :size="17" />
              <CircleX v-else :size="17" />
              <span>{{ selectedAssignmentCoverageStatus.message }}</span>
            </div>
          </header>

          <div ref="assignmentTimelineRef" class="assignment-timeline-canvas">
            <span
              v-for="mechanic in selectedAssignmentTimelineTicks"
              :key="mechanic.mechanicId"
              class="assignment-tick"
              :class="{ current: mechanic.mechanicId === selectedAssignment.mechanicId }"
              :style="{ left: assignmentTimelinePercent(mechanic.plannedAtMs) }"
              :title="mechanicInlineLabel(mechanic)"
            ></span>
            <span class="assignment-range window" :style="{ left: assignmentTimelinePercent(selectedAssignment.earliestUseAtMs), width: `calc(${assignmentTimelinePercent(selectedAssignment.latestUseAtMs)} - ${assignmentTimelinePercent(selectedAssignment.earliestUseAtMs)})` }"></span>
            <span v-if="selectedAssignmentCoverageEndMs() !== null" class="assignment-range coverage" :style="{ left: assignmentTimelinePercent(selectedAssignment.earliestUseAtMs), width: `calc(${assignmentTimelinePercent(selectedAssignmentCoverageEndMs())} - ${assignmentTimelinePercent(selectedAssignment.earliestUseAtMs)})` }"></span>
            <button
              type="button"
              :class="['assignment-marker', 'highlight', { dragging: draggingAssignmentField === 'highlightAtMs' }]"
              :style="{ left: assignmentTimelinePercent(selectedAssignment.highlightAtMs) }"
              title="拖动设置亮起时间"
              @pointerdown="startAssignmentTimelineDrag('highlightAtMs', $event)"
            ><b>亮起</b></button>
            <button
              type="button"
              :class="['assignment-marker', 'release', { dragging: draggingAssignmentField === 'earliestUseAtMs' }]"
              :style="{ left: assignmentTimelinePercent(selectedAssignment.earliestUseAtMs) }"
              title="拖动设置释放时间"
              @pointerdown="startAssignmentTimelineDrag('earliestUseAtMs', $event)"
            ><b>释放</b></button>
            <button
              type="button"
              :class="['assignment-marker', 'latest', { dragging: draggingAssignmentField === 'latestUseAtMs' }]"
              :style="{ left: assignmentTimelinePercent(selectedAssignment.latestUseAtMs) }"
              title="拖动设置最晚释放时间"
              @pointerdown="startAssignmentTimelineDrag('latestUseAtMs', $event)"
            ><b>最晚</b></button>
            <button
              type="button"
              :class="['assignment-marker', 'impact', { dragging: draggingAssignmentField === 'impactAtMs' }]"
              :style="{ left: assignmentTimelinePercent(selectedAssignment.impactAtMs) }"
              title="拖动设置机制判定时间"
              @pointerdown="startAssignmentTimelineDrag('impactAtMs', $event)"
            ><b>判定</b></button>
            <div class="assignment-axis">
              <span>{{ formatTime(selectedAssignmentTimelineStartMs) }}</span>
              <span>{{ formatTime(selectedAssignmentTimelineEndMs) }}</span>
            </div>
          </div>

          <div class="assignment-editor-controls">
            <label v-for="field in ASSIGNMENT_TIME_FIELDS" :key="field.key">
              <span>
                <b>{{ field.label }}</b>
                <em>{{ displayMs(selectedAssignment[field.key]) }}</em>
              </span>
              <input
                type="range"
                :min="0"
                :max="selectedAssignmentTimelineInputMaxMs"
                :step="ASSIGNMENT_TIME_STEP_MS"
                :value="selectedAssignment[field.key]"
                @input="setSelectedAssignmentTime(field.key, eventValue($event))"
              />
              <input
                type="number"
                :step="ASSIGNMENT_TIME_STEP_MS"
                :value="selectedAssignment[field.key]"
                @input="setSelectedAssignmentTime(field.key, eventValue($event))"
              />
            </label>
          </div>

          <label class="assignment-editor-target">
            <span>单体目标轨道</span>
            <select :value="selectedAssignment.targetTrackId ?? ''" @change="setSelectedAssignmentTargetTrack(eventValue($event))">
              <option value="">无</option>
              <option v-for="track in snapshot.tracks" :key="track.trackId" :value="track.trackId">{{ track.slot }} · {{ track.displayName }}</option>
            </select>
          </label>
          <footer>
            <p>绿色条表示技能持续覆盖范围，青色条表示允许释放窗口。可以直接拖动时间轴上的节点，也可以用滑条或输入毫秒值微调。</p>
            <div class="assignment-editor-actions">
              <button class="secondary-button assignment-editor-cancel" type="button" @click="cancelAssignmentEditor">取消</button>
              <button class="primary-button assignment-editor-done" type="button" @click="closeAssignmentEditor">完成</button>
            </div>
          </footer>
        </div>
      </div>
    </div>
  </section>
</template>
