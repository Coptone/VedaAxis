import { attackClass } from './combatPresentation'
import type {
  AbilityDefinition,
  Assignment,
  DamageEstimate,
  ExecutionTrack,
  MitigationEffectProfile,
  PlanSnapshot,
  TimelineMechanic,
} from '../types/domain'

export interface AssignmentCoverage {
  assignment: Assignment
  ability: AbilityDefinition | undefined
  sourceMechanic: TimelineMechanic | undefined
  sourceTrack: ExecutionTrack | undefined
  coversUntilMs: number | null
  carriedFromAnotherMechanic: boolean
}

export interface CooldownConflict {
  assignmentId: string
  previousAssignmentId: string | null
  actionId: number
  trackSlot: string
  abilityName: string
  availableAtMs: number
  latestUseAtMs: number
  previousUseAtMs: number | null
  previousMechanicId: string | null
  previousMechanicName: string | null
  previousMechanicTimeMs: number | null
}

export function abilityFitsTrack(
  ability: Pick<AbilityDefinition, 'jobIds'>,
  track: Pick<ExecutionTrack, 'allowedJobIds'> | null | undefined,
): boolean {
  if (!track || !ability.jobIds.length || !track.allowedJobIds.length) return true
  return ability.jobIds.some((jobId) => track.allowedJobIds.includes(jobId))
}

export function previewDamageEstimatesLocally(
  snapshot: PlanSnapshot,
  abilities: AbilityDefinition[],
  extraNotice = '当前结果由浏览器按已加载技能目录本地计算，用于服务端预计伤害暂不可用时兜底。',
): DamageEstimate[] {
  const abilityById = new Map(abilities.map((ability) => [ability.actionId, ability]))
  return snapshot.mechanics.map((mechanic) => {
    if (!mechanic.damageProfile) return calibrationRequired(mechanic.mechanicId)

    const targetEstimates = targetTracks(snapshot, mechanic)
      .map((track) => analyzeTarget(snapshot, mechanic, track, abilityById))
      .filter((estimate): estimate is TargetEstimate => estimate !== null)
    if (!targetEstimates.length) return calibrationRequired(mechanic.mechanicId)

    const worst = targetEstimates.reduce((current, candidate) =>
      candidate.damageAfterMitigation > current.damageAfterMitigation ? candidate : current)
    const notices = unique([
      ...worst.notices,
      extraNotice,
      '本地计算同样只扣除百分比减伤；治疗威力护盾、即时治疗和无敌不从伤害数字中扣除。',
    ])

    return {
      mechanicId: mechanic.mechanicId,
      status: worst.specialCase ? 'SPECIAL_CASE_REVIEW_REQUIRED' : 'CALCULATED',
      baselineDamage: mechanic.damageProfile.amount,
      damageAfterMitigation: worst.damageAfterMitigation,
      modeledReduction: worst.modeledReduction,
      riskLevel: riskLevel(mechanic, worst.damageAfterMitigation),
      worstTrackId: worst.track.trackId,
      worstTrackSlot: worst.track.slot,
      sampleCount: mechanic.damageProfile.sampleCount,
      statistic: mechanic.damageProfile.statistic,
      source: mechanic.damageProfile.source,
      notices,
    }
  })
}

export function assignmentsCoveringMechanic(
  snapshot: PlanSnapshot,
  abilities: AbilityDefinition[],
  mechanic: TimelineMechanic,
): AssignmentCoverage[] {
  const abilityById = new Map(abilities.map((ability) => [ability.actionId, ability]))
  const mechanicById = new Map(snapshot.mechanics.map((item) => [item.mechanicId, item]))
  const trackById = new Map(snapshot.tracks.map((track) => [track.trackId, track]))
  return snapshot.assignments
    .map((assignment) => {
      const ability = abilityById.get(assignment.actionId)
      if (!isRelevantToImpact(assignment, ability, mechanic)) return null
      return {
        assignment,
        ability,
        sourceMechanic: mechanicById.get(assignment.mechanicId),
        sourceTrack: trackById.get(assignment.trackId),
        coversUntilMs: ability && ability.durationMs > 0 ? assignment.earliestUseAtMs + ability.durationMs : null,
        carriedFromAnotherMechanic: assignment.mechanicId !== mechanic.mechanicId,
      }
    })
    .filter((item): item is AssignmentCoverage => item !== null)
}

export function localCooldownConflicts(snapshot: PlanSnapshot, abilities: AbilityDefinition[]): CooldownConflict[] {
  const abilityById = new Map(abilities.map((ability) => [ability.actionId, ability]))
  const trackById = new Map(snapshot.tracks.map((track) => [track.trackId, track]))
  const mechanicById = new Map(snapshot.mechanics.map((mechanic) => [mechanic.mechanicId, mechanic]))
  const groups = new Map<string, Assignment[]>()
  for (const assignment of snapshot.assignments) {
    const key = `${assignment.trackId}:${assignment.actionId}`
    groups.set(key, [...(groups.get(key) ?? []), assignment])
  }

  const conflicts: CooldownConflict[] = []
  for (const group of groups.values()) {
    group.sort((left, right) => left.earliestUseAtMs - right.earliestUseAtMs)
    let nextAvailableAtMs = Number.NEGATIVE_INFINITY
    let previousAssignment: Assignment | null = null
    for (const assignment of group) {
      const ability = abilityById.get(assignment.actionId)
      if (!ability) continue
      const recovery = Math.max(1, ability.maxCharges) === 1
        ? ability.cooldownMs
        : ability.cooldownMs / Math.max(1, ability.maxCharges)
      const scheduledAtMs = Math.max(assignment.earliestUseAtMs, nextAvailableAtMs)
      if (scheduledAtMs > assignment.latestUseAtMs) {
        const previousMechanic = previousAssignment ? mechanicById.get(previousAssignment.mechanicId) : undefined
        conflicts.push({
          assignmentId: assignment.assignmentId,
          previousAssignmentId: previousAssignment?.assignmentId ?? null,
          actionId: assignment.actionId,
          trackSlot: trackById.get(assignment.trackId)?.slot ?? assignment.trackId.slice(0, 8),
          abilityName: ability.name,
          availableAtMs: scheduledAtMs,
          latestUseAtMs: assignment.latestUseAtMs,
          previousUseAtMs: previousAssignment?.earliestUseAtMs ?? null,
          previousMechanicId: previousMechanic?.mechanicId ?? null,
          previousMechanicName: previousMechanic?.name ?? null,
          previousMechanicTimeMs: previousMechanic?.plannedAtMs ?? null,
        })
        continue
      }
      nextAvailableAtMs = scheduledAtMs + recovery
      previousAssignment = assignment
    }
  }
  return conflicts
}

function analyzeTarget(
  snapshot: PlanSnapshot,
  mechanic: TimelineMechanic,
  targetTrack: ExecutionTrack,
  abilityById: Map<number, AbilityDefinition>,
): TargetEstimate | null {
  const profile = mechanic.damageProfile
  if (!profile) return null

  const effects: MitigationEffectProfile[] = []
  const notices: string[] = []
  const includedActionIds = new Set<number>()
  let specialCase = false

  for (const assignment of snapshot.assignments) {
    const ability = abilityById.get(assignment.actionId)
    if (!isRelevantToImpact(assignment, ability, mechanic)) continue
    if (!ability) {
      notices.push(`Action ${assignment.actionId} 未加载技能资料，未计入。`)
      continue
    }
    const assignmentTrack = snapshot.tracks.find((track) => track.trackId === assignment.trackId)
    if (!abilityFitsTrack(ability, assignmentTrack)) {
      notices.push(`${ability.name} 与执行轨道职业不匹配，未计入。`)
      continue
    }
    if (!coversImpact(ability, assignment, mechanic)) {
      if (isPostImpactSupportAssignment(ability, assignment, mechanic)) {
        notices.push(`${ability.name} 安排在伤害判定后，用于抬血/恢复，未计入本次命中减伤。`)
      } else {
        notices.push(`${ability.name} 的持续时间无法覆盖本次命中，未计入。`)
      }
      continue
    }

    const effect = ability.effect
    switch (effect.scope) {
      case 'SELF':
        if (assignment.trackId === targetTrack.trackId) addEffectOnce(assignment.actionId, effect, effects, includedActionIds, notices)
        break
      case 'TARGET':
        if (assignment.targetTrackId === targetTrack.trackId) addEffectOnce(assignment.actionId, effect, effects, includedActionIds, notices)
        else if (!assignment.targetTrackId) notices.push(`${ability.name} 未指定单体目标轨道，未计入。`)
        break
      case 'PARTY':
      case 'GROUND_AREA':
      case 'ENEMY_TARGET':
      case 'ENEMY_AREA':
        addEffectOnce(assignment.actionId, effect, effects, includedActionIds, notices)
        break
      default:
        notices.push(`${ability.name} 的作用范围未知，未计入。`)
        break
    }
  }

  const selectedEffects = selectStackingGroups(effects, mechanic.damageType, notices)
  let multiplier = 1
  for (const effect of selectedEffects) {
    if (effect.invulnerability) {
      specialCase = true
      notices.push('存在无敌技能，需人工确认该机制是否可被无敌处理。')
      continue
    }
    const reduction = reductionFor(effect, mechanic.damageType)
    if (reduction > 0) multiplier *= (100 - reduction) / 100
    if (effect.maximumHpBarrierPercent || effect.maximumHpIncreasePercent) {
      notices.push('存在最大生命/最大生命护盾效果，当前只提示、不从伤害数字中扣除。')
    }
    if (effect.barrierCurePotency) {
      notices.push(`存在治疗威力护盾（${effect.barrierCurePotency} 威力），未计入数值。`)
    }
    if (effect.calculationReadiness === 'NO_DIRECT_MITIGATION') {
      notices.push('存在治疗或增疗效果，未计入命中前实际治疗量。')
    }
  }

  const damageAfterMitigation = Math.ceil(profile.amount * multiplier)
  return {
    track: targetTrack,
    damageAfterMitigation,
    modeledReduction: 1 - multiplier,
    specialCase,
    notices,
  }
}

function isRelevantToImpact(
  assignment: Assignment,
  ability: AbilityDefinition | undefined,
  mechanic: TimelineMechanic,
): boolean {
  if (assignment.mechanicId === mechanic.mechanicId || assignment.impactAtMs === mechanic.plannedAtMs) return true
  if (!ability || ability.durationMs <= 0) return false
  return assignment.earliestUseAtMs <= mechanic.plannedAtMs
    && assignment.latestUseAtMs <= mechanic.plannedAtMs
    && assignment.earliestUseAtMs + ability.durationMs >= mechanic.plannedAtMs
}

function coversImpact(ability: AbilityDefinition, assignment: Assignment, mechanic: TimelineMechanic): boolean {
  if (assignment.earliestUseAtMs > mechanic.plannedAtMs || assignment.latestUseAtMs > mechanic.plannedAtMs) return false
  return ability.durationMs > 0 && assignment.earliestUseAtMs + ability.durationMs >= mechanic.plannedAtMs
}

export function abilityRequiresImpactCoverage(
  ability: Pick<AbilityDefinition, 'effect'> | null | undefined,
): boolean {
  const effect = ability?.effect
  if (!effect) return true
  if (effect.allDamageReductionPercent
    || effect.physicalDamageReductionPercent
    || effect.magicalDamageReductionPercent
    || effect.maximumHpIncreasePercent
    || effect.maximumHpBarrierPercent
    || effect.barrierCurePotency
    || effect.invulnerability) {
    return true
  }
  const readiness = effect.calculationReadiness
  return readiness === 'DIRECT_REDUCTION'
    || readiness === 'MAX_HP_BARRIER'
    || readiness === 'INVULNERABILITY_SPECIAL_CASE'
}

export function isPostImpactSupportAssignment(
  ability: Pick<AbilityDefinition, 'effect'> | null | undefined,
  assignment: Pick<Assignment, 'earliestUseAtMs' | 'latestUseAtMs'>,
  mechanic: Pick<TimelineMechanic, 'plannedAtMs'>,
): boolean {
  return !abilityRequiresImpactCoverage(ability)
    && (assignment.earliestUseAtMs > mechanic.plannedAtMs || assignment.latestUseAtMs > mechanic.plannedAtMs)
}

function addEffectOnce(
  actionId: number,
  effect: MitigationEffectProfile,
  effects: MitigationEffectProfile[],
  includedActionIds: Set<number>,
  notices: string[],
) {
  if (includedActionIds.has(actionId)) {
    notices.push(`同一技能 ${actionId} 对同一目标只按一次效果计入。`)
    return
  }
  includedActionIds.add(actionId)
  effects.push(effect)
}

function selectStackingGroups(
  effects: MitigationEffectProfile[],
  damageType: TimelineMechanic['damageType'],
  notices: string[],
): MitigationEffectProfile[] {
  const selected: MitigationEffectProfile[] = []
  const grouped = new Map<string, MitigationEffectProfile[]>()
  for (const effect of effects) {
    if (!effect.stackingGroup) selected.push(effect)
    else grouped.set(effect.stackingGroup, [...(grouped.get(effect.stackingGroup) ?? []), effect])
  }
  for (const [group, candidates] of grouped) {
    candidates.sort((left, right) => reductionFor(right, damageType) - reductionFor(left, damageType))
    selected.push(candidates[0]!)
    if (candidates.length > 1) notices.push(`不可叠加组 ${group} 同时存在 ${candidates.length} 个效果，仅计入最强项。`)
  }
  return selected
}

function reductionFor(effect: MitigationEffectProfile, damageType: TimelineMechanic['damageType']): number {
  const specific = damageType === 'PHYSICAL'
    ? effect.physicalDamageReductionPercent
    : damageType === 'MAGICAL' ? effect.magicalDamageReductionPercent : 0
  return Math.max(effect.allDamageReductionPercent, specific)
}

function targetTracks(snapshot: PlanSnapshot, mechanic: TimelineMechanic): ExecutionTrack[] {
  if (mechanic.type !== 'TANK_BUSTER' && attackClass(mechanic) !== 'AUTO_ATTACK') return snapshot.tracks
  const tanks = snapshot.tracks.filter((track) => ['MT', 'ST', 'T1'].includes(track.slot))
  return tanks.length ? tanks : snapshot.tracks
}

function riskLevel(mechanic: TimelineMechanic, damageAfterMitigation: number): DamageEstimate['riskLevel'] {
  if (mechanic.type === 'RAIDWIDE') {
    if (damageAfterMitigation <= 100_000) return 'GREEN'
    return damageAfterMitigation <= 190_000 ? 'YELLOW' : 'RED'
  }
  if (mechanic.type === 'TANK_BUSTER') {
    if (damageAfterMitigation <= 200_000) return 'GREEN'
    return damageAfterMitigation < 290_000 ? 'YELLOW' : 'RED'
  }
  return 'UNCLASSIFIED'
}

function calibrationRequired(mechanicId: string): DamageEstimate {
  return {
    mechanicId,
    status: 'CALIBRATION_REQUIRED',
    baselineDamage: null,
    damageAfterMitigation: null,
    modeledReduction: null,
    riskLevel: 'CALIBRATION_REQUIRED',
    worstTrackId: null,
    worstTrackSlot: null,
    sampleCount: null,
    statistic: null,
    source: null,
    notices: ['该机制尚无可追溯的基准总伤害。'],
  }
}

function unique(values: string[]): string[] {
  return [...new Set(values)]
}

interface TargetEstimate {
  track: ExecutionTrack
  damageAfterMitigation: number
  modeledReduction: number
  specialCase: boolean
  notices: string[]
}
