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
            :class="{ active: handCount[n+'m'] }"
            @click="addTile(n+'m')">
            <span v-html="tileSvg(n+'m', 38)"></span>
            <span v-if="handCount[n+'m']" class="mjc-badge">{{ handCount[n+'m'] }}</span>
          </button>
        </div>
        <div class="mjc-row">
          <span class="mjc-label">筒</span>
          <button v-for="n in 9" :key="'p'+n"
            class="mjc-tile"
            :class="{ active: handCount[n+'p'] }"
            @click="addTile(n+'p')">
            <span v-html="tileSvg(n+'p', 38)"></span>
            <span v-if="handCount[n+'p']" class="mjc-badge">{{ handCount[n+'p'] }}</span>
          </button>
        </div>
        <div class="mjc-row">
          <span class="mjc-label">条</span>
          <button v-for="n in 9" :key="'s'+n"
            class="mjc-tile"
            :class="{ active: handCount[n+'s'] }"
            @click="addTile(n+'s')">
            <span v-html="tileSvg(n+'s', 38)"></span>
            <span v-if="handCount[n+'s']" class="mjc-badge">{{ handCount[n+'s'] }}</span>
          </button>
        </div>
        <div class="mjc-row">
          <span class="mjc-label">字</span>
          <button v-for="(_, code) in honors" :key="code"
            class="mjc-tile mjc-honor"
            :class="{ active: handCount[code] }"
            @click="addTile(code)">
            <span v-html="tileSvg(code, 38)"></span>
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
            v-html="tileSvg(t, 44)"
          ></span>
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
              <span v-for="(cnt, tile, i) in ukeire" :key="tile" class="mjc-wait-item">
                <span class="mjc-wait-tile" v-html="tileSvg(tile, 30)"></span><span v-if="cnt > 0" class="mjc-wait-remain">剩{{ cnt }}张</span>{{ i < Object.keys(ukeire).length - 1 ? '、' : '' }}
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
import { tileSvg } from '../utils/mahjong-tiles.js'

defineEmits(['close'])

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
  width: 48px;
  height: 54px;
  border: 2px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.12s;
  padding: 0;
  background: none;
  display: flex;
  align-items: center;
  justify-content: center;
}
.mjc-tile:hover {
  border-color: #4a90d9;
  transform: translateY(-2px);
  filter: drop-shadow(0 2px 4px rgba(74, 144, 217, 0.35));
}
.mjc-tile.active {
  border-color: #4a90d9;
  border-radius: 8px;
  filter: drop-shadow(0 0 6px rgba(74, 144, 217, 0.5));
}
.mjc-badge {
  position: absolute;
  top: -4px;
  right: -4px;
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
  z-index: 1;
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
  border: 2px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.12s;
  padding: 0;
  background: none;
}
.mjc-hand-tile:hover {
  border-color: #ff4d4f;
  transform: translateY(-2px);
  filter: drop-shadow(0 2px 4px rgba(255, 77, 79, 0.35));
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
.mjc-wait-item {
  white-space: nowrap;
}
.mjc-wait-tile {
  display: inline-block;
  vertical-align: middle;
  border: 1px solid #ffd591;
  border-radius: 4px;
  line-height: 0;
  padding: 1px;
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

@media (max-width: 600px) {
  .mjc-modal { padding: 14px; max-width: 100vw; border-radius: 0; max-height: 100vh; }
  .mjc-header { margin-bottom: 12px; }
  .mjc-title { font-size: 16px; }
  .mjc-suits { gap: 6px; margin-bottom: 10px; }
  .mjc-row { gap: 2px; }
  .mjc-label { width: 18px; font-size: 11px; }
  .mjc-tile { width: 36px; height: 40px; border-radius: 4px; }
  .mjc-tile.active { border-radius: 6px; }
  .mjc-badge { top: -3px; right: -3px; min-width: 15px; height: 15px; font-size: 10px; padding: 0 3px; }
  .mjc-hand-area { padding: 8px; }
  .mjc-btn { padding: 10px; font-size: 14px; margin-bottom: 10px; }
  .mjc-result { padding: 10px; font-size: 13px; margin-bottom: 10px; }
  .mjc-btn-sec { font-size: 12px; padding: 6px 16px; }
}
</style>
