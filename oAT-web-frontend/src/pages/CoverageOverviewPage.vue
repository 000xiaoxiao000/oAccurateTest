<template>
  <section>
    <div class="page-header plain-header coverage-page-header">
      <div>
        <div class="eyebrow">Coverage Overview</div>
        <h1>{{ payload?.app.name || selectedAppName || appId }}</h1>
        <p class="subtext">{{ displayCoverageVersionNumber }}</p>
      </div>
      <div class="header-actions">
        <RouterLink class="secondary-link" :to="`/p/${projectId}/coverage`">返回覆盖率中心</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载覆盖率概览...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <div v-else-if="emptyState" class="empty-state-card">
      <div>
        <div class="eyebrow">No Coverage Data</div>
        <h2>{{ emptyState.title }}</h2>
        <p class="subtext">{{ emptyState.description }}</p>
      </div>
      <div class="empty-actions">
        <RouterLink class="primary-button" :to="`/p/${projectId}/apps/${appId}/versions/new`">创建版本</RouterLink>
        <RouterLink class="ghost-link" :to="`/p/${projectId}/coverage`">返回覆盖率中心</RouterLink>
      </div>
    </div>
    <template v-else-if="payload">
      <div v-if="autoSelectionNotice" class="inline-notice">{{ autoSelectionNotice }}</div>

      <CoverageOverviewSummaryPanel
        :buckets="reportBuckets"
        :generating="generating"
        :can-generate-current-commit="canGenerateCurrentCommit"
        :can-generate-incremental="canGenerateIncremental"
        :current-commit-tooltip="currentCommitGenerateTooltip"
        :incremental-tooltip="incrementalGenerateTooltip"
        :has-newer-data="Boolean(payload.hasNewerData)"
        @generate-current="generateCurrentCommit"
        @open-incremental="openIncrementalDialog"
      />

      <CoverageJobProgressPanel
        :status="jobStatus"
        :logs="jobLogs"
        :progress="jobProgress"
        :failed="jobFailed"
        :state-text="jobStateText"
        :stage-text="jobStageText"
      />

      <CoverageReportWorkbench
        :buckets="reportBuckets"
        :project-id="projectId"
        :app-id="appId"
        :deleting-report-id="deletingReportId"
        @delete="removeCoverageReport"
      />

      <CoverageBuildSessionsPanel :sessions="buildSessions" />

      <CoverageComparisonPanel
        :visible="hasComparisonPanel"
        :options="comparisonOptions"
        :selected-type="comparisonReportType"
        :active-type="activeComparisonOption.type"
        :comparison="activeComparison"
        :label="activeComparisonLabel"
        :description="activeComparisonDescription"
        :meta="activeComparisonMeta"
        @select="selectComparisonReportType"
        @show-methods="showComparisonMethods"
      />

      <CoverageTrendPanel
        :trend="trend"
        :filtered-trend="filteredTrend"
        :counts="trendCounts"
        :report-type="trendReportType"
        :report-kind="trendReportKind"
        :report-type-text="reportTypeText"
        :metric="trendMetric"
        :direction-tone="trendDirectionTone"
        :direction-symbol="trendDirectionSymbol"
        :direction-text="trendDirectionText"
        :direction-title="trendDirectionTitle"
        :trend-time="trendTime"
        :report-type-label="trendReportTypeLabel"
        :report-meta="trendReportMeta"
        @select="selectTrendReportType"
      />
    </template>

    <CoverageIncrementalReportDialog
      :open="incrementalDialogOpen"
      :generating="generating"
      :project-id="projectId"
      :app-id="appId"
      :current-version-number="currentCoverageVersionNumber"
      :current-commit-id="currentCoverageCommit"
      :initial-reports="incrementalInitialReports"
      :default-report="defaultIncrementalBaseReport"
      :error="incrementalError"
      @close="closeIncrementalDialog"
      @submit="generateIncremental"
      @error="incrementalError = $event"
    />

    <CoverageComparisonMethodsDialog
      :open="comparisonDialog.open"
      :type="comparisonDialog.type"
      :title="comparisonDialog.title"
      :label="comparisonDialog.label"
      :description="comparisonDialog.description"
      :methods="comparisonDialog.methods"
      :comparison-report-id="comparisonReportId"
      :project-id="projectId"
      :app-id="appId"
      @close="comparisonDialog.open = false"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useDialog } from '@/composables/useDialog'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import {
  fetchVersionCenter,
} from '@/api/bootstrap'
import {
  deleteCoverageReportV2,
  fetchBuildSessions,
  fetchCoverageJobV2,
  fetchCoverageOverviewV2,
  fetchCoverageTrendV2,
  triggerCoverageGenerateCurrentV2,
  triggerCoverageGenerateIncrementalV2,
} from '@/features/coverage/api/core'
import CoverageComparisonMethodsDialog from '@/features/coverage/components/CoverageComparisonMethodsDialog.vue'
import CoverageBuildSessionsPanel from '@/features/coverage/components/CoverageBuildSessionsPanel.vue'
import CoverageComparisonPanel from '@/features/coverage/components/CoverageComparisonPanel.vue'
import CoverageIncrementalReportDialog, { type IncrementalBaseReport } from '@/features/coverage/components/CoverageIncrementalReportDialog.vue'
import CoverageJobProgressPanel from '@/features/coverage/components/CoverageJobProgressPanel.vue'
import CoverageOverviewSummaryPanel from '@/features/coverage/components/CoverageOverviewSummaryPanel.vue'
import CoverageReportWorkbench, { type CoverageReportBucket } from '@/features/coverage/components/CoverageReportWorkbench.vue'
import CoverageTrendPanel from '@/features/coverage/components/CoverageTrendPanel.vue'
import { useCoverageJobProgress, type CoverageJobLog, type CoverageJobStatus } from '@/features/coverage/composables/useCoverageJobProgress'
import { useCoverageTrendMetrics, type CoverageTrendReportType } from '@/features/coverage/composables/useCoverageTrendMetrics'
import type { CoverageComparisonMethod, CoverageOverviewPayload, VersionItemSummary } from '@/api/types'
import type { BuildSession } from '@/entities/coverage/model'

const route = useRoute()
const router = useRouter()
const dialog = useDialog()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const versionNumber = computed(() => String(route.query.versionNumber || ''))
const reportId = computed(() => String(route.query.reportId || ''))
const commitId = computed(() => String(route.query.commitId || ''))
const payload = ref<CoverageOverviewPayload | null>(null)
const loading = ref(false)
const generating = ref(false)
const error = ref('')
const autoSelectionNotice = ref('')
const emptyState = ref<{ title: string; description: string } | null>(null)
const selectedAppName = ref('')
const trend = ref<Array<Record<string, unknown>>>([])
const buildSessions = ref<BuildSession[]>([])
type ReportViewType = CoverageTrendReportType
const comparisonReportType = ref<ReportViewType>('full')
const trendReportType = ref<ReportViewType>('full')
const jobStatus = ref<CoverageJobStatus>(null)
const jobLogs = ref<CoverageJobLog[]>([])
const deletingReportId = ref('')
const incrementalDialogOpen = ref(false)
const incrementalError = ref('')
const comparisonDialog = ref<{
  open: boolean
  type: 'added' | 'stable' | 'decreased'
  title: string
  label: string
  description: string
  methods: CoverageComparisonMethod[]
}>({ open: false, type: 'added', title: '', label: '', description: '', methods: [] })

const primaryReportKind = computed(() => coverageReportKind(payload.value?.report))
const currentCoverageVersionNumber = computed(() => payload.value?.version?.versionNumber || versionNumber.value)
const currentCoverageBranch = computed(() => payload.value?.version?.repoBranch || payload.value?.app.currentBranch || '')
const currentCoverageCommit = computed(() => payload.value?.version?.repoCommitId || commitId.value || payload.value?.app.currentCommitId || '')
const isCurrentCoverageVersion = computed(() => {
  const version = payload.value?.version
  const app = payload.value?.app
  if (!version || !app) return true
  return sameOptional(version.versionNumber, app.currentVersion)
    && sameOptional(version.repoBranch, app.currentBranch)
    && sameOptional(version.repoCommitId, app.currentCommitId)
})
const displayCoverageVersionNumber = computed(() => currentCoverageVersionNumber.value || payload.value?.report?.versionNumber || '未选择版本')
const versionFullReport = computed(() => payload.value?.versionFullReport || (primaryReportKind.value === 'full' ? payload.value?.report || null : null))
const currentCommitReport = computed(() => payload.value?.currentCommitReport || (primaryReportKind.value === 'commit' ? payload.value?.report || null : null))
const incrementalReport = computed(() => payload.value?.incrementalReport || (primaryReportKind.value === 'incremental' ? payload.value?.report || null : null))
const reportBuckets = computed<CoverageReportBucket[]>(() => [
  {
    type: 'commit',
    title: '本次 Commit 报告',
    detailLabel: '本次 Commit 明细',
    comparisonLabel: '本次 Commit 对比',
    emptyText: '暂无本次 Commit 报告',
    report: currentCommitReport.value,
    comparison: payload.value?.currentCommitComparison || (primaryReportKind.value === 'commit' ? payload.value?.comparison || null : null),
  },
  {
    type: 'incremental',
    title: '增量报告',
    detailLabel: '增量明细',
    comparisonLabel: '增量对比',
    emptyText: '暂无增量报告',
    report: incrementalReport.value,
    comparison: payload.value?.incrementalComparison || (primaryReportKind.value === 'incremental' ? payload.value?.comparison || null : null),
  },
])
const incrementalInitialReports = computed<IncrementalBaseReport[]>(() => [
  ...(versionFullReport.value ? [versionFullReport.value] : []),
  ...(currentCommitReport.value ? [currentCommitReport.value] : []),
  ...(payload.value?.report ? [payload.value.report] : []),
])
const defaultIncrementalBaseReport = computed<IncrementalBaseReport | null>(() => {
  const fallbackBaseReport = payload.value?.report?.reportType === 1 ? null : payload.value?.report || null
  return versionFullReport.value || currentCommitReport.value || fallbackBaseReport
})
const canGenerateCurrentCommit = computed(() => isCurrentCoverageVersion.value && Boolean(currentCoverageCommit.value))
const canGenerateIncremental = computed(() => isCurrentCoverageVersion.value && Boolean(currentCoverageVersionNumber.value))
const currentCommitGenerateTooltip = computed(() => {
  if (!isCurrentCoverageVersion.value) return '非当前版本仅支持查看覆盖率，不能生成报告'
  return currentCoverageCommit.value ? `只针对当前页面 Commit 生成报告：${currentCoverageCommit.value}` : '当前页面未绑定 CommitId，无法生成本次 Commit 报告'
})
const incrementalGenerateTooltip = computed(() => isCurrentCoverageVersion.value
  ? '选择基准版本或基准 Commit 后生成增量覆盖率报告，仅统计当前版本相对基准的变更范围。'
  : '非当前版本仅支持查看覆盖率，不能生成报告')
const comparisonOptions = computed(() => reportBuckets.value.map((bucket) => ({
  type: bucket.type,
  label: bucket.comparisonLabel,
  badge: bucket.title,
  report: bucket.report,
  comparison: bucket.comparison,
})))
const hasComparisonPanel = computed(() => comparisonOptions.value.some((option) => option.report || option.comparison))
const activeComparisonOption = computed(() => {
  const current = comparisonOptions.value.find((option) => option.type === comparisonReportType.value && (option.report || option.comparison))
  return current || comparisonOptions.value.find((option) => option.report || option.comparison) || comparisonOptions.value[0]
})
const activeComparison = computed(() => activeComparisonOption.value?.comparison || null)
const activeComparisonLabel = computed(() => activeComparisonOption.value?.badge || '报告')
const activeComparisonDescription = computed(() => {
  if (activeComparisonOption.value?.type === 'incremental') return '仅展示增量报告相对所选基准报告的覆盖变化，避免与版本全量口径混算。'
  if (activeComparisonOption.value?.type === 'commit') return '仅展示本次 Commit 报告与上一份同类型报告的覆盖变化，避免混入版本全量。'
  return '仅展示当前报告与上一份同类型报告的覆盖变化。'
})
const activeComparisonMeta = computed(() => {
  const report = activeComparisonOption.value?.report
  if (!report) return '暂无对应报告'
  const commit = shortHash(report.repoCommitId)
  if (activeComparisonOption.value?.type === 'incremental') {
    return `基准 ${report.baseVersionNumber || '-'} · Commit ${shortHash(report.baseRepoCommitId)}`
  }
  return `版本 ${report.versionNumber || '-'} · Commit ${commit}`
})
const comparisonReportId = computed(() => activeComparisonOption.value?.report?.id || '')
const {
  filteredTrend,
  trendCounts,
  trendMetric,
  trendDirectionTone,
  trendDirectionSymbol,
  trendDirectionText,
  trendDirectionTitle,
  trendTime,
  trendReportTypeLabel,
  trendReportMeta,
} = useCoverageTrendMetrics({
  trend,
  trendReportType,
  reportKind: trendReportKind,
  reportTypeText,
  shortHash,
})
const {
  jobProgress,
  jobFailed,
  jobStateText,
  jobStageText,
  appendJobLog,
  formatJobStage,
  readableJobError,
} = useCoverageJobProgress(jobStatus, jobLogs)

function shortHash(value?: string) {
  if (!value) return '-'
  return value.length > 18 ? `${value.slice(0, 12)}...${value.slice(-6)}` : value
}

function commitTooltip(value?: string) {
  return value || '暂无 CommitID'
}

function sameOptional(value?: string, current?: string) {
  return !current || !value || value === current
}

function coverageRate(covered?: number, total?: number) {
  if (!total) {
    return '0%'
  }
  return `${(((covered || 0) / total) * 100).toFixed(1)}%`
}

function trendReportKind(item: Record<string, unknown>): ReportViewType {
  return coverageReportKind({
    reportType: Number(item.reportType),
    repoCommitId: String(item.repoCommitId || ''),
    baseVersionNumber: String(item.baseVersionNumber || ''),
    baseRepoCommitId: String(item.baseRepoCommitId || ''),
  })
}

function coverageReportKind(report?: { reportType?: number; repoCommitId?: string; baseVersionNumber?: string; baseRepoCommitId?: string } | null): ReportViewType {
  if (!report) return 'full'
  const reportType = Number(report.reportType)
  if (reportType === 1 || normalizeCommit(report.baseVersionNumber) || normalizeCommit(report.baseRepoCommitId)) return 'incremental'
  if (reportType === 2) return 'commit'
  const reportCommit = normalizeCommit(report.repoCommitId)
  const versionCommit = normalizeCommit(payload.value?.version?.repoCommitId)
  const currentCommit = normalizeCommit(payload.value?.app.currentCommitId)
  if (reportCommit && versionCommit && reportCommit !== versionCommit) return 'commit'
  if (reportCommit && !versionCommit && currentCommit && reportCommit === currentCommit) return 'commit'
  return 'full'
}

function normalizeCommit(value?: string) {
  return value?.trim() || ''
}

function reportTypeText(type: ReportViewType) {
  if (type === 'incremental') return '增量报告'
  if (type === 'commit') return '本次 Commit 报告'
  return '版本全量报告'
}

function selectComparisonReportType(type: ReportViewType) {
  const option = comparisonOptions.value.find((item) => item.type === type)
  if (!option?.report && !option?.comparison) return
  comparisonReportType.value = type
}

function selectTrendReportType(type: ReportViewType) {
  if (!trendCounts.value[type]) return
  trendReportType.value = type
}

function syncReportTypeDefaults(selection: CoverageSelection) {
  const selectedType = selectedReportType(selection)
  comparisonReportType.value = selectedType
  const hasSelectedTrend = trendCounts.value[selectedType] > 0
  const hasCommitTrend = trendCounts.value.commit > 0
  const hasIncrementalTrend = trendCounts.value.incremental > 0
  trendReportType.value = hasSelectedTrend ? selectedType : hasCommitTrend ? 'commit' : hasIncrementalTrend ? 'incremental' : 'commit'
}

function selectedReportType(selection: CoverageSelection): ReportViewType {
  if (selection.reportId) {
    if (payload.value?.incrementalReport?.id === selection.reportId) return 'incremental'
    if (payload.value?.currentCommitReport?.id === selection.reportId) return 'commit'
    if (payload.value?.versionFullReport?.id === selection.reportId) return 'commit'
  }
  if (!payload.value?.report && payload.value?.incrementalReport) return 'incremental'
  return primaryReportKind.value === 'full' ? 'commit' : primaryReportKind.value
}

type CoverageSelection = {
  versionNumber: string
  reportId?: string
  commitId?: string
}

function versionPriority(version: VersionItemSummary) {
  return version.current ? 0 : 1
}

async function resolveCoverageSelection(): Promise<CoverageSelection | null> {
  if (!appId.value) {
    error.value = '缺少 appId'
    return null
  }
  if (versionNumber.value) {
    return {
      versionNumber: versionNumber.value,
      reportId: reportId.value || undefined,
      commitId: commitId.value || undefined,
    }
  }

  const center = await fetchVersionCenter(projectId.value, appId.value)
  selectedAppName.value = center.app?.name || ''
  const latestReport = (center.coverageReports || []).find((report) => !!report.versionNumber)
  if (latestReport?.versionNumber) {
    const latestVersion = (center.versions || []).find((version) => version.versionNumber === latestReport.versionNumber)
    const latestReportKind = latestReport.reportType === 1 || latestReport.baseVersionNumber || latestReport.baseRepoCommitId ? '增量' : latestReport.reportType === 2 || (latestReport.repoCommitId && latestVersion?.repoCommitId && latestReport.repoCommitId !== latestVersion.repoCommitId) ? '本次 Commit' : '版本全量'
    autoSelectionNotice.value = `已自动打开最新${latestReportKind}报告：${latestReport.versionNumber}`
    void router.replace({
      name: 'coverage-overview',
      params: { projectId: projectId.value, appId: appId.value },
      query: {
        versionNumber: latestReport.versionNumber,
        reportId: latestReport.id,
        commitId: latestReport.repoCommitId || undefined,
      },
    })
    return {
      versionNumber: latestReport.versionNumber,
      reportId: latestReport.id,
      commitId: latestReport.repoCommitId || undefined,
    }
  }

  const versions = [...(center.versions || [])]
    .filter((version) => !!version.versionNumber)
    .sort((left, right) => versionPriority(left) - versionPriority(right))
  const latestVersion = versions[0]
  if (latestVersion?.versionNumber) {
    autoSelectionNotice.value = `当前应用还没有覆盖率报告，已进入版本 ${latestVersion.versionNumber} 的报告生成页。`
    void router.replace({
      name: 'coverage-overview',
      params: { projectId: projectId.value, appId: appId.value },
      query: {
        versionNumber: latestVersion.versionNumber,
        commitId: latestVersion.repoCommitId || undefined,
      },
    })
    return {
      versionNumber: latestVersion.versionNumber,
      commitId: latestVersion.repoCommitId || undefined,
    }
  }

  emptyState.value = {
    title: center.app?.name ? `${center.app.name} 暂无可用版本` : '暂无可用版本',
    description: '覆盖率报告需要先创建或导入应用版本，再基于版本生成全量或增量报告。',
  }
  return null
}

async function load() {
  loading.value = true
  error.value = ''
  autoSelectionNotice.value = ''
  emptyState.value = null
  payload.value = null
  trend.value = []
  buildSessions.value = []
  try {
    const selection = await resolveCoverageSelection()
    if (!selection) {
      return
    }
    payload.value = await fetchCoverageOverviewV2(projectId.value, appId.value, {
      versionNumber: selection.versionNumber,
      reportId: selection.reportId,
      commitId: selection.commitId,
    })
    const [trendData, buildSessionPayload] = await Promise.all([
      fetchCoverageTrendV2(projectId.value, appId.value, selection.versionNumber),
      fetchBuildSessions(projectId.value, appId.value).catch(() => null),
    ])
    trend.value = trendData
    buildSessions.value = buildSessionPayload?.sessions || []
    syncReportTypeDefaults(selection)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载覆盖率概览失败'
  } finally {
    loading.value = false
  }
}

async function pollJob(jobId: string, title: string) {
  jobLogs.value = []
  appendJobLog(`已提交${title}任务`)
  while (true) {
    try {
      jobStatus.value = await fetchCoverageJobV2(jobId)
      appendJobLog(formatJobStage(jobStatus.value), jobStatus.value?.finish ? jobStatus.value.success === false ? 'error' : 'done' : 'running')
      if (jobStatus.value?.finish) return jobStatus.value.success !== false
    } catch (err) {
      const message = readableJobError(err)
      jobStatus.value = { progress: jobProgress.value, finish: true, success: false, message }
      appendJobLog(message, 'error')
      return false
    }
    await new Promise((resolve) => setTimeout(resolve, 1500))
  }
}

async function switchToCoverageSelection(version: string, commit?: string, selectedReportId?: string) {
  await router.replace({
    name: 'coverage-overview',
    params: { projectId: projectId.value, appId: appId.value },
    query: {
      versionNumber: version,
      reportId: selectedReportId || undefined,
      commitId: commit || undefined,
    },
  })
}

async function generateCurrentCommit() {
  if (!isCurrentCoverageVersion.value) {
    error.value = '非当前版本仅支持查看覆盖率，不能生成报告'
    return
  }
  if (!currentCoverageVersionNumber.value || !currentCoverageCommit.value) {
    error.value = '当前页面未绑定版本或 CommitId，无法生成本次 Commit 报告'
    return
  }
  generating.value = true
  error.value = ''
  try {
    const jobId = await triggerCoverageGenerateCurrentV2(projectId.value, appId.value, {
      versionNumber: currentCoverageVersionNumber.value,
      branch: currentCoverageBranch.value,
      commitId: currentCoverageCommit.value,
    })
    if (!(await pollJob(jobId, '本次 Commit 报告生成'))) return
    await switchToCoverageSelection(currentCoverageVersionNumber.value, currentCoverageCommit.value, jobStatus.value?.data)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '生成本次 Commit 报告失败'
  } finally {
    generating.value = false
  }
}

async function removeCoverageReport(targetReportId: string) {
  if (!targetReportId) return
  const confirmed = await dialog.confirm({
    title: '删除覆盖率报告',
    message: '确认删除该覆盖率报告？删除后需要重新生成才能查看。',
    confirmText: '确认删除',
    tone: 'danger',
  })
  if (!confirmed) return
  deletingReportId.value = targetReportId
  error.value = ''
  try {
    await deleteCoverageReportV2(projectId.value, targetReportId)
    const nextVersion = payload.value?.version?.versionNumber || payload.value?.report?.versionNumber || versionNumber.value
    const nextCommit = payload.value?.version?.repoCommitId || payload.value?.report?.repoCommitId || commitId.value
    if (nextVersion) await switchToCoverageSelection(nextVersion, nextCommit)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除覆盖率报告失败'
  } finally {
    deletingReportId.value = ''
  }
}

function closeIncrementalDialog() {
  if (generating.value) return
  incrementalDialogOpen.value = false
  incrementalError.value = ''
}

async function openIncrementalDialog() {
  if (!isCurrentCoverageVersion.value) {
    error.value = '非当前版本仅支持查看覆盖率，不能生成报告'
    return
  }
  if (!currentCoverageVersionNumber.value) {
    error.value = '缺少当前版本信息，无法生成增量报告'
    return
  }
  incrementalError.value = ''
  incrementalDialogOpen.value = true
}

async function generateIncremental(base: { baseVersionNumber: string; baseCommitId?: string }) {
  if (!isCurrentCoverageVersion.value) {
    incrementalError.value = '非当前版本仅支持查看覆盖率，不能生成报告'
    return
  }
  if (!currentCoverageVersionNumber.value) {
    incrementalError.value = '缺少当前版本信息'
    return
  }
  if (!base.baseVersionNumber) {
    incrementalError.value = '请填写基准版本号'
    return
  }
  generating.value = true
  error.value = ''
  incrementalError.value = ''
  try {
    const jobId = await triggerCoverageGenerateIncrementalV2(projectId.value, appId.value, {
      versionNumber: currentCoverageVersionNumber.value,
      branch: currentCoverageBranch.value,
      commitId: currentCoverageCommit.value,
      baseVersionNumber: base.baseVersionNumber,
      baseCommitId: base.baseCommitId || undefined,
    })
    incrementalDialogOpen.value = false
    if (!(await pollJob(jobId, '增量报告生成'))) return
    await switchToCoverageSelection(currentCoverageVersionNumber.value, currentCoverageCommit.value, jobStatus.value?.data)
    await load()
  } catch (err) {
    incrementalError.value = err instanceof Error ? err.message : '生成增量报告失败'
  } finally {
    generating.value = false
  }
}

function showComparisonMethods(type: 'added' | 'stable' | 'decreased') {
  const comparison = activeComparison.value
  if (!comparison) return
  const titleMap = { added: '新增覆盖方法', stable: '稳定覆盖方法', decreased: '覆盖下降方法' }
  const labelMap = { added: '新增覆盖', stable: '稳定覆盖', decreased: '下降覆盖' }
  const descriptionMap = {
    added: `${activeComparisonLabel.value}中新增进入覆盖范围的方法，可用于快速确认新增能力的测试触达情况。`,
    stable: `${activeComparisonLabel.value}中与上一版本保持覆盖的方法，可用于确认核心稳定路径是否持续被测试保护。`,
    decreased: `${activeComparisonLabel.value}中相比上一版本覆盖下降的方法，建议优先定位源码并补充用例或回放流量。`,
  }
  const methodMap = { added: comparison.addedMethods, stable: comparison.stableMethods, decreased: comparison.decreasedMethods }
  comparisonDialog.value = {
    open: true,
    type,
    title: titleMap[type],
    label: labelMap[type],
    description: descriptionMap[type],
    methods: methodMap[type] || [],
  }
}

onMounted(load)
</script>

<style scoped>
.page-header,
.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.page-header {
  margin-bottom: 12px;
}


.subtext {
  color: #64748b;
}

.secondary-link,
.ghost-link {
  color: #0f766e;
  font-weight: 700;
}

.primary-button,
.ghost-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  text-decoration: none;
}

.empty-state-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
}

.empty-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.inline-notice {
  margin: 0 0 16px;
  border: 1px solid rgba(15, 118, 110, .18);
  border-radius: 16px;
  padding: 12px 14px;
  background: rgba(240, 253, 250, .82);
  color: #0f766e;
  font-weight: 800;
}

.primary-button {
  background: #0f172a;
  color: #fff;
}

.ghost-button {
  background: rgba(15, 23, 42, 0.08);
}

.status-card,
.empty-state-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

@media (max-width: 960px) {
  .empty-state-card {
    align-items: stretch;
    flex-direction: column;
  }
}

</style>
