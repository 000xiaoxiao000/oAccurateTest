<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">{{ eyebrow }}</div>
        <h1>{{ title }}</h1>
        <p v-if="subtext" class="subtext">{{ subtext }}</p>
      </div>
      <div class="header-actions">
        <RouterLink class="ghost-button" :to="graphRoute">查看链路图</RouterLink>
        <button v-if="secondaryActionLabel" class="ghost-button" type="button" @click="$emit('secondary-action')">
          {{ secondaryActionLabel }}
        </button>
        <RouterLink class="secondary-link" :to="backRoute">{{ backLabel }}</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">{{ loadingText }}</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="report">
      <div class="hero-grid">
        <article class="hero-card">
          <span>类覆盖率</span>
          <strong>{{ formatRate(report.coveredClasses, report.totalClasses) }}</strong>
          <small>{{ report.coveredClasses }} / {{ report.totalClasses }}</small>
        </article>
        <article class="hero-card">
          <span>方法覆盖率</span>
          <strong>{{ formatRate(report.coveredMethods, report.totalMethods) }}</strong>
          <small>{{ report.coveredMethods }} / {{ report.totalMethods }}</small>
        </article>
        <article class="hero-card">
          <span>行覆盖率</span>
          <strong>{{ formatRate(report.coveredLines, report.totalLines) }}</strong>
          <small>{{ report.coveredLines }} / {{ report.totalLines }}</small>
        </article>
        <article class="hero-card">
          <span>分支目标覆盖率</span>
          <strong>{{ formatRate(report.coveredBranchTargets, report.totalBranchTargets) }}</strong>
          <small>{{ report.coveredBranchTargets }} / {{ report.totalBranchTargets }}</small>
        </article>
      </div>

      <div class="detail-grid">
        <section class="panel">
          <div class="card-title">
            <h2>报告元信息</h2>
          </div>
          <div class="info-grid">
            <div v-for="item in infoItems" :key="item.label" class="info-item">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="card-title">
            <h2>覆盖率明细</h2>
          </div>
          <div class="metric-list">
            <article class="metric-card">
              <div>
                <strong>类</strong>
                <span>covered / total</span>
              </div>
              <div class="metric-value">{{ report.coveredClasses }} / {{ report.totalClasses }}</div>
            </article>
            <article class="metric-card">
              <div>
                <strong>方法</strong>
                <span>covered / total</span>
              </div>
              <div class="metric-value">{{ report.coveredMethods }} / {{ report.totalMethods }}</div>
            </article>
            <article class="metric-card">
              <div>
                <strong>代码行</strong>
                <span>covered / total</span>
              </div>
              <div class="metric-value">{{ report.coveredLines }} / {{ report.totalLines }}</div>
            </article>
            <article class="metric-card">
              <div>
                <strong>分支</strong>
                <span>covered / total</span>
              </div>
              <div class="metric-value">{{ report.coveredBranches }} / {{ report.totalBranches }}</div>
            </article>
            <article class="metric-card">
              <div>
                <strong>分支目标</strong>
                <span>covered / total</span>
              </div>
              <div class="metric-value">{{ report.coveredBranchTargets }} / {{ report.totalBranchTargets }}</div>
            </article>
          </div>
        </section>
      </div>

      <section class="panel class-panel">
        <div class="card-title">
          <h2>类级统计</h2>
          <span class="table-count">{{ filteredClassStats.length }} 个类</span>
        </div>
        <label class="search-box">
          <span>类名筛选</span>
          <input v-model.trim="classKeyword" class="text-input" type="text" placeholder="输入类名关键字" />
        </label>
        <div v-if="!filteredClassStats.length" class="empty-card">暂无类级统计数据</div>
        <div v-else class="table-shell">
          <table class="report-table">
            <thead>
              <tr>
                <th>类名</th>
                <th>方法</th>
                <th>方法覆盖率</th>
                <th>分支</th>
                <th>分支目标覆盖率</th>
                <th>代码行</th>
                <th>代码行覆盖率</th>
                <th>复杂度</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in filteredClassStats" :key="item.className">
                <td class="class-cell" :title="item.className">{{ item.className }}</td>
                <td>{{ item.coveredMethods }} / {{ item.totalMethods }}</td>
                <td>{{ formatRate(item.coveredMethods, item.totalMethods) }}</td>
                <td>{{ item.coveredBranches }} / {{ item.totalBranches }}</td>
                <td>{{ item.totalBranchTargets ? `${item.branchRate.toFixed(1)}%` : 'N/A' }}</td>
                <td>{{ item.coveredLines }} / {{ item.totalLines }}</td>
                <td>{{ formatRate(item.coveredLines, item.totalLines) }}</td>
                <td>{{ item.totalComplexity }}</td>
                <td>
                  <RouterLink class="table-link" :to="buildCodeRoute(item.className)">源码</RouterLink>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="codeRelationships?.length" class="panel interface-panel">
        <div class="card-title">
          <h2>接口聚合</h2>
          <span class="table-count">{{ codeRelationships.length }} 个请求入口</span>
        </div>
        <div class="relationship-list">
          <article v-for="group in codeRelationships" :key="group.requestUrl" class="relationship-card">
            <div class="relationship-top">
              <div>
                <strong class="relationship-url">{{ group.requestUrl || '-' }}</strong>
                <div class="relationship-meta">
                  <span>方法 {{ summarizeGroup(group.methods).methodCount }}</span>
                  <span>行 {{ summarizeGroup(group.methods).coveredLines }} / {{ summarizeGroup(group.methods).totalLines }}</span>
                  <span>分支 {{ summarizeGroup(group.methods).coveredBranches }} / {{ summarizeGroup(group.methods).totalBranches }}</span>
                  <span>分支目标 {{ summarizeGroup(group.methods).coveredBranchTargets }} / {{ summarizeGroup(group.methods).totalBranchTargets }}</span>
                </div>
              </div>
              <span class="group-rate">{{ summarizeGroup(group.methods).totalBranchTargets > 0 ? formatRate(summarizeGroup(group.methods).coveredBranchTargets, summarizeGroup(group.methods).totalBranchTargets) : 'N/A' }}</span>
            </div>
            <div class="table-shell relationship-shell">
              <table class="report-table relationship-table">
                <thead>
                  <tr>
                    <th>类名</th>
                    <th>方法</th>
                    <th>代码行</th>
                    <th>分支</th>
                    <th>覆盖状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="method in group.methods" :key="`${method.className}-${method.methodName}-${method.methodDescriptor}`">
                    <td class="class-cell" :title="method.className">{{ method.className }}</td>
                    <td>
                      <strong>{{ method.methodName }}</strong>
                      <div class="helper-text">{{ method.methodDescriptor || '-' }}</div>
                    </td>
                    <td>{{ method.coveredLineCount }} / {{ method.lineTotalCount }}</td>
                    <td>
                      {{ method.branchTargetCoveredCount }} / {{ method.branchTargetTotalCount }}
                    </td>
                    <td>
                      <span :class="['status-pill', relationshipTone(method)]">
                        {{ relationshipText(method) }}
                      </span>
                    </td>
                    <td>
                      <RouterLink class="table-link" :to="buildCodeRoute(method.className)">源码</RouterLink>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </article>
        </div>
      </section>
    </template>
    <div v-else class="status-card">
      <strong>{{ emptyTitle }}</strong>
      <p>{{ emptyDescription }}</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

import type {
  ClassCoverageSummary,
  CoverageReportSummary,
  SnapshotCodeRelationshipGroupSummary,
  SnapshotCodeRelationshipMethodSummary,
} from '@/api/types'

interface InfoItem {
  label: string
  value: string | number
}

const props = defineProps<{
  eyebrow: string
  title: string
  subtext?: string
  graphRoute: RouteLocationRaw
  backRoute: RouteLocationRaw
  backLabel: string
  loading: boolean
  loadingText: string
  error?: string
  report?: CoverageReportSummary
  classStats?: ClassCoverageSummary[]
  codeRelationships?: SnapshotCodeRelationshipGroupSummary[]
  infoItems: InfoItem[]
  buildCodeRoute: (className: string) => RouteLocationRaw
  emptyTitle: string
  emptyDescription: string
  secondaryActionLabel?: string
}>()

defineEmits<{
  (event: 'secondary-action'): void
}>()

const classKeyword = ref('')
const filteredClassStats = computed(() => {
  const source = props.classStats || []
  if (!classKeyword.value) {
    return source
  }
  return source.filter((item) => item.className.toLowerCase().includes(classKeyword.value.toLowerCase()))
})

function formatRate(covered = 0, total = 0) {
  if (!total) {
    return '0%'
  }
  return `${((covered / total) * 100).toFixed(1)}%`
}

function summarizeGroup(methods: SnapshotCodeRelationshipMethodSummary[] = []) {
  return methods.reduce(
    (acc, method) => {
      acc.methodCount += 1
      acc.totalLines += method.lineTotalCount || 0
      acc.coveredLines += method.coveredLineCount || 0
      acc.totalBranches += method.branchTotalCount || 0
      acc.coveredBranches += method.branchCoveredCount || 0
      acc.totalBranchTargets += method.branchTargetTotalCount || 0
      acc.coveredBranchTargets += method.branchTargetCoveredCount || 0
      return acc
    },
    {
      methodCount: 0,
      totalLines: 0,
      coveredLines: 0,
      totalBranches: 0,
      coveredBranches: 0,
      totalBranchTargets: 0,
      coveredBranchTargets: 0,
    },
  )
}

function relationshipTone(method: SnapshotCodeRelationshipMethodSummary) {
  if (!method.lineTotalCount) {
    return 'default'
  }
  const lineRate = method.coveredLineCount / method.lineTotalCount
  const branchRate = method.branchTargetTotalCount ? method.branchTargetCoveredCount / method.branchTargetTotalCount : 0
  if (lineRate === 1 && (!method.branchTargetTotalCount || branchRate === 1)) {
    return 'success'
  }
  if (lineRate > 0 || branchRate > 0) {
    return 'warning'
  }
  return 'default'
}

function relationshipText(method: SnapshotCodeRelationshipMethodSummary) {
  const tone = relationshipTone(method)
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
.info-item span,
.metric-card span,
.search-box span,
.table-count {
  color: #64748b;
}

.secondary-link,
.ghost-button,
.table-link {
  color: #0f766e;
  font-weight: 700;
}

.ghost-button {
  border: 1px solid rgba(15, 118, 110, 0.18);
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(15, 118, 110, 0.06);
  cursor: pointer;
}

.status-card,
.hero-card,
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

.hero-grid,
.info-grid,
.metric-list {
  display: grid;
  gap: 14px;
}

.hero-grid {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  margin-bottom: 18px;
}

.hero-card strong {
  display: block;
  margin: 12px 0 6px;
  font-size: 28px;
  line-height: 1;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 18px;
}

.class-panel {
  margin-top: 18px;
}

.info-grid {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.info-item,
.metric-card {
  padding: 14px;
  border-radius: 16px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.info-item strong {
  display: block;
  margin-top: 6px;
  word-break: break-word;
}

.metric-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.metric-card div {
  display: grid;
  gap: 4px;
}

.metric-value {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.search-box {
  display: grid;
  gap: 8px;
  margin-bottom: 16px;
}

.text-input {
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
}

.table-shell {
  overflow: auto;
}

.report-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 960px;
}

.report-table th,
.report-table td {
  padding: 12px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
  vertical-align: middle;
}

.report-table th {
  color: #475569;
  font-size: 13px;
}

.class-cell {
  max-width: 360px;
  word-break: break-word;
}

.interface-panel {
  margin-top: 18px;
}

.relationship-list {
  display: grid;
  gap: 14px;
}

.relationship-card {
  padding: 16px;
  border-radius: 18px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.relationship-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.relationship-url {
  display: block;
  word-break: break-all;
}

.relationship-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
}

.group-rate {
  color: #0f766e;
  font-weight: 700;
  white-space: nowrap;
}

.relationship-shell {
  background: transparent;
}

.relationship-table {
  min-width: 880px;
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

@media (max-width: 960px) {
  .hero-grid,
  .detail-grid,
  .info-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .header-actions,
  .card-title {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
