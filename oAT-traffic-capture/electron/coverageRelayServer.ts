import http from 'http'
import type { CoverageRelayConfig, TrafficRecord } from './types.js'
import { coverageResponseHeaders, relayCoverageReport, type CoverageReporterOptions } from './proxy.js'

export function createCoverageRelayServer(
  onTraffic: (record: TrafficRecord) => void,
  coverageReporter: CoverageReporterOptions
) {
  return http.createServer((request, response) => {
    const headers = coverageResponseHeaders(request.headers)
    const url = request.url ?? ''
    if (!/\/oat\/coverage\/report(\?|$)/i.test(url)) {
      response.writeHead(404, headers)
      response.end(JSON.stringify({ result: false, message: 'Not Found' }))
      return
    }

    if (request.method === 'OPTIONS') {
      response.writeHead(204, headers)
      response.end()
      return
    }

    if (request.method !== 'POST') {
      response.writeHead(405, headers)
      response.end(JSON.stringify({ result: false, message: 'Method Not Allowed' }))
      return
    }

    const startTime = Date.now()
    let requestBody = ''
    request.on('data', (chunk: Buffer) => {
      requestBody += chunk.toString()
    })
    request.on('end', async () => {
      const record: Partial<TrafficRecord> = {
        id: `coverage-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
        caseName: '',
        method: 'POST',
        url: `http://${request.headers.host ?? 'localhost'}/oat/coverage/report`,
        protocol: 'COVERAGE',
        statusCode: 0,
        duration: 0,
        timestamp: Date.now(),
        requestHeaders: Object.fromEntries(Object.entries(request.headers).map(([key, value]) => [key, Array.isArray(value) ? value.join(', ') : String(value ?? '')])),
        requestBody,
        source: 'capture'
      }

      const relayResult = await relayCoverageReport(record, requestBody, coverageReporter)
      record.duration = Date.now() - startTime
      record.responseBody = relayResult.responseBody
      record.responseHeaders = {
        ...headers,
        'content-length': Buffer.byteLength(relayResult.responseBody).toString()
      }
      if (!record.statusCode) {
        record.statusCode = relayResult.statusCode
      }
      if (record.coverageRelay) {
        onTraffic(record as TrafficRecord)
      }

      response.writeHead(relayResult.statusCode, record.responseHeaders)
      response.end(relayResult.responseBody)
    })
    request.on('error', (error: Error) => {
      response.writeHead(502, headers)
      response.end(JSON.stringify({ result: false, message: error.message }))
    })
  })
}

export function coverageRelayPort(config: CoverageRelayConfig): number {
  const port = Number(config.coveragePort)
  return Number.isInteger(port) && port > 0 && port <= 65535 ? port : 8889
}
