import Proxy from 'http-mitm-proxy'
import type { TrafficRecord, WsMessage } from './types.js'

export function createProxyServer(onTraffic: (record: TrafficRecord) => void) {
  const proxy = Proxy()
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

  function messageData(message: any): Pick<WsMessage, 'type' | 'data'> {
    if (Buffer.isBuffer(message)) {
      return { type: 'binary', data: message.toString('base64') }
    }
    return { type: 'text', data: String(message) }
  }

  proxy.onRequest((ctx: any, callback: any) => {
    const startTime = Date.now()
    const requestId = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
    
    const record: Partial<TrafficRecord> = {
      id: requestId,
      caseName: '',
      method: ctx.clientToProxyRequest.method,
      url: ctx.clientToProxyRequest.url || `${ctx.isSSL ? 'https' : 'http'}://${ctx.clientToProxyRequest.headers.host}${ctx.clientToProxyRequest.url}`,
      protocol: ctx.isSSL ? 'HTTPS' : 'HTTP',
      timestamp: Date.now(),
      requestHeaders: headerRecord(ctx.clientToProxyRequest.headers),
      source: 'capture'
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
    ctx.onResponseData((ctx: any, chunk: Buffer, callback: any) => {
      responseBody += chunk.toString()
      return callback(null, chunk)
    })

    ctx.onResponseEnd((ctx: any, callback: any) => {
      const endTime = Date.now()
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
    const req = ctx.clientToProxyWebSocket?.upgradeReq
    const record: TrafficRecord = {
      id: `ws-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
      caseName: '',
      method: 'WS',
      url: getWebSocketUrl(ctx),
      protocol: ctx.isSSL ? 'WSS' : 'WS',
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
