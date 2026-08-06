import type {
  AbilityDefinition,
  AuthorizedDevice,
  AiCandidate,
  DamageEstimate,
  ExecutionStats,
  ExecutionSummary,
  PlanDetails,
  PlanSnapshot,
  PlanSummary,
  RuleValidationResult,
  SurvivabilityAnalysis,
  SurvivabilityRequest,
  TimelineImportCandidate,
  TrackMode,
} from '../types/domain'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly body?: unknown,
  ) {
    super(message)
  }
}

function accessToken(): string | null {
  return localStorage.getItem('vedaaxis.accessToken')
}

let refreshInFlight: Promise<void> | null = null

async function refreshAccessToken(): Promise<void> {
  const refreshToken = localStorage.getItem('vedaaxis.refreshToken')
  if (!refreshToken) throw new Error('missing refresh token')
  const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  if (!response.ok) {
    localStorage.removeItem('vedaaxis.accessToken')
    localStorage.removeItem('vedaaxis.refreshToken')
    throw new Error('refresh token rejected')
  }
  const tokens = (await response.json()) as TokenPair
  localStorage.setItem('vedaaxis.accessToken', tokens.accessToken)
  localStorage.setItem('vedaaxis.refreshToken', tokens.refreshToken)
}

async function request<T>(path: string, init: RequestInit = {}, allowRefresh = true): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const token = accessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers })
  if (response.status === 401 && allowRefresh && localStorage.getItem('vedaaxis.refreshToken')) {
    refreshInFlight ??= refreshAccessToken().finally(() => { refreshInFlight = null })
    await refreshInFlight
    return request<T>(path, init, false)
  }
  if (response.status === 204) return undefined as T
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const record = body as { code?: string; message?: string } | null
    throw new ApiError(response.status, record?.code ?? 'REQUEST_FAILED', record?.message ?? '请求失败', body)
  }
  return body as T
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
  tokenType: string
  accessTokenExpiresAt: string
}

export const api = {
  register: (email: string, password: string) =>
    request<TokenPair>('/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) }),
  login: (email: string, password: string) =>
    request<TokenPair>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  plans: () => request<PlanSummary[]>('/plans'),
  plan: (planId: string) => request<PlanDetails>(`/plans/${planId}`),
  copyPlan: (planId: string) => request<PlanDetails>(`/plans/${planId}/copy`, { method: 'POST' }),
  createPlan: (payload: { name: string; encounterId: string; territoryId: number; strategyTag: string; trackMode: TrackMode }) =>
    request<PlanDetails>('/plans', { method: 'POST', body: JSON.stringify(payload) }),
  updatePlan: (planId: string, name: string, snapshot: PlanSnapshot) =>
    request<PlanDetails>(`/plans/${planId}`, {
      method: 'PUT',
      body: JSON.stringify({ name, snapshot }),
    }),
  validatePlan: (planId: string) =>
    request<RuleValidationResult>(`/plans/${planId}/validate`, { method: 'POST' }),
  analyzeSurvivability: (planId: string, mechanicId: string, payload: SurvivabilityRequest) =>
    request<SurvivabilityAnalysis>(`/plans/${planId}/mechanics/${mechanicId}/survivability`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  previewDamageEstimates: (snapshot: PlanSnapshot) =>
    request<DamageEstimate[]>('/damage-estimates/preview', {
      method: 'POST',
      body: JSON.stringify({ snapshot }),
    }),
  publishPlan: (planId: string) =>
    request<{ snapshot: PlanSnapshot; shareCode: string; validation: RuleValidationResult }>(
      `/plans/${planId}/publish`,
      { method: 'POST' },
    ),
  abilities: () => request<AbilityDefinition[]>('/abilities'),
  generateAiCandidate: (planId: string, instruction = '') =>
    request<AiCandidate>(`/plans/${planId}/ai-candidates`, {
      method: 'POST',
      body: JSON.stringify({ instruction }),
    }),
  importMSpecTimeline: (sourceUrl: string, includeRecommendations: boolean) =>
    request<TimelineImportCandidate>('/timeline-imports/m-spec', {
      method: 'POST',
      body: JSON.stringify({ sourceUrl, includeRecommendations }),
    }),
  executions: (limit = 20) => request<ExecutionSummary[]>(`/fight-executions?limit=${limit}`),
  executionStats: (limit = 100) => request<ExecutionStats>(`/fight-executions/stats?limit=${limit}`),
  approveDevice: (userCode: string) =>
    request<void>(`/device-authorizations/${encodeURIComponent(userCode)}/approve`, { method: 'POST' }),
  devices: () => request<AuthorizedDevice[]>('/devices'),
  revokeDevice: (deviceId: string) => request<void>(`/devices/${encodeURIComponent(deviceId)}`, { method: 'DELETE' }),
  sharedPlan: (shareCode: string) => request<{ name: string; status: string; snapshot: PlanSnapshot }>(`/shares/${shareCode}`),
}
