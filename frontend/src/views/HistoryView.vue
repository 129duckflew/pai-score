<template>
  <div class="history">
    <div class="header flex items-center gap-12 mb-12">
      <button class="btn-secondary" @click="$router.push('/lobby')">← 返回</button>
      <h2 class="flex-1">历史记录</h2>
    </div>

    <div class="card" v-if="rooms.length">
      <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>房间码</th>
            <th>状态</th>
            <th>人数</th>
            <th>时间</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rooms" :key="r.roomCode">
            <td><strong>{{ r.roomCode }}</strong></td>
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
    <div class="card" v-else>
      <p class="text-center text-muted">暂无历史记录</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
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

function formatTime(t) {
  if (!t) return ''
  return t.substring(0, 16).replace('T', ' ')
}
</script>

<style scoped>
.items-center { align-items: center; }
.badge { font-size: 12px; padding: 2px 8px; border-radius: 10px; }
.badge-waiting { background: #e6f7ff; color: #1890ff; }
.badge-playing { background: #f6ffed; color: #52c41a; }
.badge-finished { background: #f5f5f5; color: #999; }
.badge-disbanded { background: #f5f5f5; color: #666; }

.table-scroll { overflow-x: auto; -webkit-overflow-scrolling: touch; }

@media (max-width: 600px) {
  .header { flex-wrap: wrap; gap: 8px; }
  .header h2 { font-size: 15px; }
}
</style>
