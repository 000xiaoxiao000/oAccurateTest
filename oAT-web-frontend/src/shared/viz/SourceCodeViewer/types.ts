export interface SourceCoverageLine {
  line: number
  text: string
  hits?: number
  cases?: string[]
  changed?: boolean
}

export interface SourceCoverageBranch {
  line: number
  groupId?: string
  branchIndex?: number
  hits?: number
}

export interface SourceCoveragePayload {
  language?: string
  sourcePath?: string
  lines?: SourceCoverageLine[]
  branches?: SourceCoverageBranch[]
}
