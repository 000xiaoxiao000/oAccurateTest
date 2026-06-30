<template>
  <div class="coverage-tree-table">
    <div class="tree-toolbar">
      <label class="tree-search">
        <span>筛选</span>
        <input v-model.trim="keyword" class="text-input" type="search" placeholder="模块、文件或函数" aria-label="筛选统一覆盖率模型" />
      </label>
      <div class="tree-stats">
        <span>{{ normalizedLanguage }}</span>
        <strong>{{ visibleRows.length }} / {{ filteredRows.length }}</strong>
      </div>
    </div>

    <div v-if="!filteredRows.length" class="empty-card">暂无匹配的统一覆盖率模型数据</div>
    <div v-else class="table-shell">
      <table class="report-table coverage-unit-table">
        <colgroup>
          <col class="col-name" />
          <col class="col-kind" />
          <col class="col-count" />
          <col class="col-rate" />
          <col class="col-count" />
          <col class="col-rate" />
          <col class="col-count" />
          <col class="col-complexity" />
          <col class="col-action" />
        </colgroup>
        <thead>
          <tr>
            <th>模块/文件/函数</th>
            <th>类型</th>
            <th>代码行</th>
            <th>行覆盖率</th>
            <th>分支</th>
            <th>分支覆盖率</th>
            <th>函数</th>
            <th>复杂度</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visibleRows" :key="row.id" :class="[`row-${row.kind}`]">
            <td :title="row.title">
              <div class="tree-name" :style="{ paddingLeft: `${row.level * 20}px` }">
                <button
                  v-if="row.expandable"
                  class="tree-fold-button"
                  type="button"
                  :aria-label="isExpanded(row.id) ? '收起' : '展开'"
                  @click="toggleRow(row.id)"
                >
                  {{ isExpanded(row.id) ? '−' : '+' }}
                </button>
                <span v-else class="tree-fold-spacer"></span>
                <span class="kind-mark">{{ kindMark(row.kind) }}</span>
                <span class="name-text">{{ row.name }}</span>
                <span v-if="row.subtitle" class="name-subtitle">{{ row.subtitle }}</span>
              </div>
            </td>
            <td><span class="kind-badge">{{ kindLabel(row.kind) }}</span></td>
            <td>{{ row.coveredLines }} / {{ row.totalLines }}</td>
            <td class="coverage-rate" :class="rateTone(row.lineRate, row.totalLines)">{{ formatRate(row.lineRate, row.totalLines) }}</td>
            <td>{{ row.coveredBranches }} / {{ row.totalBranches }}</td>
            <td class="coverage-rate" :class="rateTone(row.branchRate, row.totalBranches)">{{ formatRate(row.branchRate, row.totalBranches) }}</td>
            <td>{{ row.coveredFunctions }} / {{ row.totalFunctions }}</td>
            <td>{{ row.complexity || '-' }}</td>
            <td>
              <RouterLink v-if="row.unit && openCodeRoute" class="table-link" :to="openCodeRoute(row.unit)">代码</RouterLink>
              <span v-else class="muted-action">-</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { CoverageFunction, CoverageLine, CoverageUnit } from '@/entities/coverage/model'
import type { CoverageTreeTableProps } from './types'

type RowKind = 'module' | 'unit' | 'function'

interface CoverageTreeRow {
  id: string
  parentId?: string
  kind: RowKind
  level: number
  name: string
  title: string
  subtitle?: string
  unit?: CoverageUnit
  expandable: boolean
  totalLines: number
  coveredLines: number
  lineRate: number
  totalBranches: number
  coveredBranches: number
  branchRate: number
  totalFunctions: number
  coveredFunctions: number
  complexity: number
  searchText: string
}

const props = defineProps<CoverageTreeTableProps>()
const keyword = ref('')
const expandedRowIds = ref<Set<string>>(new Set())

const normalizedLanguage = computed(() =>
  props.language
  || props.modules?.find((module) => module.language)?.language
  || props.units.find((unit) => unit.language)?.language
  || 'UNKNOWN',
)

const rows = computed(() => buildRows(props.units || []))
const filteredRows = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  if (!term) return rows.value
  return rows.value.filter((row) => row.searchText.includes(term))
})
const visibleRows = computed(() => {
  const term = keyword.value.trim()
  if (term) return filteredRows.value
  return filteredRows.value.filter((row) => isRowVisible(row))
})

watch(
  rows,
  (nextRows) => {
    const next = new Set(expandedRowIds.value)
    nextRows.filter((row) => row.kind === 'module').forEach((row) => next.add(row.id))
    expandedRowIds.value = next
  },
  { immediate: true },
)

function buildRows(units: CoverageUnit[]): CoverageTreeRow[] {
  const moduleEntries = props.modules?.length
    ? props.modules.map((module) => ({ name: module.moduleKey || 'default', units: module.units || [] }))
    : groupUnits(units)

  const result: CoverageTreeRow[] = []
  moduleEntries
    .sort((left, right) => left.name.localeCompare(right.name))
    .forEach((moduleEntry) => {
      const moduleMetrics = mergeMetrics(moduleEntry.units.map(unitMetrics))
      const moduleId = `module:${moduleEntry.name}`
      result.push(toRow(moduleId, undefined, 'module', 0, moduleEntry.name, moduleEntry.name, moduleMetrics, undefined, undefined, true))

      moduleEntry.units
        .slice()
        .sort((left, right) => displayNameOf(left).localeCompare(displayNameOf(right)))
        .forEach((unit) => {
          const unitName = displayNameOf(unit)
          const unitMetricsValue = unitMetrics(unit)
          const unitId = `unit:${unit.unitKey || unit.sourcePath || unitName}`
          result.push(
            toRow(
              unitId,
              moduleId,
              'unit',
              1,
              unitName,
              unit.sourcePath || unit.unitKey || unitName,
              unitMetricsValue,
              unit,
              undefined,
              Boolean(unit.functions?.length),
            ),
          )

          ;(unit.functions || [])
            .slice()
            .sort((left, right) => functionNameOf(left).localeCompare(functionNameOf(right)))
            .forEach((fn, index) => {
              const name = functionNameOf(fn)
              const metrics = functionMetrics(fn)
              result.push(
                toRow(
                  `function:${unit.unitKey || unit.sourcePath || unitName}:${fn.signature || index}`,
                  unitId,
                  'function',
                  2,
                  name,
                  `${unitName} ${name}`,
                  metrics,
                  unit,
                  lineRangeOf(fn),
                  false,
                ),
              )
            })
        })
    })
  return result
}

function groupUnits(units: CoverageUnit[]) {
  const moduleMap = new Map<string, { name: string; units: CoverageUnit[] }>()
  units.forEach((unit) => {
    const moduleName = moduleNameOf(unit)
    const entry = moduleMap.get(moduleName) || { name: moduleName, units: [] }
    entry.units.push(unit)
    moduleMap.set(moduleName, entry)
  })
  return Array.from(moduleMap.values())
}

function toRow(
  id: string,
  parentId: string | undefined,
  kind: RowKind,
  level: number,
  name: string,
  title: string,
  metrics: Metrics,
  unit?: CoverageUnit,
  subtitle?: string,
  expandable = false,
): CoverageTreeRow {
  return {
    id,
    parentId,
    kind,
    level,
    name,
    title,
    subtitle,
    unit,
    expandable,
    totalLines: metrics.totalLines,
    coveredLines: metrics.coveredLines,
    lineRate: rate(metrics.coveredLines, metrics.totalLines),
    totalBranches: metrics.totalBranches,
    coveredBranches: metrics.coveredBranches,
    branchRate: rate(metrics.coveredBranches, metrics.totalBranches),
    totalFunctions: metrics.totalFunctions,
    coveredFunctions: metrics.coveredFunctions,
    complexity: metrics.complexity,
    searchText: [name, title, subtitle, unit?.sourcePath, unit?.unitKey, unit?.displayName].filter(Boolean).join(' ').toLowerCase(),
  }
}

function isRowVisible(row: CoverageTreeRow): boolean {
  let parentId = row.parentId
  while (parentId) {
    if (!expandedRowIds.value.has(parentId)) return false
    parentId = rows.value.find((item) => item.id === parentId)?.parentId
  }
  return true
}

function isExpanded(rowId: string) {
  return expandedRowIds.value.has(rowId)
}

function toggleRow(rowId: string) {
  const next = new Set(expandedRowIds.value)
  if (next.has(rowId)) next.delete(rowId)
  else next.add(rowId)
  expandedRowIds.value = next
}

interface Metrics {
  totalLines: number
  coveredLines: number
  totalBranches: number
  coveredBranches: number
  totalFunctions: number
  coveredFunctions: number
  complexity: number
}

function emptyMetrics(): Metrics {
  return {
    totalLines: 0,
    coveredLines: 0,
    totalBranches: 0,
    coveredBranches: 0,
    totalFunctions: 0,
    coveredFunctions: 0,
    complexity: 0,
  }
}

function mergeMetrics(items: Metrics[]): Metrics {
  return items.reduce((sum, item) => ({
    totalLines: sum.totalLines + item.totalLines,
    coveredLines: sum.coveredLines + item.coveredLines,
    totalBranches: sum.totalBranches + item.totalBranches,
    coveredBranches: sum.coveredBranches + item.coveredBranches,
    totalFunctions: sum.totalFunctions + item.totalFunctions,
    coveredFunctions: sum.coveredFunctions + item.coveredFunctions,
    complexity: sum.complexity + item.complexity,
  }), emptyMetrics())
}

function unitMetrics(unit: CoverageUnit): Metrics {
  const functionMetricsList = (unit.functions || []).map(functionMetrics)
  const ownLines = lineMetrics(unit.lines)
  const ownBranches = branchMetrics(unit.branches)
  const functionTotals = mergeMetrics(functionMetricsList)
  return {
    totalLines: Math.max(ownLines.total, functionTotals.totalLines),
    coveredLines: Math.max(ownLines.covered, functionTotals.coveredLines),
    totalBranches: Math.max(ownBranches.total, functionTotals.totalBranches),
    coveredBranches: Math.max(ownBranches.covered, functionTotals.coveredBranches),
    totalFunctions: unit.functions?.length || 0,
    coveredFunctions: (unit.functions || []).filter(isFunctionCovered).length,
    complexity: functionTotals.complexity,
  }
}

function functionMetrics(fn: CoverageFunction): Metrics {
  const lines = lineMetrics(fn.lines)
  const branches = branchMetrics(fn.branches)
  return {
    totalLines: lines.total,
    coveredLines: lines.covered,
    totalBranches: branches.total,
    coveredBranches: branches.covered,
    totalFunctions: 1,
    coveredFunctions: isFunctionCovered(fn) ? 1 : 0,
    complexity: fn.complexity || 0,
  }
}

function lineMetrics(lines?: CoverageLine[]) {
  return {
    total: lines?.length || 0,
    covered: lines?.filter((line) => (line.hits || 0) > 0).length || 0,
  }
}

function branchMetrics(branches?: { hits?: number }[]) {
  return {
    total: branches?.length || 0,
    covered: branches?.filter((branch) => (branch.hits || 0) > 0).length || 0,
  }
}

function isFunctionCovered(fn: CoverageFunction) {
  return Boolean(fn.lines?.some((line) => (line.hits || 0) > 0) || fn.branches?.some((branch) => (branch.hits || 0) > 0))
}

function displayNameOf(unit: CoverageUnit) {
  return unit.displayName || unit.sourcePath || unit.unitKey || '未命名单元'
}

function moduleNameOf(unit: CoverageUnit) {
  const value = unit.sourcePath || unit.displayName || unit.unitKey || 'default'
  const normalized = value.replace(/\\/g, '/')
  const parts = normalized.split('/').filter(Boolean)
  if (parts.length > 1) return parts.slice(0, -1).join('/')
  if (value.includes('.')) return value.split('.').slice(0, -1).join('.') || 'default'
  return 'default'
}

function functionNameOf(fn: CoverageFunction) {
  return fn.signature || (fn.startLine ? `line ${fn.startLine}` : 'anonymous')
}

function lineRangeOf(fn: CoverageFunction) {
  if (!fn.startLine && !fn.endLine) return undefined
  if (fn.startLine === fn.endLine || !fn.endLine) return `L${fn.startLine}`
  return `L${fn.startLine}-L${fn.endLine}`
}

function rate(covered: number, total: number) {
  return total > 0 ? (covered / total) * 100 : 0
}

function formatRate(value: number, total: number) {
  return total > 0 ? `${value.toFixed(1)}%` : 'N/A'
}

function rateTone(value: number, total: number) {
  if (total <= 0) return ''
  return value > 0 ? 'positive' : 'negative'
}

function kindLabel(kind: RowKind) {
  if (kind === 'module') return '模块'
  if (kind === 'unit') return '文件'
  return '函数'
}

function kindMark(kind: RowKind) {
  if (kind === 'module') return 'M'
  if (kind === 'unit') return 'U'
  return 'F'
}
</script>

<style scoped>
.coverage-tree-table {
  display: grid;
  gap: 12px;
}

.tree-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.tree-search {
  position: relative;
  flex: 1;
  min-width: 220px;
}

.tree-search span {
  position: absolute;
  left: 12px;
  top: 50%;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  transform: translateY(-50%);
}

.tree-search .text-input {
  width: 100%;
  padding-left: 52px;
}

.tree-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.tree-stats strong {
  color: #0f172a;
}

.coverage-unit-table {
  min-width: 1080px;
  table-layout: fixed;
}

.col-name {
  width: 34%;
}

.col-kind,
.col-count,
.col-rate,
.col-complexity,
.col-action {
  width: 8%;
}

.coverage-unit-table th:not(:first-child),
.coverage-unit-table td:not(:first-child) {
  text-align: right;
}

.coverage-unit-table th:last-child,
.coverage-unit-table td:last-child {
  text-align: center;
}

.tree-name {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
}

.tree-fold-button,
.tree-fold-spacer {
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
}

.tree-fold-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 6px;
  background: #fff;
  color: #0f172a;
  font-weight: 800;
  cursor: pointer;
}

.tree-fold-button:hover {
  background: rgba(15, 118, 110, 0.08);
}

.kind-mark {
  display: inline-flex;
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
}

.row-function .kind-mark {
  background: rgba(37, 99, 235, 0.1);
  color: #2563eb;
}

.row-module .kind-mark {
  background: rgba(15, 23, 42, 0.1);
  color: #0f172a;
}

.name-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
}

.name-subtitle {
  flex: 0 0 auto;
  color: #94a3b8;
  font-size: 12px;
}

.kind-badge {
  display: inline-flex;
  justify-content: center;
  min-width: 44px;
  border-radius: 999px;
  padding: 4px 8px;
  background: rgba(15, 23, 42, 0.06);
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.coverage-rate.positive {
  color: #0f766e;
  font-weight: 800;
}

.coverage-rate.negative {
  color: #b91c1c;
  font-weight: 800;
}

.muted-action {
  color: #94a3b8;
}

@media (max-width: 720px) {
  .tree-toolbar {
    align-items: stretch;
  }

  .tree-search,
  .tree-stats {
    width: 100%;
  }
}
</style>
