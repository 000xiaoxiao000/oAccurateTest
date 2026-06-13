import { app, BrowserWindow, ipcMain, dialog } from 'electron'
import path from 'path'
import fs from 'fs'
import { fileURLToPath } from 'url'
import { createProxyServer } from './proxy.js'
import { disableSystemProxy, enableSystemProxy, getSystemProxyStatus } from './systemProxy.js'
import { generateRootCert, getCertInfo, installCertMacOS, openCertFolder } from './certificate.js'
import { connectMqtt, disconnectAllMqtt, disconnectMqtt } from './protocols/mqtt.js'
import {
  deleteSession,
  initDatabase,
  listSessions,
  loadSessionRecords,
  saveRecord,
  saveSession,
  updateSessionEndTime
} from './database.js'
import type { TrafficRecord } from './types.js'
import ExcelJS from 'exceljs'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

let mainWindow: BrowserWindow | null = null
let proxyServer: any = null
let captureEnabled = false
let currentCaseName = ''
let currentSessionId = ''
const trafficRecords: TrafficRecord[] = []
const PROXY_PORT = 8888

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

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 1000,
    minHeight: 600,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      nodeIntegration: false,
      contextIsolation: true
    }
  })

  if (process.env.NODE_ENV === 'development') {
    mainWindow.loadURL('http://localhost:5173')
    mainWindow.webContents.openDevTools()
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'))
  }

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

app.whenReady().then(() => {
  initDatabase()
  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (proxyServer) {
    proxyServer.close()
  }
  disconnectAllMqtt()
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

ipcMain.handle('start-capture', async (_event, caseName: string) => {
  try {
    if (!proxyServer) {
      proxyServer = createProxyServer((record: TrafficRecord) => {
        if (captureEnabled) {
          record.caseName = currentCaseName
          trafficRecords.push(record)
          saveRecord(record, currentSessionId)
          mainWindow?.webContents.send('traffic-captured', record)
        }
      })

      await startProxyServer(proxyServer, PROXY_PORT)
    }

    currentCaseName = caseName
    captureEnabled = true
    currentSessionId = `session-${Date.now()}`
    saveSession({ id: currentSessionId, caseName, startTime: Date.now() })

    return { success: true, port: proxyServer.httpPort ?? PROXY_PORT }
  } catch (error: any) {
    captureEnabled = false
    proxyServer = null
    return { success: false, error: error?.message ?? String(error) }
  }
})

ipcMain.handle('stop-capture', async () => {
  captureEnabled = false
  if (currentSessionId) {
    updateSessionEndTime(currentSessionId, Date.now())
  }
  return { success: true }
})

ipcMain.handle('get-traffic-records', async () => {
  return trafficRecords
})

ipcMain.handle('clear-traffic-records', async () => {
  trafficRecords.length = 0
  return { success: true }
})

ipcMain.handle('delete-traffic-record', async (_event, id: string) => {
  const index = trafficRecords.findIndex(r => r.id === id)
  if (index !== -1) {
    trafficRecords.splice(index, 1)
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
    workbook.creator = '流量捕获工具'
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

ipcMain.handle('get-proxy-status', async () => {
  try {
    return await getSystemProxyStatus()
  } catch (error: any) {
    return { enabled: false, error: error?.message ?? String(error) }
  }
})

ipcMain.handle('enable-system-proxy', async (_event, port: number) => {
  try {
    await enableSystemProxy({ port, bypass: ['localhost', '127.0.0.1', '*.local'] })
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

ipcMain.handle('load-session', async (_event, sessionId: string) => loadSessionRecords(sessionId))

ipcMain.handle('delete-session', async (_event, sessionId: string) => {
  deleteSession(sessionId)
  return { success: true }
})

ipcMain.handle('connect-mqtt', async (_event, config) => {
  try {
    connectMqtt(
      config,
      (record) => {
        if (captureEnabled) {
          record.caseName = currentCaseName
          trafficRecords.push(record)
          saveRecord(record, currentSessionId)
          mainWindow?.webContents.send('traffic-captured', record)
        }
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
    return { success: true, ...generateRootCert() }
  } catch (error: any) {
    return { success: false, error: error.message }
  }
})

ipcMain.handle('install-cert', async () => {
  const info = getCertInfo()
  if (!info.certPath) return { success: false, error: '证书不存在，请先生成' }
  return await installCertMacOS(info.certPath)
})

ipcMain.handle('open-cert-folder', async () => {
  const info = getCertInfo()
  if (info.certPath) openCertFolder(info.certPath)
})
