# oAccurateTest (oAT)

oAccurateTest 是一个面向 Java 应用的智能测试分析平台。通过 JavaAgent 无侵入地采集运行时链路数据，结合静态代码分析与 AI 能力，为测试和研发团队提供覆盖率分析、链路追踪、版本对比和智能诊断。

---

## 整体架构

```
oAccurateTest/
├── oAT-agent/            # 探针模块（默认 Java 7 字节码，支持 JDK6/7/8 profile）
│   ├── oAT-client-model/ # Agent 与服务端共用的数据模型
│   ├── oAT-agent-core/   # 字节码增强、链路采集、数据上报
│   └── oAT-agnet-shaded/ # 依赖 shaded 打包
├── oAT-service/          # 服务端模块（Java 17）
│   ├── oAT-ai/           # AI 分析模块（LangChain4j）
│   └── oAT-service-web/  # Web 平台主服务（Spring Boot 3）
├── oAT-web-frontend/     # 前端界面（Vue 3 + TypeScript）
├── oAT-traffic-capture/  # 桌面流量采集器与覆盖率上送 SDK（Electron + Vue 3）
└── README.md
```

### 数据流

```
目标 Java 应用
  └─ oAT-agent（JavaAgent）
       │ HTTP 上报链路 / 快照数据
       ▼
  oAT-service-web（Spring Boot）
       ├─ Elasticsearch  ← 快照、链路、静态源码
       ├─ MySQL          ← 项目、应用、版本、用例等结构化数据
       ├─ Redis          ← 会话、缓存
       ├─ MinIO          ← 覆盖率 codeNodes 对象存储
       ├─ Git 仓库       ← Commit、Diff、源码下载
       └─ oAT-ai        ← AI 工具编排与 LLM 集成
            │
            ▼
  oAT-web-frontend（Vue 3）
       ▲
       │ 前端 / 多语言覆盖率上送
  oAT-traffic-capture / sdk
```

---

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 探针 | Java、ASM 字节码增强 | 默认 Java 7 字节码，支持 JDK6/7/8 profile |
| 服务端框架 | Spring Boot | 3.3.6 |
| 服务端语言 | Java | 17 |
| AI 框架 | LangChain4j | 1.12.2 |
| 存储 - 文档 | Elasticsearch | 7.x / 8.x |
| 存储 - 关系型 | MySQL | 5.7+ / 8.x |
| 存储 - 对象 | MinIO | — |
| 存储 - 缓存 | Redis + Redisson | — |
| 代码分析 | JGit、ASM、JavaParser | — |
| 报表导出 | EasyExcel | 3.1.1 |
| 前端框架 | Vue 3 + TypeScript | Vue 3.5 |
| 前端构建 | Vite | 7.x |
| 前端状态 | Pinia | 3.x |
| 桌面采集器 | Electron、Vue 3、Vite | Electron 30 / Vite 5.x |

---

## 核心功能

**运行时采集**
Agent 通过字节码增强拦截 HTTP、SQL、Redis、Dubbo、SOFA-RPC、Feign、RocketMQ、Kafka、RabbitMQ 等协议，将链路节点和代码执行栈实时上报至平台。

**系统快照**
每次测试场景执行后，平台将该次请求的完整调用链路沉淀为系统快照，支持场景目录管理、版本归档、图谱可视化。

**覆盖率分析**
基于快照和静态源码结构生成全量或增量覆盖率报告，支持类 / 方法 / 行 / 分支四个维度，提供源码着色视图和 Excel 导出；同时支持前端 Istanbul 覆盖率和 Go / Python / C/C++ 原生覆盖率上报。

**版本管理**
接入 Git 仓库，管理应用的分支、Commit 与版本号，支持跨版本 Diff 和代码变更影响分析。

**用例中心**
管理测试用例目录与详情，支持与快照关联、导入导出，关联缺陷/PRD 链接。

**API 端点分析**
自动识别应用暴露的 HTTP 接口，结合链路数据分析接口覆盖情况。

**探针监控**
实时监控在线 Agent 实例状态，支持探针下线告警和 Webhook 通知。

**桌面流量采集**
`oAT-traffic-capture` 提供 HTTP/HTTPS、WebSocket、MQTT 流量捕获、过滤、重放、导出和历史会话管理，并内置覆盖率上送中继能力，方便前端和多语言测试产物接入平台。

**AI 智能分析**
基于 LangChain4j 的对话式 AI 助手，内置覆盖率分析、缺陷检测、性能分析、调用链比较、测试推荐等专用工具，支持 OpenAI / Ollama / DeepSeek 等多种模型。

---

## 模块说明

每个模块的详细构建、配置和使用说明见各自的 README：

- `oAT-agent/` → [oAT-agent README](oAT-agent/README.md)
- `oAT-service/oAT-ai/` → [oAT-ai README](oAT-service/oAT-ai/README.md)
- `oAT-service/oAT-service-web/` → [oAT-service-web README](oAT-service/oAT-service-web/README.md)
- `oAT-web-frontend/` → [oAT-web-frontend README](oAT-web-frontend/README.md)
- `oAT-traffic-capture/` → [oAT-traffic-capture README](oAT-traffic-capture/README.md)
- `oAT-traffic-capture/sdk/coverage/` → [多语言覆盖率上送 SDK README](oAT-traffic-capture/sdk/coverage/README.md)

---

## 构建顺序

模块间存在依赖，必须按以下顺序构建：

```bash
# 1. 构建 Agent（含 oAT-client-model，安装到本地 Maven 仓库）
cd oAT-agent
mvn clean install

# 2. 构建 AI 模块
cd ../oAT-service/oAT-ai
mvn clean install

# 3. 构建 Web 服务
cd ../oAT-service-web
mvn clean package

# 4. 构建前端（可选，生产部署时需要）
cd ../../oAT-web-frontend
npm install
npm run build

# 5. 构建桌面流量采集器（可选）
cd ../oAT-traffic-capture
npm install
npm run build
```

---

## 启动顺序

1. 启动 Elasticsearch
2. 启动 MySQL
3. 启动 Redis
4. 启动 MinIO（启用覆盖率对象存储时需要）
5. 启动 `oAT-service-web`
6. 启动挂载了 Agent 的目标应用
7. 按需启动 `oAT-traffic-capture` 进行桌面流量采集或覆盖率中继

详细配置和启动参数见各模块 README。

---

## 覆盖率数据存储

本次优化引入了基于 MinIO 的覆盖率 codeNodes 对象存储，将链路上报时的代码执行栈数据从 Elasticsearch 中剥离，写入 MinIO 以降低 ES 存储压力。

**工作机制**

- 写入（异步）：Agent 上报 TraceNode 时，服务端提取 `codeNodes` → 序列化为 MessagePack → 异步写入 MinIO。内置有界队列（最多积压 500 个任务），队列满时自动降级为同步写，不丢失数据。
- 读取（同步）：生成覆盖率报告时按 `traceId` 从 MinIO 加载 codeNodes → 反序列化 → 用于覆盖率合并。
- 降级（Fallback）：MinIO 不可用或对象不存在时，自动回退到旧的 ES 链路数据兜底，保证已有数据可用。

**配置项**（`application.properties`）

```properties
# 是否启用 MinIO 对象存储（默认 true，设为 false 则完全使用旧 ES 模式）
coverage.storage.enabled=true
coverage.storage.type=minIO
coverage.storage.endpoint=http://localhost:9000
coverage.storage.bucket=oat-coverage
coverage.storage.access-key=<your-access-key>
coverage.storage.secret-key=<your-secret-key>
```

MinIO 的 Bucket 会在服务启动时自动创建，无需手动初始化。如不需要 MinIO，将 `coverage.storage.enabled` 设为 `false` 即可保持原有行为。

## 前端与多语言覆盖率上送

服务端提供两类非 Java 覆盖率入口：

```text
POST /api/projects/{projectId}/apps/{appId}/coverage/frontend/report
POST /api/projects/{projectId}/apps/{appId}/coverage/universal/{CPP|GO|PYTHON}/report
POST /api/projects/{projectId}/apps/{appId}/coverage/frontend/generate
POST /api/projects/{projectId}/apps/{appId}/coverage/universal/{CPP|GO|PYTHON}/generate
```

`oAT-traffic-capture` 可作为本地覆盖率中继，默认接收 `http://localhost:8889/oat/coverage/report`，再转发到平台；多语言上送脚本见 `oAT-traffic-capture/sdk/coverage/`。

---

## 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK（Agent） | 8+ | — |
| JDK（Service） | 17+ | — |
| Maven | 3.8+ | — |
| Node.js | 18+ | 前端开发/构建 |
| Elasticsearch | 7.x / 8.x | — |
| MySQL | 5.7+ / 8.x | — |
| Redis | 5.x+ | — |
| MinIO | RELEASE.2023+ | 用于覆盖率 codeNodes 对象存储 |
| Electron 构建链 | Node.js 18+ | 桌面采集器开发/构建 |
