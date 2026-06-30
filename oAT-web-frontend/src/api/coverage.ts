import { apiGet, apiGetRaw, apiPost } from './http'
import type { CoverageCodePayload, CoverageDetailsPayload, CoverageOverviewPayload, CoverageTreeNode, UsecaseSummary } from './types'
import type { CoverageFootprintSnapshot } from '@/entities/coverage/model'

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

export function triggerCoverageGenerateCurrent(
  projectId: string,
  payload: { appId: string; versionNumber?: string; branch?: string; commitId?: string },
) {
  const body = new URLSearchParams()
  body.set('appId', payload.appId)
  if (payload.versionNumber) {
    body.set('versionNumber', payload.versionNumber)
  }
  if (payload.branch) {
    body.set('branch', payload.branch)
  }
  if (payload.commitId) {
    body.set('commitId', payload.commitId)
  }
  return apiPost<string>(
    `/api/projects/${projectId}/coverage/generate-current`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
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

export function triggerFrontendCoverageGenerate(
  projectId: string,
  appId: string,
  payload: { versionNumber?: string; branch?: string; commitId?: string },
) {
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/coverage/frontend/generate`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function triggerUniversalCoverageGenerate(
  projectId: string,
  appId: string,
  sourceType: 'CPP' | 'GO' | 'PYTHON',
  payload: { versionNumber?: string; branch?: string; commitId?: string },
) {
  return apiPost<string>(
    `/api/projects/${projectId}/apps/${appId}/coverage/universal/${sourceType}/generate`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function fetchCoverageJob(projectId: string, jobId: string) {
  return apiGetRaw<{ id?: string; data?: string; progress?: number; progressName?: string; finish?: boolean; success?: boolean; message?: string }>(
    `/api/projects/${projectId}/coverage/jobs/${jobId}`,
  )
}

export function fetchCoverageTrend(projectId: string, appId: string, versionNumber: string) {
  const query = new URLSearchParams()
  query.set('appId', appId)
  query.set('versionNumber', versionNumber)
  return apiGetRaw<Array<Record<string, unknown>>>(`/api/projects/${projectId}/coverage/trend-data?${query.toString()}`)
}

export function fetchCoverageFootprints(
  projectId: string,
  params: { appId?: string; language?: string; versionNumber?: string; commitId?: string } = {},
) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) {
      query.set(key, value)
    }
  })
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return apiGet<CoverageFootprintSnapshot[]>(`/api/projects/${projectId}/coverage/footprints${suffix}`)
}

export function fetchCoverageFootprint(projectId: string, footprintKey: string) {
  return apiGet<CoverageFootprintSnapshot>(`/api/projects/${projectId}/coverage/footprints/${encodeURIComponent(footprintKey)}`)
}

export function deleteCoverageFootprint(projectId: string, footprintKey: string) {
  return apiPost<string>(
    `/api/projects/${projectId}/coverage/footprints/${encodeURIComponent(footprintKey)}/delete`,
    '',
    'application/json',
  )
}

export function fetchCoverageFootprintUsecases(projectId: string, footprintKey: string) {
  return apiGet<UsecaseSummary[]>(`/api/projects/${projectId}/coverage/footprints/${encodeURIComponent(footprintKey)}/usecases`)
}

export function bindCoverageFootprintUsecases(projectId: string, footprintKey: string, usecaseIds: string[]) {
  return apiPost<number>(
    `/api/projects/${projectId}/coverage/footprints/${encodeURIComponent(footprintKey)}/usecases/bind`,
    JSON.stringify({ usecaseIds }),
    'application/json',
  )
}
