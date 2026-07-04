<template>
  <div class="room">
    <div class="header flex items-center gap-12 mb-12">
      <button class="btn-secondary" @click="$router.push('/lobby')">← 返回</button>
      <h2 class="flex-1">房间 {{ roomCode }}</h2>
      <span v-if="roomState" :class="'badge badge-' + roomState.status.toLowerCase()">
        {{ statusText(roomState.status) }}
      </span>
    </div>

    <div class="card">
      <div class="flex items-center gap-8 mb-12">
        <span class="text-lg">玩家列表</span>
        <span class="text-muted" v-if="roomState">当前 {{ players.length }} 人</span>
      </div>
      <div v-if="players.length" class="player-grid">
        <div
          v-for="p in players"
          :key="p.playerId"
          class="player-card"
          :class="{ 'is-self': p.userId === myUserId }"
        >
          <div
            class="avatar"
            :class="{ clickable: isActive && p.userId !== myUserId }"
            @click="isActive && p.userId !== myUserId && openScoreModal(p)"
          >
            <span class="avatar-emoji">{{ p.avatar || '?' }}</span>
            <span v-if="p.userId === myUserId" class="self-label">自己</span>
          </div>
          <div class="player-info">
            <div class="player-name">
              {{ p.username }}
              <span v-if="p.userId === roomState?.hostId" class="host-tag">房主</span>
            </div>
            <div class="player-score">{{ p.totalScore }} 分</div>
          </div>
        </div>
      </div>
      <p v-else class="text-muted">暂无玩家</p>
    </div>

    <!-- Waiting: host controls -->
    <div class="card" v-if="isWaiting && isHost">
      <p class="text-muted mb-12">邀请码: <strong>{{ roomCode }}</strong>（分享给好友加入）</p>
      <button class="btn-success" @click="startGame" :disabled="players.length < 2">
        开始游戏（至少2人）
      </button>
    </div>

    <!-- Waiting: non-host -->
    <div class="card" v-if="isWaiting && !isHost">
      <p class="text-muted">等待房主开始游戏...</p>
      <button class="btn-danger mt-12" @click="leaveRoom">离开房间</button>
    </div>

    <!-- Playing: hint -->
    <div class="card" v-if="isActive">
      <p class="text-muted">点击其他玩家的头像为其记分</p>
    </div>

    <!-- Host controls during play -->
    <div class="card" v-if="isActive && isHost">
      <div class="flex gap-8">
        <button class="btn-danger" @click="endGame">结束游戏</button>
      </div>
    </div>

    <!-- Score entries history -->
    <div class="card" v-if="entries.length">
      <h3 class="mb-12">记分记录</h3>
      <table>
        <thead>
          <tr>
            <th>时间</th>
            <th>玩家</th>
            <th>分数</th>
            <th>备注</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="e in sortedEntries" :key="e.id">
            <td class="text-muted">{{ formatTime(e.createdAt) }}</td>
            <td>{{ playerName(e.targetPlayerId) }}</td>
            <td :class="e.score >= 0 ? 'score-pos' : 'score-neg'">
              {{ e.score >= 0 ? '+' : '' }}{{ e.score }}
            </td>
            <td class="text-muted">{{ e.note || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Game over summary -->
    <div v-if="isFinished" class="card">
      <h3 class="mb-12">最终排名</h3>
      <table>
        <thead>
          <tr><th>排名</th><th>玩家</th><th>总分</th></tr>
        </thead>
        <tbody>
          <tr v-for="(p, i) in sortedPlayers" :key="p.playerId">
            <td>{{ i + 1 }}</td>
            <td>{{ p.username }}</td>
            <td>{{ p.totalScore }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <p v-if="error" class="alert alert-error">{{ error }}</p>

    <!-- Score Modal -->
    <div v-if="showModal && scoringTarget" class="modal-overlay" @click.self="closeModal">
      <div class="modal">
        <div class="modal-header">
          <span class="modal-avatar">{{ scoringTarget.avatar || '?' }}</span>
          <span class="modal-title">给 {{ scoringTarget.username }} 记分</span>
        </div>
        <div class="modal-body">
          <label class="mb-4">分数</label>
          <input
            v-model.number="scoreInput"
            type="number"
            placeholder="输入分数"
            class="full-width"
            autofocus
            @keyup.enter="submitModalScore"
          />
          <label class="mt-12 mb-4">备注（可选）</label>
          <input
            v-model="noteInput"
            type="text"
            placeholder="例如：自摸、点炮等"
            class="full-width"
            @keyup.enter="submitModalScore"
          />
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="closeModal">取消</button>
          <button class="btn-primary" @click="submitModalScore" :disabled="scoreInput === ''">
            确认记分
          </button>
        </div>
        <p v-if="modalError" class="alert alert-error mt-12">{{ modalError }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { wsService } from '../services/websocket'

const route = useRoute()
const router = useRouter()
const roomCode = route.params.roomCode

const roomState = ref(null)
const players = ref([])
const entries = ref([])
const error = ref('')

const myUserId = computed(() => Number(localStorage.getItem('userId')))
const isHost = computed(() => roomState.value?.hostId === myUserId.value)
const isWaiting = computed(() => roomState.value?.status === 'WAITING')
const isActive = computed(() => roomState.value?.status === 'PLAYING')
const isFinished = computed(() => roomState.value?.status === 'FINISHED')

// Modal state
const showModal = ref(false)
const scoringTarget = ref(null)
const scoreInput = ref('')
const noteInput = ref('')
const modalError = ref('')

const sortedEntries = computed(() => {
  return [...entries.value].sort((a, b) => a.id - b.id)
})

const sortedPlayers = computed(() => {
  return [...players.value].sort((a, b) => b.totalScore - a.totalScore)
})

let unsubs = []

onMounted(() => {
  unsubs.push(wsService.on('ROOM_STATE', handleRoomState))
  unsubs.push(wsService.on('PLAYER_LIST', handlePlayerList))
  unsubs.push(wsService.on('GAME_STARTED', handleGameStarted))
  unsubs.push(wsService.on('SCORE_ADDED', handleScoreAdded))
  unsubs.push(wsService.on('GAME_OVER', handleGameOver))
  unsubs.push(wsService.on('ERROR', (msg) => { error.value = msg.message }))

  wsService.send('JOIN_ROOM', { roomCode })
})

onUnmounted(() => {
  unsubs.forEach(fn => fn())
})

function handleRoomState(msg) {
  roomState.value = msg
  players.value = msg.players || []
  entries.value = msg.entries || []
}

function handlePlayerList(msg) {
  players.value = msg.players || []
}

function handleGameStarted(msg) {
  roomState.value = { ...roomState.value, status: 'PLAYING' }
}

function handleScoreAdded(msg) {
  entries.value.push(msg.entry)
  players.value = msg.players || []
}

function handleGameOver(msg) {
  roomState.value = { ...roomState.value, status: 'FINISHED' }
  players.value = msg.players || []
  entries.value = msg.entries || []
}

function openScoreModal(player) {
  scoringTarget.value = player
  scoreInput.value = ''
  noteInput.value = ''
  modalError.value = ''
  showModal.value = true
}

function closeModal() {
  showModal.value = false
  scoringTarget.value = null
  scoreInput.value = ''
  noteInput.value = ''
  modalError.value = ''
}

function submitModalScore() {
  if (scoreInput.value === '') return
  modalError.value = ''
  wsService.send('SUBMIT_SCORE', {
    roomCode,
    targetPlayerId: scoringTarget.value.playerId,
    score: Number(scoreInput.value),
    note: noteInput.value || ''
  })
  closeModal()
}

function startGame() {
  wsService.send('START_GAME', { roomCode })
}

function endGame() {
  if (confirm('确认结束游戏？')) {
    wsService.send('END_GAME', { roomCode })
  }
}

function leaveRoom() {
  wsService.send('LEAVE_ROOM', { roomCode })
  router.push('/lobby')
}

function playerName(playerId) {
  const p = players.value.find(p => p.playerId === playerId)
  return p ? p.username : '?'
}

function statusText(s) {
  return s === 'WAITING' ? '等待中' : s === 'PLAYING' ? '进行中' : '已结束'
}

function formatTime(t) {
  if (!t) return ''
  return t.substring(0, 19).replace('T', ' ')
}
</script>

<style scoped>
.items-center { align-items: center; }

.player-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

.player-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 8px;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  background: #fafafa;
  transition: box-shadow 0.2s;
}

.player-card:not(.is-self) {
  cursor: default;
}

.avatar {
  position: relative;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fff;
  border: 2px solid #e8e8e8;
  margin-bottom: 8px;
  transition: transform 0.15s, border-color 0.15s;
}

.avatar.clickable {
  cursor: pointer;
}

.avatar.clickable:hover {
  transform: scale(1.1);
  border-color: #1890ff;
  box-shadow: 0 0 12px rgba(24, 144, 255, 0.3);
}

.avatar-emoji {
  font-size: 28px;
  line-height: 1;
}

.self-label {
  position: absolute;
  bottom: -6px;
  font-size: 10px;
  background: #e6f7ff;
  color: #1890ff;
  padding: 0 6px;
  border-radius: 6px;
  white-space: nowrap;
}

.player-info {
  text-align: center;
  width: 100%;
}

.player-name {
  font-weight: 600;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-score {
  font-size: 16px;
  font-weight: 700;
  color: #333;
  margin-top: 4px;
}

.host-tag { font-size: 11px; background: #fff7e6; color: #fa8c16; padding: 1px 6px; border-radius: 8px; margin-left: 4px; }
.badge { font-size: 12px; padding: 2px 10px; border-radius: 10px; }
.badge-waiting { background: #e6f7ff; color: #1890ff; }
.badge-playing { background: #f6ffed; color: #52c41a; }
.badge-finished { background: #f5f5f5; color: #999; }
.score-pos { color: #52c41a; font-weight: 600; }
.score-neg { color: #ff4d4f; font-weight: 600; }

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  width: 340px;
  max-width: 90vw;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.modal-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.modal-avatar {
  font-size: 36px;
  line-height: 1;
}

.modal-title {
  font-size: 18px;
  font-weight: 600;
}

.modal-body label {
  display: block;
  font-size: 13px;
  color: #666;
}

.modal-footer {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 16px;
}

.full-width {
  width: 100%;
  box-sizing: border-box;
}

input[type="number"].full-width {
  width: 100%;
}
</style>
