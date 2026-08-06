import type { AbilityDefinition, MitigationEffectProfile } from '../types/domain'

export type AbilityPlanningCategory =
  | 'DIRECT_MITIGATION'
  | 'BARRIER_OR_MAX_HP'
  | 'HEALING_OR_HEALING_BUFF'
  | 'INVULNERABILITY_OR_SPECIAL'
  | 'UNMODELED'

export const ABILITY_CATEGORY_ORDER: AbilityPlanningCategory[] = [
  'DIRECT_MITIGATION',
  'BARRIER_OR_MAX_HP',
  'HEALING_OR_HEALING_BUFF',
  'INVULNERABILITY_OR_SPECIAL',
  'UNMODELED',
]

export const ABILITY_CATEGORY_LABELS: Record<AbilityPlanningCategory, string> = {
  DIRECT_MITIGATION: '直接减伤',
  BARRIER_OR_MAX_HP: '护盾 / 最大生命',
  HEALING_OR_HEALING_BUFF: '治疗 / 增疗 / 资源',
  INVULNERABILITY_OR_SPECIAL: '无敌 / 特殊处理',
  UNMODELED: '未建模 / 需复核',
}

export function abilityPlanningCategory(
  ability: Pick<AbilityDefinition, 'effect'> | null | undefined,
): AbilityPlanningCategory {
  const effect = ability?.effect
  if (!effect) return 'UNMODELED'
  if (effect.invulnerability || effect.calculationReadiness === 'INVULNERABILITY_SPECIAL_CASE') {
    return 'INVULNERABILITY_OR_SPECIAL'
  }
  if (hasDirectReduction(effect)) return 'DIRECT_MITIGATION'
  if (hasBarrierOrMaxHp(effect)) return 'BARRIER_OR_MAX_HP'
  if (effect.calculationReadiness === 'NO_DIRECT_MITIGATION' || effect.calculationReadiness === 'REQUIRES_HEALING_STATS') {
    return 'HEALING_OR_HEALING_BUFF'
  }
  return 'UNMODELED'
}

export function abilityPlanningCategoryLabel(
  ability: Pick<AbilityDefinition, 'effect'> | null | undefined,
): string {
  return ABILITY_CATEGORY_LABELS[abilityPlanningCategory(ability)]
}

function hasDirectReduction(effect: MitigationEffectProfile): boolean {
  return Boolean(
    effect.allDamageReductionPercent
    || effect.physicalDamageReductionPercent
    || effect.magicalDamageReductionPercent,
  )
}

function hasBarrierOrMaxHp(effect: MitigationEffectProfile): boolean {
  return Boolean(
    effect.maximumHpIncreasePercent
    || effect.maximumHpBarrierPercent
    || effect.barrierCurePotency,
  )
}
