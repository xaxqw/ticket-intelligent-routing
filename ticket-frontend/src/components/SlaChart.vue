<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  stats: { type: Array, default: () => [] }
})

const el = ref(null)
let chart = null
let ro = null

function render() {
  if (!chart) return
  const nodes = props.stats.map(s => s.nodeName)
  const avgMin = props.stats.map(s => +(s.avgDurationMs / 60000).toFixed(1))
  const colors = props.stats.map(s => (s.slow ? '#d4380d' : '#1677ff'))
  chart.setOption({
    title: { text: '各环节平均处理时长 (分钟)', left: 'center', textStyle: { color: '#e6e6e6' } },
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>平均 ${p[0].value} 分钟` },
    grid: { left: 64, right: 24, bottom: 48, top: 48 },
    xAxis: {
      type: 'category',
      data: nodes,
      axisLabel: { color: '#bfbfbf', interval: 0 },
      axisLine: { lineStyle: { color: '#444' } }
    },
    yAxis: {
      type: 'value',
      name: '分钟',
      nameTextStyle: { color: '#bfbfbf' },
      axisLabel: { color: '#bfbfbf' },
      splitLine: { lineStyle: { color: '#2a2a2a' } }
    },
    series: [{
      type: 'bar',
      barWidth: '46%',
      data: avgMin.map((v, i) => ({ value: v, itemStyle: { color: colors[i] } })),
      label: { show: true, position: 'top', color: '#e6e6e6', formatter: p => p.value + ' min' }
    }]
  })
}

onMounted(() => {
  chart = echarts.init(el.value)
  render()
  // 容器在 SLA 页签隐藏时宽高为 0，切到该页签显示后自动 resize，避免文字挤在一起
  ro = new ResizeObserver(() => { if (chart) chart.resize() })
  ro.observe(el.value)
  window.addEventListener('resize', onWinResize)
})
function onWinResize() { if (chart) chart.resize() }
watch(() => props.stats, render, { deep: true })
onBeforeUnmount(() => {
  if (ro) ro.disconnect()
  window.removeEventListener('resize', onWinResize)
  if (chart) chart.dispose()
})
</script>

<template>
  <div ref="el" style="width: 100%; height: 360px;"></div>
</template>
