<template>
  <section :class="[compact && 'compact-graph-view']">
    <div class="page-header">
      <div>
        <div class="eyebrow">{{ eyebrow }}</div>
        <h1>{{ graph?.title || fallbackTitle }}</h1>
        <p class="subtext">{{ graph?.nodes.length || 0 }} 个节点 · {{ graph?.edges.length || 0 }} 条连线</p>
      </div>
      <div class="header-actions">
        <RouterLink v-if="codeGraphRoute" class="ghost-button" :to="codeGraphRoute">源码堆栈图谱</RouterLink>
        <RouterLink class="secondary-link" :to="backRoute">{{ backLabel }}</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">{{ loadingText }}</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="graph">
      <section class="panel graph-panel-card">
        <div class="card-title graph-title-row">
          <div>
            <h2>节点拓扑</h2>
            <p class="subtext">默认自适应居中；支持滚轮缩放、拖拽平移和拖拽节点。</p>
          </div>
          <span class="graph-count-pill">{{ graph.nodes.length }} 节点 / {{ graph.edges.length }} 连线</span>
        </div>
        <div
          class="graph-board"
          @wheel.prevent="handleGraphWheel"
          @pointerdown="startGraphPan"
          @pointerleave="endGraphPan"
          @pointerup="endGraphPan"
          @pointermove="moveGraphPan"
        >
          <div class="graph-tools">
            <span>{{ Math.round(graphZoom * 100) }}%</span>
            <button type="button" @click="fitGraph">适配</button>
            <button type="button" @click="zoomGraph(0.15)">放大</button>
            <button type="button" @click="zoomGraph(-0.15)">缩小</button>
            <button type="button" @click="resetGraphLayout">重排</button>
            <button type="button" @click="resetGraphView">重置</button>
          </div>
          <svg class="graph-svg" :viewBox="graphViewBox" preserveAspectRatio="xMidYMid meet">
            <defs>
              <marker :id="arrowMarkerId" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
                <path d="M0,0 L0,6 L9,3 z" fill="#94a3b8" />
              </marker>
            </defs>
            <g :transform="graphTransform">
              <path
                v-for="edge in edgePositions"
                :key="`${edge.from}-${edge.to}-${edge.label}`"
                :d="edge.path"
                class="graph-edge"
                :marker-end="`url(#${arrowMarkerId})`"
              />
              <text
                v-for="edge in edgePositions"
                :key="`${edge.from}-${edge.to}-${edge.label}-text`"
                :x="edge.mx"
                :y="edge.my"
                class="edge-label"
              >
                {{ edge.label || edge.title || edge.type || edge.count }}
              </text>
              <g
                v-for="node in nodePositions"
                :key="node.id"
                class="graph-node"
                :class="[`node-${node.state || 'normal'}`, { active: selectedNodeId === node.id }]"
                @pointerdown.stop="startNodeDrag($event, node)"
                @click.stop="$emit('select-node', node.id)"
              >
                <title>{{ nodeTooltip(node) }}</title>
                <rect :x="node.x" :y="node.y" rx="16" ry="16" :width="nodeWidth" :height="nodeHeight" />
                <circle :cx="node.x + 34" :cy="node.y + 34" r="18" class="node-icon-ring" />
                <text :x="node.x + 34" :y="node.y + 41" class="node-icon">{{ iconGlyph(node.icon || node.type) }}</text>
                <text :x="node.x + 64" :y="node.y + 30" class="node-title">{{ compactText(node.title || node.id, 20) }}</text>
                <text :x="node.x + 64" :y="node.y + 54" class="node-subtitle">{{ compactText(node.subTitle || '-', 24) }}</text>
                <text :x="node.x + 16" :y="node.y + 82" class="node-type">{{ compactText(node.tips || node.type || 'unknown', 30) }}</text>
              </g>
            </g>
          </svg>
        </div>
      </section>

      <section class="panel bottom-panel">
        <div class="category-header">
          <h3>全部节点</h3>
          <span class="category-count">{{ sortedNodes.length }} 个节点</span>
        </div>
        <div class="node-tabs-bar">
          <button
            v-for="node in sortedNodes"
            :key="node.id"
            class="node-tab-btn"
            :class="{ active: selectedNodeId === node.id }"
            type="button"
            @click="$emit('select-node', node.id)"
          >
            <span class="node-tab-icon">{{ iconGlyph(node.icon || node.type) }}</span>
            <span class="node-tab-label">{{ node.title || node.id }}</span>
          </button>
        </div>
        <div
          v-if="selectedNode"
          class="node-content-panel"
        >
          <div class="node-content-header">
            <span class="node-content-icon">{{ iconGlyph(selectedNode.icon || selectedNode.type) }}</span>
            <div class="node-content-meta">
              <strong>{{ selectedNode.title || selectedNode.id }}</strong>
              <span>{{ selectedNode.subTitle || '-' }}</span>
            </div>
            <span class="node-type-badge">{{ selectedNode.type || '-' }}</span>
          </div>
          <div v-if="selectedNodeDetail" class="node-content-body">
            <GraphNodeDetailCard
              :detail="selectedNodeDetail"
              empty-text=""
            />
          </div>
          <div v-else class="node-content-empty">
            点击图谱中的节点查看详细信息
          </div>
        </div>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

import type { GraphEdgeSummary, GraphNodeDetailPayload, GraphNodeSummary, GraphViewPayload } from '@/api/types'
import GraphNodeDetailCard from '@/components/snapshot/GraphNodeDetailCard.vue'

type PositionedEdge = GraphEdgeSummary & {
  path: string
  mx: number
  my: number
}

type PositionedNode = GraphNodeSummary & {
  x: number
  y: number
  rank: number
}

const nodeWidth = 230
const nodeHeight = 92
const graphZoom = ref(1)
const graphOffset = ref({ x: 0, y: 0 })
const graphPan = ref<{ startX: number; startY: number; originX: number; originY: number } | null>(null)
const nodePositionOverrides = ref<Record<string, { x: number; y: number }>>({})
const nodeDrag = ref<{ id: string; startX: number; startY: number; originX: number; originY: number } | null>(null)


const props = defineProps<{
  eyebrow: string
  fallbackTitle: string
  backRoute: RouteLocationRaw
  backLabel: string
  loading: boolean
  loadingText: string
  error?: string
  graph?: GraphViewPayload
  selectedNodeId?: string
  selectedNodeDetail?: GraphNodeDetailPayload
  arrowMarkerId: string
  codeGraphRoute?: RouteLocationRaw
  compact?: boolean
}>()

const emit = defineEmits<{
  (event: 'select-node', nodeId: string): void
}>()

const sortedNodes = computed<GraphNodeSummary[]>(() => {
  const nodes = props.graph?.nodes || []
  const edges = props.graph?.edges || []
  const incoming = new Map<string, number>()
  const children = new Map<string, string[]>()

  nodes.forEach((node) => {
    incoming.set(node.id, 0)
    children.set(node.id, [])
  })
  edges.forEach((edge) => {
    if (!incoming.has(edge.from) || !incoming.has(edge.to)) return
    incoming.set(edge.to, (incoming.get(edge.to) || 0) + 1)
    children.get(edge.from)?.push(edge.to)
  })

  const ranks = new Map<string, number>()
  const roots = nodes.filter((node) => (incoming.get(node.id) || 0) === 0)
  const queue = (roots.length ? roots : nodes.slice(0, 1)).map((node) => node.id)
  queue.forEach((id) => ranks.set(id, 0))
  for (let i = 0; i < queue.length; i += 1) {
    const id = queue[i]
    const nextRank = (ranks.get(id) || 0) + 1
    ;(children.get(id) || []).forEach((childId) => {
      if ((ranks.get(childId) ?? -1) < nextRank) {
        ranks.set(childId, nextRank)
        queue.push(childId)
      }
    })
  }

  return [...nodes].sort((a, b) => {
    const rankA = ranks.get(a.id) ?? 999
    const rankB = ranks.get(b.id) ?? 999
    if (rankA !== rankB) return rankA - rankB
    return nodes.indexOf(a) - nodes.indexOf(b)
  })
})

const selectedNode = computed(() =>
  sortedNodes.value.find((n) => n.id === props.selectedNodeId) ?? null,
)

const nodePositions = computed<PositionedNode[]>(() => {
  const nodes = props.graph?.nodes || []
  const edges = props.graph?.edges || []
  const incoming = new Map<string, number>()
  const children = new Map<string, string[]>()
  nodes.forEach((node) => {
    incoming.set(node.id, 0)
    children.set(node.id, [])
  })
  edges.forEach((edge) => {
    if (!incoming.has(edge.from) || !incoming.has(edge.to)) return
    incoming.set(edge.to, (incoming.get(edge.to) || 0) + 1)
    children.get(edge.from)?.push(edge.to)
  })

  const ranks = new Map<string, number>()
  const roots = nodes.filter((node) => (incoming.get(node.id) || 0) === 0)
  const queue = (roots.length ? roots : nodes.slice(0, 1)).map((node) => node.id)
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

  const buckets = new Map<number, GraphNodeSummary[]>()
  nodes.forEach((node, index) => {
    const rank = ranks.get(node.id) ?? Math.floor(index / 5)
    const list = buckets.get(rank) || []
    list.push(node)
    buckets.set(rank, list)
  })

  return nodes.map((node, index) => {
    const rank = ranks.get(node.id) ?? Math.floor(index / 5)
    const bucket = buckets.get(rank) || []
    const row = Math.max(0, bucket.findIndex((item) => item.id === node.id))
    const columnHeight = Math.max(1, bucket.length)
    const autoPosition = {
      x: 96 + rank * 310,
      y: 96 + row * 126 + Math.max(0, 3 - columnHeight) * 34,
    }
    const override = nodePositionOverrides.value[node.id]
    return {
      ...node,
      rank,
      x: override?.x ?? autoPosition.x,
      y: override?.y ?? autoPosition.y,
    }
  })
})

const graphBounds = computed(() => {
  const nodes = nodePositions.value
  if (!nodes.length) return { minX: 0, minY: 0, maxX: 960, maxY: 560, width: 960, height: 560 }
  const minX = Math.min(...nodes.map((node) => node.x))
  const minY = Math.min(...nodes.map((node) => node.y))
  const maxX = Math.max(...nodes.map((node) => node.x + nodeWidth))
  const maxY = Math.max(...nodes.map((node) => node.y + nodeHeight))
  return { minX, minY, maxX, maxY, width: maxX - minX, height: maxY - minY }
})

const graphViewBox = computed(() => {
  const bounds = graphBounds.value
  const padding = 140
  const width = Math.max(920, bounds.width + padding * 2)
  const height = Math.max(560, bounds.height + padding * 2)
  return `0 0 ${width} ${height}`
})

const graphTransform = computed(() => `translate(${graphOffset.value.x} ${graphOffset.value.y}) scale(${graphZoom.value})`)

const edgePositions = computed(() => {
  const nodeMap = new Map(nodePositions.value.map((node) => [node.id, node]))
  return (props.graph?.edges || [])
    .map((edge): PositionedEdge | null => {
      const from = nodeMap.get(edge.from)
      const to = nodeMap.get(edge.to)
      if (!from || !to) {
        return null
      }
      const x1 = from.x + nodeWidth
      const y1 = from.y + nodeHeight / 2
      const x2 = to.x
      const y2 = to.y + nodeHeight / 2
      const dx = Math.max(56, Math.abs(x2 - x1) / 2)
      return {
        ...edge,
        path: `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`,
        mx: (x1 + x2) / 2,
        my: (y1 + y2) / 2 - 10,
      }
    })
    .filter((edge): edge is PositionedEdge => edge !== null)
})

watch(() => props.graph, () => {
  resetGraphLayout()
  resetGraphView()
})

function clampZoom(value: number) {
  return Math.min(2.2, Math.max(0.45, value))
}

function zoomGraph(delta: number) {
  graphZoom.value = clampZoom(graphZoom.value + delta)
}

function fitGraph() {
  const bounds = graphBounds.value
  const viewWidth = Math.max(920, bounds.width + 280)
  const viewHeight = Math.max(560, bounds.height + 280)
  const zoom = clampZoom(Math.min((viewWidth - 180) / Math.max(bounds.width, 1), (viewHeight - 180) / Math.max(bounds.height, 1), 1.05))
  graphZoom.value = zoom
  graphOffset.value = {
    x: (viewWidth - bounds.width * zoom) / 2 - bounds.minX * zoom,
    y: (viewHeight - bounds.height * zoom) / 2 - bounds.minY * zoom,
  }
}

function resetGraphView() {
  fitGraph()
}

function resetGraphLayout() {
  nodePositionOverrides.value = {}
}

function handleGraphWheel(event: WheelEvent) {
  const direction = event.deltaY > 0 ? -0.1 : 0.1
  zoomGraph(direction)
}

function startGraphPan(event: PointerEvent) {
  if ((event.target as Element).closest('.graph-tools, .graph-node')) return
  graphPan.value = {
    startX: event.clientX,
    startY: event.clientY,
    originX: graphOffset.value.x,
    originY: graphOffset.value.y,
  }
}

function moveGraphPan(event: PointerEvent) {
  if (nodeDrag.value) {
    nodePositionOverrides.value = {
      ...nodePositionOverrides.value,
      [nodeDrag.value.id]: {
        x: nodeDrag.value.originX + (event.clientX - nodeDrag.value.startX) / graphZoom.value,
        y: nodeDrag.value.originY + (event.clientY - nodeDrag.value.startY) / graphZoom.value,
      },
    }
    return
  }
  if (!graphPan.value) return
  graphOffset.value = {
    x: graphPan.value.originX + (event.clientX - graphPan.value.startX),
    y: graphPan.value.originY + (event.clientY - graphPan.value.startY),
  }
}

function endGraphPan() {
  graphPan.value = null
  nodeDrag.value = null
}

function startNodeDrag(event: PointerEvent, node: PositionedNode) {
  emit('select-node', node.id)
  nodeDrag.value = {
    id: node.id,
    startX: event.clientX,
    startY: event.clientY,
    originX: node.x,
    originY: node.y,
  }
}

function compactText(value: string, maxLength: number) {
  if (!value) return ''
  return value.length > maxLength ? `${value.slice(0, maxLength - 1)}…` : value
}

function nodeTooltip(node: GraphNodeSummary) {
  return [node.title || node.id, node.subTitle, node.tips || node.type]
    .filter(Boolean)
    .join('\n')
}

function iconGlyph(value?: string) {
  const normalized = (value || '').toLowerCase()
  if (normalized.includes('database') || normalized.includes('sql')) return 'DB'
  if (normalized.includes('redis')) return 'R'
  if (normalized.includes('server') || normalized.includes('application')) return 'A'
  if (normalized.includes('http') || normalized.includes('client')) return 'H'
  if (normalized.includes('cloud')) return 'C'
  return 'N'
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
  margin-bottom: 16px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtext,
.node-card span,
.node-card small,
.node-card p {
  color: #64748b;
}

.secondary-link,
.ghost-button {
  color: #0f766e;
  font-weight: 700;
}

.ghost-button {
  border: 1px solid rgba(15, 118, 110, 0.18);
  border-radius: 999px;
  padding: 9px 13px;
  background: rgba(15, 118, 110, 0.06);
}

.status-card,
.panel {
  min-width: 0;
  padding: 16px 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.graph-panel-card {
  display: flex;
  flex-direction: column;
  margin-bottom: 14px;
}

.graph-title-row {
  align-items: flex-start;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.graph-title-row h2 {
  margin: 0;
}

.graph-count-pill {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 5px 11px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.graph-board {
  position: relative;
  overflow: hidden;
  height: min(480px, 52vh);
  min-height: 320px;
  border-radius: 16px;
  background:
    radial-gradient(circle at 24px 24px, rgba(15, 118, 110, .08) 1.5px, transparent 1.5px),
    linear-gradient(rgba(15, 23, 42, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, 0.035) 1px, transparent 1px),
    linear-gradient(135deg, #fbfefd, #f5fbfb);
  background-size: 56px 56px, 28px 28px, 28px 28px, auto;
  border: 1px solid rgba(15, 23, 42, 0.07);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.8);
  cursor: grab;
}

.graph-board:active {
  cursor: grabbing;
}

.graph-svg {
  width: 100%;
  height: 100%;
  display: block;
}

.graph-tools {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.10);
  backdrop-filter: blur(14px);
}

.graph-tools span {
  min-width: 42px;
  border-radius: 999px;
  padding: 5px 7px;
  background: #0f172a;
  color: #fff;
  font-size: 12px;
  font-weight: 900;
  text-align: center;
  white-space: nowrap;
}

.graph-tools button {
  border: none;
  border-radius: 999px;
  padding: 5px 10px;
  background: #eef7f7;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  transition: transform .12s ease, background .12s ease, color .12s ease, box-shadow .12s ease;
}

.graph-tools button:hover,
.graph-tools button:focus-visible {
  background: #0f766e;
  color: #fff;
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(15, 118, 110, .18);
}

.graph-tools button:active {
  transform: translateY(0);
}

.graph-edge {
  fill: none;
  stroke: #8aa0b8;
  stroke-width: 2.2;
  opacity: .88;
}

.edge-label {
  fill: #475569;
  font-size: 12px;
  font-weight: 800;
  paint-order: stroke;
  stroke: rgba(255,255,255,.92);
  stroke-width: 5px;
  text-anchor: middle;
}

.graph-node {
  cursor: grab;
}

.graph-node:active {
  cursor: grabbing;
}

.graph-node rect {
  fill: rgba(255, 255, 255, .98);
  stroke: rgba(15, 118, 110, 0.16);
  stroke-width: 1.5;
  filter: drop-shadow(0 10px 20px rgba(15, 23, 42, 0.10));
}

.graph-node.active rect {
  stroke: #2563eb;
  stroke-width: 2.8;
  filter: drop-shadow(0 14px 26px rgba(37, 99, 235, 0.18));
}

.graph-node.node-error rect {
  fill: rgba(254, 242, 242, 0.96);
  stroke: rgba(185, 28, 28, 0.42);
}

.node-icon-ring {
  fill: #ecfeff;
  stroke: rgba(15, 118, 110, 0.22);
  stroke-width: 1.4;
}

.graph-node.node-error .node-icon-ring {
  fill: #fff1f2;
  stroke: rgba(185, 28, 28, 0.28);
}

.node-icon {
  fill: #0f766e;
  font-size: 12px;
  font-weight: 900;
  text-anchor: middle;
}

.node-title {
  fill: #0f172a;
  font-size: 14px;
  font-weight: 900;
}

.node-subtitle,
.node-type {
  fill: #64748b;
  font-size: 11px;
}

.bottom-panel {
  padding: 0;
  overflow: hidden;
}

.sections-container {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.category-section {
  padding: 16px 18px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.07);
}

.category-section:last-child {
  border-bottom: none;
}

.category-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.category-header h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.category-count {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
}

.node-tabs-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 0;
}

.node-tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 12px 7px 8px;
  border: 1.5px solid rgba(15, 23, 42, 0.09);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.80);
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color .14s, background .14s, color .14s, box-shadow .14s;
  white-space: nowrap;
}

.node-tab-btn:hover {
  border-color: rgba(15, 118, 110, 0.28);
  background: #f0faf9;
  color: #0f766e;
}

.node-tab-btn.active {
  border-color: #0f766e;
  background: #edfaf8;
  color: #0f766e;
  box-shadow: 0 2px 8px rgba(15, 118, 110, 0.12);
}

.node-tab-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.10);
  color: #0f766e;
  font-size: 10px;
  font-weight: 900;
  flex-shrink: 0;
}

.node-tab-btn.active .node-tab-icon {
  background: rgba(15, 118, 110, 0.18);
}

.node-tab-label {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-content-panel {
  margin-top: 12px;
  border: 1.5px solid rgba(15, 118, 110, 0.22);
  border-radius: 16px;
  background: #f8fbfb;
  overflow: hidden;
}

.node-content-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.70);
}

.node-content-icon {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: #ecfeff;
  border: 1px solid rgba(15, 118, 110, 0.18);
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.node-content-meta {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.node-content-meta strong {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-content-meta span {
  font-size: 12px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-type-badge {
  flex: 0 0 auto;
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.10);
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.node-content-body {
  max-height: 480px;
  overflow-y: auto;
  overflow-x: hidden;
}

.node-content-body :deep(.detail-card) {
  border: none;
  background: transparent;
  padding: 14px;
}

.node-content-empty {
  padding: 24px 14px;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
}

.compact-graph-view .page-header {
  margin-bottom: 10px;
}

.compact-graph-view .page-header h1 {
  margin: 2px 0;
  font-size: 20px;
}

.compact-graph-view .bottom-panel {
  display: none;
}

.compact-graph-view .graph-board {
  height: 380px;
  min-height: 280px;
}

@media (max-width: 760px) {
  .page-header,
  .header-actions,
  .card-title {
    flex-direction: column;
    align-items: stretch;
  }

  .graph-board {
    height: min(340px, 46vh);
    min-height: 260px;
  }

  .graph-tools {
    top: 8px;
    right: 8px;
    gap: 4px;
    padding: 5px 6px;
    overflow-x: auto;
    max-width: calc(100% - 16px);
    border-radius: 14px;
  }

  .node-tabs-bar {
    gap: 4px;
  }

  .node-tab-btn {
    font-size: 12px;
    padding: 6px 10px 6px 7px;
  }

  .node-tab-label {
    max-width: 100px;
  }

  .node-content-body {
    max-height: 360px;
  }
}
</style>
