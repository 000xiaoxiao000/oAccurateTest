const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('electronAPI', {
  startCapture: (caseName) => ipcRenderer.invoke('start-capture', caseName),
  stopCapture: () => ipcRenderer.invoke('stop-capture'),
  getCaptureState: () => ipcRenderer.invoke('get-capture-state'),
  showFloatingWindow: () => ipcRenderer.invoke('show-floating-window'),
  restoreMainWindow: () => ipcRenderer.invoke('restore-main-window'),
  getTrafficRecords: () => ipcRenderer.invoke('get-traffic-records'),
  clearTrafficRecords: () => ipcRenderer.invoke('clear-traffic-records'),
  deleteTrafficRecord: (id) => ipcRenderer.invoke('delete-traffic-record', id),
  exportRecords: (format, records) => ipcRenderer.invoke('export-records', format, records),
  onTrafficCaptured: (callback) => {
    ipcRenderer.on('traffic-captured', (_event, record) => callback(record))
  },
  onCaptureStateChanged: (callback) => {
    ipcRenderer.on('capture-state-changed', (_event, state) => callback(state))
  },
  getProxyStatus: () => ipcRenderer.invoke('get-proxy-status'),
  enableSystemProxy: (port) => ipcRenderer.invoke('enable-system-proxy', port),
  disableSystemProxy: () => ipcRenderer.invoke('disable-system-proxy'),
  listSessions: () => ipcRenderer.invoke('list-sessions'),
  loadSession: (sessionId) => ipcRenderer.invoke('load-session', sessionId),
  deleteSession: (sessionId) => ipcRenderer.invoke('delete-session', sessionId),
  connectMqtt: (config) => ipcRenderer.invoke('connect-mqtt', config),
  disconnectMqtt: (id) => ipcRenderer.invoke('disconnect-mqtt', id),
  onMqttStatus: (callback) => {
    ipcRenderer.on('mqtt-status', (_event, data) => callback(data))
  },
  getCertInfo: () => ipcRenderer.invoke('get-cert-info'),
  generateCert: () => ipcRenderer.invoke('generate-cert'),
  installCert: () => ipcRenderer.invoke('install-cert'),
  openCertFolder: () => ipcRenderer.invoke('open-cert-folder')
})
