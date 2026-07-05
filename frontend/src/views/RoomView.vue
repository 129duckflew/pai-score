<template>
  <div class="app-shell room space-y-5">
    <div class="header flex flex-col gap-4 rounded-3xl border border-white/10 bg-ink/65 p-5 shadow-glow backdrop-blur-xl sm:flex-row sm:items-center">
      <button class="btn-secondary w-fit" @click="goBack"><ArrowLeft :size="17" />返回</button>
      <div class="flex-1">
        <p class="text-xs font-semibold uppercase tracking-[0.24em] text-gold/80">Room {{ roomCode }}</p>
        <h2 class="mt-2 text-3xl font-black text-white">{{ roomDisplayName }}</h2>
      </div>
      <span class="socket-dot" :class="{ online: socketConnected }" :title="socketConnected ? 'Socket 已连接' : 'Socket 未连接'"></span>
      <span v-if="roomState" :class="'badge badge-' + roomState.status.toLowerCase()">
        {{ statusText(roomState.status) }}
      </span>
    </div>

    <!-- Toast notifications -->
    <div class="toast-container" v-if="toasts.length">
      <div v-for="t in toasts" :key="t.id" class="toast">
        <span class="toast-avatar">{{ t.avatar || '?' }}</span>
        <span>{{ t.message }}</span>
      </div>
    </div>

    <div class="panel-strong">
      <div class="mb-4 flex items-center gap-3">
        <span class="text-xl font-black text-white">玩家列表</span>
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
            <span class="player-online-dot" :class="{ online: p.online }" :title="p.online ? '在线' : '离线'"></span>
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
    <div class="panel" v-if="isWaiting && isHost">
      <p class="mb-4 text-sm text-emerald-100/65">邀请码: <strong class="text-gold">{{ roomCode }}</strong>（分享给好友加入）</p>
      <div class="flex flex-col gap-3 sm:flex-row">
        <button class="btn-success flex-1" @click="startGame" :disabled="players.length < 2">
          <Play :size="18" />
          开始游戏（至少2人）
        </button>
        <button class="btn-danger-outline" @click="confirmDestroyRoom"><Trash2 :size="18" />解散房间</button>
      </div>
    </div>

    <!-- Waiting: non-host -->
    <div class="panel" v-if="isWaiting && !isHost">
      <p class="text-muted">等待房主开始游戏...</p>
      <button class="btn-danger mt-4" @click="leaveRoom">离开房间</button>
    </div>

    <!-- Playing: hint -->
    <div class="panel" v-if="isActive">
      <p class="text-muted">点击其他玩家的头像为其记分</p>
    </div>

    <!-- Host controls during play -->
    <div class="panel" v-if="isActive && isHost">
      <div class="flex gap-3">
        <button class="btn-danger" @click="endGame"><Square :size="18" />结束游戏</button>
      </div>
    </div>

    <!-- Mahjong Calculator -->
    <div class="panel">
      <div class="flex items-center gap-3">
        <span class="flex-1 text-xl font-black text-white">麻将计算器</span>
        <button class="btn-primary" @click="showMahjongCalc = true"><Calculator :size="18" />打开</button>
      </div>
      <p class="mt-3 text-sm text-emerald-100/55">输入手牌，分析胡牌牌型与听牌推荐</p>
    </div>

    <!-- Score entries history -->
    <div class="panel" v-if="entries.length">
      <h3 class="mb-4 text-xl font-black text-white">记分记录</h3>
      <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>时间</th>
            <th>操作</th>
            <th>分数</th>
            <th>备注</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="e in sortedEntries" :key="e.id" :class="e.type === 'DICE_ROLL' ? 'dice-row' : ''">
            <td class="text-muted">{{ formatTime(e.createdAt) }}</td>
            <td>
              <template v-if="e.type === 'DICE_ROLL'">{{ playerName(e.targetPlayerId) }} 🎲</template>
              <template v-else>{{ e._display.operationText }}</template>
            </td>
            <td>
              <template v-if="e.type === 'DICE_ROLL'">🎲 {{ e.score }}</template>
              <template v-else>
                <span :class="e._display.cssClass">
                  {{ e._display.displayScore }}
                </span>
              </template>
            </td>
            <td class="text-muted">
              <template v-if="e.type === 'DICE_ROLL'">{{ e.note || '-' }}</template>
              <template v-else>{{ e.note || '-' }}</template>
            </td>
          </tr>
        </tbody>
      </table>
      </div>
    </div>

    <!-- Game over summary -->
    <div v-if="isFinished" class="panel-strong">
      <h3 class="mb-4 text-xl font-black text-white">最终排名</h3>
      <div class="table-scroll">
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
          <label class="mb-2">分数</label>
          <input
            ref="scoreInputEl"
            v-model.number="scoreInput"
            type="number"
            inputmode="decimal"
            placeholder="输入分数"
            class="full-width"
            autofocus
            @keyup.enter="submitModalScore"
          />
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="closeModal">取消</button>
          <button class="btn-primary" @click="submitModalScore" :disabled="scoreInput === ''">
            确认记分
          </button>
        </div>
        <p v-if="modalError" class="alert alert-error mt-4">{{ modalError }}</p>
      </div>
    </div>

    <!-- Dice Roller -->
    <DiceRoller
      v-if="diceRoller.show"
      :dice="diceRoller.dice"
      :roller="diceRoller.roller"
      @close="diceRoller.show = false"
    />

    <!-- Mahjong Calculator modal -->
    <MahjongCalculator v-if="showMahjongCalc" @close="showMahjongCalc = false" />

    <!-- Floating action menu -->
    <div class="fab-container" v-if="isActive">
      <div v-if="fabOpen" class="fab-menu">
        <button class="fab-menu-item" @click="rollDice"><Dice5 :size="17" />投掷骰子</button>
      </div>
      <button class="fab-btn" @click="fabOpen = !fabOpen">
        <X v-if="fabOpen" :size="24" />
        <Dice5 v-else :size="25" />
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Calculator, Dice5, Play, Square, Trash2, X } from '@lucide/vue'
import { wsService } from '../services/socketio'
import DiceRoller from '../components/DiceRoller.vue'
import MahjongCalculator from '../components/MahjongCalculator.vue'
import { getScoreDisplay } from '../utils/score-display'

const route = useRoute()
const router = useRouter()
const roomCode = route.params.roomCode

const roomState = ref(null)
const players = ref([])
const entries = ref([])
const error = ref('')
const leavingRef = ref(false)
const socketConnected = ref(wsService.connected)

const myUserId = computed(() => Number(localStorage.getItem('userId')))
const isHost = computed(() => roomState.value?.hostId === myUserId.value)
const isWaiting = computed(() => roomState.value?.status === 'WAITING')
const isActive = computed(() => roomState.value?.status === 'PLAYING')
const isFinished = computed(() => roomState.value?.status === 'FINISHED')
const roomDisplayName = computed(() => roomState.value?.roomName || roomState.value?.name || `房间 ${roomCode}`)

// Modal state
const showModal = ref(false)
const scoringTarget = ref(null)
const scoreInput = ref('')
const scoreInputEl = ref(null)
const modalError = ref('')

// Dice roller
const diceRoller = ref({ show: false, dice: [1, 1], roller: null })
const fabOpen = ref(false)

// Mahjong Calculator
const showMahjongCalc = ref(false)

// Toast notifications
const toasts = ref([])
let toastId = 0
function addToast(message, avatar) {
  const id = ++toastId
  toasts.value.push({ id, message, avatar })
  setTimeout(() => {
    toasts.value = toasts.value.filter(t => t.id !== id)
  }, 4000)
}

const sortedEntries = computed(() => {
  const sorted = [...entries.value].sort((a, b) => b.id - a.id)
  return sorted.map(e => ({
    ...e,
    _display: e.type === 'SCORE' ? getScoreDisplay(e, myUserId.value, players.value) : null,
  }))
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
  unsubs.push(wsService.on('DICE_ROLL_RESULT', handleDiceRollResult))
  unsubs.push(wsService.on('ROOM_DESTROYED', handleRoomDestroyed))
  unsubs.push(wsService.on('PLAYER_JOINED', handlePlayerJoined))
  unsubs.push(wsService.on('ERROR', (msg) => { error.value = msg.message }))
  unsubs.push(wsService.on('connected', () => {
    socketConnected.value = true
    wsService.send('GET_ROOM_STATE', { roomCode })
  }))
  unsubs.push(wsService.on('disconnected', () => {
    socketConnected.value = false
  }))

  wsService.send('GET_ROOM_STATE', { roomCode })
})

onUnmounted(() => {
  unsubs.forEach(fn => fn())
  if (wsService.connected && !leavingRef.value) {
    wsService.send('LEAVE_ROOM', { roomCode })
  }
})

function handleRoomState(msg) {
  roomState.value = msg
  players.value = msg.players || []
  entries.value = msg.entries || []
}

function handlePlayerList(msg) {
  players.value = msg.players || []
}

function handlePlayerJoined(msg) {
  addToast(`${msg.username} 加入了房间`, msg.avatar)
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

function handleRoomDestroyed(msg) {
  roomState.value = { ...roomState.value, status: 'DISBANDED' }
}

function handleDiceRollResult(msg) {
  entries.value.push(msg.entry)
  players.value = msg.players || []
  diceRoller.value = {
    show: true,
    dice: msg.dice || [1, 1],
    roller: msg.roller || null
  }
  fabOpen.value = false
}

function rollDice() {
  wsService.send('ROLL_DICE', { roomCode })
  fabOpen.value = false
}

function openScoreModal(player) {
  scoringTarget.value = player
  scoreInput.value = ''
  modalError.value = ''
  showModal.value = true
  nextTick(() => { scoreInputEl.value?.focus() })
}

function closeModal() {
  showModal.value = false
  scoringTarget.value = null
  scoreInput.value = ''
  modalError.value = ''
}

function submitModalScore() {
  if (scoreInput.value === '') return
  modalError.value = ''
  wsService.send('SUBMIT_SCORE', {
    roomCode,
    targetPlayerId: scoringTarget.value.playerId,
    score: Number(scoreInput.value),
    note: ''
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
  leavingRef.value = true
  wsService.send('LEAVE_ROOM', { roomCode })
  router.push('/lobby')
}

function goBack() {
  if (isHost.value) {
    if (confirm('退出会解散房间，其他玩家将被移出，确定退出吗？')) {
      leaveRoom()
    }
  } else {
    leaveRoom()
  }
}

function confirmDestroyRoom() {
  if (confirm('解散房间后所有玩家将被移出，确定要解散吗？')) {
    leaveRoom()
  }
}

function playerName(playerId) {
  const p = players.value.find(p => p.playerId === playerId)
  return p ? p.username : '?'
}

function statusText(s) {
  return s === 'WAITING' ? '等待中' : s === 'PLAYING' ? '进行中' : s === 'DISBANDED' ? '已解散' : '已结束'
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
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255,255,255,.12), rgba(255,255,255,.055));
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}

.player-card.is-self {
  border-color: rgba(243, 201, 105, 0.55);
  box-shadow: 0 0 28px rgba(243, 201, 105, 0.12);
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
  background: rgba(255,255,255,.92);
  border: 2px solid rgba(243, 201, 105, 0.32);
  margin-bottom: 8px;
  transition: transform 0.15s, border-color 0.15s;
}

.avatar.clickable {
  cursor: pointer;
  touch-action: manipulation;
  user-select: none;
  -webkit-user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.avatar.clickable:hover {
  transform: translateY(-2px) scale(1.08);
  border-color: #f3c969;
  box-shadow: 0 0 18px rgba(243, 201, 105, 0.32);
}

.avatar-emoji {
  font-size: 28px;
  line-height: 1;
}

.socket-dot,
.player-online-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(255,255,255,.35);
  flex: 0 0 auto;
}

.socket-dot.online,
.player-online-dot.online {
  background: #35d399;
  box-shadow: 0 0 0 3px rgba(53,211,153,.16);
}

.player-online-dot {
  position: absolute;
  right: 3px;
  bottom: 5px;
  border: 2px solid #0d1f1b;
}

.self-label {
  position: absolute;
  bottom: -6px;
  font-size: 10px;
  background: #f3c969;
  color: #09110f;
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
  color: #fff;
}

.player-score {
  font-size: 16px;
  font-weight: 700;
  color: #f3c969;
  margin-top: 4px;
}

.host-tag { font-size: 11px; background: rgba(243,201,105,.18); color: #f3c969; padding: 1px 6px; border-radius: 8px; margin-left: 4px; }

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.62);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  background: rgba(9, 17, 15, 0.96);
  border: 1px solid rgba(255,255,255,.12);
  border-radius: 18px;
  padding: 24px;
  width: 340px;
  max-width: 90vw;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.45);
  color: #fff;
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
  color: rgba(209, 250, 229, 0.65);
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

/* Floating action button */
.fab-container {
  position: fixed;
  bottom: 32px;
  right: 32px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  z-index: 150;
}

.fab-btn {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: none;
  background: #f3c969;
  color: #09110f;
  font-size: 24px;
  cursor: pointer;
  box-shadow: 0 14px 34px rgba(243, 201, 105, 0.28);
  transition: transform 0.15s, box-shadow 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fab-btn:hover {
  transform: scale(1.1);
  box-shadow: 0 18px 42px rgba(243, 201, 105, 0.34);
}

.fab-menu {
  background: rgba(9, 17, 15, 0.96);
  border: 1px solid rgba(255,255,255,.12);
  border-radius: 16px;
  box-shadow: 0 16px 44px rgba(0, 0, 0, 0.32);
  overflow: hidden;
}

.fab-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 12px 20px;
  border: none;
  background: none;
  color: #fff;
  cursor: pointer;
  font-size: 15px;
  text-align: left;
  white-space: nowrap;
  transition: background 0.15s;
}

.fab-menu-item:hover {
  background: rgba(243, 201, 105, 0.14);
}

.dice-row {
  background: rgba(243, 201, 105, 0.08);
}

.dice-row:hover {
  background: rgba(243, 201, 105, 0.14);
}

@media (max-width: 600px) {
  .header { flex-wrap: wrap; gap: 8px; }
  .header h2 { font-size: 15px; }
  .player-grid { grid-template-columns: repeat(auto-fill, minmax(100px, 1fr)); gap: 8px; }
  .player-card { padding: 12px 4px; }
  .avatar { width: 48px; height: 48px; }
  .avatar-emoji { font-size: 24px; }
  .player-name { font-size: 12px; }
  .player-score { font-size: 14px; }
  .fab-container { bottom: 16px; right: 16px; }
  .fab-btn { width: 48px; height: 48px; font-size: 20px; }
  .modal { width: 100%; max-width: 100%; border-radius: 16px 16px 0 0; position: fixed; bottom: 0; left: 0; right: 0; padding: 20px 16px; }
  .modal-header { margin-bottom: 12px; }
  .modal-title { font-size: 16px; }
  .modal-footer { flex-direction: column; gap: 8px; }
  .modal-footer button { width: 100%; }
  .table-scroll { overflow-x: auto; -webkit-overflow-scrolling: touch; margin: 0 -14px; padding: 0 14px; }
  table { min-width: 500px; }
}

.toast-container {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(9, 17, 15, 0.96);
  border: 1px solid rgba(255,255,255,.12);
  color: #fff;
  padding: 10px 16px;
  border-radius: 14px;
  font-size: 14px;
  box-shadow: 0 4px 12px rgba(0,0,0,.2);
  animation: toast-in .3s ease-out;
  pointer-events: auto;
}

.toast-avatar {
  font-size: 18px;
  line-height: 1;
}

@keyframes toast-in {
  from { opacity: 0; transform: translateY(-12px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
