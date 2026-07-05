<template>
  <div class="lobby">
    <div class="header flex items-center gap-12 mb-12">
      <h2 class="flex-1">打牌记账</h2>
      <span class="socket-dot" :class="{ online: socketConnected }" :title="socketConnected ? 'Socket 已连接' : 'Socket 未连接'"></span>
      <span class="text-muted">{{ username }}</span>
      <button class="btn-secondary" @click="$router.push('/history')">历史</button>
      <button class="btn-secondary" @click="handleLogout">退出</button>
    </div>

    <div class="card">
      <h3>创建房间</h3>
      <button class="btn-success mt-12" @click="createRoom" :disabled="creating">
        {{ creating ? '创建中...' : '创建新房间' }}
      </button>
    </div>

    <div class="card">
      <h3>加入房间</h3>
      <form @submit.prevent="joinRoom" class="flex gap-8 mt-12">
        <input
          v-model="roomCode"
          placeholder="输入6位邀请码"
          class="flex-1"
          maxlength="6"
          style="text-transform: uppercase;"
        />
        <button type="submit" class="btn-primary" :disabled="!roomCode.trim()">加入</button>
      </form>
    </div>

    <div class="card" v-if="activeRooms.length">
      <h3>可加入的房间</h3>
      <div v-for="r in activeRooms" :key="'a-'+r.roomCode" class="room-item flex items-center gap-8 mt-12">
        <span class="flex-1 room-title-block">
          <strong>{{ roomDisplayName(r) }}</strong>
          <span class="badge badge-waiting">等待中</span>
          <span class="room-code text-muted">邀请码: {{ r.roomCode }}</span>
        </span>
        <span class="text-muted">{{ r.playerCount }}人</span>
        <button class="btn-primary" @click="joinRoomByCode(r.roomCode)">加入</button>
      </div>
    </div>

    <div class="card" v-if="rooms.length">
      <h3>我的房间</h3>
      <div v-for="r in rooms" :key="r.roomCode" class="room-item flex items-center gap-8 mt-12">
        <span class="flex-1 room-title-block">
          <strong>{{ roomDisplayName(r) }}</strong>
          <span :class="'badge badge-' + r.status.toLowerCase()">{{ statusText(r.status) }}</span>
          <span class="room-code text-muted">邀请码: {{ r.roomCode }}</span>
        </span>
        <span class="text-muted">{{ r.playerCount }}人</span>
        <button class="btn-secondary" @click="$router.push('/room/' + r.roomCode)">进入</button>
      </div>
    </div>

    <p v-if="loading" class="text-muted text-center">加载中...</p>
    <p v-if="error" class="alert alert-error">{{ error }}</p>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { wsService } from '../services/socketio'
import { getUserHistory, getActiveRooms } from '../services/api'

const router = useRouter()
const username = ref(localStorage.getItem('username') || '')
const roomCode = ref('')
const creating = ref(false)
const error = ref('')
const rooms = ref([])
const activeRooms = ref([])
const loading = ref(false)
const socketConnected = ref(wsService.connected)
let unsubs = []

onMounted(() => {
  loadHistory()
  unsubs.push(wsService.on('ROOM_CREATED', (msg) => {
    router.push('/room/' + msg.roomCode)
  }))
  unsubs.push(wsService.on('ROOM_JOINED', (msg) => {
    router.push('/room/' + msg.roomCode)
  }))
  unsubs.push(wsService.on('ROOM_DESTROYED', () => {
    loadHistory()
  }))
  unsubs.push(wsService.on('connected', () => {
    socketConnected.value = true
    loadHistory()
  }))
  unsubs.push(wsService.on('disconnected', () => {
    socketConnected.value = false
  }))
  unsubs.push(wsService.on('ERROR', (msg) => {
    error.value = msg.message
  }))
})

onUnmounted(() => {
  unsubs.forEach(fn => fn())
})

async function loadHistory() {
  loading.value = true
  try {
    const uid = localStorage.getItem('userId')
    const [userRooms, allActive] = await Promise.all([
      uid ? getUserHistory(uid) : Promise.resolve([]),
      getActiveRooms()
    ])
    rooms.value = userRooms
    const userCodes = new Set(userRooms.map(r => r.roomCode))
    activeRooms.value = allActive.filter(r => !userCodes.has(r.roomCode))
  } catch (e) {
    error.value = '加载房间列表失败'
  } finally {
    loading.value = false
  }
}

function joinRoomByCode(code) {
  error.value = ''
  wsService.send('JOIN_ROOM', { roomCode: code })
}

function createRoom() {
  creating.value = true
  error.value = ''
  wsService.send('CREATE_ROOM')
  setTimeout(() => { creating.value = false }, 2000)
}

function joinRoom() {
  if (!roomCode.value.trim()) return
  error.value = ''
  wsService.send('JOIN_ROOM', { roomCode: roomCode.value.trim().toUpperCase() })
}

function handleLogout() {
  wsService.disconnect()
  localStorage.clear()
  router.push('/login')
}

function statusText(s) {
  return s === 'WAITING' ? '等待中' : s === 'PLAYING' ? '进行中' : s === 'DISBANDED' ? '已解散' : '已结束'
}

function roomDisplayName(room) {
  return room.name || room.roomName || room.roomCode
}
</script>

<style scoped>
.items-center { align-items: center; }
.room-item { padding: 8px 0; }
.room-title-block { display: block; min-width: 0; }
.room-code { display: block; font-size: 12px; margin-top: 2px; }
.socket-dot { width: 10px; height: 10px; border-radius: 50%; background: #bfbfbf; flex: 0 0 auto; }
.socket-dot.online { background: #52c41a; box-shadow: 0 0 0 3px rgba(82,196,26,.14); }
.badge { font-size: 12px; padding: 2px 8px; border-radius: 10px; margin-left: 8px; }
.badge-waiting { background: #e6f7ff; color: #1890ff; }
.badge-playing { background: #f6ffed; color: #52c41a; }
.badge-finished { background: #f5f5f5; color: #999; }
.badge-disbanded { background: #f5f5f5; color: #666; }

@media (max-width: 600px) {
  .header { flex-wrap: wrap; gap: 6px; }
  .header h2 { font-size: 15px; width: 100%; margin-bottom: 4px; }
  .header .text-muted { font-size: 12px; }
  .room-item { flex-wrap: wrap; gap: 6px; padding: 10px 0; }
  .room-item .flex-1 { min-width: 140px; }
  .room-item button { flex-shrink: 0; }
  .flex.gap-8 { gap: 6px; }
}
</style>
