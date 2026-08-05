export type TrackMode = 'FOUR' | 'EIGHT'
export type TrackSlot = 'T1' | 'MT' | 'ST' | 'H1' | 'H2' | 'D1' | 'D2' | 'D3' | 'D4'
export type Confidence = 'POC_PENDING' | 'UNVERIFIED' | 'REVIEWED' | 'VERIFIED'
export type ConfirmationStrategy = 'ACTION_EFFECT' | 'STATUS_APPLY' | 'COOLDOWN_CHANGE' | 'COMPOSITE'

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
  highlightAtMs: number
  earliestUseAtMs: number
  latestUseAtMs: number
  impactAtMs: number
  locked: boolean
  confirmationStrategy: ConfirmationStrategy
  fallbacks: Array<{ trackId: string; actionId: number }>
}

export interface PlanSnapshot {
  schemaVersion: '1.0'
  minimumPluginVersion: string
  planId: string
  planVersion: number
  timelineId: string
  timelineVersion: number
  encounterId: string
  strategyTag: string
  trackMode: TrackMode
  source: PlanSource
  anchors: TimelineAnchor[]
  tracks: ExecutionTrack[]
  assignments: Assignment[]
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
  jobIds: number[]
  cooldownMs: number
  maxCharges: number
  durationMs: number
  confirmationStrategy: ConfirmationStrategy
  source: string
  confidence: string
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

export interface Mechanic {
  id: string
  phase: string
  name: string
  timeMs: number
  damageType: '魔法' | '物理' | '特殊'
  target: string
  confidence: Confidence
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
