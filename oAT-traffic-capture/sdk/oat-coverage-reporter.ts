type CoveragePayload = {
  projectId: string
  appId: string
  commitId?: string
  versionNumber?: string
  branch?: string
  caseName?: string
  serviceBaseUrl?: string
}

type CoverageWindow = Window & {
  __coverage__?: unknown
}

export function reportOatCoverage(endpoint: string, meta: CoveragePayload): void {
  const coverage = (window as CoverageWindow).__coverage__
  if (!coverage) return

  const body = JSON.stringify({
    ...meta,
    coverage,
    timestamp: Date.now(),
  })

  if (navigator.sendBeacon) {
    const blob = new Blob([body], { type: 'application/json' })
    navigator.sendBeacon(endpoint, blob)
    return
  }

  void fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
    keepalive: true,
  })
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
