/**
 * Computes the display properties for a score entry from a specific user's perspective.
 *
 * @param {Object} entry - The score entry with { sourcePlayerId, targetPlayerId, score, type, ... }
 * @param {number|null} myUserId - The current user's ID (null for anonymous)
 * @param {Array} players - Array of player objects with { playerId, userId, username, ... }
 * @returns {{ isSource: boolean, isTarget: boolean, sign: string, displayScore: string, cssClass: string, operationText: string }}
 */
export function getScoreDisplay(entry, myUserId, players) {
  const score = entry.score || 0

  function findPlayer(playerId) {
    return players.find(p => p.playerId === playerId) || null
  }

  const sourcePlayer = findPlayer(entry.sourcePlayerId)
  const targetPlayer = findPlayer(entry.targetPlayerId)
  const sourceName = sourcePlayer ? sourcePlayer.username : '?'
  const targetName = targetPlayer ? targetPlayer.username : '?'

  const sourceUserId = sourcePlayer ? sourcePlayer.userId : null
  const targetUserId = targetPlayer ? targetPlayer.userId : null

  const isSource = myUserId != null && sourceUserId === myUserId
  const isTarget = myUserId != null && targetUserId === myUserId

  let sign
  let cssClass
  let operationText

  if (isSource && isTarget) {
    sign = '-'
    cssClass = 'score-neg'
    operationText = '我 → 我'
  } else if (isSource) {
    sign = '-'
    cssClass = 'score-neg'
    operationText = `我 → ${targetName}`
  } else if (isTarget) {
    sign = '+'
    cssClass = 'score-pos'
    operationText = `${sourceName} → 我`
  } else {
    sign = '+'
    cssClass = 'score-pos'
    operationText = `${sourceName} → ${targetName}`
  }

  return {
    isSource,
    isTarget,
    sign,
    cssClass,
    displayScore: `${sign}${score}`,
    operationText,
  }
}
