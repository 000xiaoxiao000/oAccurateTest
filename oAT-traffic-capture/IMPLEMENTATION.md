# oAT 流量采集器桌面 App — 完整实施手册

> 基于现有代码基础，完善系统代理集成、多协议支持、数据持久化和证书管理。

---

## 前置准备：修复依赖安装

```bash
# 修复 npm 日志目录权限
sudo mkdir -p /Users/xiaoxiao/.npm/_logs
sudo chown -R xiaoxiao /Users/xiaoxiao/.npm

# 切换国内镜像源
cd /Users/xiaoxiao/javaProject/oAccurateTest/oAT-traffic-capture
npm config set registry https://registry.npmmirror.com

# 安装现有依赖
npm install

# 追加新协议依赖（精确版本）
npm install mqtt@5.10.1 @grpc/grpc-js@1.12.0
```

---

## 目录结构（最终）

```
electron/
  main.ts           # 主进程（增强所有 IPC）
  preload.ts        # 预加载脚本（暴露新 API）
  proxy.ts          # HTTP/HTTPS 代理（不变）
  systemProxy.ts    # 系统代理管理（不变）
  database.ts       # 新增：SQLite 持久化
  certificate.ts    # 新增：证书管理
  protocols/
    websocket.ts    # 新增：WebSocket 处理
    mqtt.ts         # 新增：MQTT 监听
src/
  components/
    ProxyControl.vue    # 新增：代理开关
    SessionHistory.vue  # 新增：历史会话
    TrafficTable.vue    # 不变
    DetailModal.vue     # 不变
    MqInputModal.vue    # 不变
  stores/
    traffic.ts          # 不变
  types/
    traffic.ts          # 追加 WS 类型
    electron.d.ts       # 追加新 API 类型
  App.vue               # 引入 ProxyControl + SessionHistory
```

---

## 实施步骤总览

| # | 文件 | 操作 |
|---|------|------|
| 1 | shell | 修复 npm 权限 + 安装依赖 |
| 2 | `electron/main.ts` | 追加系统代理、数据库、MQTT、证书 IPC |
| 3 | `electron/preload.ts` | 暴露新 API |
| 4 | `src/types/electron.d.ts` | 更新类型定义 |
| 5 | `src/types/traffic.ts` | 追加 WS 相关类型 |
| 6 | `electron/database.ts` | 新建数据库模块 |
| 7 | `electron/certificate.ts` | 新建证书模块 |
| 8 | `electron/protocols/websocket.ts` | 新建 WS 处理模块 |
| 9 | `electron/protocols/mqtt.ts` | 新建 MQTT 监听模块 |
| 10 | `src/components/ProxyControl.vue` | 新建代理开关组件 |
| 11 | `src/components/SessionHistory.vue` | 新建历史会话组件 |
| 12 | `src/App.vue` | 引入两个新组件 |
| 13 | shell | `npm run build` 验证构建 |

---

## Phase 1：系统代理 UI 集成

### 步骤 1.1：扩展 IPC 通道（`electron/main.ts`）

在文件末尾追加以下代码：

```typescript
import { enableSystemProxy, disableSystemProxy, getSystemProxyStatus } from './systemProxy.js'

ipcMain.handle('get-proxy-status', async () => {
  return await getSystemProxyStatus()
})

ipcMain.handle('enable-system-proxy', async (_event, port: number) => {
  try {
    await enableSystemProxy({ port, bypass: ['localhost', '127.0.0.1', '*.local'] })
    return { success: true }
  } catch (error: any) {
    return { success: false, error: error.message }
  }
})

ipcMain.handle('disable-system-proxy', async () => {
  try {
    await disableSystemProxy()
    return { success: true }
  } catch (error: any) {
    return { success: false, error: error.message }
  }
})
```

### 步骤 1.2：暴露 API（`electron/preload.ts`）

在 `contextBridge.exposeInMainWorld` 的 `electronAPI` 对象中追加：

```typescript
getProxyStatus: () => ipcRenderer.invoke('get-proxy-status'),
enableSystemProxy: (port: number) => ipcRenderer.invoke('enable-system-proxy', port),
disableSystemProxy: () => ipcRenderer.invoke('disable-system-proxy'),
```

### 步骤 1.3：更新类型定义（`src/types/electron.d.ts`）

在 `electronAPI` 接口中追加：

```typescript
getProxyStatus: () => Promise<{ enabled: boolean; port?: number }>
enableSystemProxy: (port: number) => Promise<{ success: boolean; error?: string }>
disableSystemProxy: () => Promise<{ success: boolean; error?: string }>
```

### 步骤 1.4：创建代理控制组件（新建 `src/components/ProxyControl.vue`）

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'

const systemProxyEnabled = ref(false)
const loading = ref(false)
const port = defineProps<{ port: number }>().port

onMounted(async () => {
  const status = await window.electronAPI?.getProxyStatus()
  systemProxyEnabled.value = status?.enabled ?? false
})

async function toggleSystemProxy() {
  loading.value = true
  if (systemProxyEnabled.value) {
    const result = await window.electronAPI?.disableSystemProxy()
    if (result?.success) systemProxyEnabled.value = false
    else alert('关闭系统代理失败：' + result?.error)
  } else {
    const result = await window.electronAPI?.enableSystemProxy(port)
    if (result?.success) systemProxyEnabled.value = true
    else alert('开启系统代理失败（需管理员权限）：' + result?.error)
  }
  loading.value = false
}
</script>

<template>
  <div class="proxy-control">
    <span class="label">系统代理</span>
    <button
      class="toggle-btn"
      :class="{ active: systemProxyEnabled, loading }"
      :disabled="loading"
      @click="toggleSystemProxy"
    >
      <span class="dot"></span>
      {{ loading ? '处理中...' : systemProxyEnabled ? '已启用 (127.0.0.1:' + port + ')' : '未启用' }}
    </button>
    <span class="hint">启用后流量自动经过代理，无需手动配置</span>
  </div>
</template>

<style scoped>
.proxy-control { display: flex; align-items: center; gap: 12px; padding: 8px 0; }
.label { font-size: 13px; font-weight: 500; color: #262626; }
.toggle-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 5px 14px; border: 1px solid #d9d9d9; border-radius: 20px;
  background: white; font-size: 13px; cursor: pointer; transition: all 0.2s;
}
.toggle-btn.active { background: #f6ffed; border-color: #52c41a; color: #389e0d; }
.toggle-btn.loading { opacity: 0.6; cursor: not-allowed; }
.dot { width: 7px; height: 7px; border-radius: 50%; background: #d9d9d9; }
.toggle-btn.active .dot { background: #52c41a; }
.hint { font-size: 12px; color: #8c8c8c; }
</style>
```

### 步骤 1.5：在 `src/App.vue` 中引入 ProxyControl

在 `<script setup>` import 区追加：

```typescript
import ProxyControl from './components/ProxyControl.vue'
```

在 `<div class="header-content">` 内的标题 `<div>` 之后追加：

```vue
<ProxyControl :port="store.proxyPort" />
```


---

## Phase 2：SQLite 数据持久化

### 步骤 2.1：创建数据库模块（新建 `electron/database.ts`）

```typescript
import Database from 'better-sqlite3'
import path from 'path'
import { app } from 'electron'
import type { TrafficRecord, CaptureSession } from '../src/types/traffic'

let db: Database.Database

export function initDatabase(): void {
  const dbPath = path.join(app.getPath('userData'), 'traffic.db')
  db = new Database(dbPath)
  db.exec(`
    CREATE TABLE IF NOT EXISTS sessions (
      id TEXT PRIMARY KEY,
      case_name TEXT NOT NULL,
      start_time INTEGER NOT NULL,
      end_time INTEGER
    );
    CREATE TABLE IF NOT EXISTS records (
      id TEXT PRIMARY KEY,
      session_id TEXT,
      method TEXT,
      url TEXT,
      protocol TEXT,
      status_code TEXT,
      duration INTEGER,
      timestamp INTEGER,
      request_headers TEXT,
      request_body TEXT,
      response_headers TEXT,
      response_body TEXT,
      error TEXT,
      FOREIGN KEY(session_id) REFERENCES sessions(id)
    );
    CREATE INDEX IF NOT EXISTS idx_url ON records(url);
    CREATE INDEX IF NOT EXISTS idx_timestamp ON records(timestamp);
    CREATE INDEX IF NOT EXISTS idx_session ON records(session_id);
  `)
}

export function saveSession(session: Omit<CaptureSession, 'records'>): void {
  db.prepare(
    'INSERT OR REPLACE INTO sessions (id, case_name, start_time, end_time) VALUES (?,?,?,?)'
  ).run(session.id, session.caseName, session.startTime, session.endTime ?? null)
}

export function saveRecord(record: TrafficRecord, sessionId?: string): void {
  db.prepare(`
    INSERT OR REPLACE INTO records
    (id,session_id,method,url,protocol,status_code,duration,timestamp,
     request_headers,request_body,response_headers,response_body,error)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
  `).run(
    record.id, sessionId ?? null, record.method, record.url, record.protocol,
    String(record.statusCode), record.duration, record.timestamp,
    JSON.stringify(record.requestHeaders ?? {}),
    record.requestBody ?? '',
    JSON.stringify(record.responseHeaders ?? {}),
    record.responseBody ?? '',
    record.error ?? null
  )
}

export function listSessions(): Array<any> {
  return db.prepare(`
    SELECT s.*, COUNT(r.id) as record_count
    FROM sessions s
    LEFT JOIN records r ON r.session_id = s.id
    GROUP BY s.id
    ORDER BY s.start_time DESC
    LIMIT 100
  `).all()
}

export function loadSessionRecords(sessionId: string): TrafficRecord[] {
  const rows = db.prepare('SELECT * FROM records WHERE session_id = ? ORDER BY timestamp ASC')
    .all(sessionId) as any[]
  return rows.map(r => ({
    id: r.id,
    caseName: '',
    method: r.method,
    url: r.url,
    protocol: r.protocol,
    statusCode: r.status_code,
    duration: r.duration,
    timestamp: r.timestamp,
    requestHeaders: JSON.parse(r.request_headers || '{}'),
    requestBody: r.request_body,
    responseHeaders: JSON.parse(r.response_headers || '{}'),
    responseBody: r.response_body,
    error: r.error
  }))
}

export function deleteSession(sessionId: string): void {
  db.prepare('DELETE FROM records WHERE session_id = ?').run(sessionId)
  db.prepare('DELETE FROM sessions WHERE id = ?').run(sessionId)
}
```

### 步骤 2.2：在 `electron/main.ts` 中集成数据库

在顶部 import 区追加：

```typescript
import { initDatabase, saveRecord, saveSession, listSessions, loadSessionRecords, deleteSession } from './database.js'
```

在模块级变量区追加：

```typescript
let currentSessionId = ''
```

在 `app.whenReady()` 的 `createWindow()` 之前调用：

```typescript
initDatabase()
```

在 `start-capture` handler 中，`return { success: true, port: 8888 }` 之前追加：

```typescript
currentSessionId = `session-${Date.now()}`
saveSession({ id: currentSessionId, caseName, startTime: Date.now() })
```

在 `createProxyServer` 的回调中，找到 `trafficRecords.push(record)` 后追加：

```typescript
saveRecord(record, currentSessionId)
```

在文件末尾追加数据库 IPC handlers：

```typescript
ipcMain.handle('list-sessions', async () => listSessions())
ipcMain.handle('load-session', async (_e, sessionId: string) => loadSessionRecords(sessionId))
ipcMain.handle('delete-session', async (_e, sessionId: string) => {
  deleteSession(sessionId)
  return { success: true }
})
```

### 步骤 2.3：在 `electron/preload.ts` 暴露数据库 API

在 `electronAPI` 对象中追加：

```typescript
listSessions: () => ipcRenderer.invoke('list-sessions'),
loadSession: (sessionId: string) => ipcRenderer.invoke('load-session', sessionId),
deleteSession: (sessionId: string) => ipcRenderer.invoke('delete-session', sessionId),
```

### 步骤 2.4：更新 `src/types/electron.d.ts`

在 `electronAPI` 接口中追加：

```typescript
listSessions: () => Promise<Array<{ id: string; case_name: string; start_time: number; end_time?: number; record_count: number }>>
loadSession: (sessionId: string) => Promise<TrafficRecord[]>
deleteSession: (sessionId: string) => Promise<{ success: boolean }>
```


### 步骤 2.5：创建历史会话组件（新建 `src/components/SessionHistory.vue`）

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { TrafficRecord } from '../types/traffic'

interface SessionItem {
  id: string; case_name: string; start_time: number
  end_time: number | null; record_count: number
}

const emit = defineEmits<{ load: [records: TrafficRecord[]] }>()
const sessions = ref<SessionItem[]>([])
const loading = ref(false)

onMounted(async () => {
  sessions.value = await window.electronAPI?.listSessions() ?? []
})

async function loadSession(id: string) {
  loading.value = true
  const records = await window.electronAPI?.loadSession(id) ?? []
  emit('load', records)
  loading.value = false
}

async function removeSession(id: string) {
  if (!confirm('删除该历史会话？')) return
  await window.electronAPI?.deleteSession(id)
  sessions.value = sessions.value.filter(s => s.id !== id)
}

const formatDate = (ts: number) => new Date(ts).toLocaleString('zh-CN')
</script>

<template>
  <div class="session-history">
    <div class="section-title">历史会话</div>
    <div v-if="sessions.length === 0" class="empty">暂无历史记录</div>
    <div v-for="s in sessions" :key="s.id" class="session-item">
      <div class="session-info">
        <span class="case-name">{{ s.case_name }}</span>
        <span class="meta">{{ formatDate(s.start_time) }} · {{ s.record_count }} 条</span>
      </div>
      <div class="actions">
        <button @click="loadSession(s.id)" :disabled="loading">加载</button>
        <button class="danger" @click="removeSession(s.id)">删除</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.session-history { padding: 0 30px 20px; }
.section-title { font-size: 13px; font-weight: 600; color: #262626; margin-bottom: 10px; }
.empty { font-size: 13px; color: #8c8c8c; }
.session-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; border: 1px solid #f0f0f0; border-radius: 4px;
  margin-bottom: 6px; background: white;
}
.session-info { display: flex; flex-direction: column; gap: 2px; }
.case-name { font-size: 13px; font-weight: 500; }
.meta { font-size: 12px; color: #8c8c8c; }
.actions { display: flex; gap: 8px; }
.actions button {
  padding: 3px 10px; border: 1px solid #d9d9d9; border-radius: 3px;
  background: white; font-size: 12px; cursor: pointer;
}
.actions button:hover { border-color: #667eea; color: #667eea; }
.actions button.danger:hover { border-color: #ff4d4f; color: #ff4d4f; }
</style>
```

### 步骤 2.6：在 `src/App.vue` 中引入 SessionHistory

在 `<script setup>` import 区追加：

```typescript
import SessionHistory from './components/SessionHistory.vue'
```

在 `addMqRecord` 函数后追加：

```typescript
function handleLoadSession(records: TrafficRecord[]) {
  records.forEach(r => store.addRecord(r))
}
```

在 `</template>` 前的 `<MqInputModal>` 标签后追加：

```vue
<SessionHistory @load="handleLoadSession" />
```

---

## Phase 3：MQTT 实时监听

### 步骤 3.1：创建目录并建 MQTT 模块（新建 `electron/protocols/mqtt.ts`）

先创建目录 `electron/protocols/`，然后创建文件：

```typescript
import mqtt, { MqttClient } from 'mqtt'
import type { TrafficRecord } from '../../src/types/traffic'

interface MqttConnectionConfig {
  id: string; brokerUrl: string; topics: string[]
  username?: string; password?: string
}

const connections = new Map<string, MqttClient>()

export function connectMqtt(
  config: MqttConnectionConfig,
  onMessage: (record: TrafficRecord) => void,
  onStatus: (id: string, status: 'connected' | 'error' | 'closed', error?: string) => void
): void {
  const client = mqtt.connect(config.brokerUrl, {
    username: config.username,
    password: config.password,
    reconnectPeriod: 5000
  })

  client.on('connect', () => {
    onStatus(config.id, 'connected')
    config.topics.forEach(topic => client.subscribe(topic))
  })

  client.on('message', (topic, payload) => {
    onMessage({
      id: `mqtt-${Date.now()}-${Math.random().toString(36).substr(2, 6)}`,
      caseName: '', method: 'RECV',
      url: `${config.brokerUrl}/${topic}`,
      protocol: 'MQTT', statusCode: 200,
      duration: 0, timestamp: Date.now(),
      requestBody: payload.toString()
    })
  })

  client.on('error', (err) => onStatus(config.id, 'error', err.message))
  client.on('close', () => onStatus(config.id, 'closed'))
  connections.set(config.id, client)
}

export function disconnectMqtt(id: string): void {
  connections.get(id)?.end()
  connections.delete(id)
}

export function disconnectAllMqtt(): void {
  connections.forEach(c => c.end())
  connections.clear()
}
```

### 步骤 3.2：在 `electron/main.ts` 注册 MQTT IPC

在顶部 import 区追加：

```typescript
import { connectMqtt, disconnectMqtt, disconnectAllMqtt } from './protocols/mqtt.js'
```

在文件末尾追加：

```typescript
ipcMain.handle('connect-mqtt', async (_e, config) => {
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
  } catch (e: any) { return { success: false, error: e.message } }
})

ipcMain.handle('disconnect-mqtt', async (_e, id: string) => {
  disconnectMqtt(id)
  return { success: true }
})
```

在 `app.on('window-all-closed')` 回调中追加：

```typescript
disconnectAllMqtt()
```

### 步骤 3.3：在 `electron/preload.ts` 暴露 MQTT API

在 `electronAPI` 对象中追加：

```typescript
connectMqtt: (config: any) => ipcRenderer.invoke('connect-mqtt', config),
disconnectMqtt: (id: string) => ipcRenderer.invoke('disconnect-mqtt', id),
onMqttStatus: (cb: (data: any) => void) => {
  ipcRenderer.on('mqtt-status', (_e, data) => cb(data))
},
```

### 步骤 3.4：更新 `src/types/electron.d.ts`

在 `electronAPI` 接口中追加：

```typescript
connectMqtt: (config: { id: string; brokerUrl: string; topics: string[]; username?: string; password?: string }) => Promise<{ success: boolean; error?: string }>
disconnectMqtt: (id: string) => Promise<{ success: boolean }>
onMqttStatus: (callback: (data: { id: string; status: string; error?: string }) => void) => void
```

---

## Phase 4：SSL 证书管理

### 步骤 4.1：创建证书模块（新建 `electron/certificate.ts`）

```typescript
import forge from 'node-forge'
import fs from 'fs'
import path from 'path'
import { app, shell } from 'electron'
import { exec } from 'child_process'
import { promisify } from 'util'

const execAsync = promisify(exec)

export interface CertInfo {
  exists: boolean; certPath?: string; expiresAt?: string
}

const getCertDir = () => path.join(app.getPath('userData'), 'certs')

export function generateRootCert(): { certPath: string; keyPath: string } {
  const certDir = getCertDir()
  if (!fs.existsSync(certDir)) fs.mkdirSync(certDir, { recursive: true })

  const keys = forge.pki.rsa.generateKeyPair(2048)
  const cert = forge.pki.createCertificate()
  cert.publicKey = keys.publicKey
  cert.serialNumber = '01'
  cert.validity.notBefore = new Date()
  cert.validity.notAfter = new Date()
  cert.validity.notAfter.setFullYear(cert.validity.notBefore.getFullYear() + 10)

  const attrs = [
    { name: 'commonName', value: 'oAT Traffic Capture Root CA' },
    { name: 'organizationName', value: 'oAT Traffic Capture' }
  ]
  cert.setSubject(attrs)
  cert.setIssuer(attrs)
  cert.setExtensions([
    { name: 'basicConstraints', cA: true },
    { name: 'keyUsage', keyCertSign: true, digitalSignature: true, cRLSign: true }
  ])
  cert.sign(keys.privateKey, forge.md.sha256.create())

  const certPath = path.join(certDir, 'ca.pem')
  const keyPath = path.join(certDir, 'ca.key')
  fs.writeFileSync(certPath, forge.pki.certificateToPem(cert))
  fs.writeFileSync(keyPath, forge.pki.privateKeyToPem(keys.privateKey))
  return { certPath, keyPath }
}

export function getCertInfo(): CertInfo {
  const certPath = path.join(getCertDir(), 'ca.pem')
  if (!fs.existsSync(certPath)) return { exists: false }
  const cert = forge.pki.certificateFromPem(fs.readFileSync(certPath, 'utf-8'))
  return { exists: true, certPath, expiresAt: cert.validity.notAfter.toLocaleDateString('zh-CN') }
}

export async function installCertMacOS(certPath: string): Promise<{ success: boolean; error?: string }> {
  try {
    await execAsync(`security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain "${certPath}"`)
    return { success: true }
  } catch (e: any) { return { success: false, error: e.message } }
}

export const openCertFolder = (certPath: string) => shell.showItemInFolder(certPath)
```

### 步骤 4.2：在 `electron/main.ts` 注册证书 IPC

在顶部 import 区追加：

```typescript
import { generateRootCert, getCertInfo, installCertMacOS, openCertFolder } from './certificate.js'
```

在文件末尾追加：

```typescript
ipcMain.handle('get-cert-info', async () => getCertInfo())
ipcMain.handle('generate-cert', async () => {
  try { return { success: true, ...generateRootCert() } }
  catch (e: any) { return { success: false, error: e.message } }
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
```

### 步骤 4.3：在 `electron/preload.ts` 暴露证书 API

在 `electronAPI` 对象中追加：

```typescript
getCertInfo: () => ipcRenderer.invoke('get-cert-info'),
generateCert: () => ipcRenderer.invoke('generate-cert'),
installCert: () => ipcRenderer.invoke('install-cert'),
openCertFolder: () => ipcRenderer.invoke('open-cert-folder'),
```

### 步骤 4.4：更新 `src/types/electron.d.ts`

在 `electronAPI` 接口中追加：

```typescript
getCertInfo: () => Promise<{ exists: boolean; certPath?: string; expiresAt?: string }>
generateCert: () => Promise<{ success: boolean; certPath?: string; keyPath?: string; error?: string }>
installCert: () => Promise<{ success: boolean; error?: string }>
openCertFolder: () => Promise<void>
```

---

## Phase 5：追加类型定义（`src/types/traffic.ts`）

在文件末尾追加：

```typescript
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
```

---

## 构建与测试

```bash
# 开发模式（热重载）
npm run dev

# TypeScript 检查 + 生产构建
npm run build

# 构建后直接启动
npm run start

# 打包成 macOS .dmg
npm run dist
```

---

## 验证清单

- [ ] `npm install` 成功，无报错
- [ ] `npm run build` 构建通过，无 TypeScript 错误
- [ ] Phase 1：系统代理开关显示在 header，可以一键启用/禁用
- [ ] Phase 2：捕获结束后会话自动写入 SQLite，重启 App 可在历史会话中加载
- [ ] Phase 3：MQTT 连接功能（需要实际 broker 进行端到端测试）
- [ ] Phase 4：证书生成后文件存在，macOS 安装证书弹出授权对话框

---

## 故障排查

**依赖安装失败**

```bash
npm cache clean --force
rm -rf node_modules package-lock.json
npm install
```

**系统代理设置失败**
macOS 首次修改网络配置需要管理员授权，弹出对话框时点击"允许"即可。

**TypeScript 编译报错**
通常是类型定义遗漏，确保 `electron.d.ts` 中所有新增 IPC 方法都已声明。

---

**文档版本**：v1.0 | **平台**：macOS（优先），Windows/Linux 后续扩展
