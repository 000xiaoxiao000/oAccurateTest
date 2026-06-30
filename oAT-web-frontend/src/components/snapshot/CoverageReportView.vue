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
            <button class="ghost-button small" type="button" @click="metaOpen = !metaOpen">{{ metaOpen ? '收起' : '展开' }}</button>
          </div>
          <div v-show="metaOpen" class="info-grid">
            <div v-for="item in infoItems" :key="item.label" class="info-item">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="card-title">
            <h2>覆盖率明细</h2>
            <button class="ghost-button small" type="button" @click="metricsOpen = !metricsOpen">{{ metricsOpen ? '收起' : '展开' }}</button>
          </div>
          <div v-show="metricsOpen" class="metric-list">
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
            <button class="ghost-button small" type="button" @click="relationshipsOpen = !relationshipsOpen">
              {{ relationshipsOpen ? '收起列表' : '展开列表' }}
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
            highlight-related
          />
        </div>
        <div v-show="relationshipsOpen" class="list-toolbar">
          <label class="search-box compact-search">
            <span>接口/类/方法筛选</span>
            <input v-model.trim="relationshipKeyword" class="text-input" type="search" placeholder="输入 URL、类名或方法名" aria-label="筛选接口类方法关系" />
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
        <div v-show="relationshipsOpen" class="relationship-list">
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
        <div v-if="relationshipsOpen && !filteredRelationships.length" class="empty-card">没有匹配的接口聚合数据</div>
        <AppPagination
          v-if="relationshipsOpen && filteredRelationships.length > relationshipPageSize"
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
          <div class="title-actions">
            <span class="table-count">{{ filteredClassStats.length }} 个类</span>
            <button class="ghost-button small" type="button" @click="classStatsOpen = !classStatsOpen">{{ classStatsOpen ? '收起' : '展开' }}</button>
          </div>
        </div>
        <div v-show="classStatsOpen" class="list-toolbar">
          <label class="search-box compact-search">
            <span>类名筛选</span>
            <input v-model.trim="classKeyword" class="text-input" type="search" placeholder="输入类名关键字" aria-label="筛选类级统计" />
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
        <div v-if="classStatsOpen && !filteredClassStats.length" class="empty-card">暂无类级统计数据</div>
        <div v-else-if="classStatsOpen" class="table-shell">
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
              <tr v-for="item in paginatedClassStats" :key="item.className" :class="item.hasCodeChanges && 'row-changed'">
                <td class="class-cell" :title="item.className">
                  <span>{{ item.className }}</span>
                  <span
                    v-if="item.hasCodeChanges"
                    class="change-badge"
                    title="该类在不同 Commit 间覆盖率数据有变化。当前报告汇总了所有 Commit 数据，源码着色使用最新 Commit，建议重点关注此类。"
                  >跨 Commit 差异</span>
                </td>
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
          v-if="classStatsOpen && filteredClassStats.length > classPageSize"
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
import {
  formatRate,
  relationshipText,
  relationshipTone,
  summarizeGroup,
  useCoverageReportRelationshipGraph,
} from '@/features/coverage/composables/useCoverageReportRelationships'
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
const relationshipGraphOpen = ref(true)
const relationshipsOpen = ref(true)
const classStatsOpen = ref(true)
const metaOpen = ref(false)
const metricsOpen = ref(false)
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
const { relationshipGraphNodes, relationshipGraphEdges } = useCoverageReportRelationshipGraph(paginatedRelationships)

watch([classKeyword, classPageSize], () => {
  classPage.value = 1
})

watch([relationshipKeyword, relationshipPageSize], () => {
  relationshipPage.value = 1
})

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

</script>

<style scoped src="@/components/snapshot/styles/coverage-report-view.css"></style>
