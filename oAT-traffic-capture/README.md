# oAT 流量采集器 (oAT Traffic Capture)

一个基于 Electron + Vue3 的桌面应用，用于捕获和管理 HTTP/HTTPS、MQ 等协议的网络流量。

## 功能特性

- ✅ **HTTP/HTTPS 流量捕获**：通过内置代理服务器自动拦截 HTTP/HTTPS 请求
- ✅ **用例管理**：为不同的测试场景添加用例名称标签
- ✅ **实时展示**：显示请求方法、URL、状态码、耗时等关键信息
- ✅ **详情查看**：查看完整的请求头、请求体、响应头、响应体
- ✅ **MQ 手动录入**：支持手动录入 AMQP、MQTT、Kafka 等 MQ 消息
- ✅ **数据导出**：导出为 Excel、CSV、JSON 格式
- ✅ **搜索过滤**：实时搜索和过滤流量记录

## 技术栈

- **前端框架**: Vue 3 + TypeScript
- **桌面框架**: Electron
- **状态管理**: Pinia
- **构建工具**: Vite
- **代理服务器**: http-mitm-proxy
- **Excel 导出**: ExcelJS

## 安装依赖

**注意**：由于当前系统使用的 npm 镜像源 (`registry.npmmirror.com`) 返回 403 错误，你需要先切换到官方源或其他可用镜像源。

### 方法 1: 使用官方 npm 源（推荐）

```bash
# 切换到官方源
npm config set registry https://registry.npmjs.org/

# 安装依赖
npm install
```

### 方法 2: 使用淘宝新镜像源

```bash
# 切换到淘宝新源
npm config set registry https://registry.npmmirror.com/

# 如果还是失败，尝试旧源
npm config set registry https://registry.npm.taobao.org/

# 安装依赖
npm install
```

### 方法 3: 临时指定源

```bash
# 临时使用官方源安装
npm install --registry=https://registry.npmjs.org/
```

### 检查当前源

```bash
npm config get registry
```

## 开发运行

```bash
# 启动开发服务器
npm run dev

# 或分别启动
npm run dev:vite    # 启动 Vite 开发服务器
npm run dev:electron # 启动 Electron
```

## 使用说明

### 1. 启动流量捕获

1. 在"用例名称"输入框中填写测试场景名称（如"用户登录流程"）
2. 点击"开始捕获"按钮
3. 应用会启动本地代理服务器（默认端口 8888）

### 2. 配置系统代理

启动捕获后，需要将系统或浏览器的 HTTP 代理设置为：

```
代理地址: 127.0.0.1
端口: 8888
```

#### macOS 系统代理设置

1. 打开"系统设置" > "网络"
2. 选择当前网络 > "详细信息" > "代理"
3. 勾选"网页代理(HTTP)" 和 "安全网页代理(HTTPS)"
4. 服务器填写 `127.0.0.1`，端口填写 `8888`

#### Chrome 浏览器代理设置

可使用 SwitchyOmega 等代理管理扩展，或使用命令行启动：

```bash
# macOS
open -a "Google Chrome" --args --proxy-server="127.0.0.1:8888"

# Windows
chrome.exe --proxy-server="127.0.0.1:8888"
```

### 3. 手动录入 MQ 流量

对于非 HTTP 协议（如 AMQP、MQTT、Kafka），点击"手动录入 MQ"按钮，填写：
- 协议类型（AMQP、MQTT、Kafka 等）
- 操作方法（SEND、RECEIVE 等）
- Topic/Queue 地址
- 消息内容

### 4. 导出数据

点击工具栏的导出按钮，选择格式：
- **Excel (.xlsx)**: 包含所有字段的完整数据，带格式化表头
- **CSV (.csv)**: 基础字段的 CSV 文件，方便在 Excel 中打开
- **JSON (.json)**: 完整的 JSON 格式，包含所有请求/响应数据

## 项目结构

```
oAT-traffic-capture/
├── electron/               # Electron 主进程
│   ├── main.ts            # 主进程入口（窗口管理、IPC）
│   ├── preload.ts         # 预加载脚本（安全桥接）
│   └── proxy.ts           # HTTP 代理服务器
├── src/                   # Vue 渲染进程
│   ├── components/        # Vue 组件
│   │   ├── TrafficTable.vue      # 流量列表表格
│   │   ├── DetailModal.vue       # 详情弹窗
│   │   └── MqInputModal.vue      # MQ 录入弹窗
│   ├── stores/           # Pinia 状态管理
│   │   └── traffic.ts    # 流量数据 store
│   ├── types/            # TypeScript 类型定义
│   │   ├── traffic.ts    # 流量记录类型
│   │   └── electron.d.ts # Electron API 类型
│   ├── App.vue           # 主应用组件
│   ├── main.ts           # Vue 入口
│   └── style.css         # 全局样式
├── public/               # 静态资源
├── dist/                 # Vite 构建输出
├── dist-electron/        # Electron 编译输出
├── package.json          # 项目配置
├── vite.config.ts        # Vite 配置
├── tsconfig.json         # TypeScript 配置（渲染进程）
└── tsconfig.electron.json # TypeScript 配置（主进程）
```

## 构建打包

```bash
# 构建应用
npm run build

# 打包为可执行文件
npm run start
```

使用 electron-builder 打包：

```bash
# macOS
npm run build && npx electron-builder --mac

# Windows
npm run build && npx electron-builder --win

# Linux
npm run build && npx electron-builder --linux
```

## 注意事项

### HTTPS 流量捕获

捕获 HTTPS 流量需要安装和信任代理证书：

1. 首次运行时，代理会在 `~/.http-mitm-proxy/` 目录生成证书
2. 将 `ca.pem` 证书添加到系统信任列表
3. macOS: 打开"钥匙串访问" > 导入证书 > 设置为"始终信任"

### 端口冲突

如果 8888 端口被占用，可以修改 `electron/main.ts` 中的端口号：

```typescript
await proxyServer.listen(8888)  // 改为其他端口
```

### 性能考虑

- 大量流量会占用内存，建议定期清空记录
- 响应体过大时会影响导出性能，可考虑截断

## 常见问题

### Q: 为什么无法捕获流量？

A: 请确认：
1. 已点击"开始捕获"
2. 系统/浏览器代理已正确配置为 `127.0.0.1:8888`
3. 访问的是 HTTP 站点，或已安装并信任 HTTPS 证书

### Q: 如何捕获移动设备流量？

A: 
1. 确保移动设备和电脑在同一局域网
2. 在移动设备的 Wi-Fi 设置中，配置 HTTP 代理为电脑的局域网 IP（如 `192.168.1.100:8888`）
3. 安装并信任证书（HTTPS）

### Q: 导出的 Excel 文件无法打开？

A: 确保使用最新版 Microsoft Excel 或 WPS，或使用 LibreOffice 打开 .xlsx 文件。

## 开发计划

- [ ] 支持 WebSocket 流量捕获
- [ ] 添加流量重放功能
- [ ] 支持自定义过滤规则
- [ ] 添加流量统计图表
- [ ] 支持插件扩展

## License

MIT

## 贡献

欢迎提交 Issue 和 Pull Request！

---

开发完成时间：2026-06-12
