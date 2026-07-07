import { apiGet, apiGetRaw, apiPost, backendApiUrl } from '@/api/http'
import type { CoverageOverviewPayload } from '@/api/types'
import type {
  BuildSessionsPayload,
  CoverageMethodsPayload,
  CoverageModulesPayload,
  CoverageReportMetadata,
  CoverageTreeNodesPayload,
  CoverageUnitsPayload,
  QualityGateResult,
  SourceCoveragePayload,
  TestImpactAnalysisReport,
  TestGapReport,
} from '@/entities/coverage/model'

export interface CoverageUnitQuery {
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
  page?: number
  size?: number
}

export interface CoverageOverviewQuery {
  versionNumber: string
  reportId?: string
  commitId?: string
}

export function fetchCoverageOverviewV2(projectId: string, appId: string, params: CoverageOverviewQuery) {
  const query = new URLSearchParams({ projectId, versionNumber: params.versionNumber })
  if (params.reportId) query.set('reportId', params.reportId)
  if (params.commitId) query.set('commitId', params.commitId)
  return apiGet<CoverageOverviewPayload>(`/api/v2/coverage/apps/${encodeURIComponent(appId)}/overview?${query.toString()}`)
}

export function triggerCoverageGenerateFullV2(
  projectId: string,
  appId: string,
  payload: { versionNumber: string; branch?: string; commitId?: string },
) {
  const body = new URLSearchParams({ appId, versionNumber: payload.versionNumber })
  if (payload.branch) body.set('branch', payload.branch)
  if (payload.commitId) body.set('commitId', payload.commitId)
  return apiPost<string>(
    `/api/projects/${encodeURIComponent(projectId)}/coverage/generate`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function triggerCoverageGenerateBySourceV2(
  projectId: string,
  appId: string,
  sourceType: 'FRONTEND' | 'GO' | 'PYTHON' | 'CPP',
  payload: {
    versionNumber?: string
    branch?: string
    commitId?: string
    reportType?: number
    baseVersionNumber?: string
    baseCommitId?: string
  },
) {
  return sourceType === 'FRONTEND'
    ? triggerFrontendCoverageGenerateV2(projectId, appId, payload)
    : triggerUniversalCoverageGenerateV2(projectId, appId, sourceType, payload)
}

export function triggerCoverageGenerateCurrentV2(
  projectId: string,
  appId: string,
  payload: { versionNumber?: string; branch?: string; commitId?: string },
) {
  const body = new URLSearchParams({ projectId })
  if (payload.versionNumber) body.set('versionNumber', payload.versionNumber)
  if (payload.branch) body.set('branch', payload.branch)
  if (payload.commitId) body.set('commitId', payload.commitId)
  return apiPost<string>(
    `/api/v2/coverage/apps/${encodeURIComponent(appId)}/generate-current`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function triggerCoverageGenerateIncrementalV2(
  projectId: string,
  appId: string,
  payload: { versionNumber: string; branch?: string; commitId?: string; baseVersionNumber: string; baseCommitId?: string },
) {
  const body = new URLSearchParams({ projectId, versionNumber: payload.versionNumber, baseVersionNumber: payload.baseVersionNumber })
  if (payload.branch) body.set('branch', payload.branch)
  if (payload.commitId) body.set('commitId', payload.commitId)
  if (payload.baseCommitId) body.set('baseCommitId', payload.baseCommitId)
  return apiPost<string>(
    `/api/v2/coverage/apps/${encodeURIComponent(appId)}/generate-incremental`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function triggerFrontendCoverageGenerateV2(
  projectId: string,
  appId: string,
  payload: { versionNumber?: string; branch?: string; commitId?: string },
) {
  const query = new URLSearchParams({ projectId })
  return apiPost<string>(
    `/api/v2/coverage/apps/${encodeURIComponent(appId)}/frontend/generate?${query.toString()}`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function triggerUniversalCoverageGenerateV2(
  projectId: string,
  appId: string,
  sourceType: 'CPP' | 'GO' | 'PYTHON',
  payload: { versionNumber?: string; branch?: string; commitId?: string },
) {
  const query = new URLSearchParams({ projectId })
  return apiPost<string>(
    `/api/v2/coverage/apps/${encodeURIComponent(appId)}/universal/${encodeURIComponent(sourceType)}/generate?${query.toString()}`,
    JSON.stringify(payload),
    'application/json',
  )
}

export function fetchCoverageJobV2(jobId: string) {
  return apiGetRaw<{ data?: string; progress?: number; progressName?: string; finish?: boolean; success?: boolean; message?: string }>(
    `/api/v2/coverage/jobs/${encodeURIComponent(jobId)}`,
  )
}

export function fetchCoverageTrendV2(projectId: string, appId: string, versionNumber: string) {
  const query = new URLSearchParams({ projectId, versionNumber })
  return apiGetRaw<Array<Record<string, unknown>>>(`/api/v2/coverage/apps/${encodeURIComponent(appId)}/trend-data?${query.toString()}`)
}

export function deleteCoverageReportV2(projectId: string, reportId: string) {
  const body = new URLSearchParams({ projectId })
  return apiPost<string>(
    `/api/v2/coverage/reports/${encodeURIComponent(reportId)}/delete`,
    body.toString(),
    'application/x-www-form-urlencoded;charset=UTF-8',
  )
}

export function fetchCoverageReportMetadata(projectId: string, reportId: string) {
  const query = new URLSearchParams({ projectId })
  return apiGet<CoverageReportMetadata>(`/api/v2/coverage/reports/${encodeURIComponent(reportId)}?${query.toString()}`)
}

export function coverageReportExportUrl(projectId: string, reportId: string) {
  const query = new URLSearchParams({ projectId })
  return backendApiUrl(`/api/v2/coverage/reports/${encodeURIComponent(reportId)}/export?${query.toString()}`)
}

export function coverageMethodExportUrl(projectId: string, reportId: string) {
  const query = new URLSearchParams({ projectId })
  return backendApiUrl(`/api/v2/coverage/reports/${encodeURIComponent(reportId)}/export-methods?${query.toString()}`)
}

export function fetchCoverageUnits(projectId: string, reportId: string, params: CoverageUnitQuery = {}) {
  const query = buildCoverageUnitQuery(projectId, params)
  return apiGet<CoverageUnitsPayload>(`/api/v2/coverage/reports/${encodeURIComponent(reportId)}/units?${query.toString()}`)
}

export function fetchCoverageModules(projectId: string, reportId: string, params: CoverageUnitQuery = {}) {
  const query = buildCoverageUnitQuery(projectId, params, false)
  return apiGet<CoverageModulesPayload>(`/api/v2/coverage/reports/${encodeURIComponent(reportId)}/modules?${query.toString()}`)
}

export function fetchCoverageTreeNodes(projectId: string, reportId: string, parentPackage = '', params: CoverageUnitQuery = {}) {
  const query = buildCoverageUnitQuery(projectId, params, false)
  if (parentPackage) query.set('parentPackage', parentPackage)
  return apiGet<CoverageTreeNodesPayload>(`/api/v2/coverage/reports/${encodeURIComponent(reportId)}/tree-nodes?${query.toString()}`)
}

export function fetchSourceCoverage(projectId: string, reportId: string, unitKey: string) {
  const query = new URLSearchParams({ projectId, unitKey })
  return apiGet<SourceCoveragePayload>(`/api/v2/coverage/reports/${encodeURIComponent(reportId)}/source?${query.toString()}`)
}

export function fetchCoverageMethods(projectId: string, reportId: string, unitKey: string) {
  const query = new URLSearchParams({ projectId, unitKey })
  return apiGet<CoverageMethodsPayload>(`/api/v2/coverage/reports/${encodeURIComponent(reportId)}/methods?${query.toString()}`)
}

export function fetchBuildSessions(projectId: string, appId: string) {
  const query = new URLSearchParams({ projectId })
  return apiGet<BuildSessionsPayload>(`/api/v2/coverage/apps/${encodeURIComponent(appId)}/build-sessions?${query.toString()}`)
}

export function fetchTestGap(projectId: string, reportId: string) {
  const query = new URLSearchParams({ projectId })
  return apiGet<TestGapReport>(`/api/v2/coverage/reports/${encodeURIComponent(reportId)}/test-gap?${query.toString()}`)
}

export function fetchQualityGate(projectId: string, reportId: string, minLineCoverageRate = 80) {
  const query = new URLSearchParams({ projectId, minLineCoverageRate: String(minLineCoverageRate) })
  return apiGet<QualityGateResult>(`/api/v2/coverage/reports/${encodeURIComponent(reportId)}/quality-gate?${query.toString()}`)
}

export interface AppQualityGateParams {
  versionNumber?: string
  commitId?: string
  buildId?: string
  testStage?: string
  reportType?: number
  minLineCoverageRate?: number
}

export function fetchAppQualityGate(projectId: string, appId: string, params: AppQualityGateParams = {}) {
  const query = new URLSearchParams({ projectId })
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim()) {
      query.set(key, String(value))
    }
  })
  return apiGet<QualityGateResult>(`/api/v2/coverage/apps/${encodeURIComponent(appId)}/quality-gate?${query.toString()}`)
}

export function fetchTestImpact(projectId: string, reportId: string, changedLines?: string) {
  const query = new URLSearchParams({ projectId })
  if (changedLines) query.set('changedLines', changedLines)
  return apiGet<TestImpactAnalysisReport>(`/api/v2/coverage/reports/${encodeURIComponent(reportId)}/test-impact?${query.toString()}`)
}

function buildCoverageUnitQuery(projectId: string, params: CoverageUnitQuery, includePaging = true) {
  const query = new URLSearchParams({ projectId })
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    if (!includePaging && (key === 'page' || key === 'size')) return
    query.set(key, String(value))
  })
  return query
}
