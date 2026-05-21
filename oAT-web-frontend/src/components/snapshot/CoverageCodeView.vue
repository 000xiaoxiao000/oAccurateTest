<template>
  <section>
    <div class="page-header">
      <div>
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
            <span class="helper-text">{{ methods.length }} 个方法</span>
          </div>
          <div v-if="!methods.length" class="empty-card">暂无方法覆盖数据</div>
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
                <tr v-for="(method, index) in methods" :key="`${method.methodName}-${index}`">
                  <td class="method-name">
                    <a class="table-link" :href="`#method-${index}`">{{ method.methodName }}</a>
                    <small>{{ method.methodDesc || '-' }}</small>
                  </td>
                  <td>{{ method.coveredLines }} / {{ method.totalLines }} ({{ formatRate(method.coveredLines, method.totalLines) }})</td>
                  <td>
                    {{ method.coveredBranchTargets }} / {{ method.totalBranchTargets }}
                    ({{ formatBranchRate(method.branchRate, method.totalBranchTargets) }})
                  </td>
                  <td>{{ method.complexity }}</td>
                  <td>
                    <span :class="['status-pill', coverageTone(method)]">
                      {{ coverageText(method) }}
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
    </template>
  </section>
</template>

<script setup lang="ts">
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
.card-title {
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

.status-card.error {
  color: #b91c1c;
}

.detail-grid {
  display: grid;
  gap: 18px;
}

.table-shell,
.source-container {
  overflow: auto;
}

.method-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 900px;
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
  gap: 6px;
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
  font-size: 13px;
  line-height: 18px;
}

:deep(.source-container pre > div) {
  min-width: max-content;
}
</style>
