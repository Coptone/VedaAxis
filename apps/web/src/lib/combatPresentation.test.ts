import { describe, expect, it } from 'vitest'
import { attackClass, attackClassLabel, damageEstimateLabel, damageTypeLabel, hasDirectDamage } from './combatPresentation'

describe('combat presentation', () => {
  it('keeps explicit raidwides and tank busters ahead of name heuristics', () => {
    expect(attackClass({ type: 'RAIDWIDE', name: '攻击' })).toBe('AOE')
    expect(attackClass({ type: 'TANK_BUSTER', name: '攻击' })).toBe('TANK_BUSTER')
  })

  it('recognises only named auto attacks as 平A', () => {
    expect(attackClassLabel({ type: 'MECHANIC', name: '攻击 x4' })).toBe('平A')
    expect(attackClassLabel({ type: 'MECHANIC', name: '波动炮' })).toBe('机制伤害')
  })

  it('renders an explicit unknown damage attribute instead of guessing', () => {
    expect(damageTypeLabel('MAGICAL')).toBe('魔法')
    expect(damageTypeLabel('UNKNOWN')).toBe('属性待确认')
  })

  it('identifies observed values as calibration instead of a universal hit value', () => {
    expect(damageEstimateLabel({ type: 'RAIDWIDE', name: 'AOE', damageType: 'MAGICAL', damageProfile: {
      amount: 321_000,
      basis: 'OBSERVED_TARGET_ADJUSTED',
      sampleCount: 8,
      statistic: 'MAX_OBSERVED',
      source: 'test',
      confidence: 'POC_PENDING',
    } })).toBe('最大实测 321,000 · n=8 · 目标调整实测')
  })

  it('does not call non-damaging timeline markers uncalibrated damage', () => {
    const marker = { type: 'MECHANIC' as const, name: 'Boss回到场中', damageType: 'UNKNOWN' as const, damageProfile: null }
    expect(hasDirectDamage(marker)).toBe(false)
    expect(damageEstimateLabel(marker)).toBe('时间轴标记 · 无直接伤害')
  })

  it('keeps real attacks without a profile visibly pending', () => {
    const attack = { type: 'MECHANIC' as const, name: '攻击', damageType: 'PHYSICAL' as const, damageProfile: null }
    expect(hasDirectDamage(attack)).toBe(true)
    expect(damageEstimateLabel(attack)).toBe('伤害值待校准')
  })
})
