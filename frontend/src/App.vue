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
#app-container { min-height: 100vh; }
</style>
