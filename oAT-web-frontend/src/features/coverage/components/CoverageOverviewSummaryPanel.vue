<template>
  <section class="overview-command-card">
    <div>
      <div class="eyebrow">Coverage Actions</div>
      <h2>Commit 覆盖率工作台</h2>
      <p class="subtext">当前页面只展示本次 Commit 与增量报告；版本全量与版本增量请在覆盖率中心查看。</p>
    </div>
    <div class="coverage-toolbar">
      <button
        class="primary-button current-commit-button tooltip-button"
        type="button"
        :disabled="generating || !canGenerateCurrentCommit"
        :data-tooltip="currentCommitTooltip"
        @click="$emit('generate-current')"
      >
        生成本次 Commit 报告
      </button>
      <button
        class="ghost-button tooltip-button"
        type="button"
        :disabled="generating || !canGenerateIncremental"
        :data-tooltip="incrementalTooltip"
        @click="$emit('open-incremental')"
      >
        生成增量报告
      </button>
    </div>
  </section>

  <div class="hero-grid coverage-metrics">
    <article v-for="bucket in buckets" :key="bucket.type" class="hero-card">
      <span>{{ bucket.title }}</span>
      <strong>{{ coverageRate(bucket.report?.coveredLines, bucket.report?.totalLines) }}</strong>
      <small v-if="bucket.report">{{ bucket.report.coveredLines || 0 }} / {{ bucket.report.totalLines || 0 }}</small>
      <small v-else>{{ bucket.emptyText }}</small>
    </article>
    <article :class="['hero-card', 'status-metric-card', hasNewerData ? 'warning' : 'success']">
      <span>报告状态</span>
      <strong><i aria-hidden="true"></i>{{ hasNewerData ? '需重算' : '最新' }}</strong>
      <small>{{ hasNewerData ? '检测到有更新快照数据' : '当前报告已对齐最新数据' }}</small>
    </article>
  </div>
</template>

<script setup lang="ts">
import type { CoverageReportBucket } from '@/features/coverage/components/CoverageReportWorkbench.vue'

defineProps<{
  buckets: CoverageReportBucket[]
  generating: boolean
  canGenerateCurrentCommit: boolean
  canGenerateIncremental: boolean
  currentCommitTooltip: string
  incrementalTooltip: string
  hasNewerData: boolean
}>()

defineEmits<{
  (event: 'generate-current'): void
  (event: 'open-incremental'): void
}>()

function coverageRate(covered?: number, total?: number) {
  if (!total) return '0%'
  return `${(((covered || 0) / total) * 100).toFixed(1)}%`
}
</script>

<style scoped>
.subtext {
  color: #64748b;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.primary-button,
.ghost-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  text-decoration: none;
}

.primary-button {
  background: #0f172a;
  color: #fff;
}

.ghost-button {
  background: rgba(15, 23, 42, 0.08);
}

.overview-command-card,
.hero-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.overview-command-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 12px;
  padding: 14px 16px;
  background: linear-gradient(135deg, rgba(255, 255, 255, .96), rgba(240, 253, 250, .82));
}

.overview-command-card h2 {
  margin: 2px 0 0;
  font-size: 22px;
}

.overview-command-card .subtext {
  margin: 6px 0 0;
}

.coverage-toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.current-commit-button {
  background: linear-gradient(135deg, #2563eb, #14b8a6);
}

.tooltip-button {
  position: relative;
}

.tooltip-button::after {
  content: attr(data-tooltip);
  position: absolute;
  left: 50%;
  bottom: calc(100% + 10px);
  z-index: 20;
  width: max-content;
  max-width: 280px;
  padding: 9px 11px;
  border-radius: 12px;
  background: rgba(15, 23, 42, .94);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.45;
  text-align: left;
  white-space: normal;
  box-shadow: 0 14px 32px rgba(15, 23, 42, .18);
  opacity: 0;
  pointer-events: none;
  transform: translate(-50%, 4px);
  transition: opacity .16s ease, transform .16s ease;
}

.tooltip-button::before {
  content: '';
  position: absolute;
  left: 50%;
  bottom: calc(100% + 4px);
  z-index: 21;
  width: 10px;
  height: 10px;
  background: rgba(15, 23, 42, .94);
  opacity: 0;
  pointer-events: none;
  transform: translate(-50%, 4px) rotate(45deg);
  transition: opacity .16s ease, transform .16s ease;
}

.tooltip-button:hover::before,
.tooltip-button:focus-visible::before,
.tooltip-button:hover::after,
.tooltip-button:focus-visible::after {
  opacity: 1;
  transform: translate(-50%, 0) rotate(45deg);
}

.tooltip-button:hover::after,
.tooltip-button:focus-visible::after {
  transform: translate(-50%, 0);
}

.hero-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 12px;
}

.coverage-metrics {
  align-items: stretch;
}

.coverage-metrics .hero-card {
  display: grid;
  align-content: center;
  min-height: 96px;
  padding: 14px 16px;
}

.hero-card strong {
  display: block;
  margin-top: 5px;
}

.coverage-metrics .hero-card strong {
  font-size: 26px;
}

.status-metric-card {
  position: relative;
  overflow: hidden;
}

.status-metric-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, var(--status-bg), rgba(255, 255, 255, 0));
  pointer-events: none;
}

.status-metric-card > * {
  position: relative;
  z-index: 1;
}

.status-metric-card strong {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--status-text);
}

.status-metric-card strong i {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: currentColor;
  box-shadow: 0 0 0 6px var(--status-dot-bg);
}

.status-metric-card.success {
  --status-bg: rgba(22, 163, 74, .12);
  --status-text: #15803d;
  --status-dot-bg: rgba(22, 163, 74, .12);
  border-color: rgba(22, 163, 74, .18);
}

.status-metric-card.warning {
  --status-bg: rgba(245, 158, 11, .16);
  --status-text: #b45309;
  --status-dot-bg: rgba(245, 158, 11, .16);
  border-color: rgba(245, 158, 11, .22);
}

@media (max-width: 960px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }

  .overview-command-card {
    align-items: stretch;
    flex-direction: column;
  }

  .coverage-toolbar {
    justify-content: flex-start;
  }
}
</style>
