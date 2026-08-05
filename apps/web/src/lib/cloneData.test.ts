import { afterEach, describe, expect, it, vi } from 'vitest'
import { cloneData } from './cloneData'

describe('cloneData', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates an independent clone when structuredClone is unavailable', () => {
    vi.stubGlobal('structuredClone', undefined)
    const source = { phases: [{ name: 'P1' }], assignments: [] as string[] }

    const cloned = cloneData(source)
    cloned.phases[0]!.name = 'P2'

    expect(cloned).not.toBe(source)
    expect(source.phases[0]!.name).toBe('P1')
  })
})
