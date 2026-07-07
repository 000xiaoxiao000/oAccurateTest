import type { CoverageLanguage, CoverageModule, CoverageTreeNode, CoverageUnit } from '@/entities/coverage/model'
import type { RouteLocationRaw } from 'vue-router'

export interface CoverageTreeTableProps {
  units: CoverageUnit[]
  modules?: CoverageModule[]
  treeNodes?: CoverageTreeNode[]
  loadedNodeIds?: string[]
  loadingNodeIds?: string[]
  language?: CoverageLanguage | string
  openCodeRoute?: (unit: CoverageUnit) => RouteLocationRaw
}
