import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import type { PlanSummary } from '../types/domain'
import PlansView from './PlansView.vue'

vi.mock('../api/client', () => ({
  api: {
    plans: vi.fn().mockResolvedValue([]),
    createPlan: vi.fn(),
    copyPlan: vi.fn(),
    deletePlan: vi.fn(),
  },
  ApiError: class ApiError extends Error {},
}))

function createTestRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/plans', component: PlansView },
      { path: '/plans/:planId', component: { template: '<div />' } },
    ],
  })
  return router
}

describe('PlansView', () => {
  afterEach(() => vi.clearAllMocks())

  it('creates a plan through the encounter party and template wizard', async () => {
    vi.mocked(api.createPlan).mockResolvedValue({
      plan: {
        id: '11111111-1111-4111-8111-111111111111',
        name: 'O8S 游戏与网页联调计划',
        latestVersion: 1,
        updatedAt: '2026-08-06T00:00:00Z',
      },
      snapshot: {} as never,
    })
    const router = createTestRouter()
    await router.push('/plans')
    await router.isReady()
    const wrapper = mount(PlansView, { global: { plugins: [createPinia(), router] } })
    await flushPromises()

    await wrapper.get('.create-actions .primary-button').trigger('click')
    await wrapper.findAll('.encounter-choice')[1]!.trigger('click')
    await wrapper.get('.create-plan-grid input').setValue('我的 O8S 联调计划')
    expect(wrapper.findAll('.job-select-card img')).toHaveLength(8)
    await wrapper.get('.create-plan-dialog footer .primary-button').trigger('click')
    await flushPromises()

    expect(api.createPlan).toHaveBeenCalledWith({
      name: '我的 O8S 联调计划',
      encounterId: '9789ba9a-b761-4c44-b179-2e3e86ee0d3b',
      territoryId: 755,
      strategyTag: 'O8S-POC',
      trackMode: 'EIGHT',
      useDefaultTemplate: true,
      partyJobIds: {
        MT: 21,
        ST: 37,
        H1: 24,
        H2: 40,
        D1: 34,
        D2: 41,
        D3: 38,
        D4: 42,
      },
    })
    expect(router.currentRoute.value.path).toBe('/plans/11111111-1111-4111-8111-111111111111')
    expect(router.currentRoute.value.query.jobId).toBe('40')
    wrapper.unmount()
  })

  it('deletes a cloud plan after confirmation', async () => {
    const plan: PlanSummary = {
      id: '22222222-2222-4222-8222-222222222222',
      name: '待删除计划',
      encounterId: 'c97e8840-1697-476f-a4ac-8c7996df277b',
      territoryId: 1363,
      strategyTag: 'DMU-P1P2',
      trackMode: 'EIGHT',
      latestVersion: 2,
      updatedAt: '2026-08-07T00:00:00Z',
    }
    vi.mocked(api.plans).mockResolvedValue([plan])
    vi.mocked(api.deletePlan).mockResolvedValue(undefined)
    const router = createTestRouter()
    await router.push('/plans')
    await router.isReady()
    const wrapper = mount(PlansView, { global: { plugins: [createPinia(), router] } })
    await flushPromises()

    await wrapper.get('.plan-card .icon-button.danger').trigger('click')
    expect(wrapper.get('.delete-plan-dialog').text()).toContain('待删除计划')
    await wrapper.get('.delete-plan-confirm').trigger('click')
    await flushPromises()

    expect(api.deletePlan).toHaveBeenCalledWith(plan.id)
    expect(wrapper.text()).not.toContain('待删除计划')
    wrapper.unmount()
  })
})
