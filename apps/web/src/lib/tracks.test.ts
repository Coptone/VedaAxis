import { describe, expect, it } from 'vitest'
import { createTracks, formatTime } from './tracks'

describe('track helpers', () => {
  it('creates the accepted four-track layout', () => {
    expect(createTracks('FOUR').map((track) => track.slot)).toEqual(['T1', 'H1', 'D1', 'D2'])
  })

  it('creates the accepted eight-track layout', () => {
    expect(createTracks('EIGHT').map((track) => track.slot)).toEqual([
      'MT', 'ST', 'H1', 'H2', 'D1', 'D2', 'D3', 'D4',
    ])
  })

  it('formats negative and positive timeline offsets', () => {
    expect(formatTime(-100)).toBe('0:00')
    expect(formatTime(69_000)).toBe('1:09')
  })
})
