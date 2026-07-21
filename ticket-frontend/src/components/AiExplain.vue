<script setup>
import { computed } from 'vue'

const props = defineProps({
  result: { type: Object, default: null },
})

const pct = computed(() => {
  const c = props.result?.confidence
  return c == null ? 0 : Math.round(c * 100)
})
// 置信度条颜色：>=0.7 绿，0.4~0.7 橙，<0.4 红
const barColor = computed(() => {
  if (pct.value >= 70) return '#27ae60'
  if (pct.value >= 40) return '#e67e22'
  return '#e74c3c'
})
const catClass = (c) => ({
  '硬件类': 'tag-hardware', '财务类': 'tag-finance', '权限类': 'tag-permission'
}[c] || '')
</script>

<template>
  <div v-if="result" class="ai-explain">
    <div class="explain-head">
      <span :class="['tag', catClass(result.category)]">{{ result.category }}</span>
      <span class="conf-label">AI 置信度 {{ pct }}%</span>
      <span v-if="result.reviewRequired" class="warn-badge">置信度低 · 建议人工复核</span>
    </div>
    <div class="conf-bar">
      <div class="conf-fill" :style="{ width: pct + '%', background: barColor }"></div>
    </div>
    <div v-if="result.matches && result.matches.length" class="evidence">
      <div class="evidence-title">判定依据（Top 相似历史工单）：</div>
      <div v-for="(m, i) in result.matches.slice(0, 3)" :key="i" class="evidence-row">
        <span :class="['tag', catClass(m.category)]">{{ m.category }}</span>
        <span class="ev-score">{{ (m.score * 100).toFixed(0) }}%</span>
        <span class="ev-text">{{ m.text }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-explain {
  background: #0f3460;
  border: 1px solid #1a4a7a;
  border-radius: 8px;
  padding: 10px 12px;
  margin-top: 8px;
}
.explain-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
}
.conf-label { color: #cfe8ff; font-weight: 600; }
.warn-badge {
  background: #5a1d1d;
  color: #ffb3a7;
  border: 1px solid #a33;
  border-radius: 6px;
  padding: 1px 6px;
  font-size: 12px;
}
.conf-bar {
  height: 8px;
  background: #14305a;
  border-radius: 4px;
  overflow: hidden;
  margin: 8px 0 6px;
}
.conf-fill { height: 100%; border-radius: 4px; transition: width .3s; }
.evidence-title { color: #9fb3c8; font-size: 12px; margin-bottom: 4px; }
.evidence-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 2px 0;
  color: #d6e4f0;
}
.ev-score { color: #7fd1a6; font-weight: 600; min-width: 38px; }
.ev-text { color: #c5d3e0; }
</style>
