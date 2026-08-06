import { describe, expect, it } from 'vitest'
import { actionIconUrl } from './actionIcons'

describe('actionIconUrl', () => {
  it('uses the game asset path through the PNG asset endpoint', () => {
    expect(actionIconUrl({ iconPath: 'ui/icon/003000/003666.tex' })).toBe(
      'https://v2.xivapi.com/api/asset?path=ui%2Ficon%2F003000%2F003666.tex&format=png',
    )
  })

  it('does not emit a broken image URL when a catalog entry has no icon', () => {
    expect(actionIconUrl({ iconPath: '   ' })).toBeNull()
    expect(actionIconUrl(undefined)).toBeNull()
  })
})
