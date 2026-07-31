import { ref } from 'vue'
import type { Ref } from 'vue'
import { defineStore } from 'pinia'

import {
  addProjectMembers,
  createProject,
  createProjectApp,
  addSystemSnapshotComment,
  askAiInteractive,
  batchBindMySnapshotUsecases,
  batchBindSystemSnapshotUsecases,
  bindMySnapshotUsecases,
  bindSystemSnapshotUsecases,
  clearAiSessionState,
  createUsecaseDirectory,
  deleteMySnapshot,
  deleteProjectLabel,
  deleteProject,
  deleteProjectApp,
  deleteSystemSnapshot,
  deleteSystemSnapshotComment,
  deleteSystemSnapshotDirectory,
  deleteUsecase,
  deleteUsecaseDirectory,
  calculateSystemSnapshotReport,
  fetchAppSettings,
  fetchAiInteractiveContext,
  fetchCollectorSources,
  fetchMySnapshotDetail,
  fetchMySnapshotCode,
  fetchMySnapshotReport,
  fetchMySnapshotGraph,
  fetchMySnapshotGraphNode,
  fetchMySnapshotList,
  fetchProbeAlerts,
  fetchProjectApps,
  fetchProjectContext,
  fetchProjectLabels,
  fetchProjectMembers,
  fetchProjects,
  fetchOnlineSessions,
  fetchRepositoryBranches,
  fetchRepositoryConfig,
  fetchSystemSnapshotCode,
  fetchSystemSnapshotDetail,
  fetchSystemSnapshotGraph,
  fetchSystemSnapshotGraphNode,
  fetchSystemSnapshotList,
  fetchSystemSnapshotReport,
  fetchUsecaseDirectoryDeletePreview,
  fetchUsecaseBootstrap,
  fetchUsecaseDetail,
  fetchUsecaseList,
  rebuildUsecaseSearchData,
  removeProjectMember,
  renameUsecaseDirectory,
  saveAiSessionState,
  saveAppSettings,
  sendSandboxCommand,
  saveRepositoryConfig,
  saveSystemSnapshotDirectory,
  saveUsecase,
  updateUsecaseShare,
  uploadUsecases,
  updateProject,
  updateMySnapshotBasic,
  updateMySnapshotShare,
  updateSystemSnapshotBasic,
  updateProjectMemberRole,
  upsertProjectLabel,
  saveMySnapshotAsSystemSnapshot,
} from '@/api/bootstrap'
import type {
  AIInteractivePagePayload,
  AIInteractiveReply,
  AppSummary,
  AppSettingsPayload,
  CollectorSourcesPayload,
  DirectoryDeletePreview,
  GraphNodeDetailPayload,
  GraphViewPayload,
  MySnapshotDetailPayload,
  MySnapshotListPayload,
  MySnapshotCodePayload,
  MySnapshotReportPayload,
  ProbeAlertsPayload,
  ProjectContext,
  ProjectLabelsPayload,
  ProjectMembersPayload,
  ProjectSummary,
  OnlineSessionsPayload,
  RepositoryConfigPayload,
  SystemSnapshotCodePayload,
  SystemSnapshotDetailPayload,
  SystemSnapshotListPayload,
  SystemSnapshotReportPayload,
  UsecaseBootstrapPayload,
  UsecaseDetailPayload,
  UsecaseListPayload,
} from '@/api/types'

export const useProjectStore = defineStore('project', () => {
  const projects = ref<ProjectSummary[]>([])
  const contextByProjectId = ref<Record<string, ProjectContext>>({})
  const appsByProjectId = ref<Record<string, AppSummary[]>>({})
  const membersByProjectId = ref<Record<string, ProjectMembersPayload>>({})
  const labelsByProjectId = ref<Record<string, ProjectLabelsPayload>>({})
  const onlineSessionsByProjectId = ref<Record<string, OnlineSessionsPayload>>({})
  const collectorSourcesByProjectId = ref<Record<string, CollectorSourcesPayload>>({})
  const appSettingsByKey = ref<Record<string, AppSettingsPayload>>({})
  const probeAlertsByKey = ref<Record<string, ProbeAlertsPayload>>({})
  const repositoryByKey = ref<Record<string, RepositoryConfigPayload>>({})
  const usecaseListByProjectId = ref<Record<string, UsecaseListPayload>>({})
  const usecaseBootstrapByKey = ref<Record<string, UsecaseBootstrapPayload>>({})
  const usecaseDetailByKey = ref<Record<string, UsecaseDetailPayload>>({})
  const aiContextByProjectId = ref<Record<string, AIInteractivePagePayload>>({})
  const aiLastReplyByProjectId = ref<Record<string, AIInteractiveReply>>({})
  const aiAssistantLastReplyByProjectId = ref<Record<string, AIInteractiveReply>>({})
  const systemSnapshotListByKey = ref<Record<string, SystemSnapshotListPayload>>({})
  const systemSnapshotDetailByKey = ref<Record<string, SystemSnapshotDetailPayload>>({})
  const systemSnapshotReportByKey = ref<Record<string, SystemSnapshotReportPayload>>({})
  const systemSnapshotCodeByKey = ref<Record<string, SystemSnapshotCodePayload>>({})
  const systemSnapshotGraphByKey = ref<Record<string, GraphViewPayload>>({})
  const systemSnapshotGraphNodeByKey = ref<Record<string, GraphNodeDetailPayload>>({})
  const mySnapshotGraphByKey = ref<Record<string, GraphViewPayload>>({})
  const mySnapshotGraphNodeByKey = ref<Record<string, GraphNodeDetailPayload>>({})
  const mySnapshotListByProjectId = ref<Record<string, MySnapshotListPayload>>({})
  const mySnapshotDetailByKey = ref<Record<string, MySnapshotDetailPayload>>({})
  const mySnapshotReportByKey = ref<Record<string, MySnapshotReportPayload>>({})
  const mySnapshotCodeByKey = ref<Record<string, MySnapshotCodePayload>>({})

  async function loadProjects() {
    projects.value = await fetchProjects()
    return projects.value
  }

  async function createManagedProject(payload: { name: string; describe?: string }) {
    const project = await createProject(payload)
    projects.value = [...projects.value, project]
    return project
  }

  async function updateManagedProject(projectId: string, payload: { name: string; describe?: string }) {
    const updated = await updateProject(projectId, payload)
    projects.value = projects.value.map((project) => (project.id === projectId ? updated : project))
    const currentContext = contextByProjectId.value[projectId]
    if (currentContext) {
      assignByKey(contextByProjectId, projectId, { ...currentContext, project: updated })
    }
    return updated
  }

  async function removeManagedProject(projectId: string, password: string) {
    await deleteProject(projectId, { password })
    projects.value = projects.value.filter((project) => project.id !== projectId)
  }

  async function loadProjectContext(projectId: string) {
    return loadRecordByKey(contextByProjectId, projectId, () => fetchProjectContext(projectId))
  }

  async function loadProjectApps(projectId: string) {
    return loadRecordByKey(appsByProjectId, projectId, () => fetchProjectApps(projectId))
  }

  async function createManagedApp(
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
      probeAlertOnOnline?: boolean
      probeAlertOnOffline?: boolean
      probeAlertOnRecovered?: boolean
    },
  ) {
    const app = await createProjectApp(projectId, payload)
    assignByKey(appsByProjectId, projectId, [...(appsByProjectId.value[projectId] || []), app])
    const currentContext = contextByProjectId.value[projectId]
    if (currentContext) {
      assignByKey(contextByProjectId, projectId, {
        ...currentContext,
        apps: [...currentContext.apps, app],
        appCount: currentContext.appCount + 1,
      })
    }
    return app
  }

  async function removeManagedApp(projectId: string, appId: string, password: string) {
    await deleteProjectApp(projectId, appId, { password })
    const currentApps = appsByProjectId.value[projectId] || []
    assignByKey(
      appsByProjectId,
      projectId,
      currentApps.filter((app) => app.id !== appId),
    )
    const currentContext = contextByProjectId.value[projectId]
    if (currentContext) {
      const remainingApps = currentContext.apps.filter((app) => app.id !== appId)
      assignByKey(contextByProjectId, projectId, {
        ...currentContext,
        apps: remainingApps,
        appCount: remainingApps.length,
        onlineAppCount: remainingApps.filter((app) => app.onlineCount > 0).length,
      })
    }
  }

  async function loadProjectMembers(projectId: string) {
    return loadRecordByKey(membersByProjectId, projectId, () => fetchProjectMembers(projectId))
  }

  async function loadCollectorSources(projectId: string) {
    return loadRecordByKey(collectorSourcesByProjectId, projectId, () => fetchCollectorSources(projectId))
  }

  async function addMembers(projectId: string, userIds: string[]) {
    return loadRecordByKey(membersByProjectId, projectId, () => addProjectMembers(projectId, userIds))
  }

  async function deleteMember(projectId: string, projectMemberId: string) {
    return loadRecordByKey(membersByProjectId, projectId, () => removeProjectMember(projectId, projectMemberId))
  }

  async function changeMemberRole(projectId: string, projectMemberId: string, role: string) {
    return loadRecordByKey(
      membersByProjectId,
      projectId,
      () => updateProjectMemberRole(projectId, projectMemberId, role),
    )
  }

  async function loadProjectLabels(projectId: string) {
    return loadRecordByKey(labelsByProjectId, projectId, () => fetchProjectLabels(projectId))
  }

  async function saveProjectLabel(projectId: string, type: string, name: string, color: string) {
    return loadRecordByKey(
      labelsByProjectId,
      projectId,
      () => upsertProjectLabel(projectId, { type, name, color }),
    )
  }

  async function removeLabel(projectId: string, type: string, name: string) {
    return loadRecordByKey(
      labelsByProjectId,
      projectId,
      () => deleteProjectLabel(projectId, { type, name }),
    )
  }

  async function loadOnlineSessions(projectId: string) {
    return loadRecordByKey(onlineSessionsByProjectId, projectId, () => fetchOnlineSessions(projectId))
  }

  async function controlSandbox(projectId: string, sessionId: string, command: 'start' | 'stop' | 'restart' | 'status') {
    return sendSandboxCommand(projectId, sessionId, command)
  }

  function appKey(projectId: string, appId: string) {
    return `${projectId}:${appId}`
  }

  async function loadAppSettings(projectId: string, appId: string) {
    return loadRecordByKey(appSettingsByKey, appKey(projectId, appId), () => fetchAppSettings(projectId, appId))
  }

  async function loadProbeAlerts(projectId: string, appId: string) {
    return loadRecordByKey(probeAlertsByKey, appKey(projectId, appId), () => fetchProbeAlerts(projectId, appId))
  }

  async function updateAppSettings(
    projectId: string,
    appId: string,
    payload: Parameters<typeof saveAppSettings>[2],
  ) {
    return loadRecordByKey(
      appSettingsByKey,
      appKey(projectId, appId),
      () => saveAppSettings(projectId, appId, payload),
    )
  }

  async function loadRepository(projectId: string, appId: string) {
    return loadRecordByKey(repositoryByKey, appKey(projectId, appId), () => fetchRepositoryConfig(projectId, appId))
  }

  async function updateRepository(
    projectId: string,
    appId: string,
    payload: Parameters<typeof saveRepositoryConfig>[2],
  ) {
    return loadRecordByKey(
      repositoryByKey,
      appKey(projectId, appId),
      () => saveRepositoryConfig(projectId, appId, payload),
    )
  }

  async function loadRepositoryBranches(projectId: string, appId: string) {
    return fetchRepositoryBranches(projectId, appId)
  }

  async function loadUsecaseList(
    projectId: string,
    params?: { directory?: string; sort?: string; keyword?: string },
  ) {
    return loadRecordByKey(usecaseListByProjectId, projectId, () => fetchUsecaseList(projectId, params))
  }

  function usecaseKey(projectId: string, usecaseId: string) {
    return `${projectId}:${usecaseId}`
  }

  function snapshotAppKey(projectId: string, appId: string) {
    return `${projectId}:${appId}`
  }

  function snapshotKey(projectId: string, snapshotId: string) {
    return `${projectId}:${snapshotId}`
  }

  function snapshotCodeKey(projectId: string, snapshotId: string, className: string) {
    return `${projectId}:${snapshotId}:${className}`
  }

  function snapshotGraphNodeKey(projectId: string, snapshotId: string, nodeId: string) {
    return `${projectId}:${snapshotId}:graph:${nodeId}`
  }

  function assignByKey<T>(target: Ref<Record<string, T>>, key: string, payload: T) {
    target.value = {
      ...target.value,
      [key]: payload,
    }
    return payload
  }

  async function loadRecordByKey<T>(target: Ref<Record<string, T>>, key: string, loader: () => Promise<T>) {
    const payload = await loader()
    return assignByKey(target, key, payload)
  }

  async function loadUsecaseBootstrap(projectId: string, params?: { directory?: string; id?: string }) {
    const key = usecaseKey(projectId, params?.id || 'new')
    return loadRecordByKey(usecaseBootstrapByKey, key, () => fetchUsecaseBootstrap(projectId, params))
  }

  async function loadUsecaseDetail(projectId: string, usecaseId: string) {
    return loadRecordByKey(
      usecaseDetailByKey,
      usecaseKey(projectId, usecaseId),
      () => fetchUsecaseDetail(projectId, usecaseId),
    )
  }

  async function persistUsecase(
    projectId: string,
    payload: Parameters<typeof saveUsecase>[1],
  ) {
    const usecaseId = await saveUsecase(projectId, payload)
    return usecaseId
  }

  async function removeUsecase(projectId: string, usecaseId: string) {
    return deleteUsecase(projectId, usecaseId)
  }

  async function changeUsecaseShare(projectId: string, usecaseId: string, share: boolean) {
    return updateUsecaseShare(projectId, usecaseId, share)
  }

  async function importUsecases(projectId: string, directory: string, file: File) {
    return uploadUsecases(projectId, directory, file)
  }

  async function rebuildUsecaseSearch(projectId: string) {
    return rebuildUsecaseSearchData(projectId)
  }

  async function addUsecaseDirectory(projectId: string, payload: { parentId?: string; name: string }) {
    return createUsecaseDirectory(projectId, payload)
  }

  async function updateUsecaseDirectory(
    projectId: string,
    directoryId: string,
    payload: { parentId: string; name: string },
  ) {
    return renameUsecaseDirectory(projectId, directoryId, payload)
  }

  async function previewUsecaseDirectoryDelete(projectId: string, directoryId: string) {
    return fetchUsecaseDirectoryDeletePreview(projectId, directoryId)
  }

  async function removeUsecaseDirectory(
    projectId: string,
    directoryId: string,
    payload: { parentId: string; name: string; deleteUsecases: boolean },
  ) {
    return deleteUsecaseDirectory(projectId, directoryId, payload)
  }

  async function loadAiContext(projectId: string) {
    return loadRecordByKey(aiContextByProjectId, projectId, () => fetchAiInteractiveContext(projectId))
  }

  async function askAi(projectId: string, payload: Parameters<typeof askAiInteractive>[1]) {
    const reply = await askAiInteractive(projectId, payload)
    if (payload.memoryScope === 'assistant') {
      assignByKey(aiAssistantLastReplyByProjectId, projectId, reply)
    } else {
      assignByKey(aiLastReplyByProjectId, projectId, reply)
    }
    if (typeof reply.sessionState === 'string') {
      const current = aiContextByProjectId.value[projectId]
      if (current) {
        assignByKey(aiContextByProjectId, projectId, { ...current, sessionState: reply.sessionState })
      }
    }
    return reply
  }

  async function persistAiSessionState(projectId: string, sessionState: string) {
    const saved = await saveAiSessionState(projectId, sessionState)
    const current = aiContextByProjectId.value[projectId]
    if (current) {
      assignByKey(aiContextByProjectId, projectId, { ...current, sessionState: saved })
    }
    return saved
  }

  async function resetAiSessionState(projectId: string, memoryScope: 'workbench' | 'assistant' = 'workbench') {
    const cleared = await clearAiSessionState(projectId, memoryScope)
    if (memoryScope === 'workbench') {
      const current = aiContextByProjectId.value[projectId]
      if (current) {
        assignByKey(aiContextByProjectId, projectId, { ...current, sessionState: cleared })
      }
      assignByKey(aiLastReplyByProjectId, projectId, {})
    } else {
      assignByKey(aiAssistantLastReplyByProjectId, projectId, {})
    }
    return cleared
  }

  async function loadSystemSnapshotList(
    projectId: string,
    appId: string,
    params?: { directoryId?: string; sort?: string; keyword?: string },
  ) {
    return loadRecordByKey(
      systemSnapshotListByKey,
      snapshotAppKey(projectId, appId),
      () => fetchSystemSnapshotList(projectId, appId, params),
    )
  }

  async function loadSystemSnapshotDetail(projectId: string, appId: string, snapshotId: string) {
    return loadRecordByKey(
      systemSnapshotDetailByKey,
      snapshotKey(projectId, snapshotId),
      () => fetchSystemSnapshotDetail(projectId, appId, snapshotId),
    )
  }

  async function loadSystemSnapshotReport(projectId: string, appId: string, snapshotId: string) {
    return loadRecordByKey(
      systemSnapshotReportByKey,
      snapshotKey(projectId, snapshotId),
      () => fetchSystemSnapshotReport(projectId, appId, snapshotId),
    )
  }

  async function triggerSystemSnapshotReport(projectId: string, appId: string, snapshotId: string) {
    return calculateSystemSnapshotReport(projectId, appId, snapshotId)
  }

  async function loadSystemSnapshotCode(projectId: string, appId: string, snapshotId: string, className: string) {
    return loadRecordByKey(
      systemSnapshotCodeByKey,
      snapshotCodeKey(projectId, snapshotId, className),
      () => fetchSystemSnapshotCode(projectId, appId, snapshotId, className),
    )
  }

  async function loadSystemSnapshotGraph(projectId: string, appId: string, snapshotId: string) {
    return loadRecordByKey(
      systemSnapshotGraphByKey,
      snapshotKey(projectId, snapshotId),
      () => fetchSystemSnapshotGraph(projectId, appId, snapshotId),
    )
  }

  async function loadSystemSnapshotGraphNode(projectId: string, appId: string, snapshotId: string, nodeId: string) {
    return loadRecordByKey(
      systemSnapshotGraphNodeByKey,
      snapshotGraphNodeKey(projectId, snapshotId, nodeId),
      () => fetchSystemSnapshotGraphNode(projectId, appId, snapshotId, nodeId),
    )
  }

  async function persistSystemSnapshotDirectory(
    projectId: string,
    appId: string,
    payload: { id?: number; parentId?: string; name: string },
  ) {
    return saveSystemSnapshotDirectory(projectId, appId, payload)
  }

  async function removeSystemSnapshotDirectory(projectId: string, appId: string, directoryId: string) {
    return deleteSystemSnapshotDirectory(projectId, appId, directoryId)
  }

  async function updateSystemSnapshotBasicInfo(
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
    return updateSystemSnapshotBasic(projectId, appId, snapshotId, payload)
  }

  async function appendSystemSnapshotComment(projectId: string, appId: string, snapshotId: string, content: string) {
    return addSystemSnapshotComment(projectId, appId, snapshotId, content)
  }

  async function removeSystemSnapshotComment(
    projectId: string,
    appId: string,
    snapshotId: string,
    payload: { content: string; dateTime: string },
  ) {
    return deleteSystemSnapshotComment(projectId, appId, snapshotId, payload)
  }

  async function removeSystemSnapshot(projectId: string, appId: string, snapshotId: string) {
    return deleteSystemSnapshot(projectId, appId, snapshotId)
  }

  async function updateSystemSnapshotUsecases(
    projectId: string,
    appId: string,
    snapshotId: string,
    usecaseIds: string[],
  ) {
    return bindSystemSnapshotUsecases(projectId, appId, snapshotId, usecaseIds)
  }

  async function batchUpdateSystemSnapshotUsecases(
    projectId: string,
    appId: string,
    snapshotIds: string[],
    usecaseIds: string[],
  ) {
    return batchBindSystemSnapshotUsecases(projectId, appId, snapshotIds, usecaseIds)
  }

  async function loadMySnapshotList(projectId: string, params?: { sort?: string; keyword?: string; labels?: string }) {
    return loadRecordByKey(mySnapshotListByProjectId, projectId, () => fetchMySnapshotList(projectId, params))
  }

  async function loadMySnapshotDetail(projectId: string, snapshotId: string) {
    return loadRecordByKey(
      mySnapshotDetailByKey,
      snapshotKey(projectId, snapshotId),
      () => fetchMySnapshotDetail(projectId, snapshotId),
    )
  }

  async function loadMySnapshotReport(projectId: string, snapshotId: string) {
    return loadRecordByKey(
      mySnapshotReportByKey,
      snapshotKey(projectId, snapshotId),
      () => fetchMySnapshotReport(projectId, snapshotId),
    )
  }

  async function loadMySnapshotCode(projectId: string, snapshotId: string, className: string) {
    return loadRecordByKey(
      mySnapshotCodeByKey,
      snapshotCodeKey(projectId, snapshotId, className),
      () => fetchMySnapshotCode(projectId, snapshotId, className),
    )
  }

  async function loadMySnapshotGraph(projectId: string, snapshotId: string) {
    return loadRecordByKey(
      mySnapshotGraphByKey,
      snapshotKey(projectId, snapshotId),
      () => fetchMySnapshotGraph(projectId, snapshotId),
    )
  }

  async function loadMySnapshotGraphNode(projectId: string, snapshotId: string, nodeId: string) {
    return loadRecordByKey(
      mySnapshotGraphNodeByKey,
      snapshotGraphNodeKey(projectId, snapshotId, nodeId),
      () => fetchMySnapshotGraphNode(projectId, snapshotId, nodeId),
    )
  }

  async function updateMySnapshotUsecases(projectId: string, snapshotId: string, usecaseIds: string[]) {
    return bindMySnapshotUsecases(projectId, snapshotId, usecaseIds)
  }

  async function updateMySnapshotBasicInfo(
    projectId: string,
    snapshotId: string,
    payload: { name: string; describe?: string; labels: string[] },
  ) {
    return updateMySnapshotBasic(projectId, snapshotId, payload)
  }

  async function changeMySnapshotShare(projectId: string, snapshotId: string, share: boolean) {
    return updateMySnapshotShare(projectId, snapshotId, share)
  }

  async function batchUpdateMySnapshotUsecases(projectId: string, snapshotIds: string[], usecaseIds: string[]) {
    return batchBindMySnapshotUsecases(projectId, snapshotIds, usecaseIds)
  }

  async function removeMySnapshot(projectId: string, snapshotId: string) {
    return deleteMySnapshot(projectId, snapshotId)
  }

  async function persistMySnapshotAsSystemSnapshot(
    projectId: string,
    snapshotId: string,
    payload: Parameters<typeof saveMySnapshotAsSystemSnapshot>[2],
  ) {
    return saveMySnapshotAsSystemSnapshot(projectId, snapshotId, payload)
  }

  return {
    projects,
    contextByProjectId,
    appsByProjectId,
    membersByProjectId,
    labelsByProjectId,
    onlineSessionsByProjectId,
    collectorSourcesByProjectId,
    appSettingsByKey,
    probeAlertsByKey,
    repositoryByKey,
    usecaseListByProjectId,
    usecaseBootstrapByKey,
    usecaseDetailByKey,
    aiContextByProjectId,
    aiLastReplyByProjectId,
    aiAssistantLastReplyByProjectId,
    systemSnapshotListByKey,
    systemSnapshotDetailByKey,
    systemSnapshotReportByKey,
    systemSnapshotCodeByKey,
    systemSnapshotGraphByKey,
    systemSnapshotGraphNodeByKey,
    mySnapshotGraphByKey,
    mySnapshotGraphNodeByKey,
    mySnapshotListByProjectId,
    mySnapshotDetailByKey,
    mySnapshotReportByKey,
    mySnapshotCodeByKey,
    loadProjects,
    createManagedProject,
    updateManagedProject,
    removeManagedProject,
    loadProjectContext,
    loadProjectApps,
    createManagedApp,
    removeManagedApp,
    loadProjectMembers,
    addMembers,
    deleteMember,
    changeMemberRole,
    loadProjectLabels,
    saveProjectLabel,
    removeLabel,
    loadOnlineSessions,
    loadCollectorSources,
    controlSandbox,
    loadAppSettings,
    loadProbeAlerts,
    updateAppSettings,
    loadRepository,
    updateRepository,
    loadRepositoryBranches,
    loadUsecaseList,
    loadUsecaseBootstrap,
    loadUsecaseDetail,
    persistUsecase,
    removeUsecase,
    changeUsecaseShare,
    importUsecases,
    rebuildUsecaseSearch,
    addUsecaseDirectory,
    updateUsecaseDirectory,
    previewUsecaseDirectoryDelete,
    removeUsecaseDirectory,
    loadAiContext,
    askAi,
    persistAiSessionState,
    resetAiSessionState,
    loadSystemSnapshotList,
    loadSystemSnapshotDetail,
    loadSystemSnapshotReport,
    loadSystemSnapshotCode,
    loadSystemSnapshotGraph,
    loadSystemSnapshotGraphNode,
    triggerSystemSnapshotReport,
    persistSystemSnapshotDirectory,
    removeSystemSnapshotDirectory,
    updateSystemSnapshotBasicInfo,
    appendSystemSnapshotComment,
    removeSystemSnapshotComment,
    removeSystemSnapshot,
    updateSystemSnapshotUsecases,
    batchUpdateSystemSnapshotUsecases,
    loadMySnapshotList,
    loadMySnapshotDetail,
    loadMySnapshotReport,
    loadMySnapshotCode,
    loadMySnapshotGraph,
    loadMySnapshotGraphNode,
    updateMySnapshotBasicInfo,
    changeMySnapshotShare,
    updateMySnapshotUsecases,
    batchUpdateMySnapshotUsecases,
    removeMySnapshot,
    persistMySnapshotAsSystemSnapshot,
  }
})
