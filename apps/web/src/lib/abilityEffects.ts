import type { AbilityDefinition } from '../types/domain'

export function abilityEffectSummary(ability: Pick<AbilityDefinition, 'effect'> | null | undefined): string {
  const effect = ability?.effect
  if (!effect) return '效果资料未加载；不会用于生存判定'

  const parts = [
    effect.allDamageReductionPercent ? `全伤害 -${effect.allDamageReductionPercent}%` : '',
    effect.physicalDamageReductionPercent ? `物理 -${effect.physicalDamageReductionPercent}%` : '',
    effect.magicalDamageReductionPercent ? `魔法 -${effect.magicalDamageReductionPercent}%` : '',
  ].filter(Boolean)
  if (effect.maximumHpBarrierPercent) parts.push(`护盾 ${effect.maximumHpBarrierPercent}% 最大生命`)
  if (effect.maximumHpIncreasePercent) parts.push(`最大生命 +${effect.maximumHpIncreasePercent}%`)
  if (effect.barrierCurePotency) parts.push(`治疗量护盾（${effect.barrierCurePotency} 威力）`)
  if (effect.invulnerability) parts.push('无敌：特殊机制需人工确认')
  return parts.length ? parts.join(' · ') : '不直接减伤；需与治疗/护盾联动计算'
}
