<template>
  <div class="panel-grid report-workbench">
    <section v-for="bucket in buckets" :key="bucket.type" class="panel report-summary-panel">
      <div class="panel-head">
        <h2>{{ bucket.title }}</h2>
        <span>{{ bucket.report?.createTimeText || '-' }}</span>
      </div>
      <div v-if="bucket.report" class="report-actions">
        <RouterLink class="report-action-link" :to="{ name: 'coverage-details', params: { projectId, appId }, query: { reportId: bucket.report.id } }">{{ bucket.detailLabel }}</RouterLink>
        <a class="report-action-link" :href="coverageReportExportUrl(projectId, bucket.report.id || '')">导出报告</a>
        <a class="report-action-link" :href="coverageMethodExportUrl(projectId, bucket.report.id || '')">导出方法</a>
        <button class="report-action-link danger" type="button" :disabled="!bucket.report.id || deletingReportId === bucket.report.id" @click="$emit('delete', bucket.report.id || '')">
          {{ deletingReportId === bucket.report.id ? '删除中...' : '删除报告' }}
        </button>
      </div>
      <div v-if="bucket.report" class="info-grid">
        <div class="info-item"><span>版本</span><strong>{{ bucket.report.versionNumber || '-' }}</strong></div>
        <template v-if="bucket.type === 'incremental'">
          <div class="info-item"><span>基准版本</span><strong>{{ bucket.report.baseVersionNumber || '-' }}</strong></div>
          <div class="info-item wide"><span>基准提交</span><strong class="commit-value" :title="commitTooltip(bucket.report.baseRepoCommitId)">{{ shortHash(bucket.report.baseRepoCommitId) }}</strong></div>
        </template>
        <template v-else>
          <div class="info-item"><span>分支</span><strong>{{ bucket.report.repoBranch || '-' }}</strong></div>
          <div class="info-item wide"><span>提交</span><strong class="commit-value" :title="commitTooltip(bucket.report.repoCommitId)">{{ shortHash(bucket.report.repoCommitId) }}</strong></div>
        </template>
        <div class="info-item"><span>类覆盖</span><strong>{{ coverageRate(bucket.report.coveredClasses, bucket.report.totalClasses) }}</strong><small>{{ bucket.report.coveredClasses }} / {{ bucket.report.totalClasses }}</small></div>
        <div class="info-item"><span>方法覆盖</span><strong>{{ coverageRate(bucket.report.coveredMethods, bucket.report.totalMethods) }}</strong><small>{{ bucket.report.coveredMethods }} / {{ bucket.report.totalMethods }}</small></div>
        <div class="info-item"><span>复杂度</span><strong>{{ bucket.report.totalComplexity }}</strong></div>
      </div>
      <div v-else class="empty-card">{{ bucket.emptyText }}</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { coverageMethodExportUrl, coverageReportExportUrl } from '@/features/coverage/api/core'
import type { CoverageComparisonSummary, ExtendedCoverageReportSummary } from '@/api/types'
import type { CoverageTrendReportType } from '@/features/coverage/composables/useCoverageTrendMetrics'

export type CoverageReportBucket = {
  type: CoverageTrendReportType
  title: string
  detailLabel: string
  comparisonLabel: string
  emptyText: string
  report: ExtendedCoverageReportSummary | null
  comparison: CoverageComparisonSummary | null
}

defineProps<{
  buckets: CoverageReportBucket[]
  projectId: string
  appId: string
  deletingReportId: string
}>()

defineEmits<{
  (event: 'delete', reportId: string): void
}>()

function coverageRate(covered?: number, total?: number) {
  if (!total) return '0%'
  return `${(((covered || 0) / total) * 100).toFixed(1)}%`
}

function shortHash(value?: string) {
  if (!value) return '-'
  return value.length > 18 ? `${value.slice(0, 12)}...${value.slice(-6)}` : value
}

function commitTooltip(value?: string) {
  return value || '暂无 CommitID'
}
</script>

<style scoped>
.panel,
.empty-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.panel-grid,
.info-grid {
  display: grid;
  gap: 10px;
}

.panel-grid {
  margin-top: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.report-workbench {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: stretch;
  gap: 12px;
}

.report-summary-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 14px 16px;
}

.report-summary-panel .info-grid {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(180px, 1.55fr);
  gap: 8px;
}

.info-item {
  min-width: 0;
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(248, 250, 252, .72);
  border: 1px solid rgba(15, 23, 42, .045);
}

.info-item.wide {
  grid-column: span 2;
}

.report-summary-panel .info-item.wide {
  grid-column: span 1;
}

.info-item span {
  color: #64748b;
}

.info-item strong {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-item small {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.commit-value {
  cursor: default;
}

.report-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(15, 23, 42, .06);
}

.report-action-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  border: 1px solid rgba(15, 118, 110, .16);
  border-radius: 999px;
  padding: 6px 12px;
  background: rgba(15, 118, 110, .06);
  color: #0f766e;
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
  text-decoration: none;
}

.report-action-link:hover:not(:disabled) {
  border-color: rgba(15, 118, 110, .28);
  background: rgba(15, 118, 110, .1);
}

.report-action-link.danger {
  border-color: rgba(220, 38, 38, .18);
  background: rgba(220, 38, 38, .06);
  color: #dc2626;
}

.report-action-link.danger:hover:not(:disabled) {
  border-color: rgba(185, 28, 28, .26);
  background: rgba(220, 38, 38, .1);
  color: #b91c1c;
}

.report-action-link:disabled {
  cursor: not-allowed;
  opacity: .58;
}

@media (max-width: 960px) {
  .panel-grid,
  .info-grid {
    grid-template-columns: 1fr;
  }

  .info-item.wide,
  .report-summary-panel .info-item.wide {
    grid-column: auto;
  }
}
</style>
