<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Coverage Details</div>
        <h1>{{ payload?.app.name || appId }}</h1>
        <p class="subtext">{{ payload?.report?.versionNumber || '-' }}</p>
      </div>
      <div class="header-actions">
        <RouterLink class="secondary-link" :to="backRoute">返回概览</RouterLink>
        <a v-if="reportId" class="secondary-link" :href="backendApiUrl(`/api/projects/${projectId}/coverage/export?reportId=${reportId}`)">导出报告</a>
        <a v-if="reportId" class="secondary-link" :href="backendApiUrl(`/api/projects/${projectId}/coverage/export-methods?reportId=${reportId}`)">导出方法</a>
      </div>
    </div>

    <form class="filter-card" @submit.prevent="applyFilters">
      <div class="filter-grid">
        <label><span>类名搜索</span><input v-model.trim="filters.className" class="text-input" type="search" placeholder="支持模糊匹配" aria-label="搜索类名" /></label>
        <label><span>方法名搜索</span><input v-model.trim="filters.methodName" class="text-input" type="search" placeholder="类中包含该方法" aria-label="搜索方法名" /></label>
        <label><span>最小行覆盖率</span><input v-model.number="filters.minRate" class="text-input" type="number" min="0" max="100" step="0.01" /></label>
        <label><span>最大行覆盖率</span><input v-model.number="filters.maxRate" class="text-input" type="number" min="0" max="100" step="0.01" /></label>
        <label><span>最小分支覆盖</span><input v-model.number="filters.minBranchRate" class="text-input" type="number" min="0" max="100" step="0.01" /></label>
        <label><span>最大分支覆盖</span><input v-model.number="filters.maxBranchRate" class="text-input" type="number" min="0" max="100" step="0.01" /></label>
        <label><span>最小方法覆盖</span><input v-model.number="filters.minMethodRate" class="text-input" type="number" min="0" max="100" step="0.01" /></label>
        <label><span>最大方法覆盖</span><input v-model.number="filters.maxMethodRate" class="text-input" type="number" min="0" max="100" step="0.01" /></label>
        <label><span>最小圈复杂度</span><input v-model.number="filters.minComplexity" class="text-input" type="number" min="0" /></label>
        <label><span>最大圈复杂度</span><input v-model.number="filters.maxComplexity" class="text-input" type="number" min="0" /></label>
      </div>
      <div class="toolbar">
        <button class="primary-button" type="submit">搜索</button>
        <button class="ghost-button" type="button" @click="clearFilters">清空筛选</button>
        <button type="button" :class="['tab-button', viewType === 'list' && 'active']" @click="switchView('list')">列表</button>
        <button type="button" :class="['tab-button', viewType === 'tree' && 'active']" @click="switchView('tree')">树结构</button>
      </div>
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
          <table class="report-table">
            <thead>
              <tr>
                <th>类名</th>
                <th>方法覆盖率</th>
                <th>分支覆盖率</th>
                <th>行覆盖率</th>
                <th>复杂度</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in payload.classPage?.content || []" :key="item.className">
                <td>{{ item.className }}</td>
                <td>{{ percent(item.methodRate) }}</td>
                <td>{{ percent(item.branchRate) }}</td>
                <td>{{ percent(item.lineRate) }}</td>
                <td>{{ item.totalComplexity }}</td>
                <td>
                  <RouterLink class="table-link" :to="buildCodeRoute(item.className)">源码</RouterLink>
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
              <tr v-for="row in visibleTreeRows" :key="row.id" :class="[row.type === 'package' ? 'package-row' : 'class-row']">
                <td :title="row.fullName || row.name" :style="{ paddingLeft: `${16 + row.level * 22}px` }">
                  <button v-if="row.hasChildren" class="tree-fold" type="button" :disabled="row.loading" @click="toggleTreeRow(row)">
                    {{ row.loading ? '…' : row.expanded ? '▣' : '▢' }}
                  </button>
                  <span v-else class="tree-fold muted">−</span>
                  <span class="tree-icon">{{ row.type === 'package' ? '📁' : '📄' }}</span>
                  <span>{{ row.name }}</span>
                  <RouterLink v-if="row.type === 'class'" class="code-link" :to="buildCodeRoute(row.fullName || row.name)">代码</RouterLink>
                </td>
                <td>{{ row.coveredMethods }} / {{ row.totalMethods }}</td>
                <td :class="rateTone(row.methodRate)">{{ percent(row.methodRate) }}</td>
                <td>{{ row.coveredBranchTargets }} / {{ row.totalBranchTargets }}</td>
                <td :class="rateTone(row.branchRate, row.totalBranchTargets)">{{ row.totalBranchTargets > 0 ? percent(row.branchRate) : 'N/A' }}</td>
                <td>{{ row.coveredLines }} / {{ row.totalLines }}</td>
                <td :class="rateTone(row.lineRate)">{{ percent(row.lineRate) }}</td>
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

function percent(value?: number) {
  return value === undefined || value === null ? '-' : `${value.toFixed(1)}%`
}

function rateTone(value?: number, total = 1) {
  if (total <= 0) return ''
  return (value || 0) > 0 ? 'positive' : 'negative'
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
  position: sticky;
  top: 12px;
  z-index: 4;
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 12px;
}

.filter-grid label {
  display: grid;
  gap: 6px;
}

.text-input {
  width: 100%;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 9px 10px;
}

.status-card,
.panel,
.tree-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.toolbar,
.panel {
  margin-top: 16px;
}

.table-shell {
  max-height: min(620px, calc(100vh - 280px));
  overflow: auto;
}

.report-table {
  width: 100%;
  border-collapse: collapse;
}

.report-table th,
.report-table td {
  padding: 11px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
}

.tree-table {
  table-layout: fixed;
}

.tree-table th:first-child,
.tree-table td:first-child {
  width: 34%;
}

.tree-table th:not(:first-child),
.tree-table td:not(:first-child) {
  width: 9.4%;
  text-align: center;
}

.tree-table td {
  padding-top: 7px;
  padding-bottom: 7px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-table .package-row {
  background: #f9fafb;
  font-weight: 800;
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
  margin-left: 10px;
  color: #0f766e;
  font-weight: 800;
}

.positive {
  color: #047857;
}

.negative {
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
</style>

