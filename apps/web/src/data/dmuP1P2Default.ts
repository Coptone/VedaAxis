import rawDefaultPlan from '../../../../data/seeds/dmu/p1-p2-default-plan.json'
import type { PlanSnapshot } from '../types/domain'
import { cloneData } from '../lib/cloneData'

export const DMU_ENCOUNTER_ID = 'c97e8840-1697-476f-a4ac-8c7996df277b'
export const DMU_TERRITORY_ID = 1363
export const DMU_P1_P2_STRATEGY = 'DMU-P1P2'

export function dmuP1P2DefaultPlan(): PlanSnapshot {
  return cloneData(rawDefaultPlan as PlanSnapshot)
}
