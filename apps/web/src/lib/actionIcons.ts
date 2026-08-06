import type { AbilityDefinition } from '../types/domain'

const XIVAPI_ASSET_ENDPOINT = 'https://v2.xivapi.com/api/asset'

/**
 * XIVAPI resolves this game-client asset path to a cacheable PNG. We retain
 * only the Action sheet path rather than copying game artwork into VedaAxis.
 */
export function actionIconUrl(ability: Pick<AbilityDefinition, 'iconPath'> | null | undefined): string | null {
  const path = ability?.iconPath?.trim()
  if (!path) return null
  return `${XIVAPI_ASSET_ENDPOINT}?path=${encodeURIComponent(path)}&format=png`
}
