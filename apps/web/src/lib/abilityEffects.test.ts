import { describe, expect, it } from 'vitest'
import { abilityEffectSummary } from './abilityEffects'

describe('ability effect summary', () => {
  it('preserves physical and magical split reductions', () => {
    expect(abilityEffectSummary({ effect: {
      allDamageReductionPercent: 0,
      physicalDamageReductionPercent: 10,
      magicalDamageReductionPercent: 5,
      maximumHpBarrierPercent: 0,
      maximumHpIncreasePercent: 0,
      invulnerability: false,
      barrierCurePotency: 0,
    } })).toBe('物理 -10% · 魔法 -5%')
  })

  it('does not misrepresent healing-only actions as mitigation', () => {
    expect(abilityEffectSummary({ effect: {
      allDamageReductionPercent: 0,
      physicalDamageReductionPercent: 0,
      magicalDamageReductionPercent: 0,
      maximumHpBarrierPercent: 0,
      maximumHpIncreasePercent: 0,
      invulnerability: false,
      barrierCurePotency: 0,
    } })).toContain('不直接减伤')
  })

  it('shows both direct mitigation and the unmodeled potency barrier', () => {
    expect(abilityEffectSummary({ effect: {
      allDamageReductionPercent: 10,
      physicalDamageReductionPercent: 0,
      magicalDamageReductionPercent: 0,
      maximumHpBarrierPercent: 0,
      maximumHpIncreasePercent: 0,
      invulnerability: false,
      barrierCurePotency: 300,
    } })).toBe('全伤害 -10% · 治疗量护盾（300 威力）')
  })
})
