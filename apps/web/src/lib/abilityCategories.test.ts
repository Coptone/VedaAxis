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
  it('prioritizes direct mitigation even when a skill also has healing text', () => {
    const holos = ability({
      allDamageReductionPercent: 10,
      barrierCurePotency: 300,
      calculationReadiness: 'REQUIRES_HEALING_STATS',
    })

    expect(abilityPlanningCategory(holos)).toBe('DIRECT_MITIGATION')
    expect(abilityPlanningCategoryLabel(holos)).toBe('直接减伤')
  })

  it('classifies Ixochole-like pure healing as a healing planning action', () => {
    const ixochole = ability({
      scope: 'PARTY',
      calculationReadiness: 'NO_DIRECT_MITIGATION',
    })

    expect(abilityPlanningCategory(ixochole)).toBe('HEALING_OR_HEALING_BUFF')
    expect(abilityPlanningCategoryLabel(ixochole)).toContain('治疗')
  })

  it('separates shields and invulnerability from normal healing', () => {
    expect(abilityPlanningCategory(ability({
      barrierCurePotency: 320,
      calculationReadiness: 'REQUIRES_HEALING_STATS',
    }))).toBe('BARRIER_OR_MAX_HP')
    expect(abilityPlanningCategory(ability({
      invulnerability: true,
      calculationReadiness: 'INVULNERABILITY_SPECIAL_CASE',
    }))).toBe('INVULNERABILITY_OR_SPECIAL')
  })
})
