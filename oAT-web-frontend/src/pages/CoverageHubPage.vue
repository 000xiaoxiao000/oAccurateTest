<template>
  <section>
    <div class="page-header plain-header coverage-page-header">
      <div>
        <div class="eyebrow">Coverage Center</div>
        <h1>覆盖率中心</h1>
        <p class="subtext">按应用查看版本、已生成报告，并进入新的覆盖率详情页。</p>
      </div>
    </div>

    <div class="selector-row">
      <label class="field grow">
        <span>选择应用</span>
        <select v-model="selectedAppId" class="text-input">
          <option value="">请选择应用</option>
          <option v-for="app in apps" :key="app.id" :value="app.id">{{ app.name }}</option>
        </select>
      </label>
      <label class="field search-field">
        <span>搜索版本/报告</span>
        <input v-model.trim="keyword" class="text-input" type="search" placeholder="版本号、分支、Commit" aria-label="搜索覆盖率版本或报告" />
      </label>
      <button class="ghost-button" type="button" @click="loadApps">刷新应用</button>
    </div>

    <div v-if="error" class="status-card error">{{ error }}</div>

    <template v-if="selectedCenter">
      <section class="panel">
        <div class="panel-head">
          <div class="panel-title-block">
            <h2>版本入口</h2>
            <p>选择版本进入覆盖率概览。</p>
          </div>
          <span class="count-badge">{{ filteredVersions.length }} / {{ selectedCenter.versions.length }}</span>
        </div>
        <div class="card-grid version-card-grid">
          <article v-for="version in paginatedVersions" :key="version.id" class="card">
            <div class="card-main">
              <div class="card-title-block">
                <div class="card-top">
                  <strong>{{ version.versionNumber }}</strong>
                  <span v-if="version.current" class="tag">当前版本</span>
                </div>
                <p class="subtext">{{ version.describe || '暂无描述' }}</p>
              </div>
              <div class="meta-stack">
                <span class="branch-name">{{ version.repoBranch || '-' }}</span>
                <span class="commit-id" :title="commitTooltip(version.repoCommitId)">{{ version.repoCommitId || '-' }}</span>
              </div>
              <span class="time-text">{{ version.createTimeRelativeText || version.createTimeText || '-' }}</span>
            </div>
            <div class="action-row">
              <RouterLink
                class="table-link"
                :to="{
                  name: 'coverage-overview',
                  params: { projectId, appId: selectedAppId },
                  query: { versionNumber: version.versionNumber, commitId: version.repoCommitId || undefined },
                }"
              >
                查看覆盖率概览
              </RouterLink>
            </div>
          </article>
        </div>
        <div v-if="!filteredVersions.length" class="empty-card compact">暂无匹配版本</div>
        <AppPagination
          v-if="filteredVersions.length > 0"
          v-model:page="versionPage"
          v-model:page-size="versionPageSize"
          :total="filteredVersions.length"
          item-name="版本"
          :page-sizes="[6, 12, 24, 48]"
        />
      </section>

      <section class="panel">
        <div class="panel-head">
          <div class="panel-title-block">
            <h2>已生成报告</h2>
            <p>查看最新生成结果，可手动刷新同步报告列表。</p>
          </div>
          <div class="panel-actions">
            <span class="count-badge">{{ filteredReports.length }} / {{ selectedCenter.coverageReports.length }}</span>
            <button class="ghost-button small-button" type="button" :disabled="refreshingReports || !selectedAppId" @click="refreshReports">
              {{ refreshingReports ? '刷新中...' : '刷新报告' }}
            </button>
          </div>
        </div>
        <div class="card-grid report-card-grid">
          <article v-for="report in paginatedReports" :key="report.id" class="card">
            <div class="card-main report-main">
              <div class="card-title-block">
                <div class="card-top">
                  <strong>{{ report.versionNumber || '未命名版本' }}</strong>
                  <span :class="['tag', report.reportType === 1 ? 'increment' : 'full']">
                    {{ report.reportType === 1 ? '增量' : '全量' }}
                  </span>
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
              <span class="time-text">{{ report.createTimeRelativeText || report.createTimeText || '-' }}</span>
            </div>
            <div class="action-row">
              <RouterLink
                class="table-link"
                :to="{
                  name: 'coverage-overview',
                  params: { projectId, appId: selectedAppId },
                  query: {
                    versionNumber: report.versionNumber || undefined,
                    reportId: report.id,
                    commitId: report.repoCommitId || undefined,
                  },
                }"
              >
                打开报告
              </RouterLink>
              <a class="table-link" :href="backendApiUrl(`/p/${projectId}/coverage/export?reportId=${report.id}`)">导出报告</a>
              <a class="table-link" :href="backendApiUrl(`/p/${projectId}/coverage/export-methods?reportId=${report.id}`)">导出方法</a>
              <button class="danger-link" type="button" :disabled="deletingReportId === report.id" @click="removeCoverageReport(report.id)">
                {{ deletingReportId === report.id ? '删除中...' : '删除' }}
              </button>
              <span v-if="report.hasNewerData" class="warn-text">有新数据待重新生成</span>
            </div>
          </article>
        </div>
        <div v-if="!filteredReports.length" class="empty-card compact">暂无匹配报告</div>
        <AppPagination
          v-if="filteredReports.length > 0"
          v-model:page="reportPage"
          v-model:page-size="reportPageSize"
          :total="filteredReports.length"
          item-name="报告"
          :page-sizes="[6, 12, 24, 48]"
        />
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { deleteCoverageReport, fetchProjectApps, fetchVersionCenter } from '@/api/bootstrap'
import { backendApiUrl } from '@/api/http'
import AppPagination from '@/components/AppPagination.vue'
import { useDialog } from '@/composables/useDialog'
import type { AppSummary, VersionCenterPayload } from '@/api/types'

const route = useRoute()
const dialog = useDialog()
const projectId = computed(() => String(route.params.projectId || ''))
const apps = ref<AppSummary[]>([])
const centers = ref<Record<string, VersionCenterPayload>>({})
const selectedAppId = ref('')
const error = ref('')
const keyword = ref('')
const versionPage = ref(1)
const versionPageSize = ref(6)
const reportPage = ref(1)
const reportPageSize = ref(6)
const deletingReportId = ref('')
const refreshingReports = ref(false)

const selectedCenter = computed(() => centers.value[selectedAppId.value])
const keywordTerm = computed(() => keyword.value.toLowerCase())
const filteredVersions = computed(() => {
  const versions = selectedCenter.value?.versions || []
  const term = keywordTerm.value
  if (!term) return versions
  return versions.filter((version) => [
    version.versionNumber,
    version.describe,
    version.repoBranch,
    version.repoCommitId,
    version.sourceType,
  ].some((value) => String(value || '').toLowerCase().includes(term)))
})
const filteredReports = computed(() => {
  const reports = selectedCenter.value?.coverageReports || []
  const term = keywordTerm.value
  if (!term) return reports
  return reports.filter((report) => [
    report.versionNumber,
    report.repoBranch,
    report.repoCommitId,
    report.reportType === 1 ? '增量' : '全量',
  ].some((value) => String(value || '').toLowerCase().includes(term)))
})
const paginatedVersions = computed(() => {
  const start = (versionPage.value - 1) * versionPageSize.value
  return filteredVersions.value.slice(start, start + versionPageSize.value)
})
const paginatedReports = computed(() => {
  const start = (reportPage.value - 1) * reportPageSize.value
  return filteredReports.value.slice(start, start + reportPageSize.value)
})

function commitTooltip(value?: string) {
  return value || '暂无 CommitID'
}

function coverageRate(covered?: number, total?: number) {
  if (!total) return '0%'
  return `${(((covered || 0) / total) * 100).toFixed(1)}%`
}

async function loadApps() {
  error.value = ''
  try {
    apps.value = await fetchProjectApps(projectId.value)
    if (!selectedAppId.value && apps.value.length) {
      selectedAppId.value = apps.value[0].id
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载应用失败'
  }
}

async function loadCenter(appId: string) {
  if (!appId || centers.value[appId]) {
    return
  }
  error.value = ''
  try {
    centers.value = {
      ...centers.value,
      [appId]: await fetchVersionCenter(projectId.value, appId),
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载覆盖率中心失败'
  }
}

async function refreshCenter(appId: string) {
  if (!appId) return
  centers.value = {
    ...centers.value,
    [appId]: await fetchVersionCenter(projectId.value, appId),
  }
}

async function refreshReports() {
  if (!selectedAppId.value) return
  refreshingReports.value = true
  error.value = ''
  try {
    await refreshCenter(selectedAppId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '刷新覆盖率报告失败'
  } finally {
    refreshingReports.value = false
  }
}

async function removeCoverageReport(reportId: string) {
  if (!selectedAppId.value || !reportId) return
  const confirmed = await dialog.confirm({
    title: '删除覆盖率报告',
    message: '确认删除该覆盖率报告？删除后需要重新生成才能查看。',
    confirmText: '确认删除',
    tone: 'danger',
  })
  if (!confirmed) return
  deletingReportId.value = reportId
  error.value = ''
  try {
    await deleteCoverageReport(projectId.value, selectedAppId.value, reportId)
    await refreshCenter(selectedAppId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除覆盖率报告失败'
  } finally {
    deletingReportId.value = ''
  }
}

watch(selectedAppId, (appId) => {
  versionPage.value = 1
  reportPage.value = 1
  void loadCenter(appId)
})

watch(keyword, () => {
  versionPage.value = 1
  reportPage.value = 1
})

watch([versionPageSize, () => filteredVersions.value.length], ([, total]) => {
  const totalPages = Math.max(1, Math.ceil(total / versionPageSize.value))
  if (versionPage.value > totalPages) {
    versionPage.value = totalPages
  }
})

watch([reportPageSize, () => filteredReports.value.length], ([, total]) => {
  const totalPages = Math.max(1, Math.ceil(total / reportPageSize.value))
  if (reportPage.value > totalPages) {
    reportPage.value = totalPages
  }
})

onMounted(loadApps)
</script>

<style scoped>
.page-header,
.selector-row,
.panel-head,
.panel-actions,
.action-row {
  display: flex;
  gap: 12px;
}

.selector-row {
  position: sticky;
  top: 12px;
  z-index: 4;
  align-items: end;
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.search-field {
  min-width: min(360px, 100%);
}

.page-header,
.panel-head {
  justify-content: space-between;
  align-items: center;
}

.panel-title-block {
  display: grid;
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

.action-row {
  align-items: center;
}

.card-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-header {
  margin-bottom: 12px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtext,
.field span {
  color: #64748b;
}

.grow {
  flex: 1;
}

.field {
  display: grid;
  gap: 8px;
}

.text-input,
.ghost-button {
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 10px 12px;
}

.ghost-button {
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

.panel,
.card,
.status-card,
.empty-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.panel {
  margin-top: 12px;
}

.status-card.error,
.warn-text {
  color: #b91c1c;
}

.warn-text {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(220, 38, 38, 0.08);
  font-size: 13px;
  font-weight: 700;
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
  grid-template-columns: minmax(180px, 0.9fr) minmax(260px, 1.2fr) minmax(72px, auto);
  align-items: center;
  gap: 18px;
}

.report-main {
  grid-template-columns: minmax(220px, 0.9fr) minmax(420px, 1.45fr) minmax(72px, auto);
}

.card-title-block {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.card-title-block .subtext {
  margin: 0;
}

.meta-stack {
  display: grid;
  gap: 6px;
  min-width: 0;
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

.time-text {
  justify-self: end;
  color: #64748b;
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

.action-row {
  justify-content: flex-start;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
}

.report-card-grid .action-row {
  justify-content: flex-end;
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

@media (max-width: 900px) {
  .selector-row,
  .card-main,
  .report-main,
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .selector-row {
    display: grid;
    align-items: stretch;
  }

  .time-text {
    justify-self: start;
  }

  .report-card-grid .action-row {
    justify-content: flex-start;
  }
}
</style>
