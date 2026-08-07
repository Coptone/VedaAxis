import type { AbilityDefinition, MitigationEffectProfile } from '../types/domain'

export type AbilityPlanningCategory =
  | 'SINGLE_MITIGATION'
  | 'RAID_MITIGATION'
  | 'RAID_HEALING'
  | 'SPECIAL_SUPPORT'
  | 'UNMODELED'

export const ABILITY_CATEGORY_ORDER: AbilityPlanningCategory[] = [
  'SINGLE_MITIGATION',
  'RAID_MITIGATION',
  'RAID_HEALING',
  'SPECIAL_SUPPORT',
  'UNMODELED',
]

export const ABILITY_CATEGORY_LABELS: Record<AbilityPlanningCategory, string> = {
  SINGLE_MITIGATION: '单减',
  RAID_MITIGATION: '团减',
  RAID_HEALING: '团血',
  SPECIAL_SUPPORT: '特殊',
  UNMODELED: '待复核',
}

export function abilityPlanningCategory(
  ability: Pick<AbilityDefinition, 'effect'> | null | undefined,
): AbilityPlanningCategory {
  const effect = ability?.effect
  if (!effect) return 'UNMODELED'
  if (effect.invulnerability || effect.calculationReadiness === 'INVULNERABILITY_SPECIAL_CASE') {
    return effect.scope === 'PARTY' || effect.scope === 'GROUND_AREA' ? 'SPECIAL_SUPPORT' : 'SINGLE_MITIGATION'
  }
  if (hasDirectReduction(effect) || hasBarrierOrMaxHp(effect)) {
    return isGroupOrEnemyScope(effect.scope) ? 'RAID_MITIGATION' : 'SINGLE_MITIGATION'
  }
  if (isHealingOrSupport(effect)) {
    return isGroupOrEnemyScope(effect.scope) ? 'RAID_HEALING' : 'SINGLE_MITIGATION'
  }
  return effect.scope === 'PARTY' || effect.scope === 'GROUND_AREA' ? 'SPECIAL_SUPPORT' : 'UNMODELED'
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

function isHealingOrSupport(effect: MitigationEffectProfile): boolean {
  return effect.calculationReadiness === 'NO_DIRECT_MITIGATION'
    || effect.calculationReadiness === 'REQUIRES_HEALING_STATS'
}

function isGroupOrEnemyScope(effectScope: MitigationEffectProfile['scope']): boolean {
  return effectScope === 'PARTY'
    || effectScope === 'GROUND_AREA'
    || effectScope === 'ENEMY_TARGET'
    || effectScope === 'ENEMY_AREA'
}
