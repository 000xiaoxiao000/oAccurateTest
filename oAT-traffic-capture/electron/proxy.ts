import Proxy from 'http-mitm-proxy'
import type { CaptureProtocolConfig, CoverageRelayConfig, TrafficRecord, WsMessage } from './types.js'

const defaultProtocols: CaptureProtocolConfig = {
  http: true,
  https: true,
  ws: true,
  wss: true
}

type CoverageReporterOptions = {
  isEnabled: () => boolean
  getConfig: () => CoverageRelayConfig
}

export function createProxyServer(
  onTraffic: (record: TrafficRecord) => void,
  sslCaDir?: string,
  enabledProtocols: CaptureProtocolConfig = defaultProtocols,
  coverageReporter?: CoverageReporterOptions
) {
  const proxyFactory = Proxy as unknown as () => any
  const proxy = proxyFactory()
  proxy.use?.((Proxy as any).gunzip)
  let protocols = { ...defaultProtocols, ...enabledProtocols }
  proxy.setEnabledProtocols = (nextProtocols: CaptureProtocolConfig) => {
    protocols = { ...defaultProtocols, ...nextProtocols }
  }
  if (sslCaDir) {
    const originalListen = proxy.listen.bind(proxy)
    proxy.listen = (options: Record<string, unknown> = {}, callback?: (...args: unknown[]) => void) => {
      originalListen({ ...options, sslCaDir }, callback)
    }
  }
  const websocketRecords = new WeakMap<object, TrafficRecord>()

  function headerRecord(headers?: Record<string, string | string[]>): Record<string, string> {
    const next: Record<string, string> = {}
    for (const [key, value] of Object.entries(headers ?? {})) {
      next[key] = Array.isArray(value) ? value.join(', ') : String(value)
    }
    return next
  }

  function getWebSocketUrl(ctx: any): string {
    const req = ctx.clientToProxyWebSocket?.upgradeReq
    if (!req) return ''
    if (req.url && !req.url.startsWith('/')) return req.url
    const host = req.headers?.host ?? ''
    return `${ctx.isSSL ? 'wss' : 'ws'}://${host}${req.url ?? ''}`
  }

  function getHttpUrl(ctx: any): string {
    const requestUrl = ctx.clientToProxyRequest.url ?? ''
    if (requestUrl && !requestUrl.startsWith('/')) {
      return requestUrl
    }
    const host = ctx.clientToProxyRequest.headers.host ?? ''
    return `${ctx.isSSL ? 'https' : 'http'}://${host}${requestUrl}`
  }

  function isProtocolEnabled(protocol: keyof CaptureProtocolConfig): boolean {
    return protocols[protocol] !== false
  }

  function messageData(message: any): Pick<WsMessage, 'type' | 'data'> {
    if (Buffer.isBuffer(message)) {
      return { type: 'binary', data: message.toString('base64') }
    }
    return { type: 'text', data: String(message) }
  }

  function isCoverageRelayEnabled(): boolean {
    return coverageReporter?.isEnabled() === true
  }

  function isCoverageReportUrl(url?: string): boolean {
    return /\/oat\/coverage\/report(\?|$)/i.test(url ?? '')
      || /\/api\/projects\/[^/]+\/apps\/[^/]+\/coverage\/frontend\/report(\?|$)/i.test(url ?? '')
  }

  function coverageResponseHeaders(): Record<string, string> {
    return {
      'content-type': 'application/json',
      'access-control-allow-origin': '*',
      'access-control-allow-methods': 'POST, OPTIONS',
      'access-control-allow-headers': 'content-type',
      'x-oat-coverage-relay': 'intercepted'
    }
  }

  function isHtmlResponse(ctx: any): boolean {
    const contentType = String(ctx.serverToProxyResponse?.headers?.['content-type'] ?? '').toLowerCase()
    return contentType.includes('text/html')
  }

  function isJavaScriptResponse(ctx: any): boolean {
    const contentType = String(ctx.serverToProxyResponse?.headers?.['content-type'] ?? '').toLowerCase()
    return contentType.includes('javascript') || contentType.includes('ecmascript')
  }

  function shouldInjectCoverageReporter(ctx: any, record: Partial<TrafficRecord>): boolean {
    if (!isCoverageRelayEnabled()) return false
    if (!isHtmlResponse(ctx) && !isJavaScriptResponse(ctx)) return false
    if (record.method && !['GET', ''].includes(String(record.method).toUpperCase())) return false
    return !isCoverageReportUrl(record.url)
  }

  function handleCoverageReportRequest(ctx: any, record: Partial<TrafficRecord>, startTime: number): void {
    let requestBody = ''
    ctx.clientToProxyRequest.on('data', (chunk: Buffer) => {
      requestBody += chunk.toString()
    })
    ctx.clientToProxyRequest.on('end', () => {
      record.requestBody = requestBody
      record.duration = Date.now() - startTime
      record.statusCode = 204
      record.responseHeaders = coverageResponseHeaders()
      record.responseBody = ''
      if (String(record.method).toUpperCase() !== 'OPTIONS') {
        onTraffic(record as TrafficRecord)
      }

      if (!ctx.proxyToClientResponse.headersSent) {
        ctx.proxyToClientResponse.writeHead(204, record.responseHeaders)
      }
      ctx.proxyToClientResponse.end()
    })
    ctx.clientToProxyRequest.on('error', (err: Error) => {
      record.statusCode = 'ERROR'
      record.error = err.message
      record.duration = Date.now() - startTime
      onTraffic(record as TrafficRecord)
      if (!ctx.proxyToClientResponse.headersSent) {
        ctx.proxyToClientResponse.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' })
      }
      ctx.proxyToClientResponse.end(err.message)
    })
    ctx.clientToProxyRequest.resume()
  }

  function buildCoverageEndpoint(config: CoverageRelayConfig): string {
    if (config.serviceBaseUrl && config.projectId && config.appId) {
      return `${config.serviceBaseUrl.replace(/\/$/, '')}/api/projects/${encodeURIComponent(config.projectId)}/apps/${encodeURIComponent(config.appId)}/coverage/frontend/report`
    }
    return '/oat/coverage/report'
  }

  function buildCoverageReporterCode(): string {
    const config = coverageReporter?.getConfig() ?? { intervalMs: 30000 }
    const intervalMs = Number.isFinite(Number(config.intervalMs)) && Number(config.intervalMs) >= 1000
      ? Math.round(Number(config.intervalMs))
      : 30000
    const endpoint = buildCoverageEndpoint(config)
    const meta = {
      serviceBaseUrl: config.serviceBaseUrl,
      projectId: config.projectId,
      appId: config.appId
    }
    return `;(() => {
  if (window.__oatCoverageReporterInstalled) return;
  window.__oatCoverageReporterInstalled = true;
  const endpoint = ${JSON.stringify(endpoint)};
  const intervalMs = ${intervalMs};
  const meta = ${JSON.stringify(meta)};
  let missingReported = false;
  const send = (payload) => {
    const body = JSON.stringify({ ...meta, ...payload });
    if (navigator.sendBeacon) {
      navigator.sendBeacon(endpoint, new Blob([body], { type: 'application/json' }));
      return;
    }
    fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
      keepalive: true
    }).catch(() => {});
  };
  const report = (diagnoseMissing = false) => {
    const coverage = window.__coverage__;
    if (!coverage) {
      if (diagnoseMissing && !missingReported) {
        missingReported = true;
        send({ coverageMissing: true, timestamp: Date.now(), intervalMs, href: location.href });
      }
      return;
    }
    send({ coverage, timestamp: Date.now(), intervalMs, href: location.href });
  };
  window.setTimeout(() => report(true), 1200);
  window.setInterval(report, intervalMs);
  window.addEventListener('beforeunload', report);
})();`
  }

  function buildCoverageReporterScript(): string {
    return `<script>${buildCoverageReporterCode()}</script>`
  }

  function injectCoverageReporter(body: string, ctx: any): string {
    if (body.includes('__oatCoverageReporterInstalled')) return body
    if (isJavaScriptResponse(ctx)) {
      return `${body}\n${buildCoverageReporterCode()}\n`
    }
    const script = buildCoverageReporterScript()
    if (/<\/head>/i.test(body)) {
      return body.replace(/<\/head>/i, `${script}</head>`)
    }
    if (/<\/body>/i.test(body)) {
      return body.replace(/<\/body>/i, `${script}</body>`)
    }
    return `${body}${script}`
  }

  proxy.onRequest((ctx: any, callback: any) => {
    const protocol = ctx.isSSL ? 'https' : 'http'
    if (!isProtocolEnabled(protocol)) {
      callback()
      return
    }
    const startTime = Date.now()
    const requestId = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
    
    const record: Partial<TrafficRecord> = {
      id: requestId,
      caseName: '',
      method: ctx.clientToProxyRequest.method,
      url: getHttpUrl(ctx),
      protocol: protocol.toUpperCase(),
      timestamp: Date.now(),
      requestHeaders: headerRecord(ctx.clientToProxyRequest.headers),
      source: 'capture'
    }

    if (isCoverageRelayEnabled() && isCoverageReportUrl(record.url)) {
      handleCoverageReportRequest(ctx, record, startTime)
      return
    }

    let requestBody = ''
    ctx.onRequestData((ctx: any, chunk: Buffer, callback: any) => {
      requestBody += chunk.toString()
      return callback(null, chunk)
    })

    ctx.onRequestEnd((ctx: any, callback: any) => {
      record.requestBody = requestBody
      callback()
    })

    let responseBody = ''
    const responseChunks: Buffer[] = []
    let shouldInjectReporter = false
    ctx.onResponse((ctx: any, callback: any) => {
      shouldInjectReporter = shouldInjectCoverageReporter(ctx, record)
      if (shouldInjectReporter) {
        ctx.responseContentPotentiallyModified = true
      }
      callback()
    })

    ctx.onResponseData((ctx: any, chunk: Buffer, callback: any) => {
      if (shouldInjectReporter) {
        responseChunks.push(chunk)
        return callback(null, null)
      }
      responseBody += chunk.toString()
      return callback(null, chunk)
    })

    ctx.onResponseEnd((ctx: any, callback: any) => {
      const endTime = Date.now()
      if (shouldInjectReporter) {
        responseBody = injectCoverageReporter(Buffer.concat(responseChunks).toString(), ctx)
        ctx.proxyToClientResponse.write(responseBody)
      }
      record.duration = endTime - startTime
      record.statusCode = ctx.serverToProxyResponse?.statusCode || 0
      record.responseHeaders = ctx.serverToProxyResponse?.headers
      record.responseBody = responseBody

      onTraffic(record as TrafficRecord)
      callback()
    })

    ctx.onError((ctx: any, err: Error) => {
      record.statusCode = 'ERROR'
      record.error = err.message
      record.duration = Date.now() - startTime
      onTraffic(record as TrafficRecord)
    })

    callback()
  })

  proxy.onWebSocketConnection((ctx: any, callback: any) => {
    const protocol = ctx.isSSL ? 'wss' : 'ws'
    if (!isProtocolEnabled(protocol)) {
      callback()
      return
    }
    const req = ctx.clientToProxyWebSocket?.upgradeReq
    const record: TrafficRecord = {
      id: `ws-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
      caseName: '',
      method: 'WS',
      url: getWebSocketUrl(ctx),
      protocol: protocol.toUpperCase(),
      statusCode: 'OPEN',
      duration: 0,
      timestamp: Date.now(),
      requestHeaders: headerRecord(req?.headers),
      source: 'capture',
      websocketMessages: []
    }
    websocketRecords.set(ctx, record)
    callback()
  })

  proxy.onWebSocketFrame((ctx: any, type: string, fromServer: boolean, message: any, flags: any, callback: any) => {
    const record = websocketRecords.get(ctx)
    if (record && type === 'message') {
      const data = messageData(message)
      record.websocketMessages?.push({
        id: `ws-msg-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        direction: fromServer ? 'receive' : 'send',
        type: data.type,
        data: data.data,
        timestamp: Date.now()
      })
    }
    callback(null, message, flags)
  })

  proxy.onWebSocketClose((ctx: any, code: any, message: any, callback: any) => {
    const record = websocketRecords.get(ctx)
    if (record) {
      record.statusCode = code || 'CLOSED'
      record.duration = Date.now() - record.timestamp
      if (message) {
        record.error = String(message)
      }
      onTraffic(record)
      websocketRecords.delete(ctx)
    }
    callback(null, code, message)
  })

  proxy.onWebSocketError((ctx: any, err: Error | undefined) => {
    const record = websocketRecords.get(ctx)
    if (record) {
      record.statusCode = 'ERROR'
      record.duration = Date.now() - record.timestamp
      record.error = err?.message
      onTraffic(record)
      websocketRecords.delete(ctx)
    }
  })

  return proxy
}
