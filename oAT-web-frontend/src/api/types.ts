import type { CoverageFootprintSnapshot, SourceCoveragePayload } from '@/entities/coverage/model'
import type { TestImpactAnalysisReport } from '@/entities/coverage/model'

export interface ApiResponse<T> {
  result: boolean
  success?: boolean
  message?: string
  errorMessage?: string
  data: T
}

export interface UserSummary {
  id: string
  name: string
  nickname?: string
  email?: string
  header?: string
  phone?: string
  readme?: string
}

export interface ProjectSummary {
  id: string
  name: string
  describe?: string
  create?: string
  createDisplayName?: string
  memberCount: number
  createTime?: string
  updateTime?: string
}

export interface AppSummary {
  id: string
  name: string
  srcName?: string
  language?: string
  languageConfig?: string
  describe?: string
  range?: string
  onlineCount: number
  currentVersion?: string
  currentBranch?: string
  currentCommitId?: string
  repoConfigured: boolean
  probeAlertEnabled: boolean
  sourceType?: string
}

export interface AiSummary {
  enabled: boolean
  timeout: number
  interactivePath: string
  askApiPath: string
  feedbackApiBasePath: string
  mascotPrimary: string
}

export interface ProjectContext {
  currentUser: UserSummary
  project: ProjectSummary
  apps: AppSummary[]
  recentSnapshots?: SnapshotOption[]
  recentLogs?: ProjectLogItem[]
  currentUserRole: string
  ai: AiSummary
  onlineAppCount: number
  appCount: number
}

export interface ProjectLogItem {
  id?: string
  title?: string
  message?: string
  createTime?: string
  userName?: string
}

export interface ProjectMembersPayload {
  members: ProjectMemberSummary[]
  availableUsers: UserSummary[]
  currentUserRole: string
}

export interface ProjectMemberSummary {
  id: string
  projectId: string
  memberId: string
  memberName?: string
  memberEmail?: string
  role?: string
  star: boolean
  defaultProject: boolean
  createTime?: string
}

export interface ProjectLabelsPayload {
  usecaseLabels: LabelSummary[]
  snapshotLabels: LabelSummary[]
  currentUserRole: string
}

export interface LabelSummary {
  name: string
  color: string
}

export interface OnlineSessionsPayload {
  sessions: OnlineSessionSummary[]
  total: number
  currentUserRole: string
}

export interface CollectorSourcesPayload {
  sources: CollectorSourceSummary[]
  total: number
  currentUserRole: string
}

export interface CollectorSourceSummary {
  sourceId?: string
  projectId?: string
  appId?: string
  appName?: string
  language?: string
  collectorType?: 'RESIDENT' | 'BATCH'
  lastSeenTime?: number
  reportIntervalMillis?: number
  health?: 'ONLINE' | 'SILENT' | 'OFFLINE' | 'UNKNOWN'
  sessionId?: string
  addressIp?: string
  pid?: string
  agentVersion?: string
  sandboxStatus?: string
}

export interface OnlineSessionSummary {
  sessionId?: string
  appId?: string
  addressIp?: string
  agentVersion?: string
  systemDir?: string
  pid?: string
  jvmVersion?: string
  jvmOption?: string
  onlineTime?: string
  appName?: string
  projectSrcName?: string
  sandboxStatus?: string
}

export interface TraceItemSummary {
  traceId: string
  title?: string
  cacheTime?: number
  validity?: number
  index?: number
  appId?: string
  addressIp?: string
  clientIp?: string
  entryType?: string
  entryName?: string
  displayName?: string
  status?: string
}

export interface MonitorSnapshotDirectory {
  id: string
  name: string
  path?: string
}

export interface MonitorSnapshotContextPayload {
  traceId: string
  appId: string
  appName?: string
  projectSrcName?: string
  directories: MonitorSnapshotDirectory[]
  labels: LabelSummary[]
  members: ProjectMemberSummary[]
  currentUserId: string
  defaultTitle?: string
  subTitle?: string
}

export interface UsecaseImportError {
  rowNumber: number
  message: string
}

export interface UsecaseImportResult {
  successCount: number
  errors?: UsecaseImportError[]
}

export interface PulledZipItem {
  fileName: string
  cachePath: string
  size?: number
  lastModified?: number
  lastModifiedText?: string
}

export interface ApiEndpointUsecaseLink {
  id: string
  title?: string
  directory?: string
}

export interface ApiEndpointItem {
  id?: string
  endpointType?: string
  url?: string
  httpMethod?: string
  className?: string
  methodName?: string
  methodDesc?: string
  sourceType?: string
  sourceName?: string
  coverageStatus?: string
  covered?: boolean
  hitCount?: number
  mergedSourceCount?: number
  classNameList?: string[]
  methodNameList?: string[]
  methodDescList?: string[]
  sourceTypeList?: string[]
  sourceNameList?: string[]
  endpointKeyParts?: string[]
  linkedUsecases?: ApiEndpointUsecaseLink[]
}

export interface AppSettingsPayload {
  app: AppSettingsSummary
  apps: AppSummary[]
  probeAlertDashboard: ProbeAlertDashboard
  currentUserRole: string
}

export interface AppSettingsSummary {
  id: string
  name?: string
  srcName?: string
  language?: string
  languageConfig?: string
  range?: string
  describe?: string
  properties?: string
  currentVersion?: string
  currentBranch?: string
  currentCommitId?: string
  probeAlertEnabled: boolean
  probeOfflineThresholdSeconds?: number
  probeAlertOnOnline: boolean
  probeAlertOnOffline: boolean
  probeAlertOnRecovered: boolean
}

export interface ProbeAlertDashboard {
  onlineCount: number
  offlineCount: number
  recentEventCount: number
  latestEventTimeText?: string
  latestEventMessage?: string
  statuses?: ProbeStatusItem[]
  recentEvents?: ProbeAlertEventItem[]
}

export interface RepositoryConfigPayload {
  app: RepositorySummary
  currentUserRole: string
}

export interface RepositorySummary {
  appId: string
  appName?: string
  repoAddress?: string
  repoUserName?: string
  repoPassword?: string
  configured: boolean
}

export interface ProbeStatusItem {
  probeKey?: string
  status?: string
  statusLabel?: string
  statusColor?: string
  addressIp?: string
  pid?: string
  systemDir?: string
  agentVersion?: string
  sessionId?: string
  lastHeartbeatTimeText?: string
  onlineSinceText?: string
  offlineSinceText?: string
  lastAlertEventType?: string
  lastAlertTimeText?: string
}

export interface ProbeAlertEventItem {
  id: string
  eventType?: string
  eventTypeLabel?: string
  eventTypeColor?: string
  appId?: string
  appName?: string
  probeText?: string
  eventTimeText?: string
  message?: string
}

export interface ProbeAlertsPayload {
  app: AppSummary
  apps: AppSummary[]
  probeAlertDashboard: ProbeAlertDashboard
  currentUserRole: string
}

export interface UsecaseDirectory {
  id: string
  name: string
  parentId?: string
  projectId?: string
  updateTimeText?: string
  updateTimeRelativeText?: string
}

export interface UsecaseSummary {
  id: string
  projectId: string
  title: string
  headImage?: string
  content?: string
  directory: string
  snapshots?: string[]
  systemSnapshots?: string[]
  coverageFootprints?: string[]
  defects?: string[]
  prdRequirements?: string[]
  defectsText?: string
  prdRequirementsText?: string
  labels?: string[]
  authors?: string[]
  lastUpdateAuthor?: string
  createTime?: string
  updateTime?: string
  updateTimeText?: string
  share?: boolean
  snapshotCount?: number
  systemSnapshotCount?: number
  coverageFootprintCount?: number
}

export interface RelationOption {
  id: string
  name: string
  url?: string
  external: boolean
}

export interface SnapshotOption {
  id: string
  title?: string
  name?: string
  appId?: string
  traceId?: string
  describe?: string
  share?: boolean
  labels?: string[]
  createTimeText?: string
  updateTimeText?: string
  updateTimeRelativeText?: string
  apiCoverageText?: string
  versionNumber?: string
  repoBranch?: string
  repoCommitId?: string
}

export interface UsecaseListPayload {
  currentDirectory: string
  currentDirectoryName: string
  sort: string
  keyword?: string
  usecases: UsecaseSummary[]
  directories: UsecaseDirectory[]
  directoryTiers?: UsecaseDirectory[]
  maintainerNameMap: Record<string, string>
  apps: AppSummary[]
  currentUserRole: string
}

export interface UsecaseBootstrapPayload {
  currentDirectory: string
  currentDirectoryName: string
  labels: LabelSummary[]
  snapshots: SnapshotOption[]
  systemSnapshots: RelationOption[]
  usecase?: UsecaseSummary
  selectedSnapshotIds: string[]
  selectedSystemSnapshotIds: string[]
  selectedLabelNames: string[]
  defectsText?: string
  prdRequirementsText?: string
  currentUserRole: string
}

export interface UsecaseDetailPayload {
  usecase: UsecaseSummary
  lastUpdateAuthor?: UserSummary
  snapshots?: SnapshotOption[]
  systemSnapshots?: RelationOption[]
  coverageFootprints?: CoverageFootprintSnapshot[]
  labels?: LabelSummary[]
  defects?: RelationOption[]
  prdRequirements?: RelationOption[]
  contentHtml?: string
  currentUserRole: string
}

export interface DirectoryDeletePreview {
  requiresCascade: boolean
  directoryCount: number
  usecaseCount: number
}

export interface AIAbilityCard {
  title: string
  value: string
  description: string
}

export interface AIQuickLink {
  title: string
  description: string
  url: string
}

export interface AIAction {
  type: string
  title: string
  description: string
  url?: string
  requireConfirm?: boolean
  confirmText?: string
  payload?: Record<string, unknown>
}

export interface AIInteractivePagePayload {
  projectId: string
  projectName: string
  projectSummary: string
  welcomeMessage: string
  mascotHint: string
  onlineAppCount: number
  appCount: number
  appNames: string[]
  starterQuestions: string[]
  abilityCards: AIAbilityCard[]
  quickLinks: AIQuickLink[]
  mascot?: Record<string, string>
  aiTimeout: number
  sessionState?: string
}

export interface AIInteractiveReply {
  question?: string
  answer?: string
  topic?: string
  suggestions?: string[]
  quickLinks?: AIQuickLink[]
  actions?: AIAction[]
  visualizationSuggestions?: Array<Record<string, unknown>>
  confidence?: number
  usedTools?: string[]
  needMoreData?: boolean
  metadata?: Record<string, unknown>
  sessionState?: string
}

export interface AIFeedbackPayload {
  projectId: string
  question?: string
  answer?: string
  rating: number
  feedbackType: 'helpful' | 'not_helpful' | 'incorrect' | 'incomplete'
  comment?: string
  usedTools?: string
  responseTime?: number
}

export interface AISelfLearningStatus {
  knowledgeBaseSize?: number
  trackedTopics?: number
  failurePatterns?: number
  pendingSuggestions?: number
  topicGuidanceCount?: number
  topicHealth?: Record<string, string>
}

export interface AIFeedbackStats {
  total?: number
  positive?: number
  negative?: number
  neutral?: number
  satisfactionRate?: string
  selfLearning?: AISelfLearningStatus
}

export interface AILearningSuggestion {
  priority: 'HIGH' | 'MEDIUM' | 'LOW'
  title: string
  description: string
  id: string
  createdTime?: number
}

export interface AILearningReport {
  timestamp?: string
  knowledgeBaseEntries?: number
  trackedTopics?: number
  failurePatternsAnalyzed?: number
  suggestionsGenerated?: number
  topicHealthScores?: Record<string, number>
  suggestions?: AILearningSuggestion[]
}

export interface SnapshotDirectorySummary {
  id: string
  parentId?: string
  name: string
}

export interface SystemSnapshotSummary {
  id: string
  projectId: string
  appId: string
  traceId?: string
  title: string
  subTitle?: string
  topicImage?: string
  describe?: string
  directory?: string
  version?: string
  versionCycle?: number
  versionLastUpdate?: string
  versionLastUpdateText?: string
  versionLastUpdateRelativeText?: string
  versionNumber?: string
  repoBranch?: string
  repoCommitId?: string
  labels?: string[]
  principals?: string[]
  reportStatus?: number
}

export interface SystemSnapshotListPayload {
  app: AppSummary
  apps: AppSummary[]
  currentDirectory: string
  sort: string
  keyword?: string
  directories: SnapshotDirectorySummary[]
  directoryTiers: SnapshotDirectorySummary[]
  snapshots: SystemSnapshotSummary[]
  allUsecases: UsecaseSummary[]
  members: ProjectMemberLite[]
  currentUserRole: string
}

export interface ProjectMemberLite {
  memberId: string
  memberName?: string
  memberEmail?: string
  role?: string
}

export interface SnapshotDynamicItem {
  time?: string
  title?: string
  type?: string
  describe?: string
  date?: string
}

export interface SystemSnapshotDetailPayload {
  app: AppSummary
  snapshot: SystemSnapshotSummary
  labels: LabelSummary[]
  selectedLabelNames: string[]
  members: ProjectMemberLite[]
  selectedPrincipalIds: string[]
  usecases: UsecaseSummary[]
  allUsecases: UsecaseSummary[]
  coverageFootprints?: CoverageFootprintSnapshot[]
  dynamics: SnapshotDynamicItem[]
  currentUserRole: string
}

export interface MySnapshotListPayload {
  snapshots: SnapshotOption[]
  allUsecases: UsecaseSummary[]
  snapshotLabels: LabelSummary[]
  apiCoverageSummaryText?: string
  currentUserRole: string
}

export interface MySnapshotDetailPayload {
  snapshot: SnapshotOption
  createUser?: UserSummary
  labels: LabelSummary[]
  selectedLabelNames?: string[]
  usecases: UsecaseSummary[]
  allUsecases: UsecaseSummary[]
  shareUrl?: string
  currentUserRole: string
}

export interface PublicSnapshotPayload extends MySnapshotDetailPayload {
  projectId: string
}

export interface PublicUsecasePayload extends UsecaseDetailPayload {
  projectId: string
}

export interface MySnapshotReportPayload {
  snapshot: SnapshotOption
  report?: CoverageReportSummary
  classStats?: ClassCoverageSummary[]
  codeRelationships?: SnapshotCodeRelationshipGroupSummary[]
  currentUserRole: string
}

export interface MySnapshotCodePayload {
  app?: AppSummary
  snapshot: SnapshotOption
  className: string
  methods: MethodCoverageSummary[]
  sourceCoverage?: SourceCoveragePayload
  coloredSourceHtml?: string
  currentUserRole: string
}

export interface MySnapshotCodeReportPayload {
  appId?: string
  report?: CoverageReportSummary
  classStats?: ClassCoverageSummary[]
  codeRelationships?: SnapshotCodeRelationshipGroupSummary[]
  currentUserRole: string
}

export interface SnapshotCodeRelationshipGroupSummary {
  requestUrl: string
  methods: SnapshotCodeRelationshipMethodSummary[]
}

export interface SnapshotCodeRelationshipMethodSummary {
  className: string
  methodName: string
  methodDescriptor?: string
  doLines?: number[]
  lineTotalCount: number
  coveredLineCount: number
  branchTotalCount: number
  branchCoveredCount: number
  branchTargetTotalCount: number
  branchTargetCoveredCount: number
}

export interface MySnapshotAggregateCodePayload {
  app?: AppSummary
  className: string
  methods: MethodCoverageSummary[]
  sourceCoverage?: SourceCoveragePayload
  coloredSourceHtml?: string
  currentUserRole: string
}

export interface CoverageReportSummary {
  id?: string
  appId?: string
  versionNumber?: string
  repoBranch?: string
  repoCommitId?: string
  createTime?: string
  createTimeText?: string
  lastProcessedTime?: string
  totalClasses: number
  coveredClasses: number
  totalMethods: number
  coveredMethods: number
  totalBranches: number
  coveredBranches: number
  totalBranchTargets: number
  coveredBranchTargets: number
  totalLines: number
  coveredLines: number
  totalComplexity: number
  snapshotCount?: number
}

export interface ClassCoverageSummary {
  className: string
  totalMethods: number
  coveredMethods: number
  totalLines: number
  coveredLines: number
  totalBranches: number
  coveredBranches: number
  totalBranchTargets: number
  coveredBranchTargets: number
  branchRate: number
  totalComplexity: number
  lineRate?: number
  methodRate?: number
  hasCodeChanges?: boolean
}

export interface SystemSnapshotReportPayload {
  app: AppSummary
  snapshot: SystemSnapshotSummary
  report?: CoverageReportSummary
  classStats?: ClassCoverageSummary[]
  codeRelationships?: SnapshotCodeRelationshipGroupSummary[]
  coverageFootprints?: CoverageFootprintSnapshot[]
  currentUserRole: string
}

export interface MethodCoverageSummary {
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
}

export * from './types-coverage-runtime'
