<template>
  <div class="oscilloscope-card">
    <div class="oscilloscope-header">
      <div>
        <strong>实时监控示波器</strong>
        <p>{{ subtitle }}</p>
      </div>
      <div class="oscilloscope-actions">
        <button class="ghost-button" :class="{ active: scopeMode === 'aggregate' }" type="button" @click="$emit('set-scope-mode', 'aggregate')">全部探针</button>
        <button class="ghost-button" :class="{ active: scopeMode === 'single' }" type="button" @click="$emit('set-scope-mode', 'single')">当前探针</button>
        <button class="ghost-button" :class="{ active: scopeMode === 'lanes' }" type="button" @click="$emit('set-scope-mode', 'lanes')">多探针泳道</button>
      </div>
    </div>
    <div class="wave-board">
      <div v-if="!wavePoints.length" class="wave-empty">暂无监控波形<br /><small>当监控列表收到 Trace 或覆盖率上报后，每个事件会形成一个圆点</small></div>
      <svg v-if="wavePolyline" class="wave-line" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
        <polyline :points="wavePolyline" />
      </svg>
      <span v-for="point in wavePoints" :key="point.id" class="wave-point" :class="{ fresh: point.fresh }" :style="{ left: `${point.x}%`, top: `${point.y}%` }" :title="point.title"></span>
    </div>
    <div class="monitor-request-summary">
      <div><span>最新事件</span><strong>{{ latestTraceTitle || '暂无' }}</strong></div>
      <div><span>事件来源</span><strong>{{ latestTraceSource }}</strong></div>
      <div><span>操作提示</span><strong>点击左侧事件查看详情</strong></div>
    </div>
  </div>
</template>

<script setup lang="ts">
type ScopeMode = 'aggregate' | 'single' | 'lanes'

defineProps<{
  subtitle: string
  scopeMode: ScopeMode
  wavePoints: Array<{
    id: string
    title: string
    x: number
    y: number
    fresh: boolean
  }>
  wavePolyline: string
  latestTraceTitle?: string
  latestTraceSource: string
}>()

defineEmits<{
  'set-scope-mode': [mode: ScopeMode]
}>()
</script>

<style scoped>
.oscilloscope-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.oscilloscope-header,
.oscilloscope-actions,
.monitor-request-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.oscilloscope-header strong {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.oscilloscope-header p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.ghost-button {
  border: none;
  border-radius: 999px;
  padding: 10px 16px;
  background: rgba(14, 116, 144, .08);
  color: #0f766e;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms ease;
  white-space: nowrap;
}

.ghost-button:hover:not(:disabled) {
  background: rgba(14, 116, 144, .14);
  transform: translateY(-1px);
}

.ghost-button.active {
  background: #0f766e;
  color: #fff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, .08);
}

.wave-board {
  position: relative;
  flex: 1;
  min-height: 280px;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, .1);
  border-radius: 16px;
  background:
    linear-gradient(rgba(20, 184, 166, .08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(20, 184, 166, .08) 1px, transparent 1px),
    radial-gradient(ellipse at 30% 50%, rgba(20, 184, 166, .06), transparent 55%),
    radial-gradient(ellipse at 70% 50%, rgba(99, 102, 241, .04), transparent 50%),
    #06101e;
  background-size: 32px 32px, 32px 32px, auto, auto, auto;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .04), 0 10px 28px rgba(15, 23, 42, .12);
}

.wave-board::after {
  content: '';
  position: absolute;
  inset: 0;
  width: 30%;
  background: linear-gradient(90deg, transparent, rgba(45, 212, 191, .1), transparent);
  animation: scan-line 4s linear infinite;
  pointer-events: none;
}

.wave-line {
  position: absolute;
  inset: 0;
  z-index: 1;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.wave-line polyline {
  fill: none;
  stroke: rgba(45, 212, 191, .45);
  stroke-width: .5;
  stroke-linecap: round;
  stroke-linejoin: round;
  filter: drop-shadow(0 0 6px rgba(45, 212, 191, .36));
}

@keyframes scan-line {
  from { transform: translateX(-110%); }
  to { transform: translateX(380%); }
}

.wave-point {
  position: absolute;
  z-index: 2;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #2dd4bf;
  box-shadow: 0 0 0 4px rgba(45, 212, 191, .15), 0 0 14px rgba(45, 212, 191, .7);
  transform: translate(-50%, -50%);
  cursor: pointer;
  transition: transform 150ms ease, box-shadow 150ms ease;
}

.wave-point:hover {
  transform: translate(-50%, -50%) scale(1.4);
  box-shadow: 0 0 0 6px rgba(45, 212, 191, .2), 0 0 20px rgba(45, 212, 191, .9);
}

.wave-point.fresh {
  animation: wave-pulse 1.2s ease-out infinite;
}

@keyframes wave-pulse {
  0% { box-shadow: 0 0 0 0 rgba(45, 212, 191, .5), 0 0 14px rgba(45, 212, 191, .9); }
  100% { box-shadow: 0 0 0 16px rgba(45, 212, 191, 0), 0 0 14px rgba(45, 212, 191, .6); }
}

.wave-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: rgba(148, 163, 184, .8);
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: .01em;
}

.wave-empty small {
  display: block;
  font-size: 12px;
  color: rgba(100, 116, 139, .7);
  font-weight: 400;
}

.monitor-request-summary > div {
  flex: 1 1 180px;
  display: grid;
  gap: 4px;
  padding: 12px 16px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 16px;
  background: rgba(248, 250, 252, .8);
  transition: background 150ms ease;
}

.monitor-request-summary > div:hover {
  background: rgba(236, 253, 245, .6);
}

.monitor-request-summary span {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: .06em;
}

.monitor-request-summary strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 600;
  word-break: break-all;
  line-height: 1.4;
}
</style>
