import type { CoverageLanguage, CoverageModule, CoverageUnit } from '@/entities/coverage/model'
import type { RouteLocationRaw } from 'vue-router'

export interface CoverageTreeTableProps {
  units: CoverageUnit[]
  modules?: CoverageModule[]
  language?: CoverageLanguage | string
  openCodeRoute?: (unit: CoverageUnit) => RouteLocationRaw
}
