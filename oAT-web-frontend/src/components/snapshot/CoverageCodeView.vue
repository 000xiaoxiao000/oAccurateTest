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
              <colgroup>
                <col class="method-col" />
                <col class="rate-col" />
                <col class="rate-col" />
                <col class="complexity-col" />
                <col class="status-col" />
              </colgroup>
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
                    <button class="method-jump" type="button" @click="jumpToMethod(item.method.methodName)">{{ item.method.methodName }}</button>
                    <small>{{ item.method.methodDesc || '-' }}</small>
                  </td>
                  <td class="metric-cell">{{ item.method.coveredLines }} / {{ item.method.totalLines }} ({{ formatRate(item.method.coveredLines, item.method.totalLines) }})</td>
                  <td class="metric-cell">
                    {{ item.method.coveredBranchTargets }} / {{ item.method.totalBranchTargets }}
                    ({{ formatBranchRate(item.method.branchRate, item.method.totalBranchTargets) }})
                  </td>
                  <td class="metric-cell">{{ item.method.complexity }}</td>
                  <td class="status-cell">
                    <span class="status-pill" :data-tone="coverageTone(item.method)">
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
          <div v-if="coloredSourceHtml" ref="sourceRef" class="source-container" v-html="coloredSourceHtml"></div>
          <div v-else class="empty-card">源码不可用</div>
        </section>
      </div>
      <button class="back-to-top" type="button" @click="scrollTop">回到顶部</button>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
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
const sourceRef = ref<HTMLElement | null>(null)

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
  const source = sourceRef.value
  if (source) {
    source.scrollTo({ top: 0, left: 0, behavior: 'smooth' })
    source.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function jumpToMethod(methodName: string) {
  if (!methodName) return
  await nextTick()
  const source = sourceRef.value
  if (!source) return
  source.querySelectorAll<HTMLElement>('[data-source-jump-highlight="true"]').forEach((node) => {
    delete node.dataset.sourceJumpHighlight
  })
  const escaped = methodName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const signaturePattern = new RegExp(`\\b${escaped}\\s*\\(`)
  const candidates = Array.from(source.querySelectorAll<HTMLElement>('tr, li, div, span'))
    .filter((element) => {
      const text = element.textContent || ''
      return signaturePattern.test(text) || text.includes(`${methodName}(`)
    })
    .sort((a, b) => (a.textContent || '').length - (b.textContent || '').length)
  const target = candidates[0]
  if (!target) {
    source.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  target.dataset.sourceJumpHighlight = 'true'
  target.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' })
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
  padding-bottom: 28px;
}

.page-header {
  min-width: 0;
  margin-bottom: 18px;
  padding: 6px 0 10px;
  border-bottom: 1px solid rgba(15, 23, 42, .06);
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
.method-jump {
  color: #0f766e;
  font-weight: 700;
}

.method-jump {
  width: fit-content;
  border: none;
  padding: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.method-jump:hover {
  text-decoration: underline;
}

.status-card,
.panel,
.empty-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 18px 45px rgba(15, 23, 42, .05);
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

.table-shell {
  padding: 0 58px 8px 0;
}

.source-container {
  --line-number-width: 3em;
  min-height: 360px;
  max-height: min(72vh, 760px);
  scrollbar-gutter: stable both-edges;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 14px;
  background: #fff;
}

.method-table {
  width: 100%;
  min-width: 920px;
  table-layout: fixed;
  border-collapse: collapse;
}

.method-col {
  width: 46%;
}

.rate-col {
  width: 18%;
}

.complexity-col {
  width: 8%;
}

.status-col {
  width: 10%;
}

.method-table th,
.method-table td {
  padding: 12px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
  vertical-align: middle;
}

.method-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: rgba(255, 255, 255, .96);
  color: #0f172a;
  font-size: 14px;
}

.method-name {
  display: grid;
  gap: 6px;
  min-width: 0;
  overflow-wrap: anywhere;
}

.method-name small {
  display: block;
  max-width: 100%;
  font-size: 12px;
  line-height: 1.45;
}

.metric-cell {
  color: #1f2937;
  white-space: nowrap;
}

.status-cell {
  padding-right: 24px;
  white-space: nowrap;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 58px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill[data-tone="success"] {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
}

.status-pill[data-tone="warning"] {
  background: rgba(234, 88, 12, 0.12);
  color: #c2410c;
}

.status-pill[data-tone="default"] {
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

:deep(.source-container [class~="branch-line"]) {
  position: relative;
}

:deep(.source-container [class~="branch-line"] [class~="branch-flag"]) {
  position: absolute;
  left: calc(var(--line-number-width, 3em) + 6px);
  top: 50%;
  z-index: 3;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #1e88e5;
  box-shadow: 0 0 0 2px rgba(30, 136, 229, .18);
  transform: translateY(-50%);
  cursor: help;
  pointer-events: auto;
  transition: transform .12s ease, box-shadow .12s ease, opacity .12s ease;
}

:deep(.source-container [class~="branch-line"] [class~="branch-flag"]:hover) {
  transform: translateY(-50%) scale(1.2);
  box-shadow: 0 0 0 3px rgba(30, 136, 229, .26), 0 0 10px rgba(30, 136, 229, .28);
}

:deep(.source-container [class~="branch-line"][class~="branch-green"] [class~="branch-flag"]) {
  background: #2e7d32;
  box-shadow: 0 0 0 2px rgba(46, 125, 50, .18);
}

:deep(.source-container [class~="branch-line"][class~="branch-green"] [class~="branch-flag"]:hover) {
  box-shadow: 0 0 0 3px rgba(46, 125, 50, .26), 0 0 10px rgba(46, 125, 50, .26);
}

:deep(.source-container [class~="branch-line"][class~="branch-orange"] [class~="branch-flag"]) {
  background: #ef6c00;
  box-shadow: 0 0 0 2px rgba(239, 108, 0, .18);
}

:deep(.source-container [class~="branch-line"][class~="branch-orange"] [class~="branch-flag"]:hover) {
  box-shadow: 0 0 0 3px rgba(239, 108, 0, .26), 0 0 10px rgba(239, 108, 0, .26);
}

:deep(.source-container [class~="branch-line"][class~="branch-red"] [class~="branch-flag"]) {
  background: #c62828;
  box-shadow: 0 0 0 2px rgba(198, 40, 40, .18);
}

:deep(.source-container [class~="branch-line"][class~="branch-red"] [class~="branch-flag"]:hover) {
  box-shadow: 0 0 0 3px rgba(198, 40, 40, .26), 0 0 10px rgba(198, 40, 40, .26);
}

:deep(.source-container [class~="branch-tooltip"]) {
  position: absolute;
  left: calc(var(--line-number-width, 3em) + 24px);
  top: 50%;
  z-index: 20;
  min-width: 220px;
  max-width: 420px;
  padding: 8px 10px;
  border: 1px solid rgba(255, 255, 255, .12);
  border-radius: 6px;
  background: rgba(20, 24, 33, .96);
  color: #f7f7f2;
  box-shadow: 0 10px 24px rgba(0, 0, 0, .18);
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  opacity: 0;
  pointer-events: none;
  transform: translateY(-50%);
  transition: opacity .12s ease;
}

:deep(.source-container [class~="branch-line"] [class~="branch-flag"]:hover + [class~="branch-tooltip"]) {
  opacity: 1;
}

:deep(.source-container table),
:deep(.source-container [class~="source"]),
:deep(.source-container [class~="code"]) {
  width: max-content;
  min-width: 100%;
}

:deep(.source-container td),
:deep(.source-container th),
:deep(.source-container code) {
  white-space: pre;
}

:deep(.source-container [data-source-jump-highlight="true"]) {
  outline: 2px solid rgba(15, 118, 110, .42);
  outline-offset: -2px;
  background: rgba(15, 118, 110, .12) !important;
  transition: background .2s ease, outline-color .2s ease;
}

.back-to-top {
  position: fixed;
  right: 24px;
  bottom: 116px;
  z-index: 70;
  border: 1px solid rgba(15, 118, 110, .18);
  border-radius: 999px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, .92);
  color: #0f766e;
  font-weight: 800;
  box-shadow: 0 12px 28px rgba(15, 23, 42, .12);
  cursor: pointer;
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
    min-width: 860px;
  }
}
</style>
