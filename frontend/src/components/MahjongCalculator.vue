<template>
  <div class="mjc-overlay" @click.self="$emit('close')">
    <div class="mjc-modal">
      <div class="mjc-header">
        <span class="mjc-title">🀄 麻将胡牌计算器</span>
        <button class="mjc-close" @click="$emit('close')">✕</button>
      </div>

      <div class="mjc-suits">
        <div class="mjc-row">
          <span class="mjc-label">万</span>
          <button v-for="n in 9" :key="'m'+n"
            class="mjc-tile"
            :class="{ active: handCount['m'+n] }"
            @click="addTile('m'+n)">
            {{ n }}<span class="mjc-suf">万</span>
            <span v-if="handCount['m'+n]" class="mjc-badge">{{ handCount['m'+n] }}</span>
          </button>
        </div>
        <div class="mjc-row">
          <span class="mjc-label">筒</span>
          <button v-for="n in 9" :key="'p'+n"
            class="mjc-tile"
            :class="{ active: handCount['p'+n] }"
            @click="addTile('p'+n)">
            {{ n }}<span class="mjc-suf">筒</span>
            <span v-if="handCount['p'+n]" class="mjc-badge">{{ handCount['p'+n] }}</span>
          </button>
        </div>
        <div class="mjc-row">
          <span class="mjc-label">条</span>
          <button v-for="n in 9" :key="'s'+n"
            class="mjc-tile"
            :class="{ active: handCount['s'+n] }"
            @click="addTile('s'+n)">
            {{ n }}<span class="mjc-suf">条</span>
            <span v-if="handCount['s'+n]" class="mjc-badge">{{ handCount['s'+n] }}</span>
          </button>
        </div>
        <div class="mjc-row">
          <span class="mjc-label">字</span>
          <button v-for="(label, code) in honors" :key="code"
            class="mjc-tile mjc-honor"
            :class="{ active: handCount[code] }"
            @click="addTile(code)">
            {{ label }}
            <span v-if="handCount[code]" class="mjc-badge">{{ handCount[code] }}</span>
          </button>
        </div>
      </div>

      <div class="mjc-hand-area">
        <div class="mjc-hand">
          <span v-if="!tiles.length" class="mjc-placeholder">点击上方牌面选择手牌</span>
          <span
            v-for="(t, i) in tiles" :key="i"
            class="mjc-hand-tile"
            @click="removeTile(i)"
          >{{ tileDisplay(t) }}</span>
        </div>
        <div class="mjc-hand-info">
          <span>{{ tiles.length }} 张</span>
        </div>
      </div>

      <button class="mjc-btn" :disabled="tiles.length < 2" @click="analyze">
        分析手牌
      </button>

      <div v-if="result !== null" class="mjc-result">
        <div v-if="result === -1" class="mjc-result-win">
          <strong>✅ 胡牌！</strong>
          <span>当前手牌已成胡</span>
        </div>
        <div v-else-if="result === 0" class="mjc-result-tenpai">
          <strong>🎯 听牌！</strong>
          <span v-if="ukeire && Object.keys(ukeire).length">
            可胡：
            <span v-for="(cnt, tile, i) in ukeire" :key="tile">
              <span class="mjc-wait-tile">{{ tileDisplay(tile) }}</span><span v-if="cnt > 0" class="mjc-wait-remain">剩{{ cnt }}张</span>{{ i < Object.keys(ukeire).length - 1 ? '、' : '' }}
            </span>
          </span>
          <span class="mjc-ukeire-total">共 {{ totalUkeire }} 张牌可胡</span>
        </div>
        <div v-else class="mjc-result-info">
          向听数：<strong>{{ result }}</strong>（还需 {{ result + 1 }} 步听牌）
        </div>
      </div>

      <div v-if="tiles.length" class="mjc-footer">
        <button class="mjc-btn-sec" @click="clearAll">清空手牌</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { tilesToHand, cal, RuleSet } from 'mahjong-tile-efficiency'

defineEmits(['close'])

const SUIT = { m: '万', p: '筒', s: '条' }
const honors = {
  '1z': '东', '2z': '南', '3z': '西', '4z': '北',
  '7z': '中', '6z': '发', '5z': '白'
}

const handCount = reactive({})
const tiles = ref([])

const result = ref(null)
const ukeire = ref(null)
const totalUkeire = ref(0)

function addTile(code) {
  const cur = handCount[code] || 0
  if (cur >= 4) return
  handCount[code] = cur + 1
  tiles.value.push(code)
  result.value = null
  ukeire.value = null
}

function removeTile(i) {
  const code = tiles.value[i]
  tiles.value.splice(i, 1)
  const cur = handCount[code] - 1
  if (cur <= 0) delete handCount[code]
  else handCount[code] = cur
  result.value = null
  ukeire.value = null
}

function clearAll() {
  for (const k of Object.keys(handCount)) delete handCount[k]
  tiles.value = []
  result.value = null
  ukeire.value = null
}

function tileDisplay(code) {
  if (code.length === 2) {
    const n = code[0], s = code[1]
    if (SUIT[s]) return n + SUIT[s]
  }
  return honors[code] || code
}

function analyze() {
  if (tiles.value.length < 2) return

  const hand = tilesToHand(tiles.value)
  const shanten = cal.calShantenMenzu(hand)
  result.value = shanten
  ukeire.value = null

  if (shanten === 0) {
    const rs = new RuleSet('MCR')
    const u = rs.calUkeire(hand)
    ukeire.value = u.ukeire
    totalUkeire.value = u.totalUkeire
  }
}
</script>

<style scoped>
.mjc-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}
.mjc-modal {
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  width: 520px;
  max-width: 94vw;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
}
.mjc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.mjc-title {
  font-size: 18px;
  font-weight: 700;
}
.mjc-close {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: #f0f0f0;
  cursor: pointer;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.mjc-close:hover { background: #e0e0e0; }

.mjc-suits {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}
.mjc-row {
  display: flex;
  align-items: center;
  gap: 4px;
}
.mjc-label {
  width: 24px;
  font-size: 13px;
  font-weight: 600;
  color: #888;
  flex-shrink: 0;
}
.mjc-tile {
  position: relative;
  width: 46px;
  height: 42px;
  border: 1.5px solid #d9d9d9;
  border-radius: 6px;
  background: #fafafa;
  cursor: pointer;
  font-size: 15px;
  font-weight: 500;
  color: #333;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.12s;
  padding: 0;
}
.mjc-tile:hover {
  border-color: #4a90d9;
  background: #e6f0ff;
  transform: translateY(-2px);
  box-shadow: 0 2px 6px rgba(74, 144, 217, 0.25);
}
.mjc-tile.active {
  border-color: #4a90d9;
  background: #e6f0ff;
}
.mjc-honor {
  color: #d4380d;
  font-weight: 600;
}
.mjc-suf {
  font-size: 10px;
  opacity: 0.65;
  margin-left: 1px;
}
.mjc-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  background: #4a90d9;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
}

.mjc-hand-area {
  background: #f9f9f9;
  border: 1.5px dashed #d9d9d9;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 12px;
  min-height: 52px;
}
.mjc-hand {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 6px;
}
.mjc-placeholder {
  color: #bbb;
  font-size: 14px;
}
.mjc-hand-tile {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 40px;
  border: 1.5px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.12s;
}
.mjc-hand-tile:hover {
  border-color: #ff4d4f;
  background: #fff2f0;
  transform: translateY(-2px);
}
.mjc-hand-info {
  font-size: 12px;
  color: #999;
}

.mjc-btn {
  width: 100%;
  padding: 10px;
  border: none;
  border-radius: 8px;
  background: #4a90d9;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 12px;
}
.mjc-btn:hover:not(:disabled) { background: #357abd; }
.mjc-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.mjc-result {
  padding: 14px;
  border-radius: 10px;
  margin-bottom: 12px;
  font-size: 14px;
  line-height: 1.6;
}
.mjc-result-win {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  color: #389e0d;
}
.mjc-result-tenpai {
  background: #fff7e6;
  border: 1px solid #ffd591;
  color: #d46b08;
}
.mjc-result-info {
  background: #f0f5ff;
  border: 1px solid #adc6ff;
  color: #1d39c4;
}
.mjc-wait-tile {
  display: inline-block;
  padding: 1px 6px;
  background: #fff;
  border: 1px solid #ffd591;
  border-radius: 4px;
  font-weight: 600;
  margin: 0 1px;
}
.mjc-wait-remain {
  font-size: 12px;
  color: #999;
  margin-left: 2px;
}
.mjc-ukeire-total {
  display: block;
  margin-top: 4px;
  font-size: 13px;
  color: #999;
}

.mjc-footer {
  text-align: center;
}
.mjc-btn-sec {
  padding: 6px 20px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
  color: #666;
  font-size: 13px;
  cursor: pointer;
}
.mjc-btn-sec:hover { border-color: #999; color: #333; }
</style>
