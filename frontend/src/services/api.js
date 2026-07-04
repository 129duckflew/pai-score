export async function register(username) {
  const res = await fetch('/api/users/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username })
  })
  if (!res.ok) throw new Error('注册失败')
  return res.json()
}

export async function getUserHistory(userId) {
  const res = await fetch(`/api/users/${userId}/history`)
  if (!res.ok) throw new Error('获取历史记录失败')
  return res.json()
}

export async function getRoomHistory(roomCode) {
  const res = await fetch(`/api/rooms/${roomCode}/history`)
  if (!res.ok) throw new Error('获取房间记录失败')
  return res.json()
}
