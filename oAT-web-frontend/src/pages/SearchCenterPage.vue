<template>
  <section :class="['search-page', mode === 'table' && 'table-mode']">
    <form class="search-form" @submit.prevent="submitSearch">
      <div v-if="mode === 'table' && error" class="inline-error">{{ error }}</div>
      <div class="action-input">
        <input
          v-model.trim="searchText"
          type="text"
          :maxlength="mode === 'table' ? 30 : 120"
          :placeholder="mode === 'table' ? '格式：数据库名 表名' : '搜索...'
          "
          @keydown.enter.prevent="submitSearch"
        />
        <details class="mode-dropdown">
          <summary>
            <span>{{ modeLabel }}</span>
            <span class="caret">⌄</span>
          </summary>
          <div class="mode-menu">
            <button type="button" :class="{ active: mode === 'keyword' }" @click="switchMode('keyword')">用例</button>
            <button type="button" :class="{ active: mode === 'table' }" @click="switchMode('table')">表结构图</button>
          </div>
        </details>
        <button class="search-button" type="submit" :disabled="activeLoading">{{ activeLoading ? '搜索中...' : '搜索' }}</button>
      </div>
    </form>

    <div v-if="mode === 'keyword' && error" class="message error">{{ error }}</div>

    <section v-if="mode === 'keyword'" class="keyword-results">
      <div v-if="!keywordSearched" class="placeholder-state">
        <strong>⌕</strong>
        <span>输入关键词搜索系统快照、SQL 或远程调用</span>
      </div>
      <template v-else>
        <div class="result-count">为您找到：{{ keywordResults?.total || 0 }} 条结果</div>
        <div v-if="!keywordResults?.results?.length" class="placeholder-state compact">
          <strong>⌕</strong>
          <span>没有找到匹配结果</span>
        </div>
        <div v-else class="search-items">
          <RouterLink
            v-for="item in keywordResults.results"
            :key="item.id"
            class="search-item"
            :to="item.targetPath"
            target="_blank"
          >
            <div class="item-image">
              <img :src="resultImage(item)" alt="" loading="lazy" />
            </div>
            <div class="item-content">
              <div class="item-title" v-html="item.titleFragment || item.title || item.plainTitle || '-'" />
              <div class="item-desc">
                <template v-if="hitFragments(item).length">
                  <span v-for="fragment in hitFragments(item)" :key="fragment" v-html="fragment"></span>
                </template>
                <span v-else>{{ item.subTitle || item.directoryPath || '暂无命中片段' }}</span>
              </div>
            </div>
          </RouterLink>
        </div>
      </template>
    </section>

    <section v-else class="table-search-area">
      <div v-if="!tableSearched && !loadingTable" class="table-empty">
        <strong>▦</strong>
        <span>输入“数据库名 表名”查看表结构关联快照</span>
      </div>
      <div v-else-if="loadingTable" class="table-empty">
        <strong>…</strong>
        <span>正在搜索表结构关系...</span>
      </div>
      <template v-else>
        <div v-if="!tableGraph || tableGraph.nodes.length <= 1" class="table-empty floating">
          <strong>▦</strong>
          <span>没有找到关联快照</span>
        </div>
        <div class="graph-canvas">
          <svg class="table-graph" :viewBox="tableViewBox" role="img" aria-label="表结构关系图">
            <defs>
              <marker id="search-graph-arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
                <path d="M0,0 L0,6 L9,3 z" fill="currentColor" />
              </marker>
            </defs>
            <g class="edge-layer">
              <g v-for="edge in positionedEdges" :key="edge.id" :class="['graph-edge', edgeTone(edge.action)]">
                <line :x1="edge.source.x" :y1="edge.source.y" :x2="edge.target.x" :y2="edge.target.y" marker-end="url(#search-graph-arrow)" />
                <text :x="edge.labelX" :y="edge.labelY">{{ edge.label || actionLabel(edge.action) || '关联' }}</text>
              </g>
            </g>
            <g class="node-layer">
              <a
                v-for="node in positionedNodes"
                :key="node.id"
                :href="nodeHref(node)"
                :target="node.type === 'snapshot' ? '_blank' : undefined"
                :class="['graph-node', node.type || 'node']"
              >
                <circle :cx="node.x" :cy="node.y" :r="node.type === 'table' ? 36 : 32" />
                <image v-if="node.type === 'snapshot' && node.backgroundImage" :href="node.backgroundImage" :x="node.x - 25" :y="node.y - 25" width="50" height="50" preserveAspectRatio="xMidYMid slice" />
                <text :x="node.x" :y="node.y + 52" text-anchor="middle">{{ node.label || node.id }}</text>
              </a>
            </g>
          </svg>
        </div>
        <aside v-if="tableGraph?.edges.length" class="relation-list">
          <div class="relation-head">{{ tableGraph.nodes.length }} 个节点 / {{ tableGraph.edges.length }} 条关系</div>
          <article v-for="edge in tableGraph.edges" :key="edge.id" :class="['relation-row', edgeTone(edge.action)]">
            <strong>{{ edge.label || actionLabel(edge.action) || '关联' }}</strong>
            <span>{{ nodeLabel(edge.source) }} → {{ nodeLabel(edge.target) }}</span>
          </article>
        </aside>
      </template>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { searchKeyword, searchTableGraph } from '@/api/bootstrap'
import type { NetworkGraphEdge, NetworkGraphNode, SearchKeywordResult } from '@/api/types'

type SearchMode = 'keyword' | 'table'

interface PositionedNode extends NetworkGraphNode {
  x: number
  y: number
}

interface PositionedEdge extends Omit<NetworkGraphEdge, 'source' | 'target'> {
  source: PositionedNode
  target: PositionedNode
  labelX: number
  labelY: number
}

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.projectId || ''))
const mode = ref<SearchMode>(route.query.tab === 'table' ? 'table' : 'keyword')
const keyword = ref(String(route.query.keyword || route.query.q || ''))
const tableKeyword = ref(String(route.query.tableKeyword || ''))
const database = ref(String(route.query.database || ''))
const table = ref(String(route.query.table || ''))
const searchText = ref(mode.value === 'table' ? initialTableText() : keyword.value)
const error = ref('')
const loadingKeyword = ref(false)
const loadingTable = ref(false)
const keywordSearched = ref(false)
const tableSearched = ref(false)
const keywordResults = ref<Awaited<ReturnType<typeof searchKeyword>> | null>(null)
const tableGraph = ref<Awaited<ReturnType<typeof searchTableGraph>> | null>(null)

const modeLabel = computed(() => (mode.value === 'table' ? '表结构图' : '用例'))
const activeLoading = computed(() => (mode.value === 'keyword' ? loadingKeyword.value : loadingTable.value))
const tableViewBox = computed(() => '0 0 1100 620')

const positionedNodes = computed<PositionedNode[]>(() => {
  const nodes = tableGraph.value?.nodes || []
  const tableNode = nodes.find((node) => node.type === 'table') || nodes[nodes.length - 1]
  const snapshots = nodes.filter((node) => node.id !== tableNode?.id)
  const centerX = 760
  const centerY = 310
  const radiusX = 360
  const radiusY = 210
  const positioned = snapshots.map((node, index) => {
    const angle = snapshots.length <= 1 ? Math.PI : Math.PI * 0.2 + (index * Math.PI * 1.6) / Math.max(snapshots.length - 1, 1)
    return {
      ...node,
      x: centerX - Math.cos(angle) * radiusX,
      y: centerY + Math.sin(angle) * radiusY,
    }
  })
  if (tableNode) {
    positioned.push({ ...tableNode, x: centerX, y: centerY })
  }
  return positioned
})

const positionedEdges = computed<PositionedEdge[]>(() => {
  const nodeMap = new Map(positionedNodes.value.map((node) => [node.id, node]))
  return (tableGraph.value?.edges || [])
    .map((edge) => {
      const source = nodeMap.get(edge.source)
      const target = nodeMap.get(edge.target)
      if (!source || !target) return null
      return {
        ...edge,
        source,
        target,
        labelX: (source.x + target.x) / 2,
        labelY: (source.y + target.y) / 2 - 8,
      }
    })
    .filter((edge): edge is PositionedEdge => Boolean(edge))
})

watch(mode, () => syncTextFromMode())

onMounted(() => {
  if (mode.value === 'keyword' && keyword.value) {
    submitKeywordSearch()
  } else if (mode.value === 'table' && (tableKeyword.value || (database.value && table.value))) {
    submitTableSearch()
  }
})

function initialTableText() {
  if (tableKeyword.value) return tableKeyword.value
  return [database.value, table.value].filter(Boolean).join(' ')
}

function syncTextFromMode() {
  searchText.value = mode.value === 'table' ? initialTableText() : keyword.value
  error.value = ''
}

function switchMode(next: SearchMode) {
  mode.value = next
  syncTextFromMode()
  updateQuery({ tab: next === 'table' ? 'table' : undefined })
}

function submitSearch() {
  if (mode.value === 'keyword') {
    keyword.value = searchText.value
    submitKeywordSearch()
    return
  }
  tableKeyword.value = searchText.value
  submitTableSearch()
}

async function submitKeywordSearch() {
  const value = keyword.value.trim()
  if (!value) {
    error.value = '请输入搜索关键字'
    return
  }
  loadingKeyword.value = true
  keywordSearched.value = true
  error.value = ''
  try {
    keywordResults.value = await searchKeyword(projectId.value, value)
    updateQuery({ keyword: value, tab: undefined, q: undefined, database: undefined, table: undefined, tableKeyword: undefined })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '搜索失败，请稍后重试'
  } finally {
    loadingKeyword.value = false
  }
}

async function submitTableSearch() {
  const parsed = parseTableKeyword(tableKeyword.value || searchText.value)
  if (!parsed) return
  loadingTable.value = true
  tableSearched.value = true
  error.value = ''
  database.value = parsed.database
  table.value = parsed.table
  tableKeyword.value = `${parsed.database} ${parsed.table}`
  searchText.value = tableKeyword.value
  try {
    tableGraph.value = await searchTableGraph(projectId.value, parsed.database, parsed.table)
    updateQuery({ tab: 'table', database: parsed.database, table: parsed.table, tableKeyword: undefined, keyword: undefined, q: undefined })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '表结构搜索失败，请稍后重试'
  } finally {
    loadingTable.value = false
  }
}

function parseTableKeyword(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    error.value = '请输入数据库名和表名'
    return null
  }
  if (trimmed.length > 30) {
    error.value = '搜索关键字不能超过30个字符'
    return null
  }
  const parts = trimmed.split(/\s+/)
  if (parts.length !== 2 || !parts[0] || !parts[1]) {
    error.value = '输入格式为:数据库名 表名'
    return null
  }
  return { database: parts[0], table: parts[1] }
}

function updateQuery(next: Record<string, string | undefined>) {
  const query = { ...route.query }
  Object.entries(next).forEach(([key, value]) => {
    if (value === undefined || value === '') delete query[key]
    else query[key] = value
  })
  router.replace({ query }).catch(() => undefined)
}

function resultImage(item: SearchKeywordResult) {
  return item.imagePath || (item.headImage ? `/r/${item.headImage}` : '/images/image.png')
}

function hitFragments(item: SearchKeywordResult) {
  return [
    ...(item.describeFragments || []),
    ...(item.sqlContentFragments || []),
    ...(item.remoteContentFragments || []),
  ]
}

function nodeLabel(id: string) {
  return tableGraph.value?.nodes.find((node) => node.id === id)?.label || id
}

function nodeHref(node: PositionedNode) {
  if (node.type !== 'snapshot') return undefined
  const appId = tableGraph.value?.edges.find((edge) => edge.source === node.id || edge.target === node.id) ? undefined : undefined
  void appId
  return undefined
}

function actionLabel(action?: string) {
  if (!action) return ''
  return action
    .split(',')
    .map((item) => {
      const value = item.trim().toLowerCase()
      if (value === 'insert') return '增'
      if (value === 'delete') return '删'
      if (value === 'update') return '改'
      if (value === 'select') return '查'
      return item
    })
    .filter(Boolean)
    .join(',')
}

function edgeTone(action?: string) {
  const value = (action || '').toLowerCase()
  if (value.includes('delete')) return 'delete'
  if (value.includes('update')) return 'update'
  if (value.includes('insert')) return 'insert'
  if (value.includes('select')) return 'select'
  return 'default'
}
</script>

<style scoped>
.search-page {
  min-height: calc(100vh - 130px);
  padding: 18px 16px 0;
}

.search-page.table-mode {
  position: relative;
  min-height: calc(100vh - 82px);
  padding-bottom: 0;
}

.search-form {
  position: relative;
  z-index: 8;
  max-width: 760px;
}

.action-input {
  display: flex;
  min-width: min(420px, 100%);
  max-width: 760px;
  width: 60vw;
  border: 1px solid rgba(15, 23, 42, .14);
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, .08);
}

.action-input input {
  flex: 1 1 auto;
  min-width: 0;
  border: none;
  border-radius: 6px 0 0 6px;
  padding: 11px 13px;
  outline: none;
  font-size: 14px;
}

.mode-dropdown {
  position: relative;
  flex: 0 0 auto;
  border-left: 1px solid rgba(15, 23, 42, .12);
}

.mode-dropdown summary {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 100%;
  min-width: 116px;
  padding: 0 12px;
  background: #f8fafc;
  color: #334155;
  font-weight: 700;
  cursor: pointer;
  list-style: none;
}

.mode-dropdown summary::-webkit-details-marker {
  display: none;
}

.caret {
  color: #64748b;
}

.mode-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 20;
  min-width: 132px;
  padding: 6px;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 16px 34px rgba(15, 23, 42, .16);
}

.mode-menu button {
  display: block;
  width: 100%;
  border: none;
  border-radius: 6px;
  padding: 8px 10px;
  background: transparent;
  color: #334155;
  text-align: left;
  cursor: pointer;
}

.mode-menu button:hover,
.mode-menu button.active {
  background: rgba(33, 133, 208, .1);
  color: #1e70bf;
}

.search-button {
  flex: 0 0 auto;
  border: none;
  border-radius: 0 5px 5px 0;
  padding: 0 18px;
  background: #2185d0;
  color: #fff;
  font-weight: 800;
  cursor: pointer;
}

.search-button:disabled {
  opacity: .72;
  cursor: wait;
}

.inline-error,
.message.error {
  margin-bottom: 8px;
  max-width: 600px;
  border-radius: 6px;
  padding: 8px 10px;
  background: #fff6f6;
  color: #9f3a38;
  border: 1px solid #e0b4b4;
}

.message.error {
  margin-top: 14px;
}

.keyword-results {
  margin-top: 24px;
  max-width: 980px;
}

.result-count {
  margin-bottom: 12px;
  color: #767676;
  font-size: 14px;
  font-weight: 700;
}

.placeholder-state {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 220px;
  border: 1px solid rgba(34, 36, 38, .12);
  border-radius: 6px;
  background: #f9fafb;
  color: #64748b;
  text-align: center;
}

.placeholder-state strong {
  color: #94a3b8;
  font-size: 38px;
}

.placeholder-state.compact {
  min-height: 160px;
}

.search-items {
  border-top: 1px solid rgba(34, 36, 38, .15);
}

.search-item {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr);
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid rgba(34, 36, 38, .15);
  color: inherit;
  text-decoration: none;
}

.search-item:hover .item-title {
  color: #1e70bf;
}

.item-image {
  width: 80px;
  height: 80px;
  border-radius: 4px;
  overflow: hidden;
  background: #f3f4f6;
}

.item-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.item-title {
  color: #1f2937;
  font-size: 17px;
  font-weight: 800;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.item-desc {
  margin-top: 8px;
  color: #4b5563;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.item-desc span + span::before {
  content: ' ';
}

.item-title :deep(em),
.item-desc :deep(em) {
  color: red;
  font-style: normal;
  font-weight: 900;
}

.table-search-area {
  position: relative;
  min-height: calc(100vh - 148px);
}

.table-empty {
  position: fixed;
  top: 190px;
  left: 50%;
  z-index: 4;
  display: grid;
  place-items: center;
  gap: 8px;
  width: 420px;
  max-width: calc(100% - 32px);
  min-height: 150px;
  transform: translateX(-50%);
  border: 1px solid rgba(34, 36, 38, .12);
  border-radius: 6px;
  background: rgba(255, 255, 255, .95);
  color: #64748b;
  text-align: center;
  box-shadow: 0 18px 40px rgba(15, 23, 42, .12);
}

.table-empty strong {
  color: #94a3b8;
  font-size: 38px;
}

.table-empty.floating {
  pointer-events: none;
}

.graph-canvas {
  position: fixed;
  inset: 70px 0 0;
  z-index: 1;
  overflow: hidden;
  background:
    linear-gradient(rgba(15, 23, 42, .04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, .04) 1px, transparent 1px),
    #f7f7f7;
  background-size: 34px 34px;
}

.table-graph {
  width: 100%;
  height: 100%;
  min-height: calc(100vh - 70px);
}

.graph-edge {
  color: #94a3b8;
}

.graph-edge line {
  stroke: currentColor;
  stroke-width: 1.6;
}

.graph-edge text {
  fill: currentColor;
  font-size: 14px;
  font-family: serif;
  paint-order: stroke;
  stroke: #f7f7f7;
  stroke-width: 5px;
}

.graph-edge.insert { color: green; }
.graph-edge.update { color: orange; }
.graph-edge.delete { color: red; }
.graph-edge.select { color: #64748b; }

.graph-node {
  color: inherit;
  text-decoration: none;
  cursor: pointer;
}

.graph-node circle {
  fill: #bfbfbf;
  stroke: #999;
  stroke-width: 1.5;
  transition: stroke .12s ease, stroke-width .12s ease;
}

.graph-node:hover circle {
  stroke: dodgerblue;
  stroke-width: 2;
}

.graph-node.snapshot circle {
  fill: #fff;
}

.graph-node.table circle {
  fill: #99ccff;
}

.graph-node text {
  fill: #111827;
  font-size: 13px;
  font-weight: 400;
  paint-order: stroke;
  stroke: #f7f7f7;
  stroke-width: 4px;
}

.graph-node image {
  clip-path: circle(25px at 25px 25px);
  pointer-events: none;
}

.relation-list {
  position: fixed;
  right: 18px;
  bottom: 18px;
  z-index: 5;
  display: grid;
  gap: 8px;
  width: min(420px, calc(100vw - 36px));
  max-height: 34vh;
  overflow: auto;
  padding: 10px;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 10px;
  background: rgba(255, 255, 255, .94);
  box-shadow: 0 18px 40px rgba(15, 23, 42, .16);
}

.relation-head {
  color: #334155;
  font-weight: 800;
}

.relation-row {
  display: grid;
  gap: 2px;
  border-left: 3px solid #94a3b8;
  padding: 7px 8px;
  background: #f8fafc;
  color: #475569;
}

.relation-row strong {
  color: #334155;
}

.relation-row.insert { border-left-color: green; }
.relation-row.update { border-left-color: orange; }
.relation-row.delete { border-left-color: red; }

@media (max-width: 767px) {
  .search-page {
    padding: 12px 10px;
  }

  .action-input {
    width: 100%;
    min-width: 0;
  }

  .search-item {
    grid-template-columns: 56px minmax(0, 1fr);
  }

  .item-image {
    width: 56px;
    height: 56px;
  }

  .graph-canvas {
    inset-top: 108px;
  }
}
</style>
