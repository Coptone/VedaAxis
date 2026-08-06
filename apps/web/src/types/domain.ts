export type TrackMode = 'FOUR' | 'EIGHT'
export type TrackSlot = 'T1' | 'MT' | 'ST' | 'H1' | 'H2' | 'D1' | 'D2' | 'D3' | 'D4'
export type Confidence = 'POC_PENDING' | 'UNVERIFIED' | 'REVIEWED' | 'VERIFIED'
export type ConfirmationStrategy = 'ACTION_EFFECT' | 'STATUS_APPLY' | 'COOLDOWN_CHANGE' | 'COMPOSITE'
export type PhaseTimingMode = 'ABSOLUTE' | 'RELATIVE'

export const TRACK_SLOTS: Record<TrackMode, TrackSlot[]> = {
  FOUR: ['T1', 'H1', 'D1', 'D2'],
  EIGHT: ['MT', 'ST', 'H1', 'H2', 'D1', 'D2', 'D3', 'D4'],
}

export interface PlanSource {
  kind: 'PERSONAL' | 'PUBLIC_TEMPLATE' | 'AI_CANDIDATE' | 'IMPORTED'
  reference: string | null
  confidence: Confidence
}

export interface ExecutionTrack {
  trackId: string
  slot: TrackSlot
  allowedJobIds: number[]
  displayName: string | null
}

export interface Assignment {
  assignmentId: string
  mechanicId: string
  trackId: string
  actionId: number
  anchorId: string | null
  targetTrackId?: string | null
  highlightAtMs: number
  earliestUseAtMs: number
  latestUseAtMs: number
  impactAtMs: number
  locked: boolean
  confirmationStrategy: ConfirmationStrategy
  fallbacks: Array<{ trackId: string; actionId: number }>
}

export interface PlanSnapshot {
  schemaVersion: '1.0' | '1.1' | '1.2' | '1.3'
  minimumPluginVersion: string
  planId: string
  planVersion: number
  timelineId: string
  timelineVersion: number
  encounterId: string
  territoryId: number
  strategyTag: string
  trackMode: TrackMode
  source: PlanSource
  phases: TimelinePhase[]
  mechanics: TimelineMechanic[]
  anchors: TimelineAnchor[]
  tracks: ExecutionTrack[]
  assignments: Assignment[]
}

export interface TimelinePhase {
  phaseId: string
  externalId: string | null
  name: string
  plannedAtMs: number
  durationMs?: number
  timingMode?: PhaseTimingMode
  confidence: Confidence
}

export interface TimelineMechanic {
  mechanicId: string
  externalId: string | null
  phase: string
  name: string
  plannedAtMs: number
  durationMs: number
  type: 'MECHANIC' | 'TANK_BUSTER' | 'RAIDWIDE'
  damageType: 'UNKNOWN' | 'MAGICAL' | 'PHYSICAL' | 'SPECIAL'
  target: string
  actionId: number | null
  confidence: Confidence
  damageProfile?: DamageProfile | null
}

export interface DamageProfile {
  amount: number
  basis: 'OBSERVED_TARGET_ADJUSTED' | 'FORMULA_VERIFIED'
  sampleCount: number
  statistic: 'MAX_OBSERVED' | 'P95' | 'EXPECTED'
  source: string
  confidence: Confidence
}

export interface SurvivabilityRequest {
  targetTrackId: string
  currentHp: number
  maximumHp: number
  partyRangeConfirmed: boolean
  enemyEffectConfirmed: boolean
}

export interface SurvivabilityAnalysis {
  status: 'CALIBRATION_REQUIRED' | 'SURVIVES_WITH_MODELED_EFFECTS' | 'INSUFFICIENT_WITH_MODELED_EFFECTS' | 'SPECIAL_CASE_REVIEW_REQUIRED'
  hardGuarantee: boolean
  incomingDamage: number | null
  damageAfterMitigation: number | null
  effectiveHp: number | null
  remainingHp: number | null
  modeledReduction: number | null
  notices: string[]
}

export type DamageRiskLevel = 'GREEN' | 'YELLOW' | 'RED' | 'UNCLASSIFIED' | 'CALIBRATION_REQUIRED'

export interface DamageEstimate {
  mechanicId: string
  status: 'CALIBRATION_REQUIRED' | 'CALCULATED' | 'SPECIAL_CASE_REVIEW_REQUIRED'
  baselineDamage: number | null
  damageAfterMitigation: number | null
  modeledReduction: number | null
  riskLevel: DamageRiskLevel
  worstTrackId: string | null
  worstTrackSlot: TrackSlot | null
  sampleCount: number | null
  statistic: DamageProfile['statistic'] | null
  source: string | null
  notices: string[]
}

export interface TimelineAnchor {
  anchorId: string
  actionId: number
  occurrence: number
  plannedAtMs: number
  offsetMs: number
  phase: string
  kind: 'CAST_START' | 'ACTION_EFFECT' | 'STATUS_GAIN'
}

export interface AbilityDefinition {
  actionId: number
  name: string
  iconPath: string
  jobIds: number[]
  cooldownMs: number
  maxCharges: number
  durationMs: number
  confirmationStrategy: ConfirmationStrategy
  source: string
  confidence: string
  effect: MitigationEffectProfile
}

export interface MitigationEffectProfile {
  scope: 'SELF' | 'TARGET' | 'PARTY' | 'GROUND_AREA' | 'ENEMY_TARGET' | 'ENEMY_AREA' | 'UNKNOWN'
  allDamageReductionPercent: number
  physicalDamageReductionPercent: number
  magicalDamageReductionPercent: number
  maximumHpIncreasePercent: number
  maximumHpBarrierPercent: number
  barrierCurePotency: number
  invulnerability: boolean
  stackingGroup: string
  calculationReadiness: 'DIRECT_REDUCTION' | 'MAX_HP_BARRIER' | 'REQUIRES_HEALING_STATS' | 'INVULNERABILITY_SPECIAL_CASE' | 'NO_DIRECT_MITIGATION' | 'UNMODELED'
  conditions: string[]
  source: string
  confidence: Confidence
}

export interface RuleIssue {
  severity: 'ERROR' | 'WARNING'
  code: string
  message: string
  reference: string
}

export interface RuleValidationResult {
  valid: boolean
  issues: RuleIssue[]
}

export interface AiCandidate {
  schemaVersion: '1.0'
  candidateId: string
  basePlanId: string
  assignments: Assignment[]
  reasons: string[]
  warnings: string[]
  confidence: 'UNVERIFIED' | 'RULE_VALIDATED'
  provider: string
  model: string
  generatedAt: string
  validation: RuleValidationResult
}

export interface PlanSummary {
  id: string
  name: string
  encounterId: string
  territoryId: number
  strategyTag: string
  trackMode: TrackMode
  latestVersion: number
  updatedAt: string
}

export interface PlanDetails {
  plan: {
    id: string
    name: string
    latestVersion: number
    updatedAt: string
  }
  snapshot: PlanSnapshot
}

export interface CooldownWindow {
  spellId: number
  spellName: string
  category: 'RAID_MIT' | 'SINGLE_MIT'
  phase: string
  occurrence: number
  sampleCount: number
  medianPhaseTimeMs: number
  p25PhaseTimeMs: number
  p75PhaseTimeMs: number
  confidence: Confidence
}

export interface TimelineImportCandidate {
  schemaVersion: '1.0'
  sourceUrl: string
  bossSlug: string
  specSlug: string
  bossDataUrl: string
  rankingDataUrl: string | null
  fetchedAt: string
  phases: TimelinePhase[]
  mechanics: TimelineMechanic[]
  recommendations: CooldownWindow[]
  stats: {
    bossEventCount: number
    phaseCount: number
    mechanicCount: number
    actionIdCount: number
    reportCount: number
    anonymizedCastCount: number
    recommendationCount: number
  }
  warnings: string[]
}

export interface ExecutionSummary {
  fightExecutionId: string
  planId: string
  planVersion: number
  result: 'CLEAR' | 'WIPE' | 'ABANDONED'
  startedAt: string
  endedAt: string
  uploadedAt: string
}

export interface ExecutionStats {
  fights: number
  clears: number
  wipes: number
  assignments: number
  stateCounts: Partial<Record<'SUCCESS' | 'EARLY' | 'MISSED' | 'LATE' | 'INVALID' | 'CANCELLED', number>>
  averageObservedOffsetMs: number | null
}
