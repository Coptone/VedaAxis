import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'

describe('API authentication refresh', () => {
  afterEach(() => {
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('refreshes an expired access token after a 401 and retries the request', async () => {
    localStorage.setItem('vedaaxis.accessToken', 'expired-access-token')
    localStorage.setItem('vedaaxis.refreshToken', 'valid-refresh-token')

    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        accessToken: 'fresh-access-token',
        refreshToken: 'fresh-refresh-token',
        tokenType: 'Bearer',
        accessTokenExpiresAt: '2026-08-06T02:00:00Z',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api.plans()).resolves.toEqual([])

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect((fetchMock.mock.calls[2]![1] as RequestInit).headers).toHaveProperty('get')
    expect(((fetchMock.mock.calls[2]![1] as RequestInit).headers as Headers).get('Authorization'))
      .toBe('Bearer fresh-access-token')
    expect(localStorage.getItem('vedaaxis.refreshToken')).toBe('fresh-refresh-token')
  })
})
