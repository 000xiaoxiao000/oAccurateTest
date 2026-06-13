# oAT 流量采集器

`oAT-traffic-capture` 是一个基于 Electron + Vue 3 的桌面流量采集工具，用于测试过程中捕获、查看、过滤、重放和导出接口流量。

## 功能

- HTTP/HTTPS 流量捕获：通过本地代理记录请求、响应、状态码、耗时、请求头和响应头。
- WebSocket 流量捕获：记录 WS/WSS 连接及 send/receive 消息。
- MQTT 接入与 MQ 手动录入：支持 MQTT 连接采集，也支持手动补录 AMQP、MQTT、Kafka 等消息。
- 流量重放：支持单条和批量重放 HTTP/HTTPS 记录；WebSocket 可按已捕获的发送消息重放。
- 自定义过滤规则：支持按 URL、方法、协议、状态码、Header、Body 做 include、exclude、mark。
- 统计图表：展示协议分布、状态分布、Top Host、分钟趋势和基础统计。
- 历史会话：使用 SQLite 保存采集会话和记录，可加载或删除历史会话。
- 数据导出：支持 Excel、CSV、JSON。
- 插件扩展：支持本地插件在捕获后、保存前处理流量记录。
- 悬浮窗口：采集过程中可切换为小窗，减少桌面占用。

## 技术栈

- Electron 30
- Vue 3 + TypeScript
- Pinia
- Vite
- http-mitm-proxy
- better-sqlite3
- ws
- mqtt
- ExcelJS

## 安装

```bash
cd oAT-traffic-capture
npm install
```

如果当前 npm 源不可用，可以切换到官方源：

```bash
npm config set registry https://registry.npmjs.org/
npm install
```

查看当前 npm 源：

```bash
npm config get registry
```

## 开发运行

```bash
npm run dev
```

也可以分开启动：

```bash
npm run dev:vite
npm run dev:electron
```

## 构建

```bash
npm run build
```

打包目录构建：

```bash
npm run pack
```

生成安装包：

```bash
npm run dist
```

## 使用方式

### 启动捕获

1. 填写“用例名称 / 流量描述”。
2. 点击“开始捕获”。
3. 将系统或浏览器 HTTP/HTTPS 代理设置为 `127.0.0.1:8888`。
4. 通过被测系统发起请求，流量会实时显示在列表中。

应用也提供“系统代理”开关；启用后，系统 HTTP/HTTPS 流量会经过本地代理。

### HTTPS 和 WSS

HTTPS/WSS 捕获依赖 MITM 证书。首次使用需要生成并信任证书：

1. 在顶部“HTTPS 证书”区域点击“生成证书”。
2. 点击“安装信任”。macOS 会要求输入管理员密码，用于把证书加入系统钥匙串。
3. 如果自动安装失败，点击“打开目录”，手动导入 `ca.pem` 到钥匙串，并设置为“始终信任”。
4. 重启浏览器或被测客户端。

未信任证书时，HTTP/WS 可以正常捕获，HTTPS/WSS 可能无法解密。

### 重放

- 点击列表单条记录的“重放”，可重放该记录。
- 勾选多条记录后点击“重放选中”，可批量重放。
- HTTP/HTTPS 会按原始 method、url、headers、body 发起请求。
- WebSocket 会重新连接并重发已捕获的发送方向消息。
- 不支持自动重放的协议会生成失败记录，并展示原因。

### 过滤规则

点击“过滤规则”打开规则面板。

规则字段：

- `target`：`url`、`method`、`protocol`、`statusCode`、`header`、`body`
- `operator`：`contains`、`equals`、`regex`、`startsWith`、`endsWith`
- `action`：`include`、`exclude`、`mark`

行为说明：

- `exclude`：匹配后丢弃该记录。
- `include`：存在启用的 include 规则时，只保留匹配记录。
- `mark`：匹配后给记录加标签。

规则会保存到本地 SQLite 数据库。

### 统计图表

点击“统计图表”查看：

- 协议分布
- 状态分布
- Top Host
- 分钟趋势
- 总请求数、成功数、失败数、平均耗时

### 历史会话

每次开始捕获会创建一个会话。历史会话列表支持：

- 加载历史记录
- 删除历史会话
- 查看会话名称、时间和记录数

### 导出

支持三种格式：

- Excel：完整字段，适合人工查看。
- CSV：基础字段，适合表格工具处理。
- JSON：完整结构，适合程序处理或归档。

## 插件扩展

插件是 oAT 应用内部的流量处理插件，不是 Chrome、Safari 或其他浏览器插件。插件只处理本工具捕获到的流量记录。

插件目录位于应用数据目录下的 `plugins` 文件夹。可在“插件扩展”面板中查看实际路径，也可以在界面中打开目录。

点击“安装内置插件”会安装一个真实可用的流量清洗插件：

- 过滤 OPTIONS 预检请求。
- 过滤图片、CSS、JS、字体、source map 等静态资源。
- 给 `/api/` 请求打 `API` 标签。
- 给 4xx/5xx 响应打 `错误` 标签。
- 给耗时超过 1000ms 的请求打 `慢请求` 标签。

点击“卸载内置插件”会删除 `traffic-cleanup-plugin` 目录并重新加载插件列表。

每个插件一个子目录：

```text
plugins/
  traffic-cleanup-plugin/
    package.json
    plugin.json
    index.js
```

`plugin.json`：

```json
{
  "id": "traffic-cleanup-plugin",
  "name": "流量清洗插件",
  "version": "1.0.0",
  "main": "index.js",
  "enabled": true,
  "description": "过滤静态资源和 OPTIONS 预检请求，并标记 API、错误、慢请求"
}
```

插件入口可导出 hook：

```js
export function onRecordCaptured(record) {
  const tags = new Set(record.tags || [])
  if (/\/api(\/|$)/i.test(record.url || '')) {
    tags.add('API')
  }
  return { ...record, tags: Array.from(tags) }
}

export function beforeSave(record) {
  if ((record.method || '').toUpperCase() === 'OPTIONS') {
    return null
  }
  return record
}
```

支持的 hook：

- `onRecordCaptured(record)`：捕获到记录后执行。
- `beforeSave(record)`：写入数据库前执行。

hook 返回修改后的记录会继续处理；返回 `null` 会丢弃记录。

## 项目结构

```text
oAT-traffic-capture/
├── electron/
│   ├── main.ts                 # Electron 主进程、IPC、窗口管理
│   ├── preload.ts              # 渲染进程安全桥接
│   ├── preload.cjs             # Electron 实际加载的 preload
│   ├── proxy.ts                # HTTP/HTTPS/WS/WSS 代理捕获
│   ├── replay.ts               # 流量重放
│   ├── filterRules.ts          # 捕获规则匹配
│   ├── database.ts             # SQLite 会话、记录、规则存储
│   ├── certificate.ts          # 证书生成与安装辅助
│   ├── systemProxy.ts          # 系统代理开关
│   ├── plugins/
│   │   └── pluginManager.ts    # 插件加载和 hook 调用
│   └── protocols/
│       └── mqtt.ts             # MQTT 连接采集
├── src/
│   ├── components/
│   │   ├── TrafficTable.vue
│   │   ├── DetailModal.vue
│   │   ├── FilterRulesPanel.vue
│   │   ├── TrafficStatsPanel.vue
│   │   ├── PluginPanel.vue
│   │   ├── ProxyControl.vue
│   │   ├── SessionHistory.vue
│   │   └── MqInputModal.vue
│   ├── stores/
│   │   └── traffic.ts
│   ├── types/
│   │   ├── traffic.ts
│   │   └── electron.d.ts
│   ├── App.vue
│   └── main.ts
├── package.json
├── vite.config.ts
├── tsconfig.json
└── tsconfig.electron.json
```

## 常见问题

### 无法捕获 HTTP/HTTPS 流量

检查：

1. 是否已经点击“开始捕获”。
2. 系统或浏览器代理是否指向 `127.0.0.1:8888`。
3. HTTPS 证书是否已安装并信任。
4. 被测客户端是否绕过了系统代理。

### 重放失败：Failed to parse URL

旧记录可能只保存了相对路径。当前版本会尝试使用 `Host` 请求头补全 URL；如果记录缺少 Host，则无法重放，需要重新捕获完整记录。

### WSS 捕获失败

WSS 依赖 HTTPS 证书信任。确认证书已安装，并重启浏览器或被测客户端。

### 端口 8888 被占用

当前代理端口在 `electron/main.ts` 的 `PROXY_PORT` 中定义，可修改后重新构建。

### 大量流量导致界面变慢

建议按用例分批采集，定期清空当前列表或加载历史会话查看。
