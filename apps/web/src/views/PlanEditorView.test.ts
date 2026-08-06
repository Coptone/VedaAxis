import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { dmuP1P2DefaultPlan } from '../data/dmuP1P2Default'
import PlanEditorView from './PlanEditorView.vue'

vi.mock('../api/client', () => ({
  api: {
    abilities: vi.fn().mockResolvedValue([]),
    createPlan: vi.fn(),
    updatePlan: vi.fn(),
  },
  ApiError: class ApiError extends Error {},
}))

describe('PlanEditorView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('renders a new eight-track plan without browser-only clone support', async () => {
    vi.stubGlobal('structuredClone', undefined)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/plans/new', component: PlanEditorView }],
    })
    await router.push('/plans/new')
    await router.isReady()

    const wrapper = mount(PlanEditorView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.get('[aria-label="计划名称"]').element).toHaveProperty('value', '妖星乱舞 P1/P2 默认减伤表')
    expect(wrapper.get('.mechanic-panel h2').text()).toBe('妖星乱舞 · P1/P2')
    expect(wrapper.get('.mechanic-panel > header > span').text()).toBe('76 项 · 108 个减伤安排')
    expect(wrapper.get('.assignment-board h2').text()).toBe('攻击 x4')
    expect(wrapper.findAll('.assignment-card')).toHaveLength(8)
    expect(wrapper.findAll('.track-column')).toHaveLength(8)
    expect(wrapper.get('.survivability-panel h3').text()).toBe('减伤承伤校验')
    expect(wrapper.get('.damage-estimate-note').text()).toBe('伤害值待校准')

    const analyzeButton = wrapper.findAll('button').find((button) => button.text().includes('计算承伤'))
    expect(analyzeButton).toBeDefined()
    await analyzeButton!.trigger('click')
    expect(wrapper.get('.survivability-status').text()).toBe('伤害值待校准')
    expect(api.createPlan).not.toHaveBeenCalled()
  })

  it('remaps execution and single-target tracks when first saving a default plan', async () => {
    const planId = '11111111-1111-4111-8111-111111111111'
    const createdTrackIds = [
      '20000000-0000-4000-8000-000000000001',
      '20000000-0000-4000-8000-000000000002',
      '20000000-0000-4000-8000-000000000003',
      '20000000-0000-4000-8000-000000000004',
      '20000000-0000-4000-8000-000000000005',
      '20000000-0000-4000-8000-000000000006',
      '20000000-0000-4000-8000-000000000007',
      '20000000-0000-4000-8000-000000000008',
    ]
    const createdSnapshot = dmuP1P2DefaultPlan()
    createdSnapshot.planId = planId
    createdSnapshot.tracks = createdSnapshot.tracks.map((track, index) => ({ ...track, trackId: createdTrackIds[index]! }))
    vi.mocked(api.createPlan).mockResolvedValue({
      plan: { id: planId, name: 'test', latestVersion: 1, updatedAt: '2026-08-06T00:00:00Z' },
      snapshot: createdSnapshot,
    })
    vi.mocked(api.updatePlan).mockImplementation(async (_id, planName, planSnapshot) => ({
      plan: { id: planId, name: planName, latestVersion: 1, updatedAt: '2026-08-06T00:00:00Z' },
      snapshot: planSnapshot,
    }))
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/plans/new', component: PlanEditorView },
        { path: '/plans/:planId', component: PlanEditorView },
      ],
    })
    await router.push('/plans/new')
    await router.isReady()
    const wrapper = mount(PlanEditorView, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.get('.editor-actions button').trigger('click')
    await flushPromises()

    const updatedSnapshot = vi.mocked(api.updatePlan).mock.calls[0]![2]
    const validTrackIds = new Set(createdTrackIds)
    expect(updatedSnapshot.assignments.every((assignment) => validTrackIds.has(assignment.trackId))).toBe(true)
    expect(updatedSnapshot.assignments
      .filter((assignment) => assignment.targetTrackId)
      .every((assignment) => validTrackIds.has(assignment.targetTrackId!))).toBe(true)
  })
})
