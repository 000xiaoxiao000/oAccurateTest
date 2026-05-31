<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Coverage Overview</div>
        <h1>{{ payload?.app.name || selectedAppName || appId }}</h1>
        <p class="subtext">{{ versionNumber || payload?.report?.versionNumber || '未选择版本' }}</p>
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

      <section class="overview-command-card">
        <div>
          <div class="eyebrow">Coverage Actions</div>
          <h2>覆盖率报告工作台</h2>
          <p class="subtext">核心指标、生成操作和明细入口集中展示，避免浮层遮挡正文。</p>
        </div>
        <div class="coverage-toolbar">
          <button class="primary-button" type="button" :disabled="generating" @click="generateFull">
            {{ generating ? '处理中...' : '生成全量报告' }}
          </button>
          <button
            class="primary-button current-commit-button"
            type="button"
            :disabled="generating || !payload.app.currentCommitId"
            :title="payload.app.currentCommitId ? `当前 Commit：${payload.app.currentCommitId}` : '当前应用未配置 CommitId'"
            @click="generateCurrentCommit"
          >
            生成本次 Commit 报告
          </button>
          <button class="ghost-button" type="button" :disabled="generating || !payload.report" @click="openIncrementalDialog">
            生成增量报告
          </button>
          <RouterLink
            v-if="payload.report"
            class="ghost-link button-link"
            :to="{ name: 'coverage-details', params: { projectId, appId }, query: { reportId: payload.report.id } }"
          >
            查看明细
          </RouterLink>
        </div>
      </section>

      <div class="hero-grid coverage-metrics">
        <article class="hero-card">
          <span>全量报告</span>
          <strong>{{ coverageRate(payload.report?.coveredLines, payload.report?.totalLines) }}</strong>
          <small>{{ payload.report?.coveredLines || 0 }} / {{ payload.report?.totalLines || 0 }}</small>
        </article>
        <article class="hero-card">
          <span>增量报告</span>
          <strong>{{ coverageRate(payload.incrementalReport?.coveredLines, payload.incrementalReport?.totalLines) }}</strong>
          <small>{{ payload.incrementalReport?.coveredLines || 0 }} / {{ payload.incrementalReport?.totalLines || 0 }}</small>
        </article>
        <article class="hero-card">
          <span>报告状态</span>
          <strong>{{ payload.hasNewerData ? '需重算' : '最新' }}</strong>
          <small>{{ payload.hasNewerData ? '检测到有更新快照数据' : '当前报告已对齐最新数据' }}</small>
        </article>
      </div>

      <section v-if="jobStatus" class="panel job-panel" aria-live="polite">
        <div class="panel-head job-head">
          <div>
            <h2>生成任务</h2>
            <p class="subtext">覆盖率报告生成</p>
          </div>
          <div class="job-state-stack">
            <span :class="['job-state', jobFailed ? 'error' : jobStatus.finish ? 'done' : 'running']">{{ jobStateText }}</span>
            <strong>{{ jobProgress }}%</strong>
          </div>
        </div>

        <div class="progress-track" role="progressbar" :aria-valuenow="jobProgress" aria-valuemin="0" aria-valuemax="100">
          <span :style="{ width: `${jobProgress}%` }"></span>
        </div>

        <div class="job-summary-grid">
          <div class="job-summary-item">
            <span>当前阶段</span>
            <strong>{{ jobStageText }}</strong>
          </div>
          <div class="job-summary-item">
            <span>任务状态</span>
            <strong>{{ jobFailed ? '生成失败，请检查任务信息' : jobStatus.finish ? '已完成' : '请稍候，正在生成' }}</strong>
          </div>
        </div>

        <div v-if="jobLogs.length" class="job-log-list">
          <div v-for="(log, index) in jobLogs" :key="`${log.time}-${index}`" class="job-log-item" :data-tone="log.tone">
            <span>{{ log.time }}</span>
            <strong>{{ log.text }}</strong>
          </div>
        </div>
      </section>

      <div class="panel-grid report-workbench">
        <section class="panel report-summary-panel">
          <div class="panel-head">
            <h2>全量报告</h2>
            <span>{{ payload.report?.createTimeText || '-' }}</span>
          </div>
          <div v-if="payload.report" class="report-actions">
            <RouterLink class="report-action-link" :to="{ name: 'coverage-details', params: { projectId, appId }, query: { reportId: payload.report.id } }">明细</RouterLink>
            <a class="report-action-link" :href="backendApiUrl(`/p/${projectId}/coverage/export?reportId=${payload.report.id}`)">导出报告</a>
            <a class="report-action-link" :href="backendApiUrl(`/p/${projectId}/coverage/export-methods?reportId=${payload.report.id}`)">导出方法</a>
            <button class="report-action-link danger" type="button" :disabled="!payload.report.id || deletingReportId === payload.report.id" @click="removeCoverageReport(payload.report.id || '')">
              {{ deletingReportId === payload.report.id ? '删除中...' : '删除报告' }}
            </button>
          </div>
          <div v-if="payload.report" class="info-grid">
            <div class="info-item"><span>版本</span><strong>{{ payload.report.versionNumber || '-' }}</strong></div>
            <div class="info-item"><span>分支</span><strong>{{ payload.report.repoBranch || '-' }}</strong></div>
            <div class="info-item wide"><span>提交</span><strong>{{ shortHash(payload.report.repoCommitId) }}</strong></div>
            <div class="info-item"><span>类</span><strong>{{ payload.report.coveredClasses }} / {{ payload.report.totalClasses }}</strong></div>
            <div class="info-item"><span>方法</span><strong>{{ payload.report.coveredMethods }} / {{ payload.report.totalMethods }}</strong></div>
            <div class="info-item"><span>复杂度</span><strong>{{ payload.report.totalComplexity }}</strong></div>
          </div>
          <div v-else class="empty-card">暂无全量报告</div>
        </section>

        <section class="panel report-summary-panel">
          <div class="panel-head">
            <h2>增量报告</h2>
            <span>{{ payload.incrementalReport?.createTimeText || '-' }}</span>
          </div>
          <div v-if="payload.incrementalReport" class="report-actions">
            <RouterLink class="report-action-link" :to="{ name: 'coverage-details', params: { projectId, appId }, query: { reportId: payload.incrementalReport.id } }">明细</RouterLink>
            <a class="report-action-link" :href="backendApiUrl(`/p/${projectId}/coverage/export?reportId=${payload.incrementalReport.id}`)">导出报告</a>
            <a class="report-action-link" :href="backendApiUrl(`/p/${projectId}/coverage/export-methods?reportId=${payload.incrementalReport.id}`)">导出方法</a>
            <button class="report-action-link danger" type="button" :disabled="!payload.incrementalReport.id || deletingReportId === payload.incrementalReport.id" @click="removeCoverageReport(payload.incrementalReport.id || '')">
              {{ deletingReportId === payload.incrementalReport.id ? '删除中...' : '删除报告' }}
            </button>
          </div>
          <div v-if="payload.incrementalReport" class="info-grid">
            <div class="info-item"><span>版本</span><strong>{{ payload.incrementalReport.versionNumber || '-' }}</strong></div>
            <div class="info-item"><span>基准版本</span><strong>{{ payload.incrementalReport.baseVersionNumber || '-' }}</strong></div>
            <div class="info-item wide"><span>基准提交</span><strong>{{ shortHash(payload.incrementalReport.baseRepoCommitId) }}</strong></div>
            <div class="info-item"><span>类</span><strong>{{ payload.incrementalReport.coveredClasses }} / {{ payload.incrementalReport.totalClasses }}</strong></div>
            <div class="info-item"><span>方法</span><strong>{{ payload.incrementalReport.coveredMethods }} / {{ payload.incrementalReport.totalMethods }}</strong></div>
            <div class="info-item"><span>复杂度</span><strong>{{ payload.incrementalReport.totalComplexity }}</strong></div>
          </div>
          <div v-else class="empty-card">暂无增量报告</div>
        </section>
      </div>

      <section v-if="payload.comparison" class="panel comparison-panel">
        <div class="panel-head">
          <h2>与上一版对比</h2>
          <span>按覆盖变化快速定位方法</span>
        </div>
        <div class="hero-grid comparison-grid">
          <button class="hero-card clickable" type="button" @click="showComparisonMethods('added')">
            <span>新增覆盖</span>
            <strong>{{ payload.comparison.addedCount }}</strong>
            <small>查看新增方法</small>
          </button>
          <button class="hero-card clickable" type="button" @click="showComparisonMethods('stable')">
            <span>稳定覆盖</span>
            <strong>{{ payload.comparison.stableCount }}</strong>
            <small>查看稳定方法</small>
          </button>
          <button class="hero-card clickable" type="button" @click="showComparisonMethods('decreased')">
            <span>下降覆盖</span>
            <strong>{{ payload.comparison.decreasedCount }}</strong>
            <small>查看下降方法</small>
          </button>
        </div>
      </section>

      <section v-if="trend.length" class="panel trend-panel">
        <div class="panel-head">
          <h2>趋势数据</h2>
          <span>{{ trend.length }} 条</span>
        </div>
        <div class="trend-list">
          <article v-for="(item, index) in trend" :key="index" class="trend-item">
            <div class="trend-time">{{ trendTime(item) }}</div>
            <div class="trend-main">
              <strong>{{ trendMetric(item, 'lineCoverage') }}</strong>
              <span>行覆盖率</span>
            </div>
            <div class="trend-meta">
              <span>分支 {{ trendMetric(item, 'branchCoverage') }}</span>
              <span>方法 {{ trendMetric(item, 'methodCoverage') }}</span>
            </div>
          </article>
        </div>
      </section>
    </template>

    <div v-if="incrementalDialogOpen" class="modal-mask" @click.self="incrementalDialogOpen = false">
      <form class="modal-card" @submit.prevent="generateIncremental">
        <div class="panel-head">
          <h2>生成增量覆盖率报告</h2>
          <button class="text-button danger" type="button" @click="incrementalDialogOpen = false">关闭</button>
        </div>
        <label class="field">
          <span>基准版本号</span>
          <input v-model.trim="incrementalForm.baseVersionNumber" class="text-input" type="text" placeholder="例如 1.0.0" />
        </label>
        <label class="field">
          <span>基准 Commit</span>
          <input v-model.trim="incrementalForm.baseCommitId" class="text-input" type="text" placeholder="可为空，默认使用基准报告提交" />
        </label>
        <p class="subtext">当前版本：{{ payload?.version?.versionNumber || versionNumber }} · {{ payload?.version?.repoCommitId || '-' }}</p>
        <p v-if="error" class="error-text">{{ error }}</p>
        <div class="action-bar">
          <button class="primary-button" type="submit" :disabled="generating">{{ generating ? '处理中...' : '确认生成' }}</button>
          <button class="ghost-button" type="button" @click="incrementalDialogOpen = false">取消</button>
        </div>
      </form>
    </div>

    <div v-if="comparisonDialog.open" class="modal-mask" @click.self="comparisonDialog.open = false">
      <section class="modal-card">
        <div class="panel-head">
          <h2>{{ comparisonDialog.title }}</h2>
          <button class="text-button danger" type="button" @click="comparisonDialog.open = false">关闭</button>
        </div>
        <div class="method-list">
          <article v-for="method in comparisonDialog.methods" :key="`${method.className}-${method.methodName}-${method.methodDesc}`" class="method-item">
            <strong>{{ method.className }}</strong>
            <span>{{ method.methodName }}{{ method.methodDesc || '' }}</span>
          </article>
          <div v-if="!comparisonDialog.methods.length" class="empty-card">暂无方法明细</div>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { backendApiUrl } from '@/api/http'
import { useDialog } from '@/composables/useDialog'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import {
  deleteCoverageReport,
  fetchCoverageJob,
  fetchCoverageOverview,
  fetchCoverageTrend,
  fetchVersionCenter,
  triggerCoverageGenerate,
  triggerCoverageGenerateCurrent,
  triggerCoverageGenerateIncremental,
} from '@/api/bootstrap'
import type { CoverageComparisonMethod, CoverageOverviewPayload, VersionItemSummary } from '@/api/types'

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
const jobStatus = ref<{ progress?: number; progressName?: string; finish?: boolean; success?: boolean; message?: string } | null>(null)
const jobLogs = ref<Array<{ time: string; text: string; tone: 'running' | 'done' | 'error' }>>([])
const deletingReportId = ref('')
const incrementalDialogOpen = ref(false)
const incrementalForm = ref({ baseVersionNumber: '', baseCommitId: '' })
const comparisonDialog = ref<{ open: boolean; title: string; methods: CoverageComparisonMethod[] }>({ open: false, title: '', methods: [] })

function shortHash(value?: string) {
  if (!value) return '-'
  return value.length > 18 ? `${value.slice(0, 12)}...${value.slice(-6)}` : value
}

function coverageRate(covered?: number, total?: number) {
  if (!total) {
    return '0%'
  }
  return `${(((covered || 0) / total) * 100).toFixed(1)}%`
}

const jobProgress = computed(() => {
  const directProgress = Number(jobStatus.value?.progress)
  const parsedProgress = Number(parsedJobProgress.value?.percent)
  const progress = Number.isFinite(directProgress) ? directProgress : Number.isFinite(parsedProgress) ? parsedProgress : 0
  return Math.max(0, Math.min(100, Math.round(progress)))
})

const parsedJobProgress = computed(() => {
  const raw = jobStatus.value?.progressName || ''
  try {
    return JSON.parse(raw) as { name?: string; percent?: number; loaded?: number; total?: number }
  } catch {
    return null
  }
})

const jobFailed = computed(() => jobStatus.value?.success === false || /失败|错误|异常/.test(parsedJobProgress.value?.name || jobStatus.value?.message || ''))

const jobStateText = computed(() => {
  if (jobFailed.value) return '失败'
  return jobStatus.value?.finish ? '已完成' : '处理中'
})

const jobStageText = computed(() => {
  return formatJobStage(jobStatus.value)
})

function formatJobStage(status: typeof jobStatus.value) {
  const raw = status?.message || status?.progressName || '处理中'
  try {
    const parsed = JSON.parse(status?.progressName || '') as { name?: string; loaded?: number; total?: number }
    const name = parsed.name || raw
    if (typeof parsed.loaded === 'number' && typeof parsed.total === 'number' && parsed.total > 0) return `${name}（${parsed.loaded} / ${parsed.total}）`
    return name
  } catch {
    return raw
  }
}

function appendJobLog(text: string, tone: 'running' | 'done' | 'error' = 'running') {
  const last = jobLogs.value[jobLogs.value.length - 1]
  if (last?.text === text && last.tone === tone) return
  jobLogs.value = [...jobLogs.value, { time: new Date().toLocaleTimeString(), text, tone }].slice(-8)
}

function readableJobError(err: unknown) {
  const message = err instanceof Error ? err.message : '任务状态查询失败'
  if (message.includes('非 JSON')) return '任务状态接口返回异常响应，请稍后刷新或检查后端任务日志'
  return message
}

const trendKeyMap: Record<string, string[]> = {
  lineCoverage: ['lineCoverage', 'lineCoverageRate', 'lineRate'],
  branchCoverage: ['branchCoverage', 'branchCoverageRate', 'branchRate'],
  methodCoverage: ['methodCoverage', 'methodCoverageRate', 'methodRate'],
}

function trendValue(item: Record<string, unknown>, key: string) {
  for (const candidate of trendKeyMap[key] || [key]) {
    if (item[candidate] !== undefined && item[candidate] !== null) return item[candidate]
  }
  return undefined
}

function trendMetric(item: Record<string, unknown>, key: string) {
  const value = Number(trendValue(item, key))
  if (!Number.isFinite(value)) return '-'
  return `${value.toFixed(1)}%`
}

function trendTime(item: Record<string, unknown>) {
  const timestamp = Number(item.timestamp)
  if (Number.isFinite(timestamp) && timestamp > 0) {
    return new Date(timestamp).toLocaleString()
  }
  return String(item.time || item.createTimeText || '-')
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
    autoSelectionNotice.value = `已自动打开最新${latestReport.reportType === 1 ? '增量' : '全量'}报告：${latestReport.versionNumber}`
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
  try {
    const selection = await resolveCoverageSelection()
    if (!selection) {
      return
    }
    payload.value = await fetchCoverageOverview(projectId.value, {
      appId: appId.value,
      versionNumber: selection.versionNumber,
      reportId: selection.reportId,
      commitId: selection.commitId,
    })
    trend.value = await fetchCoverageTrend(projectId.value, appId.value, selection.versionNumber)
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
      jobStatus.value = await fetchCoverageJob(projectId.value, jobId)
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

async function switchToCoverageSelection(version: string, commit?: string) {
  await router.replace({
    name: 'coverage-overview',
    params: { projectId: projectId.value, appId: appId.value },
    query: {
      versionNumber: version,
      commitId: commit || undefined,
    },
  })
}

async function generateFull() {
  if (!payload.value?.version?.versionNumber) {
    error.value = '当前版本信息不完整，无法生成报告'
    return
  }
  generating.value = true
  error.value = ''
  try {
    const jobId = await triggerCoverageGenerate(projectId.value, {
      appId: appId.value,
      versionNumber: payload.value.version.versionNumber,
      branch: payload.value.version.repoBranch,
      commitId: payload.value.version.repoCommitId,
    })
    if (!(await pollJob(jobId, '全量报告生成'))) return
    await switchToCoverageSelection(payload.value.version.versionNumber, payload.value.version.repoCommitId)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '生成全量报告失败'
  } finally {
    generating.value = false
  }
}

async function generateCurrentCommit() {
  if (!payload.value?.app.currentVersion || !payload.value?.app.currentCommitId) {
    error.value = '当前应用未配置当前版本或当前 CommitId，无法生成本次 Commit 报告'
    return
  }
  generating.value = true
  error.value = ''
  try {
    const jobId = await triggerCoverageGenerateCurrent(projectId.value, appId.value)
    if (!(await pollJob(jobId, '本次 Commit 报告生成'))) return
    await switchToCoverageSelection(payload.value.app.currentVersion, payload.value.app.currentCommitId)
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
    await deleteCoverageReport(projectId.value, appId.value, targetReportId)
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

function openIncrementalDialog() {
  if (!payload.value?.version?.versionNumber || !payload.value?.report?.versionNumber) {
    error.value = '缺少版本或基准报告信息'
    return
  }
  incrementalForm.value = {
    baseVersionNumber: payload.value.report.versionNumber || payload.value.version.versionNumber || '',
    baseCommitId: payload.value.report.repoCommitId || '',
  }
  incrementalDialogOpen.value = true
}

async function generateIncremental() {
  if (!payload.value?.version?.versionNumber) {
    error.value = '缺少当前版本信息'
    return
  }
  if (!incrementalForm.value.baseVersionNumber) {
    error.value = '请填写基准版本号'
    return
  }
  generating.value = true
  error.value = ''
  try {
    const jobId = await triggerCoverageGenerateIncremental(projectId.value, {
      appId: appId.value,
      versionNumber: payload.value.version.versionNumber,
      branch: payload.value.version.repoBranch,
      commitId: payload.value.version.repoCommitId,
      baseVersionNumber: incrementalForm.value.baseVersionNumber,
      baseCommitId: incrementalForm.value.baseCommitId || undefined,
    })
    incrementalDialogOpen.value = false
    if (!(await pollJob(jobId, '增量报告生成'))) return
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '生成增量报告失败'
  } finally {
    generating.value = false
  }
}

function showComparisonMethods(type: 'added' | 'stable' | 'decreased') {
  const comparison = payload.value?.comparison
  if (!comparison) return
  const titleMap = { added: '新增覆盖方法', stable: '稳定覆盖方法', decreased: '覆盖下降方法' }
  const methodMap = { added: comparison.addedMethods, stable: comparison.stableMethods, decreased: comparison.decreasedMethods }
  comparisonDialog.value = { open: true, title: titleMap[type], methods: methodMap[type] || [] }
}

onMounted(load)
</script>

<style scoped>
.page-header,
.header-actions,
.action-bar,
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.page-header {
  margin-bottom: 20px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtext,
.info-item span {
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
.hero-card,
.panel,
.empty-card,
.empty-state-card,
.overview-command-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.action-bar,
.panel,
.panel-grid,
.hero-grid {
  margin-top: 18px;
}

.overview-command-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 18px;
  background: linear-gradient(135deg, rgba(255, 255, 255, .96), rgba(240, 253, 250, .82));
}

.overview-command-card h2 {
  margin: 4px 0 0;
}

.coverage-toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.current-commit-button {
  background: linear-gradient(135deg, #2563eb, #14b8a6);
}

.button-link {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(15, 118, 110, .08);
}

.hero-grid,
.panel-grid,
.info-grid {
  display: grid;
  gap: 14px;
}

.hero-grid,
.comparison-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.coverage-metrics {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.coverage-metrics .hero-card {
  min-height: 118px;
}

.coverage-metrics .hero-card strong {
  font-size: 30px;
}

.report-workbench {
  align-items: start;
}

.report-summary-panel {
  overflow: hidden;
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
  border: 1px solid rgba(15, 23, 42, .08);
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
  border: 1px solid rgba(15, 23, 42, .06);
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

.panel-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.info-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.report-summary-panel .info-grid {
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
}

.info-item {
  min-width: 0;
}

.info-item.wide {
  grid-column: span 2;
}

.info-item strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero-card strong,
.info-item strong {
  display: block;
  margin-top: 6px;
}

.hero-card.clickable {
  border: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
  cursor: pointer;
}

.hero-card.clickable:hover {
  border-color: rgba(15, 118, 110, .35);
  box-shadow: 0 12px 30px rgba(15, 118, 110, .12);
}

.field {
  display: grid;
  gap: 8px;
}

.text-input {
  width: 100%;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 14px;
  padding: 10px 12px;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, .44);
}

.modal-card {
  width: min(720px, calc(100vw - 32px));
  max-height: min(760px, calc(100vh - 48px));
  overflow: auto;
  display: grid;
  gap: 14px;
}

.method-list {
  display: grid;
  gap: 10px;
}

.method-item {
  display: grid;
  gap: 4px;
  padding: 12px;
  border-radius: 14px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, .08);
}

.text-button {
  border: 0;
  background: transparent;
  cursor: pointer;
  font-weight: 800;
}

.text-button.danger,
.error-text {
  color: #b91c1c;
}

.report-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 16px;
  padding-bottom: 12px;
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

.comparison-panel,
.trend-panel {
  overflow: hidden;
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
  color: #64748b;
  font-weight: 700;
}

.trend-main strong {
  display: inline;
  color: #0f172a;
  font-size: 22px;
}

.trend-main span,
.trend-meta {
  color: #64748b;
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

@media (max-width: 960px) {
  .hero-grid,
  .panel-grid,
  .info-grid,
  .job-summary-grid {
    grid-template-columns: 1fr;
  }

  .empty-state-card,
  .overview-command-card {
    align-items: stretch;
    flex-direction: column;
  }

  .coverage-toolbar {
    justify-content: flex-start;
  }

  .info-item.wide {
    grid-column: auto;
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

  .trend-item {
    grid-template-columns: 1fr;
  }

  .trend-meta {
    justify-content: flex-start;
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
