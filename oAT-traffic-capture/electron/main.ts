import { app, BrowserWindow, ipcMain, dialog } from 'electron'
import path from 'path'
import fs from 'fs'
import { fileURLToPath } from 'url'
import { createProxyServer } from './proxy.js'
import { disableSystemProxy, enableSystemProxy, getSystemProxyStatus } from './systemProxy.js'
import { generateRootCert, getCertInfo, getProxyCaDir, installCertMacOS, openCertFolder, uninstallCertMacOS } from './certificate.js'
import { connectMqtt, disconnectAllMqtt, disconnectMqtt } from './protocols/mqtt.js'
import { applyCaptureRules } from './filterRules.js'
import { replayRecord } from './replay.js'
import {
  getPluginsPath,
  installBuiltinPlugin,
  listPlugins,
  loadPlugins,
  openPluginsFolder,
  runBeforeSaveHooks,
  runRecordCapturedHooks,
  uninstallBuiltinPlugin
} from './plugins/pluginManager.js'
import {
  deleteSession,
  initDatabase,
  listFilterRules,
  listSessions,
  loadSessionRecords,
  saveRecord,
  saveFilterRules,
  saveSession,
  updateSessionEndTime
} from './database.js'
import type { TrafficFilterRule, TrafficRecord } from './types.js'
import ExcelJS from 'exceljs'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

let mainWindow: BrowserWindow | null = null
let floatingWindow: BrowserWindow | null = null
let proxyServer: any = null
let captureEnabled = false
let currentCaseName = ''
let currentSessionId = ''
const trafficRecords: TrafficRecord[] = []
let filterRules: TrafficFilterRule[] = []
const PROXY_PORT = 8888

function getCaptureState() {
  return {
    isCapturing: captureEnabled,
    caseName: currentCaseName,
    port: proxyServer?.httpPort ?? PROXY_PORT,
    recordCount: trafficRecords.length
  }
}

function broadcastCaptureState() {
  const state = getCaptureState()
  mainWindow?.webContents.send('capture-state-changed', state)
  floatingWindow?.webContents.send('capture-state-changed', state)
}

function closeProxyServer() {
  if (proxyServer) {
    proxyServer.close()
    proxyServer = null
  }
}

async function acceptCapturedRecord(record: TrafficRecord) {
  if (!captureEnabled) return
  const afterPlugins = await runRecordCapturedHooks({
    ...record,
    caseName: currentCaseName,
    source: record.source ?? 'capture'
  })
  if (!afterPlugins) return
  const afterRules = applyCaptureRules(afterPlugins, filterRules)
  if (!afterRules) return
  const beforeSave = await runBeforeSaveHooks(afterRules)
  if (!beforeSave) return

  trafficRecords.push(beforeSave)
  saveRecord(beforeSave, currentSessionId)
  mainWindow?.webContents.send('traffic-captured', beforeSave)
  floatingWindow?.webContents.send('traffic-captured', beforeSave)
  broadcastCaptureState()
}

function startProxyServer(server: any, port: number): Promise<number> {
  return new Promise((resolve, reject) => {
    server.listen({ port }, (error?: Error) => {
      if (error) {
        reject(error)
        return
      }
      resolve(server.httpPort ?? port)
    })
  })
}

async function ensureProxyServer(): Promise<number> {
  if (!proxyServer) {
    if (!getCertInfo().exists) {
      generateRootCert()
    }
    proxyServer = createProxyServer((record: TrafficRecord) => {
      acceptCapturedRecord(record)
    }, getProxyCaDir())

    await startProxyServer(proxyServer, PROXY_PORT)
  }

  return proxyServer.httpPort ?? PROXY_PORT
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 920,
    height: 620,
    minWidth: 780,
    minHeight: 500,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      nodeIntegration: false,
      contextIsolation: true
    }
  })

  mainWindow.webContents.on('did-finish-load', () => {
    mainWindow?.webContents.setZoomFactor(0.82)
  })

  loadAppWindow(mainWindow)

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

function loadAppWindow(window: BrowserWindow, query = '') {
  if (process.env.NODE_ENV === 'development') {
    window.loadURL(`http://localhost:5173${query}`)
  } else {
    window.loadFile(path.join(__dirname, '../dist/index.html'), query ? { query: { floating: '1' } } : undefined)
  }
}

function createFloatingWindow() {
  if (floatingWindow) {
    floatingWindow.show()
    floatingWindow.focus()
    return
  }

  floatingWindow = new BrowserWindow({
    width: 260,
    height: 172,
    minWidth: 260,
    minHeight: 172,
    maxWidth: 260,
    maxHeight: 172,
    frame: false,
    resizable: false,
    alwaysOnTop: true,
    skipTaskbar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      nodeIntegration: false,
      contextIsolation: true
    }
  })

  floatingWindow.setAlwaysOnTop(true, 'floating')
  loadAppWindow(floatingWindow, '?floating=1')

  floatingWindow.on('closed', () => {
    floatingWindow = null
  })
}

function restoreMainWindow() {
  if (!mainWindow) {
    createWindow()
  }
  mainWindow?.show()
  mainWindow?.focus()
  floatingWindow?.close()
}

app.whenReady().then(() => {
  initDatabase()
  filterRules = listFilterRules()
  loadPlugins()
  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  closeProxyServer()
  disconnectAllMqtt()
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

ipcMain.handle('start-capture', async (_event, caseName: string) => {
  try {
    const port = await ensureProxyServer()

    currentCaseName = caseName
    captureEnabled = true
    currentSessionId = `session-${Date.now()}`
    saveSession({ id: currentSessionId, caseName, startTime: Date.now() })
    broadcastCaptureState()

    return { success: true, port }
  } catch (error: any) {
    captureEnabled = false
    proxyServer = null
    broadcastCaptureState()
    return { success: false, error: error?.message ?? String(error) }
  }
})

ipcMain.handle('stop-capture', async () => {
  captureEnabled = false
  if (currentSessionId) {
    updateSessionEndTime(currentSessionId, Date.now())
  }
  broadcastCaptureState()
  return { success: true }
})

ipcMain.handle('get-capture-state', async () => getCaptureState())

ipcMain.handle('show-floating-window', async () => {
  createFloatingWindow()
  mainWindow?.hide()
  return { success: true }
})

ipcMain.handle('restore-main-window', async () => {
  restoreMainWindow()
  return { success: true }
})

ipcMain.handle('get-traffic-records', async () => {
  return trafficRecords
})

ipcMain.handle('clear-traffic-records', async () => {
  trafficRecords.length = 0
  broadcastCaptureState()
  return { success: true }
})

ipcMain.handle('list-filter-rules', async () => filterRules)

ipcMain.handle('save-filter-rules', async (_event, rules: TrafficFilterRule[]) => {
  filterRules = rules
  saveFilterRules(filterRules)
  return { success: true }
})

ipcMain.handle('delete-traffic-record', async (_event, id: string) => {
  const index = trafficRecords.findIndex(r => r.id === id)
  if (index !== -1) {
    trafficRecords.splice(index, 1)
    broadcastCaptureState()
    return { success: true }
  }
  return { success: false }
})

ipcMain.handle('export-records', async (_event, format: string, records: TrafficRecord[]) => {
  if (format === 'json') {
    const { filePath } = await dialog.showSaveDialog({
      defaultPath: `traffic-export-${Date.now()}.json`,
      filters: [{ name: 'JSON', extensions: ['json'] }]
    })
    if (!filePath) return { success: false }
    fs.writeFileSync(filePath, JSON.stringify(records, null, 2), 'utf-8')
    return { success: true, filePath }
  }

  if (format === 'csv') {
    const { filePath } = await dialog.showSaveDialog({
      defaultPath: `traffic-export-${Date.now()}.csv`,
      filters: [{ name: 'CSV', extensions: ['csv'] }]
    })
    if (!filePath) return { success: false }
    const headers = ['用例名称', '方法', 'URL', '协议', '状态码', '耗时(ms)', '时间']
    const rows = records.map(r => [
      r.caseName, r.method, r.url, r.protocol,
      String(r.statusCode), String(r.duration),
      new Date(r.timestamp).toLocaleString('zh-CN')
    ])
    const csv = [headers, ...rows].map(row => row.map(c => `"${c}"`).join(',')).join('\n')
    fs.writeFileSync(filePath, '\uFEFF' + csv, 'utf-8')
    return { success: true, filePath }
  }

  if (format === 'excel') {
    const { filePath } = await dialog.showSaveDialog({
      defaultPath: `traffic-export-${Date.now()}.xlsx`,
      filters: [{ name: 'Excel', extensions: ['xlsx'] }]
    })
    if (!filePath) return { success: false }

    const workbook = new ExcelJS.Workbook()
    workbook.creator = 'oAT 流量采集器'
    workbook.created = new Date()

    const sheet = workbook.addWorksheet('流量记录')
    sheet.columns = [
      { header: '用例名称', key: 'caseName', width: 20 },
      { header: '方法', key: 'method', width: 10 },
      { header: 'URL', key: 'url', width: 60 },
      { header: '协议', key: 'protocol', width: 10 },
      { header: '状态码', key: 'statusCode', width: 10 },
      { header: '耗时(ms)', key: 'duration', width: 12 },
      { header: '时间', key: 'time', width: 22 },
      { header: '请求体', key: 'requestBody', width: 40 },
      { header: '响应体', key: 'responseBody', width: 40 }
    ]

    // Style header row
    const headerRow = sheet.getRow(1)
    headerRow.font = { bold: true, color: { argb: 'FFFFFFFF' } }
    headerRow.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF667EEA' } }
    headerRow.alignment = { vertical: 'middle', horizontal: 'center' }
    headerRow.height = 22

    records.forEach(r => {
      sheet.addRow({
        caseName: r.caseName,
        method: r.method,
        url: r.url,
        protocol: r.protocol,
        statusCode: r.statusCode,
        duration: r.duration,
        time: new Date(r.timestamp).toLocaleString('zh-CN'),
        requestBody: r.requestBody || '',
        responseBody: r.responseBody || ''
      })
    })

    await workbook.xlsx.writeFile(filePath)
    return { success: true, filePath }
  }

  return { success: false }
})

ipcMain.handle('replay-record', async (_event, record: TrafficRecord) => {
  const result = await replayRecord(record)
  if (result.record) {
    result.record.caseName = currentCaseName || record.caseName
    trafficRecords.push(result.record)
    saveRecord(result.record, currentSessionId || undefined)
    mainWindow?.webContents.send('traffic-captured', result.record)
    floatingWindow?.webContents.send('traffic-captured', result.record)
    broadcastCaptureState()
  }
  return result
})

ipcMain.handle('replay-records', async (_event, records: TrafficRecord[]) => {
  const results = []
  for (const record of records) {
    const result = await replayRecord(record)
    if (result.record) {
      result.record.caseName = currentCaseName || record.caseName
      trafficRecords.push(result.record)
      saveRecord(result.record, currentSessionId || undefined)
      mainWindow?.webContents.send('traffic-captured', result.record)
      floatingWindow?.webContents.send('traffic-captured', result.record)
    }
    results.push(result)
  }
  broadcastCaptureState()
  return results
})

ipcMain.handle('get-proxy-status', async () => {
  try {
    return await getSystemProxyStatus()
  } catch (error: any) {
    return { enabled: false, error: error?.message ?? String(error) }
  }
})

ipcMain.handle('enable-system-proxy', async (_event, port: number) => {
  try {
    const proxyPort = await ensureProxyServer()
    await enableSystemProxy({ port: proxyPort || port, bypass: ['localhost', '127.0.0.1', '*.local'] })
    broadcastCaptureState()
    return { success: true }
  } catch (error: any) {
    return { success: false, error: error?.message ?? String(error) }
  }
})

ipcMain.handle('disable-system-proxy', async () => {
  try {
    await disableSystemProxy()
    return { success: true }
  } catch (error: any) {
    return { success: false, error: error?.message ?? String(error) }
  }
})

ipcMain.handle('list-sessions', async () => listSessions())

ipcMain.handle('load-session', async (_event, sessionId: string) => {
  const records = loadSessionRecords(sessionId)
  trafficRecords.length = 0
  trafficRecords.push(...records)
  broadcastCaptureState()
  return records
})

ipcMain.handle('delete-session', async (_event, sessionId: string) => {
  deleteSession(sessionId)
  return { success: true }
})

ipcMain.handle('connect-mqtt', async (_event, config) => {
  try {
    connectMqtt(
      config,
      (record) => {
        acceptCapturedRecord(record)
      },
      (id, status, error) => {
        mainWindow?.webContents.send('mqtt-status', { id, status, error })
      }
    )
    return { success: true }
  } catch (error: any) {
    return { success: false, error: error.message }
  }
})

ipcMain.handle('disconnect-mqtt', async (_event, id: string) => {
  disconnectMqtt(id)
  return { success: true }
})

ipcMain.handle('get-cert-info', async () => getCertInfo())

ipcMain.handle('generate-cert', async () => {
  try {
    closeProxyServer()
    captureEnabled = false
    const result = generateRootCert()
    broadcastCaptureState()
    return { success: true, ...result }
  } catch (error: any) {
    return { success: false, error: error.message }
  }
})

ipcMain.handle('install-cert', async () => {
  const info = getCertInfo()
  if (!info.certPath) return { success: false, error: '证书不存在，请先生成' }
  return await installCertMacOS(info.certPath)
})

ipcMain.handle('uninstall-cert', async () => {
  closeProxyServer()
  captureEnabled = false
  broadcastCaptureState()
  return uninstallCertMacOS()
})

ipcMain.handle('open-cert-folder', async () => {
  const info = getCertInfo()
  if (info.certPath) openCertFolder(info.certPath)
})

ipcMain.handle('list-plugins', async () => listPlugins())

ipcMain.handle('reload-plugins', async () => loadPlugins())

ipcMain.handle('get-plugins-path', async () => getPluginsPath())

ipcMain.handle('open-plugins-folder', async () => {
  openPluginsFolder()
  return { success: true }
})

ipcMain.handle('install-builtin-plugin', async () => installBuiltinPlugin())

ipcMain.handle('uninstall-builtin-plugin', async () => uninstallBuiltinPlugin())
