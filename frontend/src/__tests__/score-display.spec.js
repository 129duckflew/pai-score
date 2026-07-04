import { describe, it, expect } from 'vitest'
import { getScoreDisplay } from '../utils/score-display'

const players = [
  { playerId: 1, userId: 10, username: 'Alice' },
  { playerId: 2, userId: 20, username: 'Bob' },
  { playerId: 3, userId: 30, username: 'Charlie' },
]

const entry = { sourcePlayerId: 1, targetPlayerId: 2, score: 5 }

describe('getScoreDisplay', () => {
  it('source user sees negative score and outgoing direction', () => {
    const result = getScoreDisplay(entry, 10, players)
    expect(result.isSource).toBe(true)
    expect(result.isTarget).toBe(false)
    expect(result.sign).toBe('-')
    expect(result.cssClass).toBe('score-neg')
    expect(result.displayScore).toBe('-5')
    expect(result.operationText).toBe('我 → Bob')
  })

  it('target user sees positive score and incoming direction', () => {
    const result = getScoreDisplay(entry, 20, players)
    expect(result.isSource).toBe(false)
    expect(result.isTarget).toBe(true)
    expect(result.sign).toBe('+')
    expect(result.cssClass).toBe('score-pos')
    expect(result.displayScore).toBe('+5')
    expect(result.operationText).toBe('Alice → 我')
  })

  it('observer sees neutral display with positive score', () => {
    const result = getScoreDisplay(entry, 30, players)
    expect(result.isSource).toBe(false)
    expect(result.isTarget).toBe(false)
    expect(result.sign).toBe('+')
    expect(result.cssClass).toBe('score-pos')
    expect(result.displayScore).toBe('+5')
    expect(result.operationText).toBe('Alice → Bob')
  })

  it('anonymous user sees neutral display', () => {
    const result = getScoreDisplay(entry, null, players)
    expect(result.isSource).toBe(false)
    expect(result.isTarget).toBe(false)
    expect(result.sign).toBe('+')
    expect(result.cssClass).toBe('score-pos')
    expect(result.displayScore).toBe('+5')
    expect(result.operationText).toBe('Alice → Bob')
  })

  it('user sees neutral display when source and target are unknown', () => {
    const badEntry = { sourcePlayerId: 99, targetPlayerId: 88, score: 3 }
    const result = getScoreDisplay(badEntry, 10, players)
    expect(result.isSource).toBe(false)
    expect(result.isTarget).toBe(false)
    expect(result.sign).toBe('+')
    expect(result.cssClass).toBe('score-pos')
    expect(result.displayScore).toBe('+3')
    expect(result.operationText).toBe('? → ?')
  })

  it('user is both source and target (self-transfer, edge case)', () => {
    const selfEntry = { sourcePlayerId: 1, targetPlayerId: 1, score: 5 }
    const result = getScoreDisplay(selfEntry, 10, players)
    // isSource is checked first, so it wins
    expect(result.isSource).toBe(true)
    expect(result.isTarget).toBe(true)
    expect(result.sign).toBe('-')
    expect(result.cssClass).toBe('score-neg')
    expect(result.displayScore).toBe('-5')
    expect(result.operationText).toBe('我 → 我')
  })

  // Reproduce the bug: without perspective, both source and target see same thing
  it('demonstrates the original bug — source sees +5 instead of -5', () => {
    // Old behavior (no perspective):
    const oldSign = entry.score >= 0 ? '+' : ''
    const oldDisplay = `${oldSign}${entry.score}`
    const oldOp = `${players[0].username} → ${players[1].username}`

    // Source user (userId 10, Alice) would see:
    expect(oldSign).toBe('+')
    expect(oldDisplay).toBe('+5')
    expect(oldOp).toBe('Alice → Bob') // same for everyone — the bug

    // New behavior for source:
    const newResult = getScoreDisplay(entry, 10, players)
    expect(newResult.displayScore).toBe('-5')
    expect(newResult.operationText).toBe('我 → Bob')
    expect(newResult.cssClass).toBe('score-neg')
  })
})
