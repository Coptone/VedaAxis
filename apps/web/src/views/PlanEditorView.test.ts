import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { dmuP1P2DefaultPlan } from '../data/dmuP1P2Default'
import type { AbilityDefinition } from '../types/domain'
import PlanEditorView from './PlanEditorView.vue'

vi.mock('../api/client', () => ({
  api: {
    abilities: vi.fn().mockResolvedValue([]),
    createPlan: vi.fn(),
    updatePlan: vi.fn(),
    previewDamageEstimates: vi.fn().mockResolvedValue([]),
    generateAiCandidate: vi.fn(),
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
    expect(wrapper.get('.mechanic-panel > header > span').text()).toBe('59 / 76 项 · 108 个减伤安排')
    expect(wrapper.get('.timeline-pager span').text()).toBe('第 1 / 5 页')
    expect(wrapper.get('.timeline-marker-toggle').text()).toContain('隐藏 17')
    expect(wrapper.findAll('.mechanic-item')).toHaveLength(12)
    expect(wrapper.get('.assignment-board h2').text()).toBe('攻击 x4')
    expect(wrapper.findAll('.assignment-card')).toHaveLength(8)
    expect(wrapper.findAll('.assignment-card .action-icon-placeholder')).toHaveLength(8)
    expect(wrapper.findAll('.track-column')).toHaveLength(8)
    expect(wrapper.get('.damage-analysis-panel h3').text()).toBe('当前减伤后预计伤害')
    expect(wrapper.get('.damage-estimate-note').text()).toContain('P95 实测')
    expect(wrapper.get('.damage-analysis-status').text()).toBe('伤害值待校准')
    expect(wrapper.find('input[placeholder="按角色实际值填写"]').exists()).toBe(false)
    expect(api.createPlan).not.toHaveBeenCalled()
  })

  it('colors the post-mitigation number from the current plan result', async () => {
    const defaultPlan = dmuP1P2DefaultPlan()
    const assignedMechanics = new Set(defaultPlan.assignments.map((assignment) => assignment.mechanicId))
    const firstMechanicId = defaultPlan.mechanics.find((mechanic) => mechanic.damageProfile && assignedMechanics.has(mechanic.mechanicId))!.mechanicId
    vi.mocked(api.previewDamageEstimates).mockResolvedValueOnce([{
      mechanicId: firstMechanicId,
      status: 'CALCULATED',
      baselineDamage: 300_000,
      damageAfterMitigation: 200_000,
      modeledReduction: 1 / 3,
      riskLevel: 'RED',
      worstTrackId: 'track-1',
      worstTrackSlot: 'MT',
      sampleCount: 6,
      source: 'FFLogs sample',
      notices: [],
    }])
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/plans/new', component: PlanEditorView }],
    })
    await router.push('/plans/new')
    await router.isReady()

    const wrapper = mount(PlanEditorView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.get('.post-mitigation-damage.damage-risk-yellow').text()).toContain('减伤后 200,000 · 80.0%HP')
    expect(wrapper.get('.damage-analysis-metrics .damage-risk-yellow').text()).toBe('200,000')
    expect(wrapper.get('.damage-analysis-status').text()).toBe('剩余HP不足25%')
    expect(wrapper.get('.hp-damage-labels').text()).toContain('预计承伤 80.0% HP')
    expect(wrapper.get('.hp-damage-labels').text()).toContain('剩余 20.0%')
  })

  it('groups selectable abilities by planning category while preserving job filtering', async () => {
    vi.mocked(api.abilities).mockResolvedValueOnce([
      testAbility(24298, '白牛清汁 / Kerachole', [40], { allDamageReductionPercent: 10, calculationReadiness: 'DIRECT_REDUCTION' }),
      testAbility(24299, '寄生清汁 / Ixochole', [40], { calculationReadiness: 'NO_DIRECT_MITIGATION' }),
      testAbility(140, '天赐祝福 / Benediction', [24], { calculationReadiness: 'NO_DIRECT_MITIGATION' }),
    ])
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/plans/new', component: PlanEditorView }],
    })
    await router.push('/plans/new')
    await router.isReady()

    const wrapper = mount(PlanEditorView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('select').setValue(dmuP1P2DefaultPlan().tracks.find((track) => track.slot === 'H2')!.trackId)
    await flushPromises()

    await wrapper.get('.ability-picker-trigger').trigger('click')
    await flushPromises()

    expect(wrapper.get('.ability-picker-modal').attributes('role')).toBe('dialog')
    const labels = wrapper.findAll('.ability-picker-group header').map((group) => group.text())
    expect(labels).toContain('团减1')
    expect(labels).toContain('团血1')
    expect(wrapper.text()).toContain('寄生清汁 / Ixochole')
    expect(wrapper.text()).not.toContain('天赐祝福 / Benediction')
  })

  it('keeps assignment timing readonly until the visual editor is opened', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/plans/new', component: PlanEditorView }],
    })
    await router.push('/plans/new')
    await router.isReady()

    const wrapper = mount(PlanEditorView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('.assignment-card').trigger('click')
    await flushPromises()

    expect(wrapper.get('.assignment-readonly-grid').text()).toContain('开始高亮')
    expect(wrapper.find('.inspector-panel input').exists()).toBe(false)

    await wrapper.get('.assignment-edit-button').trigger('click')
    await flushPromises()

    expect(wrapper.get('.assignment-editor-modal').attributes('role')).toBe('dialog')
    expect(wrapper.findAll('.assignment-editor-controls input[type="range"]')).toHaveLength(4)
    expect(wrapper.get('.assignment-editor-cancel').text()).toBe('取消')
  })

  it('sends focused AI candidate requests with the selected track scope', async () => {
    const planId = '11111111-1111-4111-8111-111111111111'
    const createdSnapshot = dmuP1P2DefaultPlan()
    createdSnapshot.planId = planId
    const h2Track = createdSnapshot.tracks.find((track) => track.slot === 'H2')!
    vi.mocked(api.createPlan).mockResolvedValue({
      plan: { id: planId, name: 'test', latestVersion: 1, updatedAt: '2026-08-07T00:00:00Z' },
      snapshot: createdSnapshot,
    })
    vi.mocked(api.updatePlan).mockImplementation(async (_id, planName, planSnapshot) => ({
      plan: { id: planId, name: planName, latestVersion: 1, updatedAt: '2026-08-07T00:00:00Z' },
      snapshot: planSnapshot,
    }))
    vi.mocked(api.generateAiCandidate).mockResolvedValue({
      schemaVersion: '1.0',
      candidateId: '22222222-2222-4222-8222-222222222222',
      basePlanId: planId,
      assignments: createdSnapshot.assignments,
      reasons: [],
      warnings: [],
      confidence: 'RULE_VALIDATED',
      provider: 'DeepSeek',
      model: 'test',
      generatedAt: '2026-08-07T00:00:00Z',
      validation: { valid: true, issues: [] },
    })
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
    await wrapper.findAll('.editor-actions button').find((button) => button.text().includes('AI 候选'))!.trigger('click')
    await flushPromises()
    await wrapper.findAll('.ai-mode-option')[1]!.trigger('click')
    await wrapper.get('.ai-focus-track select').setValue(h2Track.trackId)
    await wrapper.get('.ai-request-panel textarea').setValue('只优化 H2')
    await wrapper.get('.ai-request-panel .primary-button').trigger('click')
    await flushPromises()

    expect(api.generateAiCandidate).toHaveBeenCalledWith(planId, {
      instruction: '只优化 H2',
      mode: 'FOCUSED',
      focusTrackId: h2Track.trackId,
    })
    expect(wrapper.get('.inline-message').text()).toContain('指向 H2')
  })

  it('reverts timing changes when cancelling the editor', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/plans/new', component: PlanEditorView }],
    })
    await router.push('/plans/new')
    await router.isReady()

    const wrapper = mount(PlanEditorView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('.assignment-card').trigger('click')
    await wrapper.get('.assignment-edit-button').trigger('click')
    await flushPromises()

    const impactInput = wrapper.findAll('.assignment-editor-controls input[type="number"]')[3]!
    const originalImpact = Number((impactInput.element as HTMLInputElement).value)
    await impactInput.setValue(String(originalImpact + 1_000))
    expect(Number((impactInput.element as HTMLInputElement).value)).toBe(originalImpact + 1_000)

    await wrapper.get('.assignment-editor-cancel').trigger('click')
    await flushPromises()
    expect(wrapper.find('.assignment-editor-modal').exists()).toBe(false)

    await wrapper.get('.assignment-edit-button').trigger('click')
    await flushPromises()
    const revertedImpactInput = wrapper.findAll('.assignment-editor-controls input[type="number"]')[3]!
    expect(Number((revertedImpactInput.element as HTMLInputElement).value)).toBe(originalImpact)
  })

  it('updates assignment timing by dragging a marker on the timeline', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/plans/new', component: PlanEditorView }],
    })
    await router.push('/plans/new')
    await router.isReady()

    const wrapper = mount(PlanEditorView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('.assignment-card').trigger('click')
    await wrapper.get('.assignment-edit-button').trigger('click')
    await flushPromises()

    const canvas = wrapper.get('.assignment-timeline-canvas')
    Object.defineProperty(canvas.element, 'getBoundingClientRect', {
      value: () => ({
        left: 0,
        right: 1_000,
        top: 0,
        bottom: 240,
        width: 1_000,
        height: 240,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      }),
    })
    const impactInput = wrapper.findAll('.assignment-editor-controls input[type="number"]')[3]!
    const originalImpact = Number((impactInput.element as HTMLInputElement).value)

    await wrapper.get('.assignment-marker.impact').trigger('pointerdown', { clientX: 100, pointerId: 1 })
    window.dispatchEvent(new MouseEvent('pointermove', { clientX: 850 }))
    window.dispatchEvent(new MouseEvent('pointerup', { clientX: 850 }))
    await flushPromises()

    expect(Number((impactInput.element as HTMLInputElement).value)).not.toBe(originalImpact)
  })

  it('blocks adding a duplicate ability when the current placement window is on cooldown', async () => {
    vi.mocked(api.abilities).mockResolvedValueOnce([
      testAbility(999001, '测试团减 / Test Mitigation', [], { allDamageReductionPercent: 10, calculationReadiness: 'DIRECT_REDUCTION' }),
    ])
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/plans/new', component: PlanEditorView }],
    })
    await router.push('/plans/new')
    await router.isReady()

    const wrapper = mount(PlanEditorView, { global: { plugins: [router] } })
    await flushPromises()

    const addButton = wrapper.get('.quick-assign .primary-button')
    expect(addButton.attributes('disabled')).toBeUndefined()
    await addButton.trigger('click')
    await flushPromises()

    expect(wrapper.get('.quick-assign .primary-button').attributes('disabled')).toBeDefined()
    await wrapper.get('.ability-picker-trigger').trigger('click')
    await flushPromises()
    expect(wrapper.get('.ability-picker-option').classes()).toContain('blocked')
    expect(wrapper.get('.cooldown-overlay').text()).toBeTruthy()
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

function testAbility(
  actionId: number,
  name: string,
  jobIds: number[],
  effect: Partial<AbilityDefinition['effect']>,
): AbilityDefinition {
  return {
    actionId,
    name,
    iconPath: 'ui/icon/003000/003667.tex',
    jobIds,
    cooldownMs: 30_000,
    maxCharges: 1,
    durationMs: effect.calculationReadiness === 'NO_DIRECT_MITIGATION' ? 0 : 15_000,
    confirmationStrategy: 'STATUS_APPLY',
    source: 'test',
    confidence: 'REVIEWED',
    effect: {
      scope: 'PARTY',
      allDamageReductionPercent: 0,
      physicalDamageReductionPercent: 0,
      magicalDamageReductionPercent: 0,
      maximumHpIncreasePercent: 0,
      maximumHpBarrierPercent: 0,
      barrierCurePotency: 0,
      invulnerability: false,
      stackingGroup: '',
      calculationReadiness: 'NO_DIRECT_MITIGATION',
      conditions: [],
      source: 'test',
      confidence: 'REVIEWED',
      ...effect,
    },
  }
}
