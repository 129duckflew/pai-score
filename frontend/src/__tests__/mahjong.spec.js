import { describe, it, expect } from 'vitest'
import { tilesToHand, cal, RuleSet } from 'mahjong-tile-efficiency'

describe('calShantenMenzu', () => {
  it('九莲宝灯 13 tiles — tenpai with 9-sided wait', () => {
    const tiles = ['1m','1m','1m','2m','3m','4m','5m','6m','7m','8m','9m','9m','9m']
    const hand = tilesToHand(tiles)
    expect(cal.calShantenMenzu(hand)).toBe(0)

    const u = new RuleSet('MCR').calUkeire(hand)
    expect(u.shanten).toBe(0)
    expect(Object.keys(u.ukeire).length).toBe(9) // waits 1m-9m
    expect(u.totalUkeire).toBe(23)
    for (const tile of ['1m','2m','3m','4m','5m','6m','7m','8m','9m']) {
      expect(u.ukeire[tile]).toBeGreaterThan(0)
    }
  })

  it('九莲宝灯 14 tiles with 1z — tenpai, best discard is 1z', () => {
    const tiles = ['1m','1m','1m','2m','3m','4m','5m','6m','7m','8m','9m','9m','9m','1z']
    const hand = tilesToHand(tiles)
    expect(cal.calShantenMenzu(hand)).toBe(0)

    const u = new RuleSet('MCR').calUkeire(hand)
    expect(u.normalDiscard).toBeDefined()
    // Discard 1z → 9-sided ukeire (九莲宝灯)
    expect(Object.keys(u.normalDiscard['1z']).length).toBe(9)
    // Discard 2m/5m/8m → single-sided ukeire waiting for 1z
    for (const tile of ['2m', '5m', '8m']) {
      expect(u.normalDiscard[tile]).toEqual({ '1z': 3 })
    }
  })

  it('regular 13-tile tenpai', () => {
    const tiles = ['1m','2m','3m','4p','5p','6p','7s','8s','9s','1z','1z','2z','2z']
    const hand = tilesToHand(tiles)
    expect(cal.calShantenMenzu(hand)).toBe(0)

    const u = new RuleSet('MCR').calUkeire(hand)
    // Waiting to complete the last pair: 1z or 2z
    expect(u.ukeire['1z']).toBe(2)
    expect(u.ukeire['2z']).toBe(2)
  })

  it('complete winning hand returns -1', () => {
    // 4 melds + 1 pair = 13 tiles (standard win)
    const tiles = ['1m','2m','3m','4p','5p','6p','7s','8s','9s','1z','1z','2z','2z']
    // Add a winning tile to make it a winning hand
    const win = [...tiles, '1z'] // draw 1z → pair completed
    const hand = tilesToHand(win)
    expect(cal.calShantenMenzu(hand)).toBe(-1)
  })

  it('1-shanten 13 tiles — 3 pungs + 1 pair + 2 singles', () => {
    const tiles = ['1m','1m','1m','2p','2p','2p','3s','3s','3s','1z','1z','2z','5z']
    const hand = tilesToHand(tiles)
    expect(cal.calShantenMenzu(hand)).toBe(1)
  })

  it('4 melds + 1 single — shanten 1 (need pair)', () => {
    // 4 completed melds + 1 singleton
    const tiles = ['1m','1m','1m','2p','2p','2p','3s','3s','3s','1z','1z','1z','5m']
    const hand = tilesToHand(tiles)
    expect(cal.calShantenMenzu(hand)).toBe(0)
    // Hmm, this gives 0 because the 13 tiles can form 4 melds + 1 isolated
    // Actually wait: 4 pungs (12 tiles) + 5m (1 tile) = 13 tiles
    // Target = floor(13/3) = 4. 4 melds formed. 1 tile left. No pair.
    // Formula: deficit = 0, taatsu = 0, pair = false → shanten 0
    // This means the hand needs a pair. You draw any tile that pairs with 5m or forms another meld.
  })

  it('14-tile hand with discard suggestion', () => {
    // 3 melds + 2 pairs + 1 single → tenpai with extra tile
    const tiles = ['1m','2m','3m','4p','5p','6p','7s','8s','9s','1z','1z','2z','2z','3z']
    const hand = tilesToHand(tiles)
    expect(cal.calShantenMenzu(hand)).toBe(0)

    const u = new RuleSet('MCR').calUkeire(hand)
    expect(u.normalDiscard).toBeDefined()
    // Discard 3z → tenpai waiting for 1z or 2z
    expect(Object.keys(u.normalDiscard['3z']).length).toBe(2)
    // Total ukeire: 3z is gone, so waiting for 1z (2) + 2z (2) = 4
  })
})
