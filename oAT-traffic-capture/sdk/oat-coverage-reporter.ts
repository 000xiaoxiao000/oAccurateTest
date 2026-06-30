type CoveragePayload = {
  projectId: string
  appId: string
  commitId?: string
  versionNumber?: string
  branch?: string
  caseName?: string
  buildId?: string
  testStage?: string
  serviceBaseUrl?: string
}

type CoverageWindow = Window & {
  __coverage__?: unknown
  __oatCoverageReportInFlight?: boolean
  __oatCoverageLastSentSignature?: string
}

export function reportOatCoverage(endpoint: string, meta: CoveragePayload): void {
  const coverageWindow = window as CoverageWindow
  const coverage = coverageWindow.__coverage__
  if (!coverage) return

  const coverageSignature = JSON.stringify(coverage)
  if (coverageSignature === coverageWindow.__oatCoverageLastSentSignature) return

  const body = JSON.stringify({
    ...meta,
    language: 'FRONTEND',
    testStage: meta.testStage || 'unknown',
    payload: coverage,
    timestamp: Date.now(),
  })

  if (window.fetch) {
    if (coverageWindow.__oatCoverageReportInFlight) return
    coverageWindow.__oatCoverageReportInFlight = true
    void fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
      credentials: 'omit',
    }).then((response) => {
      if (response.ok) {
        return response.clone().json().catch(() => null).then((data) => {
          if (!data?.skipped && data?.result !== false) {
            coverageWindow.__oatCoverageLastSentSignature = coverageSignature
          }
        })
      }
      return undefined
    }).catch(() => {}).finally(() => {
      coverageWindow.__oatCoverageReportInFlight = false
    })
    return
  }
}

export function installOatCoverageReporter(endpoint: string, meta: CoveragePayload, intervalMs = 30000): () => void {
  const report = () => reportOatCoverage(endpoint, { ...meta, intervalMs } as CoveragePayload & { intervalMs: number })
  report()
  const timer = window.setInterval(report, intervalMs)
  window.addEventListener('beforeunload', report)
  return () => {
    window.clearInterval(timer)
    window.removeEventListener('beforeunload', report)
  }
}
