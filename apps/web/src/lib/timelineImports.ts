import type { PlanSnapshot, TimelineImportCandidate } from '../types/domain'
import { cloneData } from './cloneData'

export function applyTimelineImport(
  snapshot: PlanSnapshot,
  candidate: TimelineImportCandidate,
  timelineId: string,
): PlanSnapshot {
  return {
    ...cloneData(snapshot),
    schemaVersion: '1.2',
    minimumPluginVersion: '0.1.5',
    timelineId,
    timelineVersion: snapshot.timelineVersion + 1,
    source: {
      kind: 'IMPORTED',
      reference: candidate.sourceUrl,
      confidence: 'POC_PENDING',
    },
    phases: cloneData(candidate.phases),
    mechanics: cloneData(candidate.mechanics),
    anchors: [],
    assignments: [],
  }
}
