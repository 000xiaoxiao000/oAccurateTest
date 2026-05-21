<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">{{ eyebrow }}</div>
        <h1>{{ graph?.title || fallbackTitle }}</h1>
        <p class="subtext">{{ graph?.nodes.length || 0 }} 个节点 · {{ graph?.edges.length || 0 }} 条连线</p>
      </div>
      <div class="header-actions">
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
          <div class="graph-board">
            <svg class="graph-svg" viewBox="0 0 1200 720" preserveAspectRatio="xMidYMid meet">
              <defs>
                <marker :id="arrowMarkerId" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
                  <path d="M0,0 L0,6 L9,3 z" fill="#94a3b8" />
                </marker>
              </defs>
              <line
                v-for="edge in edgePositions"
                :key="`${edge.from}-${edge.to}-${edge.label}`"
                :x1="edge.x1"
                :y1="edge.y1"
                :x2="edge.x2"
                :y2="edge.y2"
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
                {{ edge.label }}
              </text>
              <g
                v-for="node in nodePositions"
                :key="node.id"
                class="graph-node"
                :class="[`node-${node.state || 'normal'}`, { active: selectedNodeId === node.id || graph.showDefaultNode?.id === node.id }]"
                @click="$emit('select-node', node.id)"
              >
                <rect :x="node.x" :y="node.y" rx="18" ry="18" width="220" height="92" />
                <text :x="node.x + 18" :y="node.y + 28" class="node-title">{{ node.title || node.id }}</text>
                <text :x="node.x + 18" :y="node.y + 52" class="node-subtitle">{{ node.subTitle || node.type || '-' }}</text>
                <text :x="node.x + 18" :y="node.y + 74" class="node-type">{{ node.type || 'unknown' }}</text>
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
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

import type { GraphEdgeSummary, GraphNodeDetailPayload, GraphViewPayload } from '@/api/types'
import GraphNodeDetailCard from '@/components/snapshot/GraphNodeDetailCard.vue'

type PositionedEdge = GraphEdgeSummary & {
  x1: number
  y1: number
  x2: number
  y2: number
  mx: number
  my: number
}

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
}>()

defineEmits<{
  (event: 'select-node', nodeId: string): void
}>()

const nodePositions = computed(() => {
  const nodes = props.graph?.nodes || []
  return nodes.map((node, index) => {
    const column = index % 4
    const row = Math.floor(index / 4)
    return {
      ...node,
      x: 40 + column * 285,
      y: 40 + row * 150,
    }
  })
})

const edgePositions = computed(() => {
  const nodeMap = new Map(nodePositions.value.map((node) => [node.id, node]))
  return (props.graph?.edges || [])
    .map((edge): PositionedEdge | null => {
      const from = nodeMap.get(edge.from)
      const to = nodeMap.get(edge.to)
      if (!from || !to) {
        return null
      }
      const x1 = from.x + 220
      const y1 = from.y + 46
      const x2 = to.x
      const y2 = to.y + 46
      return {
        ...edge,
        x1,
        y1,
        x2,
        y2,
        mx: (x1 + x2) / 2,
        my: (y1 + y2) / 2 - 8,
      }
    })
    .filter((edge): edge is PositionedEdge => edge !== null)
})

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

.secondary-link {
  color: #0f766e;
  font-weight: 700;
}

.status-card,
.panel {
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
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
}

.graph-board {
  overflow: auto;
  border-radius: 18px;
  background:
    radial-gradient(circle at top, rgba(15, 118, 110, 0.08), transparent 55%),
    linear-gradient(180deg, #f8fbfb, #eef6f6);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.graph-svg {
  width: 100%;
  min-width: 1120px;
  height: 720px;
}

.graph-edge {
  stroke: #94a3b8;
  stroke-width: 2;
}

.edge-label {
  fill: #475569;
  font-size: 12px;
  text-anchor: middle;
}

.graph-node {
  cursor: pointer;
}

.graph-node rect {
  fill: #ffffff;
  stroke: rgba(15, 23, 42, 0.12);
  stroke-width: 1.5;
}

.graph-node.active rect {
  stroke: #0f766e;
  stroke-width: 2.5;
}

.graph-node.node-error rect {
  fill: rgba(254, 242, 242, 0.96);
  stroke: rgba(185, 28, 28, 0.4);
}

.node-title {
  fill: #0f172a;
  font-size: 16px;
  font-weight: 700;
}

.node-subtitle,
.node-type {
  fill: #64748b;
  font-size: 12px;
}

.side-stack,
.node-list {
  display: grid;
  gap: 12px;
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

@media (max-width: 1100px) {
  .graph-shell {
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
