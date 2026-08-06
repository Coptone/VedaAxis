import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import DeviceAuthorizeView from './DeviceAuthorizeView.vue'

vi.mock('../api/client', () => ({
  api: {
    devices: vi.fn().mockResolvedValue([]),
    approveDevice: vi.fn().mockResolvedValue(undefined),
    revokeDevice: vi.fn().mockResolvedValue(undefined),
  },
  ApiError: class ApiError extends Error {},
}))

describe('DeviceAuthorizeView', () => {
  afterEach(() => vi.clearAllMocks())

  it('prefills the one-time code and shows the persisted device after approval', async () => {
    vi.mocked(api.devices)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([{
        id: '11111111-1111-4111-8111-111111111111',
        name: 'Dalamud 插件',
        lastSeenAt: '2026-08-06T00:00:00Z',
        revokedAt: null,
        createdAt: '2026-08-06T00:00:00Z',
      }])
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/device', component: DeviceAuthorizeView }],
    })
    await router.push('/device?code=ABCD-1234')
    await router.isReady()
    const wrapper = mount(DeviceAuthorizeView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.get('input').element).toHaveProperty('value', 'ABCD-1234')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.approveDevice).toHaveBeenCalledWith('ABCD-1234')
    expect(wrapper.text()).toContain('设备已授权，可以回到游戏')
    expect(wrapper.text()).toContain('Dalamud 插件')
  })
})
