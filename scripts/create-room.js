const path = require('path')
const { WebSocket } = require(path.join(__dirname, 'node_modules', 'ws'))

const token = process.argv[2]
const wsUrl = process.argv[3] || 'ws://localhost:8082'

const ws = new WebSocket(wsUrl + '/ws?token=' + token)
ws.on('open', () => {
  ws.send(JSON.stringify({ type: 'CREATE_ROOM' }))
})
ws.on('message', (data) => {
  const msg = JSON.parse(data.toString())
  if (msg.type === 'ROOM_CREATED') {
    console.log(msg.roomCode)
    ws.close()
  }
  if (msg.type === 'ERROR') {
    console.error('ERROR: ' + msg.message)
    process.exit(1)
  }
})
ws.on('close', () => process.exit(0))
ws.on('error', (e) => {
  console.error('WS error: ' + e.message)
  process.exit(1)
})
setTimeout(() => {
  console.error('Timeout connecting to WS')
  process.exit(1)
}, 5000)
