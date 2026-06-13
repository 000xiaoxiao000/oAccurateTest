// Traffic record type shared between main and renderer processes
export interface TrafficRecord {
  id: string
  caseName: string
  method: string
  url: string
  protocol: string
  statusCode: number | string
  duration: number
  timestamp: number
  requestHeaders?: Record<string, string>
  requestBody?: string
  responseHeaders?: Record<string, string>
  responseBody?: string
  error?: string
}

export interface ProxyStatus {
  running: boolean
  port: number
  error?: string
}

export interface CaptureSession {
  id: string
  caseName: string
  startTime: number
  endTime?: number
  records: TrafficRecord[]
}

export interface WsMessage {
  id: string
  direction: 'send' | 'receive'
  type: 'text' | 'binary'
  data: string
  timestamp: number
}

export interface WebSocketRecord extends TrafficRecord {
  protocol: 'WS' | 'WSS'
  messages: WsMessage[]
  connectionState: 'open' | 'closed'
}
