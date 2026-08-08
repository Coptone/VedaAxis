import { describe, expect, it } from 'vitest'
import type { AbilityDefinition, PlanSnapshot } from '../types/domain'
import {
  abilityFitsTrack,
  assignmentsCoveringMechanic,
  localCooldownConflicts,
  previewDamageEstimatesLocally,
} from './damageEstimates'

const reprisal: AbilityDefinition = {
  actionId: 7535,
  name: '雪仇 / Reprisal',
  iconPath: 'ui/icon/000000/000806.tex',
  jobIds: [19, 21, 32, 37],
  cooldownMs: 60_000,
  maxCharges: 1,
  durationMs: 15_000,
  confirmationStrategy: 'STATUS_APPLY',
  source: 'test',
  confidence: 'REVIEWED',
  castCategory: 'OGCD',
  effect: {
    scope: 'ENEMY_AREA',
    allDamageReductionPercent: 10,
    physicalDamageReductionPercent: 0,
    magicalDamageReductionPercent: 0,
    maximumHpIncreasePercent: 0,
    maximumHpBarrierPercent: 0,
    barrierCurePotency: 0,
    invulnerability: false,
    stackingGroup: '',
    calculationReadiness: 'DIRECT_REDUCTION',
    conditions: [],
    source: 'test',
    confidence: 'REVIEWED',
  },
}

const physis: AbilityDefinition = {
  ...reprisal,
  actionId: 24302,
  name: '魂灵风息 / Physis II',
  cooldownMs: 60_000,
  durationMs: 15_000,
  effect: {
    ...reprisal.effect,
    scope: 'PARTY',
    allDamageReductionPercent: 0,
    calculationReadiness: 'NO_DIRECT_MITIGATION',
  },
}

const rampart: AbilityDefinition = {
  ...reprisal,
  actionId: 7531,
  name: '铁壁 / Rampart',
  jobIds: [21],
  durationMs: 20_000,
  effect: {
    ...reprisal.effect,
    scope: 'SELF',
    allDamageReductionPercent: 20,
  },
}

function basePlan(): PlanSnapshot {
  const firstMechanicId = '10000000-0000-4000-8000-000000000001'
  const secondMechanicId = '10000000-0000-4000-8000-000000000002'
  const mtTrackId = '20000000-0000-4000-8000-000000000001'
  const h1TrackId = '20000000-0000-4000-8000-000000000002'
  return {
    schemaVersion: '1.3',
    minimumPluginVersion: '0.1.7',
    planId: '30000000-0000-4000-8000-000000000001',
    planVersion: 1,
    timelineId: '40000000-0000-4000-8000-000000000001',
    timelineVersion: 1,
    encounterId: '50000000-0000-4000-8000-000000000001',
    territoryId: 1363,
    strategyTag: 'TEST',
    trackMode: 'EIGHT',
    source: { kind: 'PERSONAL', reference: null, confidence: 'UNVERIFIED' },
    phases: [],
    mechanics: [
      {
        mechanicId: firstMechanicId,
        externalId: null,
        phase: 'P1',
        name: 'AOE',
        plannedAtMs: 30_000,
        durationMs: 0,
        type: 'RAIDWIDE',
        damageType: 'MAGICAL',
        target: '全体',
        actionId: 1,
        confidence: 'POC_PENDING',
        damageProfile: {
          amount: 100_000,
          basis: 'OBSERVED_TARGET_ADJUSTED',
          sampleCount: 6,
          statistic: 'P95',
          source: 'test',
          confidence: 'POC_PENDING',
        },
      },
      {
        mechanicId: secondMechanicId,
        externalId: null,
        phase: 'P1',
        name: '死刑',
        plannedAtMs: 35_000,
        durationMs: 0,
        type: 'TANK_BUSTER',
        damageType: 'MAGICAL',
        target: '坦克',
        actionId: 2,
        confidence: 'POC_PENDING',
        damageProfile: {
          amount: 200_000,
          basis: 'OBSERVED_TARGET_ADJUSTED',
          sampleCount: 6,
          statistic: 'P95',
          source: 'test',
          confidence: 'POC_PENDING',
        },
      },
    ],
    anchors: [],
    tracks: [
      { trackId: mtTrackId, slot: 'MT', allowedJobIds: [21], displayName: 'MT' },
      { trackId: h1TrackId, slot: 'H1', allowedJobIds: [24], displayName: 'H1' },
    ],
    assignments: [
      {
        assignmentId: '60000000-0000-4000-8000-000000000001',
        mechanicId: firstMechanicId,
        trackId: mtTrackId,
        actionId: 7535,
        anchorId: null,
        targetTrackId: null,
        highlightAtMs: 22_000,
        earliestUseAtMs: 25_000,
        latestUseAtMs: 29_000,
        impactAtMs: 30_000,
        locked: false,
        confirmationStrategy: 'STATUS_APPLY',
        fallbacks: [],
      },
    ],
  }
}

describe('local damage estimates', () => {
  it('uses one lasting mitigation assignment for multiple later mechanics', () => {
    const plan = basePlan()
    const estimates = previewDamageEstimatesLocally(plan, [reprisal])

    expect(estimates[0]!.damageAfterMitigation).toBe(90_000)
    expect(estimates[1]!.damageAfterMitigation).toBe(180_000)

    const carried = assignmentsCoveringMechanic(plan, [reprisal], plan.mechanics[1]!)
    expect(carried).toHaveLength(1)
    expect(carried[0]!.carriedFromAnotherMechanic).toBe(true)
    expect(carried[0]!.coversUntilMs).toBe(40_000)
  })

  it('uses assignment adjudication time and current-main-tank targeting for auto attacks', () => {
    const plan = basePlan()
    const stTrackId = '20000000-0000-4000-8000-000000000003'
    plan.tracks.push({ trackId: stTrackId, slot: 'ST', allowedJobIds: [32], displayName: 'ST' })
    plan.mechanics = [{
      ...plan.mechanics[0]!,
      name: '攻击 x4',
      plannedAtMs: 1_000,
      type: 'MECHANIC',
      damageType: 'PHYSICAL',
      target: '当前一仇',
      damageProfile: {
        ...plan.mechanics[0]!.damageProfile!,
        amount: 100_000,
      },
    }]
    plan.assignments = [{
      ...plan.assignments[0]!,
      actionId: 7531,
      earliestUseAtMs: 4_000,
      latestUseAtMs: 6_000,
      impactAtMs: 6_000,
    }]

    const estimates = previewDamageEstimatesLocally(plan, [rampart])

    expect(estimates[0]!.damageAfterMitigation).toBe(80_000)
    expect(estimates[0]!.modeledReduction).toBeCloseTo(0.2)
    expect(estimates[0]!.worstTrackSlot).toBe('MT')
  })

  it('keeps explicit off-tank targets on the off-tank track', () => {
    const plan = basePlan()
    const stTrackId = '20000000-0000-4000-8000-000000000003'
    plan.tracks.push({ trackId: stTrackId, slot: 'ST', allowedJobIds: [32], displayName: 'ST' })
    plan.mechanics = [{
      ...plan.mechanics[0]!,
      name: '攻击 x4',
      plannedAtMs: 1_000,
      type: 'MECHANIC',
      damageType: 'PHYSICAL',
      target: 'ST',
      damageProfile: {
        ...plan.mechanics[0]!.damageProfile!,
        amount: 100_000,
      },
    }]
    plan.assignments = [{
      ...plan.assignments[0]!,
      actionId: 7531,
      earliestUseAtMs: 4_000,
      latestUseAtMs: 6_000,
      impactAtMs: 6_000,
    }]

    const estimates = previewDamageEstimatesLocally(plan, [rampart])

    expect(estimates[0]!.damageAfterMitigation).toBe(100_000)
    expect(estimates[0]!.modeledReduction).toBe(0)
    expect(estimates[0]!.worstTrackSlot).toBe('ST')
  })

  it('filters abilities by the selected execution track job', () => {
    const plan = basePlan()
    const pictomancerBarrier = { ...reprisal, actionId: 34685, name: '坦培拉涂层 / Tempera Coat', jobIds: [42] }
    const machinistMitigation = { ...reprisal, actionId: 16889, name: '策动 / Tactician', jobIds: [31] }
    expect(abilityFitsTrack(reprisal, plan.tracks[0])).toBe(true)
    expect(abilityFitsTrack(reprisal, plan.tracks[1])).toBe(false)
    expect(abilityFitsTrack(pictomancerBarrier, plan.tracks[0])).toBe(false)
    expect(abilityFitsTrack(machinistMitigation, { trackId: 'd3', slot: 'D3', allowedJobIds: [31], displayName: 'MCH' })).toBe(true)
  })

  it('reports local cooldown conflicts with the same track and action', () => {
    const plan = basePlan()
    plan.assignments.push({
      ...plan.assignments[0]!,
      assignmentId: '60000000-0000-4000-8000-000000000002',
      mechanicId: plan.mechanics[1]!.mechanicId,
      earliestUseAtMs: 35_000,
      latestUseAtMs: 36_000,
      impactAtMs: 35_000,
    })

    expect(localCooldownConflicts(plan, [reprisal])).toEqual([
      expect.objectContaining({
        assignmentId: '60000000-0000-4000-8000-000000000002',
        abilityName: '雪仇 / Reprisal',
        availableAtMs: 85_000,
      }),
    ])
  })

  it('allows post-impact healing support without reducing the current hit', () => {
    const plan = basePlan()
    plan.assignments = [{
      ...plan.assignments[0]!,
      actionId: 24302,
      highlightAtMs: 27_000,
      earliestUseAtMs: 30_500,
      latestUseAtMs: 36_000,
      impactAtMs: 30_000,
    }]

    const estimates = previewDamageEstimatesLocally(plan, [physis])

    expect(estimates[0]!.damageAfterMitigation).toBe(100_000)
    expect(estimates[0]!.notices.join('\n')).toContain('安排在伤害判定后')
  })
})
