import type { CoverageFootprintSnapshot, SourceCoveragePayload } from '@/entities/coverage/model'
import type { TestImpactAnalysisReport } from '@/entities/coverage/model'
import type { AppSummary, ClassCoverageSummary, CoverageReportSummary, MethodCoverageSummary, ProjectSummary, SnapshotCodeRelationshipGroupSummary, SystemSnapshotSummary } from './types'

export interface PageSummary<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CoverageComparisonMethod {
  className: string
  methodName: string
  methodDesc?: string
}

export interface CoverageComparisonSummary {
  addedCount: number
  stableCount: number
  decreasedCount: number
  addedMethods: CoverageComparisonMethod[]
  stableMethods: CoverageComparisonMethod[]
  decreasedMethods: CoverageComparisonMethod[]
}

export interface CoverageVersionSummary {
  id?: string
  versionNumber?: string
  describe?: string
  repoBranch?: string
  repoCommitId?: string
  programFile?: string
  programName?: string
  createTimeText?: string
}

export interface ExtendedCoverageReportSummary extends CoverageReportSummary {
  reportType?: number
  baseVersionNumber?: string
  baseRepoCommitId?: string
  createTimeText?: string
  sourceType?: string
}

export interface CoverageOverviewPayload {
  project: ProjectSummary
  apps: AppSummary[]
  app: AppSummary
  version?: CoverageVersionSummary
  report?: ExtendedCoverageReportSummary
  versionFullReport?: ExtendedCoverageReportSummary
  currentCommitReport?: ExtendedCoverageReportSummary
  incrementalReport?: ExtendedCoverageReportSummary
  hasNewerData?: boolean
  comparison?: CoverageComparisonSummary
  versionFullComparison?: CoverageComparisonSummary
  currentCommitComparison?: CoverageComparisonSummary
  incrementalComparison?: CoverageComparisonSummary
  currentUserRole: string
  mascotPrimary?: string
}

export interface CoverageClassPageItem {
  className: string
  totalMethods: number
  coveredMethods: number
  totalLines: number
  coveredLines: number
  totalBranches: number
  coveredBranches: number
  totalBranchTargets: number
  coveredBranchTargets: number
  totalComplexity: number
  lineRate?: number
  branchRate?: number
  methodRate?: number
  hasCodeChanges?: boolean
}

export interface CoverageTreeNode {
  id: string
  parentId?: string
  name: string
  fullName?: string
  type?: string
  hasChildren: boolean
  anchor?: string
  totalMethods: number
  coveredMethods: number
  totalBranches: number
  coveredBranches: number
  totalBranchTargets: number
  coveredBranchTargets: number
  totalLines: number
  coveredLines: number
  totalComplexity: number
  lineRate?: number
  branchRate?: number
  methodRate?: number
  hasCodeChanges?: boolean
}

export interface CoverageDetailsPayload {
  report: ExtendedCoverageReportSummary
  app: AppSummary
  version?: CoverageVersionSummary
  reportNeedRegenerate?: boolean
  currentUserRole: string
  viewType: string
  className?: string
  methodName?: string
  minRate?: number
  maxRate?: number
  minBranchRate?: number
  maxBranchRate?: number
  minMethodRate?: number
  maxMethodRate?: number
  minComplexity?: number
  maxComplexity?: number
  classPage?: PageSummary<CoverageClassPageItem>
  treeNodes?: CoverageTreeNode[]
}

export interface CoverageCodePayload {
  app: AppSummary
  report: ExtendedCoverageReportSummary
  className: string
  methods: MethodCoverageSummary[]
  coloredSourceHtml?: string
  currentUserRole: string
}

export interface SearchKeywordResult {
  id: string
  appId?: string
  resultType?: 'usecase' | 'mySnapshot' | 'systemSnapshot'
  title: string
  plainTitle?: string
  titleFragment?: string
  subTitle?: string
  headImage?: string
  imagePath?: string
  description?: string
  describeFragments?: string[]
  sqlContentFragments?: string[]
  remoteContentFragments?: string[]
  directoryPath?: string
  updateTimeText?: string
  targetPath: string
}

export interface SearchKeywordPayload {
  keyword: string
  total: number
  results: SearchKeywordResult[]
}

export interface NetworkGraphNode {
  id: string
  type?: string
  label?: string
  backgroundImage?: string
  appId?: string
}

export interface NetworkGraphEdge {
  id: string
  source: string
  target: string
  type?: string
  label?: string
  action?: string
}

export interface NetworkGraphPayload {
  nodes: NetworkGraphNode[]
  edges: NetworkGraphEdge[]
}

export interface MapElementData {
  id: string
  source?: string
  target?: string
  name?: string
  describe?: string
  packageAndClassName?: string
  methodName?: string
  doLines?: number[]
  lineTotal?: number[]
  coverageRate?: number
  executeMethodTotal?: number[]
  methodTotal?: number[]
  executebranch?: number[]
  branchTotal?: number[]
  cyclo?: number
  unionCount?: number
  hotName?: string
  sqlContents?: string[]
  weight?: number
  appId?: string
  database?: string
  dataBaseType?: string
  interfaceName?: string
  action?: string
  references?: string[]
  image?: string
  x?: number
  y?: number
}

export interface MapElement {
  data: MapElementData
  group?: string
  classes?: string[]
}

export interface VersionItemSummary {
  id: string
  versionNumber: string
  describe?: string
  programFile?: string
  programName?: string
  sourceType?: string
  repoBranch?: string
  repoCommitId?: string
  createTimeText?: string
  createTimeRelativeText?: string
  fileExist: boolean
  hasReport: boolean
  current: boolean
  rawCoverageSourceTypes?: string[]
}

export interface CompareReportSummary {
  id: string
  name?: string
  createTimeText?: string
  createTimeRelativeText?: string
  sourceVersion?: string
  targetVersion?: string
  gitBranch?: string
  gitOldCommit?: string
  gitNewCommit?: string
  addClassCount: number
  updateClassCount: number
  deleteClassCount: number
  addMethodCount: number
  updateMethodCount: number
  deleteMethodCount: number
  impactCaseCount: number
}

export interface CoverageReportCard {
  id: string
  versionNumber?: string
  repoBranch?: string
  repoCommitId?: string
  createTimeText?: string
  createTimeRelativeText?: string
  sourceType?: string
  reportType?: number
  baseVersionNumber?: string
  baseRepoCommitId?: string
  snapshotCount?: number
  totalClasses: number
  coveredClasses: number
  totalMethods: number
  coveredMethods: number
  totalLines: number
  coveredLines: number
  hasNewerData: boolean
}

export interface VersionCenterPayload {
  app: AppSummary & { repoConfigured?: boolean }
  apps: AppSummary[]
  currentUserRole: string
  versions: VersionItemSummary[]
  packageVersions: VersionItemSummary[]
  compareReports: CompareReportSummary[]
  coverageReports: CoverageReportCard[]
}

export interface CompareJobSummary {
  id: string
  name?: string
  progress: number
  progressName?: string
  finish: boolean
  error: boolean
  errorMessage?: string
  log?: string
  sourceFile?: string
  targetFile?: string
  gitBranch?: string
  gitOldCommit?: string
  gitNewCommit?: string
  addClassCount: number
  updateClassCount: number
  deleteClassCount: number
  addMethodCount: number
  updateMethodCount: number
  deleteMethodCount: number
  beginTimeText?: string
  endTimeText?: string
}

export interface CompareJobPayload {
  jobId: string
  job?: CompareJobSummary
}

export interface CompareReportDetailSummary {
  jobId: string
  projectId: string
  appId: string
  jobName?: string
  sourceVersion?: string
  targetVersion?: string
  gitBranch?: string
  gitOldCommit?: string
  gitNewCommit?: string
  createTimeText?: string
  addClassCount: number
  updateClassCount: number
  deleteClassCount: number
  addMethodCount: number
  updateMethodCount: number
  deleteMethodCount: number
  impactCaseCount: number
  jobLog?: string
}

export interface MethodDifferenceSummary {
  className: string
  methodName: string
  methodDesc?: string
  model?: string
}

export interface DifferenceGroupSummary {
  className: string
  model?: string
  methods: MethodDifferenceSummary[]
}

export interface UsecaseImpactSummary {
  id: string
  title: string
  directoryPath?: string
  differences: string[]
  labels?: string[]
  available?: boolean
}

export interface EndpointLinkedUsecaseSummary {
  id: string
  title?: string
  directory?: string
}

export interface EndpointImpactSummary {
  id?: string
  endpointType?: string
  url?: string
  httpMethod?: string
  className?: string
  methodName?: string
  coverageStatus?: string
  covered?: boolean
  hitCount?: number
  matchedClasses: string[]
  matchedMethods: string[]
  linkedUsecases?: EndpointLinkedUsecaseSummary[]
}

export interface ImpactHintSummary {
  snapshotCount?: number
  hitSnapshots: string[]
  zeroHitClasses: string[]
}

export interface VersionReportDetailPayload {
  state: string
  retryMessage?: string
  app?: AppSummary
  report?: CompareReportDetailSummary
  differences?: DifferenceGroupSummary[]
  usecases?: UsecaseImpactSummary[]
  endpoints?: EndpointImpactSummary[]
  testImpact?: TestImpactAnalysisReport
  impactHints?: ImpactHintSummary
}

export interface GitPullEstimate {
  estimatedDurationMs?: number
  estimatedPackageSizeBytes?: number
  branch?: string
  commitId?: string
  packageCommitVerify?: PackageCommitVerify
}

export interface PackageCommitVerify {
  runtimeCommitId?: string
  targetCommitId?: string
  matched?: boolean
  probeOnline?: boolean
  unavailableReason?: string
}

export interface GitCommitOption {
  commitId: string
  shortCommitId?: string
  message?: string
  author?: string
}

export interface GitJobSummary {
  id: string
  progress: number
  progressName?: string
  finish: boolean
  success: boolean
  message?: string
  fileName?: string
  cachePath?: string
  md5?: string
  repoCommitId?: string
  pullDurationMs?: number
  packageSizeBytes?: number
}

export interface SystemSnapshotCodePayload {
  app: AppSummary
  snapshot: SystemSnapshotSummary
  className: string
  methods: MethodCoverageSummary[]
  sourceCoverage?: SourceCoveragePayload
  coloredSourceHtml?: string
  coverageFootprints?: CoverageFootprintSnapshot[]
  currentUserRole: string
}

export interface GraphNodeSummary {
  id: string
  title?: string
  subTitle?: string
  icon?: string
  state?: string
  tips?: string
  type?: string
  data?: unknown
}

export interface GraphEdgeSummary {
  from: string
  to: string
  label?: string
  title?: string
  description?: string
  type?: string
  count: number
}

export interface GraphViewPayload {
  title?: string
  traceId?: string
  hasCodeLayer?: boolean
  showDefaultNode?: GraphNodeSummary
  nodes: GraphNodeSummary[]
  edges: GraphEdgeSummary[]
}

export interface GraphNodeDetailPayload {
  id: string
  title?: string
  name?: string
  type?: string
  ip?: string
  logPreview?: string
  stats: Record<string, unknown>
  request?: Record<string, unknown>
  sections?: GraphNodeDetailSection[]
  sqlGroups?: GraphSqlSummary[]
  sqlStatements?: GraphSqlSummary[]
  tableOperations?: GraphTableOperationSummary[]
  remoteCalls?: GraphRemoteCallSummary[]
  redisCommands?: GraphRedisCommandSummary[]
  errors?: GraphErrorSummary[]
}

export interface GraphNodeDetailSection {
  title: string
  kind?: string
  content?: string
  fields?: GraphNodeDetailField[]
}

export interface GraphNodeDetailField {
  label: string
  value?: string
}

export interface GraphSqlSummary {
  type?: string
  sql?: string
  jdbcUrl?: string
  addressIp?: string
  port?: string
  databaseName?: string
  databaseType?: string
  executes?: string[]
  params?: string[][]
  count?: number
  useTime?: number
  beginTime?: string
  error?: GraphErrorSummary
}

export interface GraphTableOperationSummary {
  action?: string
  model?: string
  tableName?: string
  columns?: string[]
  sql?: string
}

export interface GraphRemoteCallSummary {
  type?: string
  traceNodeId?: string
  title?: string
  interfaceName?: string
  methodName?: string
  url?: string
  headers?: string
  request?: string
  response?: string
  beginTime?: string
  useTime?: number
  addressIp?: string
  remoteApp?: Record<string, string>
  extra?: Record<string, unknown>
  error?: GraphErrorSummary
}

export interface GraphRedisCommandSummary {
  type?: string
  command?: string
  host?: string
  port?: string
  beginTime?: string
  useTime?: number
  error?: GraphErrorSummary
}

export interface GraphErrorSummary {
  type?: string
  code?: string
  message?: string
  errorStack?: string
}

export interface SnapshotCommentSummary {
  time?: string
  title?: string
  type?: string
  describe?: string
  date?: string
}
