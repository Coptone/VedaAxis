<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, KeyRound, ShieldCheck } from 'lucide-vue-next'
import { ApiError } from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const email = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  busy.value = true
  error.value = ''
  try {
    if (mode.value === 'login') await auth.login(email.value, password.value)
    else await auth.register(email.value, password.value)
    const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/plans'
    await router.push(target)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '无法连接服务，请稍后重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="auth-layout">
    <div class="auth-story">
      <p class="eyebrow">BATTLE RESOURCE PLANNING</p>
      <h1>把减伤表变成<br /><span>可执行的战斗轨道</span></h1>
      <p class="story-copy">在网页规划八人或四人减伤，战斗前同步不可变快照，进入窗口后只高亮属于你的技能。</p>
      <div class="principle-list">
        <div><b>01</b><span>事件锚点持续校时</span></div>
        <div><b>02</b><span>规则引擎拦截非法方案</span></div>
        <div><b>03</b><span>插件只提示，不代替操作</span></div>
      </div>
    </div>

    <form class="auth-card" @submit.prevent="submit">
      <div class="card-icon"><KeyRound :size="22" /></div>
      <p class="eyebrow">VEDAAXIS ACCOUNT</p>
      <h2>{{ mode === 'login' ? '继续你的计划' : '建立个人空间' }}</h2>
      <p>{{ mode === 'login' ? '登录后同步计划、插件设备与个人复盘。' : '个人执行数据默认只属于你的账户。' }}</p>

      <label>
        <span>邮箱</span>
        <input v-model.trim="email" type="email" autocomplete="email" placeholder="you@example.com" required />
      </label>
      <label>
        <span>密码</span>
        <input
          v-model="password"
          type="password"
          :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
          placeholder="至少 10 个字符"
          minlength="10"
          required
        />
      </label>
      <p v-if="error" class="form-error">{{ error }}</p>
      <button class="primary-button full" type="submit" :disabled="busy">
        {{ busy ? '处理中…' : mode === 'login' ? '登录' : '注册并登录' }}
        <ArrowRight :size="17" />
      </button>
      <button class="text-button" type="button" @click="mode = mode === 'login' ? 'register' : 'login'">
        {{ mode === 'login' ? '没有账户？创建账户' : '已有账户？返回登录' }}
      </button>
      <div class="privacy-note"><ShieldCheck :size="15" />密钥与第三方 API 凭据不会保存在插件端</div>
    </form>
  </section>
</template>
