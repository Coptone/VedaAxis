import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PlanEditorView from './PlanEditorView.vue'

vi.mock('../api/client', () => ({
  api: {
    abilities: vi.fn().mockResolvedValue([]),
  },
  ApiError: class ApiError extends Error {},
}))

describe('PlanEditorView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
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
    expect(wrapper.findAll('.track-column')).toHaveLength(8)
  })
})
