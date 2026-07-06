const ADMIN_TOKEN_KEY = 'adminToken'

function adminToken() {
  return sessionStorage.getItem(ADMIN_TOKEN_KEY) || ''
}

function adminHeaders() {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${adminToken()}`
  }
}

async function parseResponse(res, fallback) {
  if (res.status === 401) {
    sessionStorage.removeItem(ADMIN_TOKEN_KEY)
    throw new Error('管理员登录已失效')
  }
  if (!res.ok) {
    let message = fallback
    try {
      const body = await res.json()
      message = body.message || message
    } catch (e) {
      // Ignore non-JSON error bodies.
    }
    throw new Error(message)
  }
  if (res.status === 204) return null
  return res.json()
}

export function getAdminToken() {
  return adminToken()
}

export function clearAdminToken() {
  sessionStorage.removeItem(ADMIN_TOKEN_KEY)
}

export async function adminLogin(password) {
  const res = await fetch('/api/admin/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password })
  })
  const data = await parseResponse(res, '管理员登录失败')
  sessionStorage.setItem(ADMIN_TOKEN_KEY, data.token)
  return data
}

export async function getAdminUsers({ page = 0, size = 20, q = '' } = {}) {
  const params = new URLSearchParams({ page, size, q })
  const res = await fetch(`/api/admin/users?${params}`, { headers: adminHeaders() })
  return parseResponse(res, '获取用户列表失败')
}

export async function createAdminUser(payload) {
  const res = await fetch('/api/admin/users', {
    method: 'POST',
    headers: adminHeaders(),
    body: JSON.stringify(payload)
  })
  return parseResponse(res, '创建用户失败')
}

export async function updateAdminUser(id, payload) {
  const res = await fetch(`/api/admin/users/${id}`, {
    method: 'PUT',
    headers: adminHeaders(),
    body: JSON.stringify(payload)
  })
  return parseResponse(res, '更新用户失败')
}

export async function deleteAdminUser(id) {
  const res = await fetch(`/api/admin/users/${id}`, {
    method: 'DELETE',
    headers: adminHeaders()
  })
  return parseResponse(res, '删除用户失败')
}

export async function getAdminRooms({ page = 0, size = 20, q = '', status = '' } = {}) {
  const params = new URLSearchParams({ page, size, q, status })
  const res = await fetch(`/api/admin/rooms?${params}`, { headers: adminHeaders() })
  return parseResponse(res, '获取房间列表失败')
}

export async function getAdminRoomHistory(roomCode) {
  const res = await fetch(`/api/admin/rooms/${roomCode}/history`, { headers: adminHeaders() })
  return parseResponse(res, '获取房间日志失败')
}

export async function closeAllAdminRooms() {
  const res = await fetch('/api/admin/rooms/close-all', {
    method: 'POST',
    headers: adminHeaders()
  })
  return parseResponse(res, '关闭房间失败')
}

export async function getAdminRuntime() {
  const res = await fetch('/api/admin/runtime', { headers: adminHeaders() })
  return parseResponse(res, '获取运行状态失败')
}

export async function getAdminLogs({ limit = 200, traceId = '' } = {}) {
  const params = new URLSearchParams({ limit })
  if (traceId) params.set('traceId', traceId)
  const res = await fetch(`/api/admin/logs?${params}`, { headers: adminHeaders() })
  return parseResponse(res, '获取运行日志失败')
}
