<template>
  <div class="app-shell flex items-center justify-center">
    <div class="w-full max-w-md">
      <div class="mb-5 flex justify-center">
        <div class="rounded-3xl border border-gold/30 bg-gold/15 p-4 text-gold shadow-gold">
          <Gamepad2 :size="36" />
        </div>
      </div>
      <div class="panel-strong">
        <p class="mb-2 text-center text-xs font-semibold uppercase tracking-[0.28em] text-gold/80">Pai Score</p>
        <h2 class="text-center text-4xl font-black text-white">打牌记账</h2>
        <p class="mb-6 mt-3 text-center text-sm text-emerald-100/60">进入牌桌，实时记分、投骰和查看战绩</p>
      <form @submit.prevent="handleRegister">
        <div class="flex flex-col gap-3 sm:flex-row">
          <input
            v-model="username"
            placeholder="请输入名称"
            class="min-h-12 flex-1 text-base"
            :disabled="loading"
            autofocus
          />
          <button type="submit" class="btn-primary min-h-12 sm:px-6" :disabled="loading || !username.trim()">
            <LogIn :size="18" />
            {{ loading ? '登录中...' : '进入' }}
          </button>
        </div>
      </form>
      <p v-if="error" class="alert alert-error mt-4">{{ error }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Gamepad2, LogIn } from '@lucide/vue'
import { register } from '../services/api'
import { wsService } from '../services/socketio'

const router = useRouter()
const username = ref('')
const loading = ref(false)
const error = ref('')

async function handleRegister() {
  if (!username.value.trim()) return
  loading.value = true
  error.value = ''
  try {
    const user = await register(username.value.trim())
    localStorage.setItem('token', user.token)
    localStorage.setItem('userId', user.id)
    localStorage.setItem('username', user.username)
    localStorage.setItem('avatar', user.avatar || '')
    wsService.connect(user.token)
    router.push('/lobby')
  } catch (e) {
    error.value = e.message || '登录失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>
