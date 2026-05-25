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
      <div class="graph-shell">
        <section class="panel">
          <div class="card-title">
            <h2>节点拓扑</h2>
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
              <span>滚轮缩放 · 拖拽平移 · 拖拽节点</span>
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
                  :class="[`node-${node.state || 'normal'}`, { active: selectedNodeId === node.id || graph.showDefaultNode?.id === node.id }]"
                  @pointerdown.stop="startNodeDrag($event, node)"
                  @click.stop="$emit('select-node', node.id)"
                >
                  <rect :x="node.x" :y="node.y" rx="6" ry="6" :width="nodeWidth" :height="nodeHeight" />
                  <circle :cx="node.x + 30" :cy="node.y + 33" r="18" class="node-icon-ring" />
                  <text :x="node.x + 30" :y="node.y + 40" class="node-icon">{{ iconGlyph(node.icon || node.type) }}</text>
                  <text :x="node.x + 58" :y="node.y + 30" class="node-title">{{ node.title || node.id }}</text>
                  <text :x="node.x + 58" :y="node.y + 56" class="node-subtitle">{{ node.subTitle || '-' }}</text>
                  <text :x="node.x + 14" :y="node.y + 82" class="node-type">{{ node.tips || node.type || 'unknown' }}</text>
                </g>
              </g>
            </svg>
          </div>
        </section>

        <aside class="side-stack">
          <section class="panel">
            <div class="card-title">
              <h2>节点详情</h2>
            </div>
            <GraphNodeDetailCard :detail="selectedNodeDetail" empty-text="点击左侧节点查看详情" />
          </section>

          <section class="panel">
            <div class="card-title">
              <h2>节点列表</h2>
            </div>
            <div class="node-list">
              <button
                v-for="node in graph.nodes"
                :key="node.id"
                class="node-card node-button"
                :class="[`node-${node.state || 'normal'}`, { selected: selectedNodeId === node.id }]"
                type="button"
                @click="$emit('select-node', node.id)"
              >
                <strong>{{ node.title || node.id }}</strong>
                <span>{{ node.subTitle || '-' }}</span>
                <small>{{ node.type || '-' }}</small>
                <p v-if="node.tips">{{ node.tips }}</p>
              </button>
            </div>
          </section>
        </aside>
      </div>
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

const nodeWidth = 240
const nodeHeight = 96
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
      x: 42 + rank * 318,
      y: 42 + row * 132 + Math.max(0, 4 - columnHeight) * 36,
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

const graphViewBox = computed(() => {
  const maxX = Math.max(1200, ...nodePositions.value.map((node) => node.x + nodeWidth + 80))
  const maxY = Math.max(720, ...nodePositions.value.map((node) => node.y + nodeHeight + 80))
  return `0 0 ${maxX} ${maxY}`
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

function resetGraphView() {
  graphZoom.value = 0.92
  graphOffset.value = { x: 0, y: 0 }
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
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.graph-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(520px, 34vw);
  gap: 20px;
  align-items: start;
}

.graph-board {
  position: relative;
  overflow: auto;
  min-height: min(820px, calc(100vh - 240px));
  border-radius: 18px;
  background:
    linear-gradient(rgba(15, 23, 42, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, 0.05) 1px, transparent 1px),
    #f8fbfb;
  background-size: 28px 28px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  cursor: grab;
}

.graph-board:active {
  cursor: grabbing;
}

.graph-tools {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
}

.graph-tools span {
  padding: 0 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.graph-tools button {
  border: none;
  border-radius: 999px;
  padding: 6px 10px;
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
  box-shadow: 0 10px 22px rgba(15, 118, 110, .18);
}

.graph-tools button:active {
  transform: translateY(0);
}

.graph-svg {
  width: 100%;
  min-width: 1160px;
  min-height: 760px;
  height: max(760px, calc(100vh - 240px));
}

.graph-edge {
  fill: none;
  stroke: #94a3b8;
  stroke-width: 2;
}

.edge-label {
  fill: #475569;
  font-size: 12px;
  text-anchor: middle;
}

.graph-node {
  cursor: grab;
}

.graph-node:active {
  cursor: grabbing;
}

.graph-node rect {
  fill: #ffffff;
  stroke: rgba(15, 23, 42, 0.16);
  stroke-width: 1.4;
  filter: drop-shadow(0 10px 18px rgba(15, 23, 42, 0.10));
}

.graph-node.active rect {
  stroke: dodgerblue;
  stroke-width: 2.6;
}

.graph-node.node-error rect {
  fill: rgba(254, 242, 242, 0.96);
  stroke: rgba(185, 28, 28, 0.42);
}

.node-icon-ring {
  fill: #ecfeff;
  stroke: rgba(15, 118, 110, 0.22);
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
  font-size: 15px;
  font-weight: 800;
}

.node-subtitle,
.node-type {
  fill: #64748b;
  font-size: 12px;
}

.node-type {
  font-size: 11px;
}

.side-stack,
.node-list {
  display: grid;
  gap: 12px;
}

.side-stack {
  min-width: 0;
  position: sticky;
  top: 88px;
  max-height: calc(100vh - 116px);
  overflow: auto;
  padding-right: 2px;
}

.node-card {
  display: grid;
  gap: 4px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.node-card.node-error {
  background: rgba(254, 242, 242, 0.92);
  border-color: rgba(185, 28, 28, 0.18);
}

.node-button {
  width: 100%;
  text-align: left;
  cursor: pointer;
}

.node-button.selected {
  border-color: rgba(15, 118, 110, 0.32);
  box-shadow: inset 0 0 0 1px rgba(15, 118, 110, 0.14);
}


.compact-graph-view .page-header {
  margin-bottom: 12px;
}

.compact-graph-view .page-header h1 {
  margin: 2px 0;
  font-size: 20px;
}

.compact-graph-view .graph-shell {
  grid-template-columns: minmax(0, 1fr);
}

.compact-graph-view .side-stack {
  display: none;
}

.compact-graph-view .panel {
  padding: 12px;
  border-radius: 16px;
}

.compact-graph-view .graph-svg {
  min-width: 780px;
  height: 420px;
}

@media (max-width: 1360px) {
  .graph-shell {
    grid-template-columns: 1fr;
  }

  .side-stack {
    position: static;
    max-height: none;
    overflow: visible;
  }

  .page-header,
  .header-actions,
  .card-title {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
