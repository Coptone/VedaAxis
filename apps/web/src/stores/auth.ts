import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api, type TokenPair } from '../api/client'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem('vedaaxis.accessToken'))
  const refreshToken = ref(localStorage.getItem('vedaaxis.refreshToken'))
  const authenticated = computed(() => Boolean(accessToken.value))

  function save(tokens: TokenPair) {
    accessToken.value = tokens.accessToken
    refreshToken.value = tokens.refreshToken
    localStorage.setItem('vedaaxis.accessToken', tokens.accessToken)
    localStorage.setItem('vedaaxis.refreshToken', tokens.refreshToken)
  }

  async function login(email: string, password: string) {
    save(await api.login(email, password))
  }

  async function register(email: string, password: string) {
    save(await api.register(email, password))
  }

  function logout() {
    accessToken.value = null
    refreshToken.value = null
    localStorage.removeItem('vedaaxis.accessToken')
    localStorage.removeItem('vedaaxis.refreshToken')
  }

  return { accessToken, refreshToken, authenticated, login, register, logout }
})
