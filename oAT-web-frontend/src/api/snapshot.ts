import { apiGet, apiPost } from './http'
import type {
  GraphNodeDetailPayload,
  GraphViewPayload,
  MySnapshotAggregateCodePayload,
  MySnapshotCodePayload,
  MySnapshotCodeReportPayload,
  MySnapshotDetailPayload,
  MySnapshotListPayload,
  MySnapshotReportPayload,
  SystemSnapshotCodePayload,
  SystemSnapshotDetailPayload,
  SystemSnapshotListPayload,
  SystemSnapshotReportPayload,
} from './types'

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

export function saveMySnapshotAsSystemSnapshot(
  projectId: string,
  snapshotId: string,
  payload: {
    appId: string
    directory: string
    title: string
    describe?: string
    topicImage?: string
    versionCycle?: number
    labels: string[]
    principals: string[]
  },
) {
  return apiPost<string>(
    `/api/projects/${projectId}/snapshots/my/${snapshotId}/save-as-system-snapshot`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function backfillSnapshotCommitMapping(
  projectId: string,
  appId: string,
  versionNumber?: string,
) {
  const body = new URLSearchParams()
  if (versionNumber) {
    body.set('versionNumber', versionNumber)
  }
  return apiPost<number>(
    `/api/projects/${projectId}/apps/${appId}/snapshots/commit-mapping/backfill`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}
