import { io } from 'socket.io-client'

const SOCKETIO_URL = import.meta.env.VITE_SOCKETIO_URL || ''

class SocketIOService {
  constructor() {
    this.socket = null
    this.listeners = {}
    this.connected = false
    this.userId = null
    this.username = null
    this.pendingQueue = []
  }

  connect(token) {
    if (this.socket) {
      this.socket.disconnect()
    }

    this.socket = io(SOCKETIO_URL, {
      query: { token },
      reconnection: true,
      reconnectionDelay: 1000,
      reconnectionDelayMax: 15000,
      reconnectionAttempts: 20
    })

    this.socket.on('connect', () => {
      this.connected = true
      this._emit('connected')
      this._flushQueue()
    })

    this.socket.on('disconnect', () => {
      this.connected = false
      this._emit('disconnected')
    })

    this.socket.on('connect_error', (err) => {
      this.connected = false
      if (this._isAuthError(err)) {
        this.disconnect()
        this._emit('AUTH_FAILED', err)
        return
      }
      this._emit('error', err)
    })

    this.socket.on('AUTH_OK', (msg) => {
      this.userId = msg.userId
      this.username = msg.username
      localStorage.setItem('userId', msg.userId)
      localStorage.setItem('username', msg.username)
      if (msg.avatar) localStorage.setItem('avatar', msg.avatar)
      this._emit('AUTH_OK', msg)
    })

    this.socket.on('ERROR', (msg) => {
      if (msg.message === 'token 无效' || msg.message === '缺少 token' || msg.message === '未认证') {
        this.disconnect()
        this._emit('AUTH_FAILED', msg)
        return
      }
      this._emit('ERROR', msg)
    })

    this.socket.onAny((event, ...args) => {
      if (['AUTH_OK', 'ERROR', 'connect', 'disconnect', 'connect_error'].includes(event)) return
      this._emit(event, args[0])
    })
  }

  send(type, data = {}) {
    if (this.socket && this.socket.connected) {
      this.socket.emit(type, data)
    } else if (this.socket) {
      this.pendingQueue.push({ type, data })
    }
  }

  _flushQueue() {
    const queue = this.pendingQueue
    this.pendingQueue = []
    queue.forEach(({ type, data }) => {
      if (this.socket && this.socket.connected) {
        this.socket.emit(type, data)
      }
    })
  }

  on(event, callback) {
    if (!this.listeners[event]) this.listeners[event] = []
    this.listeners[event].push(callback)
    return () => {
      this.listeners[event] = this.listeners[event].filter(cb => cb !== callback)
    }
  }

  _emit(event, data) {
    (this.listeners[event] || []).forEach(cb => cb(data))
  }

  _isAuthError(err) {
    const message = String(err?.message || '')
    const status = err?.description || err?.context?.status || err?.context?.statusCode
    return status === 401 || /401|unauthorized|token|auth/i.test(message)
  }

  disconnect() {
    if (this.socket) {
      this.socket.disconnect()
      this.socket = null
    }
    this.connected = false
    this.pendingQueue = []
  }
}

export const wsService = new SocketIOService()
