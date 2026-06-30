<template>
  <section v-if="trend.length" class="panel trend-panel">
    <div class="panel-head typed-panel-head">
      <div>
        <h2>趋势数据</h2>
        <p class="subtext">按本次 Commit 与增量报告分别查看覆盖率变化，避免与版本全量口径混算。</p>
      </div>
      <div class="report-type-switch" aria-label="切换趋势报告类型">
        <button
          type="button"
          :class="['type-switch-button', { active: reportType === 'commit' }]"
          :disabled="!counts.commit"
          @click="$emit('select', 'commit')"
        >
          本次 Commit {{ counts.commit }} 条
        </button>
        <button
          type="button"
          :class="['type-switch-button', { active: reportType === 'incremental' }]"
          :disabled="!counts.incremental"
          @click="$emit('select', 'incremental')"
        >
          增量 {{ counts.incremental }} 条
        </button>
      </div>
    </div>
    <div v-if="filteredTrend.length" class="trend-list">
      <article v-for="(item, index) in filteredTrend" :key="String(item.reportId || item.timestamp || index)" class="trend-item">
        <div class="trend-time">
          <span :class="['trend-type-badge', reportKind(item)]">{{ reportTypeLabel(item) }}</span>
          <strong>{{ trendTime(item) }}</strong>
          <small>{{ reportMeta(item) }}</small>
        </div>
        <div class="trend-main">
          <div class="trend-value-row">
            <strong>{{ metric(item, 'lineCoverage') }}</strong>
            <span :class="['trend-direction', directionTone(index, 'lineCoverage')]" :title="directionTitle(index, 'lineCoverage')">
              {{ directionSymbol(index, 'lineCoverage') }} {{ directionText(index, 'lineCoverage') }}
            </span>
          </div>
          <span>行覆盖率</span>
        </div>
        <div class="trend-meta">
          <span>分支 {{ metric(item, 'branchCoverage') }} <b :class="directionTone(index, 'branchCoverage')">{{ directionSymbol(index, 'branchCoverage') }}</b></span>
          <span>方法 {{ metric(item, 'methodCoverage') }} <b :class="directionTone(index, 'methodCoverage')">{{ directionSymbol(index, 'methodCoverage') }}</b></span>
        </div>
      </article>
    </div>
    <div v-else class="empty-card">暂无{{ reportTypeText(reportType) }}趋势数据</div>
  </section>
</template>

<script setup lang="ts">
import type { CoverageTrendReportType } from '@/features/coverage/composables/useCoverageTrendMetrics'

type TrendMetricKey = 'lineCoverage' | 'branchCoverage' | 'methodCoverage'

defineProps<{
  trend: Array<Record<string, unknown>>
  filteredTrend: Array<Record<string, unknown>>
  counts: Record<CoverageTrendReportType, number>
  reportType: CoverageTrendReportType
  reportKind: (item: Record<string, unknown>) => CoverageTrendReportType
  reportTypeText: (type: CoverageTrendReportType) => string
  metric: (item: Record<string, unknown>, key: TrendMetricKey) => string
  directionTone: (index: number, key: TrendMetricKey) => string
  directionSymbol: (index: number, key: TrendMetricKey) => string
  directionText: (index: number, key: TrendMetricKey) => string
  directionTitle: (index: number, key: TrendMetricKey) => string
  trendTime: (item: Record<string, unknown>) => string
  reportTypeLabel: (item: Record<string, unknown>) => string
  reportMeta: (item: Record<string, unknown>) => string
}>()

defineEmits<{
  (event: 'select', type: CoverageTrendReportType): void
}>()
</script>

<style scoped>
.panel,
.empty-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.panel {
  margin-top: 12px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.trend-panel {
  overflow: hidden;
}

.typed-panel-head {
  align-items: flex-start;
}

.typed-panel-head h2,
.typed-panel-head .subtext {
  margin: 0;
}

.typed-panel-head .subtext {
  margin-top: 4px;
}

.subtext,
.typed-panel-meta,
.trend-main span,
.trend-meta {
  color: #64748b;
}

.report-type-switch {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
  padding: 4px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 999px;
  background: rgba(248, 250, 252, .78);
}

.type-switch-button {
  border: 0;
  border-radius: 999px;
  padding: 7px 12px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
}

.type-switch-button.active {
  background: #0f766e;
  color: #fff;
  box-shadow: 0 8px 18px rgba(15, 118, 110, .18);
}

.type-switch-button:disabled {
  cursor: not-allowed;
  opacity: .42;
}

.trend-type-badge {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  border-radius: 999px;
  padding: 4px 9px;
  background: rgba(15, 118, 110, .10);
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
  line-height: 1;
}

.trend-type-badge.commit {
  background: rgba(245, 158, 11, .14);
  color: #b45309;
}

.trend-type-badge.incremental {
  background: rgba(37, 99, 235, .10);
  color: #2563eb;
}

.trend-list {
  display: grid;
  gap: 10px;
  max-height: 280px;
  overflow: auto;
}

.trend-item {
  display: grid;
  grid-template-columns: minmax(180px, .9fr) minmax(140px, .7fr) minmax(220px, 1.2fr);
  align-items: center;
  gap: 14px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, .06);
}

.trend-time {
  display: grid;
  gap: 5px;
  min-width: 0;
  color: #64748b;
  font-weight: 700;
}

.trend-time strong,
.trend-time small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trend-time strong {
  color: #334155;
  font-size: 14px;
}

.trend-time small {
  color: #94a3b8;
  font-size: 12px;
}

.trend-main strong {
  display: inline;
  color: #0f172a;
  font-size: 22px;
}

.trend-value-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.trend-direction {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}

.trend-direction.up {
  background: rgba(22, 163, 74, .12);
  color: #15803d;
}

.trend-direction.flat {
  background: rgba(100, 116, 139, .12);
  color: #64748b;
}

.trend-direction.down {
  background: rgba(220, 38, 38, .12);
  color: #dc2626;
}

.trend-main span,
.trend-meta {
  font-size: 13px;
}

.trend-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.trend-meta span {
  border-radius: 999px;
  padding: 5px 8px;
  background: rgba(15, 23, 42, .05);
}

.trend-meta b {
  margin-left: 4px;
  font-size: 13px;
}

.trend-meta b.up {
  color: #15803d;
}

.trend-meta b.flat {
  color: #64748b;
}

.trend-meta b.down {
  color: #dc2626;
}

@media (max-width: 960px) {
  .typed-panel-head,
  .trend-meta,
  .report-type-switch {
    justify-content: flex-start;
  }

  .trend-item {
    grid-template-columns: 1fr;
  }
}
</style>
