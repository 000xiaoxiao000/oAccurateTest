# oAT 流量采集器增强计划

## 目标
将当前的 Electron 应用打造成真正独立的桌面 APP，支持系统级代理配置和多协议流量捕获。

## 核心增强功能

### 1. 系统代理集成 (macOS 支持)

#### 功能描述
- 一键启用/禁用系统代理
- 自动配置系统 HTTP/HTTPS 代理到本地端口
- 支持代理白名单/黑名单
- 支持 PAC (Proxy Auto-Config) 文件生成

#### 技术实现
```javascript
// electron/systemProxy.ts
import { exec } from 'child_process'
import { promisify } from 'util'

const execAsync = promisify(exec)

export async function enableSystemProxy(port: number) {
  // macOS
  await execAsync(`networksetup -setwebproxy "Wi-Fi" 127.0.0.1 ${port}`)
  await execAsync(`networksetup -setsecurewebproxy "Wi-Fi" 127.0.0.1 ${port}`)
  await execAsync(`networksetup -setwebproxystate "Wi-Fi" on`)
  await execAsync(`networksetup -setsecurewebproxystate "Wi-Fi" on`)
}

export async function disableSystemProxy() {
  // macOS
  await execAsync(`networksetup -setwebproxystate "Wi-Fi" off`)
  await execAsync(`networksetup -setsecurewebproxystate "Wi-Fi" off`)
}
```

### 2. 多协议支持

#### 2.1 WebSocket 捕获
- 捕获 WS/WSS 连接
- 记录握手和消息帧
- 支持二进制消息

#### 2.2 gRPC 捕获
- 解析 gRPC 协议
- 显示 protobuf 消息
- 支持流式 RPC

#### 2.3 MQTT/MQ 增强
- 除手动录入外，支持实时监听
- 集成 MQTT broker 捕获
- RabbitMQ/Kafka 消息捕获

#### 2.4 TCP/UDP 原始流量
- 支持指定端口的 TCP 流量捕获
- UDP 数据包记录

### 3. SSL/TLS 证书管理

#### 功能描述
- 自动生成根证书
- 一键安装证书到系统信任区
- 证书过期提醒
- 支持自定义证书

#### 技术实现
```javascript
// electron/certificate.ts
import forge from 'node-forge'
import fs from 'fs'
import path from 'path'

export function generateRootCertificate() {
  const keys = forge.pki.rsa.generateKeyPair(2048)
  const cert = forge.pki.createCertificate()
  
  cert.publicKey = keys.publicKey
  cert.serialNumber = '01'
  cert.validity.notBefore = new Date()
  cert.validity.notAfter = new Date()
  cert.validity.notAfter.setFullYear(cert.validity.notBefore.getFullYear() + 10)
  
  const attrs = [{
    name: 'commonName',
    value: 'oAT Traffic Capture Root CA'
  }, {
    name: 'organizationName',
    value: 'oAT Traffic Capture'
  }]
  
  cert.setSubject(attrs)
  cert.setIssuer(attrs)
  cert.sign(keys.privateKey, forge.md.sha256.create())
  
  return {
    cert: forge.pki.certificateToPem(cert),
    key: forge.pki.privateKeyToPem(keys.privateKey)
  }
}
```

### 4. 高级过滤和分析

#### 4.1 智能过滤
- 按域名、路径、状态码过滤
- 正则表达式匹配
- 响应时间范围筛选
- 请求/响应体内容搜索

#### 4.2 流量分析
- 请求时序图
- 域名统计图表
- 性能瓶颈分析
- API 调用链路追踪

#### 4.3 数据对比
- 对比两次捕获的差异
- 请求/响应 Diff 视图
- 用例版本管理

### 5. 流量重放和Mock

#### 功能描述
- 重放已捕获的请求
- 编辑后重放
- 批量重放
- Mock 服务器模式（返回已捕获的响应）

#### 技术实现
```javascript
// electron/replay.ts
export async function replayRequest(record: TrafficRecord) {
  const options = {
    method: record.method,
    headers: record.requestHeaders,
    body: record.requestBody
  }
  
  const response = await fetch(record.url, options)
  return {
    statusCode: response.status,
    headers: response.headers,
    body: await response.text()
  }
}
```

### 6. 数据持久化

#### 功能描述
- SQLite 本地数据库存储
- 会话历史管理
- 快速查询和检索
- 数据导入/导出

#### 技术实现
```javascript
// electron/database.ts
import Database from 'better-sqlite3'

export class TrafficDatabase {
  private db: Database.Database
  
  constructor(dbPath: string) {
    this.db = new Database(dbPath)
    this.initTables()
  }
  
  initTables() {
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS sessions (
        id TEXT PRIMARY KEY,
        case_name TEXT,
        start_time INTEGER,
        end_time INTEGER
      );
      
      CREATE TABLE IF NOT EXISTS records (
        id TEXT PRIMARY KEY,
        session_id TEXT,
        method TEXT,
        url TEXT,
        protocol TEXT,
        status_code INTEGER,
        duration INTEGER,
        timestamp INTEGER,
        request_headers TEXT,
        request_body TEXT,
        response_headers TEXT,
        response_body TEXT,
        FOREIGN KEY(session_id) REFERENCES sessions(id)
      );
      
      CREATE INDEX idx_url ON records(url);
      CREATE INDEX idx_timestamp ON records(timestamp);
    `)
  }
}
```

### 7. 插件系统

#### 功能描述
- 自定义协议解析器
- 自定义导出格式
- 请求/响应转换器
- UI 扩展

### 8. 性能优化

#### 优化点
- 大流量场景的虚拟滚动
- 响应体流式处理
- 请求去重
- 内存使用监控和自动清理

## UI/UX 改进

### 1. 深色模式
- 支持系统主题自动切换
- 手动主题选择

### 2. 快捷键支持
- Cmd+F: 搜索
- Cmd+E: 导出
- Cmd+R: 开始/停止捕获
- Cmd+K: 清空记录

### 3. 状态指示
- 实时流量速率显示
- CPU/内存使用监控
- 代理连接数统计

### 4. 布局优化
- 可调整的分栏布局
- 详情面板浮动/固定
- 列宽自定义

## 安全性

### 1. 权限控制
- 明确的权限申请流程
- 证书安装需要用户确认
- 系统代理修改提示

### 2. 数据安全
- 敏感信息脱敏显示
- 本地数据加密存储
- 导出文件密码保护选项

## 跨平台支持

### macOS (优先)
- 系统代理 API
- Keychain 证书管理
- 原生菜单栏图标

### Windows (后续)
- 注册表代理配置
- 证书存储集成

### Linux (后续)
- gsettings/NetworkManager 集成

## 实施路线图

### Phase 1: 核心功能完善 (1-2周)
- [x] 基础 HTTP/HTTPS 代理
- [x] 前端界面框架
- [ ] 系统代理集成 (macOS)
- [ ] SSL 证书生成和安装
- [ ] SQLite 数据持久化

### Phase 2: 协议扩展 (1-2周)
- [ ] WebSocket 支持
- [ ] gRPC 支持
- [ ] MQTT 实时监听
- [ ] TCP 流量捕获

### Phase 3: 高级功能 (2-3周)
- [ ] 流量重放
- [ ] Mock 服务器
- [ ] 高级过滤和搜索
- [ ] 数据分析和可视化

### Phase 4: 优化和发布 (1-2周)
- [ ] 性能优化
- [ ] UI/UX 完善
- [ ] 打包和签名
- [ ] 文档和教程

## 依赖包更新

需要添加的依赖：
```json
{
  "dependencies": {
    "better-sqlite3": "^11.0.0",
    "node-forge": "^1.3.1",
    "ws": "^8.18.0",
    "@grpc/grpc-js": "^1.12.0",
    "mqtt": "^5.10.0"
  }
}
```

## 配置文件结构

```json
{
  "proxy": {
    "port": 8888,
    "enableSystemProxy": false,
    "bypassList": ["localhost", "127.0.0.1"]
  },
  "capture": {
    "maxRecords": 10000,
    "autoSave": true,
    "captureBody": true,
    "maxBodySize": "10MB"
  },
  "certificate": {
    "autoGenerate": true,
    "certPath": "~/.traffic-capture/cert.pem",
    "keyPath": "~/.traffic-capture/key.pem"
  },
  "ui": {
    "theme": "auto",
    "language": "zh-CN"
  }
}
```

## 技术栈总结

- **前端**: Vue 3 + TypeScript + Pinia
- **后端**: Electron + Node.js
- **代理**: http-mitm-proxy / custom proxy server
- **数据库**: better-sqlite3
- **证书**: node-forge
- **协议**: ws, @grpc/grpc-js, mqtt
- **构建**: Vite + electron-builder
