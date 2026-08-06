import { describe, expect, it } from 'vitest'
import { applyTimelineImport } from './timelineImports'
import type { PlanSnapshot, TimelineImportCandidate } from '../types/domain'

describe('applyTimelineImport', () => {
  it('requires an explicit caller and preserves the track mode while invalidating old assignments', () => {
    const snapshot = {
      schemaVersion: '1.1', minimumPluginVersion: '0.1.4', planId: 'plan', planVersion: 3,
      timelineId: 'old-timeline', timelineVersion: 7, encounterId: 'encounter', territoryId: 755,
      strategyTag: 'O8S-POC', trackMode: 'FOUR',
      source: { kind: 'PERSONAL', reference: null, confidence: 'UNVERIFIED' },
      phases: [], mechanics: [], anchors: [{ anchorId: 'a', actionId: 1, occurrence: 1, plannedAtMs: 0, offsetMs: 0, phase: 'P1', kind: 'CAST_START' }],
      tracks: [{ trackId: 'track', slot: 'T1', allowedJobIds: [], displayName: 'T1' }],
      assignments: [{ assignmentId: 'assignment', mechanicId: 'old', trackId: 'track', actionId: 1, anchorId: 'a', highlightAtMs: 0, earliestUseAtMs: 1, latestUseAtMs: 2, impactAtMs: 3, locked: false, confirmationStrategy: 'ACTION_EFFECT', fallbacks: [] }],
    } satisfies PlanSnapshot
    const candidate = {
      schemaVersion: '1.0', sourceUrl: 'https://raalm.com/m-spec/timelinev2.html?boss=dancing-mad&spec=sage-sage',
      bossSlug: 'dancing-mad', specSlug: 'sage-sage', bossDataUrl: 'boss', rankingDataUrl: null,
      fetchedAt: '2026-08-05T00:00:00Z',
      phases: [{ phaseId: 'p1', externalId: 'DM_P1', name: 'P1', plannedAtMs: 0, confidence: 'POC_PENDING' }],
      mechanics: [{ mechanicId: 'm1', externalId: 'dmu-1', phase: 'P1', name: '机制', plannedAtMs: 15_000, durationMs: 5_000, type: 'RAIDWIDE', damageType: 'UNKNOWN', target: '全体', actionId: null, confidence: 'POC_PENDING' }],
      recommendations: [],
      stats: { bossEventCount: 2, phaseCount: 1, mechanicCount: 1, actionIdCount: 0, reportCount: 0, anonymizedCastCount: 0, recommendationCount: 0 },
      warnings: [],
    } satisfies TimelineImportCandidate

    const applied = applyTimelineImport(snapshot, candidate, 'new-timeline')

    expect(applied.trackMode).toBe('FOUR')
    expect(applied.tracks).toEqual(snapshot.tracks)
    expect(applied.timelineVersion).toBe(8)
    expect(applied.schemaVersion).toBe('1.3')
    expect(applied.phases[0]).toMatchObject({ durationMs: 20_000, timingMode: 'ABSOLUTE' })
    expect(applied.mechanics[0]?.mechanicId).toBe('m1')
    expect(applied.anchors).toEqual([])
    expect(applied.assignments).toEqual([])
    expect(applied.source).toEqual({ kind: 'IMPORTED', reference: candidate.sourceUrl, confidence: 'POC_PENDING' })
    expect(snapshot.assignments).toHaveLength(1)
  })
})
