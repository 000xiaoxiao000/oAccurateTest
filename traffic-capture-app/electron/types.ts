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

export interface CaptureSession {
  id: string
  caseName: string
  startTime: number
  endTime?: number
  records: TrafficRecord[]
}
