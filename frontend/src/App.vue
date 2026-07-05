<template>
  <div id="app-container">
    <router-view />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { wsService } from './services/socketio'

const router = useRouter()
let unsubAuthFailed = null

onMounted(() => {
  unsubAuthFailed = wsService.on('AUTH_FAILED', () => {
    localStorage.clear()
    router.push('/login')
  })
  const token = localStorage.getItem('token')
  if (token) {
    wsService.connect(token)
  }
})

onUnmounted(() => {
  if (unsubAuthFailed) unsubAuthFailed()
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; color: #333; }
#app-container { max-width: 800px; margin: 0 auto; padding: 20px; }
button { cursor: pointer; padding: 8px 16px; border: none; border-radius: 6px; font-size: 14px; transition: .2s; }
button:hover { opacity: .85; }
button:disabled { opacity: .5; cursor: not-allowed; }
input, select { padding: 8px 12px; border: 1px solid #ddd; border-radius: 6px; font-size: 14px; outline: none; }
input:focus, select:focus { border-color: #4a90d9; }
.card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,.08); margin-bottom: 16px; }
.btn-primary { background: #4a90d9; color: #fff; }
.btn-success { background: #52c41a; color: #fff; }
.btn-danger { background: #ff4d4f; color: #fff; }
.btn-danger-outline { background: #fff; color: #ff4d4f; border: 1px solid #ff4d4f; }
.btn-warning { background: #faad14; color: #fff; }
.btn-secondary { background: #e8e8e8; color: #333; }
.flex { display: flex; }
.flex-1 { flex: 1; }
.gap-8 { gap: 8px; }
.gap-12 { gap: 12px; }
.mt-12 { margin-top: 12px; }
.mt-16 { margin-top: 16px; }
.mb-12 { margin-bottom: 12px; }
.text-center { text-align: center; }
.text-right { text-align: right; }
.text-muted { color: #999; font-size: 13px; }
.text-lg { font-size: 18px; font-weight: 600; }
h2 { margin-bottom: 16px; }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #eee; }
th { font-weight: 600; color: #666; font-size: 13px; }
.alert { padding: 12px 16px; border-radius: 6px; margin-bottom: 12px; }
.alert-error { background: #fff2f0; border: 1px solid #ffccc7; color: #ff4d4f; }

@media (max-width: 600px) {
  #app-container { padding: 12px; }
  h2 { font-size: 16px; margin-bottom: 12px; }
  th, td { padding: 8px; font-size: 12px; }
  .text-lg { font-size: 16px; }
  input, select { font-size: 16px; }
  .card { padding: 14px; margin-bottom: 12px; }
  button { padding: 8px 12px; font-size: 13px; }
}
</style>
