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
            :data-tooltip="currentCommitGenerateTooltip"
            @click="generateCurrentCommit"
          >
            生成本次 Commit 报告
          </button>
          <button
            class="ghost-button tooltip-button"
            type="button"
            :disabled="generating || !canGenerateIncremental"
            :data-tooltip="incrementalGenerateTooltip"
            @click="openIncrementalDialog"
          >
            生成增量报告
          </button>
          <RouterLink
            v-for="link in toolbarDetailLinks"
            :key="link.type"
            :class="['ghost-link', 'button-link', `${link.type}-detail-link`]"
            :to="{ name: 'coverage-details', params: { projectId, appId }, query: { reportId: link.report?.id } }"
          >
            查看{{ link.detailLabel }}
          </RouterLink>
        </div>
      </section>

      <div class="hero-grid coverage-metrics">
        <article v-for="bucket in reportBuckets" :key="bucket.type" class="hero-card">
          <span>{{ bucket.title }}</span>
          <strong>{{ coverageRate(bucket.report?.coveredLines, bucket.report?.totalLines) }}</strong>
          <small v-if="bucket.report">{{ bucket.report.coveredLines || 0 }} / {{ bucket.report.totalLines || 0 }}</small>
          <small v-else>{{ bucket.emptyText }}</small>
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
        <section v-for="bucket in reportBuckets" :key="bucket.type" class="panel report-summary-panel">
          <div class="panel-head">
            <h2>{{ bucket.title }}</h2>
            <span>{{ bucket.report?.createTimeText || '-' }}</span>
          </div>
          <div v-if="bucket.report" class="report-actions">
            <RouterLink class="report-action-link" :to="{ name: 'coverage-details', params: { projectId, appId }, query: { reportId: bucket.report.id } }">{{ bucket.detailLabel }}</RouterLink>
            <a class="report-action-link" :href="backendApiUrl(`/p/${projectId}/coverage/export?reportId=${bucket.report.id}`)">导出报告</a>
            <a class="report-action-link" :href="backendApiUrl(`/p/${projectId}/coverage/export-methods?reportId=${bucket.report.id}`)">导出方法</a>
            <button class="report-action-link danger" type="button" :disabled="!bucket.report.id || deletingReportId === bucket.report.id" @click="removeCoverageReport(bucket.report.id || '')">
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

      <section v-if="hasComparisonPanel" class="panel comparison-panel">
        <div class="panel-head typed-panel-head">
          <div>
            <h2>与上一版本对比</h2>
            <p class="subtext">{{ activeComparisonDescription }}</p>
          </div>
          <div class="report-type-switch" aria-label="切换对比报告类型">
            <button
              v-for="option in comparisonOptions"
              :key="option.type"
              type="button"
              :class="['type-switch-button', { active: comparisonReportType === option.type }]"
              :disabled="!option.report && !option.comparison"
              @click="selectComparisonReportType(option.type)"
            >
              {{ option.label }}
            </button>
          </div>
        </div>
        <div class="typed-panel-meta">
          <span :class="['trend-type-badge', activeComparisonOption.type]">{{ activeComparisonLabel }}</span>
          <span>{{ activeComparisonMeta }}</span>
        </div>
        <div v-if="activeComparison" class="hero-grid comparison-grid">
          <button class="hero-card clickable" type="button" @click="showComparisonMethods('added')">
            <span>新增覆盖</span>
            <strong>{{ comparisonRate(activeComparison.addedCount) }}</strong>
            <small>{{ activeComparison.addedCount }} 个方法 · 查看新增方法</small>
          </button>
          <button class="hero-card clickable" type="button" @click="showComparisonMethods('stable')">
            <span>稳定覆盖</span>
            <strong>{{ comparisonRate(activeComparison.stableCount) }}</strong>
            <small>{{ activeComparison.stableCount }} 个方法 · 查看稳定方法</small>
          </button>
          <button class="hero-card clickable" type="button" @click="showComparisonMethods('decreased')">
            <span>下降覆盖</span>
            <strong>{{ comparisonRate(activeComparison.decreasedCount) }}</strong>
            <small>{{ activeComparison.decreasedCount }} 个方法 · 查看下降方法</small>
          </button>
        </div>
        <div v-else class="empty-card">暂无{{ activeComparisonLabel }}的上一版本对比数据</div>
      </section>

      <section v-if="trend.length" class="panel trend-panel">
        <div class="panel-head typed-panel-head">
          <div>
            <h2>趋势数据</h2>
            <p class="subtext">按本次 Commit 与增量报告分别查看覆盖率变化，避免与版本全量口径混算。</p>
          </div>
          <div class="report-type-switch" aria-label="切换趋势报告类型">
            <button
              type="button"
              :class="['type-switch-button', { active: trendReportType === 'commit' }]"
              :disabled="!trendCounts.commit"
              @click="selectTrendReportType('commit')"
            >
              本次 Commit {{ trendCounts.commit }} 条
            </button>
            <button
              type="button"
              :class="['type-switch-button', { active: trendReportType === 'incremental' }]"
              :disabled="!trendCounts.incremental"
              @click="selectTrendReportType('incremental')"
            >
              增量 {{ trendCounts.incremental }} 条
            </button>
          </div>
        </div>
        <div v-if="filteredTrend.length" class="trend-list">
          <article v-for="(item, index) in filteredTrend" :key="String(item.reportId || item.timestamp || index)" class="trend-item">
            <div class="trend-time">
              <span :class="['trend-type-badge', trendReportKind(item)]">{{ trendReportTypeLabel(item) }}</span>
              <strong>{{ trendTime(item) }}</strong>
              <small>{{ trendReportMeta(item) }}</small>
            </div>
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
        <div v-else class="empty-card">暂无{{ reportTypeText(trendReportType) }}趋势数据</div>
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
  triggerCoverageGenerateCurrent,
  triggerCoverageGenerateIncremental,
} from '@/api/bootstrap'
import type { CoverageComparisonMethod, CoverageComparisonSummary, CoverageOverviewPayload, ExtendedCoverageReportSummary, VersionItemSummary } from '@/api/types'

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
type ReportViewType = 'full' | 'commit' | 'incremental'
const comparisonReportType = ref<ReportViewType>('full')
const trendReportType = ref<ReportViewType>('full')
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

type ReportBucket = {
  type: ReportViewType
  title: string
  detailLabel: string
  comparisonLabel: string
  emptyText: string
  report: ExtendedCoverageReportSummary | null
  comparison: CoverageComparisonSummary | null
}

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
const reportBuckets = computed<ReportBucket[]>(() => [
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
const baseReportOptions = computed(() => {
  const candidates: IncrementalBaseReport[] = [
    ...(versionFullReport.value ? [versionFullReport.value] : []),
    ...(currentCommitReport.value ? [currentCommitReport.value] : []),
    ...(payload.value?.report ? [payload.value.report] : []),
    ...incrementalBaseReports.value,
  ]
  const seen = new Set<string>()
  return candidates
    .filter((report) => report.versionNumber && reportTypeText(coverageReportKind(report)) !== '增量报告')
    .filter((report) => {
      const key = baseReportKey(report)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
})
const toolbarDetailLinks = computed(() => reportBuckets.value.filter((bucket) => bucket.report?.id))
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
  if (activeComparisonOption.value?.type === 'incremental') return '仅展示增量报告与上一份增量报告的覆盖变化，避免与全量口径混算。'
  if (activeComparisonOption.value?.type === 'commit') return '仅展示本次 Commit 报告与同 Commit 历史报告的覆盖变化，避免混入版本全量。'
  return '仅展示当前 Commit 报告与同 Commit 历史报告的覆盖变化。'
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
const filteredTrend = computed(() => trend.value.filter((item) => trendReportKind(item) === trendReportType.value))
const trendCounts = computed(() => ({
  full: trend.value.filter((item) => trendReportKind(item) === 'full').length,
  commit: trend.value.filter((item) => trendReportKind(item) === 'commit').length,
  incremental: trend.value.filter((item) => trendReportKind(item) === 'incremental').length,
}))
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

function sameOptional(value?: string, current?: string) {
  return !current || !value || value === current
}

function coverageRate(covered?: number, total?: number) {
  if (!total) {
    return '0%'
  }
  return `${(((covered || 0) / total) * 100).toFixed(1)}%`
}

function comparisonRate(count?: number) {
  const comparison = activeComparison.value
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
  const current = trendNumber(filteredTrend.value[index], key)
  const previous = trendNumber(filteredTrend.value[index - 1], key)
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

function trendReportTypeLabel(item: Record<string, unknown>) {
  return reportTypeText(trendReportKind(item)).replace('报告', '')
}

function trendReportMeta(item: Record<string, unknown>) {
  if (trendReportKind(item) === 'incremental') {
    return `基准 ${String(item.baseVersionNumber || '-')} · Commit ${shortHash(String(item.baseRepoCommitId || ''))}`
  }
  return `Commit ${shortHash(String(item.repoCommitId || ''))}`
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
    const jobId = await triggerCoverageGenerateCurrent(projectId.value, {
      appId: appId.value,
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
  if (!isCurrentCoverageVersion.value) {
    error.value = '非当前版本仅支持查看覆盖率，不能生成报告'
    return
  }
  if (!currentCoverageVersionNumber.value) {
    error.value = '缺少当前版本信息，无法生成增量报告'
    return
  }
  const fallbackBaseReport = payload.value?.report?.reportType === 1 ? null : payload.value?.report || null
  const defaultBaseReport = versionFullReport.value || currentCommitReport.value || fallbackBaseReport
  if (defaultBaseReport) {
    selectBaseReport(defaultBaseReport)
  } else {
    incrementalForm.value = { baseVersionNumber: '', baseCommitId: '', baseReportId: '' }
  }
  incrementalError.value = ''
  incrementalDialogOpen.value = true
  await loadIncrementalBaseReports()
  if (!defaultBaseReport && baseReportOptions.value.length) {
    selectBaseReport(baseReportOptions.value[0])
  }
}

async function generateIncremental() {
  if (!isCurrentCoverageVersion.value) {
    incrementalError.value = '非当前版本仅支持查看覆盖率，不能生成报告'
    return
  }
  if (!currentCoverageVersionNumber.value) {
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
      versionNumber: currentCoverageVersionNumber.value,
      branch: currentCoverageBranch.value,
      commitId: currentCoverageCommit.value,
      baseVersionNumber: incrementalForm.value.baseVersionNumber,
      baseCommitId: incrementalForm.value.baseCommitId || undefined,
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

function closeComparisonDialog() {
  comparisonDialog.value.open = false
  comparisonDialog.value.keyword = ''
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

.incremental-detail-link {
  background: rgba(37, 99, 235, .08);
  color: #2563eb;
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
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
  color: #64748b;
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

  .typed-panel-head,
  .trend-meta,
  .report-type-switch {
    justify-content: flex-start;
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
