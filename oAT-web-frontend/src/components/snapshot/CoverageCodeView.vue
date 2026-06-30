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
                        title="该方法在不同 Commit 间覆盖率数据有变化。当前报告汇总了所有 Commit 数据，源码着色使用最新 Commit，建议重点关注此方法。"
                      >跨 Commit 差异</span>
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
          <SourceCodeViewer
            v-if="hasStructuredSource || coloredSourceHtml"
            ref="sourceViewerRef"
            class="source-container"
            :payload="sourceViewerPayload"
            :html="hasStructuredSource ? undefined : coloredSourceHtml"
          />
          <div v-else class="empty-card">源码不可用</div>
        </section>
      </div>
      <button class="back-to-top" type="button" @click="scrollTop">回到顶部</button>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

import AppPagination from '@/components/AppPagination.vue'
import { useSourceMethodJump } from '@/features/coverage/composables/useSourceMethodJump'
import { SourceCodeViewer } from '@/shared/viz/SourceCodeViewer'
import type { CoverageMethodSummary, SourceCoveragePayload } from '@/entities/coverage/model'

const props = defineProps<{
  eyebrow: string
  title: string
  subtext: string
  reportRoute: RouteLocationRaw
  loading: boolean
  loadingText: string
  error?: string
  methods?: CoverageMethodSummary[]
  coloredSourceHtml?: string
  sourceCoverage?: SourceCoveragePayload
  language?: string
}>()

const methodKeyword = ref('')
const statusFilter = ref('')
const methodPage = ref(1)
const methodPageSize = ref(50)
const sourceViewerRef = ref<InstanceType<typeof SourceCodeViewer> | null>(null)
const sourceRef = computed(() => sourceViewerRef.value?.htmlRef || null)
const { jumpToMethod } = useSourceMethodJump(() => sourceRef.value)
const hasStructuredSource = computed(() => Boolean(props.sourceCoverage?.lines?.length))
const sourceViewerPayload = computed(() => ({
  language: props.sourceCoverage?.language || props.language,
  sourcePath: props.sourceCoverage?.sourcePath || props.title,
  lines: (props.sourceCoverage?.lines || []).map((line) => ({
    ...line,
    text: line.text || '',
  })),
  branches: props.sourceCoverage?.branches || [],
}))

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

function methodContextSummary(method: CoverageMethodSummary) {
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
  if (sourceViewerRef.value?.scrollToTop) {
    sourceViewerRef.value.scrollToTop()
    return
  }
  const source = sourceRef.value
  if (source) {
    source.scrollTo({ top: 0, left: 0, behavior: 'smooth' })
    source.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
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

function coverageTone(method: CoverageMethodSummary) {
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

function coverageStatus(method: CoverageMethodSummary) {
  const tone = coverageTone(method)
  if (tone === 'success') return 'full'
  if (tone === 'warning') return 'partial'
  return 'none'
}

function coverageText(method: CoverageMethodSummary) {
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

<style scoped src="@/components/snapshot/styles/coverage-code-view.css"></style>
