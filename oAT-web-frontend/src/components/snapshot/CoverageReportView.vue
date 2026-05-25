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
        <RouterLink v-if="codeGraphRoute" class="ghost-button" :to="codeGraphRoute">查看代码图层</RouterLink>
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

      <section v-if="codeRelationships?.length" class="panel interface-panel">
        <div class="card-title">
          <h2>接口聚合</h2>
          <div class="title-actions">
            <span class="table-count">{{ filteredRelationships.length }} / {{ codeRelationships.length }} 个请求入口</span>
            <button class="ghost-button small" type="button" @click="relationshipGraphOpen = !relationshipGraphOpen">
              {{ relationshipGraphOpen ? '收起图谱' : '展开图谱' }}
            </button>
          </div>
        </div>
        <div v-show="relationshipGraphOpen" class="relationship-graph-card">
          <div class="relationship-graph-head">
            <div>
              <strong>接口-类-方法关系图</strong>
              <span>把请求入口、命中类、执行方法按调用关系展开，补齐老前端的代码关联图谱视图。</span>
            </div>
            <RouterLink v-if="codeGraphRoute" class="table-link" :to="codeGraphRoute">打开源码链路图</RouterLink>
          </div>
          <RelationBoard
            eyebrow="Code Relationship"
            title="代码关联图谱"
            :loading="false"
            :nodes="relationshipGraphNodes"
            :edges="relationshipGraphEdges"
            compact
            hide-lists
          />
        </div>
        <div class="list-toolbar">
          <label class="search-box compact-search">
            <span>接口/类/方法筛选</span>
            <input v-model.trim="relationshipKeyword" class="text-input" type="text" placeholder="输入 URL、类名或方法名" />
          </label>
          <label class="select-box">
            <span>每页</span>
            <select v-model.number="relationshipPageSize" class="text-input">
              <option :value="5">5 个入口</option>
              <option :value="10">10 个入口</option>
              <option :value="20">20 个入口</option>
            </select>
          </label>
        </div>
        <div class="relationship-list">
          <article v-for="group in paginatedRelationships" :key="group.requestUrl" class="relationship-card">
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
              <div class="relationship-actions">
                <span class="group-rate">{{ summarizeGroup(group.methods).totalBranchTargets > 0 ? formatRate(summarizeGroup(group.methods).coveredBranchTargets, summarizeGroup(group.methods).totalBranchTargets) : 'N/A' }}</span>
                <button v-if="group.methods.length > relationshipMethodPreviewLimit" class="ghost-button small" type="button" @click="toggleRelationshipGroup(group.requestUrl)">
                  {{ expandedRelationshipGroups.has(relationshipGroupKey(group.requestUrl)) ? '收起' : `展开 ${group.methods.length} 个方法` }}
                </button>
              </div>
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
                  <tr v-for="method in visibleRelationshipMethods(group)" :key="`${method.className}-${method.methodName}-${method.methodDescriptor}`">
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
        <div v-if="!filteredRelationships.length" class="empty-card">没有匹配的接口聚合数据</div>
        <AppPagination
          v-if="filteredRelationships.length > relationshipPageSize"
          v-model:page="relationshipPage"
          v-model:page-size="relationshipPageSize"
          :total="filteredRelationships.length"
          item-name="个请求入口"
          :page-sizes="[5, 10, 20]"
        />
      </section>

      <section class="panel class-panel">
        <div class="card-title">
          <h2>类级统计</h2>
          <span class="table-count">{{ filteredClassStats.length }} 个类</span>
        </div>
        <div class="list-toolbar">
          <label class="search-box compact-search">
            <span>类名筛选</span>
            <input v-model.trim="classKeyword" class="text-input" type="text" placeholder="输入类名关键字" />
          </label>
          <label class="select-box">
            <span>每页</span>
            <select v-model.number="classPageSize" class="text-input">
              <option :value="10">10 个类</option>
              <option :value="20">20 个类</option>
              <option :value="50">50 个类</option>
            </select>
          </label>
        </div>
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
              <tr v-for="item in paginatedClassStats" :key="item.className">
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
        <AppPagination
          v-if="filteredClassStats.length > classPageSize"
          v-model:page="classPage"
          v-model:page-size="classPageSize"
          :total="filteredClassStats.length"
          item-name="个类"
        />
      </section>

    </template>
    <div v-else class="status-card">
      <strong>{{ emptyTitle }}</strong>
      <p>{{ emptyDescription }}</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import RelationBoard from '@/components/map/RelationBoard.vue'
import AppPagination from '@/components/AppPagination.vue'
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
  codeGraphRoute?: RouteLocationRaw
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
const classPage = ref(1)
const classPageSize = ref(20)
const relationshipKeyword = ref('')
const relationshipPage = ref(1)
const relationshipPageSize = ref(5)
const relationshipGraphOpen = ref(false)
const expandedRelationshipGroups = ref(new Set<string>())
const relationshipMethodPreviewLimit = 8
const filteredClassStats = computed(() => {
  const source = props.classStats || []
  if (!classKeyword.value) {
    return source
  }
  return source.filter((item) => item.className.toLowerCase().includes(classKeyword.value.toLowerCase()))
})

const paginatedClassStats = computed(() => {
  const start = (classPage.value - 1) * classPageSize.value
  return filteredClassStats.value.slice(start, start + classPageSize.value)
})

const filteredRelationships = computed(() => {
  const source = props.codeRelationships || []
  const needle = relationshipKeyword.value.toLowerCase()
  if (!needle) return source
  return source.filter((group) => [
    group.requestUrl,
    ...group.methods.flatMap((method) => [method.className, method.methodName, method.methodDescriptor]),
  ].join(' ').toLowerCase().includes(needle))
})

const paginatedRelationships = computed(() => {
  const start = (relationshipPage.value - 1) * relationshipPageSize.value
  return filteredRelationships.value.slice(start, start + relationshipPageSize.value)
})

watch([classKeyword, classPageSize], () => {
  classPage.value = 1
})

watch([relationshipKeyword, relationshipPageSize], () => {
  relationshipPage.value = 1
})

const relationshipGraphNodes = computed(() => {
  const nodes: Array<{ id: string; label: string; type: string; description?: string; meta?: string[] }> = []
  const classIds = new Set<string>()

  ;(props.codeRelationships || []).forEach((group, groupIndex) => {
    const requestId = requestNodeId(group, groupIndex)
    const summary = summarizeGroup(group.methods)
    nodes.push({
      id: requestId,
      label: group.requestUrl || `请求入口 ${groupIndex + 1}`,
      type: 'request entry snapshot',
      description: '请求入口',
      meta: [
        `方法 ${summary.methodCount}`,
        `行 ${summary.coveredLines}/${summary.totalLines}`,
        `分支目标 ${summary.coveredBranchTargets}/${summary.totalBranchTargets}`,
      ],
    })

    ;(group.methods || []).forEach((method, methodIndex) => {
      const classId = classNodeId(groupIndex, method.className)
      if (!classIds.has(classId)) {
        classIds.add(classId)
        nodes.push({
          id: classId,
          label: shortClassName(method.className),
          type: 'code class',
          description: method.className,
          meta: [`类 ${method.className}`],
        })
      }
      nodes.push({
        id: methodNodeId(groupIndex, methodIndex, method),
        label: method.methodName || '匿名方法',
        type: 'code method',
        description: method.methodDescriptor || method.className,
        meta: [
          `类 ${shortClassName(method.className)}`,
          `行覆盖 ${formatRate(method.coveredLineCount, method.lineTotalCount)}`,
          method.branchTargetTotalCount ? `分支目标 ${formatRate(method.branchTargetCoveredCount, method.branchTargetTotalCount)}` : '分支目标 N/A',
        ],
      })
    })
  })
  return nodes
})

const relationshipGraphEdges = computed(() => {
  const edges: Array<{ id: string; source: string; target: string; label: string; action?: string; sourceLabel?: string; targetLabel?: string }> = []
  const edgeIds = new Set<string>()
  const nodeLabelMap = new Map(relationshipGraphNodes.value.map((node) => [node.id, node.label]))

  ;(props.codeRelationships || []).forEach((group, groupIndex) => {
    const requestId = requestNodeId(group, groupIndex)
    ;(group.methods || []).forEach((method, methodIndex) => {
      const classId = classNodeId(groupIndex, method.className)
      const methodId = methodNodeId(groupIndex, methodIndex, method)
      pushEdge(edges, edgeIds, {
        id: `${requestId}->${classId}`,
        source: requestId,
        target: classId,
        label: '命中类',
        action: 'select',
        sourceLabel: nodeLabelMap.get(requestId),
        targetLabel: nodeLabelMap.get(classId),
      })
      pushEdge(edges, edgeIds, {
        id: `${classId}->${methodId}`,
        source: classId,
        target: methodId,
        label: relationshipText(method),
        action: relationshipAction(method),
        sourceLabel: nodeLabelMap.get(classId),
        targetLabel: nodeLabelMap.get(methodId),
      })
    })
  })
  return edges
})


function requestNodeId(group: SnapshotCodeRelationshipGroupSummary, groupIndex: number) {
  return `request:${groupIndex}:${encodeURIComponent(group.requestUrl || 'unknown')}`
}

function classNodeId(groupIndex: number, className?: string) {
  return `class:${groupIndex}:${encodeURIComponent(className || 'unknown')}`
}

function methodNodeId(groupIndex: number, methodIndex: number, method: SnapshotCodeRelationshipMethodSummary) {
  return [
    'method',
    groupIndex,
    methodIndex,
    encodeURIComponent(method.className || 'unknown'),
    encodeURIComponent(method.methodName || 'unknown'),
    encodeURIComponent(method.methodDescriptor || ''),
  ].join(':')
}

function pushEdge<T extends { id: string }>(target: T[], seen: Set<string>, edge: T) {
  if (seen.has(edge.id)) {
    return
  }
  seen.add(edge.id)
  target.push(edge)
}

function relationshipGroupKey(requestUrl?: string) {
  return requestUrl || '-'
}

function toggleRelationshipGroup(requestUrl?: string) {
  const key = relationshipGroupKey(requestUrl)
  const next = new Set(expandedRelationshipGroups.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expandedRelationshipGroups.value = next
}

function visibleRelationshipMethods(group: SnapshotCodeRelationshipGroupSummary) {
  const methods = group.methods || []
  if (expandedRelationshipGroups.value.has(relationshipGroupKey(group.requestUrl))) {
    return methods
  }
  return methods.slice(0, relationshipMethodPreviewLimit)
}

function shortClassName(className?: string) {
  if (!className) {
    return '-'
  }
  const parts = className.split('.')
  return parts[parts.length - 1] || className
}

function relationshipAction(method: SnapshotCodeRelationshipMethodSummary) {
  const tone = relationshipTone(method)
  if (tone === 'success') {
    return 'select'
  }
  if (tone === 'warning') {
    return 'update'
  }
  return 'delete'
}

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
.card-title,
.title-actions,
.list-toolbar,
.relationship-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-header,
.card-title {
  justify-content: space-between;
}

.header-actions,
.title-actions,
.list-toolbar,
.relationship-actions {
  flex-wrap: wrap;
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

.ghost-button.small {
  padding: 7px 10px;
  font-size: 12px;
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

.search-box,
.select-box {
  display: grid;
  gap: 8px;
}

.list-toolbar {
  justify-content: space-between;
  margin: 14px 0 16px;
}

.compact-search {
  min-width: min(460px, 100%);
  flex: 1 1 360px;
}

.select-box {
  min-width: 150px;
}

.text-input {
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
}

.table-shell {
  overflow: auto;
  max-width: 100%;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 16px;
}

.report-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 1040px;
  table-layout: fixed;
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

.report-table th:first-child,
.report-table td:first-child {
  width: 34%;
  min-width: 300px;
}

.report-table th:nth-child(2),
.report-table td:nth-child(2) {
  width: 26%;
}

.relationship-table th:first-child,
.relationship-table td:first-child {
  width: 32%;
}

.relationship-table th:nth-child(2),
.relationship-table td:nth-child(2) {
  width: 30%;
}

.class-cell {
  max-width: none;
  min-width: 300px;
  white-space: normal;
  word-break: normal;
  overflow-wrap: anywhere;
  line-height: 1.55;
}

.interface-panel {
  margin-top: 18px;
}

.relationship-graph-card {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.08), rgba(37, 99, 235, 0.08)), #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.relationship-graph-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.relationship-graph-head div {
  display: grid;
  gap: 4px;
}

.relationship-graph-head span {
  color: #64748b;
  line-height: 1.5;
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

.relationship-actions {
  justify-content: flex-end;
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
  .card-title,
  .relationship-graph-head,
  .list-toolbar,
  .relationship-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
