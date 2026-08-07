<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Activity, CheckCircle2, Clock3, TriangleAlert } from 'lucide-vue-next'
import { api, ApiError } from '../api/client'
import type { ExecutionStats, ExecutionSummary } from '../types/domain'

const stats = ref<ExecutionStats | null>(null)
const executions = ref<ExecutionSummary[]>([])
const error = ref('')
const loading = ref(true)
const refreshedAt = ref('')

const successRate = computed(() => {
  const total = stats.value?.assignments ?? 0
  const success = stats.value?.stateCounts.SUCCESS ?? 0
  return total ? Math.round((success / total) * 100) : 0
})

onMounted(loadExecutions)

async function loadExecutions() {
  loading.value = true
  error.value = ''
  try {
    const [loadedStats, loadedExecutions] = await Promise.all([api.executionStats(), api.executions()])
    if (!loadedStats || !Array.isArray(loadedExecutions)) {
      throw new Error('服务端返回空响应，请刷新或重新登录后再试')
    }
    stats.value = loadedStats
    executions.value = loadedExecutions
    refreshedAt.value = new Date().toLocaleTimeString()
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : reason instanceof Error ? reason.message : '个人复盘加载失败'
  } finally {
    loading.value = false
  }
}

function duration(item: ExecutionSummary) {
  const seconds = Math.max(0, Math.round((Date.parse(item.endedAt) - Date.parse(item.startedAt)) / 1000))
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}
</script>

<template>
  <section class="content-page executions-page">
    <div class="page-heading">
      <div><p class="eyebrow">PERSONAL EXECUTION</p><h1>个人复盘</h1><p>只统计当前账户的插件执行批次，不进入公共样本池。</p></div>
      <button class="secondary-button" type="button" :disabled="loading" @click="loadExecutions">
        <Clock3 :size="16" />{{ refreshedAt ? `刷新 · ${refreshedAt}` : '刷新记录' }}
      </button>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="stats" class="execution-kpis">
      <article><Activity :size="19" /><span><b>{{ stats.fights }}</b><small>记录战斗</small></span></article>
      <article><CheckCircle2 :size="19" /><span><b>{{ successRate }}%</b><small>任务按窗成功</small></span></article>
      <article><TriangleAlert :size="19" /><span><b>{{ stats.stateCounts.MISSED ?? 0 }}</b><small>超时任务</small></span></article>
      <article><Clock3 :size="19" /><span><b>{{ stats.averageObservedOffsetMs ?? '—' }}</b><small>平均偏移 ms</small></span></article>
    </div>
    <div class="execution-list">
      <p v-if="loading">加载中…</p>
      <article v-for="item in executions" :key="item.fightExecutionId">
        <span :class="['result-mark', item.result.toLowerCase()]">{{ item.result }}</span>
        <div><b>计划 {{ item.planId.slice(0, 8) }} · v{{ item.planVersion }}</b><small>{{ new Date(item.startedAt).toLocaleString() }}</small></div>
        <time>{{ duration(item) }}</time>
      </article>
      <div v-if="!loading && !executions.length" class="empty-copy execution-empty">
        <p>当前账号暂无执行记录。插件会在战斗结束后异步批量上传。</p>
        <ul>
          <li>确认网页登录账号和插件绑定账号是同一个。</li>
          <li>打开插件设置，查看“执行上传状态”；如果显示 401/404/上传失败，重新连接账号后它会继续重试本地 pending 队列。</li>
          <li>只有进入战斗生命周期并结束/团灭/手动停止后的批次会出现在这里。</li>
        </ul>
      </div>
    </div>
  </section>
</template>
