<template>
  <section class="coverage-code-view">
    <div class="page-header">
      <div class="header-title">
        <div class="eyebrow">{{ eyebrow }}</div>
        <h1>{{ title }}</h1>
        <p class="subtext">{{ subtext }}</p>
      </div>
      <div class="header-actions">
        <RouterLink class="secondary-link" :to="reportRoute">返回报告</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">{{ loadingText }}</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="methods">
      <div class="detail-grid">
        <section class="panel">
          <div class="card-title">
            <h2>方法覆盖列表</h2>
            <span class="helper-text">{{ filteredMethods.length }} / {{ methods.length }} 个方法</span>
          </div>
          <div class="method-filters">
            <input v-model.trim="methodKeyword" class="text-input" type="text" placeholder="按方法名或签名筛选..." />
            <select v-model="statusFilter" class="text-input">
              <option value="">全部状态</option>
              <option value="full">全覆盖</option>
              <option value="partial">部分覆盖</option>
              <option value="none">未覆盖</option>
            </select>
            <button class="ghost-button" type="button" @click="clearFilters">清空</button>
          </div>
          <div v-if="!filteredMethods.length" class="empty-card">暂无匹配的方法覆盖数据</div>
          <div v-else class="table-shell">
            <table class="method-table">
              <thead>
                <tr>
                  <th>方法</th>
                  <th>代码行覆盖率</th>
                  <th>分支覆盖率</th>
                  <th>复杂度</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in filteredMethods" :key="`${item.method.methodName}-${item.index}`">
                  <td class="method-name">
                    <a class="table-link" :href="`#method-${item.index}`">{{ item.method.methodName }}</a>
                    <small>{{ item.method.methodDesc || '-' }}</small>
                  </td>
                  <td>{{ item.method.coveredLines }} / {{ item.method.totalLines }} ({{ formatRate(item.method.coveredLines, item.method.totalLines) }})</td>
                  <td>
                    {{ item.method.coveredBranchTargets }} / {{ item.method.totalBranchTargets }}
                    ({{ formatBranchRate(item.method.branchRate, item.method.totalBranchTargets) }})
                  </td>
                  <td>{{ item.method.complexity }}</td>
                  <td>
                    <span :class="['status-pill', coverageTone(item.method)]">
                      {{ coverageText(item.method) }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="panel">
          <div class="card-title">
            <h2>源码视图</h2>
          </div>
          <div v-if="coloredSourceHtml" class="source-container" v-html="coloredSourceHtml"></div>
          <div v-else class="empty-card">源码不可用</div>
        </section>
      </div>
      <button class="back-to-top" type="button" @click="scrollTop">回到顶部</button>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

import type { MethodCoverageSummary } from '@/api/types'

const props = defineProps<{
  eyebrow: string
  title: string
  subtext: string
  reportRoute: RouteLocationRaw
  loading: boolean
  loadingText: string
  error?: string
  methods?: MethodCoverageSummary[]
  coloredSourceHtml?: string
}>()

const methodKeyword = ref('')
const statusFilter = ref('')

const filteredMethods = computed(() => {
  const needle = methodKeyword.value.toLowerCase()
  return (props.methods || [])
    .map((method, index) => ({ method, index }))
    .filter(({ method }) => {
      if (!needle) return true
      return [method.methodName, method.methodDesc].join(' ').toLowerCase().includes(needle)
    })
    .filter(({ method }) => !statusFilter.value || coverageStatus(method) === statusFilter.value)
})

function clearFilters() {
  methodKeyword.value = ''
  statusFilter.value = ''
}

function scrollTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function formatRate(covered = 0, total = 0) {
  if (!total) {
    return '0%'
  }
  return `${((covered / total) * 100).toFixed(1)}%`
}

function formatBranchRate(rate?: number, total = 0) {
  if (!total) {
    return 'N/A'
  }
  return `${(rate || 0).toFixed(1)}%`
}

function coverageTone(method: MethodCoverageSummary) {
  const lineRate = method.totalLines ? method.coveredLines / method.totalLines : 0
  const branchRate = method.totalBranchTargets ? method.coveredBranchTargets / method.totalBranchTargets : 0
  if (lineRate === 1 && (!method.totalBranchTargets || branchRate === 1)) {
    return 'success'
  }
  if (lineRate > 0 || branchRate > 0) {
    return 'warning'
  }
  return 'default'
}

function coverageStatus(method: MethodCoverageSummary) {
  const tone = coverageTone(method)
  if (tone === 'success') return 'full'
  if (tone === 'warning') return 'partial'
  return 'none'
}

function coverageText(method: MethodCoverageSummary) {
  const tone = coverageTone(method)
  if (tone === 'success') {
    return '全覆盖'
  }
  if (tone === 'warning') {
    return '部分覆盖'
  }
  return '未覆盖'
}
</script>

<style scoped>
.page-header,
.header-actions,
.card-title,
.method-filters {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.coverage-code-view {
  min-width: 0;
  overflow: hidden;
}

.page-header {
  min-width: 0;
  margin-bottom: 20px;
}

.header-title {
  min-width: 0;
}

.page-header h1 {
  margin: 3px 0 4px;
  max-width: 100%;
  font-size: 28px;
  line-height: 1.25;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtext,
.helper-text,
.method-name small {
  color: #64748b;
}

.secondary-link,
.table-link {
  color: #0f766e;
  font-weight: 700;
}

.status-card,
.panel,
.empty-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.method-filters {
  margin: 12px 0;
  justify-content: flex-start;
  flex-wrap: wrap;
}

.text-input {
  min-width: 180px;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 12px;
  padding: 9px 11px;
}

.ghost-button {
  border: 1px solid rgba(15, 118, 110, .18);
  border-radius: 999px;
  padding: 9px 12px;
  background: rgba(15, 118, 110, .06);
  color: #0f766e;
  cursor: pointer;
}

.status-card.error {
  color: #b91c1c;
}

.detail-grid {
  display: grid;
  min-width: 0;
  gap: 18px;
}

.panel {
  min-width: 0;
  overflow: hidden;
}

.table-shell,
.source-container {
  max-width: 100%;
  overflow: auto;
  overscroll-behavior: contain;
}

.source-container {
  max-height: calc(100vh - 220px);
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 14px;
  background: #fff;
}

.method-table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
}

.method-table th,
.method-table td {
  padding: 12px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
  vertical-align: top;
}

.method-name {
  display: grid;
  max-width: 420px;
  gap: 6px;
  overflow-wrap: anywhere;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill.success {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
}

.status-pill.warning {
  background: rgba(234, 88, 12, 0.12);
  color: #c2410c;
}

.status-pill.default {
  background: rgba(100, 116, 139, 0.12);
  color: #475569;
}

:deep(.source-container pre) {
  margin: 0;
  min-width: 100%;
  padding: 10px 0;
  font-size: 13px;
  line-height: 18px;
}

:deep(.source-container pre > div) {
  min-width: max-content;
}

:deep(.source-container table),
:deep(.source-container .source),
:deep(.source-container .code) {
  width: max-content;
  min-width: 100%;
}

:deep(.source-container td),
:deep(.source-container th),
:deep(.source-container code) {
  white-space: pre;
}

@media (max-width: 720px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .page-header h1 {
    font-size: 22px;
  }

  .method-table {
    min-width: 680px;
  }
}
</style>
