import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api, type TokenPair } from '../api/client'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem('vedaaxis.accessToken'))
  const refreshToken = ref(localStorage.getItem('vedaaxis.refreshToken'))
  const accountEmail = ref(localStorage.getItem('vedaaxis.accountEmail'))
  const authenticated = computed(() => Boolean(accessToken.value))
  const accountId = computed(() => subjectFromToken(accessToken.value))
  const accountLabel = computed(() => accountEmail.value?.trim() || accountId.value || '未识别账户')

  function save(tokens: TokenPair, email?: string) {
    accessToken.value = tokens.accessToken
    refreshToken.value = tokens.refreshToken
    localStorage.setItem('vedaaxis.accessToken', tokens.accessToken)
    localStorage.setItem('vedaaxis.refreshToken', tokens.refreshToken)
    if (email?.trim()) {
      accountEmail.value = email.trim()
      localStorage.setItem('vedaaxis.accountEmail', accountEmail.value)
    }
  }

  async function login(email: string, password: string) {
    save(await api.login(email, password), email)
  }

  async function register(email: string, password: string) {
    save(await api.register(email, password), email)
  }

  function logout() {
    accessToken.value = null
    refreshToken.value = null
    accountEmail.value = null
    localStorage.removeItem('vedaaxis.accessToken')
    localStorage.removeItem('vedaaxis.refreshToken')
    localStorage.removeItem('vedaaxis.accountEmail')
  }

  return { accessToken, refreshToken, accountEmail, accountId, accountLabel, authenticated, login, register, logout }
})

function subjectFromToken(token: string | null): string {
  if (!token) return ''
  try {
    const encodedPayload = token.split('.')[1]
    if (!encodedPayload) return ''
    const json = atob(encodedPayload.replace(/-/g, '+').replace(/_/g, '/'))
    const payload = JSON.parse(json) as { sub?: unknown }
    const subject = typeof payload.sub === 'string' ? payload.sub : ''
    return subject ? `账户 ${subject.slice(0, 8)}` : ''
  } catch {
    return ''
  }
}
