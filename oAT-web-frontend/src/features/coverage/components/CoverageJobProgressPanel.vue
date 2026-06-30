<template>
  <section v-if="status" class="panel job-panel" aria-live="polite">
    <div class="panel-head job-head">
      <div>
        <h2>生成任务</h2>
        <p class="subtext">覆盖率报告生成</p>
      </div>
      <div class="job-state-stack">
        <span :class="['job-state', failed ? 'error' : status.finish ? 'done' : 'running']">{{ stateText }}</span>
        <strong>{{ progress }}%</strong>
      </div>
    </div>

    <div class="progress-track" role="progressbar" :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100">
      <span :style="{ width: `${progress}%` }"></span>
    </div>

    <div class="job-summary-grid">
      <div class="job-summary-item">
        <span>当前阶段</span>
        <strong>{{ stageText }}</strong>
      </div>
      <div class="job-summary-item">
        <span>任务状态</span>
        <strong>{{ failed ? '生成失败，请检查任务信息' : status.finish ? '已完成' : '请稍候，正在生成' }}</strong>
      </div>
    </div>

    <div v-if="logs.length" class="job-log-list">
      <div v-for="(log, index) in logs" :key="`${log.time}-${index}`" class="job-log-item" :data-tone="log.tone">
        <span>{{ log.time }}</span>
        <strong>{{ log.text }}</strong>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { CoverageJobLog, CoverageJobStatus } from '@/features/coverage/composables/useCoverageJobProgress'

defineProps<{
  status: CoverageJobStatus
  logs: CoverageJobLog[]
  progress: number
  failed: boolean
  stateText: string
  stageText: string
}>()
</script>

<style scoped>
.panel,
.job-summary-item,
.job-log-item {
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.94);
}

.panel {
  margin-top: 12px;
  padding: 16px;
  border-radius: 20px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.subtext {
  color: #64748b;
}

.job-panel {
  display: grid;
  gap: 14px;
}

.job-head {
  align-items: flex-start;
}

.job-head h2 {
  margin: 0;
}

.job-head .subtext {
  margin: 4px 0 0;
}

.job-state-stack {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.job-state-stack strong {
  min-width: 54px;
  color: #0f172a;
  font-size: 24px;
  text-align: right;
}

.job-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.job-state::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: currentColor;
}

.job-state.running {
  background: rgba(37, 99, 235, .10);
  color: #1d4ed8;
}

.job-state.running::before {
  animation: pulse-dot 1s ease-in-out infinite;
}

.job-state.done {
  background: rgba(22, 163, 74, .12);
  color: #15803d;
}

.job-state.error {
  background: rgba(185, 28, 28, .12);
  color: #b91c1c;
}

.progress-track {
  overflow: hidden;
  height: 12px;
  border-radius: 999px;
  background: rgba(15, 23, 42, .08);
}

.progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #0f766e, #14b8a6, #38bdf8);
  box-shadow: 0 8px 18px rgba(20, 184, 166, .24);
  transition: width .35s ease;
}

.job-summary-grid {
  display: grid;
  grid-template-columns: minmax(220px, 2fr) minmax(160px, 1fr);
  gap: 10px;
}

.job-summary-item {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 12px;
  border-radius: 14px;
  background: rgba(248, 250, 252, .86);
}

.job-summary-item span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.job-summary-item strong {
  overflow: hidden;
  color: #172033;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.job-log-list {
  display: grid;
  gap: 8px;
  max-height: 190px;
  overflow: auto;
  padding: 10px;
  border-radius: 16px;
  background: rgba(248, 250, 252, .72);
}

.job-log-item {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  align-items: start;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, .84);
}

.job-log-item span {
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.job-log-item strong {
  color: #172033;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.job-log-item[data-tone='done'] strong {
  color: #15803d;
}

.job-log-item[data-tone='error'] strong {
  color: #b91c1c;
}

@media (max-width: 960px) {
  .job-summary-grid {
    grid-template-columns: 1fr;
  }

  .job-head,
  .job-state-stack {
    align-items: flex-start;
  }

  .job-state-stack {
    flex-direction: column;
  }

  .job-log-item {
    grid-template-columns: 1fr;
  }
}

@keyframes pulse-dot {
  0%, 100% {
    opacity: .45;
    transform: scale(.9);
  }
  50% {
    opacity: 1;
    transform: scale(1.18);
  }
}
</style>
