<template>
  <section class="panel">
    <div class="panel-head">
      <div class="panel-title-block">
        <h2>已生成报告</h2>
        <p>查看最新生成结果，可手动刷新同步报告列表。</p>
      </div>
      <div class="panel-actions">
        <span class="count-badge">{{ filteredCount }} / {{ totalCount }}</span>
        <button class="ghost-button small-button" type="button" :disabled="refreshing || !appId" @click="$emit('refresh')">
          {{ refreshing ? '刷新中...' : '刷新报告' }}
        </button>
      </div>
    </div>
    <div class="card-grid report-card-grid">
      <article v-for="report in reports" :key="report.id" class="card">
        <div class="card-main report-main">
          <div class="card-title-block">
            <div class="card-top">
              <strong>{{ report.versionNumber || '未命名版本' }}</strong>
              <span :class="['tag', reportKind(report)]">
                {{ reportKindText(report) }}
              </span>
              <span class="tag source">{{ sourceTypeLabel(report.sourceType) }}</span>
            </div>
            <div class="meta-stack">
              <span class="branch-name">{{ report.repoBranch || '-' }}</span>
              <span class="commit-id" :title="commitTooltip(report.repoCommitId)">{{ report.repoCommitId || '-' }}</span>
            </div>
          </div>
          <div class="metric-grid">
            <span><b>{{ coverageRate(report.coveredClasses, report.totalClasses) }}</b><small>{{ report.coveredClasses }} / {{ report.totalClasses }} 类</small></span>
            <span><b>{{ coverageRate(report.coveredMethods, report.totalMethods) }}</b><small>{{ report.coveredMethods }} / {{ report.totalMethods }} 方法</small></span>
            <span><b>{{ coverageRate(report.coveredLines, report.totalLines) }}</b><small>{{ report.coveredLines }} / {{ report.totalLines }} 行</small></span>
          </div>
          <span
            class="time-text time-tooltip"
            :class="{ 'has-tooltip': timeTooltip(report) }"
            :aria-label="timeTooltip(report) || undefined"
            :tabindex="timeTooltip(report) ? 0 : undefined"
            @mouseenter="$emit('show-time-tooltip', $event, timeTooltip(report))"
            @mouseleave="$emit('hide-time-tooltip')"
            @focus="$emit('show-time-tooltip', $event, timeTooltip(report))"
            @blur="$emit('hide-time-tooltip')"
          >{{ displayTime(report) }}</span>
        </div>
        <div class="action-row">
          <RouterLink
            class="table-link"
            :to="isVersionFullReport(report)
              ? { name: 'coverage-details', params: { projectId, appId }, query: { reportId: report.id } }
              : {
                name: 'coverage-overview',
                params: { projectId, appId },
                query: {
                  versionNumber: report.versionNumber || undefined,
                  reportId: report.id,
                  commitId: report.repoCommitId || undefined,
                },
              }"
          >
            {{ isVersionFullReport(report) ? '查看版本全量明细' : '打开报告' }}
          </RouterLink>
          <a class="table-link" :href="coverageReportExportUrl(projectId, report.id)">导出报告</a>
          <a class="table-link" :href="coverageMethodExportUrl(projectId, report.id)">导出方法</a>
          <button class="danger-link" type="button" :disabled="deletingReportId === report.id" @click="$emit('delete', report.id)">
            {{ deletingReportId === report.id ? '删除中...' : '删除' }}
          </button>
          <span v-if="report.hasNewerData" class="warn-text">有新数据待重新生成</span>
        </div>
      </article>
    </div>
    <div v-if="!filteredCount" class="empty-card compact">暂无匹配报告</div>
    <AppPagination
      v-if="filteredCount > 0"
      :page="page"
      :page-size="pageSize"
      :total="filteredCount"
      item-name="报告"
      :page-sizes="[6, 12, 24, 48]"
      @update:page="$emit('update:page', $event)"
      @update:page-size="$emit('update:pageSize', $event)"
    />
  </section>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import AppPagination from '@/components/AppPagination.vue'
import { coverageMethodExportUrl, coverageReportExportUrl } from '@/features/coverage/api/core'
import type { CoverageReportCard } from '@/api/types'

defineProps<{
  projectId: string
  appId: string
  reports: CoverageReportCard[]
  filteredCount: number
  totalCount: number
  refreshing: boolean
  deletingReportId: string
  page: number
  pageSize: number
  sourceTypeLabel: (value?: string) => string
  coverageRate: (covered?: number, total?: number) => string
  timeTooltip: (item: { createTimeText?: string }) => string
  displayTime: (item: { createTimeRelativeText?: string; createTimeText?: string }) => string
  commitTooltip: (value?: string) => string
}>()

defineEmits<{
  (event: 'refresh'): void
  (event: 'delete', reportId: string): void
  (event: 'update:page', page: number): void
  (event: 'update:pageSize', pageSize: number): void
  (event: 'show-time-tooltip', payload: MouseEvent | FocusEvent, text: string): void
  (event: 'hide-time-tooltip'): void
}>()

function isVersionFullReport(report: CoverageReportCard) {
  return report.reportType !== 1 && report.reportType !== 2 && !report.baseVersionNumber && !report.baseRepoCommitId
}

function reportKind(report: CoverageReportCard) {
  if (report.reportType === 1 || report.baseVersionNumber || report.baseRepoCommitId) return 'increment'
  if (report.reportType === 2) return 'commit'
  return 'full'
}

function reportKindText(report: CoverageReportCard) {
  if (reportKind(report) === 'increment') return '增量'
  if (reportKind(report) === 'commit') return '本次 Commit'
  return '全量'
}
</script>

<style scoped>
.panel,
.card,
.empty-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.panel {
  margin-top: 12px;
}

.panel-head,
.panel-actions,
.action-row {
  display: flex;
  gap: 12px;
}

.panel-head {
  justify-content: space-between;
  align-items: center;
}

.panel-title-block,
.card-title-block,
.meta-stack {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.panel-title-block {
  gap: 4px;
}

.panel-title-block h2,
.panel-title-block p {
  margin: 0;
}

.panel-title-block p {
  color: #64748b;
  font-size: 13px;
}

.panel-actions {
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.count-badge {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.08);
  color: #2563eb;
  font-weight: 800;
  white-space: nowrap;
}

.ghost-button {
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 10px 12px;
  background: rgba(15, 23, 42, 0.06);
  cursor: pointer;
}

.ghost-button.small-button {
  min-height: 36px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 800;
}

.ghost-button:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.card-grid {
  display: grid;
  grid-template-columns: 1fr;
  align-items: stretch;
  gap: 12px;
  max-height: min(520px, calc(100vh - 300px));
  overflow: auto;
  padding-right: 4px;
}

.card {
  display: grid;
  gap: 14px;
  transition: border-color .16s ease, box-shadow .16s ease, transform .16s ease;
}

.card:hover {
  border-color: rgba(var(--oat-primary-rgb), 0.2);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.card-main {
  display: grid;
  align-items: center;
  gap: 18px;
}

.report-main {
  grid-template-columns: minmax(220px, 0.9fr) minmax(420px, 1.45fr) minmax(72px, auto);
}

.card-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.meta-stack {
  gap: 6px;
}

.branch-name {
  color: #64748b;
  font-weight: 700;
}

.commit-id {
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.metric-grid span {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.9);
  color: #64748b;
  white-space: normal;
}

.metric-grid b {
  color: #0f172a;
}

.metric-grid small {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.time-text {
  justify-self: end;
  color: #64748b;
  white-space: nowrap;
}

.time-tooltip {
  position: relative;
  display: inline-flex;
  align-items: center;
  width: max-content;
  min-height: 28px;
  padding: 4px 8px;
  border-radius: 999px;
  outline: none;
  cursor: default;
  transition: color .16s ease, background .16s ease, box-shadow .16s ease;
}

.time-tooltip.has-tooltip:hover,
.time-tooltip.has-tooltip:focus-visible {
  color: #0f766e;
  background: rgba(15, 118, 110, 0.08);
  box-shadow: inset 0 0 0 1px rgba(15, 118, 110, 0.14);
}

.action-row {
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
}

.table-link {
  color: #0f766e;
  font-weight: 700;
}

.danger-link {
  border: 0;
  padding: 0;
  background: transparent;
  color: #dc2626;
  cursor: pointer;
  font-weight: 700;
}

.danger-link:hover:not(:disabled) {
  color: #b91c1c;
}

.danger-link:disabled {
  cursor: not-allowed;
  opacity: .58;
}

.warn-text {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(220, 38, 38, 0.08);
  color: #b91c1c;
  font-size: 13px;
  font-weight: 700;
}

.empty-card.compact {
  margin-top: 12px;
  padding: 14px;
  text-align: center;
  color: #64748b;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.tag.full {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
}

.tag.increment {
  background: rgba(234, 88, 12, 0.12);
  color: #c2410c;
}

.tag.commit {
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
}

@media (max-width: 900px) {
  .report-main,
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .time-text {
    justify-self: start;
  }

  .action-row {
    justify-content: flex-start;
  }
}
</style>
