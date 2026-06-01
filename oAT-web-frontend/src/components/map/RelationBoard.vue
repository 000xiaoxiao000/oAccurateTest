<template>
  <section :class="['relation-board', compact && 'compact-board']">
    <div v-if="!compact" class="page-header">
      <div>
        <div class="eyebrow">{{ eyebrow }}</div>
        <h1>{{ title }}</h1>
        <p v-if="subtext" class="subtext">{{ subtext }}</p>
      </div>
      <div v-if="backRoute" class="header-actions">
        <RouterLink class="secondary-link" :to="backRoute">{{ backLabel || '返回' }}</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">{{ loadingText || '正在加载...' }}</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else>
      <div v-if="!compact" class="summary-grid">
        <article class="summary-card">
          <span>节点</span>
          <strong>{{ filteredNodes.length }}</strong>
        </article>
        <article class="summary-card">
          <span>关系</span>
          <strong>{{ filteredEdges.length }}</strong>
        </article>
      </div>

      <div v-if="!compact || compactSearchOpen || keyword" :class="['graph-search-block', compact && 'compact-search-block']">
        <label class="search-box">
          <span>图谱搜索</span>
              <input
                ref="searchInputRef"
                v-model.trim="keyword"
                class="text-input"
                type="search"
                placeholder="输入名称、类型或描述，自动定位节点"
                aria-label="搜索图谱节点"
                @keydown.down.prevent="selectSearchMatch(searchMatches[0]?.id)"
              />
        </label>
        <div v-if="keyword" class="graph-search-results">
          <button
            v-for="node in searchMatches.slice(0, 8)"
            :key="node.id"
            type="button"
            :class="{ active: selectedNode?.id === node.id }"
            @click="selectSearchMatch(node.id)"
          >
            <strong>{{ node.label || node.id }}</strong>
            <span>{{ node.type || 'node' }}</span>
          </button>
          <div v-if="!searchMatches.length" class="empty-search-result">没有匹配节点</div>
        </div>
      </div>

      <div :class="['board-workspace', hideLists && 'graph-only']">
        <section
          class="graph-panel"
          @click="closeContextMenu"
          @wheel.prevent="handleGraphWheel"
          @pointerdown="startBoardPan"
          @pointermove="moveBoardPan"
          @pointerup="endBoardPan"
          @pointerleave="endBoardPan"
          @contextmenu.prevent="openCanvasContextMenu"
        >
          <div class="graph-toolbar">
            <div>图形画布</div>
            <div class="graph-tools">
              <button type="button" @click.stop="openSearchPanel">查找</button>
              <button type="button" @click.stop="fitGraph">适配视图</button>
              <button type="button" @click.stop="zoomGraph(0.15)">放大</button>
              <button type="button" @click.stop="zoomGraph(-0.15)">缩小</button>
              <button type="button" @click.stop="resetGraphLayout">重排</button>
              <button type="button" @click.stop="showSelectedTip" :disabled="!selectedNode">节点提示</button>
            </div>
          </div>
          <svg ref="relationGraphRef" class="relation-graph" :viewBox="graphViewBox" role="img" aria-label="关系图画布">
          <defs>
            <marker id="graph-arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
              <path d="M0,0 L0,6 L9,3 z" fill="#64748b" />
            </marker>
          </defs>
          <g :transform="graphTransform">
            <g class="edge-layer">
              <g v-for="edge in graphEdges" :key="edge.id" :class="['graph-edge', edgeTone(edge), edgeRelated(edge) && 'related', edgeDimmed(edge) && 'dimmed']">
                <path :d="edge.path" marker-end="url(#graph-arrow)" />
                <text v-if="showEdgeLabel(edge)" :x="edge.labelX" :y="edge.labelY">{{ edge.label || actionText(edge.action) || '关联' }}</text>
              </g>
            </g>
            <g class="node-layer">
              <g
                v-for="node in graphNodes"
                :key="node.id"
                :class="['graph-node', nodeTone(node), selectedNode?.id === node.id && 'active', searchMatchIds.has(node.id) && 'find-match', nodeRelated(node) && 'related', nodeDimmed(node) && 'dimmed']"
                :transform="`translate(${node.x}, ${node.y})`"
                @pointerdown.stop="startNodeDrag($event, node)"
                @click.stop="selectGraphNode(node.id)"
                @contextmenu.prevent.stop="openContextMenu($event, node.id)"
              >
                <circle :r="node.radius" />
                <text v-if="showNodeLabel(node)" class="node-label" text-anchor="middle" :y="node.radius + 16">{{ node.shortLabel }}</text>
                <text v-if="node.metric" class="node-metric" text-anchor="middle" y="5">{{ node.metric }}</text>
              </g>
            </g>
          </g>
          </svg>
          <div v-if="contextMenu.open && visibleContextActions.length" class="graph-context-menu" :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }" @click.stop>
          <button
            v-for="action in visibleContextActions"
            :key="`${action.id}-${action.target || 'node'}`"
            type="button"
            :class="{ danger: action.danger }"
            :disabled="action.disabled"
            @click="runContextAction(action)"
          >
            <span>{{ action.label }}</span>
            <small v-if="action.shortcut">{{ action.shortcut }}</small>
          </button>
          </div>
          <div v-if="tip.open && selectedNode" class="graph-tip" @click.stop="tip.open = false">
          <strong>{{ selectedNode.label || selectedNode.id }}</strong>
          <span class="tip-line">ID：{{ selectedNode.id }}</span>
          <span class="tip-line">类型：{{ selectedNode.type || 'node' }}</span>
          <span v-if="selectedNode.description" class="tip-line">描述：{{ selectedNode.description }}</span>
          <ul v-if="selectedNode.meta?.length" class="tip-meta">
            <li v-for="item in selectedNode.meta" :key="item">{{ item }}</li>
          </ul>
          <span class="tip-line">关联关系：{{ selectedEdges.length }} 条</span>
          </div>
        </section>

        <aside v-if="!hideLists" class="board-side">
          <div class="layout-grid compact-lists">
            <section class="panel node-list-panel">
              <div class="panel-head">
                <h2>节点列表</h2>
                <span>{{ filteredNodes.length }}</span>
              </div>
              <div v-if="!filteredNodes.length" class="empty-card">暂无节点数据</div>
              <div v-else class="node-grid">
                <button
                  v-for="node in paginatedNodes"
                  :key="node.id"
                  type="button"
                  class="node-card"
                  :class="{ active: selectedNode?.id === node.id }"
                  @click="selectGraphNode(node.id)"
                >
                  <div class="node-top">
                    <strong>{{ node.label || node.id }}</strong>
                    <span class="type-pill">{{ node.type || 'node' }}</span>
                  </div>
                  <p v-if="node.description" class="node-desc">{{ node.description }}</p>
                  <ul v-if="node.meta?.length" class="meta-list">
                    <li v-for="item in node.meta" :key="item">{{ item }}</li>
                  </ul>
                </button>
              </div>
              <div v-if="nodeTotalPages > 1" class="mini-pagination" aria-label="节点分页">
                <button type="button" :disabled="nodePage <= 1" @click="nodePage -= 1">上一页</button>
                <span>{{ nodePage }} / {{ nodeTotalPages }}</span>
                <button type="button" :disabled="nodePage >= nodeTotalPages" @click="nodePage += 1">下一页</button>
              </div>
            </section>

            <section class="panel node-detail-panel">
              <div class="panel-head">
                <h2>节点详情</h2>
                <span>{{ selectedNode?.type || '-' }}</span>
              </div>
              <div v-if="selectedNode" class="detail-card">
                <h3>{{ selectedNode.label || selectedNode.id }}</h3>
                <p class="detail-id">{{ selectedNode.id }}</p>
                <p v-if="selectedNode.description" class="node-desc">{{ selectedNode.description }}</p>
                <ul v-if="selectedNode.meta?.length" class="meta-list">
                  <li v-for="item in selectedNode.meta" :key="item">{{ item }}</li>
                </ul>
                <div class="connection-group">
                  <strong>关联关系</strong>
                  <div v-if="!selectedEdges.length" class="empty-inline">该节点暂无关系</div>
                  <div v-else class="edge-list">
                    <article v-for="edge in visibleSelectedEdges" :key="edge.id" :class="['edge-card', edgeTone(edge)]">
                      <span>{{ edge.sourceLabel || edge.source }}</span>
                      <strong>{{ edge.label || actionText(edge.action) || '关联' }}</strong>
                      <span>{{ edge.targetLabel || edge.target }}</span>
                    </article>
                  </div>
                  <button v-if="selectedEdges.length > selectedEdgePreviewLimit" class="inline-more-button" type="button" @click="showAllSelectedEdges = !showAllSelectedEdges">
                    {{ showAllSelectedEdges ? '收起关系' : `查看全部 ${selectedEdges.length} 条` }}
                  </button>
                </div>
              </div>
              <div v-else class="empty-card">选择一个节点后可查看详细关系</div>
            </section>

            <section class="panel edge-panel">
              <div class="panel-head">
                <h2>关系清单</h2>
                <span>{{ filteredEdges.length }}</span>
              </div>
              <div v-if="!filteredEdges.length" class="empty-card">暂无关系数据</div>
              <div v-else class="table-shell">
                <table class="edge-table">
                  <thead>
                    <tr>
                      <th>来源</th>
                      <th>关系</th>
                      <th>目标</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="edge in paginatedEdges" :key="edge.id">
                      <td>{{ edge.sourceLabel || edge.source }}</td>
                      <td><span :class="['action-pill', edgeTone(edge)]">{{ edge.label || actionText(edge.action) || '-' }}</span></td>
                      <td>{{ edge.targetLabel || edge.target }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-if="edgeTotalPages > 1" class="mini-pagination edge-pagination" aria-label="关系分页">
                <button type="button" :disabled="edgePage <= 1" @click="edgePage -= 1">上一页</button>
                <span>显示 {{ edgeFirstItem }}–{{ edgeLastItem }} / 共 {{ filteredEdges.length }} 条</span>
                <button type="button" :disabled="edgePage >= edgeTotalPages" @click="edgePage += 1">下一页</button>
              </div>
            </section>
          </div>
        </aside>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch, watchEffect } from 'vue'
import { RouterLink } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

interface RelationNode {
  id: string
  label?: string
  type?: string
  description?: string
  meta?: string[]
  raw?: Record<string, unknown>
  classes?: string[]
}

interface RelationEdge {
  id: string
  source: string
  target: string
  label?: string
  action?: string
  sourceLabel?: string
  targetLabel?: string
}

interface RelationContextAction {
  id: string
  label: string
  target?: 'canvas' | 'node' | 'app' | 'snapshot' | 'table' | 'remote' | 'code'
  shortcut?: string
  danger?: boolean
  disabled?: boolean
}

interface GraphNode extends RelationNode {
  x: number
  y: number
  radius: number
  shortLabel: string
  metric: string
}

const props = defineProps<{
  eyebrow: string
  title: string
  subtext?: string
  loading: boolean
  loadingText?: string
  error?: string
  nodes: RelationNode[]
  edges: RelationEdge[]
  backRoute?: RouteLocationRaw
  backLabel?: string
  compact?: boolean
  hideLists?: boolean
  contextActions?: RelationContextAction[]
  showEdgeLabels?: boolean
  highlightRelated?: boolean
}>()

const emit = defineEmits<{
  (event: 'node-select', node: RelationNode | null): void
  (event: 'context-action', actionId: string, node: RelationNode | null): void
}>()

const compact = computed(() => Boolean(props.compact))
const hideLists = computed(() => Boolean(props.hideLists))

const keyword = ref('')
const compactSearchOpen = ref(false)
const selectedId = ref('')
const nodePage = ref(1)
const edgePage = ref(1)
const nodePageSize = 8
const edgePageSize = 10
const selectedEdgePreviewLimit = 8
const showAllSelectedEdges = ref(false)
const searchInputRef = ref<HTMLInputElement | null>(null)
const contextMenu = reactive({ open: false, x: 0, y: 0, nodeId: '', target: 'canvas' as 'canvas' | 'node' })
const tip = reactive({ open: false })
const relationGraphRef = ref<SVGSVGElement | null>(null)
const graphZoom = ref(1)
const graphOffset = ref({ x: 0, y: 0 })
const graphPan = ref<{ startX: number; startY: number; originX: number; originY: number } | null>(null)
const nodePositionOverrides = ref<Record<string, { x: number; y: number }>>({})
const nodeDrag = ref<{ id: string; startX: number; startY: number; originX: number; originY: number } | null>(null)


const filteredNodes = computed(() => {
  if (!keyword.value) {
    return props.nodes
  }
  const needle = keyword.value.toLowerCase()
  return props.nodes.filter((node) =>
    [node.id, node.label, node.type, node.description, ...(node.meta || [])]
      .filter(Boolean)
      .some((item) => String(item).toLowerCase().includes(needle)),
  )
})

const filteredEdges = computed(() => {
  const allowed = new Set(filteredNodes.value.map((node) => node.id))
  return props.edges.filter((edge) => allowed.has(edge.source) || allowed.has(edge.target))
})

const nodeTotalPages = computed(() => Math.max(1, Math.ceil(filteredNodes.value.length / nodePageSize)))
const paginatedNodes = computed(() => {
  const start = (nodePage.value - 1) * nodePageSize
  return filteredNodes.value.slice(start, start + nodePageSize)
})

const edgeTotalPages = computed(() => Math.max(1, Math.ceil(filteredEdges.value.length / edgePageSize)))
const paginatedEdges = computed(() => {
  const start = (edgePage.value - 1) * edgePageSize
  return filteredEdges.value.slice(start, start + edgePageSize)
})
const edgeFirstItem = computed(() => (filteredEdges.value.length ? (edgePage.value - 1) * edgePageSize + 1 : 0))
const edgeLastItem = computed(() => Math.min(filteredEdges.value.length, edgePage.value * edgePageSize))

const selectedNode = computed(() => filteredNodes.value.find((node) => node.id === selectedId.value))
const selectedEdges = computed(() =>
  filteredEdges.value.filter((edge) => edge.source === selectedId.value || edge.target === selectedId.value),
)
const visibleSelectedEdges = computed(() => showAllSelectedEdges.value ? selectedEdges.value : selectedEdges.value.slice(0, selectedEdgePreviewLimit))
const relatedNodeIds = computed(() => {
  const ids = new Set<string>()
  if (!selectedId.value) return ids
  ids.add(selectedId.value)
  filteredEdges.value.forEach((edge) => {
    if (edge.source === selectedId.value) ids.add(edge.target)
    if (edge.target === selectedId.value) ids.add(edge.source)
  })
  return ids
})
const relatedEdgeIds = computed(() => {
  if (!selectedId.value) return new Set<string>()
  return new Set(filteredEdges.value
    .filter((edge) => edge.source === selectedId.value || edge.target === selectedId.value)
    .map((edge) => edge.id))
})
const searchMatches = computed(() => {
  const needle = keyword.value.trim().toLowerCase()
  if (!needle) return []
  return props.nodes.filter((node) => nodeSearchText(node).includes(needle))
})
const searchMatchIds = computed(() => new Set(searchMatches.value.map((node) => node.id)))
const contextMenuNode = computed(() => filteredNodes.value.find((node) => node.id === contextMenu.nodeId) || null)
const builtInContextActions = computed<RelationContextAction[]>(() => {
  if (contextMenu.target === 'canvas') {
    return [
      { id: 'find', label: '查找', target: 'canvas', shortcut: 'Ctrl+F' },
      { id: 'fit', label: '适配视图', target: 'canvas' },
      { id: 'relayout', label: '重排图谱', target: 'canvas' },
    ]
  }
  return [{ id: 'tip', label: '节点概要', target: 'node', shortcut: 'F2' }]
})
const visibleContextActions = computed(() =>
  [...(props.contextActions || []), ...builtInContextActions.value]
    .filter(actionMatchesContext)
)

const layoutSourceNodes = computed(() => filteredNodes.value)
const layoutSourceEdges = computed(() => filteredEdges.value)
const graphLayoutKind = computed(() => resolveGraphLayoutKind(layoutSourceNodes.value))
const graphLayoutPlan = computed(() => buildGraphLayout(layoutSourceNodes.value, layoutSourceEdges.value, graphLayoutKind.value))
const graphCanvas = computed(() => graphLayoutPlan.value.canvas)
const graphViewBox = computed(() => `0 0 ${graphCanvas.value.width} ${graphCanvas.value.height}`)
const graphTransform = computed(() => `translate(${graphOffset.value.x} ${graphOffset.value.y}) scale(${graphZoom.value})`)
const denseGraph = computed(() => layoutSourceNodes.value.length > 70)

const graphNodes = computed<GraphNode[]>(() => {
  const positions = graphLayoutPlan.value.positions
  return layoutSourceNodes.value.map((node) => {
    const autoPosition = positions.get(node.id) || { x: 80, y: 80 }
    const override = nodePositionOverrides.value[node.id]
    const type = node.type || ''
    const isCode = type.includes('code') || type.includes('class') || type.includes('method')
    return {
      ...node,
      x: override?.x ?? autoPosition.x,
      y: override?.y ?? autoPosition.y,
      radius: type.includes('snapshot') ? 27 : isCode ? 18 : type.includes('table') ? 22 : 20,
      shortLabel: shorten(node.label || node.id, denseGraph.value ? 14 : 20),
      metric: Number(node.meta?.join(' ').match(/([0-9]+(?:\.[0-9]+)?)%/)?.[1] || 0) ? `${Number(node.meta?.join(' ').match(/([0-9]+(?:\.[0-9]+)?)%/)?.[1] || 0)}%` : '',
    }
  })
})

const graphNodeMap = computed(() => new Map(graphNodes.value.map((node) => [node.id, node])))
const graphEdges = computed(() => filteredEdges.value
  .map((edge) => {
    const source = graphNodeMap.value.get(edge.source)
    const target = graphNodeMap.value.get(edge.target)
    if (!source || !target) return null
    const labelPoint = edgeLabelPoint(source, target)
    return {
      ...edge,
      source,
      target,
      path: edgePath(source, target),
      labelX: labelPoint.x,
      labelY: labelPoint.y,
    }
  })
  .filter((edge): edge is NonNullable<typeof edge> => Boolean(edge)))

function nodeSearchText(node: RelationNode) {
  return [node.id, node.label, node.type, node.description, ...(node.meta || [])]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
}

function resolveGraphLayoutKind(nodes: RelationNode[]) {
  const codeCount = nodes.filter(isCodeNode).length
  if (nodes.length > 60 || codeCount > Math.max(8, nodes.length * 0.35)) return 'layered'
  if (nodes.some((node) => nodeTypeText(node).includes('snapshot') || nodeTypeText(node).includes('table') || nodeTypeText(node).includes('app'))) return 'layered'
  return 'grid'
}

function nodeTypeText(node: RelationNode) {
  return `${node.type || ''} ${(node.classes || []).join(' ')}`.toLowerCase()
}

function isCodeNode(node: RelationNode) {
  const type = nodeTypeText(node)
  return type.includes('code') || type.includes('class') || type.includes('method')
}

function buildGraphLayout(nodes: RelationNode[], edges: RelationEdge[], kind: string) {
  if (!nodes.length) {
    return { canvas: { width: 1200, height: 680 }, positions: new Map<string, { x: number; y: number }>() }
  }
  return kind === 'grid' ? buildGridLayout(nodes) : buildLayeredLayout(nodes, edges)
}

function buildGridLayout(nodes: RelationNode[]) {
  const count = nodes.length
  const columns = Math.max(1, Math.ceil(Math.sqrt(count * 1.6)))
  const rows = Math.ceil(count / columns)
  const spacingX = 160
  const spacingY = 92
  const canvas = {
    width: Math.max(1200, columns * spacingX + 160),
    height: Math.max(680, rows * spacingY + 160),
  }
  const positions = new Map<string, { x: number; y: number }>()
  nodes.forEach((node, index) => {
    const column = index % columns
    const row = Math.floor(index / columns)
    positions.set(node.id, {
      x: 90 + column * spacingX,
      y: 92 + row * spacingY,
    })
  })
  return { canvas, positions }
}

function buildLayeredLayout(nodes: RelationNode[], edges: RelationEdge[]) {
  const nodeIds = new Set(nodes.map((node) => node.id))
  const incoming = new Map<string, number>()
  const children = new Map<string, string[]>()
  nodes.forEach((node) => {
    incoming.set(node.id, 0)
    children.set(node.id, [])
  })
  edges.forEach((edge) => {
    if (!nodeIds.has(edge.source) || !nodeIds.has(edge.target)) return
    incoming.set(edge.target, (incoming.get(edge.target) || 0) + 1)
    children.get(edge.source)?.push(edge.target)
  })

  const ranks = new Map<string, number>()
  const roots = nodes.filter((node) => (incoming.get(node.id) || 0) === 0)
  const queue = (roots.length ? roots : nodes.slice(0, Math.min(4, nodes.length))).map((node) => node.id)
  queue.forEach((id) => ranks.set(id, 0))
  for (let index = 0; index < queue.length; index += 1) {
    const id = queue[index]
    const nextRank = (ranks.get(id) || 0) + 1
    ;(children.get(id) || []).forEach((childId) => {
      if ((ranks.get(childId) ?? -1) < nextRank) {
        ranks.set(childId, nextRank)
        queue.push(childId)
      }
    })
  }

  nodes.forEach((node, index) => {
    if (!ranks.has(node.id)) ranks.set(node.id, Math.floor(index / 10))
  })

  const buckets = new Map<number, RelationNode[]>()
  nodes.forEach((node) => {
    const rank = ranks.get(node.id) || 0
    const list = buckets.get(rank) || []
    list.push(node)
    buckets.set(rank, list)
  })

  const mostlyCode = nodes.filter(isCodeNode).length > nodes.length * 0.5
  const maxRowsPerColumn = mostlyCode ? (nodes.length > 80 ? 5 : 4) : (nodes.length > 80 ? 7 : 6)
  const maxColumnsPerBand = mostlyCode ? (nodes.length > 80 ? 5 : 6) : (nodes.length > 80 ? 6 : 7)
  const spacingX = mostlyCode ? 230 : 220
  const spacingY = mostlyCode ? 118 : 108
  const bandGap = mostlyCode ? 132 : 118
  const left = 130
  const top = 118
  const positions = new Map<string, { x: number; y: number }>()
  let visualColumn = 0

  Array.from(buckets.keys()).sort((a, b) => a - b).forEach((rank) => {
    const bucket = buckets.get(rank) || []
    const sortedBucket = bucket.slice().sort((leftNode, rightNode) => nodeSortWeight(leftNode) - nodeSortWeight(rightNode) || (leftNode.label || leftNode.id).localeCompare(rightNode.label || rightNode.id))
    const chunkCount = Math.max(1, Math.ceil(sortedBucket.length / maxRowsPerColumn))
    for (let chunkIndex = 0; chunkIndex < chunkCount; chunkIndex += 1) {
      const chunk = sortedBucket.slice(chunkIndex * maxRowsPerColumn, (chunkIndex + 1) * maxRowsPerColumn)
      const band = Math.floor(visualColumn / maxColumnsPerBand)
      const column = visualColumn % maxColumnsPerBand
      const x = left + column * spacingX
      const bandTop = top + band * (maxRowsPerColumn * spacingY + bandGap)
      const verticalOffset = Math.max(0, maxRowsPerColumn - chunk.length) * spacingY * 0.5
      chunk.forEach((node, row) => {
        positions.set(node.id, {
          x,
          y: bandTop + verticalOffset + row * spacingY,
        })
      })
      visualColumn += 1
    }
  })

  const usedColumns = Math.min(maxColumnsPerBand, Math.max(1, visualColumn))
  const bands = Math.max(1, Math.ceil(visualColumn / maxColumnsPerBand))
  const canvas = {
    width: Math.max(1120, left * 2 + (usedColumns - 1) * spacingX + 160),
    height: Math.max(680, top * 2 + bands * maxRowsPerColumn * spacingY + (bands - 1) * bandGap),
  }
  return { canvas, positions }
}

function nodeSortWeight(node: RelationNode) {
  const type = nodeTypeText(node)
  if (type.includes('entry')) return 0
  if (type.includes('controller')) return 1
  if (type.includes('service')) return 2
  if (type.includes('code')) return 3
  return 4
}

function shorten(value: string, limit: number) {
  return value.length > limit ? `${value.slice(0, limit - 1)}...` : value
}

function edgePath(source: GraphNode, target: GraphNode) {
  const dx = target.x - source.x
  const dy = target.y - source.y
  const curve = Math.max(50, Math.min(180, Math.abs(dx) * 0.45 + Math.abs(dy) * 0.12))
  if (Math.abs(dx) >= Math.abs(dy)) {
    const direction = dx >= 0 ? 1 : -1
    return `M ${source.x} ${source.y} C ${source.x + curve * direction} ${source.y}, ${target.x - curve * direction} ${target.y}, ${target.x} ${target.y}`
  }
  const direction = dy >= 0 ? 1 : -1
  return `M ${source.x} ${source.y} C ${source.x} ${source.y + curve * direction}, ${target.x} ${target.y - curve * direction}, ${target.x} ${target.y}`
}

function edgeLabelPoint(source: GraphNode, target: GraphNode) {
  return {
    x: (source.x + target.x) / 2,
    y: (source.y + target.y) / 2 - 10,
  }
}

function nodeTone(node: RelationNode) {
  const type = node.type || ''
  if (type.includes('snapshot')) return 'snapshot'
  if (type.includes('table')) return 'table'
  if (type.includes('code')) return 'code'
  if (type.includes('notice')) return 'notice'
  return 'default'
}

function edgeTone(edge: Pick<RelationEdge, 'action' | 'label'>) {
  const value = `${edge.action || ''} ${edge.label || ''}`.toLowerCase()
  if (value.includes('delete') || value.includes('删')) return 'delete'
  if (value.includes('update') || value.includes('改')) return 'update'
  if (value.includes('insert') || value.includes('增')) return 'insert'
  if (value.includes('select') || value.includes('查')) return 'select'
  if (value.includes('invoke') || value.includes('调用') || value.includes('执行') || value.includes('entry') || value.includes('入口')) return 'invoke'
  return 'default'
}

function actionText(action?: string) {
  if (!action) return ''
  return action.split(',').map((item) => {
    const value = item.trim().toLowerCase()
    if (value === 'insert') return '增'
    if (value === 'delete') return '删'
    if (value === 'update') return '改'
    if (value === 'select') return '查'
    if (value === 'invoke') return '调用'
    if (value === 'entry' || value === 'start') return '入口'
    return item
  }).filter(Boolean).join(',')
}

function selectGraphNode(nodeId: string) {
  selectedId.value = nodeId
  tip.open = false
  emit('node-select', filteredNodes.value.find((node) => node.id === nodeId) || null)
}

function closeContextMenu() {
  contextMenu.open = false
}

function openCanvasContextMenu(event: MouseEvent) {
  if ((event.target as Element).closest('.graph-node, .graph-tools, .graph-context-menu, .graph-tip')) return
  contextMenu.open = true
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.nodeId = ''
  contextMenu.target = 'canvas'
}

function openContextMenu(event: MouseEvent, nodeId: string) {
  selectGraphNode(nodeId)
  contextMenu.open = true
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.nodeId = nodeId
  contextMenu.target = 'node'
}

function actionMatchesContext(action: RelationContextAction) {
  const target = action.target || 'node'
  if (target === 'canvas') {
    return contextMenu.target === 'canvas'
  }
  if (contextMenu.target !== 'node') {
    return false
  }
  if (target === 'node') {
    return Boolean(contextMenuNode.value)
  }
  return hasContextNodeClass(target)
}

function hasContextNodeClass(target: Exclude<NonNullable<RelationContextAction['target']>, 'canvas' | 'node'>) {
  const node = contextMenuNode.value
  if (!node) return false
  const tokens = `${node.type || ''} ${(node.classes || []).join(' ')}`.toLowerCase().split(/\s+/).filter(Boolean)
  if (target === 'remote') return tokens.some((item) => item.includes('remote') || item.includes('dubbo'))
  if (target === 'code') return tokens.some((item) => item.includes('code'))
  return tokens.some((item) => item === target || item.includes(target))
}

function runContextAction(action: RelationContextAction) {
  if (action.disabled) return
  const node = contextMenuNode.value
  if (action.id === 'find') {
    openSearchPanel()
    closeContextMenu()
    return
  }
  if (action.id === 'fit') {
    fitGraph()
    closeContextMenu()
    return
  }
  if (action.id === 'relayout') {
    resetGraphLayout()
    closeContextMenu()
    return
  }
  if (action.id === 'tip') {
    if (node) selectGraphNode(node.id)
    showSelectedTip()
    closeContextMenu()
    return
  }
  emit('context-action', action.id, node)
  closeContextMenu()
}

function focusSearchInput() {
  searchInputRef.value?.focus()
}

async function openSearchPanel() {
  compactSearchOpen.value = true
  await nextTick()
  focusSearchInput()
  searchInputRef.value?.select()
}

function clampGraphZoom(value: number) {
  return Math.min(3, Math.max(0.45, value))
}

function zoomGraph(delta: number) {
  graphZoom.value = clampGraphZoom(graphZoom.value + delta)
}

function fitGraph() {
  const bounds = graphContentBounds()
  const padding = 100
  const availableWidth = Math.max(320, graphCanvas.value.width - padding * 2)
  const availableHeight = Math.max(260, graphCanvas.value.height - padding * 2)
  const widthRatio = availableWidth / Math.max(bounds.width, 1)
  const heightRatio = availableHeight / Math.max(bounds.height, 1)
  const zoom = clampGraphZoom(Math.min(1.35, Math.max(0.55, Math.min(widthRatio, heightRatio))))
  graphZoom.value = zoom
  graphOffset.value = {
    x: graphCanvas.value.width / 2 - (bounds.x + bounds.width / 2) * zoom,
    y: graphCanvas.value.height / 2 - (bounds.y + bounds.height / 2) * zoom,
  }
}

function graphContentBounds() {
  const nodes = graphNodes.value
  if (!nodes.length) {
    return { x: 0, y: 0, width: graphCanvas.value.width, height: graphCanvas.value.height }
  }
  const labelPadding = denseGraph.value ? 36 : 72
  const minX = Math.min(...nodes.map((node) => node.x - node.radius - labelPadding))
  const maxX = Math.max(...nodes.map((node) => node.x + node.radius + labelPadding))
  const minY = Math.min(...nodes.map((node) => node.y - node.radius - labelPadding))
  const maxY = Math.max(...nodes.map((node) => node.y + node.radius + labelPadding))
  return {
    x: minX,
    y: minY,
    width: Math.max(1, maxX - minX),
    height: Math.max(1, maxY - minY),
  }
}

function resetGraphLayout() {
  nodePositionOverrides.value = {}
  fitGraph()
}

function graphPointerDelta(event: PointerEvent, startX: number, startY: number) {
  const rect = relationGraphRef.value?.getBoundingClientRect()
  const scaleX = rect?.width ? graphCanvas.value.width / rect.width : 1
  const scaleY = rect?.height ? graphCanvas.value.height / rect.height : 1
  return {
    x: (event.clientX - startX) * scaleX / graphZoom.value,
    y: (event.clientY - startY) * scaleY / graphZoom.value,
  }
}

function handleGraphWheel(event: WheelEvent) {
  zoomGraph(event.deltaY > 0 ? -0.1 : 0.1)
}

function startBoardPan(event: PointerEvent) {
  if ((event.target as Element).closest('.graph-tools, .graph-node, .graph-context-menu, .graph-tip')) return
  graphPan.value = {
    startX: event.clientX,
    startY: event.clientY,
    originX: graphOffset.value.x,
    originY: graphOffset.value.y,
  }
}

function moveBoardPan(event: PointerEvent) {
  if (nodeDrag.value) {
    const delta = graphPointerDelta(event, nodeDrag.value.startX, nodeDrag.value.startY)
    nodePositionOverrides.value = {
      ...nodePositionOverrides.value,
      [nodeDrag.value.id]: {
        x: nodeDrag.value.originX + delta.x,
        y: nodeDrag.value.originY + delta.y,
      },
    }
    return
  }
  if (!graphPan.value) return
  const delta = graphPointerDelta(event, graphPan.value.startX, graphPan.value.startY)
  graphOffset.value = {
    x: graphPan.value.originX + delta.x,
    y: graphPan.value.originY + delta.y,
  }
}

function endBoardPan() {
  graphPan.value = null
  nodeDrag.value = null
}

function startNodeDrag(event: PointerEvent, node: GraphNode) {
  selectGraphNode(node.id)
  nodeDrag.value = {
    id: node.id,
    startX: event.clientX,
    startY: event.clientY,
    originX: node.x,
    originY: node.y,
  }
}

function selectSearchMatch(nodeId?: string) {
  if (!nodeId) return
  selectGraphNode(nodeId)
  centerGraphOnNode(nodeId)
}

function centerGraphOnNode(nodeId: string) {
  const node = graphNodeMap.value.get(nodeId)
  if (!node) return
  graphZoom.value = Math.max(graphZoom.value, denseGraph.value ? 1.15 : 1)
  graphOffset.value = {
    x: graphCanvas.value.width / 2 - node.x * graphZoom.value,
    y: graphCanvas.value.height / 2 - node.y * graphZoom.value,
  }
}

function showNodeLabel(node: GraphNode) {
  if (!denseGraph.value) return true
  return selectedId.value === node.id || searchMatchIds.value.has(node.id) || relatedNodeIds.value.has(node.id)
}

function showEdgeLabel(edge: { id: string }) {
  const showByDefault = props.showEdgeLabels ?? !denseGraph.value
  return showByDefault || relatedEdgeIds.value.has(edge.id)
}

function nodeRelated(node: GraphNode) {
  return Boolean(props.highlightRelated && relatedNodeIds.value.has(node.id))
}

function nodeDimmed(node: GraphNode) {
  return Boolean(props.highlightRelated && selectedId.value && !relatedNodeIds.value.has(node.id))
}

function edgeRelated(edge: { id: string }) {
  return Boolean(props.highlightRelated && relatedEdgeIds.value.has(edge.id))
}

function edgeDimmed(edge: { id: string }) {
  return Boolean(props.highlightRelated && selectedId.value && !relatedEdgeIds.value.has(edge.id))
}

function showSelectedTip() {
  if (!selectedNode.value) return
  tip.open = true
  closeContextMenu()
}

function handleKeydown(event: KeyboardEvent) {
  if (event.ctrlKey && event.key.toLowerCase() === 'f') {
    event.preventDefault()
    openSearchPanel()
  }
  if (event.key === 'F2') {
    showSelectedTip()
  }
}

watch(
  () => [
    layoutSourceNodes.value.map((node) => node.id).join('|'),
    layoutSourceEdges.value.map((edge) => `${edge.source}>${edge.target}`).join('|'),
  ],
  async () => {
    nodePositionOverrides.value = {}
    await nextTick()
    fitGraph()
  },
  { flush: 'post' },
)

watchEffect(() => {
  if (nodePage.value > nodeTotalPages.value) nodePage.value = nodeTotalPages.value
  if (edgePage.value > edgeTotalPages.value) edgePage.value = edgeTotalPages.value
})

watchEffect(() => {
  keyword.value
  nodePage.value = 1
  edgePage.value = 1
})

watchEffect(() => {
  selectedId.value
  showAllSelectedEdges.value = false
})

watchEffect(() => {
  const firstMatch = searchMatches.value[0]
  if (keyword.value && firstMatch && selectedId.value !== firstMatch.id) {
    selectedId.value = firstMatch.id
    emit('node-select', firstMatch)
  }
})

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))

watchEffect(() => {
  if (!selectedId.value && filteredNodes.value.length) {
    selectedId.value = filteredNodes.value[0].id
    emit('node-select', filteredNodes.value[0])
    return
  }
  if (selectedId.value && !filteredNodes.value.some((node) => node.id === selectedId.value)) {
    selectedId.value = filteredNodes.value[0]?.id || ''
    emit('node-select', filteredNodes.value[0] || null)
  }
})
</script>

<style scoped>
.page-header,
.header-actions,
.panel-head,
.node-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.page-header {
  margin-bottom: 10px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtext,
.detail-id,
.search-box span,
.node-desc,
.meta-list,
.empty-inline {
  color: #64748b;
}

.secondary-link {
  color: #0f766e;
  font-weight: 700;
}

.status-card,
.summary-card,
.panel,
.empty-card,
.node-card {
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.summary-card strong {
  display: block;
  font-size: 20px;
  margin-top: 3px;
}

.graph-search-block {
  position: relative;
  margin: 10px 0;
}

.compact-search-block {
  margin: 0 0 10px;
  padding: 10px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 16px;
  background: rgba(255, 255, 255, .90);
}

.search-box {
  display: grid;
  gap: 5px;
  margin: 0;
}

.graph-search-results {
  position: absolute;
  left: 0;
  right: 0;
  top: calc(100% + 8px);
  z-index: 25;
  display: grid;
  gap: 5px;
  max-height: 280px;
  overflow: auto;
  padding: 8px;
  border: 1px solid rgba(15, 23, 42, .10);
  border-radius: 14px;
  background: rgba(255, 255, 255, .98);
  box-shadow: 0 18px 42px rgba(15, 23, 42, .16);
}

.compact-search-block .graph-search-results {
  left: 10px;
  right: 10px;
  top: calc(100% + 4px);
}

.graph-search-results button {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  border: none;
  border-radius: 10px;
  padding: 9px 11px;
  background: transparent;
  color: #172033;
  cursor: pointer;
}

.graph-search-results button:hover,
.graph-search-results button.active {
  background: rgba(15, 118, 110, .08);
  color: #0f766e;
}

.graph-search-results span,
.empty-search-result {
  color: #64748b;
  font-size: 12px;
}

.empty-search-result {
  padding: 8px 10px;
}

.text-input {
  width: 100%;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 8px 10px;
}

.board-workspace {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1fr);
  min-height: 0;
}

.board-workspace.graph-only {
  grid-template-columns: minmax(0, 1fr);
}

.board-side,
.compact-lists,
.node-list-panel,
.node-detail-panel {
  min-height: 0;
}

.board-side {
  overflow: visible;
}

.compact-lists {
  grid-template-columns: minmax(260px, .72fr) minmax(320px, 1fr);
  align-items: start;
}

.compact-lists .edge-panel {
  grid-column: 1 / -1;
}

.layout-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.node-grid {
  display: grid;
  gap: 8px;
}

.node-list-panel,
.node-detail-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.node-grid {
  overflow: auto;
  max-height: 470px;
  padding-right: 2px;
}

.node-card {
  text-align: left;
  cursor: pointer;
}

.node-card .node-top {
  align-items: flex-start;
}

.node-card strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-card.active {
  border-color: rgba(15, 118, 110, 0.45);
  box-shadow: 0 8px 20px rgba(15, 118, 110, 0.10);
}

.type-pill {
  max-width: 170px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.meta-list {
  margin: 6px 0 0;
  padding-left: 16px;
  line-height: 1.35;
}

.detail-card {
  display: grid;
  gap: 8px;
}

.detail-card h3,
.detail-card p {
  margin: 0;
}

.connection-group {
  display: grid;
  gap: 8px;
  min-height: 0;
}

.edge-list {
  display: grid;
  gap: 7px;
  min-height: 0;
  max-height: 330px;
  overflow: auto;
}

.edge-card {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  border-radius: 12px;
  background: #f8fbfb;
  font-size: 13px;
}

.edge-card span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.edge-card.insert,
.action-pill.insert { color: #15803d; background: rgba(22, 163, 74, .10); }
.edge-card.update,
.action-pill.update { color: #b45309; background: rgba(245, 158, 11, .12); }
.edge-card.delete,
.action-pill.delete { color: #b91c1c; background: rgba(220, 38, 38, .10); }
.edge-card.select,
.action-pill.select { color: #1d4ed8; background: rgba(37, 99, 235, .10); }

.action-pill {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 10px;
  background: rgba(100, 116, 139, .12);
  color: #475569;
  font-weight: 800;
}

.edge-panel {
  max-height: min(520px, calc(100vh - 390px));
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.table-shell {
  overflow: auto;
  min-height: 0;
  flex: 1;
}

.mini-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(15, 23, 42, .08);
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.mini-pagination button,
.inline-more-button {
  min-height: 32px;
  border: 1px solid rgba(15, 118, 110, .16);
  border-radius: 999px;
  padding: 5px 11px;
  background: rgba(15, 118, 110, .07);
  color: #0f766e;
  font-weight: 800;
}

.mini-pagination button:disabled {
  opacity: .45;
}

.inline-more-button {
  justify-self: start;
  margin-top: 2px;
}

.edge-table {
  width: 100%;
  border-collapse: collapse;
}

.edge-table th,
.edge-table td {
  padding: 8px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
}

@media (max-width: 960px) {
  .layout-grid {
    grid-template-columns: 1fr;
  }
}

.graph-panel {
  position: relative;
  display: flex;
  min-height: min(620px, calc(100vh - 250px));
  flex-direction: column;
  padding: 16px;
  border-radius: 24px;
  background:
    radial-gradient(circle at 10% 20%, rgba(15, 118, 110, 0.12), transparent 28%),
    radial-gradient(circle at 80% 10%, rgba(245, 158, 11, 0.12), transparent 24%),
    rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.08);
  margin: 0;
  overflow: hidden;
  cursor: grab;
  touch-action: none;
}

.graph-panel:active {
  cursor: grabbing;
}

.graph-toolbar,
.graph-tools {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.graph-toolbar {
  color: #334155;
  font-weight: 800;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.graph-tools button,
.graph-context-menu button {
  border: none;
  border-radius: 999px;
  padding: 7px 10px;
  background: rgba(15, 23, 42, 0.08);
  cursor: pointer;
  transition: transform .12s ease, background .12s ease, color .12s ease, box-shadow .12s ease;
}

.graph-tools button:hover:not(:disabled),
.graph-tools button:focus-visible {
  background: #0f766e;
  color: #fff;
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(15, 118, 110, .18);
}

.graph-tools button:active:not(:disabled) {
  transform: translateY(0);
}

.graph-tools button:disabled {
  opacity: .5;
  cursor: not-allowed;
}

.relation-graph {
  width: 100%;
  min-height: 0;
  flex: 1 1 auto;
  border-radius: 20px;
  background:
    linear-gradient(rgba(15, 23, 42, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, 0.04) 1px, transparent 1px),
    #f8fafc;
  background-size: 32px 32px;
}

.graph-edge path {
  fill: none;
  stroke: #94a3b8;
  stroke-width: 1.8;
  opacity: .62;
}

.graph-edge.related path {
  stroke: #0f766e;
  stroke-width: 3;
  opacity: 1;
}

.graph-edge.dimmed path {
  opacity: .12;
}

.graph-edge.insert path { stroke: #16a34a; }
.graph-edge.update path { stroke: #f59e0b; }
.graph-edge.delete path { stroke: #dc2626; }
.graph-edge.select path { stroke: #2563eb; }
.graph-edge.invoke path {
  stroke: #2563eb;
  stroke-width: 2.1;
  opacity: .72;
}

.graph-edge.insert text { fill: #15803d; }
.graph-edge.update text { fill: #b45309; }
.graph-edge.delete text { fill: #b91c1c; }
.graph-edge.select text { fill: #1d4ed8; }
.graph-edge.invoke text { fill: #1d4ed8; }

.graph-edge text {
  font-size: 11px;
  fill: #64748b;
  paint-order: stroke;
  stroke: #f8fafc;
  stroke-width: 4px;
}

.graph-node {
  cursor: grab;
}

.graph-node:active {
  cursor: grabbing;
}

.graph-node circle {
  fill: #475569;
  stroke: #fff;
  stroke-width: 4;
  filter: drop-shadow(0 10px 14px rgba(15, 23, 42, .16));
  transition: transform .15s ease, stroke .15s ease;
}

.graph-node.active circle {
  stroke: #f59e0b;
  stroke-width: 6;
}

.graph-node.related circle {
  filter: drop-shadow(0 0 14px rgba(15, 118, 110, .28));
}

.graph-node.dimmed {
  opacity: .28;
}

.graph-node.find-match circle {
  stroke: #06b6d4;
  stroke-width: 5;
}

.graph-node.snapshot circle { fill: #0f766e; }
.graph-node.table circle { fill: #b45309; }
.graph-node.code circle { fill: #1d4ed8; }
.graph-node.notice circle { fill: #b91c1c; }

.node-label {
  font-size: 12px;
  fill: #0f172a;
  font-weight: 800;
  paint-order: stroke;
  stroke: #f8fafc;
  stroke-width: 5px;
}

.node-metric {
  font-size: 11px;
  fill: #fff;
  font-weight: 900;
}

.graph-context-menu {
  position: fixed;
  z-index: 30;
  display: grid;
  gap: 6px;
  min-width: 190px;
  padding: 8px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.94);
  box-shadow: 0 18px 40px rgba(15, 23, 42, .24);
}

.graph-context-menu button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  color: #e2e8f0;
  text-align: left;
  background: transparent;
}

.graph-context-menu button small {
  color: #94a3b8;
  font-size: 11px;
}

.graph-context-menu button:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.10);
  color: #fff;
}

.graph-context-menu button.danger {
  color: #fecaca;
}

.graph-context-menu button:disabled {
  cursor: not-allowed;
  opacity: .45;
}

.graph-tip {
  position: absolute;
  right: 22px;
  bottom: 22px;
  z-index: 12;
  display: grid;
  gap: 6px;
  width: min(420px, calc(100% - 44px));
  padding: 14px;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.92);
  color: #fff;
  box-shadow: 0 18px 40px rgba(15, 23, 42, .24);
}

.graph-tip span,
.tip-meta {
  color: #cbd5e1;
  line-height: 1.5;
}

.tip-line {
  overflow-wrap: anywhere;
}

.tip-meta {
  margin: 0;
  padding-left: 18px;
}

.compact-board .graph-panel {
  margin-top: 0;
  padding: 10px;
  min-height: min(560px, calc(100vh - 290px));
}

.compact-board .relation-graph {
  min-height: 420px;
}

.compact-board .layout-grid {
  grid-template-columns: minmax(260px, .72fr) minmax(320px, 1fr);
}

.compact-board .board-workspace {
  height: auto;
  gap: 14px;
}

@media (max-width: 760px) {
  .board-workspace {
    height: auto;
    grid-template-columns: 1fr;
  }

  .board-side,
  .node-grid,
  .detail-card {
    max-height: none;
  }

  .relation-graph {
    min-height: 420px;
  }

  .graph-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}

</style>
