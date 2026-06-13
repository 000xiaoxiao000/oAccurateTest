import Proxy from 'http-mitm-proxy'
import type { TrafficRecord } from './types.js'

export function createProxyServer(onTraffic: (record: TrafficRecord) => void) {
  const proxy = Proxy()

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
      requestHeaders: ctx.clientToProxyRequest.headers
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

  return proxy
}
