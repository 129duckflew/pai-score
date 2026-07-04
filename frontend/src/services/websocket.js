class WebSocketService {
  constructor() {
    this.ws = null
    this.listeners = {}
    this.reconnectAttempts = 0
    this.maxAttempts = 20
    this.token = null
    this.connected = false
    this.userId = null
    this.username = null
    this.shouldReconnect = false
  }

  connect(token) {
    this.token = token
    this.shouldReconnect = true
    this.reconnectAttempts = 0
    this._doConnect()
  }

  _doConnect() {
    if (this.ws) {
      this.ws.onclose = null
      this.ws.onerror = null
      this.ws.close()
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    this.ws = new WebSocket(`${protocol}//${host}/ws?token=${this.token}`)

    this.ws.onopen = () => {
      this.connected = true
      this.reconnectAttempts = 0
      this._emit('connected')
    }

    this.ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data)
        if (msg.type === 'AUTH_OK') {
          this.userId = msg.userId
          this.username = msg.username
          localStorage.setItem('userId', msg.userId)
          localStorage.setItem('username', msg.username)
          if (msg.avatar) localStorage.setItem('avatar', msg.avatar)
        }
        this._emit(msg.type, msg)
      } catch (err) {
        console.error('WS parse error:', err)
      }
    }

    this.ws.onclose = () => {
      this.connected = false
      this._emit('disconnected')
      if (this.shouldReconnect) {
        this._reconnect()
      }
    }

    this.ws.onerror = () => {
      this._emit('error')
    }
  }

  _reconnect() {
    if (this.reconnectAttempts >= this.maxAttempts) return
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 15000)
    setTimeout(() => {
      this.reconnectAttempts++
      this._doConnect()
    }, delay)
  }

  disconnect() {
    this.shouldReconnect = false
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
  }

  send(type, data = {}) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ type, ...data }))
    }
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
}

export const wsService = new WebSocketService()
