<template>
  <div class="login-page">
    <div class="card login-card">
      <h2 class="text-center">打牌记账</h2>
      <p class="text-center text-muted mb-12">输入用户名即可开始</p>
      <form @submit.prevent="handleRegister">
        <div class="flex gap-8">
          <input
            v-model="username"
            placeholder="请输入名称"
            class="flex-1"
            :disabled="loading"
            autofocus
          />
          <button type="submit" class="btn-primary" :disabled="loading || !username.trim()">
            {{ loading ? '登录中...' : '进入' }}
          </button>
        </div>
      </form>
      <p v-if="error" class="alert alert-error mt-12">{{ error }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../services/api'
import { wsService } from '../services/websocket'

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

<style scoped>
.login-page { display: flex; justify-content: center; padding-top: 80px; }
.login-card { width: 380px; }

@media (max-width: 600px) {
  .login-page { padding: 32px 12px 0; }
  .login-card { width: 100%; }
}
</style>
