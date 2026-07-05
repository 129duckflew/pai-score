<template>
  <div class="app-shell space-y-5">
    <div class="flex flex-col gap-4 rounded-3xl border border-white/10 bg-ink/60 p-5 shadow-glow backdrop-blur-xl sm:flex-row sm:items-center">
      <div class="flex-1">
        <p class="text-xs font-semibold uppercase tracking-[0.24em] text-gold/80">Game Lobby</p>
        <h2 class="mt-2 text-3xl font-black text-white">打牌记账</h2>
        <div class="mt-3 flex flex-wrap items-center gap-3 text-sm text-emerald-100/60">
          <span class="socket-dot" :class="{ online: socketConnected }" :title="socketConnected ? 'Socket 已连接' : 'Socket 未连接'"></span>
          <span>{{ socketConnected ? '实时连接中' : '连接已断开' }}</span>
          <span>{{ username }}</span>
        </div>
      </div>
      <div class="flex gap-2">
        <button class="btn-secondary" @click="$router.push('/history')"><History :size="17" />历史</button>
        <button class="btn-secondary" @click="handleLogout"><LogOut :size="17" />退出</button>
      </div>
    </div>

    <div class="grid gap-4 lg:grid-cols-[0.95fr_1.05fr]">
      <div class="panel-strong flex flex-col justify-between gap-5">
        <div>
          <p class="text-sm font-semibold text-gold">开一桌</p>
          <h3 class="mt-2 text-2xl font-black text-white">创建新的计分房间</h3>
          <p class="mt-2 text-sm text-emerald-100/60">生成邀请码，邀请玩家入座后开始记录牌局。</p>
        </div>
        <button class="btn-success w-full justify-center" @click="createRoom" :disabled="creating">
          <Plus :size="18" />{{ creating ? '创建中...' : '创建新房间' }}
        </button>
      </div>

      <div class="panel">
        <p class="text-sm font-semibold text-gold">加入牌桌</p>
        <form @submit.prevent="joinRoom" class="mt-4 flex flex-col gap-3 sm:flex-row">
          <input
            v-model="roomCode"
            placeholder="输入6位邀请码"
            class="min-h-12 flex-1 text-base uppercase"
            maxlength="6"
          />
          <button type="submit" class="btn-primary min-h-12" :disabled="!roomCode.trim()">
            <LogIn :size="18" />加入
          </button>
        </form>
      </div>
    </div>

    <div class="panel" v-if="activeRooms.length">
      <div class="mb-4 flex items-center justify-between gap-3">
        <h3 class="text-xl font-bold text-white">可加入的房间</h3>
        <span class="text-muted">{{ activeRooms.length }} 桌</span>
      </div>
      <div class="grid gap-3 md:grid-cols-2">
        <div v-for="r in activeRooms" :key="'a-'+r.roomCode" class="room-tile">
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <strong class="truncate text-white">{{ roomDisplayName(r) }}</strong>
              <span class="badge badge-waiting">等待中</span>
            </div>
            <span class="mt-1 block text-xs text-emerald-100/50">邀请码: {{ r.roomCode }}</span>
          </div>
          <span class="text-sm text-emerald-100/60">{{ r.playerCount }}人</span>
          <button class="btn-primary" @click="joinRoomByCode(r.roomCode)">加入</button>
        </div>
      </div>
    </div>

    <div class="panel" v-if="rooms.length">
      <div class="mb-4 flex items-center justify-between gap-3">
        <h3 class="text-xl font-bold text-white">我的房间</h3>
        <span class="text-muted">历史与进行中</span>
      </div>
      <div class="grid gap-3 md:grid-cols-2">
        <div v-for="r in rooms" :key="r.roomCode" class="room-tile">
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <strong class="truncate text-white">{{ roomDisplayName(r) }}</strong>
              <span :class="'badge badge-' + r.status.toLowerCase()">{{ statusText(r.status) }}</span>
            </div>
            <span class="mt-1 block text-xs text-emerald-100/50">邀请码: {{ r.roomCode }}</span>
          </div>
          <span class="text-sm text-emerald-100/60">{{ r.playerCount }}人</span>
          <button class="btn-secondary" @click="$router.push('/room/' + r.roomCode)">进入</button>
        </div>
      </div>
    </div>

    <p v-if="loading" class="text-center text-sm text-emerald-100/60">加载中...</p>
    <p v-if="error" class="alert alert-error">{{ error }}</p>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { History, LogIn, LogOut, Plus } from '@lucide/vue'
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
.socket-dot { width: 10px; height: 10px; border-radius: 999px; background: rgba(255,255,255,.35); flex: 0 0 auto; }
.socket-dot.online { background: #35d399; box-shadow: 0 0 0 4px rgba(53,211,153,.16); }
.room-tile { display: flex; align-items: center; gap: 12px; border: 1px solid rgba(255,255,255,.1); border-radius: 18px; background: rgba(255,255,255,.06); padding: 14px; }
@media (max-width: 640px) { .room-tile { flex-wrap: wrap; } }
</style>
