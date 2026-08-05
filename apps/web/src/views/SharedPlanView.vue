<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Clock3, LockKeyhole, ShieldCheck } from 'lucide-vue-next'
import { api, ApiError } from '../api/client'
import { formatTime } from '../lib/tracks'
import type { PlanSnapshot } from '../types/domain'

const route = useRoute()
const name = ref('')
const status = ref('')
const snapshot = ref<PlanSnapshot | null>(null)
const error = ref('')
const assignmentsByTrack = computed(() => {
  const result = new Map<string, number>()
  for (const assignment of snapshot.value?.assignments ?? []) {
    result.set(assignment.trackId, (result.get(assignment.trackId) ?? 0) + 1)
  }
  return result
})

onMounted(async () => {
  try {
    const data = await api.sharedPlan(String(route.params.shareCode))
    name.value = data.name
    status.value = data.status
    snapshot.value = data.snapshot
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '分享计划加载失败'
  }
})
</script>

<template>
  <section class="content-page shared-page">
    <div v-if="error" class="empty-plans"><h2>{{ error }}</h2></div>
    <template v-else-if="snapshot">
      <div class="page-heading">
        <div>
          <p class="eyebrow">READ-ONLY PLAN SNAPSHOT</p>
          <h1>{{ name }}</h1>
          <p>{{ snapshot.strategyTag }} · 时间轴 v{{ snapshot.timelineVersion }}</p>
        </div>
        <span class="status-badge"><LockKeyhole :size="14" />{{ status }}</span>
      </div>
      <div class="snapshot-summary">
        <div><ShieldCheck :size="20" /><span><b>{{ snapshot.trackMode === 'EIGHT' ? '八轨计划' : '四轨计划' }}</b><small>协议 {{ snapshot.schemaVersion }}</small></span></div>
        <div><Clock3 :size="20" /><span><b>计划 v{{ snapshot.planVersion }}</b><small>最低插件 {{ snapshot.minimumPluginVersion }}</small></span></div>
        <div><span><b>{{ snapshot.assignments.length }} 个任务</b><small>{{ snapshot.source.confidence }}</small></span></div>
      </div>
      <div class="shared-tracks">
        <article v-for="track in snapshot.tracks" :key="track.trackId">
          <header><b>{{ track.slot }}</b><span>{{ track.displayName }}</span><small>{{ assignmentsByTrack.get(track.trackId) ?? 0 }} 项</small></header>
          <div v-for="assignment in snapshot.assignments.filter((item) => item.trackId === track.trackId)" :key="assignment.assignmentId" class="shared-assignment">
            <span>#{{ assignment.actionId }}</span>
            <b>{{ formatTime(assignment.earliestUseAtMs) }}–{{ formatTime(assignment.latestUseAtMs) }}</b>
          </div>
          <p v-if="!assignmentsByTrack.get(track.trackId)">暂无任务</p>
        </article>
      </div>
    </template>
    <div v-else class="loading-panel">正在读取不可变计划快照…</div>
  </section>
</template>
