import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import PlansView from './PlansView.vue'

vi.mock('../api/client', () => ({
  api: {
    plans: vi.fn().mockResolvedValue([]),
    createPlan: vi.fn(),
    copyPlan: vi.fn(),
  },
  ApiError: class ApiError extends Error {},
}))

describe('PlansView', () => {
  afterEach(() => vi.clearAllMocks())

  it('creates the dedicated O8S cloud-linkage plan', async () => {
    vi.mocked(api.createPlan).mockResolvedValue({
      plan: {
        id: '11111111-1111-4111-8111-111111111111',
        name: 'O8S 游戏与网页联调计划',
        latestVersion: 1,
        updatedAt: '2026-08-06T00:00:00Z',
      },
      snapshot: {} as never,
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/plans', component: PlansView },
        { path: '/plans/:planId', component: { template: '<div />' } },
      ],
    })
    await router.push('/plans')
    await router.isReady()
    const wrapper = mount(PlansView, { global: { plugins: [createPinia(), router] } })
    await flushPromises()

    await wrapper.get('.create-actions button').trigger('click')
    await flushPromises()

    expect(api.createPlan).toHaveBeenCalledWith({
      name: 'O8S 游戏与网页联调计划',
      encounterId: '9789ba9a-b761-4c44-b179-2e3e86ee0d3b',
      territoryId: 755,
      strategyTag: 'O8S-POC',
      trackMode: 'EIGHT',
    })
    expect(router.currentRoute.value.path).toBe('/plans/11111111-1111-4111-8111-111111111111')
    wrapper.unmount()
  })
})
