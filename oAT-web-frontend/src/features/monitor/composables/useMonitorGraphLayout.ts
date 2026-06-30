import { computed, ref, type Ref } from 'vue'

import type { GraphEdgeSummary, GraphNodeSummary, GraphViewPayload } from '@/api/types'

export type MonitorPositionedNode = GraphNodeSummary & { x: number; y: number; rank: number }
export type MonitorPositionedEdge = GraphEdgeSummary & { path: string; mx: number; my: number }

export function useMonitorGraphLayout(graph: Ref<GraphViewPayload | null>) {
  const graphNodeWidth = 230
  const graphNodeHeight = 92
  const graphZoom = ref(0.92)
  const graphOffset = ref({ x: 0, y: 0 })
  const graphPan = ref<{ startX: number; startY: number; originX: number; originY: number } | null>(null)

  function buildNodeRanks(nodes: GraphNodeSummary[], edges: GraphEdgeSummary[]) {
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
    return ranks
  }

  const sortedGraphNodes = computed<GraphNodeSummary[]>(() => {
    const nodes = graph.value?.nodes || []
    const ranks = buildNodeRanks(nodes, graph.value?.edges || [])
    return [...nodes].sort((a, b) => {
      const rankA = ranks.get(a.id) ?? 999
      const rankB = ranks.get(b.id) ?? 999
      if (rankA !== rankB) return rankA - rankB
      return nodes.indexOf(a) - nodes.indexOf(b)
    })
  })

  const nodePositions = computed<MonitorPositionedNode[]>(() => {
    const nodes = graph.value?.nodes || []
    const ranks = buildNodeRanks(nodes, graph.value?.edges || [])
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
      return {
        ...node,
        rank,
        x: 96 + rank * 310,
        y: 96 + row * 126 + Math.max(0, 3 - columnHeight) * 34,
      }
    })
  })

  const graphViewBox = computed(() => {
    const maxX = Math.max(1200, ...nodePositions.value.map((node) => node.x + graphNodeWidth + 80))
    const maxY = Math.max(700, ...nodePositions.value.map((node) => node.y + graphNodeHeight + 80))
    return `0 0 ${maxX} ${maxY}`
  })

  const graphTransform = computed(() => `translate(${graphOffset.value.x} ${graphOffset.value.y}) scale(${graphZoom.value})`)

  const edgePositions = computed<MonitorPositionedEdge[]>(() => {
    const nodeMap = new Map(nodePositions.value.map((node) => [node.id, node]))
    return (graph.value?.edges || [])
      .map((edge) => {
        const from = nodeMap.get(edge.from)
        const to = nodeMap.get(edge.to)
        if (!from || !to) return null
        const x1 = from.x + graphNodeWidth
        const y1 = from.y + graphNodeHeight / 2
        const x2 = to.x
        const y2 = to.y + graphNodeHeight / 2
        const dx = Math.max(56, Math.abs(x2 - x1) / 2)
        return {
          ...edge,
          path: `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`,
          mx: (x1 + x2) / 2,
          my: (y1 + y2) / 2 - 10,
        }
      })
      .filter((edge): edge is MonitorPositionedEdge => edge !== null)
  })

  function clampGraphZoom(value: number) {
    return Math.min(2.4, Math.max(0.45, value))
  }

  function zoomGraph(delta: number) {
    graphZoom.value = clampGraphZoom(graphZoom.value + delta)
  }

  function fitMonitorGraph() {
    const nodes = nodePositions.value
    if (!nodes.length) {
      graphZoom.value = 0.92
      graphOffset.value = { x: 0, y: 0 }
      return
    }
    const minX = Math.min(...nodes.map((node) => node.x))
    const minY = Math.min(...nodes.map((node) => node.y))
    const maxX = Math.max(...nodes.map((node) => node.x + graphNodeWidth))
    const maxY = Math.max(...nodes.map((node) => node.y + graphNodeHeight))
    const boundsW = maxX - minX
    const boundsH = maxY - minY
    const viewW = Math.max(920, boundsW + 280)
    const viewH = Math.max(560, boundsH + 280)
    const zoom = Math.min(2.2, Math.max(0.45, Math.min((viewW - 180) / Math.max(boundsW, 1), (viewH - 180) / Math.max(boundsH, 1), 1.05)))
    graphZoom.value = zoom
    graphOffset.value = {
      x: (viewW - boundsW * zoom) / 2 - minX * zoom,
      y: (viewH - boundsH * zoom) / 2 - minY * zoom,
    }
  }

  function resetGraphView() {
    fitMonitorGraph()
  }

  function handleGraphWheel(event: WheelEvent) {
    zoomGraph(event.deltaY > 0 ? -0.1 : 0.1)
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
    if (!graphPan.value) return
    graphOffset.value = {
      x: graphPan.value.originX + event.clientX - graphPan.value.startX,
      y: graphPan.value.originY + event.clientY - graphPan.value.startY,
    }
  }

  function endGraphPan() {
    graphPan.value = null
  }

  return {
    graphNodeWidth,
    graphNodeHeight,
    graphZoom,
    sortedGraphNodes,
    nodePositions,
    graphViewBox,
    graphTransform,
    edgePositions,
    fitMonitorGraph,
    zoomGraph,
    resetGraphView,
    handleGraphWheel,
    startGraphPan,
    moveGraphPan,
    endGraphPan,
  }
}
