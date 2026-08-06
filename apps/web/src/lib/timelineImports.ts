import type { PlanSnapshot, TimelineImportCandidate } from '../types/domain'
import { cloneData } from './cloneData'

export function applyTimelineImport(
  snapshot: PlanSnapshot,
  candidate: TimelineImportCandidate,
  timelineId: string,
): PlanSnapshot {
  const finalMechanicAtMs = candidate.mechanics.reduce(
    (latest, mechanic) => Math.max(latest, mechanic.plannedAtMs + mechanic.durationMs),
    0,
  )
  const phases = candidate.phases.map((phase, index) => ({
    ...phase,
    durationMs: Math.max(
      1_000,
      (candidate.phases[index + 1]?.plannedAtMs ?? finalMechanicAtMs) - phase.plannedAtMs,
    ),
    timingMode: 'ABSOLUTE' as const,
  }))
  return {
    ...cloneData(snapshot),
    schemaVersion: '1.3',
    minimumPluginVersion: '0.1.7',
    timelineId,
    timelineVersion: snapshot.timelineVersion + 1,
    source: {
      kind: 'IMPORTED',
      reference: candidate.sourceUrl,
      confidence: 'POC_PENDING',
    },
    phases: cloneData(phases),
    mechanics: cloneData(candidate.mechanics),
    anchors: [],
    assignments: [],
  }
}
