import { contextBridge, ipcRenderer } from 'electron'
import type { TrafficRecord } from './types.js'

contextBridge.exposeInMainWorld('electronAPI', {
  startCapture: (caseName: string) => ipcRenderer.invoke('start-capture', caseName),
  stopCapture: () => ipcRenderer.invoke('stop-capture'),
  getCaptureState: () => ipcRenderer.invoke('get-capture-state'),
  showFloatingWindow: () => ipcRenderer.invoke('show-floating-window'),
  restoreMainWindow: () => ipcRenderer.invoke('restore-main-window'),
  getTrafficRecords: () => ipcRenderer.invoke('get-traffic-records'),
  clearTrafficRecords: () => ipcRenderer.invoke('clear-traffic-records'),
  deleteTrafficRecord: (id: string) => ipcRenderer.invoke('delete-traffic-record', id),
  exportRecords: (format: string, records: TrafficRecord[]) => 
    ipcRenderer.invoke('export-records', format, records),
  onTrafficCaptured: (callback: (record: TrafficRecord) => void) => {
    ipcRenderer.on('traffic-captured', (_event, record) => callback(record))
  },
  onCaptureStateChanged: (callback: (state: { isCapturing: boolean; caseName: string; port: number }) => void) => {
    ipcRenderer.on('capture-state-changed', (_event, state) => callback(state))
  },
  getProxyStatus: () => ipcRenderer.invoke('get-proxy-status'),
  enableSystemProxy: (port: number) => ipcRenderer.invoke('enable-system-proxy', port),
  disableSystemProxy: () => ipcRenderer.invoke('disable-system-proxy'),
  listSessions: () => ipcRenderer.invoke('list-sessions'),
  loadSession: (sessionId: string) => ipcRenderer.invoke('load-session', sessionId),
  deleteSession: (sessionId: string) => ipcRenderer.invoke('delete-session', sessionId),
  connectMqtt: (config: any) => ipcRenderer.invoke('connect-mqtt', config),
  disconnectMqtt: (id: string) => ipcRenderer.invoke('disconnect-mqtt', id),
  onMqttStatus: (callback: (data: any) => void) => {
    ipcRenderer.on('mqtt-status', (_event, data) => callback(data))
  },
  getCertInfo: () => ipcRenderer.invoke('get-cert-info'),
  generateCert: () => ipcRenderer.invoke('generate-cert'),
  installCert: () => ipcRenderer.invoke('install-cert'),
  openCertFolder: () => ipcRenderer.invoke('open-cert-folder')
})
