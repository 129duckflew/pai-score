<template>
  <div class="app-shell space-y-5">
    <section v-if="!authed" class="admin-login">
      <form class="panel-strong admin-login-card" @submit.prevent="handleLogin">
        <div class="admin-lock"><ShieldCheck :size="34" /></div>
        <p class="text-xs font-semibold uppercase tracking-[0.24em] text-gold/80">Admin Portal</p>
        <h1 class="mt-2 text-3xl font-black text-white">超级管理员</h1>
        <div class="mt-5 flex flex-col gap-3 sm:flex-row">
          <input v-model="password" type="password" placeholder="管理员密码" class="min-h-12 flex-1" autofocus />
          <button class="btn-primary min-h-12" :disabled="loading || !password">
            <LogIn :size="18" />{{ loading ? '登录中...' : '进入' }}
          </button>
        </div>
        <p v-if="error" class="alert alert-error mt-4">{{ error }}</p>
      </form>
    </section>

    <template v-else>
      <header class="admin-header">
        <div>
          <p class="text-xs font-semibold uppercase tracking-[0.24em] text-gold/80">Admin Portal</p>
          <h1 class="mt-2 text-3xl font-black text-white">超级管理员控制台</h1>
        </div>
        <button class="btn-secondary" @click="logout"><LogOut :size="17" />退出</button>
      </header>

      <nav class="admin-tabs">
        <button v-for="tab in tabs" :key="tab.key" :class="['admin-tab', { active: activeTab === tab.key }]" @click="activeTab = tab.key">
          <component :is="tab.icon" :size="17" />{{ tab.label }}
        </button>
      </nav>

      <p v-if="error" class="alert alert-error">{{ error }}</p>

      <section v-if="activeTab === 'users'" class="panel space-y-4">
        <div class="admin-toolbar">
          <div>
            <h2 class="admin-section-title">用户管理</h2>
            <p class="text-muted">在线状态、用户资料和 token</p>
          </div>
          <div class="admin-actions">
            <input v-model="userQuery" placeholder="搜索用户名" @keyup.enter="loadUsers(0)" />
            <button class="btn-secondary" @click="loadUsers(0)"><Search :size="17" />搜索</button>
            <button class="btn-primary" @click="startNewUser"><UserPlus :size="17" />新增</button>
          </div>
        </div>

        <form v-if="editingUser" class="admin-editor" @submit.prevent="saveUser">
          <input v-model="userForm.username" placeholder="用户名" required />
          <input v-model="userForm.avatar" placeholder="头像" maxlength="10" />
          <input v-model="userForm.activeRoomCode" placeholder="当前房间码" />
          <input v-if="userForm.id" v-model="userForm.token" placeholder="token" />
          <div class="admin-actions">
            <button class="btn-primary" :disabled="loading"><Save :size="17" />保存</button>
            <button type="button" class="btn-secondary" @click="editingUser = false"><X :size="17" />取消</button>
          </div>
        </form>

        <div class="table-scroll">
          <table>
            <thead>
              <tr><th>用户</th><th>在线</th><th>房间</th><th>Token</th><th>创建时间</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="user in users.content" :key="user.id">
                <td><span class="admin-avatar">{{ user.avatar || '•' }}</span>{{ user.username }} <span class="text-muted">#{{ user.id }}</span></td>
                <td><span :class="['status-pill', user.online ? 'online' : 'offline']">{{ user.online ? '在线' : '离线' }}</span></td>
                <td>{{ user.activeRoomCode || '-' }}</td>
                <td><code class="token-cell">{{ user.token }}</code></td>
                <td>{{ formatTime(user.createdAt) }}</td>
                <td class="admin-row-actions">
                  <button class="btn-secondary" @click="editUser(user)"><Pencil :size="16" />编辑</button>
                  <button class="btn-danger-outline" @click="removeUser(user)"><Trash2 :size="16" />删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <Pager :page="users.page" :total-pages="users.totalPages" @prev="loadUsers(users.page - 1)" @next="loadUsers(users.page + 1)" />
      </section>

      <section v-if="activeTab === 'rooms'" class="panel space-y-4">
        <div class="admin-toolbar">
          <div>
            <h2 class="admin-section-title">房间管理</h2>
            <p class="text-muted">分页查找房间并查看日志记录</p>
          </div>
          <div class="admin-actions">
            <input v-model="roomQuery" placeholder="搜索房间码或名称" @keyup.enter="loadRooms(0)" />
            <select v-model="roomStatus"><option value="">全部状态</option><option>WAITING</option><option>PLAYING</option><option>FINISHED</option><option>DISBANDED</option></select>
            <button class="btn-secondary" @click="loadRooms(0)"><Search :size="17" />搜索</button>
            <button class="btn-danger" @click="closeAllRooms"><Power :size="17" />关闭全部</button>
          </div>
        </div>

        <div class="table-scroll">
          <table>
            <thead>
              <tr><th>房间</th><th>状态</th><th>房主</th><th>人数</th><th>创建时间</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="room in rooms.content" :key="room.id">
                <td><strong>{{ room.name || room.roomCode }}</strong><span class="text-muted block">{{ room.roomCode }}</span></td>
                <td><span :class="'badge badge-' + room.status.toLowerCase()">{{ room.status }}</span></td>
                <td>#{{ room.hostId }}</td>
                <td>{{ room.playerCount }}</td>
                <td>{{ formatTime(room.createdAt) }}</td>
                <td><button class="btn-secondary" @click="loadRoomHistory(room.roomCode)"><ScrollText :size="16" />日志</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <Pager :page="rooms.page" :total-pages="rooms.totalPages" @prev="loadRooms(rooms.page - 1)" @next="loadRooms(rooms.page + 1)" />

        <div v-if="roomHistory" class="admin-history">
          <div class="admin-toolbar">
            <div>
              <h3 class="admin-section-title">{{ roomHistory.name || roomHistory.roomCode }}</h3>
              <p class="text-muted">{{ roomHistory.roomCode }} · {{ roomHistory.status }}</p>
            </div>
            <button class="btn-secondary" @click="roomHistory = null"><X :size="17" />关闭</button>
          </div>
          <div class="admin-history-grid">
            <div v-for="player in roomHistory.players" :key="player.playerId" class="history-player">
              <span>{{ player.avatar }}</span><strong>{{ player.username }}</strong><span>{{ player.totalScore }}</span>
            </div>
          </div>
          <div class="table-scroll">
            <table>
              <thead><tr><th>时间</th><th>类型</th><th>来源</th><th>目标</th><th>分数</th><th>记录人</th><th>备注</th></tr></thead>
              <tbody>
                <tr v-for="entry in roomHistory.entries" :key="entry.id">
                  <td>{{ formatTime(entry.createdAt) }}</td><td>{{ entry.type }}</td><td>{{ entry.sourcePlayerName }}</td><td>{{ entry.targetPlayerName }}</td><td>{{ entry.score }}</td><td>{{ entry.addedByUsername }}</td><td>{{ entry.note || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section v-if="activeTab === 'runtime'" class="panel space-y-4">
        <div class="admin-toolbar">
          <div>
            <h2 class="admin-section-title">运行状态</h2>
            <p class="text-muted">Spring Boot 实时指标和最近日志</p>
          </div>
          <button class="btn-secondary" @click="loadRuntime"><RefreshCw :size="17" />刷新</button>
        </div>
        <div class="metric-grid">
          <div class="metric"><span>在线用户</span><strong>{{ runtime.onlineUsers ?? '-' }}</strong></div>
          <div class="metric"><span>CPU</span><strong>{{ percent(runtime.processCpuLoad) }}</strong></div>
          <div class="metric"><span>系统 CPU</span><strong>{{ percent(runtime.systemCpuLoad) }}</strong></div>
          <div class="metric"><span>堆内存</span><strong>{{ bytes(runtime.heapUsedBytes) }} / {{ bytes(runtime.heapMaxBytes) }}</strong></div>
          <div class="metric"><span>非堆内存</span><strong>{{ bytes(runtime.nonHeapUsedBytes) }}</strong></div>
          <div class="metric"><span>线程</span><strong>{{ runtime.threadCount ?? '-' }}</strong></div>
          <div class="metric"><span>运行时间</span><strong>{{ uptime(runtime.uptimeSeconds) }}</strong></div>
          <div class="metric"><span>处理器</span><strong>{{ runtime.availableProcessors ?? '-' }}</strong></div>
        </div>
        <div class="trace-search">
          <input v-model="traceQuery" placeholder="输入 requestId / traceId" @keyup.enter="searchTrace" />
          <button class="btn-secondary" @click="searchTrace"><Search :size="17" />查询链路</button>
          <button class="btn-secondary" @click="clearTrace"><X :size="17" />清空</button>
        </div>
        <div v-if="traceLogs.length" class="log-view trace-view">
          <pre v-for="(entry, index) in traceLogs" :key="`trace-${index}`" class="log-entry">{{ formatLogEntry(entry) }}</pre>
        </div>
        <div class="log-view">
          <pre v-for="(entry, index) in logs" :key="index" class="log-entry">{{ formatLogEntry(entry) }}</pre>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, onUnmounted, ref, watch } from 'vue'
import { LogIn, LogOut, MonitorCog, Pencil, Power, RefreshCw, Save, ScrollText, Search, ShieldCheck, Trash2, UserPlus, Users, X } from '@lucide/vue'
import {
  adminLogin,
  clearAdminToken,
  closeAllAdminRooms,
  createAdminUser,
  deleteAdminUser,
  getAdminLogs,
  getAdminRoomHistory,
  getAdminRooms,
  getAdminRuntime,
  getAdminToken,
  getAdminUsers,
  updateAdminUser
} from '../services/admin-api'

const Pager = defineComponent({
  props: { page: Number, totalPages: Number },
  emits: ['prev', 'next'],
  setup(props, { emit }) {
    return () => h('div', { class: 'pager' }, [
      h('button', { class: 'btn-secondary', disabled: props.page <= 0, onClick: () => emit('prev') }, '上一页'),
      h('span', { class: 'text-muted' }, `第 ${props.page + 1} / ${Math.max(props.totalPages || 1, 1)} 页`),
      h('button', { class: 'btn-secondary', disabled: props.page + 1 >= props.totalPages, onClick: () => emit('next') }, '下一页')
    ])
  }
})

const tabs = [
  { key: 'users', label: '用户', icon: Users },
  { key: 'rooms', label: '房间', icon: ScrollText },
  { key: 'runtime', label: '监控', icon: MonitorCog }
]

const authed = ref(Boolean(getAdminToken()))
const activeTab = ref('users')
const password = ref('')
const loading = ref(false)
const error = ref('')
const userQuery = ref('')
const roomQuery = ref('')
const roomStatus = ref('')
const users = ref(emptyPage())
const rooms = ref(emptyPage())
const runtime = ref({})
const logs = ref([])
const traceQuery = ref('')
const traceLogs = ref([])
const roomHistory = ref(null)
const editingUser = ref(false)
const userForm = ref({ id: null, username: '', avatar: '', token: '', activeRoomCode: '' })
let runtimeTimer = null

const shouldRefreshRuntime = computed(() => authed.value && activeTab.value === 'runtime')

onMounted(() => {
  if (authed.value) loadInitialData()
})

onUnmounted(() => {
  if (runtimeTimer) clearInterval(runtimeTimer)
})

watch(activeTab, (tab) => {
  error.value = ''
  if (tab === 'users') loadUsers(users.value.page || 0)
  if (tab === 'rooms') loadRooms(rooms.value.page || 0)
  if (tab === 'runtime') loadRuntime()
})

watch(shouldRefreshRuntime, (enabled) => {
  if (runtimeTimer) clearInterval(runtimeTimer)
  runtimeTimer = enabled ? setInterval(loadRuntime, 5000) : null
}, { immediate: true })

function emptyPage() {
  return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
}

async function handleLogin() {
  loading.value = true
  error.value = ''
  try {
    await adminLogin(password.value)
    authed.value = true
    password.value = ''
    await loadInitialData()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function logout() {
  clearAdminToken()
  authed.value = false
  users.value = emptyPage()
  rooms.value = emptyPage()
  runtime.value = {}
  logs.value = []
  traceLogs.value = []
}

async function loadInitialData() {
  await Promise.all([loadUsers(0), loadRooms(0)])
}

async function guardRequest(fn) {
  loading.value = true
  error.value = ''
  try {
    return await fn()
  } catch (e) {
    error.value = e.message
    if (e.message.includes('登录')) authed.value = false
    return null
  } finally {
    loading.value = false
  }
}

async function loadUsers(page = 0) {
  const data = await guardRequest(() => getAdminUsers({ page: Math.max(0, page), q: userQuery.value }))
  if (data) users.value = data
}

async function loadRooms(page = 0) {
  const data = await guardRequest(() => getAdminRooms({ page: Math.max(0, page), q: roomQuery.value, status: roomStatus.value }))
  if (data) rooms.value = data
}

function startNewUser() {
  editingUser.value = true
  userForm.value = { id: null, username: '', avatar: '', token: '', activeRoomCode: '' }
}

function editUser(user) {
  editingUser.value = true
  userForm.value = { ...user }
}

async function saveUser() {
  const payload = { ...userForm.value }
  const data = await guardRequest(() => payload.id ? updateAdminUser(payload.id, payload) : createAdminUser(payload))
  if (!data) return
  editingUser.value = false
  await loadUsers(users.value.page)
}

async function removeUser(user) {
  if (!confirm(`删除用户 ${user.username}？`)) return
  const data = await guardRequest(() => deleteAdminUser(user.id))
  if (data !== null || !error.value) await loadUsers(users.value.page)
}

async function closeAllRooms() {
  if (!confirm('确认强制关闭所有等待中和进行中的房间？')) return
  const data = await guardRequest(closeAllAdminRooms)
  if (data) {
    await loadRooms(0)
    roomHistory.value = null
  }
}

async function loadRoomHistory(roomCode) {
  const data = await guardRequest(() => getAdminRoomHistory(roomCode))
  if (data) roomHistory.value = data
}

async function loadRuntime() {
  const [runtimeData, logData] = await Promise.all([
    guardRequest(getAdminRuntime),
    guardRequest(() => getAdminLogs({ limit: 240 }))
  ])
  if (runtimeData) runtime.value = runtimeData
  if (logData) logs.value = logEntries(logData)
}

async function searchTrace() {
  if (!traceQuery.value.trim()) return
  const data = await guardRequest(() => getAdminLogs({ limit: 500, traceId: traceQuery.value.trim() }))
  if (data) traceLogs.value = logEntries(data)
}

function clearTrace() {
  traceQuery.value = ''
  traceLogs.value = []
}

function formatTime(value) {
  return value ? String(value).substring(0, 19).replace('T', ' ') : '-'
}

function logEntries(data) {
  if (Array.isArray(data?.entries) && data.entries.length) return data.entries
  return (data?.lines || []).map(line => ({ raw: line }))
}

function formatLogEntry(entry) {
  if (typeof entry === 'string') return entry
  return JSON.stringify(compactLogEntry(entry), null, 2)
}

function compactLogEntry(entry) {
  return Object.fromEntries(Object.entries(entry || {}).filter(([, value]) => value !== null && value !== ''))
}

function bytes(value) {
  if (value == null || value < 0) return '-'
  if (value < 1024 * 1024) return `${Math.round(value / 1024)} KB`
  return `${Math.round(value / 1024 / 1024)} MB`
}

function percent(value) {
  if (value == null || value < 0) return '-'
  return `${Math.round(value * 100)}%`
}

function uptime(seconds) {
  if (!seconds) return '-'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return `${h}h ${m}m`
}
</script>

<style scoped>
.admin-login { min-height: calc(100vh - 48px); display: grid; place-items: center; }
.admin-login-card { width: min(100%, 560px); }
.admin-lock { width: 64px; height: 64px; display: grid; place-items: center; border-radius: 18px; border: 1px solid rgba(243,201,105,.35); background: rgba(243,201,105,.16); color: #f3c969; margin-bottom: 18px; }
.admin-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; border: 1px solid rgba(255,255,255,.1); background: rgba(9,17,15,.72); border-radius: 20px; padding: 18px; }
.admin-tabs { display: flex; gap: 8px; overflow-x: auto; }
.admin-tab { min-height: 42px; display: inline-flex; align-items: center; gap: 8px; border: 1px solid rgba(255,255,255,.1); border-radius: 12px; background: rgba(255,255,255,.08); padding: 0 14px; color: rgba(238,248,242,.72); font-weight: 700; }
.admin-tab.active { border-color: rgba(243,201,105,.55); background: rgba(243,201,105,.18); color: #fff; }
.admin-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; flex-wrap: wrap; }
.admin-section-title { font-size: 1.25rem; font-weight: 850; color: #fff; }
.admin-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.admin-editor { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)) auto; gap: 10px; align-items: center; border: 1px solid rgba(255,255,255,.1); border-radius: 14px; padding: 12px; background: rgba(255,255,255,.055); }
.admin-avatar { margin-right: 8px; }
.status-pill { display: inline-flex; align-items: center; border-radius: 999px; border: 1px solid rgba(255,255,255,.16); padding: 4px 9px; font-size: 12px; font-weight: 800; }
.status-pill.online { border-color: rgba(53,211,153,.45); background: rgba(53,211,153,.14); color: #a8f5cf; }
.status-pill.offline { color: rgba(238,248,242,.55); }
.token-cell { display: inline-block; max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #f4d88a; }
.admin-row-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.pager { display: flex; align-items: center; justify-content: flex-end; gap: 12px; }
.admin-history { border-top: 1px solid rgba(255,255,255,.12); padding-top: 16px; }
.admin-history-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; margin: 14px 0; }
.history-player { display: flex; align-items: center; gap: 8px; border: 1px solid rgba(255,255,255,.1); border-radius: 12px; background: rgba(255,255,255,.055); padding: 10px; }
.history-player strong { flex: 1; }
.metric-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; }
.metric { border: 1px solid rgba(255,255,255,.1); border-radius: 14px; padding: 14px; background: rgba(255,255,255,.055); }
.metric span { display: block; color: rgba(209,250,229,.58); font-size: 12px; margin-bottom: 8px; }
.metric strong { color: #fff; font-size: 1.1rem; }
.trace-search { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.trace-search input { min-width: min(100%, 360px); flex: 1; }
.log-view { min-height: 360px; max-height: 560px; overflow: auto; border: 1px solid rgba(255,255,255,.1); border-radius: 14px; background: rgba(0,0,0,.35); padding: 14px; color: #d7f7e8; font-size: 12px; line-height: 1.55; white-space: pre-wrap; }
.log-view.trace-view { border-color: rgba(243,201,105,.32); background: rgba(28,21,5,.36); }
.log-entry { margin: 0 0 10px; padding-bottom: 10px; border-bottom: 1px solid rgba(255,255,255,.08); white-space: pre-wrap; word-break: break-word; }
.log-entry:last-child { margin-bottom: 0; padding-bottom: 0; border-bottom: 0; }
@media (max-width: 760px) {
  .admin-header { align-items: flex-start; flex-direction: column; }
  .admin-editor { grid-template-columns: 1fr; }
  .admin-actions input, .admin-actions select { width: 100%; }
  .admin-actions { width: 100%; }
}
</style>
