import { computed, type ComputedRef, type MaybeRefOrGetter, toValue } from 'vue'
import type {
  SnapshotCodeRelationshipGroupSummary,
  SnapshotCodeRelationshipMethodSummary,
} from '@/api/types'

type GraphNode = { id: string; label: string; type: string; description?: string; meta?: string[] }
type GraphEdge = { id: string; source: string; target: string; label: string; action?: string; sourceLabel?: string; targetLabel?: string }

export function useCoverageReportRelationshipGraph(
  paginatedRelationships: MaybeRefOrGetter<SnapshotCodeRelationshipGroupSummary[]>,
): { relationshipGraphNodes: ComputedRef<GraphNode[]>; relationshipGraphEdges: ComputedRef<GraphEdge[]> } {
  const relationshipGraphNodes = computed(() => {
    const nodes: GraphNode[] = []
    const classIds = new Set<string>()

    ;(toValue(paginatedRelationships) || []).forEach((group, groupIndex) => {
      const requestId = requestNodeId(group, groupIndex)
      const summary = summarizeGroup(group.methods)
      nodes.push({
        id: requestId,
        label: group.requestUrl || `请求入口 ${groupIndex + 1}`,
        type: 'request entry snapshot',
        description: '请求入口',
        meta: [
          `方法 ${summary.methodCount}`,
          `行 ${summary.coveredLines}/${summary.totalLines}`,
          `分支目标 ${summary.coveredBranchTargets}/${summary.totalBranchTargets}`,
        ],
      })

      ;(group.methods || []).forEach((method, methodIndex) => {
        const classId = classNodeId(groupIndex, method.className)
        if (!classIds.has(classId)) {
          classIds.add(classId)
          nodes.push({
            id: classId,
            label: shortClassName(method.className),
            type: 'code class',
            description: method.className,
            meta: [`类 ${method.className}`],
          })
        }
        nodes.push({
          id: methodNodeId(groupIndex, methodIndex, method),
          label: method.methodName || '匿名方法',
          type: 'code method',
          description: method.methodDescriptor || method.className,
          meta: [
            `类 ${shortClassName(method.className)}`,
            `行覆盖 ${formatRate(method.coveredLineCount, method.lineTotalCount)}`,
            method.branchTargetTotalCount
              ? `分支目标 ${formatRate(method.branchTargetCoveredCount, method.branchTargetTotalCount)}`
              : '分支目标 N/A',
          ],
        })
      })
    })
    return nodes
  })

  const relationshipGraphEdges = computed(() => {
    const edges: GraphEdge[] = []
    const edgeIds = new Set<string>()
    const nodeLabelMap = new Map(relationshipGraphNodes.value.map((node) => [node.id, node.label]))

    ;(toValue(paginatedRelationships) || []).forEach((group, groupIndex) => {
      const requestId = requestNodeId(group, groupIndex)
      ;(group.methods || []).forEach((method, methodIndex) => {
        const classId = classNodeId(groupIndex, method.className)
        const methodId = methodNodeId(groupIndex, methodIndex, method)
        pushEdge(edges, edgeIds, {
          id: `${requestId}->${classId}`,
          source: requestId,
          target: classId,
          label: '命中类',
          action: 'select',
          sourceLabel: nodeLabelMap.get(requestId),
          targetLabel: nodeLabelMap.get(classId),
        })
        pushEdge(edges, edgeIds, {
          id: `${classId}->${methodId}`,
          source: classId,
          target: methodId,
          label: relationshipText(method),
          action: relationshipAction(method),
          sourceLabel: nodeLabelMap.get(classId),
          targetLabel: nodeLabelMap.get(methodId),
        })
      })
    })
    return edges
  })

  return { relationshipGraphNodes, relationshipGraphEdges }
}

export function formatRate(covered = 0, total = 0) {
  if (!total) {
    return '0%'
  }
  return `${((covered / total) * 100).toFixed(1)}%`
}

export function summarizeGroup(methods: SnapshotCodeRelationshipMethodSummary[] = []) {
  return methods.reduce(
    (acc, method) => {
      acc.methodCount += 1
      acc.totalLines += method.lineTotalCount || 0
      acc.coveredLines += method.coveredLineCount || 0
      acc.totalBranches += method.branchTotalCount || 0
      acc.coveredBranches += method.branchCoveredCount || 0
      acc.totalBranchTargets += method.branchTargetTotalCount || 0
      acc.coveredBranchTargets += method.branchTargetCoveredCount || 0
      return acc
    },
    {
      methodCount: 0,
      totalLines: 0,
      coveredLines: 0,
      totalBranches: 0,
      coveredBranches: 0,
      totalBranchTargets: 0,
      coveredBranchTargets: 0,
    },
  )
}

export function relationshipTone(method: SnapshotCodeRelationshipMethodSummary) {
  if (!method.lineTotalCount) {
    return 'default'
  }
  const lineRate = method.coveredLineCount / method.lineTotalCount
  const branchRate = method.branchTargetTotalCount ? method.branchTargetCoveredCount / method.branchTargetTotalCount : 0
  if (lineRate === 1 && (!method.branchTargetTotalCount || branchRate === 1)) {
    return 'success'
  }
  if (lineRate > 0 || branchRate > 0) {
    return 'warning'
  }
  return 'default'
}

export function relationshipText(method: SnapshotCodeRelationshipMethodSummary) {
  const tone = relationshipTone(method)
  if (tone === 'success') {
    return '全覆盖'
  }
  if (tone === 'warning') {
    return '部分覆盖'
  }
  return '未覆盖'
}

function requestNodeId(group: SnapshotCodeRelationshipGroupSummary, groupIndex: number) {
  return `request:${groupIndex}:${encodeURIComponent(group.requestUrl || 'unknown')}`
}

function classNodeId(groupIndex: number, className?: string) {
  return `class:${groupIndex}:${encodeURIComponent(className || 'unknown')}`
}

function methodNodeId(groupIndex: number, methodIndex: number, method: SnapshotCodeRelationshipMethodSummary) {
  return [
    'method',
    groupIndex,
    methodIndex,
    encodeURIComponent(method.className || 'unknown'),
    encodeURIComponent(method.methodName || 'unknown'),
    encodeURIComponent(method.methodDescriptor || ''),
  ].join(':')
}

function pushEdge<T extends { id: string }>(target: T[], seen: Set<string>, edge: T) {
  if (seen.has(edge.id)) {
    return
  }
  seen.add(edge.id)
  target.push(edge)
}

function shortClassName(className?: string) {
  if (!className) {
    return '-'
  }
  const parts = className.split('.')
  return parts[parts.length - 1] || className
}

function relationshipAction(method: SnapshotCodeRelationshipMethodSummary) {
  const tone = relationshipTone(method)
  if (tone === 'success') {
    return 'select'
  }
  if (tone === 'warning') {
    return 'update'
  }
  return 'delete'
}
