import { computed, ref, watch, type MaybeRefOrGetter, toValue } from 'vue'
import type { GraphEdgeSummary, GraphNodeSummary, GraphViewPayload } from '@/api/types'

export type PositionedEdge = GraphEdgeSummary & {
  path: string
  mx: number
  my: number
}

export type PositionedNode = GraphNodeSummary & {
  x: number
  y: number
  rank: number
}

export function useGraphViewLayout(
  graph: MaybeRefOrGetter<GraphViewPayload | undefined>,
  selectedNodeId: MaybeRefOrGetter<string | undefined>,
  selectNode: (nodeId: string) => void,
) {
  const nodeWidth = 230
  const nodeHeight = 92
  const graphZoom = ref(1)
  const graphOffset = ref({ x: 0, y: 0 })
  const graphPan = ref<{ startX: number; startY: number; originX: number; originY: number } | null>(null)
  const nodePositionOverrides = ref<Record<string, { x: number; y: number }>>({})
  const nodeDrag = ref<{ id: string; startX: number; startY: number; originX: number; originY: number } | null>(null)

  const sortedNodes = computed<GraphNodeSummary[]>(() => {
    const currentGraph = toValue(graph)
    const nodes = currentGraph?.nodes || []
    const { ranks } = buildGraphRanks(nodes, currentGraph?.edges || [])
    return [...nodes].sort((a, b) => {
      const rankA = ranks.get(a.id) ?? 999
      const rankB = ranks.get(b.id) ?? 999
      if (rankA !== rankB) return rankA - rankB
      return nodes.indexOf(a) - nodes.indexOf(b)
    })
  })

  const selectedNode = computed(() =>
    sortedNodes.value.find((node) => node.id === toValue(selectedNodeId)) ?? null,
  )

  const nodePositions = computed<PositionedNode[]>(() => {
    const currentGraph = toValue(graph)
    const nodes = currentGraph?.nodes || []
    const { ranks } = buildGraphRanks(nodes, currentGraph?.edges || [])
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
    return (toValue(graph)?.edges || [])
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

  watch(() => toValue(graph), () => {
    resetGraphLayout()
    resetGraphView()
  })

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
    selectNode(node.id)
    nodeDrag.value = {
      id: node.id,
      startX: event.clientX,
      startY: event.clientY,
      originX: node.x,
      originY: node.y,
    }
  }

  return {
    nodeWidth,
    nodeHeight,
    graphZoom,
    sortedNodes,
    selectedNode,
    nodePositions,
    graphViewBox,
    graphTransform,
    edgePositions,
    zoomGraph,
    fitGraph,
    resetGraphView,
    resetGraphLayout,
    handleGraphWheel,
    startGraphPan,
    moveGraphPan,
    endGraphPan,
    startNodeDrag,
  }
}

function buildGraphRanks(nodes: GraphNodeSummary[], edges: GraphEdgeSummary[]) {
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
  return { ranks }
}

function clampZoom(value: number) {
  return Math.min(2.2, Math.max(0.45, value))
}
