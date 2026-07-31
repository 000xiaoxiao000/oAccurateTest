# oAccurateTest (oAT)

oAccurateTest 是面向 Java 应用和多语言系统的精准测试平台。平台通过 JavaAgent、静态源码分析、版本 Diff、覆盖率报告、流量采集和 AI 工具编排，帮助团队回答三个问题：

- 本次变更影响了哪些接口、类、方法和调用链？
- 当前测试覆盖了哪些代码，遗漏风险在哪里？
- 快照、用例、缺陷、PRD、覆盖率和链路数据如何沉淀为可复用测试资产？

> 使用声明：本项目仅供个人学习、技术研究与交流使用，不得用于商业用途或未经授权的生产环境部署。使用者需自行遵守相关法律法规和第三方组件许可协议。

## 交流与反馈

| 添加作者微信 | 微信交流入口 | AI + 精准测试实战交流群 |
|---|---|---|
| 扫码添加好友，备注 `oAT` / `精准测试`。 | 交流项目使用、部署问题与二次开发思路。 | 讨论 AI 测试分析、覆盖率治理和工程落地。 |
| <img src="docs/assets/wechat-friend.png" alt="添加作者微信二维码" width="220"> | <img src="docs/assets/wechat-contact.jpg" alt="微信交流二维码" width="220"> | <img src="docs/assets/ai-testing-group.png" alt="AI + 精准测试实战交流群二维码" width="220"> |

## 模块总览

```text
oAccurateTest/
├── oAT-agent/                 # JavaAgent 探针
│   ├── oAT-client-model/      # Agent 与服务端共用模型
│   ├── oAT-agnet-shaded/      # Agent shaded 依赖包
│   └── oAT-agent-core/        # 启动入口、字节码增强、链路采集、覆盖率采集、HTTP 上报
├── oAT-service/               # 服务端 Maven 聚合模块
│   ├── oAT-ai/                # LangChain4j AI 智能分析模块
│   └── oAT-service-web/       # 后端主服务，API、快照、覆盖率、版本、用例、AI 对话入口
├── oAT-relay/                 # 轻量 HTTP 中继，支持同步、异步和混合转发
├── oAT-web-frontend/          # Vue 3 Web 操作界面
├── oAT-traffic-capture/       # Electron 桌面流量采集器与覆盖率本地中继
├── scripts/                   # 辅助脚本
└── docs/                      # 文档图片资源
```

## 核心能力

| 能力 | 说明 |
|---|---|
| 运行时链路采集 | JavaAgent 采集 HTTP、JDBC、Redis、Redisson、Feign、Dubbo、SOFA-RPC、RabbitMQ、RocketMQ、Kafka、业务方法、系统日志等链路节点。 |
| 系统快照 | 将测试场景中的调用链、代码执行栈和上下文信息沉淀为快照，支持个人快照、系统快照、分享和图谱查看。 |
| 覆盖率报告 | 支持 Java Agent 快照覆盖率、前端 Istanbul 覆盖率，以及 Go、Python、C/C++ 通用覆盖率上送与报告生成。 |
| 版本中心 | 管理应用版本、分支、Commit、基线和 Diff，支撑增量覆盖率与变更影响分析。 |
| 用例中心 | 管理用例目录、用例详情、快照关联、缺陷链接、PRD 链接、分享和导入导出。 |
| API 端点分析 | 识别接口端点，结合链路与覆盖率数据分析接口测试覆盖情况。 |
| 探针监控 | 查看在线 Agent、心跳、会话、探针告警和实时链路。 |
| 代码关系图谱 | 基于静态源码与运行时数据展示应用、接口、类和方法关系。 |
| 桌面流量采集 | 通过 Electron 采集 HTTP/HTTPS、WebSocket、MQTT 等流量，支持过滤、查看、重放、导出和历史会话。 |
| HTTP 中继 | 在网络隔离场景中转发 Agent、SDK、桌面端和前端请求，支持混合转发、异步队列、限流和重试。 |
| AI 智能分析 | 基于 LangChain4j 和工具调用实现覆盖率分析、缺陷检测、性能分析、链路对比、测试推荐和交互式问答。 |

## 数据流

```text
目标 Java 应用
  └─ oAT-agent
       │ 链路、快照、代码执行栈、覆盖率 codeNodes
       ▼
  oAT-service-web
       ├─ MySQL：项目、应用、成员、版本、用例、覆盖率报告头等结构化数据
       ├─ Elasticsearch：链路、快照、静态源码、报告索引
       ├─ Redis：登录会话、缓存、AI 语义缓存
       ├─ MinIO：覆盖率 codeNodes 对象存储，可关闭
       ├─ Git：源码、Commit、Diff、静态源码分析
       └─ oAT-ai：LLM 接入、工具调用、语义缓存、对话记忆
       ▼
  oAT-web-frontend

可选入口：
  oAT-relay：隔离网络、统一代理出口、边缘节点部署
  oAT-traffic-capture：桌面流量采集、前端/Go/Python/C/C++ 覆盖率本地中继
```

## 技术栈

| 模块 | 技术 |
|---|---|
| Agent | JavaAgent、ASM、内置 sandbox 增强框架、JaCoCo 相关覆盖率能力，默认 Java 7 字节码，提供 JDK6/7/8 profile |
| 后端主服务 | Java 17、Spring Boot 3.3.6、Spring MVC、Spring Data Elasticsearch、Redis、JDBC、Druid、JGit、JavaParser、MinIO、MessagePack |
| AI 模块 | LangChain4j，支持 OpenAI、DeepSeek、Ollama 和兼容 OpenAI 协议的自定义服务 |
| Relay | Java 17、Spring Boot 3.3.6、Actuator、HTTP 转发队列与限流 |
| Web 前端 | Vue 3、TypeScript、Vite 7、Pinia、Vue Router |
| 桌面端 | Electron 30、Vue 3、TypeScript、Vite 5、SQLite、http-mitm-proxy、ws、mqtt、ExcelJS |

## 环境要求

| 组件 | 建议版本 | 用途 |
|---|---|---|
| JDK | Agent 构建建议 8+；服务端和 relay 运行 17+ | Java 模块构建与运行 |
| Maven | 3.8+ | Java 模块构建 |
| Node.js | 18+ | Web 前端和 Electron 桌面端构建 |
| MySQL | 5.7+ / 8.x | 结构化数据 |
| Elasticsearch | 7.x / 8.x | 链路、快照、静态源码和报告索引 |
| Redis | 5.x+ | 登录会话、缓存、AI 语义缓存 |
| MinIO | RELEASE.2023+ | 覆盖率 codeNodes 对象存储，可选 |

## 快速构建

模块之间存在本地 Maven 依赖，建议按以下顺序构建：

```bash
# 1. 构建 Agent，同时安装 oAT-client-model
cd oAT-agent
mvn clean install

# 2. 构建服务端聚合模块：oAT-ai + oAT-service-web
cd ../oAT-service
mvn clean install

# 3. 构建 HTTP 中继，可选
cd ../oAT-relay
mvn clean package

# 4. 构建 Web 前端，可选
cd ../oAT-web-frontend
npm install
npm run build

# 5. 构建桌面流量采集器，可选
cd ../oAT-traffic-capture
npm install
npm run build
```

主要产物：

| 模块 | 产物 |
|---|---|
| `oAT-agent/oAT-agent-core` | `target/oAT-agent-core-1.0-SNAPSHOT.jar` |
| `oAT-service/oAT-service-web` | `target/oAT-service-web-1.0.0-SNAPSHOT.war` |
| `oAT-relay` | `target/oAT-relay-1.0.0-SNAPSHOT.jar` |
| `oAT-web-frontend` | `dist/` |
| `oAT-traffic-capture` | `dist/`、`dist-electron/`，安装包输出到 `release/` |

## 快速启动

1. 启动 MySQL、Elasticsearch、Redis。
2. 如果启用覆盖率对象存储，启动 MinIO，并创建 `oat-coverage` bucket。
3. 在 MySQL 中创建数据库 `oaccurate_test`。
4. 按文件名顺序执行 `oAT-service/oAT-service-web/src/main/resources/db/mysql/` 下的 SQL：

```text
00_oaccurate_test_schema.sql
phase1_snapshot_probe.sql
phase2_api_endpoint.sql
phase2_coverage_report.sql
phase2_version_center.sql
phase3_case_center.sql
phase3_system_snapshot.sql
phase4_class_coverage.sql
phase4_static_source_info.sql
phase5_normalized_core.sql
phase6_frontend_coverage.sql
phase7_universal_coverage.sql
phase8_multilang_dimension.sql
phase9_drop_non_mysql_tables.sql
```

5. 修改 `oAT-service/oAT-service-web/src/main/resources/application.properties` 中的 MySQL、Elasticsearch、Redis、MinIO、`oat.data.path` 和 `ai.*` 配置。
6. 启动后端主服务：

```bash
cd oAT-service/oAT-service-web
java -jar target/oAT-service-web-1.0.0-SNAPSHOT.war
```

7. 启动 Web 前端开发服务：

```bash
cd oAT-web-frontend
npm run dev
```

默认地址：

```text
后端服务：http://127.0.0.1:8899
前端开发：http://127.0.0.1:5173
```

8. 在目标 Java 应用中挂载 Agent：

```bash
java -javaagent:/path/to/oAT-agent-core-1.0-SNAPSHOT.jar=appKey=your-app-key \
  -jar your-application.jar
```

`appKey` 必须与平台中登记的应用标识一致。

## 中继部署

当 Agent、SDK、桌面端或前端无法直连 `oAT-service-web` 时，可启动 `oAT-relay`：

```bash
cd oAT-relay
java -jar target/oAT-relay-1.0.0-SNAPSHOT.jar \
  --server.port=18089 \
  --oat.relay.target-base-url=http://127.0.0.1:8899
```

客户端保持接口路径不变，只替换主机和端口：

```text
http://127.0.0.1:8899/api/...
http://127.0.0.1:18089/api/...
```

Agent 配置示例：

```properties
server=127.0.0.1:18089
```

relay 默认端口为 `18089`，默认目标服务为 `http://127.0.0.1:8899`，支持 `sync`、`async`、`hybrid` 三种转发模式。上传类接口建议使用默认 `hybrid` 模式，让大载荷上送进入异步队列，普通 `/api/` 请求保持同步转发。

## 覆盖率入口

Java 覆盖率由 Agent 随链路快照采集。前端和多语言覆盖率可以直接上送到后端，也可以通过桌面采集器本地中继。

统一覆盖率入口：

```text
POST /api/v2/ingest/coverage
```

模块化入口：

```text
POST /api/projects/{projectId}/apps/{appId}/coverage/frontend/report
POST /api/projects/{projectId}/apps/{appId}/coverage/universal/{CPP|GO|PYTHON}/report
```

SDK 位置：

```text
oAT-traffic-capture/sdk/oat-coverage-reporter.ts
oAT-traffic-capture/sdk/coverage/go/oatcover/reporter.go
oAT-traffic-capture/sdk/coverage/python/oat_python_coverage_reporter.py
oAT-traffic-capture/sdk/coverage/native/oat_native_gcov_reporter.py
```

桌面采集器的覆盖率本地接收地址：

```text
http://localhost:8889/oat/coverage/report
```

## 常用端口

| 端口 | 模块 | 说明 |
|---|---|---|
| `8899` | `oAT-service-web` | 后端主服务 |
| `5173` | `oAT-web-frontend` | Vite 开发服务 |
| `18089` | `oAT-relay` | HTTP 中继 |
| `8888` | `oAT-traffic-capture` | 本地 HTTP/HTTPS 代理 |
| `8889` | `oAT-traffic-capture` | 覆盖率本地接收服务 |
| `9200` | Elasticsearch | 默认 ES 地址 |
| `6379` | Redis | 默认 Redis 地址 |
| `9000` | MinIO | 默认 MinIO API 地址 |

## 模块文档

- [oAT-agent](oAT-agent/README.md)
- [oAT-service](oAT-service/README.md)
- [oAT-service-web](oAT-service/oAT-service-web/README.md)
- [oAT-ai](oAT-service/oAT-ai/README.md)
- [oAT-relay](oAT-relay/README.md)
- [oAT-web-frontend](oAT-web-frontend/README.md)
- [oAT-traffic-capture](oAT-traffic-capture/README.md)
- [多语言覆盖率上送 SDK](oAT-traffic-capture/sdk/coverage/README.md)

## 注意事项

- 服务端和 relay 使用 Java 17+；Agent 默认编译为 Java 7 字节码，可通过 `probe-jdk6`、`probe-jdk7`、`probe-jdk8` profile 调整。
- `oAT-service-web` 单独构建前，必须先构建 `oAT-agent` 和 `oAT-service/oAT-ai`。
- 首次部署执行全部 SQL；升级部署只执行新增 phase，执行前请备份数据库。
- `oat.data.path` 用于 Git 源码缓存、大载荷和静态源码文件，服务进程需要读写权限，并应预留足够磁盘空间。
- 启用 MinIO 后，覆盖率 `codeNodes` 会优先存入对象存储；关闭 `coverage.storage.enabled` 后会回退到后端原有存储路径。
- 大覆盖率文件或大链路上送场景，需要同步调整 `oAT-service-web`、`oAT-relay`、Nginx/网关和容器平台的请求体大小限制。
- AI 工具调用要求模型支持 Function Calling / Tools；Ollama 或自定义模型响应较慢时，应调大 `ai.llm.timeout`。
