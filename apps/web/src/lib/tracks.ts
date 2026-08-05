import { TRACK_SLOTS, type ExecutionTrack, type TrackMode } from '../types/domain'
import { newId } from './ids'

const SLOT_NAMES: Record<string, string> = {
  T1: '坦克',
  MT: '主坦',
  ST: '副坦',
  H1: '治疗 1',
  H2: '治疗 2',
  D1: '输出 1',
  D2: '输出 2',
  D3: '输出 3',
  D4: '输出 4',
}

export function createTracks(mode: TrackMode): ExecutionTrack[] {
  return TRACK_SLOTS[mode].map((slot) => ({
    trackId: newId(),
    slot,
    allowedJobIds: [],
    displayName: SLOT_NAMES[slot],
  }))
}

export function formatTime(milliseconds: number): string {
  const totalSeconds = Math.max(0, Math.round(milliseconds / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}
