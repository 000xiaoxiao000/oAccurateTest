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

      <label v-if="!compact" class="search-box">
        <span>关键字筛选</span>
        <input v-model.trim="keyword" class="text-input" type="text" placeholder="输入名称、类型或描述" />
      </label>

      <section class="graph-panel" @click="closeContextMenu">
        <div class="graph-toolbar">
          <div>图形画布</div>
          <div class="graph-tools">
            <button type="button" @click.stop="fitGraph">适配视图</button>
            <button type="button" @click.stop="showSelectedTip" :disabled="!selectedNode">节点提示</button>
          </div>
        </div>
        <svg class="relation-graph" :viewBox="viewBox" role="img" aria-label="关系图画布">
          <defs>
            <marker id="graph-arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
              <path d="M0,0 L0,6 L9,3 z" fill="#64748b" />
            </marker>
          </defs>
          <g class="edge-layer">
            <g v-for="edge in graphEdges" :key="edge.id" :class="['graph-edge', edgeTone(edge)]">
              <line :x1="edge.source.x" :y1="edge.source.y" :x2="edge.target.x" :y2="edge.target.y" marker-end="url(#graph-arrow)" />
              <text :x="edge.labelX" :y="edge.labelY">{{ edge.label || actionText(edge.action) || '关联' }}</text>
            </g>
          </g>
          <g class="node-layer">
            <g
              v-for="node in graphNodes"
              :key="node.id"
              :class="['graph-node', nodeTone(node), selectedNode?.id === node.id && 'active']"
              :transform="`translate(${node.x}, ${node.y})`"
              @click.stop="selectGraphNode(node.id)"
              @contextmenu.prevent.stop="openContextMenu($event, node.id)"
            >
              <circle :r="node.radius" />
              <text class="node-label" text-anchor="middle" :y="node.radius + 16">{{ node.shortLabel }}</text>
              <text v-if="node.metric" class="node-metric" text-anchor="middle" y="5">{{ node.metric }}</text>
            </g>
          </g>
        </svg>
        <div v-if="contextMenu.open" class="graph-context-menu" :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }" @click.stop>
          <button type="button" @click="focusContextNode">定位节点</button>
          <button type="button" @click="copyContextNodeId">复制节点 ID</button>
          <button type="button" @click="showSelectedTip">显示节点提示</button>
        </div>
        <div v-if="tip.open && selectedNode" class="graph-tip" @click.stop="tip.open = false">
          <strong>{{ selectedNode.label || selectedNode.id }}</strong>
          <span>{{ selectedNode.description || selectedNode.type || '暂无描述' }}</span>
        </div>
      </section>

      <div id="relation-board-lists" class="layout-grid">
        <section class="panel">
          <div class="panel-head">
            <h2>节点列表</h2>
            <span>{{ filteredNodes.length }}</span>
          </div>
          <div v-if="!filteredNodes.length" class="empty-card">暂无节点数据</div>
          <div v-else class="node-grid">
            <button
              v-for="node in filteredNodes"
              :key="node.id"
              type="button"
              class="node-card"
              :class="{ active: selectedNode?.id === node.id }"
              @click="selectedId = node.id"
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
        </section>

        <section class="panel">
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
                <article v-for="edge in selectedEdges" :key="edge.id" :class="['edge-card', edgeTone(edge)]">
                  <span>{{ edge.sourceLabel || edge.source }}</span>
                  <strong>{{ edge.label || actionText(edge.action) || '关联' }}</strong>
                  <span>{{ edge.targetLabel || edge.target }}</span>
                </article>
              </div>
            </div>
          </div>
          <div v-else class="empty-card">选择一个节点后可查看详细关系</div>
        </section>
      </div>

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
              <tr v-for="edge in filteredEdges" :key="edge.id">
                <td>{{ edge.sourceLabel || edge.source }}</td>
                <td><span :class="['action-pill', edgeTone(edge)]">{{ edge.label || actionText(edge.action) || '-' }}</span></td>
                <td>{{ edge.targetLabel || edge.target }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watchEffect } from 'vue'
import { RouterLink } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

interface RelationNode {
  id: string
  label?: string
  type?: string
  description?: string
  meta?: string[]
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
}>()

const compact = computed(() => Boolean(props.compact))

const keyword = ref('')
const selectedId = ref('')
const viewBox = ref('0 0 1200 620')
const contextMenu = reactive({ open: false, x: 0, y: 0, nodeId: '' })
const tip = reactive({ open: false })

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

const selectedNode = computed(() => filteredNodes.value.find((node) => node.id === selectedId.value))
const selectedEdges = computed(() =>
  filteredEdges.value.filter((edge) => edge.source === selectedId.value || edge.target === selectedId.value),
)

const graphNodes = computed(() => {
  const count = Math.max(filteredNodes.value.length, 1)
  const centerX = 600
  const centerY = 300
  const radiusX = count > 8 ? 440 : 340
  const radiusY = count > 8 ? 220 : 180
  return filteredNodes.value.map((node, index) => {
    const angle = (Math.PI * 2 * index) / count - Math.PI / 2
    const weight = Number(node.meta?.join(' ').match(/([0-9]+(?:\.[0-9]+)?)%/)?.[1] || 0)
    return {
      ...node,
      x: centerX + Math.cos(angle) * radiusX,
      y: centerY + Math.sin(angle) * radiusY,
      radius: node.type?.includes('snapshot') ? 28 : node.type?.includes('code') ? 23 : 20,
      shortLabel: shorten(node.label || node.id, 18),
      metric: weight ? `${weight}%` : '',
    }
  })
})

const graphNodeMap = computed(() => new Map(graphNodes.value.map((node) => [node.id, node])))
const graphEdges = computed(() => filteredEdges.value
  .map((edge) => {
    const source = graphNodeMap.value.get(edge.source)
    const target = graphNodeMap.value.get(edge.target)
    if (!source || !target) return null
    return {
      ...edge,
      source,
      target,
      labelX: (source.x + target.x) / 2,
      labelY: (source.y + target.y) / 2 - 6,
    }
  })
  .filter((edge): edge is NonNullable<typeof edge> => Boolean(edge)))

function shorten(value: string, limit: number) {
  return value.length > limit ? `${value.slice(0, limit - 1)}...` : value
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
    return item
  }).filter(Boolean).join(',')
}

function selectGraphNode(nodeId: string) {
  selectedId.value = nodeId
  tip.open = false
}

function closeContextMenu() {
  contextMenu.open = false
}

function openContextMenu(event: MouseEvent, nodeId: string) {
  selectedId.value = nodeId
  contextMenu.open = true
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.nodeId = nodeId
}

function focusContextNode() {
  if (contextMenu.nodeId) selectedId.value = contextMenu.nodeId
  closeContextMenu()
}

async function copyContextNodeId() {
  if (contextMenu.nodeId) await navigator.clipboard?.writeText(contextMenu.nodeId)
  closeContextMenu()
}

function fitGraph() {
  viewBox.value = '0 0 1200 620'
}

function showSelectedTip() {
  if (!selectedNode.value) return
  tip.open = true
  closeContextMenu()
}

function handleKeydown(event: KeyboardEvent) {
  if (event.ctrlKey && event.key.toLowerCase() === 'f') {
    const input = document.querySelector<HTMLInputElement>('.search-box input')
    if (input) {
      event.preventDefault()
      input.focus()
    }
  }
  if (event.key === 'F2') {
    showSelectedTip()
  }
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))

watchEffect(() => {
  if (!selectedId.value && filteredNodes.value.length) {
    selectedId.value = filteredNodes.value[0].id
    return
  }
  if (selectedId.value && !filteredNodes.value.some((node) => node.id === selectedId.value)) {
    selectedId.value = filteredNodes.value[0]?.id || ''
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
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.summary-card strong {
  display: block;
  font-size: 24px;
  margin-top: 6px;
}

.search-box {
  display: grid;
  gap: 8px;
  margin: 18px 0;
}

.text-input {
  width: 100%;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 10px 12px;
}

.layout-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.node-grid {
  display: grid;
  gap: 12px;
}

.node-card {
  text-align: left;
  cursor: pointer;
}

.node-card.active {
  border-color: rgba(15, 118, 110, 0.45);
  box-shadow: 0 16px 32px rgba(15, 118, 110, 0.1);
}

.type-pill {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.meta-list {
  margin: 10px 0 0;
  padding-left: 18px;
}

.detail-card {
  display: grid;
  gap: 10px;
}

.connection-group {
  display: grid;
  gap: 10px;
}

.edge-list {
  display: grid;
  gap: 10px;
}

.edge-card {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 10px;
  align-items: center;
  padding: 12px;
  border-radius: 14px;
  background: #f8fbfb;
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
  margin-top: 18px;
}

.table-shell {
  overflow: auto;
}

.edge-table {
  width: 100%;
  border-collapse: collapse;
}

.edge-table th,
.edge-table td {
  padding: 12px 10px;
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
  padding: 16px;
  border-radius: 24px;
  background:
    radial-gradient(circle at 10% 20%, rgba(15, 118, 110, 0.12), transparent 28%),
    radial-gradient(circle at 80% 10%, rgba(245, 158, 11, 0.12), transparent 24%),
    rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.08);
  margin: 16px 0;
  overflow: hidden;
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
}

.graph-tools button,
.graph-context-menu button {
  border: none;
  border-radius: 999px;
  padding: 7px 10px;
  background: rgba(15, 23, 42, 0.08);
  cursor: pointer;
}

.graph-tools button:disabled {
  opacity: .5;
  cursor: not-allowed;
}

.relation-graph {
  width: 100%;
  min-height: 520px;
  border-radius: 20px;
  background:
    linear-gradient(rgba(15, 23, 42, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, 0.04) 1px, transparent 1px),
    #f8fafc;
  background-size: 32px 32px;
}

.graph-edge line {
  stroke: #94a3b8;
  stroke-width: 1.8;
  opacity: .78;
}

.graph-edge.insert line { stroke: #16a34a; }
.graph-edge.update line { stroke: #f59e0b; }
.graph-edge.delete line { stroke: #dc2626; }
.graph-edge.select line { stroke: #2563eb; }

.graph-edge.insert text { fill: #15803d; }
.graph-edge.update text { fill: #b45309; }
.graph-edge.delete text { fill: #b91c1c; }
.graph-edge.select text { fill: #1d4ed8; }

.graph-edge text {
  font-size: 11px;
  fill: #64748b;
  paint-order: stroke;
  stroke: #f8fafc;
  stroke-width: 4px;
}

.graph-node {
  cursor: pointer;
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
  min-width: 150px;
  padding: 8px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.94);
  box-shadow: 0 18px 40px rgba(15, 23, 42, .24);
}

.graph-context-menu button {
  color: #e2e8f0;
  text-align: left;
  background: transparent;
}

.graph-context-menu button:hover {
  background: rgba(255, 255, 255, 0.10);
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

.graph-tip span {
  color: #cbd5e1;
  line-height: 1.5;
}

.compact-board .graph-panel {
  margin-top: 0;
}

.compact-board .relation-graph {
  min-height: min(620px, calc(100vh - 250px));
}

.compact-board .layout-grid {
  grid-template-columns: minmax(260px, .72fr) minmax(320px, 1fr);
}

@media (max-width: 760px) {
  .relation-graph {
    min-height: 420px;
  }

  .graph-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}

</style>
