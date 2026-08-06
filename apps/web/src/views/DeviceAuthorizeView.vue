<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { CheckCircle2, Link2, MonitorSmartphone, RefreshCw, Unplug } from 'lucide-vue-next'
import { api, ApiError } from '../api/client'
import type { AuthorizedDevice } from '../types/domain'

const route = useRoute()
const code = ref(typeof route.query.code === 'string' ? route.query.code : '')
const busy = ref(false)
const success = ref(false)
const error = ref('')
const devices = ref<AuthorizedDevice[]>([])
const loadingDevices = ref(true)

onMounted(loadDevices)

async function loadDevices() {
  loadingDevices.value = true
  try {
    devices.value = await api.devices()
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '设备列表加载失败'
  } finally {
    loadingDevices.value = false
  }
}

async function approve() {
  busy.value = true
  success.value = false
  error.value = ''
  try {
    await api.approveDevice(code.value)
    success.value = true
    code.value = ''
    await loadDevices()
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '设备绑定失败'
  } finally {
    busy.value = false
  }
}

async function revoke(deviceId: string) {
  busy.value = true
  error.value = ''
  try {
    await api.revokeDevice(deviceId)
    await loadDevices()
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '设备撤销失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="content-page narrow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">PLUGIN DEVICE FLOW</p>
        <h1>绑定 Dalamud 插件</h1>
        <p>每台游戏电脑只需绑定一次；插件会保存可撤销的设备令牌并自动续期，不保存账户密码或服务端主密钥。</p>
      </div>
    </div>
    <div class="device-card">
      <div class="device-steps">
        <div><span>1</span><p><b>在游戏中请求绑定</b><small>打开 VedaAxis 插件，选择“连接账户”。</small></p></div>
        <div><span>2</span><p><b>首次输入一次性代码</b><small>代码有效期 10 分钟，只能使用一次；插件打开的链接会自动填入代码。</small></p></div>
        <div><span>3</span><p><b>以后自动连接</b><small>令牌会轮换续期；仅主动撤销、长期未使用或本地配置丢失时才需要重新绑定。</small></p></div>
      </div>
      <form class="device-form" @submit.prevent="approve">
        <span class="device-illustration"><MonitorSmartphone :size="34" /></span>
        <label for="device-code">插件显示的绑定码</label>
        <input id="device-code" v-model.trim="code" maxlength="9" placeholder="ABCD-EFGH" autocomplete="one-time-code" required />
        <p v-if="error" class="form-error">{{ error }}</p>
        <p v-if="success" class="success-message"><CheckCircle2 :size="17" />设备已授权，可以回到游戏。</p>
        <button class="primary-button full" type="submit" :disabled="busy">
          <Link2 :size="17" />{{ busy ? '授权中…' : '授权此设备' }}
        </button>
      </form>
    </div>
    <section class="authorized-devices">
      <header>
        <div><p class="eyebrow">AUTHORIZED DEVICES</p><h2>已绑定设备</h2></div>
        <button class="secondary-button" type="button" :disabled="loadingDevices" @click="loadDevices"><RefreshCw :size="15" />刷新</button>
      </header>
      <p v-if="loadingDevices" class="empty-copy">正在读取设备…</p>
      <p v-else-if="!devices.length" class="empty-copy">当前账户还没有已绑定设备。</p>
      <article v-for="device in devices" :key="device.id" class="authorized-device-row">
        <span class="device-status-dot"></span>
        <div><b>{{ device.name }}</b><small>绑定于 {{ new Date(device.createdAt).toLocaleString('zh-CN') }} · {{ device.id.slice(0, 8) }}</small></div>
        <button class="icon-button danger" type="button" title="撤销此设备" :disabled="busy" @click="revoke(device.id)"><Unplug :size="16" /></button>
      </article>
    </section>
  </section>
</template>
