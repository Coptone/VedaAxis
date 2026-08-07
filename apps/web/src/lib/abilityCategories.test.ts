import { describe, expect, it } from 'vitest'
import type { AbilityDefinition } from '../types/domain'
import { abilityPlanningCategory, abilityPlanningCategoryLabel } from './abilityCategories'

function ability(partial: Partial<AbilityDefinition['effect']>): Pick<AbilityDefinition, 'effect'> {
  return {
    effect: {
      scope: 'PARTY',
      allDamageReductionPercent: 0,
      physicalDamageReductionPercent: 0,
      magicalDamageReductionPercent: 0,
      maximumHpIncreasePercent: 0,
      maximumHpBarrierPercent: 0,
      barrierCurePotency: 0,
      invulnerability: false,
      stackingGroup: '',
      calculationReadiness: 'NO_DIRECT_MITIGATION',
      conditions: [],
      source: 'test',
      confidence: 'REVIEWED',
      ...partial,
    },
  }
}

describe('ability planning categories', () => {
  it('puts party mitigation in the raid mitigation column even when a skill also has healing text', () => {
    const holos = ability({
      allDamageReductionPercent: 10,
      barrierCurePotency: 300,
      calculationReadiness: 'REQUIRES_HEALING_STATS',
    })

    expect(abilityPlanningCategory(holos)).toBe('RAID_MITIGATION')
    expect(abilityPlanningCategoryLabel(holos)).toBe('团减')
  })

  it('classifies Ixochole-like pure party healing as raid healing', () => {
    const ixochole = ability({
      scope: 'PARTY',
      calculationReadiness: 'NO_DIRECT_MITIGATION',
    })

    expect(abilityPlanningCategory(ixochole)).toBe('RAID_HEALING')
    expect(abilityPlanningCategoryLabel(ixochole)).toContain('团血')
  })

  it('puts target shields and invulnerability in the single mitigation column', () => {
    expect(abilityPlanningCategory(ability({
      scope: 'TARGET',
      barrierCurePotency: 320,
      calculationReadiness: 'REQUIRES_HEALING_STATS',
    }))).toBe('SINGLE_MITIGATION')
    expect(abilityPlanningCategory(ability({
      scope: 'SELF',
      invulnerability: true,
      calculationReadiness: 'INVULNERABILITY_SPECIAL_CASE',
    }))).toBe('SINGLE_MITIGATION')
  })
})
