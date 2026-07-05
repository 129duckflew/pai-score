<template>
  <div class="app-shell space-y-5">
    <div class="flex flex-col gap-4 rounded-3xl border border-white/10 bg-ink/60 p-5 shadow-glow backdrop-blur-xl sm:flex-row sm:items-center">
      <button class="btn-secondary w-fit" @click="$router.push('/lobby')"><ArrowLeft :size="17" />返回</button>
      <div class="flex-1">
        <p class="text-xs font-semibold uppercase tracking-[0.24em] text-gold/80">Archive</p>
        <h2 class="mt-2 text-3xl font-black text-white">历史记录</h2>
      </div>
    </div>

    <div class="panel" v-if="rooms.length">
      <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>房间</th>
            <th>状态</th>
            <th>人数</th>
            <th>时间</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rooms" :key="r.roomCode">
            <td>
              <strong>{{ roomDisplayName(r) }}</strong>
              <span class="room-code text-muted">邀请码: {{ r.roomCode }}</span>
            </td>
            <td>
              <span :class="'badge badge-' + r.status.toLowerCase()">{{ statusText(r.status) }}</span>
            </td>
            <td>{{ r.playerCount }}人</td>
            <td class="text-muted">{{ formatTime(r.createdAt) }}</td>
            <td>
              <button class="btn-secondary" @click="$router.push('/room/' + r.roomCode)">查看</button>
            </td>
          </tr>
        </tbody>
      </table>
      </div>
    </div>
    <div class="panel text-center" v-else>
      <p class="text-muted">暂无历史记录</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ArrowLeft } from '@lucide/vue'
import { getUserHistory } from '../services/api'

const rooms = ref([])

onMounted(async () => {
  try {
    const uid = localStorage.getItem('userId')
    if (uid) {
      rooms.value = await getUserHistory(uid)
    }
  } catch (e) {
    // ignore
  }
})

function statusText(s) {
  return s === 'WAITING' ? '等待中' : s === 'PLAYING' ? '进行中' : s === 'DISBANDED' ? '已解散' : '已结束'
}

function roomDisplayName(room) {
  return room.name || room.roomName || room.roomCode
}

function formatTime(t) {
  if (!t) return ''
  return t.substring(0, 16).replace('T', ' ')
}
</script>

<style scoped>
.room-code { display: block; font-size: 12px; margin-top: 2px; }
@media (max-width: 640px) { table { min-width: 560px; } }
</style>
