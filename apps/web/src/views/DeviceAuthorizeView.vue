<script setup lang="ts">
import { ref } from 'vue'
import { CheckCircle2, Link2, MonitorSmartphone } from 'lucide-vue-next'
import { api, ApiError } from '../api/client'

const code = ref('')
const busy = ref(false)
const success = ref(false)
const error = ref('')

async function approve() {
  busy.value = true
  success.value = false
  error.value = ''
  try {
    await api.approveDevice(code.value)
    success.value = true
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '设备绑定失败'
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
        <p>插件只保存可撤销的设备令牌，不保存 FFLogs、模型或服务端主密钥。</p>
      </div>
    </div>
    <div class="device-card">
      <div class="device-steps">
        <div><span>1</span><p><b>在游戏中请求绑定</b><small>打开 VedaAxis 插件，选择“连接账户”。</small></p></div>
        <div><span>2</span><p><b>输入一次性代码</b><small>代码有效期 10 分钟，只能使用一次。</small></p></div>
        <div><span>3</span><p><b>回到游戏确认</b><small>插件会自动轮询并完成授权。</small></p></div>
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
  </section>
</template>
