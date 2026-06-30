<template>
  <section v-if="visible" class="panel comparison-panel">
    <div class="panel-head typed-panel-head">
      <div>
        <h2>与上一版本对比</h2>
        <p class="subtext">{{ description }}</p>
      </div>
      <div class="report-type-switch" aria-label="切换对比报告类型">
        <button
          v-for="option in options"
          :key="option.type"
          type="button"
          :class="['type-switch-button', { active: selectedType === option.type }]"
          :disabled="!option.report && !option.comparison"
          @click="$emit('select', option.type)"
        >
          {{ option.label }}
        </button>
      </div>
    </div>
    <div class="typed-panel-meta">
      <span :class="['trend-type-badge', activeType]">{{ label }}</span>
      <span>{{ meta }}</span>
    </div>
    <div v-if="comparison" class="hero-grid comparison-grid">
      <button class="hero-card clickable" type="button" @click="$emit('show-methods', 'added')">
        <span>新增覆盖</span>
        <strong>{{ comparisonRate(comparison.addedCount) }}</strong>
        <small>{{ comparison.addedCount }} 个方法 · 查看新增方法</small>
      </button>
      <button class="hero-card clickable" type="button" @click="$emit('show-methods', 'stable')">
        <span>稳定覆盖</span>
        <strong>{{ comparisonRate(comparison.stableCount) }}</strong>
        <small>{{ comparison.stableCount }} 个方法 · 查看稳定方法</small>
      </button>
      <button class="hero-card clickable" type="button" @click="$emit('show-methods', 'decreased')">
        <span>下降覆盖</span>
        <strong>{{ comparisonRate(comparison.decreasedCount) }}</strong>
        <small>{{ comparison.decreasedCount }} 个方法 · 查看下降方法</small>
      </button>
    </div>
    <div v-else class="empty-card">暂无{{ label }}的上一版本对比数据</div>
  </section>
</template>

<script setup lang="ts">
import type { CoverageComparisonSummary } from '@/api/types'
import type { CoverageTrendReportType } from '@/features/coverage/composables/useCoverageTrendMetrics'

type CoverageComparisonOption = {
  type: CoverageTrendReportType
  label: string
  badge: string
  report: unknown | null
  comparison: CoverageComparisonSummary | null
}

const props = defineProps<{
  visible: boolean
  options: CoverageComparisonOption[]
  selectedType: CoverageTrendReportType
  activeType: CoverageTrendReportType
  comparison: CoverageComparisonSummary | null
  label: string
  description: string
  meta: string
}>()

defineEmits<{
  (event: 'select', type: CoverageTrendReportType): void
  (event: 'show-methods', type: 'added' | 'stable' | 'decreased'): void
}>()

function comparisonRate(count?: number) {
  return coverageRate(count, comparisonTotal(props.comparison))
}

function comparisonTotal(comparison?: CoverageComparisonSummary | null) {
  if (!comparison) return 0
  return (comparison.addedCount || 0) + (comparison.stableCount || 0) + (comparison.decreasedCount || 0)
}

function coverageRate(covered?: number, total?: number) {
  if (!total) return '0%'
  return `${(((covered || 0) / total) * 100).toFixed(1)}%`
}
</script>

<style scoped>
.panel,
.empty-card,
.hero-card {
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

.comparison-panel {
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
.typed-panel-meta {
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

.typed-panel-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 700;
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

.hero-grid,
.comparison-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.hero-card strong {
  display: block;
  margin-top: 5px;
}

.hero-card.clickable {
  text-align: left;
  cursor: pointer;
}

.hero-card.clickable:hover {
  border-color: rgba(15, 118, 110, .35);
  box-shadow: 0 12px 30px rgba(15, 118, 110, .12);
}

@media (max-width: 960px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }

  .typed-panel-head,
  .report-type-switch {
    justify-content: flex-start;
  }
}
</style>
