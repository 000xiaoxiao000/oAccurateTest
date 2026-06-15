<template>
  <section>
    <div class="page-header plain-header coverage-page-header">
      <div>
        <div class="eyebrow">Coverage Details</div>
        <h1>{{ payload?.app.name || appId }}</h1>
        <p class="subtext">{{ payload?.report?.versionNumber || '-' }}</p>
      </div>
      <div class="header-actions">
        <RouterLink class="secondary-link" :to="backRoute">返回概览</RouterLink>
        <a v-if="reportId" class="secondary-link" :href="backendApiUrl(`/p/${projectId}/coverage/export?reportId=${reportId}`)">导出报告</a>
        <a v-if="reportId" class="secondary-link" :href="backendApiUrl(`/p/${projectId}/coverage/export-methods?reportId=${reportId}`)">导出方法</a>
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
    <template v-else-if="payload">
      <section v-if="viewType === 'list'" class="panel">
        <div class="panel-head">
          <h2>类覆盖列表</h2>
          <span>{{ payload.classPage?.totalElements || 0 }}</span>
        </div>
        <div class="table-shell">
          <table class="report-table class-table">
            <colgroup>
              <col class="col-name" />
              <col class="col-method-count" />
              <col class="col-method-rate" />
              <col class="col-branch-count" />
              <col class="col-branch-rate" />
              <col class="col-line-count" />
              <col class="col-line-rate" />
              <col class="col-complexity" />
              <col class="col-action" />
            </colgroup>
            <thead>
              <tr>
                <th>类名</th>
                <th>方法 (覆盖/总)</th>
                <th>方法覆盖率</th>
                <th>分支 (覆盖/总)</th>
                <th>分支覆盖率</th>
                <th>代码行 (覆盖/总)</th>
                <th>代码行覆盖率</th>
                <th>圈复杂度</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in payload.classPage?.content || []" :key="item.className" :class="['class-row', item.hasCodeChanges && 'row-changed']">
                <td :title="displayClassName(item.className, true)">
                  <span class="cell-name-row">
                    <span class="cell-name-text">
                      <span class="tree-fold muted">−</span>
                      <span class="tree-icon">📄</span>
                      <span class="class-name">{{ displayClassName(item.className) }}</span>
                    </span>
                    <span
                      v-if="item.hasCodeChanges"
                      class="change-badge"
                      title="该类在不同 Commit 间覆盖率数据有变化。当前报告汇总了所有 Commit 数据，源码着色使用最新 Commit，建议重点关注此类。"
                    >跨 Commit 差异</span>
                  </span>
                </td>
                <td>{{ item.coveredMethods }} / {{ item.totalMethods }}</td>
                <td class="coverage-rate" :class="rateTone(item.methodRate)">{{ percent(item.methodRate) }}</td>
                <td>{{ item.coveredBranchTargets }} / {{ item.totalBranchTargets }}</td>
                <td class="coverage-rate" :class="rateTone(item.branchRate, item.totalBranchTargets)">{{ item.totalBranchTargets > 0 ? percent(item.branchRate) : 'N/A' }}</td>
                <td>{{ item.coveredLines }} / {{ item.totalLines }}</td>
                <td class="coverage-rate" :class="rateTone(item.lineRate)">{{ percent(item.lineRate) }}</td>
                <td>{{ item.totalComplexity }}</td>
                <td>
                  <RouterLink class="table-link code-link" :to="buildCodeRoute(item.className)">代码</RouterLink>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <AppPagination
          v-if="payload.classPage && payload.classPage.totalElements > 0"
          :page="currentPage + 1"
          :page-size="pageSize"
          :total="payload.classPage.totalElements"
          item-name="类"
          @update:page="goPage($event - 1)"
          @update:page-size="changePageSize"
        />
      </section>

      <section v-else class="panel">
        <div class="panel-head">
          <h2>树结构</h2>
          <span>{{ visibleTreeRows.length }}</span>
        </div>
        <div class="table-shell">
          <table class="report-table tree-table">
            <colgroup>
              <col class="col-name" />
              <col class="col-method-count" />
              <col class="col-method-rate" />
              <col class="col-branch-count" />
              <col class="col-branch-rate" />
              <col class="col-line-count" />
              <col class="col-line-rate" />
              <col class="col-complexity" />
            </colgroup>
            <thead>
              <tr>
                <th>包/类</th>
                <th>方法 (覆盖/总)</th>
                <th>方法覆盖率</th>
                <th>分支 (覆盖/总)</th>
                <th>分支覆盖率</th>
                <th>代码行 (覆盖/总)</th>
                <th>代码行覆盖率</th>
                <th>圈复杂度</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in visibleTreeRows" :key="row.id" :class="[row.type === 'package' ? 'package-row' : 'class-row', row.hasCodeChanges && 'row-changed']">
                <td :title="row.fullName || row.name" :style="{ paddingLeft: `${16 + row.level * 22}px` }">
                  <button v-if="row.hasChildren" class="tree-fold" type="button" :disabled="row.loading" @click="toggleTreeRow(row)">
                    {{ row.loading ? '…' : row.expanded ? '▣' : '▢' }}
                  </button>
                  <span v-else class="tree-fold muted">−</span>
                  <span class="tree-icon">{{ row.type === 'package' ? '📁' : '📄' }}</span>
                  <span>{{ row.type === 'class' ? displayClassName(row.name) : row.name }}</span>
                  <span
                    v-if="row.type === 'class' && row.hasCodeChanges"
                    class="change-badge"
                    title="该类在不同 Commit 间覆盖率数据有变化。当前报告汇总了所有 Commit 数据，源码着色使用最新 Commit，建议重点关注此类。"
                  >跨 Commit 差异</span>
                  <RouterLink v-if="row.type === 'class'" class="code-link" :to="buildCodeRoute(row.fullName || row.name)">代码</RouterLink>
                </td>
                <td>{{ row.coveredMethods }} / {{ row.totalMethods }}</td>
                <td class="coverage-rate" :class="rateTone(row.methodRate)">{{ percent(row.methodRate) }}</td>
                <td>{{ row.coveredBranchTargets }} / {{ row.totalBranchTargets }}</td>
                <td class="coverage-rate" :class="rateTone(row.branchRate, row.totalBranchTargets)">{{ row.totalBranchTargets > 0 ? percent(row.branchRate) : 'N/A' }}</td>
                <td>{{ row.coveredLines }} / {{ row.totalLines }}</td>
                <td class="coverage-rate" :class="rateTone(row.lineRate)">{{ percent(row.lineRate) }}</td>
                <td>{{ row.totalComplexity }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="treeError" class="status-card error compact">{{ treeError }}</div>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import AppPagination from '@/components/AppPagination.vue'
import { backendApiUrl } from '@/api/http'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { fetchCoverageDetails, fetchCoverageTreeNodes } from '@/api/bootstrap'
import type { CoverageDetailsPayload, CoverageTreeNode } from '@/api/types'

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
const payload = ref<CoverageDetailsPayload | null>(null)
const loading = ref(false)
const error = ref('')
const treeError = ref('')
type TreeRow = CoverageTreeNode & { level: number; expanded: boolean; loaded: boolean; loading: boolean }
const treeRows = ref<TreeRow[]>([])
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

function percent(value?: number) {
  return value === undefined || value === null ? '-' : `${value.toFixed(1)}%`
}

function rateTone(value?: number, total = 1) {
  if (total <= 0) return ''
  return (value || 0) > 0 ? 'positive' : 'negative'
}

function isPathLikeName(value?: string) {
  return Boolean(value && (value.includes('/') || value.includes('\\') || /\.[a-z0-9]+$/i.test(value)))
}

function displayClassName(value?: string, keepFullPath = false) {
  if (!value) return ''
  if (isPathLikeName(value)) {
    const normalized = value.replace(/\\/g, '/')
    if (keepFullPath) return normalized
    const sourceMarkers = ['/src/', '/app/', '/pages/', '/components/', '/lib/']
    for (const marker of sourceMarkers) {
      const markerIndex = normalized.indexOf(marker)
      if (markerIndex <= 0) continue
      const rootPath = normalized.slice(0, markerIndex)
      const rootName = rootPath.slice(rootPath.lastIndexOf('/') + 1)
      if (rootName) return `${rootName}${normalized.slice(markerIndex)}`
    }
    return normalized.replace(/^\/+/, '')
  }
  return `${value}.java`
}

function toTreeRow(node: CoverageTreeNode, level: number): TreeRow {
  return { ...node, level, expanded: false, loaded: !node.hasChildren, loading: false }
}

const visibleTreeRows = computed(() => treeRows.value.filter((row) => isTreeRowVisible(row)))

function isTreeRowVisible(row: TreeRow): boolean {
  if (row.level === 0) return true
  let parentId = row.parentId
  while (parentId) {
    const parent = treeRows.value.find((item) => item.fullName === parentId)
    if (!parent || !parent.expanded) return false
    parentId = parent.parentId
  }
  return true
}

function buildTreeQuery(parentPackage?: string) {
  return {
    reportId: reportId.value,
    parentPackage,
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

async function toggleTreeRow(row: TreeRow) {
  treeError.value = ''
  if (row.loaded) {
    row.expanded = !row.expanded
    return
  }
  row.loading = true
  try {
    const children = await fetchCoverageTreeNodes(projectId.value, buildTreeQuery(row.fullName || row.name))
    const index = treeRows.value.findIndex((item) => item.id === row.id)
    if (index >= 0) {
      treeRows.value.splice(index + 1, 0, ...children.map((child) => toTreeRow(child, row.level + 1)))
    }
    row.loaded = true
    row.expanded = true
  } catch (err) {
    treeError.value = err instanceof Error ? err.message : '加载树节点失败'
  } finally {
    row.loading = false
  }
}

function buildCodeRoute(className: string) {
  return {
    name: 'coverage-code',
    params: { projectId: projectId.value, appId: appId.value },
    query: { reportId: reportId.value, className },
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
    payload.value = await fetchCoverageDetails(projectId.value, {
      reportId: reportId.value,
      viewType: viewType.value,
      page: currentPage.value,
      size: pageSize.value,
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
    })
    
    treeRows.value = viewType.value === 'tree' ? (payload.value.treeNodes || []).map((node) => toTreeRow(node, 0)) : []
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
.toolbar,
.panel-head,
.tree-top,
.meta-grid {
  display: flex;
  gap: 12px;
}

.page-header,
.panel-head,
.tree-top {
  justify-content: space-between;
  align-items: center;
}

.page-header {
  margin-bottom: 12px;
}


.subtext,
.meta-grid {
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

.table-shell {
  max-height: min(680px, calc(100vh - 240px));
  overflow: auto;
  overscroll-behavior: contain;
}

@media (max-width: 980px) {
  .quick-filters,
  .filter-actions {
    width: 100%;
  }

  .metric-filter-grid {
    grid-template-columns: repeat(2, minmax(160px, 1fr));
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
}

.report-table {
  width: 100%;
  min-width: 1200px;
  border-collapse: separate;
  border-spacing: 0;
}

.report-table th,
.report-table td {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
}

.report-table th {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  white-space: nowrap;
}

.report-table tbody tr {
  transition: background 0.16s ease, box-shadow 0.16s ease;
}

.report-table tbody tr:hover {
  background: rgba(15, 118, 110, 0.04);
  box-shadow: inset 3px 0 0 rgba(15, 118, 110, 0.62);
}

.class-table,
.tree-table {
  table-layout: fixed;
}

.class-table th:not(:first-child),
.class-table td:not(:first-child),
.tree-table th:not(:first-child),
.tree-table td:not(:first-child) {
  text-align: center;
}

.class-table .col-name {
  width: 32%;
}

.tree-table .col-name {
  width: 37.5%;
}

.class-table .col-method-count,
.class-table .col-branch-count,
.tree-table .col-method-count,
.tree-table .col-branch-count {
  width: 9.5%;
}

.class-table .col-line-count,
.tree-table .col-line-count {
  width: 10.5%;
}

.class-table .col-method-rate,
.class-table .col-branch-rate,
.tree-table .col-method-rate,
.tree-table .col-branch-rate {
  width: 8.5%;
}

.class-table .col-line-rate,
.tree-table .col-line-rate {
  width: 9%;
}

.class-table .col-complexity,
.tree-table .col-complexity {
  width: 7%;
}

.class-table .col-action {
  width: 5.5%;
}

.class-table td,
.tree-table td {
  height: 46px;
}

.class-table td:first-child,
.tree-table td:first-child {
  overflow: hidden;
  color: #172033;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.cell-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
  direction: rtl;
  text-align: left;
}

.cell-name-row .change-badge {
  flex-shrink: 0;
}

.class-name {
  vertical-align: middle;
  direction: ltr;
  unicode-bidi: embed;
}

.tree-table td {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-table .package-row {
  background: #f9fafb;
  font-weight: 800;
}

.report-table .coverage-rate {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.tree-fold {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  margin-right: 5px;
  border: none;
  background: transparent;
  color: #0f766e;
  font-weight: 900;
}

.tree-fold.muted {
  color: #94a3b8;
}

.tree-icon {
  margin-right: 6px;
}

.code-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
  margin-left: 10px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-weight: 800;
}

.class-table .code-link {
  margin-left: 0;
}

.report-table .positive {
  color: #047857;
}

.report-table .negative {
  color: #b91c1c;
}

.status-card.compact {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 10px;
}

.meta-grid {
  flex-wrap: wrap;
  margin: 10px 0;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.change-badge {
  display: inline-flex;
  align-items: center;
  margin-left: 8px;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(217, 119, 6, 0.12);
  color: #b45309;
  font-size: 11px;
  font-weight: 800;
  cursor: help;
  white-space: nowrap;
}

.row-changed {
  background: rgba(217, 119, 6, 0.04) !important;
  border-left: 3px solid rgba(217, 119, 6, 0.42) !important;
}

.row-changed:hover {
  background: rgba(217, 119, 6, 0.08) !important;
  box-shadow: inset 3px 0 0 rgba(217, 119, 6, 0.62) !important;
}
</style>
