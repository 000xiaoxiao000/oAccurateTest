<template>
  <div class="coverage-unit-list">
    <div class="table-shell">
      <table class="report-table coverage-unit-list-table">
        <thead>
          <tr>
            <th>源码类</th>
            <th>函数</th>
            <th>代码行</th>
            <th>行覆盖率</th>
            <th>分支</th>
            <th>分支覆盖率</th>
            <th>复杂度</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="unit in visibleUnits" :key="unitKey(unit)">
            <td :title="unit.sourcePath || unit.unitKey || unit.displayName">
              <strong>{{ unit.displayName || unit.sourcePath || unit.unitKey || '未命名单元' }}</strong>
              <small v-if="unit.sourcePath">{{ unit.sourcePath }}</small>
            </td>
            <td>{{ coveredFunctions(unit) }} / {{ unit.functions?.length || 0 }}</td>
            <td>{{ coveredLines(unit) }} / {{ totalLines(unit) }}</td>
            <td class="coverage-rate" :class="rateTone(lineRate(unit), totalLines(unit))">{{ formatRate(lineRate(unit), totalLines(unit)) }}</td>
            <td>{{ coveredBranches(unit) }} / {{ totalBranches(unit) }}</td>
            <td class="coverage-rate" :class="rateTone(branchRate(unit), totalBranches(unit))">{{ formatRate(branchRate(unit), totalBranches(unit)) }}</td>
            <td>{{ complexity(unit) || '-' }}</td>
            <td>
              <RouterLink v-if="openCodeRoute" class="table-link" :to="openCodeRoute(unit)">代码</RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!visibleUnits.length" class="empty-card">暂无统一模型源码类</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'
import type { CoverageUnit } from '@/entities/coverage/model'

const props = defineProps<{
  units: CoverageUnit[]
  keyword?: string
  openCodeRoute?: (unit: CoverageUnit) => RouteLocationRaw
}>()

const visibleUnits = computed(() => {
  const term = (props.keyword || '').trim().toLowerCase()
  if (!term) return props.units
  return props.units.filter((unit) => [
    unit.unitKey,
    unit.displayName,
    unit.sourcePath,
    ...(unit.functions || []).map((fn) => fn.signature),
  ].join(' ').toLowerCase().includes(term))
})

function unitKey(unit: CoverageUnit) {
  return unit.unitKey || unit.sourcePath || unit.displayName || JSON.stringify(unit)
}

function totalLines(unit: CoverageUnit) {
  return Math.max(unit.lines?.length || 0, sum(unit.functions?.map((fn) => fn.lines?.length || 0)))
}

function coveredLines(unit: CoverageUnit) {
  return Math.max(unit.lines?.filter((line) => (line.hits || 0) > 0).length || 0, sum(unit.functions?.map((fn) => fn.lines?.filter((line) => (line.hits || 0) > 0).length || 0)))
}

function totalBranches(unit: CoverageUnit) {
  return Math.max(unit.branches?.length || 0, sum(unit.functions?.map((fn) => fn.branches?.length || 0)))
}

function coveredBranches(unit: CoverageUnit) {
  return Math.max(unit.branches?.filter((branch) => (branch.hits || 0) > 0).length || 0, sum(unit.functions?.map((fn) => fn.branches?.filter((branch) => (branch.hits || 0) > 0).length || 0)))
}

function coveredFunctions(unit: CoverageUnit) {
  return (unit.functions || []).filter((fn) =>
    fn.lines?.some((line) => (line.hits || 0) > 0) || fn.branches?.some((branch) => (branch.hits || 0) > 0),
  ).length
}

function complexity(unit: CoverageUnit) {
  return sum(unit.functions?.map((fn) => fn.complexity || 0))
}

function lineRate(unit: CoverageUnit) {
  return rate(coveredLines(unit), totalLines(unit))
}

function branchRate(unit: CoverageUnit) {
  return rate(coveredBranches(unit), totalBranches(unit))
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

function sum(values?: number[]) {
  return (values || []).reduce((total, value) => total + value, 0)
}
</script>

<style scoped>
.coverage-unit-list {
  display: grid;
  gap: 10px;
}

.coverage-unit-list-table {
  min-width: 920px;
  table-layout: fixed;
}

.coverage-unit-list-table th:first-child,
.coverage-unit-list-table td:first-child {
  position: sticky;
  left: 0;
  z-index: 1;
  width: 360px;
  min-width: 360px;
  background: #fff;
  box-shadow: 8px 0 12px -12px rgba(15, 23, 42, 0.28);
}

.coverage-unit-list-table th:first-child {
  z-index: 3;
  background: rgba(248, 250, 252, 0.98);
}

.coverage-unit-list-table tbody tr:hover td:first-child {
  background: #f8fdfc;
}

.coverage-unit-list-table th:not(:first-child),
.coverage-unit-list-table td:not(:first-child) {
  text-align: right;
}

.coverage-unit-list-table th:last-child,
.coverage-unit-list-table td:last-child {
  text-align: center;
}

.coverage-unit-list-table td:first-child {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.coverage-unit-list-table td:first-child strong,
.coverage-unit-list-table td:first-child small {
  overflow-wrap: anywhere;
  white-space: normal;
}

.coverage-unit-list-table td:first-child small {
  color: #64748b;
}

.coverage-rate.positive {
  color: #0f766e;
  font-weight: 800;
}

.coverage-rate.negative {
  color: #b91c1c;
  font-weight: 800;
}
</style>
