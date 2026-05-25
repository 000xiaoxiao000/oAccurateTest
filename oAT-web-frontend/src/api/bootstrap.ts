import { apiGet, apiGetRaw, apiPost } from './http'
import type {
  AIInteractivePagePayload,
  AIInteractiveReply,
  AppSummary,
  AppSettingsPayload,
  CompareJobPayload,
  CompareJobSummary,
  CoverageCodePayload,
  CoverageDetailsPayload,
  CoverageOverviewPayload,
  CoverageTreeNode,
  GitCommitOption,
  GitJobSummary,
  GitPullEstimate,
  DirectoryDeletePreview,
  GraphNodeDetailPayload,
  GraphViewPayload,
  MapElement,
  MonitorSnapshotContextPayload,
  MySnapshotDetailPayload,
  MySnapshotListPayload,
  MySnapshotCodePayload,
  MySnapshotAggregateCodePayload,
  MySnapshotCodeReportPayload,
  MySnapshotReportPayload,
  LabelSummary,
  NetworkGraphPayload,
  OnlineSessionsPayload,
  PackageCommitVerify,
  ProbeAlertsPayload,
  ProjectContext,
  ProjectLabelsPayload,
  ProjectMembersPayload,
  ProjectSummary,
  PublicSnapshotPayload,
  PublicUsecasePayload,
  RepositoryConfigPayload,
  SearchKeywordPayload,
  SnapshotDirectorySummary,
  SystemSnapshotDetailPayload,
  SystemSnapshotListPayload,
  SystemSnapshotCodePayload,
  SystemSnapshotReportPayload,
  UsecaseBootstrapPayload,
  UsecaseDetailPayload,
  UsecaseImportResult,
  UsecaseListPayload,
  UserSummary,
  VersionCenterPayload,
  VersionReportDetailPayload,
} from './types'

export function fetchCurrentUser() {
  return apiGet<UserSummary>('/api/auth/me')
}

export function fetchAccountProfile() {
  return apiGet<UserSummary>('/api/account/profile')
}

export function updateAccountProfile(payload: {
  name: string
  nickname?: string
  email: string
  phone?: string
  readme?: string
}) {
  return apiPost<UserSummary>('/api/account/profile', JSON.stringify(payload), 'application/json')
}

export function updateAccountPassword(payload: {
  oldPassword: string
  newPassword: string
  newPasswordConfirm: string
}) {
  return apiPost<string>('/api/account/password', JSON.stringify(payload), 'application/json')
}

export function login(payload: { nameOrEmail: string; password: string }) {
  return apiPost<UserSummary>('/api/auth/login', JSON.stringify(payload), 'application/json')
}

export function register(payload: {
  name: string
  nickname?: string
  email: string
  password: string
  againPassword: string
}) {
  return apiPost<string>('/api/auth/register', JSON.stringify(payload), 'application/json')
}

export function logout() {
  return apiPost<string>('/api/auth/logout')
}

export function fetchShareSnapshot(snapshotId: string) {
  return apiGet<PublicSnapshotPayload>(`/share/api/snapshot/${snapshotId}`)
}

export function fetchShareSnapshotGraph(snapshotId: string) {
  return apiGet<GraphViewPayload>(`/share/api/snapshot/${snapshotId}/graph`)
}

export function fetchShareSnapshotGraphNode(snapshotId: string, nodeId: string) {
  return apiGet<GraphNodeDetailPayload>(`/share/api/snapshot/${snapshotId}/graph/nodes/${encodeURIComponent(nodeId)}`)
}

export function fetchShareUsecase(usecaseId: string) {
  return apiGet<PublicUsecasePayload>(`/share/api/usecase/${usecaseId}`)
}

export function fetchProjects() {
  return apiGet<ProjectSummary[]>('/api/projects')
}

export function createProject(payload: { name: string; describe?: string }) {
  return apiPost<ProjectSummary>('/api/projects', JSON.stringify(payload), 'application/json')
}

export function updateProject(projectId: string, payload: { name: string; describe?: string }) {
  return apiPost<ProjectSummary>(`/api/projects/${projectId}`, JSON.stringify(payload), 'application/json')
}

export function deleteProject(projectId: string, payload: { password: string }) {
  return apiPost<string>(`/api/projects/${projectId}/delete`, JSON.stringify(payload), 'application/json')
}

export function fetchProjectContext(projectId: string) {
  return apiGet<ProjectContext>(`/api/projects/${projectId}/context`)
}

export function fetchProjectApps(projectId: string) {
  return apiGet<AppSummary[]>(`/api/projects/${projectId}/apps`)
}

export function createProjectApp(
  projectId: string,
  payload: {
    name: string
    srcName?: string
    range?: string
    describe?: string
    properties?: string
    currentVersion?: string
    currentBranch?: string
    currentCommitId?: string
    probeAlertEnabled?: boolean
    probeOfflineThresholdSeconds?: number
    probeWebhookUrl?: string
    probeAlertOnOnline?: boolean
    probeAlertOnOffline?: boolean
    probeAlertOnRecovered?: boolean
  },
) {
  return apiPost<AppSummary>(`/api/projects/${projectId}/apps`, JSON.stringify(payload), 'application/json')
}

export function deleteProjectApp(projectId: string, appId: string, payload: { password: string }) {
  return apiPost<string>(`/api/projects/${projectId}/apps/${appId}/delete`, JSON.stringify(payload), 'application/json')
}

export function fetchProjectMembers(projectId: string) {
  return apiGet<ProjectMembersPayload>(`/api/projects/${projectId}/members`)
}

export function addProjectMembers(projectId: string, userIds: string[]) {
  return apiPost<ProjectMembersPayload>(
    `/api/projects/${projectId}/members/add`,
    JSON.stringify({ userIds }),
    'application/json',
  )
}

export function removeProjectMember(projectId: string, projectMemberId: string) {
  return apiPost<ProjectMembersPayload>(
    `/api/projects/${projectId}/members/${projectMemberId}/remove`,
    '',
    'application/json',
  )
}

export function updateProjectMemberRole(projectId: string, projectMemberId: string, role: string) {
  return apiPost<ProjectMembersPayload>(
    `/api/projects/${projectId}/members/${projectMemberId}/role`,
    JSON.stringify({ role }),
    'application/json',
  )
}

export function fetchProjectLabels(projectId: string) {
  return apiGet<ProjectLabelsPayload>(`/api/projects/${projectId}/labels`)
}

export function upsertProjectLabel(projectId: string, payload: { type: string; name: string; color: string }) {
  return apiPost<ProjectLabelsPayload>(
    `/api/projects/${projectId}/labels/upsert`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function deleteProjectLabel(projectId: string, payload: { type: string; name: string }) {
  return apiPost<ProjectLabelsPayload>(
    `/api/projects/${projectId}/labels/delete`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function fetchOnlineSessions(projectId: string) {
  return apiGet<OnlineSessionsPayload>(`/api/projects/${projectId}/online-sessions`)
}

export function fetchAppSettings(projectId: string, appId: string) {
  return apiGet<AppSettingsPayload>(`/api/projects/${projectId}/apps/${appId}/settings`)
}

export function fetchProbeAlerts(projectId: string, appId: string) {
  return apiGet<ProbeAlertsPayload>(`/api/projects/${projectId}/apps/${appId}/probe-alerts`)
}

export function saveAppSettings(
  projectId: string,
  appId: string,
  payload: {
    name: string
    srcName: string
    range: string
    describe: string
    properties: string
    currentVersion: string
    currentBranch: string
    currentCommitId: string
    probeAlertEnabled: boolean
    probeOfflineThresholdSeconds?: number
    probeWebhookUrl: string
    probeAlertOnOnline: boolean
    probeAlertOnOffline: boolean
    probeAlertOnRecovered: boolean
  },
) {
  return apiPost<AppSettingsPayload>(
    `/api/projects/${projectId}/apps/${appId}/settings`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function fetchRepositoryConfig(projectId: string, appId: string) {
  return apiGet<RepositoryConfigPayload>(`/api/projects/${projectId}/apps/${appId}/repository`)
}

export function saveRepositoryConfig(
  projectId: string,
  appId: string,
  payload: {
    repoAddress: string
    repoUserName: string
    repoPassword: string
  },
) {
  return apiPost<RepositoryConfigPayload>(
    `/api/projects/${projectId}/apps/${appId}/repository`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function fetchRepositoryBranches(projectId: string, appId: string) {
  return apiGet<string[]>(`/api/projects/${projectId}/apps/${appId}/repository/branches`)
}

export function fetchCoverageOverview(
  projectId: string,
  params: { appId: string; versionNumber: string; reportId?: string; commitId?: string },
) {
  const query = new URLSearchParams()
  query.set('appId', params.appId)
  query.set('versionNumber', params.versionNumber)
  if (params.reportId) {
    query.set('reportId', params.reportId)
  }
  if (params.commitId) {
    query.set('commitId', params.commitId)
  }
  return apiGet<CoverageOverviewPayload>(`/api/projects/${projectId}/coverage/overview?${query.toString()}`)
}

export function fetchCoverageDetails(projectId: string, params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && `${value}` !== '') {
      query.set(key, String(value))
    }
  })
  return apiGet<CoverageDetailsPayload>(`/api/projects/${projectId}/coverage/details?${query.toString()}`)
}

export function fetchCoverageTreeNodes(projectId: string, params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && `${value}` !== '') {
      query.set(key, String(value))
    }
  })
  return apiGet<CoverageTreeNode[]>(`/api/projects/${projectId}/coverage/tree-nodes?${query.toString()}`)
}

export function fetchCoverageCode(projectId: string, params: { appId: string; reportId: string; className: string }) {
  const query = new URLSearchParams()
  query.set('appId', params.appId)
  query.set('reportId', params.reportId)
  query.set('className', params.className)
  return apiGet<CoverageCodePayload>(`/api/projects/${projectId}/coverage/code?${query.toString()}`)
}

export function triggerCoverageGenerate(
  projectId: string,
  payload: { appId: string; versionNumber: string; branch?: string; commitId?: string },
) {
  const body = new URLSearchParams()
  body.set('appId', payload.appId)
  body.set('versionNumber', payload.versionNumber)
  if (payload.branch) {
    body.set('branch', payload.branch)
  }
  if (payload.commitId) {
    body.set('commitId', payload.commitId)
  }
  return apiPost<string>(`/api/projects/${projectId}/coverage/generate`, body.toString(), 'application/x-www-form-urlencoded;charset=UTF-8')
}

export function triggerCoverageGenerateIncremental(
  projectId: string,
  payload: {
    appId: string
    versionNumber: string
    branch?: string
    commitId?: string
    baseVersionNumber: string
    baseCommitId?: string
  },
) {
  const body = new URLSearchParams()
  body.set('appId', payload.appId)
  body.set('versionNumber', payload.versionNumber)
  body.set('baseVersionNumber', payload.baseVersionNumber)
  if (payload.branch) {
    body.set('branch', payload.branch)
  }
  if (payload.commitId) {
    body.set('commitId', payload.commitId)
  }
  if (payload.baseCommitId) {
    body.set('baseCommitId', payload.baseCommitId)
  }
  return apiPost<string>(
    `/api/projects/${projectId}/coverage/generate-incremental`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function fetchCoverageJob(projectId: string, jobId: string) {
  return apiGetRaw<{ id?: string; progress?: number; progressName?: string; finish?: boolean; success?: boolean; message?: string }>(
    `/api/projects/${projectId}/coverage/jobs/${jobId}`,
  )
}

export function fetchCoverageTrend(projectId: string, appId: string, versionNumber: string) {
  const query = new URLSearchParams()
  query.set('appId', appId)
  query.set('versionNumber', versionNumber)
  return apiGetRaw<Array<Record<string, unknown>>>(`/api/projects/${projectId}/coverage/trend-data?${query.toString()}`)
}

export function fetchVersionCenter(projectId: string, appId: string) {
  return apiGet<VersionCenterPayload>(`/api/projects/${projectId}/apps/${appId}/version-center`)
}

export function startCompareJob(
  projectId: string,
  appId: string,
  payload: {
    mode: string
    sourceFile?: string
    targetFile?: string
    packageName?: string
    branch?: string
    oldCommit?: string
    newCommit?: string
  },
) {
  return apiPost<CompareJobPayload>(
    `/api/projects/${projectId}/apps/${appId}/compare-jobs`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function fetchCompareJob(projectId: string, appId: string, jobId: string) {
  return apiGet<CompareJobSummary>(`/api/projects/${projectId}/apps/${appId}/compare-jobs/${jobId}`)
}

export function fetchCompareReport(projectId: string, reportId: string) {
  return apiGet<VersionReportDetailPayload>(`/api/projects/${projectId}/version/reports/${reportId}`)
}

export function createVersion(projectId: string, appId: string, payload: Record<string, string>) {
  const body = new URLSearchParams()
  Object.entries(payload).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      body.set(key, value)
    }
  })
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/versions`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function setCurrentVersion(
  projectId: string,
  appId: string,
  payload: { versionNumber: string; branch?: string; commitId?: string },
) {
  const body = new URLSearchParams()
  body.set('versionNumber', payload.versionNumber)
  if (payload.branch) {
    body.set('branch', payload.branch)
  }
  if (payload.commitId) {
    body.set('commitId', payload.commitId)
  }
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/versions/current`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function deleteVersion(projectId: string, appId: string, id: string) {
  const body = new URLSearchParams()
  body.set('id', id)
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/versions/delete`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function deleteCompareReport(projectId: string, appId: string, reportId: string) {
  const body = new URLSearchParams()
  body.set('reportId', reportId)
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/version-reports/delete`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function deleteCoverageReport(projectId: string, appId: string, reportId: string) {
  const body = new URLSearchParams()
  body.set('reportId', reportId)
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/coverage-reports/delete`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function deleteVersionFile(projectId: string, appId: string, filePath: string) {
  const query = new URLSearchParams()
  query.set('filePath', filePath)
  return apiGet<string>(`/api/projects/${projectId}/apps/${appId}/version-files/delete?${query.toString()}`)
}

export function fetchGitPullEstimate(
  projectId: string,
  appId: string,
  params: { branch?: string; commitId?: string; versionNumber?: string; excludePaths?: string },
) {
  const query = new URLSearchParams()
  if (params.branch) {
    query.set('branch', params.branch)
  }
  if (params.commitId) {
    query.set('commitId', params.commitId)
  }
  if (params.versionNumber) {
    query.set('versionNumber', params.versionNumber)
  }
  if (params.excludePaths) {
    query.set('excludePaths', params.excludePaths)
  }
  return apiGet<GitPullEstimate>(`/api/projects/${projectId}/apps/${appId}/git/pull-check?${query.toString()}`)
}

export function fetchGitRecentCommits(projectId: string, appId: string, branch: string, limit = 20) {
  const query = new URLSearchParams()
  query.set('branch', branch)
  query.set('limit', String(limit))
  return apiGet<GitCommitOption[]>(`/api/projects/${projectId}/apps/${appId}/git/commits?${query.toString()}`)
}

export function fetchGitLatestCommit(projectId: string, appId: string, branch: string) {
  const query = new URLSearchParams()
  query.set('branch', branch)
  return apiGet<string>(`/api/projects/${projectId}/apps/${appId}/git/latest-commit?${query.toString()}`)
}

export function startGitPull(
  projectId: string,
  appId: string,
  payload: { branch?: string; commitId?: string; excludePaths?: string; versionNumber?: string },
) {
  const body = new URLSearchParams()
  if (payload.branch) {
    body.set('branch', payload.branch)
  }
  if (payload.commitId) {
    body.set('commitId', payload.commitId)
  }
  if (payload.excludePaths) {
    body.set('excludePaths', payload.excludePaths)
  }
  if (payload.versionNumber) {
    body.set('versionNumber', payload.versionNumber)
  }
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/git/pull`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function fetchGitPullStatus(projectId: string, appId: string, jobId: string) {
  const query = new URLSearchParams()
  query.set('jobId', jobId)
  return apiGet<GitJobSummary>(`/api/projects/${projectId}/apps/${appId}/git/jobs/${jobId}`)
}

export function deleteGitCode(projectId: string, appId: string, cachePath: string) {
  const query = new URLSearchParams()
  query.set('cachePath', cachePath)
  return apiGet<string>(`/api/projects/${projectId}/apps/${appId}/git/cache?${query.toString()}`)
}

export function verifyUploadedPackageCommit(
  projectId: string,
  appId: string,
  payload: { programFile: string; commitId?: string },
) {
  const query = new URLSearchParams()
  query.set('programFile', payload.programFile)
  if (payload.commitId) {
    query.set('commitId', payload.commitId)
  }
  return apiGet<PackageCommitVerify>(`/api/projects/${projectId}/apps/${appId}/packages/commit-verify?${query.toString()}`)
}

export function uploadResource(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiPost<string>('/resource/upload', formData)
}

export function fetchMonitorSnapshotContext(projectId: string, traceId: string) {
  const query = new URLSearchParams()
  query.set('traceId', traceId)
  return apiGet<MonitorSnapshotContextPayload>(`/api/projects/${projectId}/monitor/system-snapshot-context?${query.toString()}`)
}

export function saveMonitorSystemSnapshot(
  projectId: string,
  payload: {
    traceId: string
    appId: string
    directory: string
    title: string
    topicImage?: string
    describe?: string
    versionCycle?: number
    labels: string[]
    principals: string[]
  },
) {
  const body = new URLSearchParams()
  body.set('traceId', payload.traceId)
  body.set('appId', payload.appId)
  body.set('directory', payload.directory)
  body.set('title', payload.title)
  if (payload.topicImage) body.set('topicImage', payload.topicImage)
  if (payload.describe) body.set('describe', payload.describe)
  if (payload.versionCycle !== undefined) body.set('versionCycle', String(payload.versionCycle))
  if (payload.labels.length) body.set('labels', payload.labels.join(','))
  if (payload.principals.length) body.set('principals', payload.principals.join(','))
  return apiPost<string>(
    `/api/projects/${projectId}/monitor/system-snapshots`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function searchKeyword(projectId: string, keyword: string) {
  const query = new URLSearchParams()
  query.set('keyword', keyword)
  return apiGet<SearchKeywordPayload>(`/api/projects/${projectId}/search/keyword?${query.toString()}`)
}

export function searchTableGraph(projectId: string, database: string, table: string) {
  const query = new URLSearchParams()
  query.set('database', database)
  query.set('table', table)
  return apiGet<NetworkGraphPayload>(`/api/projects/${projectId}/search/tables?${query.toString()}`)
}

export function fetchMapHome(projectId: string) {
  return apiGetRaw<MapElement[]>(`/api/projects/${projectId}/map/home`)
}

export function fetchMapApp(projectId: string, appId: string, layers: string[]) {
  const query = new URLSearchParams()
  query.set('appId', appId)
  if (layers.length) {
    query.set('layers', layers.join(','))
  }
  return apiGetRaw<MapElement[]>(`/api/projects/${projectId}/map/apps/${appId}?${query.toString()}`)
}

export function fetchMapCode(projectId: string, traceId: string) {
  const query = new URLSearchParams()
  query.set('traceId', traceId)
  return apiGetRaw<MapElement[]>(`/api/projects/${projectId}/map/code?${query.toString()}`)
}

export function fetchMapLayerAppSnapshots(projectId: string, appId: string) {
  const query = new URLSearchParams({ appId })
  return apiGetRaw<MapElement[]>(`/api/projects/${projectId}/map/layers/app-snapshots?${query.toString()}`)
}

export function fetchMapLayerSnapshotTables(projectId: string, snapshotId: string) {
  return apiGetRaw<MapElement[]>(`/api/projects/${projectId}/map/layers/snapshots/${snapshotId}/tables`)
}

export function fetchMapLayerSnapshotRemote(projectId: string, snapshotId: string) {
  return apiGetRaw<MapElement[]>(`/api/projects/${projectId}/map/layers/snapshots/${snapshotId}/remote`)
}

export function fetchMapLayerSnapshotCode(projectId: string, snapshotId: string) {
  return apiGetRaw<MapElement[]>(`/api/projects/${projectId}/map/layers/snapshots/${snapshotId}/code`)
}

export function fetchMapLayerTableSnapshots(projectId: string, database: string, table: string) {
  const query = new URLSearchParams({ database, table })
  return apiGetRaw<MapElement[]>(`/api/projects/${projectId}/map/layers/tables/snapshots?${query.toString()}`)
}

export function fetchMapLayerDubboSnapshots(projectId: string, interfaceName: string, methodName: string) {
  const query = new URLSearchParams({ interfaceName, methodName })
  return apiGetRaw<MapElement[]>(`/api/projects/${projectId}/map/layers/dubbo/snapshots?${query.toString()}`)
}

export function fetchUsecaseList(projectId: string, params?: { directory?: string; sort?: string; keyword?: string }) {
  const query = new URLSearchParams()
  if (params?.directory) {
    query.set('directory', params.directory)
  }
  if (params?.sort) {
    query.set('sort', params.sort)
  }
  if (params?.keyword) {
    query.set('keyword', params.keyword)
  }
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return apiGet<UsecaseListPayload>(`/api/projects/${projectId}/usecases${suffix}`)
}

export function fetchUsecaseBootstrap(projectId: string, params?: { directory?: string; id?: string }) {
  const query = new URLSearchParams()
  if (params?.directory) {
    query.set('directory', params.directory)
  }
  if (params?.id) {
    query.set('id', params.id)
  }
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return apiGet<UsecaseBootstrapPayload>(`/api/projects/${projectId}/usecases/bootstrap${suffix}`)
}

export function fetchUsecaseDetail(projectId: string, usecaseId: string) {
  return apiGet<UsecaseDetailPayload>(`/api/projects/${projectId}/usecases/${usecaseId}`)
}

export function saveUsecase(
  projectId: string,
  payload: {
    id?: string
    title: string
    headImage?: string
    content?: string
    directory: string
    snapshots: string[]
    systemSnapshots: string[]
    labels: string[]
    defectsText?: string
    prdRequirementsText?: string
  },
) {
  return apiPost<string>(`/api/projects/${projectId}/usecases/save`, JSON.stringify(payload), 'application/json')
}

export function deleteUsecase(projectId: string, usecaseId: string) {
  return apiPost<string>(`/api/projects/${projectId}/usecases/${usecaseId}/delete`, '', 'application/json')
}

export function updateUsecaseShare(projectId: string, usecaseId: string, share: boolean) {
  return apiPost<string>(
    `/api/projects/${projectId}/usecases/${usecaseId}/share`,
    JSON.stringify({ share }),
    'application/json',
  )
}

export function uploadUsecases(projectId: string, directory: string, file: File) {
  const formData = new FormData()
  formData.append('directory', directory || 'root')
  formData.append('file', file)
  return apiPost<UsecaseImportResult>(`/api/projects/${projectId}/usecases/upload`, formData)
}

export function rebuildUsecaseSearchData(projectId: string) {
  return apiPost<number>(`/api/projects/${projectId}/usecases/rebuild-search-data`, '', 'application/json')
}

export function createUsecaseDirectory(projectId: string, payload: { parentId?: string; name: string }) {
  return apiPost<string>(
    `/api/projects/${projectId}/usecases/directories/create`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function renameUsecaseDirectory(
  projectId: string,
  directoryId: string,
  payload: { parentId: string; name: string },
) {
  return apiPost<string>(
    `/api/projects/${projectId}/usecases/directories/${directoryId}/rename`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function fetchUsecaseDirectoryDeletePreview(projectId: string, directoryId: string) {
  return apiGet<DirectoryDeletePreview>(
    `/api/projects/${projectId}/usecases/directories/${directoryId}/delete-preview`,
  )
}

export function deleteUsecaseDirectory(
  projectId: string,
  directoryId: string,
  payload: { parentId: string; name: string; deleteUsecases: boolean },
) {
  return apiPost<DirectoryDeletePreview>(
    `/api/projects/${projectId}/usecases/directories/${directoryId}/delete`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function fetchAiInteractiveContext(projectId: string) {
  return apiGet<AIInteractivePagePayload>(`/api/projects/${projectId}/ai/context`)
}

export function askAiInteractive(
  projectId: string,
  payload: {
    question?: string
    pageContext?: string
    imageData?: string
    sessionState?: string
    activeSessionId?: string
    sessionSortMode?: string
    timelineExpanded?: boolean
  },
) {
  return apiPost<AIInteractiveReply>(`/api/projects/${projectId}/ai/ask`, JSON.stringify(payload), 'application/json')
}

export function saveAiSessionState(projectId: string, sessionState: string) {
  return apiPost<string>(
    `/api/projects/${projectId}/ai/session-state`,
    JSON.stringify({ sessionState }),
    'application/json',
  )
}

export function clearAiSessionState(projectId: string) {
  return apiPost<string>(`/api/projects/${projectId}/ai/session-state/clear`, '', 'application/json')
}

export function fetchSystemSnapshotList(
  projectId: string,
  appId: string,
  params?: { directoryId?: string; sort?: string; keyword?: string },
) {
  const query = new URLSearchParams()
  if (params?.directoryId) {
    query.set('directoryId', params.directoryId)
  }
  if (params?.sort) {
    query.set('sort', params.sort)
  }
  if (params?.keyword) {
    query.set('keyword', params.keyword)
  }
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return apiGet<SystemSnapshotListPayload>(`/api/projects/${projectId}/apps/${appId}/snapshots${suffix}`)
}

export function fetchSystemSnapshotDetail(projectId: string, appId: string, snapshotId: string) {
  return apiGet<SystemSnapshotDetailPayload>(`/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}`)
}

export function fetchSystemSnapshotReport(projectId: string, appId: string, snapshotId: string) {
  return apiGet<SystemSnapshotReportPayload>(`/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}/report`)
}

export function calculateSystemSnapshotReport(projectId: string, appId: string, snapshotId: string) {
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}/report/calculate`,
    '',
    'application/json',
  )
}

export function fetchSystemSnapshotCode(projectId: string, appId: string, snapshotId: string, className: string) {
  const query = new URLSearchParams({ className })
  return apiGet<SystemSnapshotCodePayload>(
    `/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}/report/code?${query.toString()}`,
  )
}

export function fetchSystemSnapshotGraph(projectId: string, appId: string, snapshotId: string) {
  return apiGet<GraphViewPayload>(`/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}/graph`)
}

export function fetchSystemSnapshotGraphNode(projectId: string, appId: string, snapshotId: string, nodeId: string) {
  return apiGet<GraphNodeDetailPayload>(
    `/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}/graph/nodes/${encodeURIComponent(nodeId)}`,
  )
}

export function fetchMySnapshotGraph(projectId: string, snapshotId: string) {
  return apiGet<GraphViewPayload>(`/api/projects/${projectId}/snapshots/my/${snapshotId}/graph`)
}

export function fetchMySnapshotGraphNode(projectId: string, snapshotId: string, nodeId: string) {
  return apiGet<GraphNodeDetailPayload>(
    `/api/projects/${projectId}/snapshots/my/${snapshotId}/graph/nodes/${encodeURIComponent(nodeId)}`,
  )
}

export function saveSystemSnapshotDirectory(
  projectId: string,
  appId: string,
  payload: { id?: number; parentId?: string; name: string },
) {
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/snapshots/directories/save`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function deleteSystemSnapshotDirectory(projectId: string, appId: string, directoryId: string) {
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/snapshots/directories/${directoryId}/delete`,
    '',
    'application/json',
  )
}

export function updateSystemSnapshotBasic(
  projectId: string,
  appId: string,
  snapshotId: string,
  payload: {
    title: string
    describe?: string
    version?: string
    versionCycle?: number
    labels: string[]
    principals: string[]
  },
) {
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}/basic`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function addSystemSnapshotComment(projectId: string, appId: string, snapshotId: string, content: string) {
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}/comments`,
    JSON.stringify({ content }),
    'application/json',
  )
}

export function deleteSystemSnapshotComment(
  projectId: string,
  appId: string,
  snapshotId: string,
  payload: { content: string; dateTime: string },
) {
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}/comments/delete`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function deleteSystemSnapshot(projectId: string, appId: string, snapshotId: string) {
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}/delete`,
    '',
    'application/json',
  )
}

export function bindSystemSnapshotUsecases(
  projectId: string,
  appId: string,
  snapshotId: string,
  usecaseIds: string[],
) {
  return apiPost<number>(
    `/api/projects/${projectId}/apps/${appId}/snapshots/${snapshotId}/usecases/bind`,
    JSON.stringify({ usecaseIds }),
    'application/json',
  )
}

export function batchBindSystemSnapshotUsecases(
  projectId: string,
  appId: string,
  snapshotIds: string[],
  usecaseIds: string[],
) {
  return apiPost<number>(
    `/api/projects/${projectId}/apps/${appId}/snapshots/usecases/batch-bind`,
    JSON.stringify({ snapshotIds, usecaseIds }),
    'application/json',
  )
}

export function fetchMySnapshotList(projectId: string, params?: { sort?: string; keyword?: string; labels?: string }) {
  const query = new URLSearchParams()
  if (params?.sort) {
    query.set('sort', params.sort)
  }
  if (params?.keyword) {
    query.set('keyword', params.keyword)
  }
  if (params?.labels) {
    query.set('labels', params.labels)
  }
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return apiGet<MySnapshotListPayload>(`/api/projects/${projectId}/snapshots/my${suffix}`)
}

export function fetchMySnapshotDetail(projectId: string, snapshotId: string) {
  return apiGet<MySnapshotDetailPayload>(`/api/projects/${projectId}/snapshots/my/${snapshotId}`)
}

export function fetchMySnapshotReport(projectId: string, snapshotId: string) {
  return apiGet<MySnapshotReportPayload>(`/api/projects/${projectId}/snapshots/my/${snapshotId}/report`)
}

export function fetchMySnapshotCode(projectId: string, snapshotId: string, className: string) {
  const query = new URLSearchParams({ className })
  return apiGet<MySnapshotCodePayload>(
    `/api/projects/${projectId}/snapshots/my/${snapshotId}/report/code?${query.toString()}`,
  )
}

export function fetchMySnapshotCodeReport(projectId: string, params?: { sort?: string; keyword?: string }) {
  const query = new URLSearchParams()
  if (params?.sort) {
    query.set('sort', params.sort)
  }
  if (params?.keyword) {
    query.set('keyword', params.keyword)
  }
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return apiGet<MySnapshotCodeReportPayload>(`/api/projects/${projectId}/snapshots/my/code-report${suffix}`)
}

export function fetchMySnapshotAggregateCode(projectId: string, params: { appId: string; className: string }) {
  const query = new URLSearchParams()
  query.set('appId', params.appId)
  query.set('className', params.className)
  return apiGet<MySnapshotAggregateCodePayload>(`/api/projects/${projectId}/snapshots/my/code?${query.toString()}`)
}

export function bindMySnapshotUsecases(projectId: string, snapshotId: string, usecaseIds: string[]) {
  return apiPost<number>(
    `/api/projects/${projectId}/snapshots/my/${snapshotId}/usecases/bind`,
    JSON.stringify({ usecaseIds }),
    'application/json',
  )
}

export function updateMySnapshotBasic(
  projectId: string,
  snapshotId: string,
  payload: { name: string; describe?: string; labels: string[] },
) {
  return apiPost<string>(
    `/api/projects/${projectId}/snapshots/my/${snapshotId}/basic`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function updateMySnapshotShare(projectId: string, snapshotId: string, share: boolean) {
  return apiPost<string>(
    `/api/projects/${projectId}/snapshots/my/${snapshotId}/share`,
    JSON.stringify({ share }),
    'application/json',
  )
}

export function batchBindMySnapshotUsecases(projectId: string, snapshotIds: string[], usecaseIds: string[]) {
  return apiPost<number>(
    `/api/projects/${projectId}/snapshots/my/usecases/batch-bind`,
    JSON.stringify({ snapshotIds, usecaseIds }),
    'application/json',
  )
}

export function deleteMySnapshot(projectId: string, snapshotId: string) {
  return apiPost<string>(
    `/api/projects/${projectId}/snapshots/my/${snapshotId}/delete`,
    '',
    'application/json',
  )
}
