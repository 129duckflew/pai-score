<template>
  <div class="dice-overlay" @click.self="$emit('close')">
    <div class="dice-modal">
      <div class="dice-title">{{ roller?.username }} 掷骰子</div>
      <div class="dice-area">
        <div class="die" :class="{ rolling: rolling }">
          <div class="die-face">
            <div v-for="i in 9" :key="i" class="dot" :class="{ filled: dotFilled(display[0], i) }" />
          </div>
        </div>
        <div class="die-plus">+</div>
        <div class="die" :class="{ rolling: rolling }">
          <div class="die-face">
            <div v-for="i in 9" :key="i" class="dot" :class="{ filled: dotFilled(display[1], i) }" />
          </div>
        </div>
      </div>
      <div v-if="!rolling" class="dice-result">
        {{ dice[0] }} + {{ dice[1] }} = <strong>{{ dice[0] + dice[1] }}</strong>
      </div>
      <div v-if="!rolling" class="dice-hint">点击任意位置关闭</div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'

const props = defineProps({
  dice: { type: Array, default: () => [1, 1] },
  roller: { type: Object, default: null }
})

const emit = defineEmits(['close'])

const rolling = ref(true)
const display = ref([1, 1])
let timer = null
let settleTimer1 = null
let settleTimer2 = null

const DOTS = {
  1: [false, false, false, false, true, false, false, false, false],
  2: [false, false, true, false, false, false, true, false, false],
  3: [false, false, true, false, true, false, true, false, false],
  4: [true, false, true, false, false, false, true, false, true],
  5: [true, false, true, false, true, false, true, false, true],
  6: [true, false, true, true, false, true, true, false, true]
}

function dotFilled(val, idx) {
  return DOTS[val]?.[idx - 1] || false
}

function randomDie() {
  return Math.floor(Math.random() * 6) + 1
}

function startAnimation() {
  rolling.value = true

  timer = setInterval(() => {
    if (rolling.value) {
      display.value = [randomDie(), randomDie()]
    }
  }, 60)

  settleTimer1 = setTimeout(() => {
    display.value = [props.dice[0], randomDie()]
    setTimeout(() => {
      display.value = [props.dice[0], props.dice[1]]
      rolling.value = false
    }, 200)
  }, 1000)
}

watch(() => props.dice, startAnimation, { immediate: true })

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (settleTimer1) clearTimeout(settleTimer1)
  if (settleTimer2) clearTimeout(settleTimer2)
})
</script>

<style scoped>
.dice-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.dice-modal {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  border-radius: 20px;
  padding: 32px 40px;
  text-align: center;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.4);
  min-width: 320px;
}

.dice-title {
  font-size: 18px;
  font-weight: 600;
  color: #e0e0e0;
  margin-bottom: 24px;
}

.dice-area {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.die {
  width: 80px;
  height: 80px;
  background: #fff;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  transition: transform 0.15s;
}

.die.rolling {
  animation: diceShake 0.12s infinite;
}

@keyframes diceShake {
  0% { transform: rotate(0deg) scale(1); }
  25% { transform: rotate(8deg) scale(1.05); }
  50% { transform: rotate(-5deg) scale(0.95); }
  75% { transform: rotate(6deg) scale(1.02); }
  100% { transform: rotate(-3deg) scale(1); }
}

.die:not(.rolling) {
  animation: diceSettle 0.4s ease-out;
}

@keyframes diceSettle {
  0% { transform: scale(0.5) rotate(30deg); opacity: 0.5; }
  60% { transform: scale(1.15) rotate(-5deg); opacity: 1; }
  100% { transform: scale(1) rotate(0deg); opacity: 1; }
}

.die-face {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(3, 1fr);
  gap: 4px;
  width: 54px;
  height: 54px;
  padding: 2px;
}

.dot {
  display: flex;
  align-items: center;
  justify-content: center;
}

.dot.filled::after {
  content: '';
  width: 10px;
  height: 10px;
  background: #1a1a2e;
  border-radius: 50%;
}

.die-plus {
  font-size: 28px;
  color: #888;
  font-weight: 300;
}

.dice-result {
  margin-top: 20px;
  font-size: 20px;
  color: #ffd700;
  font-weight: 500;
}

.dice-result strong {
  font-size: 24px;
}

.dice-hint {
  margin-top: 12px;
  font-size: 13px;
  color: #666;
  cursor: default;
}

@media (max-width: 600px) {
  .dice-modal { min-width: unset; width: 85vw; padding: 24px 16px; }
  .dice-title { font-size: 16px; margin-bottom: 18px; }
  .die { width: 64px; height: 64px; border-radius: 12px; }
  .die-face { width: 44px; height: 44px; gap: 3px; }
  .dot.filled::after { width: 8px; height: 8px; }
  .dice-area { gap: 12px; }
  .dice-result { font-size: 18px; margin-top: 16px; }
  .dice-result strong { font-size: 20px; }
}
</style>
