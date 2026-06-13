import type { PluginInfo, ReplayResult, TrafficFilterRule, TrafficRecord } from '../types/traffic'

declare global {
  interface Window {
    electronAPI: {
      startCapture: (caseName: string) => Promise<{ success: boolean; port?: number; error?: string }>
      stopCapture: () => Promise<{ success: boolean }>
      getCaptureState: () => Promise<{ isCapturing: boolean; caseName: string; port: number; recordCount: number }>
      showFloatingWindow: () => Promise<{ success: boolean }>
      restoreMainWindow: () => Promise<{ success: boolean }>
      getTrafficRecords: () => Promise<TrafficRecord[]>
      clearTrafficRecords: () => Promise<{ success: boolean }>
      deleteTrafficRecord: (id: string) => Promise<{ success: boolean }>
      listFilterRules: () => Promise<TrafficFilterRule[]>
      saveFilterRules: (rules: TrafficFilterRule[]) => Promise<{ success: boolean }>
      replayRecord: (record: TrafficRecord) => Promise<ReplayResult>
      replayRecords: (records: TrafficRecord[]) => Promise<ReplayResult[]>
      exportRecords: (format: string, records: TrafficRecord[]) => Promise<{ success: boolean; filePath?: string }>
      onTrafficCaptured: (callback: (record: TrafficRecord) => void) => void
      onCaptureStateChanged: (callback: (state: { isCapturing: boolean; caseName: string; port: number; recordCount: number }) => void) => void
      getProxyStatus: () => Promise<{ enabled: boolean; port?: number }>
      enableSystemProxy: (port: number) => Promise<{ success: boolean; error?: string }>
      disableSystemProxy: () => Promise<{ success: boolean; error?: string }>
      listSessions: () => Promise<Array<{
        id: string
        case_name: string
        start_time: number
        end_time: number | null
        record_count: number
      }>>
      loadSession: (sessionId: string) => Promise<TrafficRecord[]>
      deleteSession: (sessionId: string) => Promise<{ success: boolean }>
      connectMqtt: (config: {
        id: string
        brokerUrl: string
        topics: string[]
        username?: string
        password?: string
      }) => Promise<{ success: boolean; error?: string }>
      disconnectMqtt: (id: string) => Promise<{ success: boolean }>
      onMqttStatus: (callback: (data: { id: string; status: string; error?: string }) => void) => void
      getCertInfo: () => Promise<{ exists: boolean; certPath?: string; expiresAt?: string }>
      generateCert: () => Promise<{ success: boolean; certPath?: string; keyPath?: string; error?: string }>
      installCert: () => Promise<{ success: boolean; error?: string }>
      uninstallCert: () => Promise<{ success: boolean; error?: string }>
      openCertFolder: () => Promise<void>
      listPlugins: () => Promise<PluginInfo[]>
      reloadPlugins: () => Promise<PluginInfo[]>
      getPluginsPath: () => Promise<string>
      openPluginsFolder: () => Promise<{ success: boolean }>
      installBuiltinPlugin: () => Promise<PluginInfo[]>
      uninstallBuiltinPlugin: () => Promise<PluginInfo[]>
    }
  }
}

export {}
