<template>
  <section class="coverage-code-view">
    <div class="page-header plain-header coverage-page-header">
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
      <div class="detail-grid code-workspace">
        <section class="panel method-panel">
          <div class="card-title">
            <h2>方法覆盖列表</h2>
            <span class="helper-text">{{ filteredMethods.length }} / {{ methods.length }} 个方法</span>
          </div>
          <div class="method-filters">
            <input v-model.trim="methodKeyword" class="text-input" type="search" placeholder="按方法名或签名筛选..." aria-label="筛选方法覆盖列表" />
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
                <col class="status-col" />
              </colgroup>
              <thead>
                <tr>
                  <th>方法与覆盖率</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in paginatedMethods"
                  :key="`${item.method.methodName}-${item.index}`"
                  data-oat-coverage-method-row="true"
                  :data-method-name="methodBaseName(item.method.methodName)"
                  :data-method-display-name="item.method.methodName"
                  :data-method-desc="item.method.methodDesc || ''"
                  :data-method-complexity="item.method.complexity"
                  :data-method-line-rate="formatRate(item.method.coveredLines, item.method.totalLines)"
                  :data-method-branch-rate="formatBranchRate(item.method.branchRate, item.method.totalBranchTargets)"
                  :data-method-summary="methodContextSummary(item.method)"
                  :class="item.method.hasCodeChanges && 'method-row-changed'"
                >
                  <td class="method-name">
                    <div class="method-mainline">
                      <button class="method-jump" type="button" :title="item.method.methodName" @click="jumpToMethod(item.method.methodName)">{{ item.method.methodName }}</button>
                      <span
                        v-if="item.method.hasCodeChanges"
                        class="method-change-badge"
                        title="该方法在本版本多个 Commit 间覆盖率结果不完全一致。当前方法覆盖率已汇总所有 Commit，源码展示使用最新 Commit。"
                      >覆盖有差异</span>
                      <span class="complexity-chip">复杂度 {{ item.method.complexity }}</span>
                    </div>
                    <div class="method-subline">
                      <small :title="item.method.methodDesc || '-'">{{ item.method.methodDesc || '-' }}</small>
                      <span class="metric-chip">行 {{ item.method.coveredLines }}/{{ item.method.totalLines }} {{ formatRate(item.method.coveredLines, item.method.totalLines) }}</span>
                      <span class="metric-chip">分支 {{ item.method.coveredBranchTargets }}/{{ item.method.totalBranchTargets }} {{ formatBranchRate(item.method.branchRate, item.method.totalBranchTargets) }}</span>
                    </div>
                  </td>
                  <td class="status-cell">
                    <span class="status-pill" :data-tone="coverageTone(item.method)">
                      {{ coverageText(item.method) }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <AppPagination
            v-if="filteredMethods.length > 0"
            v-model:page="methodPage"
            v-model:page-size="methodPageSize"
            :total="filteredMethods.length"
            item-name="个方法"
            :page-sizes="[20, 50, 100, 200]"
          />
        </section>

        <section class="panel source-panel">
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
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

import AppPagination from '@/components/AppPagination.vue'
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
const methodPage = ref(1)
const methodPageSize = ref(50)
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

const paginatedMethods = computed(() => {
  const start = (methodPage.value - 1) * methodPageSize.value
  return filteredMethods.value.slice(start, start + methodPageSize.value)
})

watch([methodKeyword, statusFilter, methodPageSize], () => {
  methodPage.value = 1
})

watch(filteredMethods, () => {
  const totalPages = Math.max(1, Math.ceil(filteredMethods.value.length / methodPageSize.value))
  if (methodPage.value > totalPages) methodPage.value = totalPages
})

function clearFilters() {
  methodKeyword.value = ''
  statusFilter.value = ''
}

function methodBaseName(methodName: string) {
  return methodName.split('(')[0]?.trim() || methodName.trim()
}

function methodContextSummary(method: MethodCoverageSummary) {
  return [
    `方法=${method.methodName}`,
    method.methodDesc ? `签名=${method.methodDesc}` : '',
    `复杂度=${method.complexity}`,
    `行覆盖=${method.coveredLines}/${method.totalLines} ${formatRate(method.coveredLines, method.totalLines)}`,
    `分支覆盖=${method.coveredBranchTargets}/${method.totalBranchTargets} ${formatBranchRate(method.branchRate, method.totalBranchTargets)}`,
    `状态=${coverageText(method)}`,
  ].filter(Boolean).join('；')
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
  const lookupName = methodName.split('(')[0]?.trim() || methodName.trim()
  if (!lookupName) return
  await nextTick()
  const source = sourceRef.value
  if (!source) return
  clearSourceJumpHighlight(source)
  const escaped = lookupName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const signaturePattern = new RegExp(`\\b${escaped}\\s*\\(`)
  const target = findSourceLineTarget(source, signaturePattern)
  if (!target) {
    source.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  target.dataset.sourceJumpHighlight = 'true'
  scrollSourceTargetIntoView(source, target)
}

function clearSourceJumpHighlight(source: HTMLElement) {
  source.querySelectorAll<HTMLElement>('[data-source-jump-highlight="true"]').forEach((node) => {
    delete node.dataset.sourceJumpHighlight
  })
}

function findSourceLineTarget(source: HTMLElement, signaturePattern: RegExp) {
  const lineElements = Array.from(source.querySelectorAll<HTMLElement>('tr, pre > div, .line, [class~="line"], li'))
  const targetLine = lineElements.find((element) => signaturePattern.test(element.textContent || ''))
  if (targetLine) return targetLine

  const text = source.innerText || source.textContent || ''
  const match = signaturePattern.exec(text)
  if (!match) return null
  const lineNumber = text.slice(0, match.index).split('\n').length
  return lineElements[lineNumber - 1] || findClosestTextElement(source, signaturePattern)
}

function findClosestTextElement(source: HTMLElement, signaturePattern: RegExp) {
  return Array.from(source.querySelectorAll<HTMLElement>('tr, li, div, span, code'))
    .filter((element) => signaturePattern.test(element.textContent || ''))
    .sort((a, b) => (a.textContent || '').length - (b.textContent || '').length)[0] || null
}

function scrollSourceTargetIntoView(source: HTMLElement, target: HTMLElement) {
  const sourceRect = source.getBoundingClientRect()
  const targetRect = target.getBoundingClientRect()
  const nextTop = source.scrollTop + targetRect.top - sourceRect.top - source.clientHeight / 2 + targetRect.height / 2
  source.scrollTo({ top: Math.max(0, nextTop), behavior: 'smooth' })
  source.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
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
  padding-bottom: 8px;
}

.page-header {
  min-width: 0;
  margin-bottom: 10px;
  padding: 2px 0 8px;
  border-bottom: 1px solid rgba(15, 23, 42, .06);
}

.header-title {
  min-width: 0;
}

.page-header h1 {
  margin: 1px 0 3px;
  max-width: 100%;
  font-size: 22px;
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
  width: auto;
  max-width: 100%;
  border: none;
  padding: 0;
  background: transparent;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}

.method-jump:hover {
  text-decoration: underline;
}

.status-card,
.panel,
.empty-card {
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 18px 45px rgba(15, 23, 42, .05);
}

.method-filters {
  margin: 8px 0;
  justify-content: flex-start;
  flex-wrap: wrap;
}

.text-input {
  min-width: 160px;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 12px;
  padding: 8px 10px;
}

.ghost-button {
  border: 1px solid rgba(15, 118, 110, .18);
  border-radius: 999px;
  padding: 8px 11px;
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
  gap: 12px;
}

.code-workspace {
  grid-template-columns: minmax(360px, .72fr) minmax(620px, 1.28fr);
  height: max(760px, var(--page-data-height, calc(100vh - 96px)));
  min-height: 0;
}

.panel {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.method-panel,
.source-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.method-panel :deep(.app-pagination) {
  position: static;
  flex: 0 0 auto;
  gap: 6px;
  margin-top: 6px;
  padding: 7px 10px;
  border-radius: 14px;
  box-shadow: none;
}

.method-panel :deep(.page-stepper button),
.method-panel :deep(.page-size-control select) {
  min-height: 32px;
}

.method-panel :deep(.page-stepper button) {
  padding: 5px 10px;
}

.method-panel :deep(.page-summary),
.method-panel :deep(.page-size-control),
.method-panel :deep(.page-index) {
  font-size: 12px;
}

.method-panel :deep(.page-summary strong) {
  font-size: 13px;
}

.table-shell,
.source-container {
  max-width: 100%;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
}

.table-shell {
  flex: 0 0 auto;
  padding: 0;
  overflow-x: hidden;
  height: 536px;
  min-height: 536px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 14px;
  background: #fff;
}

.source-container {
  --line-number-width: 3em;
  flex: 1 1 auto;
  min-height: 0;
  max-height: none;
  scrollbar-gutter: stable both-edges;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 12px;
  background: #fff;
}

.method-table {
  width: 100%;
  min-width: 0;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
}

.method-col {
  width: auto;
}

.status-col {
  width: 76px;
}

.method-table th,
.method-table td {
  padding: 6px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
  vertical-align: top;
}

.method-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: rgba(255, 255, 255, .96);
  color: #0f172a;
  font-size: 12px;
  line-height: 1.2;
}

.method-table tbody tr {
  height: 50px;
}

.method-table tbody tr:hover {
  background: rgba(15, 118, 110, .035);
}

.method-name {
  display: grid;
  gap: 4px;
  min-width: 0;
  overflow-wrap: anywhere;
}

.method-mainline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.method-mainline .method-jump {
  flex: 1 1 auto;
  min-width: 0;
}

.method-subline {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: #64748b;
}

.method-name small {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 12px;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.metric-chip,
.complexity-chip {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  border-radius: 999px;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 700;
}

.metric-chip {
  padding: 2px 6px;
  background: rgba(15, 23, 42, .04);
  color: #475569;
}

.complexity-chip {
  padding: 2px 7px;
  background: rgba(14, 165, 233, .10);
  color: #0369a1;
}

.status-cell {
  padding-right: 10px;
  white-space: nowrap;
  text-align: right;
  vertical-align: middle;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 58px;
  padding: 3px 8px;
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

.method-row-changed {
  background: rgba(217, 119, 6, 0.05);
  box-shadow: inset 3px 0 0 rgba(217, 119, 6, 0.42);
}

.method-row-changed:hover {
  background: rgba(217, 119, 6, 0.09) !important;
}

.method-change-badge {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(217, 119, 6, 0.12);
  color: #b45309;
  font-size: 11px;
  font-weight: 800;
  cursor: help;
  white-space: nowrap;
}

:deep(.source-container pre) {
  margin: 0;
  min-width: 100%;
  padding: 8px 0;
  font-size: 12px;
  line-height: 17px;
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
  right: 22px;
  bottom: 92px;
  z-index: 70;
  border: 1px solid rgba(15, 118, 110, .18);
  border-radius: 999px;
  padding: 7px 11px;
  background: rgba(255, 255, 255, .92);
  color: #0f766e;
  font-weight: 800;
  box-shadow: 0 12px 28px rgba(15, 23, 42, .12);
  cursor: pointer;
}

@media (max-width: 720px) {
  .code-workspace {
    height: auto;
    grid-template-columns: 1fr;
  }

  .source-container,
  .table-shell {
    max-height: min(72vh, 640px);
  }

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
