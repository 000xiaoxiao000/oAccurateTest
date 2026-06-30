<template>
  <template v-if="graph">
    <section class="panel graph-topology-card">
      <div class="card-title graph-title-row">
        <div>
          <h2>节点拓扑</h2>
          <p class="subtext">默认自适应居中；支持滚轮缩放、拖拽平移和拖拽节点。</p>
        </div>
        <span class="graph-count-pill">{{ graph.nodes.length }} 节点 / {{ graph.edges.length }} 连线</span>
      </div>
      <div
        class="graph-board monitor-graph-board"
        @wheel.prevent="$emit('graph-wheel', $event)"
        @pointerdown="$emit('graph-pan-start', $event)"
        @pointermove="$emit('graph-pan-move', $event)"
        @pointerup="$emit('graph-pan-end')"
        @pointerleave="$emit('graph-pan-end')"
      >
        <div class="graph-tools">
          <span>{{ Math.round(graphZoom * 100) }}%</span>
          <button type="button" @click="$emit('fit-graph')">适配</button>
          <button type="button" @click="$emit('zoom-graph', 0.15)">放大</button>
          <button type="button" @click="$emit('zoom-graph', -0.15)">缩小</button>
          <button type="button" @click="$emit('reset-graph')">重置</button>
        </div>
        <svg class="graph-svg" :viewBox="graphViewBox" preserveAspectRatio="xMidYMid meet">
          <defs>
            <marker id="monitorArrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
              <path d="M0,0 L0,6 L9,3 z" fill="#94a3b8" />
            </marker>
          </defs>
          <g :transform="graphTransform">
            <path
              v-for="edge in edgePositions"
              :key="`${edge.from}-${edge.to}-${edge.label}`"
              :d="edge.path"
              class="graph-edge"
              marker-end="url(#monitorArrow)"
            />
            <text v-for="edge in edgePositions" :key="`${edge.from}-${edge.to}-label`" :x="edge.mx" :y="edge.my" class="edge-label">
              {{ edge.label || edge.type || edge.count }}
            </text>
            <g
              v-for="node in nodePositions"
              :key="node.id"
              class="graph-node"
              :class="[`node-${node.state || 'normal'}`, { active: selectedNodeId === node.id }]"
              @click.stop="$emit('select-node', node.id)"
            >
              <title>{{ graphNodeTooltip(node) }}</title>
              <rect :x="node.x" :y="node.y" rx="16" ry="16" :width="graphNodeWidth" :height="graphNodeHeight" />
              <circle :cx="node.x + 34" :cy="node.y + 34" r="18" class="node-icon-ring" />
              <text :x="node.x + 34" :y="node.y + 41" class="node-icon">{{ iconGlyph(node.icon || node.type) }}</text>
              <text :x="node.x + 64" :y="node.y + 30" class="node-title">{{ compactGraphText(node.title || node.id, 20) }}</text>
              <text :x="node.x + 64" :y="node.y + 54" class="node-subtitle">{{ compactGraphText(node.subTitle || '-', 24) }}</text>
              <text :x="node.x + 16" :y="node.y + 82" class="node-type">{{ compactGraphText(node.tips || node.type || '', 30) }}</text>
            </g>
          </g>
        </svg>
      </div>
    </section>

    <section class="panel bottom-panel">
      <div class="category-header">
        <h3>全部节点</h3>
        <span class="category-count">{{ sortedGraphNodes.length }} 个节点</span>
      </div>
      <div class="node-tabs-bar">
        <button
          v-for="node in sortedGraphNodes"
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
      <div v-if="selectedNodeId" class="node-content-panel">
        <div class="node-content-header">
          <span class="node-content-icon">{{ iconGlyph(selectedGraphNode?.icon || selectedGraphNode?.type) }}</span>
          <div class="node-content-meta">
            <strong>{{ selectedGraphNode?.title || selectedNodeId }}</strong>
            <span>{{ selectedGraphNode?.subTitle || '-' }}</span>
          </div>
          <span class="node-type-badge">{{ selectedGraphNode?.type || '-' }}</span>
        </div>
        <div v-if="nodeDetailLoading" class="node-content-body">
          <div class="status-card loading-card">
            <div class="loading-spinner"></div>
            <span>正在加载节点详情...</span>
          </div>
        </div>
        <div v-else-if="nodeDetailError" class="node-content-body">
          <div class="status-card error">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
            {{ nodeDetailError }}
          </div>
        </div>
        <div v-else-if="selectedNodeDetail" class="node-content-body">
          <GraphNodeDetailCard :detail="selectedNodeDetail" empty-text="" />
        </div>
        <div v-else class="node-content-empty">
          点击图谱中的节点查看详细信息
        </div>
      </div>
    </section>
  </template>
</template>

<script setup lang="ts">
import type { GraphEdgeSummary, GraphNodeDetailPayload, GraphNodeSummary, GraphViewPayload } from '@/api/types'
import GraphNodeDetailCard from '@/components/snapshot/GraphNodeDetailCard.vue'

type PositionedNode = GraphNodeSummary & { x: number; y: number; rank: number }
type PositionedEdge = GraphEdgeSummary & { path: string; mx: number; my: number }

defineProps<{
  graph: GraphViewPayload | null
  graphZoom: number
  graphViewBox: string
  graphTransform: string
  graphNodeWidth: number
  graphNodeHeight: number
  edgePositions: PositionedEdge[]
  nodePositions: PositionedNode[]
  sortedGraphNodes: GraphNodeSummary[]
  selectedNodeId: string
  selectedGraphNode: GraphNodeSummary | null
  selectedNodeDetail: GraphNodeDetailPayload | null
  nodeDetailLoading: boolean
  nodeDetailError: string
}>()

defineEmits<{
  'fit-graph': []
  'zoom-graph': [delta: number]
  'reset-graph': []
  'graph-wheel': [event: WheelEvent]
  'graph-pan-start': [event: PointerEvent]
  'graph-pan-move': [event: PointerEvent]
  'graph-pan-end': []
  'select-node': [nodeId: string]
}>()

function compactGraphText(value: string, maxLength: number) {
  if (!value) return ''
  return value.length > maxLength ? `${value.slice(0, maxLength - 1)}…` : value
}

function graphNodeTooltip(node: GraphNodeSummary) {
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
  return 'N'
}
</script>

<style scoped>
.panel,
.status-card {
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, .08);
  transition: all 200ms ease;
}

.panel,
.status-card {
  padding: 16px;
}

.status-card {
  border-radius: 16px;
  background: rgba(241, 245, 249, .6);
  text-align: center;
  font-size: 14px;
  color: #64748b;
}

.status-card.error {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(254, 226, 226, .8);
  color: #b91c1c;
}

.subtext {
  color: #64748b;
}

.graph-board {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border-radius: 16px;
  background:
    radial-gradient(circle at 24px 24px, rgba(15, 118, 110, .08) 1.5px, transparent 1.5px),
    linear-gradient(rgba(15, 23, 42, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, 0.035) 1px, transparent 1px),
    linear-gradient(135deg, #fbfefd, #f5fbfb);
  background-size: 56px 56px, 28px 28px, 28px 28px, auto;
  border: 1px solid rgba(15, 23, 42, 0.07);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .8);
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
  gap: 5px;
  padding: 6px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(14px);
}

.graph-tools span {
  min-width: 42px;
  border-radius: 999px;
  padding: 5px 7px;
  background: #0f172a;
  color: #fff;
  font-size: 11px;
  font-weight: 900;
  text-align: center;
  white-space: nowrap;
}

.graph-tools button {
  border: none;
  border-radius: 999px;
  padding: 5px 9px;
  background: #eef7f7;
  color: #0f766e;
  font-size: 11px;
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
  height: 100%;
  display: block;
}

.monitor-graph-board {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 360px;
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
  cursor: pointer;
}

.graph-node rect {
  fill: rgba(255, 255, 255, .98);
  stroke: rgba(15, 118, 110, 0.16);
  stroke-width: 1.5;
  filter: drop-shadow(0 14px 24px rgba(15, 23, 42, 0.10));
}

.graph-node.active rect {
  stroke: #2563eb;
  stroke-width: 2.8;
  filter: drop-shadow(0 18px 30px rgba(37, 99, 235, 0.18));
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

.graph-topology-card {
  display: flex;
  flex-direction: column;
  margin-bottom: 14px;
}

.card-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
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

.bottom-panel {
  padding: 0;
  overflow: hidden;
}

.category-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 16px 18px 0;
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
  padding: 0 18px 12px;
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
  margin: 12px 18px 18px;
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

.loading-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(248, 250, 252, .95), rgba(241, 245, 249, .9));
  border: 1px solid rgba(15, 23, 42, .06);
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(15, 118, 110, .2);
  border-top-color: #0f766e;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 980px) {
  .monitor-graph-board,
  .graph-board {
    min-height: 320px;
    height: 320px;
    flex: none;
  }
}
</style>
