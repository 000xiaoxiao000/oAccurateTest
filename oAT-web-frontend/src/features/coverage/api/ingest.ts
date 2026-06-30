import { apiPost } from '@/api/http'
import type { CoverageIngestPayload, CoverageIngestResult } from '@/entities/coverage/model'

export function ingestCoverage(payload: CoverageIngestPayload) {
  return apiPost<CoverageIngestResult>('/api/v2/ingest/coverage', JSON.stringify(payload), 'application/json')
}
