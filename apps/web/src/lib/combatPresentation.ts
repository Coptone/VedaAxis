import type { TimelineMechanic } from '../types/domain'

export type AttackClass = 'AOE' | 'AUTO_ATTACK' | 'TANK_BUSTER' | 'MECHANIC'

const ATTACK_CLASS_LABELS: Record<AttackClass, string> = {
  AOE: 'AOE',
  AUTO_ATTACK: '平A',
  TANK_BUSTER: '死刑',
  MECHANIC: '机制伤害',
}

const DAMAGE_TYPE_LABELS: Record<TimelineMechanic['damageType'], string> = {
  UNKNOWN: '属性待确认',
  MAGICAL: '魔法',
  PHYSICAL: '物理',
  SPECIAL: '特殊',
}

export function attackClass(mechanic: Pick<TimelineMechanic, 'type' | 'name'>): AttackClass {
  if (mechanic.type === 'RAIDWIDE') return 'AOE'
  if (mechanic.type === 'TANK_BUSTER') return 'TANK_BUSTER'

  const normalizedName = mechanic.name.trim().toLocaleLowerCase()
  if (/^(攻击|attack)(\s|$|x)/.test(normalizedName)) return 'AUTO_ATTACK'
  return 'MECHANIC'
}

export function attackClassLabel(mechanic: Pick<TimelineMechanic, 'type' | 'name'>): string {
  return ATTACK_CLASS_LABELS[attackClass(mechanic)]
}

export function damageTypeLabel(type: TimelineMechanic['damageType']): string {
  return DAMAGE_TYPE_LABELS[type]
}

export function hasDirectDamage(mechanic: Pick<TimelineMechanic, 'type' | 'name' | 'damageType' | 'damageProfile'>): boolean {
  return Boolean(mechanic.damageProfile)
    || mechanic.type === 'RAIDWIDE'
    || mechanic.type === 'TANK_BUSTER'
    || attackClass(mechanic) === 'AUTO_ATTACK'
    || mechanic.damageType !== 'UNKNOWN'
}

/**
 * A timeline without a target-adjusted, reviewed calibration must never show a
 * fabricated hit point value. This message deliberately distinguishes the
 * timing/type metadata from a survivability input.
 */
export function damageEstimateLabel(mechanic: Pick<TimelineMechanic, 'type' | 'name' | 'damageType' | 'damageProfile'>): string {
  const profile = mechanic.damageProfile
  if (!profile) return hasDirectDamage(mechanic) ? '伤害值待校准' : '时间轴标记 · 无直接伤害'

  const statistic = ({
    MAX_OBSERVED: '最大实测',
    P95: 'P95 实测',
    EXPECTED: '期望值',
  } as const)[profile.statistic]
  const basis = profile.basis === 'FORMULA_VERIFIED' ? '公式校验' : '目标调整实测'
  return `${statistic} ${profile.amount.toLocaleString('zh-CN')} · n=${profile.sampleCount} · ${basis}`
}
