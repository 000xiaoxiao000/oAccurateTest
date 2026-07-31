# oAT-service

`oAT-service` 是 oAccurateTest 的服务端 Maven 聚合模块，统一管理 AI 分析模块 `oAT-ai` 和后端主服务 `oAT-service-web`。该目录本身不启动进程，主要承担服务端聚合构建、公共构建配置和服务端文档入口职责。

## 模块结构

```text
oAT-service/
├── pom.xml
├── oAT-ai/           # AI LLM 集成与智能分析工具，打包为 jar
└── oAT-service-web/  # 后端主服务，打包为 war，可直接 java -jar 启动
```

| 模块 | 打包 | 职责 |
|---|---|---|
| `oAT-ai` | `jar` | LangChain4j 接入、LLM provider 配置、工具调用、语义缓存、会话记忆、反馈学习。 |
| `oAT-service-web` | `war` | 平台 API、Agent 上报、项目应用、探针监控、快照、覆盖率、版本、用例、代码图谱、搜索、AI 交互入口。 |

`oAT-service-web` 依赖两个本地模块：

- `oAT-agent/oAT-client-model`：Agent 与服务端共享的上报模型。
- `oAT-service/oAT-ai`：AI 自动装配与工具服务。

## 服务端能力

| 能力 | 主要实现 |
|---|---|
| 项目与应用 | 项目、成员、标签、应用、仓库配置、应用设置和在线实例。 |
| Agent 接入 | 接收 JavaAgent 链路、心跳、会话、探针状态和系统日志。 |
| 探针监控 | 在线会话、探针告警、离线扫描、实时链路查询和 SSE 推送。 |
| 快照中心 | 个人快照、系统快照、目录、评论、分享、用例绑定、链路图谱和代码报告。 |
| 覆盖率中心 | Java 快照覆盖率、前端 Istanbul、Go、Python、C/C++ 通用覆盖率，支持全量、增量、趋势、源码着色和导出。 |
| 覆盖率核心 | `coveragecore` 提供统一 ingest、report、query、diff、source、persistence 分层。 |
| 多语言适配 | `language` 分层提供 Java、Frontend、Universal 适配器和应用配置校验。 |
| 版本中心 | Git 拉取、分支、Commit、版本、基线、包 Commit 校验、版本对比和报告详情。 |
| 用例中心 | 用例目录、详情、导入导出、分享、缺陷/PRD 链接和搜索索引重建。 |
| 代码关系图谱 | 应用、快照、表、远程调用、Dubbo、代码覆盖层等图谱数据。 |
| 质量分析 | 测试缺口、质量门禁、测试影响分析和版本影响对比。 |
| AI 交互 | 页面上下文、SSE 流式响应、会话状态、工具路由、覆盖率/链路/源码数据提供。 |

## 代码分层

`oAT-service-web` 目前同时保留旧 `control` 接口和新 `api` 分层。新增能力优先放在新分层：

```text
oAT-service-web/src/main/java/com/oAT/web/
├── api/             # 新版 /api 和 /api/v2 接口 payload、facade、页面聚合服务
├── control/         # 兼容保留的 Controller，含部分前端接口
├── service/         # 传统业务服务接口与实现
├── coverage/        # 旧覆盖率解析、存储和报告能力
├── coveragecore/    # 新覆盖率核心：ingest、report、query、diff、source、persistence
├── language/        # Java、Frontend、Universal 覆盖率语言适配
├── analytics/       # 质量门禁、测试缺口、测试影响分析
├── runtime/         # 运行时数据 provider
├── infra/git/       # Git 基础设施
├── esDao/           # Elasticsearch Repository 与索引实体
├── domain/          # 版本、快照、用例、API endpoint 等领域模型
├── config/          # Spring、Redis、ES、异步线程池、对象存储等配置
├── security/        # 登录拦截与安全相关代码
└── common/          # 通用工具
```

资源目录：

```text
oAT-service-web/src/main/resources/
├── application.properties
├── db/mysql/          # MySQL 初始化与升级脚本
├── db/elasticsearch/  # 兼容保留的旧 ES 模板
└── elasticsearch/     # 当前 ES ILM policy 与 index template
```

## 技术栈

| 模块 | 技术 |
|---|---|
| 聚合模块 | Maven，Java 17 编译配置 |
| `oAT-service-web` | Spring Boot 3.3.6、Spring MVC、Spring Data Elasticsearch、Spring Data Redis、JDBC、Druid、JGit、JavaParser、MinIO、MessagePack、EasyExcel |
| `oAT-ai` | LangChain4j 1.12.2、Spring Boot AutoConfigure、OpenAI / DeepSeek / Ollama / custom provider |

## 构建

服务端构建前，先构建根目录下的 Agent 模块，因为 `oAT-service-web` 依赖 `oAT-client-model`：

```bash
cd ../oAT-agent
mvn clean install
```

然后在服务端聚合模块中一次性构建：

```bash
cd ../oAT-service
mvn clean install
```

也可以按依赖顺序分别构建：

```bash
cd oAT-ai
mvn clean install

cd ../oAT-service-web
mvn clean package
```

主要产物：

```text
oAT-service/oAT-ai/target/oAT-ai-1.0-SNAPSHOT.jar
oAT-service/oAT-service-web/target/oAT-service-web-1.0.0-SNAPSHOT.war
```

## 运行

`oAT-service` 不直接运行，服务端进程由 `oAT-service-web` 启动：

```bash
cd oAT-service-web
java -jar target/oAT-service-web-1.0.0-SNAPSHOT.war
```

默认后端端口：

```text
http://127.0.0.1:8899
```

外部 Tomcat 部署时使用 Tomcat 10+，以匹配 Spring Boot 3.x / Jakarta Servlet 版本。

## 依赖服务

| 服务 | 用途 |
|---|---|
| MySQL | 项目、应用、成员、版本、用例、配置、覆盖率报告头等结构化数据。 |
| Elasticsearch | 链路、快照、静态源码、覆盖率方法搜索、覆盖率趋势、探针告警、系统日志等索引。 |
| Redis | 登录会话、缓存、AI 语义缓存、会话状态。 |
| MinIO | 覆盖率 `codeNodes` 对象存储，可通过配置关闭。 |
| Git | 版本源码、Commit、Diff、静态源码分析和版本影响对比。 |
| LLM Provider | AI 对话与工具调用，可选 OpenAI、DeepSeek、Ollama 或兼容 OpenAI 协议服务。 |

## 初始化脚本

首次部署时，按文件名顺序执行 `oAT-service-web/src/main/resources/db/mysql/` 下的 SQL：

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

Elasticsearch 的 ILM policy 和 index template 由服务启动时初始化逻辑处理，模板文件位于 `oAT-service-web/src/main/resources/elasticsearch/`。

## 关键配置

配置文件：

```text
oAT-service-web/src/main/resources/application.properties
```

常用配置分组：

| 配置 | 说明 |
|---|---|
| `server.port` | 后端端口，默认 `8899`。 |
| `spring.datasource.*` | MySQL 连接与 Druid 连接池。 |
| `elasticsearch.*` | ES 地址和鉴权。 |
| `spring.data.redis.*` | Redis 地址、端口和 database。 |
| `oat.data.path` | Git 缓存、大载荷、静态源码包等本地数据目录。 |
| `spring.servlet.multipart.*` | 上传文件和请求体大小限制。 |
| `coverage.storage.*` | 覆盖率 `codeNodes` 对象存储，默认 MinIO。 |
| `oat.storage.migration.*` | 覆盖率存储迁移与 ES 重建控制。 |
| `oat.usecase.*-link-template` | 用例中的缺陷和 PRD 链接模板。 |
| `ai.llm.*` | LLM provider、base URL、API key、模型、超时、token 和日志开关。 |
| `ai.enhanced.*` | AI 语义缓存、会话记忆、自学习和反馈保留配置。 |
| `ai.interactive.route.*` | AI 根据前端页面上下文路由工具的关键词。 |

## 主要 API 入口

| 前缀 | 说明 |
|---|---|
| `/api/auth/*`、`/api/account/*` | 注册、登录、当前用户、账号设置。 |
| `/api/projects/*` | 项目、应用、成员、标签、仓库配置、监控、快照、版本、用例、图谱等主业务接口。 |
| `/api/projects/{projectId}/ai/*` | AI 上下文、提问、SSE 流式回答、会话状态。 |
| `/api/projects/{projectId}/coverage/*` | 兼容保留的覆盖率报告、源码、趋势、footprint 接口。 |
| `/api/v2/coverage/*` | 新覆盖率核心接口，包含概览、报告、导出、源码、模块、树节点、任务和质量分析。 |
| `/api/v2/ingest/coverage` | 统一覆盖率上送入口，供多语言 SDK 使用。 |
| `/api/storage-migration/*` | 覆盖率对象存储迁移和 ES 重建接口，默认需按配置显式开启。 |
| `/share/api/*` | 快照和用例分享页接口。 |

覆盖率上送常用入口：

```text
POST /api/v2/ingest/coverage
POST /api/projects/{projectId}/apps/{appId}/coverage/frontend/report
POST /api/projects/{projectId}/apps/{appId}/coverage/universal/{CPP|GO|PYTHON}/report
```

## 与其他模块关系

```text
oAT-agent
  └─ 上报链路、心跳、快照和 codeNodes 到 oAT-service-web

oAT-service-web
  ├─ 依赖 oAT-client-model
  ├─ 加载 oAT-ai 自动装配
  ├─ 读写 MySQL / Elasticsearch / Redis / MinIO / Git
  └─ 向 oAT-web-frontend 提供 /api/* 和 /api/v2/* 接口

oAT-relay
  └─ 可作为 oAT-service-web 的 HTTP 中继，不替代任何服务端存储或 AI 配置

oAT-traffic-capture
  └─ 可向 oAT-service-web 或 oAT-relay 上送流量和多语言覆盖率
```

## 子模块文档

- [oAT-service-web](oAT-service-web/README.md)
- [oAT-ai](oAT-ai/README.md)

## 注意事项

- 服务端统一使用 JDK 17+。
- 单独构建 `oAT-service-web` 前，必须先安装 `oAT-agent/oAT-client-model` 和 `oAT-ai`。
- 首次部署执行全部 SQL；升级部署只执行新增 phase，执行前先备份数据库。
- `oat.data.path` 会保存 Git 缓存、大载荷和静态源码文件，服务进程需要读写权限，并应预留足够磁盘空间。
- 启用 MinIO 后，覆盖率 `codeNodes` 优先写入对象存储；关闭 `coverage.storage.enabled` 后回退到服务端原有存储路径。
- 大覆盖率文件或大链路上送场景，需要同步调整后端、relay、Nginx/网关和容器平台的请求体限制。
- AI 工具调用依赖模型的 Function Calling / Tools 能力；使用 Ollama 或自定义 provider 时，应确认模型能力并调大 `ai.llm.timeout`。
