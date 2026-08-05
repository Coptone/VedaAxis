import type { PlanSnapshot, TimelineImportCandidate } from '../types/domain'

export function applyTimelineImport(
  snapshot: PlanSnapshot,
  candidate: TimelineImportCandidate,
  timelineId: string,
): PlanSnapshot {
  return {
    ...structuredClone(snapshot),
    schemaVersion: '1.2',
    minimumPluginVersion: '0.1.5',
    timelineId,
    timelineVersion: snapshot.timelineVersion + 1,
    source: {
      kind: 'IMPORTED',
      reference: candidate.sourceUrl,
      confidence: 'POC_PENDING',
    },
    phases: structuredClone(candidate.phases),
    mechanics: structuredClone(candidate.mechanics),
    anchors: [],
    assignments: [],
  }
}
