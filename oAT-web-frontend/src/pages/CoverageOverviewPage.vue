<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Coverage Overview</div>
        <h1>{{ payload?.app.name || appId }}</h1>
        <p class="subtext">{{ versionNumber || payload?.report?.versionNumber || '未选择版本' }}</p>
      </div>
      <div class="header-actions">
        <RouterLink class="secondary-link" :to="`/p/${projectId}/coverage`">返回覆盖率中心</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载覆盖率概览...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <div class="action-bar">
        <button class="primary-button" type="button" :disabled="generating" @click="generateFull">
          {{ generating ? '处理中...' : '生成全量报告' }}
        </button>
        <button class="ghost-button" type="button" :disabled="generating || !payload.report" @click="openIncrementalDialog">
          生成增量报告
        </button>
        <RouterLink
          v-if="payload.report"
          class="ghost-link"
          :to="{ name: 'coverage-details', params: { projectId, appId }, query: { reportId: payload.report.id } }"
        >
          查看明细
        </RouterLink>
      </div>

      <div class="hero-grid">
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

      <div v-if="jobStatus" class="panel">
        <div class="panel-head">
          <h2>生成任务</h2>
          <span>{{ jobStatus.progress || 0 }}%</span>
        </div>
        <p class="subtext">{{ jobStatus.progressName || '处理中' }}</p>
      </div>

      <div class="panel-grid">
        <section class="panel">
          <div class="panel-head">
            <h2>全量报告</h2>
            <span>{{ payload.report?.createTimeText || '-' }}</span>
          </div>
          <div v-if="payload.report" class="report-actions">
            <RouterLink class="ghost-link" :to="{ name: 'coverage-details', params: { projectId, appId }, query: { reportId: payload.report.id } }">明细</RouterLink>
            <a class="ghost-link" :href="backendApiUrl(`/api/projects/${projectId}/coverage/export?reportId=${payload.report.id}`)">导出报告</a>
            <a class="ghost-link" :href="backendApiUrl(`/api/projects/${projectId}/coverage/export-methods?reportId=${payload.report.id}`)">导出方法</a>
          </div>
          <div v-if="payload.report" class="info-grid">
            <div class="info-item"><span>版本</span><strong>{{ payload.report.versionNumber || '-' }}</strong></div>
            <div class="info-item"><span>分支</span><strong>{{ payload.report.repoBranch || '-' }}</strong></div>
            <div class="info-item"><span>提交</span><strong>{{ payload.report.repoCommitId || '-' }}</strong></div>
            <div class="info-item"><span>类</span><strong>{{ payload.report.coveredClasses }} / {{ payload.report.totalClasses }}</strong></div>
            <div class="info-item"><span>方法</span><strong>{{ payload.report.coveredMethods }} / {{ payload.report.totalMethods }}</strong></div>
            <div class="info-item"><span>复杂度</span><strong>{{ payload.report.totalComplexity }}</strong></div>
          </div>
          <div v-else class="empty-card">暂无全量报告</div>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>增量报告</h2>
            <span>{{ payload.incrementalReport?.createTimeText || '-' }}</span>
          </div>
          <div v-if="payload.incrementalReport" class="report-actions">
            <RouterLink class="ghost-link" :to="{ name: 'coverage-details', params: { projectId, appId }, query: { reportId: payload.incrementalReport.id } }">明细</RouterLink>
            <a class="ghost-link" :href="backendApiUrl(`/api/projects/${projectId}/coverage/export?reportId=${payload.incrementalReport.id}`)">导出报告</a>
            <a class="ghost-link" :href="backendApiUrl(`/api/projects/${projectId}/coverage/export-methods?reportId=${payload.incrementalReport.id}`)">导出方法</a>
          </div>
          <div v-if="payload.incrementalReport" class="info-grid">
            <div class="info-item"><span>版本</span><strong>{{ payload.incrementalReport.versionNumber || '-' }}</strong></div>
            <div class="info-item"><span>基准版本</span><strong>{{ payload.incrementalReport.baseVersionNumber || '-' }}</strong></div>
            <div class="info-item"><span>基准提交</span><strong>{{ payload.incrementalReport.baseRepoCommitId || '-' }}</strong></div>
            <div class="info-item"><span>类</span><strong>{{ payload.incrementalReport.coveredClasses }} / {{ payload.incrementalReport.totalClasses }}</strong></div>
            <div class="info-item"><span>方法</span><strong>{{ payload.incrementalReport.coveredMethods }} / {{ payload.incrementalReport.totalMethods }}</strong></div>
            <div class="info-item"><span>复杂度</span><strong>{{ payload.incrementalReport.totalComplexity }}</strong></div>
          </div>
          <div v-else class="empty-card">暂无增量报告</div>
        </section>
      </div>

      <section v-if="payload.comparison" class="panel">
        <div class="panel-head">
          <h2>与上一版对比</h2>
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

      <section v-if="trend.length" class="panel">
        <div class="panel-head">
          <h2>趋势数据</h2>
        </div>
        <div class="trend-list">
          <article v-for="(item, index) in trend" :key="index" class="trend-item">
            <strong>{{ formatTrend(item) }}</strong>
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
import { RouterLink, useRoute } from 'vue-router'

import {
  fetchCoverageJob,
  fetchCoverageOverview,
  fetchCoverageTrend,
  triggerCoverageGenerate,
  triggerCoverageGenerateIncremental,
} from '@/api/bootstrap'
import type { CoverageComparisonMethod, CoverageOverviewPayload } from '@/api/types'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const versionNumber = computed(() => String(route.query.versionNumber || ''))
const reportId = computed(() => String(route.query.reportId || ''))
const commitId = computed(() => String(route.query.commitId || ''))
const payload = ref<CoverageOverviewPayload | null>(null)
const loading = ref(false)
const generating = ref(false)
const error = ref('')
const trend = ref<Array<Record<string, unknown>>>([])
const jobStatus = ref<{ progress?: number; progressName?: string; finish?: boolean } | null>(null)
const incrementalDialogOpen = ref(false)
const incrementalForm = ref({ baseVersionNumber: '', baseCommitId: '' })
const comparisonDialog = ref<{ open: boolean; title: string; methods: CoverageComparisonMethod[] }>({ open: false, title: '', methods: [] })

function coverageRate(covered?: number, total?: number) {
  if (!total) {
    return '0%'
  }
  return `${(((covered || 0) / total) * 100).toFixed(1)}%`
}

function formatTrend(item: Record<string, unknown>) {
  return Object.entries(item)
    .map(([key, value]) => `${key}: ${value}`)
    .join(' · ')
}

async function load() {
  if (!appId.value || !versionNumber.value) {
    error.value = '缺少 appId 或 versionNumber'
    return
  }
  loading.value = true
  error.value = ''
  try {
    payload.value = await fetchCoverageOverview(projectId.value, {
      appId: appId.value,
      versionNumber: versionNumber.value,
      reportId: reportId.value || undefined,
      commitId: commitId.value || undefined,
    })
    trend.value = await fetchCoverageTrend(projectId.value, appId.value, versionNumber.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载覆盖率概览失败'
  } finally {
    loading.value = false
  }
}

async function pollJob(jobId: string) {
  while (true) {
    jobStatus.value = await fetchCoverageJob(projectId.value, jobId)
    if (jobStatus.value?.finish) {
      break
    }
    await new Promise((resolve) => setTimeout(resolve, 1500))
  }
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
    await pollJob(jobId)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '生成全量报告失败'
  } finally {
    generating.value = false
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
    await pollJob(jobId)
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
.empty-card {
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

.panel-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.info-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
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
  gap: 10px;
  margin: 12px 0;
}

.trend-list {
  display: grid;
  gap: 10px;
}

.trend-item {
  padding: 12px;
  border-radius: 14px;
  background: #f8fbfb;
}

@media (max-width: 960px) {
  .hero-grid,
  .panel-grid,
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
