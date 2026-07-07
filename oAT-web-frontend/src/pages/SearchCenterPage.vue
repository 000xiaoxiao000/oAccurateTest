<template>
  <section :class="['search-page', mode === 'table' && 'table-mode']">
    <form class="search-form" @submit.prevent="submitSearch">
      <div v-if="mode === 'table' && error" class="inline-error">{{ error }}</div>
      <div class="action-input">
        <input
          ref="searchInputRef"
          v-model.trim="searchText"
          type="search"
          aria-label="搜索中心关键词"
          :maxlength="mode === 'table' ? 30 : 120"
          :placeholder="mode === 'table' ? '格式：数据库名 表名' : '搜索...'
          "
          @keydown.enter.prevent="submitSearch"
        />
        <div class="mode-dropdown" tabindex="0">
          <button class="mode-trigger" type="button" aria-haspopup="true">
            <span>{{ modeLabel }}</span>
            <span class="caret">⌄</span>
          </button>
          <div class="mode-menu">
            <button type="button" :class="{ active: mode === 'usecase' }" @click="switchMode('usecase')">用例</button>
            <button type="button" :class="{ active: mode === 'mySnapshot' }" @click="switchMode('mySnapshot')">我的快照</button>
            <button type="button" :class="{ active: mode === 'systemSnapshot' }" @click="switchMode('systemSnapshot')">系统快照</button>
            <button type="button" :class="{ active: mode === 'table' }" @click="switchMode('table')">表结构图</button>
          </div>
        </div>
        <button class="search-button" type="submit" :disabled="activeLoading">{{ activeLoading ? '搜索中...' : '搜索' }}</button>
      </div>
    </form>

    <div v-if="isKeywordMode && error" class="message error">{{ error }}</div>

    <section v-if="isKeywordMode" class="keyword-results">
      <div v-if="!keywordSearched" class="placeholder-state">
        <strong>⌕</strong>
        <span>{{ keywordPlaceholder }}</span>
      </div>
      <template v-else>
        <div class="result-count">为您找到：{{ keywordResults?.total || 0 }} 条结果</div>
        <div v-if="!keywordResults?.results?.length" class="placeholder-state compact">
          <strong>⌕</strong>
          <span>没有找到匹配结果</span>
        </div>
        <div v-else class="search-items">
          <RouterLink
            v-for="item in paginatedKeywordResults"
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
        <AppPagination
          v-if="keywordResultItems.length > keywordPageSize"
          v-model:page="keywordPage"
          v-model:page-size="keywordPageSize"
          :total="keywordResultItems.length"
          item-name="条结果"
        />
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
        <div
          class="graph-canvas"
          @wheel.prevent="handleTableWheel"
          @pointerdown="startTablePan"
          @pointermove="moveTablePan"
          @pointerup="endTablePan"
          @pointerleave="endTablePan"
          @click="closeTableContextMenu"
          @contextmenu.prevent="openTableCanvasMenu"
        >
          <div class="table-graph-tools">
            <span>滚轮缩放 · 拖拽画布 · 拖拽节点</span>
            <button type="button" @click="zoomTableGraph(0.15)">放大</button>
            <button type="button" @click="zoomTableGraph(-0.15)">缩小</button>
            <button type="button" @click="resetTableGraphLayout">重排</button>
            <button type="button" @click="resetTableGraphView">重置</button>
          </div>
          <svg ref="tableGraphRef" class="table-graph" :viewBox="tableViewBox" role="img" aria-label="表结构关系图">
            <defs>
              <marker id="search-graph-arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
                <path d="M0,0 L0,6 L9,3 z" fill="currentColor" />
              </marker>
            </defs>
            <g :transform="tableGraphTransform">
              <g class="edge-layer">
                <g v-for="edge in positionedEdges" :key="edge.id" :class="['graph-edge', edgeTone(edge.action)]">
                  <line :x1="edge.source.x" :y1="edge.source.y" :x2="edge.target.x" :y2="edge.target.y" marker-end="url(#search-graph-arrow)" />
                  <text :x="edge.labelX" :y="edge.labelY">{{ edge.label || actionLabel(edge.action) || '关联' }}</text>
                </g>
              </g>
              <g class="node-layer">
                <g
                  v-for="node in positionedNodes"
                  :key="node.id"
                  :class="['graph-node', node.type || 'node']"
                  @pointerdown.stop="startTableNodeDrag($event, node)"
                  @click.stop="openTableNode(node)"
                  @contextmenu.prevent.stop="openTableNodeMenu($event, node)"
                >
                  <title>{{ node.label || node.id }}</title>
                  <circle :cx="node.x" :cy="node.y" :r="node.type === 'table' ? 38 : 34" />
                  <text v-if="node.type === 'table'" :x="node.x" :y="node.y + 5" class="table-symbol" text-anchor="middle">表</text>
                  <image v-if="node.type === 'snapshot' && node.backgroundImage" :href="node.backgroundImage" :x="node.x - 26" :y="node.y - 26" width="52" height="52" preserveAspectRatio="xMidYMid slice" />
                  <text :x="node.x" :y="node.y + 58" text-anchor="middle">{{ node.label || node.id }}</text>
                </g>
              </g>
            </g>
          </svg>
          <div v-if="tableContextMenu.open" class="table-context-menu" :style="{ left: `${tableContextMenu.x}px`, top: `${tableContextMenu.y}px` }" @click.stop>
            <template v-if="tableContextNode">
              <button v-if="nodeHref(tableContextNode)" type="button" @click="openTableNodeFromMenu">打开快照详情</button>
              <button type="button" @click="showTableNodeTip">节点概要</button>
            </template>
            <template v-else>
              <button type="button" @click="focusSearchInput">查找 <small>Ctrl+F</small></button>
              <button type="button" @click="resetTableGraphView">适配视图</button>
              <button type="button" @click="resetTableGraphLayout">重排图谱</button>
            </template>
          </div>
          <div v-if="tableNodeTip.open && tableContextNode" class="table-node-tip" @click.stop="tableNodeTip.open = false">
            <strong>{{ tableContextNode.label || tableContextNode.id }}</strong>
            <span>ID：{{ tableContextNode.id }}</span>
            <span>类型：{{ tableContextNode.type || 'node' }}</span>
            <span>关联关系：{{ tableContextEdges.length }} 条</span>
          </div>
        </div>
        <aside v-if="tableGraph?.edges.length" class="relation-list">
          <div class="relation-head">
            <span>{{ tableGraph.nodes.length }} 个节点 / {{ tableGraph.edges.length }} 条关系</span>
            <button v-if="tableGraph.edges.length > relationPreviewLimit" class="relation-toggle" type="button" @click="relationExpanded = !relationExpanded">
              {{ relationExpanded ? '收起' : '展开全部' }}
            </button>
          </div>
          <article v-for="edge in visibleTableEdges" :key="edge.id" :class="['relation-row', edgeTone(edge.action)]">
            <strong>{{ edge.label || actionLabel(edge.action) || '关联' }}</strong>
            <span>{{ nodeLabel(edge.source) }} → {{ nodeLabel(edge.target) }}</span>
          </article>
        </aside>
      </template>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { searchKeyword, searchTableGraph } from '@/api/bootstrap'
import type { NetworkGraphEdge, NetworkGraphNode, SearchKeywordResult } from '@/api/types'
import AppPagination from '@/components/AppPagination.vue'

type SearchMode = 'usecase' | 'mySnapshot' | 'systemSnapshot' | 'table'

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
const mode = ref<SearchMode>(initialMode())
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
const tableGraphZoom = ref(1)
const tableGraphOffset = ref({ x: 0, y: 0 })
const tableGraphPan = ref<{ startX: number; startY: number; originX: number; originY: number } | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)
const tableGraphRef = ref<SVGSVGElement | null>(null)
const tableNodeOverrides = ref<Record<string, { x: number; y: number }>>({})
const tableNodeDrag = ref<{ id: string; startX: number; startY: number; originX: number; originY: number; moved: boolean } | null>(null)
const suppressTableNodeClick = ref(false)
const tableContextMenu = reactive({ open: false, x: 0, y: 0, nodeId: '' })
const tableNodeTip = reactive({ open: false })
const tableCanvas = { width: 1400, height: 780 }
const keywordPage = ref(1)
const keywordPageSize = ref(10)
const relationExpanded = ref(false)
const relationPreviewLimit = 12

const isKeywordMode = computed(() => mode.value !== 'table')
const modeLabel = computed(() => {
  if (mode.value === 'table') return '表结构图'
  if (mode.value === 'mySnapshot') return '我的快照'
  if (mode.value === 'systemSnapshot') return '系统快照'
  return '用例'
})
const keywordPlaceholder = computed(() => {
  if (mode.value === 'mySnapshot') return '输入关键词搜索我的快照'
  if (mode.value === 'systemSnapshot') return '输入关键词搜索系统快照、SQL 或远程调用'
  return '输入关键词搜索测试用例'
})
const activeLoading = computed(() => (isKeywordMode.value ? loadingKeyword.value : loadingTable.value))
const tableViewBox = computed(() => `0 0 ${tableCanvas.width} ${tableCanvas.height}`)
const tableGraphTransform = computed(() => `translate(${tableGraphOffset.value.x} ${tableGraphOffset.value.y}) scale(${tableGraphZoom.value})`)

const positionedNodes = computed<PositionedNode[]>(() => {
  const nodes = tableGraph.value?.nodes || []
  const tableNode = nodes.find((node) => node.type === 'table') || nodes[nodes.length - 1]
  const snapshots = nodes.filter((node) => node.id !== tableNode?.id)
  const centerX = 720
  const centerY = 390
  const radiusX = snapshots.length > 10 ? 560 : 470
  const radiusY = snapshots.length > 10 ? 300 : 260
  const positioned = snapshots.map((node, index) => {
    const angle = snapshots.length <= 1 ? Math.PI : Math.PI * 0.2 + (index * Math.PI * 1.6) / Math.max(snapshots.length - 1, 1)
    const autoPosition = {
      x: centerX - Math.cos(angle) * radiusX,
      y: centerY + Math.sin(angle) * radiusY,
    }
    const override = tableNodeOverrides.value[node.id]
    return {
      ...node,
      x: override?.x ?? autoPosition.x,
      y: override?.y ?? autoPosition.y,
    }
  })
  if (tableNode) {
    const override = tableNodeOverrides.value[tableNode.id]
    positioned.push({ ...tableNode, x: override?.x ?? centerX, y: override?.y ?? centerY })
  }
  return positioned
})

const tableContextNode = computed(() => positionedNodes.value.find((node) => node.id === tableContextMenu.nodeId) || null)
const tableContextEdges = computed(() =>
  positionedEdges.value.filter((edge) => edge.source.id === tableContextMenu.nodeId || edge.target.id === tableContextMenu.nodeId),
)
const keywordResultItems = computed(() => keywordResults.value?.results || [])
const paginatedKeywordResults = computed(() => {
  const start = (keywordPage.value - 1) * keywordPageSize.value
  return keywordResultItems.value.slice(start, start + keywordPageSize.value)
})
const visibleTableEdges = computed(() => {
  const edges = tableGraph.value?.edges || []
  return relationExpanded.value ? edges : edges.slice(0, relationPreviewLimit)
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
watch(tableGraph, () => {
  resetTableGraphLayout()
  resetTableGraphView()
  relationExpanded.value = false
})
watch([keywordResults, keywordPageSize], () => {
  keywordPage.value = 1
})

function handleSearchKeydown(event: KeyboardEvent) {
  if (event.ctrlKey && event.key.toLowerCase() === 'f') {
    event.preventDefault()
    focusSearchInput()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleSearchKeydown)
  if (isKeywordMode.value && keyword.value) {
    submitKeywordSearch()
  } else if (mode.value === 'table' && (tableKeyword.value || (database.value && table.value))) {
    submitTableSearch()
  }
})

onBeforeUnmount(() => window.removeEventListener('keydown', handleSearchKeydown))

function initialTableText() {
  if (tableKeyword.value) return tableKeyword.value
  return [database.value, table.value].filter(Boolean).join(' ')
}

function initialMode(): SearchMode {
  const tab = String(route.query.tab || '')
  if (tab === 'table' || tab === 'mySnapshot' || tab === 'systemSnapshot' || tab === 'usecase') return tab
  return 'usecase'
}

function syncTextFromMode() {
  searchText.value = isKeywordMode.value ? keyword.value : initialTableText()
  error.value = ''
}

function clampTableZoom(value: number) {
  return Math.min(3, Math.max(0.5, value))
}

function zoomTableGraph(delta: number) {
  tableGraphZoom.value = clampTableZoom(tableGraphZoom.value + delta)
}

function resetTableGraphView() {
  tableGraphZoom.value = 1
  tableGraphOffset.value = { x: 0, y: 0 }
  closeTableContextMenu()
}

function resetTableGraphLayout() {
  tableNodeOverrides.value = {}
  closeTableContextMenu()
}

function handleTableWheel(event: WheelEvent) {
  zoomTableGraph(event.deltaY > 0 ? -0.1 : 0.1)
}

function graphPointerDelta(event: PointerEvent) {
  const rect = tableGraphRef.value?.getBoundingClientRect()
  const scaleX = rect?.width ? tableCanvas.width / rect.width : 1
  const scaleY = rect?.height ? tableCanvas.height / rect.height : 1
  return { scaleX, scaleY }
}

function startTablePan(event: PointerEvent) {
  if ((event.target as Element).closest('.table-graph-tools, .graph-node')) return
  tableGraphPan.value = {
    startX: event.clientX,
    startY: event.clientY,
    originX: tableGraphOffset.value.x,
    originY: tableGraphOffset.value.y,
  }
}

function moveTablePan(event: PointerEvent) {
  if (tableNodeDrag.value) {
    const { scaleX, scaleY } = graphPointerDelta(event)
    const dx = (event.clientX - tableNodeDrag.value.startX) * scaleX / tableGraphZoom.value
    const dy = (event.clientY - tableNodeDrag.value.startY) * scaleY / tableGraphZoom.value
    if (Math.abs(event.clientX - tableNodeDrag.value.startX) > 3 || Math.abs(event.clientY - tableNodeDrag.value.startY) > 3) {
      tableNodeDrag.value.moved = true
    }
    tableNodeOverrides.value = {
      ...tableNodeOverrides.value,
      [tableNodeDrag.value.id]: {
        x: tableNodeDrag.value.originX + dx,
        y: tableNodeDrag.value.originY + dy,
      },
    }
    return
  }
  if (!tableGraphPan.value) return
  const { scaleX, scaleY } = graphPointerDelta(event)
  tableGraphOffset.value = {
    x: tableGraphPan.value.originX + (event.clientX - tableGraphPan.value.startX) * scaleX / tableGraphZoom.value,
    y: tableGraphPan.value.originY + (event.clientY - tableGraphPan.value.startY) * scaleY / tableGraphZoom.value,
  }
}

function endTablePan() {
  tableGraphPan.value = null
  if (tableNodeDrag.value?.moved) {
    suppressTableNodeClick.value = true
  }
  tableNodeDrag.value = null
}

function startTableNodeDrag(event: PointerEvent, node: PositionedNode) {
  tableNodeDrag.value = {
    id: node.id,
    startX: event.clientX,
    startY: event.clientY,
    originX: node.x,
    originY: node.y,
    moved: false,
  }
}

function openTableNode(node: PositionedNode) {
  if (suppressTableNodeClick.value) {
    suppressTableNodeClick.value = false
    return
  }
  const href = nodeHref(node)
  if (href) {
    window.open(href, '_blank', 'noopener')
  }
}

function tableMenuPosition(event: MouseEvent) {
  const rect = (event.currentTarget as Element)
    .closest('.graph-canvas')
    ?.getBoundingClientRect()
  return {
    x: rect ? event.clientX - rect.left : event.offsetX,
    y: rect ? event.clientY - rect.top : event.offsetY,
  }
}

function openTableCanvasMenu(event: MouseEvent) {
  if ((event.target as Element).closest('.graph-node, .table-graph-tools, .table-context-menu, .table-node-tip')) return
  const position = tableMenuPosition(event)
  tableContextMenu.open = true
  tableContextMenu.x = position.x
  tableContextMenu.y = position.y
  tableContextMenu.nodeId = ''
  tableNodeTip.open = false
}

function openTableNodeMenu(event: MouseEvent, node: PositionedNode) {
  const position = tableMenuPosition(event)
  tableContextMenu.open = true
  tableContextMenu.x = position.x
  tableContextMenu.y = position.y
  tableContextMenu.nodeId = node.id
  tableNodeTip.open = false
}

function closeTableContextMenu() {
  tableContextMenu.open = false
}

function openTableNodeFromMenu() {
  if (tableContextNode.value) {
    openTableNode(tableContextNode.value)
  }
  closeTableContextMenu()
}

function showTableNodeTip() {
  tableNodeTip.open = true
  closeTableContextMenu()
}

function focusSearchInput() {
  searchInputRef.value?.focus()
  closeTableContextMenu()
}

function switchMode(next: SearchMode) {
  mode.value = next
  syncTextFromMode()
  if (next !== 'table') {
    keywordResults.value = null
    keywordSearched.value = false
  }
  updateQuery({ tab: next === 'usecase' ? undefined : next })
}

function submitSearch() {
  if (isKeywordMode.value) {
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
    const searchType = mode.value
    if (searchType === 'table') return
    keywordResults.value = await searchKeyword(projectId.value, value, searchType)
    keywordPage.value = 1
    updateQuery({ keyword: value, tab: searchType === 'usecase' ? undefined : searchType, q: undefined, database: undefined, table: undefined, tableKeyword: undefined })
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
  if (!node.appId) return undefined
  return `/p/${projectId.value}/apps/${node.appId}/snapshots/${node.id}`
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

<style scoped src="@/features/search/styles/search-center-page.css"></style>
