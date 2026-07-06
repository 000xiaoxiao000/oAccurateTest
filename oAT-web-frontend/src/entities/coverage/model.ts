export type CoverageLanguage = 'JAVA' | 'FRONTEND' | 'GO' | 'PYTHON' | 'CPP'

export type TestStage = 'unit' | 'api' | 'e2e' | 'manual' | 'regression' | 'unknown'

export interface CoverageIngestPayload {
  projectId: string
  appId: string
  language: CoverageLanguage
  versionNumber?: string
  branch?: string
  commitId?: string
  buildId?: string
  testStage?: TestStage | string
  caseName?: string
  traceId?: string
  requestId?: string
  timestamp?: number
  payload: unknown
}

export interface CoverageIngestResult {
  rawReportId: string
}

export interface CoverageFootprint {
  traceId?: string
  caseName?: string
  testStage?: string
  buildId?: string
  timestamp?: number
}

export interface CoverageFootprintSnapshot {
  snapshotKey?: string
  rawReportId?: string
  projectId?: string
  appId?: string
  appName?: string
  language?: CoverageLanguage | string
  versionNumber?: string
  branch?: string
  commitId?: string
  buildId?: string
  testStage?: string
  caseName?: string
  timestamp?: number
}

export interface CoverageLine {
  line: number
  hits: number
  footprints?: CoverageFootprint[]
}

export interface CoverageBranch {
  line: number
  branchIndex: number
  groupId?: string
  hits: number
  footprints?: CoverageFootprint[]
}

export interface CoverageFunction {
  signature?: string
  startLine?: number
  endLine?: number
  complexity?: number
  lines?: CoverageLine[]
  branches?: CoverageBranch[]
}

export interface CoverageMethodSummary {
  methodName: string
  methodDesc?: string
  totalLines: number
  coveredLines: number
  totalBranches: number
  coveredBranches: number
  complexity: number
  covered: boolean
  totalBranchTargets: number
  coveredBranchTargets: number
  branchRate?: number
  hasCodeChanges?: boolean
  startLine?: number
  endLine?: number
}

export interface CoverageUnit {
  language?: CoverageLanguage | string
  unitKey?: string
  displayName?: string
  sourcePath?: string
  functions?: CoverageFunction[]
  lines?: CoverageLine[]
  branches?: CoverageBranch[]
}

export interface CoverageModule {
  language?: CoverageLanguage | string
  moduleKey?: string
  units?: CoverageUnit[]
}

export interface CoverageUnitsPayload {
  reportId: string
  language?: CoverageLanguage | string
  units: CoverageUnit[]
  page?: number
  size?: number
  totalElements?: number
  totalPages?: number
}

export interface CoverageModulesPayload {
  reportId: string
  language?: CoverageLanguage | string
  modules: CoverageModule[]
}

export interface CoverageMethodsPayload {
  reportId: string
  unitKey?: string
  language?: CoverageLanguage | string
  methods: CoverageMethodSummary[]
}

export interface CoverageReportSummary {
  id?: string
  appId?: string
  versionNumber?: string
  repoBranch?: string
  repoCommitId?: string
  createTimeText?: string
  sourceType?: string
  language?: CoverageLanguage | string
  buildId?: string
  testStage?: string
  lastProcessedTime?: string
  totalClasses?: number
  coveredClasses?: number
  totalMethods?: number
  coveredMethods?: number
  totalBranches?: number
  coveredBranches?: number
  totalBranchTargets?: number
  coveredBranchTargets?: number
  totalLines?: number
  coveredLines?: number
  totalComplexity?: number
  snapshotCount?: number
  reportType?: number
  baseVersionNumber?: string
  baseRepoCommitId?: string
}

export interface CoverageAppSummary {
  id?: string
  name?: string
  currentVersion?: string
  currentBranch?: string
  currentCommitId?: string
  sourceType?: string
  language?: CoverageLanguage | string
  languageConfig?: string
}

export interface CoverageReportMetadata {
  report?: CoverageReportSummary
  app?: CoverageAppSummary
  currentUserRole?: string
}

export interface SourceCoverageLine {
  line: number
  text?: string
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
  language?: CoverageLanguage | string
  sourcePath?: string
  lines?: SourceCoverageLine[]
  branches?: SourceCoverageBranch[]
}

export interface BuildSession {
  buildId?: string
  appId?: string
  versionNumber?: string
  repoBranch?: string
  repoCommitId?: string
  firstReportTime?: string | number
  lastReportTime?: string | number
  testStages?: string[]
  reportIds?: string[]
  totalReports?: number
  totalLines?: number
  coveredLines?: number
  totalMethods?: number
  coveredMethods?: number
  totalBranchTargets?: number
  coveredBranchTargets?: number
}

export interface BuildSessionsPayload {
  appId: string
  sessions: BuildSession[]
}

export interface TestGapUnit {
  unitKey?: string
  displayName?: string
  sourcePath?: string
  totalLines?: number
  uncoveredLines?: number
  lineCoverageRate?: number
  uncoveredLineNumbers?: number[]
}

export interface TestGapReport {
  reportId?: string
  language?: CoverageLanguage | string
  totalUnits?: number
  riskyUnits?: number
  totalLines?: number
  uncoveredLines?: number
  lineCoverageRate?: number
  units?: TestGapUnit[]
}

export interface QualityGateResult {
  reportId?: string
  appId?: string
  versionNumber?: string
  repoCommitId?: string
  language?: string
  buildId?: string
  testStage?: string
  passed?: boolean
  minLineCoverageRate?: number
  actualLineCoverageRate?: number
  uncoveredLines?: number
  riskyUnits?: number
  reasons?: string[]
}

export interface TestImpactCase {
  usecaseId?: string
  caseName?: string
  testStage?: string
  buildId?: string
  traceId?: string
  coveredChangedLines?: number
  impactedUnits?: string[]
}

export interface TestImpactAnalysisReport {
  reportId?: string
  language?: CoverageLanguage | string
  changedLineCount?: number
  impactedCaseCount?: number
  impactedTraceCount?: number
  impactedCases?: TestImpactCase[]
  reasons?: string[]
}
