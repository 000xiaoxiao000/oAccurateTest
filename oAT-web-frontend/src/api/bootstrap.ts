import { apiGet, apiGetRaw, apiPost } from './http'
import type {
  AIInteractivePagePayload,
  AIInteractiveReply,
  AIFeedbackPayload,
  AIFeedbackStats,
  AILearningReport,
  AppSummary,
  AppSettingsPayload,
  CollectorSourcesPayload,
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
    language?: string
    languageConfig?: string
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

export function fetchCollectorSources(projectId: string) {
  return apiGet<CollectorSourcesPayload>(`/api/projects/${projectId}/collector-sources`)
}

export function sendSandboxCommand(projectId: string, sessionId: string, command: 'start' | 'stop' | 'restart' | 'status') {
  return apiPost<string>(
    `/api/projects/${projectId}/online-sessions/${sessionId}/sandbox-command`,
    JSON.stringify({ command }),
    'application/json',
  )
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
    language?: string
    languageConfig?: string
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

export function fetchRepositoryBranchesPreview(
  projectId: string,
  payload: { repoUrl: string; username?: string; password?: string },
) {
  const query = new URLSearchParams()
  query.set('repoUrl', payload.repoUrl)
  if (payload.username) {
    query.set('username', payload.username)
  }
  if (payload.password) {
    query.set('password', payload.password)
  }
  return apiGet<string[]>(`/api/projects/${projectId}/repository/branches?${query.toString()}`)
}

export * from './coverage'

export * from './version'

export function uploadResource(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiPost<string>('/resource/upload', formData)
}

export function fetchMonitorSnapshotContext(projectId: string, traceId: string, appId?: string) {
  const query = new URLSearchParams()
  query.set('traceId', traceId)
  if (appId) query.set('appId', appId)
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
  payload.labels.forEach((label) => body.append('labels', label))
  payload.principals.forEach((principal) => body.append('principals', principal))
  return apiPost<string>(
    `/api/projects/${projectId}/monitor/system-snapshots`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function autoSaveMonitorSystemSnapshot(projectId: string, payload: { traceId: string; appId?: string; title?: string }) {
  const body = new URLSearchParams()
  body.set('traceId', payload.traceId)
  if (payload.appId) body.set('appId', payload.appId)
  if (payload.title) body.set('title', payload.title)
  return apiPost<string>(
    `/api/projects/${projectId}/monitor/autoSaveSystemSnapshot`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function saveMonitorMySnapshot(
  projectId: string,
  payload: {
    traceId: string
    appId?: string
    name: string
    describe?: string
    labels: string[]
  },
) {
  const body = new URLSearchParams()
  body.set('traceId', payload.traceId)
  if (payload.appId) body.set('appId', payload.appId)
  body.set('name', payload.name)
  if (payload.describe) body.set('describe', payload.describe)
  payload.labels.forEach((label) => body.append('labels', label))
  return apiPost<unknown>(
    `/api/projects/${projectId}/snapshots/my/save`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function searchKeyword(projectId: string, keyword: string, type?: 'usecase' | 'mySnapshot' | 'systemSnapshot') {
  const query = new URLSearchParams()
  query.set('keyword', keyword)
  if (type) query.set('type', type)
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
    coverageFootprints?: string[]
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
    memoryScope?: 'workbench' | 'assistant'
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

export function clearAiSessionState(projectId: string, memoryScope: 'workbench' | 'assistant' = 'workbench') {
  return apiPost<string>(
    `/api/projects/${projectId}/ai/session-state/clear`,
    JSON.stringify({ memoryScope }),
    'application/json',
  )
}

export function submitAiFeedback(payload: AIFeedbackPayload) {
  return apiPost<string>('/api/ai/feedback/submit', JSON.stringify(payload), 'application/json')
}

export function fetchAiFeedbackStats(projectId?: string) {
  const query = projectId ? `?projectId=${encodeURIComponent(projectId)}` : ''
  return apiGet<AIFeedbackStats>(`/api/ai/feedback/stats${query}`)
}

export function fetchAiLearningReport() {
  return apiGet<AILearningReport>('/api/ai/feedback/learning-report')
}

export function clearAiLearningSuggestions() {
  return apiGet<{ cleared?: number }>('/api/ai/feedback/learning-suggestions/clear')
}

export * from './snapshot'
