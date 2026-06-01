<template>
  <section>
    <div class="page-header plain-header coverage-page-header">
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
          <button
            class="primary-button tooltip-button"
            type="button"
            :disabled="generating"
            data-tooltip="基于当前选择的版本与 Commit 生成完整覆盖率报告，会覆盖该版本的全量报告视图。"
            @click="generateFull"
          >
            {{ generating ? '处理中...' : '生成全量报告' }}
          </button>
          <button
            class="primary-button current-commit-button tooltip-button"
            type="button"
            :disabled="generating || !payload.app.currentCommitId"
            :data-tooltip="payload.app.currentCommitId ? `只针对应用当前 Commit 生成报告：${payload.app.currentCommitId}` : '当前应用未配置 CommitId，无法生成本次 Commit 报告'"
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
        <article :class="['hero-card', 'status-metric-card', payload.hasNewerData ? 'warning' : 'success']">
          <span>报告状态</span>
          <strong><i aria-hidden="true"></i>{{ payload.hasNewerData ? '需重算' : '最新' }}</strong>
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
            <div class="info-item wide"><span>提交</span><strong class="commit-value" :title="commitTooltip(payload.report.repoCommitId)">{{ shortHash(payload.report.repoCommitId) }}</strong></div>
            <div class="info-item"><span>类覆盖</span><strong>{{ coverageRate(payload.report.coveredClasses, payload.report.totalClasses) }}</strong><small>{{ payload.report.coveredClasses }} / {{ payload.report.totalClasses }}</small></div>
            <div class="info-item"><span>方法覆盖</span><strong>{{ coverageRate(payload.report.coveredMethods, payload.report.totalMethods) }}</strong><small>{{ payload.report.coveredMethods }} / {{ payload.report.totalMethods }}</small></div>
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
            <div class="info-item wide"><span>基准提交</span><strong class="commit-value" :title="commitTooltip(payload.incrementalReport.baseRepoCommitId)">{{ shortHash(payload.incrementalReport.baseRepoCommitId) }}</strong></div>
            <div class="info-item"><span>类覆盖</span><strong>{{ coverageRate(payload.incrementalReport.coveredClasses, payload.incrementalReport.totalClasses) }}</strong><small>{{ payload.incrementalReport.coveredClasses }} / {{ payload.incrementalReport.totalClasses }}</small></div>
            <div class="info-item"><span>方法覆盖</span><strong>{{ coverageRate(payload.incrementalReport.coveredMethods, payload.incrementalReport.totalMethods) }}</strong><small>{{ payload.incrementalReport.coveredMethods }} / {{ payload.incrementalReport.totalMethods }}</small></div>
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
            <strong>{{ comparisonRate(payload.comparison.addedCount) }}</strong>
            <small>{{ payload.comparison.addedCount }} 个方法 · 查看新增方法</small>
          </button>
          <button class="hero-card clickable" type="button" @click="showComparisonMethods('stable')">
            <span>稳定覆盖</span>
            <strong>{{ comparisonRate(payload.comparison.stableCount) }}</strong>
            <small>{{ payload.comparison.stableCount }} 个方法 · 查看稳定方法</small>
          </button>
          <button class="hero-card clickable" type="button" @click="showComparisonMethods('decreased')">
            <span>下降覆盖</span>
            <strong>{{ comparisonRate(payload.comparison.decreasedCount) }}</strong>
            <small>{{ payload.comparison.decreasedCount }} 个方法 · 查看下降方法</small>
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
              <div class="trend-value-row">
                <strong>{{ trendMetric(item, 'lineCoverage') }}</strong>
                <span :class="['trend-direction', trendDirectionTone(index, 'lineCoverage')]" :title="trendDirectionTitle(index, 'lineCoverage')">
                  {{ trendDirectionSymbol(index, 'lineCoverage') }} {{ trendDirectionText(index, 'lineCoverage') }}
                </span>
              </div>
              <span>行覆盖率</span>
            </div>
            <div class="trend-meta">
              <span>分支 {{ trendMetric(item, 'branchCoverage') }} <b :class="trendDirectionTone(index, 'branchCoverage')">{{ trendDirectionSymbol(index, 'branchCoverage') }}</b></span>
              <span>方法 {{ trendMetric(item, 'methodCoverage') }} <b :class="trendDirectionTone(index, 'methodCoverage')">{{ trendDirectionSymbol(index, 'methodCoverage') }}</b></span>
            </div>
          </article>
        </div>
      </section>
    </template>

    <div v-if="incrementalDialogOpen" class="modal-mask" @click.self="closeIncrementalDialog">
      <form class="modal-card incremental-modal" @submit.prevent="generateIncremental">
        <div class="modal-head">
          <div>
            <div class="eyebrow">Incremental Coverage</div>
            <h2>生成增量覆盖率报告</h2>
            <p class="subtext">选择一个基准报告进行差异计算，也可手动指定基准版本与 Commit。</p>
          </div>
          <button class="modal-close" type="button" aria-label="关闭生成增量报告弹窗" @click="closeIncrementalDialog">×</button>
        </div>

        <div class="incremental-summary-grid">
          <article class="summary-tile current">
            <span>当前版本</span>
            <strong>{{ payload?.version?.versionNumber || versionNumber || '-' }}</strong>
            <small :title="commitTooltip(payload?.version?.repoCommitId)">Commit {{ shortHash(payload?.version?.repoCommitId) }}</small>
          </article>
          <article class="summary-tile base">
            <span>基准版本</span>
            <strong>{{ incrementalForm.baseVersionNumber || '未选择' }}</strong>
            <small :title="commitTooltip(incrementalForm.baseCommitId)">Commit {{ shortHash(incrementalForm.baseCommitId) }}</small>
          </article>
        </div>

        <section class="modal-section">
          <div class="modal-section-head">
            <div>
              <h3>选择基准报告</h3>
              <p class="subtext">优先选择已有全量报告，系统会自动填充基准版本号与 Commit。</p>
            </div>
            <span class="count-badge">{{ baseReportOptions.length }} 个可选</span>
          </div>
          <div v-if="incrementalBaseLoading" class="base-report-empty">正在加载基准报告...</div>
          <div v-else-if="baseReportOptions.length" class="base-report-list">
            <button
              v-for="report in baseReportOptions"
              :key="baseReportKey(report)"
              class="base-report-option"
              :class="{ active: baseReportKey(report) === incrementalForm.baseReportId }"
              type="button"
              @click="selectBaseReport(report)"
            >
              <span>
                <strong>{{ report.versionNumber || '-' }}</strong>
                <small>{{ report.repoBranch || '-' }} · {{ report.createTimeText || '-' }}</small>
              </span>
              <code :title="commitTooltip(report.repoCommitId)">{{ shortHash(report.repoCommitId) }}</code>
            </button>
          </div>
          <div v-else class="base-report-empty">暂无可选基准报告，请手动填写基准版本号。</div>
        </section>

        <section class="modal-section">
          <div class="modal-section-head compact">
            <h3>手动确认基准信息</h3>
          </div>
          <div class="incremental-form-grid">
            <label class="field">
              <span>基准版本号</span>
              <input v-model.trim="incrementalForm.baseVersionNumber" class="text-input" type="text" placeholder="例如 v1.0.0" />
            </label>
            <label class="field">
              <span>基准 Commit</span>
              <input v-model.trim="incrementalForm.baseCommitId" class="text-input" type="text" placeholder="可为空，默认使用基准报告提交" />
            </label>
          </div>
        </section>

        <div v-if="incrementalError" class="modal-error">{{ incrementalError }}</div>
        <div class="modal-actions">
          <button class="ghost-button" type="button" :disabled="generating" @click="closeIncrementalDialog">取消</button>
          <button class="primary-button" type="submit" :disabled="generating || !incrementalForm.baseVersionNumber">
            {{ generating ? '处理中...' : '确认生成' }}
          </button>
        </div>
      </form>
    </div>

    <div v-if="comparisonDialog.open" class="modal-mask" @click.self="closeComparisonDialog">
      <section class="modal-card comparison-modal">
        <div class="modal-head">
          <div>
            <div class="eyebrow">Coverage Diff Methods</div>
            <h2>{{ comparisonDialog.title }}</h2>
            <p class="subtext">{{ comparisonDialog.description }}</p>
          </div>
          <button class="modal-close" type="button" aria-label="关闭覆盖变化方法弹窗" @click="closeComparisonDialog">×</button>
        </div>

        <div class="comparison-modal-toolbar">
          <span :class="['comparison-tone-badge', comparisonDialog.type]">{{ comparisonDialog.label }}</span>
          <span class="count-badge">{{ filteredComparisonMethods.length }} / {{ comparisonDialog.methods.length }} 个方法</span>
          <input v-model.trim="comparisonDialog.keyword" class="text-input comparison-search" type="search" placeholder="搜索类名、方法名或签名" aria-label="搜索覆盖变化方法" />
        </div>

        <div v-if="filteredComparisonMethods.length" class="method-list comparison-method-list">
          <article v-for="method in filteredComparisonMethods" :key="`${method.className}-${method.methodName}-${method.methodDesc}`" class="method-item comparison-method-item">
            <div class="method-main">
              <strong>{{ method.className }}</strong>
              <span>{{ method.methodName }}{{ method.methodDesc || '' }}</span>
            </div>
            <RouterLink
              v-if="comparisonReportId"
              class="report-action-link"
              :to="{ name: 'coverage-code', params: { projectId, appId }, query: { reportId: comparisonReportId, className: method.className } }"
            >
              定位源码
            </RouterLink>
          </article>
        </div>
        <div v-else class="base-report-empty">暂无匹配方法</div>
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
const jobStatus = ref<{ data?: string; progress?: number; progressName?: string; finish?: boolean; success?: boolean; message?: string } | null>(null)
const jobLogs = ref<Array<{ time: string; text: string; tone: 'running' | 'done' | 'error' }>>([])
const deletingReportId = ref('')
const incrementalDialogOpen = ref(false)
const incrementalBaseLoading = ref(false)
const incrementalError = ref('')
const incrementalBaseReports = ref<IncrementalBaseReport[]>([])
const incrementalForm = ref({ baseVersionNumber: '', baseCommitId: '', baseReportId: '' })
const comparisonDialog = ref<{
  open: boolean
  type: 'added' | 'stable' | 'decreased'
  title: string
  label: string
  description: string
  keyword: string
  methods: CoverageComparisonMethod[]
}>({ open: false, type: 'added', title: '', label: '', description: '', keyword: '', methods: [] })

type IncrementalBaseReport = {
  id?: string
  versionNumber?: string
  repoBranch?: string
  repoCommitId?: string
  createTimeText?: string
  reportType?: number
}

const baseReportOptions = computed(() => {
  const candidates: IncrementalBaseReport[] = [
    ...(payload.value?.report ? [payload.value.report] : []),
    ...incrementalBaseReports.value,
  ]
  const seen = new Set<string>()
  return candidates
    .filter((report) => report.versionNumber && report.reportType !== 1)
    .filter((report) => {
      const key = baseReportKey(report)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
})
const comparisonReportId = computed(() => payload.value?.incrementalReport?.id || payload.value?.report?.id || '')
const filteredComparisonMethods = computed(() => {
  const keyword = comparisonDialog.value.keyword.toLowerCase()
  if (!keyword) return comparisonDialog.value.methods
  return comparisonDialog.value.methods.filter((method) => [method.className, method.methodName, method.methodDesc]
    .join(' ')
    .toLowerCase()
    .includes(keyword))
})

function shortHash(value?: string) {
  if (!value) return '-'
  return value.length > 18 ? `${value.slice(0, 12)}...${value.slice(-6)}` : value
}

function commitTooltip(value?: string) {
  return value || '暂无 CommitID'
}

function coverageRate(covered?: number, total?: number) {
  if (!total) {
    return '0%'
  }
  return `${(((covered || 0) / total) * 100).toFixed(1)}%`
}

function comparisonRate(count?: number) {
  const comparison = payload.value?.comparison
  const total = (comparison?.addedCount || 0) + (comparison?.stableCount || 0) + (comparison?.decreasedCount || 0)
  return coverageRate(count, total)
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

type TrendTone = 'up' | 'flat' | 'down'

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

function trendNumber(item: Record<string, unknown> | undefined, key: string) {
  const value = Number(item ? trendValue(item, key) : undefined)
  return Number.isFinite(value) ? value : null
}

function trendDelta(index: number, key: string) {
  const current = trendNumber(trend.value[index], key)
  const previous = trendNumber(trend.value[index - 1], key)
  if (current === null || previous === null) return null
  return current - previous
}

function trendDirectionTone(index: number, key: string): TrendTone {
  const delta = trendDelta(index, key)
  if (delta === null || Math.abs(delta) < 0.05) return 'flat'
  return delta > 0 ? 'up' : 'down'
}

function trendDirectionSymbol(index: number, key: string) {
  const tone = trendDirectionTone(index, key)
  if (tone === 'up') return '↗'
  if (tone === 'down') return '↘'
  return '→'
}

function trendDirectionText(index: number, key: string) {
  const delta = trendDelta(index, key)
  if (delta === null) return '水平'
  const absDelta = Math.abs(delta).toFixed(1)
  if (Math.abs(delta) < 0.05) return '水平'
  return delta > 0 ? `上升 ${absDelta}%` : `下降 ${absDelta}%`
}

function trendDirectionTitle(index: number, key: string) {
  const delta = trendDelta(index, key)
  if (delta === null) return '暂无上一条趋势数据可比对'
  return `较上一条${trendDirectionText(index, key)}`
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
    await switchToCoverageSelection(payload.value.version.versionNumber, payload.value.version.repoCommitId, jobStatus.value?.data)
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
    await switchToCoverageSelection(payload.value.app.currentVersion, payload.value.app.currentCommitId, jobStatus.value?.data)
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

function baseReportKey(report: IncrementalBaseReport) {
  return report.id || `${report.versionNumber || ''}:${report.repoCommitId || ''}`
}

function selectBaseReport(report: IncrementalBaseReport) {
  incrementalForm.value = {
    baseVersionNumber: report.versionNumber || '',
    baseCommitId: report.repoCommitId || '',
    baseReportId: baseReportKey(report),
  }
  incrementalError.value = ''
}

function closeIncrementalDialog() {
  if (generating.value) return
  incrementalDialogOpen.value = false
  incrementalError.value = ''
}

async function loadIncrementalBaseReports() {
  incrementalBaseLoading.value = true
  try {
    const center = await fetchVersionCenter(projectId.value, appId.value)
    incrementalBaseReports.value = center.coverageReports || []
  } catch (err) {
    incrementalError.value = err instanceof Error ? err.message : '加载基准报告失败，可手动填写基准信息'
  } finally {
    incrementalBaseLoading.value = false
  }
}

async function openIncrementalDialog() {
  if (!payload.value?.version?.versionNumber || !payload.value?.report?.versionNumber) {
    error.value = '缺少版本或基准报告信息'
    return
  }
  incrementalForm.value = {
    baseVersionNumber: payload.value.report.versionNumber || payload.value.version.versionNumber || '',
    baseCommitId: payload.value.report.repoCommitId || '',
    baseReportId: baseReportKey(payload.value.report),
  }
  incrementalError.value = ''
  incrementalDialogOpen.value = true
  await loadIncrementalBaseReports()
}

async function generateIncremental() {
  if (!payload.value?.version?.versionNumber) {
    incrementalError.value = '缺少当前版本信息'
    return
  }
  if (!incrementalForm.value.baseVersionNumber) {
    incrementalError.value = '请填写基准版本号'
    return
  }
  generating.value = true
  error.value = ''
  incrementalError.value = ''
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
    incrementalError.value = err instanceof Error ? err.message : '生成增量报告失败'
  } finally {
    generating.value = false
  }
}

function closeComparisonDialog() {
  comparisonDialog.value.open = false
  comparisonDialog.value.keyword = ''
}

function showComparisonMethods(type: 'added' | 'stable' | 'decreased') {
  const comparison = payload.value?.comparison
  if (!comparison) return
  const titleMap = { added: '新增覆盖方法', stable: '稳定覆盖方法', decreased: '覆盖下降方法' }
  const labelMap = { added: '新增覆盖', stable: '稳定覆盖', decreased: '下降覆盖' }
  const descriptionMap = {
    added: '这些方法在当前版本新增进入覆盖范围，可用于快速确认新增能力的测试触达情况。',
    stable: '这些方法与上一版本保持覆盖，可用于确认核心稳定路径是否持续被测试保护。',
    decreased: '这些方法相比上一版本覆盖下降，建议优先定位源码并补充用例或回放流量。',
  }
  const methodMap = { added: comparison.addedMethods, stable: comparison.stableMethods, decreased: comparison.decreasedMethods }
  comparisonDialog.value = {
    open: true,
    type,
    title: titleMap[type],
    label: labelMap[type],
    description: descriptionMap[type],
    keyword: '',
    methods: methodMap[type] || [],
  }
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
  padding: 16px;
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
  margin-top: 12px;
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
  gap: 10px;
}

.hero-grid,
.comparison-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.coverage-metrics {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.coverage-metrics .hero-card {
  min-height: 86px;
  padding: 14px 16px;
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

.info-item small {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.commit-value,
.commit-inline {
  cursor: default;
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
  background: rgba(15, 23, 42, .52);
  backdrop-filter: blur(8px);
}

.modal-card {
  width: min(720px, calc(100vw - 32px));
  max-height: min(760px, calc(100vh - 48px));
  overflow: auto;
  display: grid;
  gap: 14px;
  padding: 22px;
  border-radius: 24px;
  background: rgba(255, 255, 255, .98);
  border: 1px solid rgba(15, 23, 42, .08);
  box-shadow: 0 28px 70px rgba(15, 23, 42, .24);
}

.incremental-modal {
  width: min(820px, calc(100vw - 32px));
}

.modal-head,
.modal-section-head,
.modal-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.modal-head {
  align-items: flex-start;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(15, 23, 42, .08);
}

.modal-head h2,
.modal-head .subtext,
.modal-section-head h3,
.modal-section-head .subtext {
  margin: 0;
}

.modal-head h2 {
  margin-top: 4px;
}

.modal-close {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 999px;
  background: rgba(15, 23, 42, .06);
  color: #334155;
  cursor: pointer;
  font-size: 24px;
  line-height: 1;
}

.modal-close:hover {
  background: rgba(220, 38, 38, .10);
  color: #dc2626;
}

.incremental-summary-grid,
.incremental-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.summary-tile {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, .08);
  background: rgba(248, 250, 252, .86);
}

.summary-tile.current {
  border-color: rgba(15, 118, 110, .18);
  background: rgba(240, 253, 250, .86);
}

.summary-tile.base {
  border-color: rgba(37, 99, 235, .14);
  background: rgba(239, 246, 255, .82);
}

.summary-tile span,
.summary-tile small {
  color: #64748b;
  font-weight: 700;
}

.summary-tile strong,
.summary-tile small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modal-section {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 20px;
  border: 1px solid rgba(15, 23, 42, .08);
  background: rgba(248, 250, 252, .64);
}

.modal-section-head.compact {
  justify-content: flex-start;
}

.count-badge {
  flex: 0 0 auto;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(37, 99, 235, .08);
  color: #2563eb;
  font-size: 13px;
  font-weight: 800;
}

.base-report-list {
  display: grid;
  gap: 8px;
  max-height: 240px;
  overflow: auto;
  padding-right: 4px;
}

.base-report-option {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  width: 100%;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 16px;
  padding: 12px;
  background: rgba(255, 255, 255, .88);
  text-align: left;
  cursor: pointer;
}

.base-report-option:hover,
.base-report-option.active {
  border-color: rgba(15, 118, 110, .26);
  background: rgba(240, 253, 250, .92);
}

.base-report-option span {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.base-report-option small,
.base-report-option code {
  color: #64748b;
}

.base-report-option strong,
.base-report-option small,
.base-report-option code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.base-report-option code {
  max-width: 180px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.base-report-empty,
.modal-error {
  padding: 12px 14px;
  border-radius: 16px;
}

.base-report-empty {
  background: rgba(241, 245, 249, .9);
  color: #64748b;
  text-align: center;
}

.modal-error {
  border: 1px solid rgba(185, 28, 28, .14);
  background: rgba(254, 242, 242, .92);
  color: #b91c1c;
  font-weight: 800;
}

.modal-actions {
  justify-content: flex-end;
  padding-top: 4px;
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

.comparison-modal {
  width: min(780px, calc(100vw - 32px));
}

.comparison-modal-toolbar {
  display: grid;
  grid-template-columns: auto auto minmax(220px, 1fr);
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 18px;
  background: rgba(248, 250, 252, .78);
  border: 1px solid rgba(15, 23, 42, .07);
}

.comparison-search {
  min-width: 0;
}

.comparison-tone-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 11px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

.comparison-tone-badge::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: currentColor;
}

.comparison-tone-badge.added {
  background: rgba(22, 163, 74, .12);
  color: #15803d;
}

.comparison-tone-badge.stable {
  background: rgba(37, 99, 235, .10);
  color: #2563eb;
}

.comparison-tone-badge.decreased {
  background: rgba(220, 38, 38, .10);
  color: #dc2626;
}

.comparison-method-list {
  max-height: min(520px, calc(100vh - 360px));
  overflow: auto;
  padding-right: 4px;
}

.comparison-method-item {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  background: rgba(255, 255, 255, .92);
}

.method-main {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.method-main strong,
.method-main span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.method-main span {
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
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
  .hero-grid,
  .panel-grid,
  .info-grid,
  .job-summary-grid {
    grid-template-columns: 1fr;
  }

  .incremental-summary-grid,
  .incremental-form-grid,
  .base-report-option,
  .comparison-modal-toolbar,
  .comparison-method-item {
    grid-template-columns: 1fr;
  }

  .base-report-option code {
    max-width: 100%;
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
