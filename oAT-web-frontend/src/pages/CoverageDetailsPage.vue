<template>
  <section>
    <div class="page-header plain-header coverage-page-header">
      <div>
        <div class="eyebrow">Coverage Details</div>
        <h1>{{ reportApp?.name || appId }}</h1>
        <p class="subtext">{{ reportSummary?.versionNumber || '-' }}</p>
      </div>
      <div class="header-actions">
        <RouterLink class="secondary-link" :to="backRoute">返回概览</RouterLink>
        <a v-if="reportId" class="secondary-link" :href="coverageReportExportUrl(projectId, reportId)">导出报告</a>
        <a v-if="reportId" class="secondary-link" :href="coverageMethodExportUrl(projectId, reportId)">导出方法</a>
      </div>
    </div>

    <form class="filter-card" @submit.prevent="applyFilters">
      <div class="filter-row">
        <div class="quick-filters" aria-label="常用筛选条件">
          <label class="search-field">
            <span>类名</span>
            <input v-model.trim="filters.className" class="text-input" type="search" placeholder="支持模糊匹配" aria-label="搜索类名" />
          </label>
          <label class="search-field">
            <span>方法名</span>
            <input v-model.trim="filters.methodName" class="text-input" type="search" placeholder="类中包含该方法" aria-label="搜索方法名" />
          </label>
        </div>

        <div class="filter-actions">
          <button class="primary-button" type="submit">搜索</button>
          <button class="ghost-button" type="button" @click="clearFilters">清空</button>
          <div class="view-toggle" role="group" aria-label="切换展示方式">
            <button type="button" :class="['tab-button', viewType === 'list' && 'active']" @click="switchView('list')">列表</button>
            <button type="button" :class="['tab-button', viewType === 'tree' && 'active']" @click="switchView('tree')">树结构</button>
          </div>
        </div>
      </div>

      <details class="advanced-filters">
        <summary>
          <span>高级筛选</span>
          <em v-if="advancedFilterCount">已选 {{ advancedFilterCount }} 项</em>
        </summary>
        <div class="metric-filter-grid">
          <fieldset class="metric-filter-group">
            <legend>行覆盖率</legend>
            <label><span>最小</span><input v-model.number="filters.minRate" class="text-input" type="number" min="0" max="100" step="0.01" placeholder="0" /></label>
            <label><span>最大</span><input v-model.number="filters.maxRate" class="text-input" type="number" min="0" max="100" step="0.01" placeholder="100" /></label>
          </fieldset>
          <fieldset class="metric-filter-group">
            <legend>分支覆盖</legend>
            <label><span>最小</span><input v-model.number="filters.minBranchRate" class="text-input" type="number" min="0" max="100" step="0.01" placeholder="0" /></label>
            <label><span>最大</span><input v-model.number="filters.maxBranchRate" class="text-input" type="number" min="0" max="100" step="0.01" placeholder="100" /></label>
          </fieldset>
          <fieldset class="metric-filter-group">
            <legend>方法覆盖</legend>
            <label><span>最小</span><input v-model.number="filters.minMethodRate" class="text-input" type="number" min="0" max="100" step="0.01" placeholder="0" /></label>
            <label><span>最大</span><input v-model.number="filters.maxMethodRate" class="text-input" type="number" min="0" max="100" step="0.01" placeholder="100" /></label>
          </fieldset>
          <fieldset class="metric-filter-group">
            <legend>圈复杂度</legend>
            <label><span>最小</span><input v-model.number="filters.minComplexity" class="text-input" type="number" min="0" placeholder="0" /></label>
            <label><span>最大</span><input v-model.number="filters.maxComplexity" class="text-input" type="number" min="0" placeholder="不限" /></label>
          </fieldset>
        </div>
      </details>
    </form>

    <div v-if="loading" class="status-card">正在加载覆盖率明细...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="hasCoverageContent">
      <section class="core-summary">
        <div>
          <span>统一模型语言</span>
          <strong>{{ coverageLanguage }}</strong>
        </div>
        <div>
          <span>CoverageUnit</span>
          <strong>{{ coverageUnitCount }}</strong>
        </div>
        <div>
          <span>函数/方法</span>
          <strong>{{ coverageUnitFunctionCount }}</strong>
        </div>
        <div>
          <span>源码类</span>
          <strong>{{ coverageSourceUnitCount }}</strong>
        </div>
      </section>

      <section class="analytics-summary">
        <div :class="['gate-card', qualityGate?.passed ? 'passed' : 'failed']">
          <span>质量门禁</span>
          <strong>{{ qualityGate?.passed ? 'PASS' : 'FAIL' }}</strong>
          <small>阈值 {{ formatRate(qualityGate?.minLineCoverageRate) }}</small>
        </div>
        <div>
          <span>行覆盖率</span>
          <strong>{{ formatRate(testGap?.lineCoverageRate) }}</strong>
          <small>{{ testGap?.totalLines || 0 }} 行</small>
        </div>
        <div>
          <span>未覆盖行</span>
          <strong>{{ testGap?.uncoveredLines || 0 }}</strong>
          <small>{{ testGap?.riskyUnits || 0 }} 个风险类</small>
        </div>
        <div class="risk-summary-card" :title="topRiskTitle">
          <span>最高风险</span>
          <strong>{{ topRiskName }}</strong>
          <small>{{ topRiskDetail }}</small>
        </div>
        <div class="tia-summary-card" :title="testImpactTitle">
          <span>TIA 选测</span>
          <strong>{{ testImpact?.impactedCaseCount || testImpact?.impactedTraceCount || 0 }}</strong>
          <small>{{ testImpactSummary }}</small>
        </div>
      </section>

      <section v-if="viewType === 'list'" class="panel">
        <div class="panel-head">
          <h2>覆盖单元列表</h2>
          <span>{{ coverageUnits?.totalElements ?? coverageCoreUnits.length }}</span>
        </div>
        <CoverageUnitListTable
          :units="coverageCoreUnits"
          :open-code-route="buildCoverageUnitCodeRoute"
        />
        <AppPagination
          v-if="(coverageUnits?.totalElements || 0) > 0"
          :page="currentPage + 1"
          :page-size="pageSize"
          :total="coverageUnits?.totalElements || 0"
          item-name="源码类"
          @update:page="goPage($event - 1)"
          @update:page-size="changePageSize"
        />
      </section>

      <section v-else class="panel">
        <div class="panel-head">
          <h2>树结构</h2>
          <span>{{ coverageUnitTreeCount }}</span>
        </div>
        <CoverageTreeTable
          :units="coverageUnits?.units || []"
          :modules="coverageModules?.modules || []"
          :language="coverageLanguage"
          :open-code-route="buildCoverageUnitCodeRoute"
        />
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import AppPagination from '@/components/AppPagination.vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { coverageMethodExportUrl, coverageReportExportUrl, fetchCoverageModules, fetchCoverageReportMetadata, fetchCoverageUnits, fetchQualityGate, fetchTestGap, fetchTestImpact } from '@/features/coverage/api/core'
import { CoverageTreeTable } from '@/shared/viz/CoverageTreeTable'
import { CoverageUnitListTable } from '@/shared/viz/CoverageUnitListTable'
import type {
  CoverageAppSummary,
  CoverageModulesPayload,
  CoverageReportMetadata,
  CoverageReportSummary,
  CoverageUnit,
  CoverageUnitsPayload,
  QualityGateResult,
  TestGapReport,
  TestImpactAnalysisReport,
} from '@/entities/coverage/model'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const reportId = computed(() => String(route.query.reportId || ''))
const viewType = computed(() => String(route.query.viewType || 'tree'))
const currentPage = computed(() => Number(route.query.page || 0))
const pageSize = computed(() => Number(route.query.size || 20))
const backRoute = computed(() => ({
  name: 'coverage-overview',
  params: { projectId: projectId.value, appId: appId.value },
  query: {
    versionNumber: route.query.versionNumber,
    reportId: reportId.value,
    commitId: route.query.commitId,
  },
}))
const reportMetadata = ref<CoverageReportMetadata | null>(null)
const coverageUnits = ref<CoverageUnitsPayload | null>(null)
const coverageModules = ref<CoverageModulesPayload | null>(null)
const testGap = ref<TestGapReport | null>(null)
const qualityGate = ref<QualityGateResult | null>(null)
const testImpact = ref<TestImpactAnalysisReport | null>(null)
const loading = ref(false)
const error = ref('')
const filters = reactive({
  className: '',
  methodName: '',
  minRate: undefined as number | undefined,
  maxRate: undefined as number | undefined,
  minBranchRate: undefined as number | undefined,
  maxBranchRate: undefined as number | undefined,
  minMethodRate: undefined as number | undefined,
  maxMethodRate: undefined as number | undefined,
  minComplexity: undefined as number | undefined,
  maxComplexity: undefined as number | undefined,
})
const advancedFilterCount = computed(
  () =>
    [
      filters.minRate,
      filters.maxRate,
      filters.minBranchRate,
      filters.maxBranchRate,
      filters.minMethodRate,
      filters.maxMethodRate,
      filters.minComplexity,
      filters.maxComplexity,
    ].filter((value) => value !== undefined && value !== null).length,
)
const coverageCoreUnits = computed(() => coverageModules.value?.modules.flatMap((module) => module.units || []) || coverageUnits.value?.units || [])
const reportSummary = computed<CoverageReportSummary | undefined>(() => reportMetadata.value?.report)
const reportApp = computed<CoverageAppSummary | undefined>(() => reportMetadata.value?.app)
const coverageLanguage = computed(() => coverageUnits.value?.language || reportSummary.value?.language || reportSummary.value?.sourceType || reportApp.value?.language || 'JAVA')
const hasCoverageContent = computed(() => Boolean(reportMetadata.value || coverageCoreUnits.value.length))
const coverageUnitCount = computed(() => coverageCoreUnits.value.length || '-')
const coverageUnitFunctionCount = computed(() =>
  coverageCoreUnits.value.reduce((sum, unit) => sum + (unit.functions?.length || 0), 0) || '-',
)
const coverageSourceUnitCount = computed(() => {
  const count = coverageCoreUnits.value.filter((unit) => Boolean(unit.sourcePath || unit.displayName || unit.unitKey)).length
  return count || '-'
})
const coverageUnitTreeCount = computed(() => {
  const units = coverageCoreUnits.value
  return units.length + units.reduce((sum, unit) => sum + (unit.functions?.length || 0), 0)
})
const topRiskUnit = computed(() => testGap.value?.units?.[0])
const topRiskName = computed(() => topRiskUnit.value?.displayName || topRiskUnit.value?.unitKey || topRiskUnit.value?.sourcePath || '-')
const topRiskDetail = computed(() => {
  const unit = topRiskUnit.value
  if (!unit) return '暂无风险类'
  const parts = [`${unit.uncoveredLines || 0} 行未覆盖`]
  const source = unit.sourcePath || unit.unitKey
  if (source && source !== topRiskName.value) parts.push(source)
  return parts.join(' · ')
})
const topRiskTitle = computed(() => `${topRiskName.value}\n${topRiskDetail.value}`)
const testImpactSummary = computed(() => {
  const report = testImpact.value
  if (!report) return '等待分析'
  if (report.impactedCaseCount) return `${report.impactedCaseCount} 个用例受影响`
  if (report.impactedTraceCount) return `${report.impactedTraceCount} 条链路受影响`
  if (report.reasons?.some((reason) => reason.includes('缺少') || reason.includes('无法推荐'))) return '暂无可推荐用例'
  if (report.reasons?.some((reason) => reason.includes('未提供'))) return '未提供变更行范围'
  return '暂无受影响用例'
})
const testImpactTitle = computed(() => {
  const report = testImpact.value
  if (!report) return testImpactSummary.value
  const changedLineText = report.changedLineCount === undefined ? '' : `变更行 ${report.changedLineCount}`
  return [testImpactSummary.value, changedLineText, ...(report.reasons || []).map(readableTestImpactReason)].filter(Boolean).join('\n')
})

function readableTestImpactReason(reason: string) {
  return reason
    .replace(/changedLines/g, '变更行范围')
    .replace(/footprint/g, '用例或链路关联数据')
}

function formatRate(value?: number) {
  return value === undefined || value === null ? '-' : `${value.toFixed(1)}%`
}

function buildCodeRoute(className: string) {
  return {
    name: 'coverage-code',
    params: { projectId: projectId.value, appId: appId.value },
    query: { reportId: reportId.value, className },
  }
}

function buildCoverageUnitCodeRoute(unit: CoverageUnit) {
  return buildCodeRoute(unit.unitKey || unit.displayName || unit.sourcePath || '')
}

function coverageUnitQuery(includePaging = true) {
  return {
    className: filters.className || undefined,
    methodName: filters.methodName || undefined,
    minRate: filters.minRate,
    maxRate: filters.maxRate,
    minBranchRate: filters.minBranchRate,
    maxBranchRate: filters.maxBranchRate,
    minMethodRate: filters.minMethodRate,
    maxMethodRate: filters.maxMethodRate,
    minComplexity: filters.minComplexity,
    maxComplexity: filters.maxComplexity,
    page: includePaging ? currentPage.value : undefined,
    size: includePaging ? pageSize.value : undefined,
  }
}

function numberQuery(value: unknown) {
  if (value === undefined || value === null || value === '') return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

function syncFiltersFromRoute() {
  filters.className = String(route.query.className || '')
  filters.methodName = String(route.query.methodName || '')
  filters.minRate = numberQuery(route.query.minRate)
  filters.maxRate = numberQuery(route.query.maxRate)
  filters.minBranchRate = numberQuery(route.query.minBranchRate)
  filters.maxBranchRate = numberQuery(route.query.maxBranchRate)
  filters.minMethodRate = numberQuery(route.query.minMethodRate)
  filters.maxMethodRate = numberQuery(route.query.maxMethodRate)
  filters.minComplexity = numberQuery(route.query.minComplexity)
  filters.maxComplexity = numberQuery(route.query.maxComplexity)
}

function buildFilterQuery(page = currentPage.value) {
  return {
    ...route.query,
    viewType: viewType.value,
    page: page ? String(page) : undefined,
    size: pageSize.value !== 20 ? String(pageSize.value) : undefined,
    className: filters.className || undefined,
    methodName: filters.methodName || undefined,
    minRate: filters.minRate,
    maxRate: filters.maxRate,
    minBranchRate: filters.minBranchRate,
    maxBranchRate: filters.maxBranchRate,
    minMethodRate: filters.minMethodRate,
    maxMethodRate: filters.maxMethodRate,
    minComplexity: filters.minComplexity,
    maxComplexity: filters.maxComplexity,
  }
}

async function applyFilters() {
  await router.replace({ query: buildFilterQuery(0) })
}

async function clearFilters() {
  Object.assign(filters, {
    className: '',
    methodName: '',
    minRate: undefined,
    maxRate: undefined,
    minBranchRate: undefined,
    maxBranchRate: undefined,
    minMethodRate: undefined,
    maxMethodRate: undefined,
    minComplexity: undefined,
    maxComplexity: undefined,
  })
  await router.replace({ query: { reportId: reportId.value, versionNumber: route.query.versionNumber, commitId: route.query.commitId, viewType: viewType.value } })
}

async function goPage(page: number) {
  await router.replace({ query: buildFilterQuery(Math.max(0, page)) })
}

async function changePageSize(size: number) {
  await router.replace({
    query: {
      ...buildFilterQuery(0),
      page: undefined,
      size: size !== 20 ? String(size) : undefined,
    },
  })
}

async function switchView(target: string) {
  await router.replace({ query: { ...buildFilterQuery(0), viewType: target } })
}

async function load() {
  if (!reportId.value) {
    error.value = '缺少 reportId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [metadata, units, modules, gap, gate, impact] = await Promise.all([
      fetchCoverageReportMetadata(projectId.value, reportId.value),
      fetchCoverageUnits(projectId.value, reportId.value, coverageUnitQuery(true)),
      fetchCoverageModules(projectId.value, reportId.value, coverageUnitQuery(false)),
      fetchTestGap(projectId.value, reportId.value),
      fetchQualityGate(projectId.value, reportId.value),
      fetchTestImpact(projectId.value, reportId.value, String(route.query.changedLines || '')),
    ])
    reportMetadata.value = metadata
    coverageUnits.value = units
    coverageModules.value = modules
    testGap.value = gap
    qualityGate.value = gate
    testImpact.value = impact
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载覆盖率明细失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => route.fullPath,
  () => {
    syncFiltersFromRoute()
    load()
  },
)

onMounted(() => {
  syncFiltersFromRoute()
  load()
})
</script>

<style scoped>
.page-header,
.header-actions,
.panel-head {
  display: flex;
  gap: 12px;
}

.page-header,
.panel-head {
  justify-content: space-between;
  align-items: center;
}

.page-header {
  margin-bottom: 12px;
}


.subtext,
.subtext {
  color: #64748b;
}

.secondary-link,
.table-link {
  color: #0f766e;
  font-weight: 700;
}

.primary-button,
.ghost-button,
.tab-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(15, 23, 42, 0.08);
  cursor: pointer;
}

.tab-button.active,
.primary-button {
  background: #0f172a;
  color: #fff;
}

.ghost-button {
  background: rgba(15, 23, 42, 0.06);
}

.filter-card {
  position: relative;
  z-index: 4;
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.filter-row,
.quick-filters,
.filter-actions,
.view-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-row {
  justify-content: space-between;
  flex-wrap: wrap;
}

.quick-filters {
  flex: 1;
  min-width: 0;
}

.search-field {
  position: relative;
  flex: 1;
  min-width: 180px;
}

.search-field span {
  position: absolute;
  top: 50%;
  left: 12px;
  z-index: 1;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  transform: translateY(-50%);
}

.search-field .text-input {
  padding-left: 58px;
}

.filter-actions {
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.view-toggle {
  padding: 3px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
}

.advanced-filters {
  margin-top: 8px;
}

.advanced-filters summary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #0f766e;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  list-style: none;
}

.advanced-filters summary::-webkit-details-marker {
  display: none;
}

.advanced-filters summary::after {
  content: '⌄';
  color: #94a3b8;
  font-size: 12px;
}

.advanced-filters[open] summary::after {
  transform: rotate(180deg);
}

.advanced-filters em {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 12px;
  font-style: normal;
}

.metric-filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 8px;
  margin-top: 8px;
}

.metric-filter-group {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  min-width: 0;
  margin: 0;
  padding: 8px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.7);
}

.metric-filter-group legend {
  padding: 0 4px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.metric-filter-group label {
  display: grid;
  gap: 4px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.text-input {
  width: 100%;
  min-height: 36px;
  border-radius: 10px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 7px 10px;
}

.filter-card :is(.primary-button, .ghost-button, .tab-button) {
  min-height: 36px;
  padding: 8px 12px;
}

.filter-card .tab-button {
  min-height: 30px;
  padding: 5px 10px;
}

.status-card,
.panel,
.tree-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.panel {
  margin-top: 12px;
}

.core-summary,
.analytics-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.core-summary div,
.analytics-summary div {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.92);
}

.core-summary span,
.analytics-summary span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.core-summary strong,
.analytics-summary strong {
  color: #0f172a;
  font-size: 18px;
  overflow-wrap: anywhere;
  white-space: normal;
}

.analytics-summary small {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.45;
  overflow-wrap: anywhere;
  white-space: normal;
}

.risk-summary-card,
.tia-summary-card {
  align-content: start;
}

.analytics-summary .gate-card.passed {
  border-color: rgba(15, 118, 110, 0.24);
  background: rgba(15, 118, 110, 0.08);
}

.analytics-summary .gate-card.failed {
  border-color: rgba(185, 28, 28, 0.24);
  background: rgba(185, 28, 28, 0.07);
}

.analytics-summary .gate-card.passed strong {
  color: #0f766e;
}

.analytics-summary .gate-card.failed strong {
  color: #b91c1c;
}

@media (max-width: 980px) {
  .quick-filters,
  .filter-actions {
    width: 100%;
  }

  .metric-filter-grid {
    grid-template-columns: repeat(2, minmax(160px, 1fr));
  }

  .core-summary,
  .analytics-summary {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }
}

@media (max-width: 640px) {
  .search-field,
  .filter-actions,
  .view-toggle {
    width: 100%;
  }

  .metric-filter-grid {
    grid-template-columns: 1fr;
  }

  .core-summary,
  .analytics-summary {
    grid-template-columns: 1fr;
  }
}
</style>
