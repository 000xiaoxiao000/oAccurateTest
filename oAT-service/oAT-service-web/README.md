# oAT-service-web

`oAT-service-web` 是 oAccurateTest 的后端主服务。它负责接收 Agent 上报，维护项目、应用、探针、快照、覆盖率、版本、用例、代码图谱、搜索和分享数据，并加载 `oAT-ai` 提供智能分析和交互式问答能力。

## 模块定位

```text
oAT-agent
  └─ 链路、心跳、快照、codeNodes
       ▼
oAT-service-web
  ├─ MySQL / Elasticsearch / Redis / MinIO / Git
  ├─ oAT-ai
  └─ /api/*、/api/v2/*、/share/api/*
       ▼
oAT-web-frontend
```

## 代码结构

```text
src/main/java/com/oAT/web/
├── api/             # 新版 API 分层：payload、facade、页面聚合服务、/api/v2 接口
├── control/         # 兼容保留的 Controller，含旧接口和部分前端 API
├── service/         # 传统业务服务接口与实现
├── coverage/        # 旧覆盖率解析、存储与报告能力
├── coveragecore/    # 新覆盖率核心：ingest、report、query、diff、source、persistence
├── language/        # Java、Frontend、Universal 覆盖率语言适配器
├── analytics/       # 测试缺口、质量门禁、测试影响分析
├── runtime/         # 运行时数据 provider
├── infra/git/       # Git 基础设施
├── collector/       # Agent 采集源相关能力
├── esDao/           # Elasticsearch Repository 与索引实体
├── domain/          # 快照、版本、用例、API endpoint 等领域模型
├── config/          # Spring、Redis、ES、对象存储、异步线程池等配置
├── security/        # 登录拦截与安全相关代码
├── common/          # 通用工具
├── dto/             # DTO
└── exceptions/      # 业务异常
```

资源目录：

```text
src/main/resources/
├── application.properties
├── db/mysql/          # MySQL 初始化与升级脚本
├── db/elasticsearch/  # 兼容保留的旧 ES 模板
└── elasticsearch/     # 当前 ES ILM policy 与 index template
```

## 核心能力

| 能力 | 说明 |
|---|---|
| 项目与应用 | 项目、成员、标签、应用、应用设置、仓库配置、分支列表。 |
| Agent 接入 | 接收链路、心跳、会话、探针状态、系统日志和覆盖率 codeNodes。 |
| 探针监控 | 在线实例、探针告警、离线扫描、实时链路查询、系统快照上下文。 |
| 快照 | 个人快照、系统快照、目录、评论、分享、用例绑定、链路图谱、代码报告。 |
| 覆盖率 | Java 快照覆盖率、前端 Istanbul、Go、Python、C/C++ 通用覆盖率。 |
| 覆盖率核心 | 统一上送、任务生成、报告查询、源码着色、趋势、导出、方法搜索和复用。 |
| 质量分析 | 测试缺口、质量门禁、测试影响分析、版本对比影响分析。 |
| 版本中心 | Git 拉取、Commit、版本、当前版本、包 Commit 校验、对比任务、报告详情。 |
| 用例中心 | 用例目录、详情、导入导出、分享、快照关联、缺陷/PRD 链接。 |
| 图谱 | 项目图谱、应用图谱、代码层、快照表关系、远程调用和 Dubbo 关系。 |
| 搜索 | 快照、用例、表关系等关键字检索。 |
| AI 交互 | 页面上下文、会话状态、SSE 流式响应、覆盖率/链路/源码数据 provider。 |

## 构建

本模块依赖 `oAT-agent/oAT-client-model` 和 `oAT-service/oAT-ai`。单独构建前先安装依赖：

```bash
cd ../../oAT-agent
mvn clean install

cd ../oAT-service/oAT-ai
mvn clean install
```

构建后端主服务：

```bash
cd ../oAT-service-web
mvn clean package
```

产物：

```text
target/oAT-service-web-1.0.0-SNAPSHOT.war
```

也可以在上级聚合模块构建：

```bash
cd ..
mvn clean install
```

## 运行

```bash
java -jar target/oAT-service-web-1.0.0-SNAPSHOT.war
```

后台启动示例：

```bash
nohup java -jar target/oAT-service-web-1.0.0-SNAPSHOT.war > oat.log 2>&1 &
```

默认端口：

```text
http://127.0.0.1:8899
```

外部 Tomcat 部署时使用 Tomcat 10+，以匹配 Spring Boot 3.x / Jakarta Servlet。

## 依赖服务

| 服务 | 用途 |
|---|---|
| MySQL | 项目、应用、成员、版本、用例、配置、覆盖率报告头等结构化数据。 |
| Elasticsearch | 链路、快照、静态源码、覆盖率方法搜索、覆盖率趋势、探针告警、系统日志。 |
| Redis | 登录会话、缓存、AI 语义缓存、AI 会话状态。 |
| MinIO | 覆盖率 codeNodes 对象存储，可关闭。 |
| Git | 版本源码、Commit、Diff、静态源码分析和影响分析。 |
| LLM Provider | AI 对话和工具调用，可选 OpenAI、DeepSeek、Ollama 或兼容 OpenAI 协议服务。 |

## 数据库初始化

首次部署时，按文件名顺序执行 `src/main/resources/db/mysql/` 下的 SQL：

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

Elasticsearch 模板和 ILM policy 位于 `src/main/resources/elasticsearch/`，由启动初始化逻辑创建或更新。

## 关键配置

配置文件：

```text
src/main/resources/application.properties
```

基础配置：

```properties
server.port=8899

spring.datasource.url=jdbc:mysql://127.0.0.1:3306/oaccurate_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=your-password

elasticsearch.gatewayIpPorts=localhost:9200
elasticsearch.username=
elasticsearch.password=

spring.data.redis.host=127.0.0.1
spring.data.redis.port=6379
spring.data.redis.database=0
```

本地数据与大载荷：

```properties
oat.data.path=${user.home}/oAT/codeData/
oat.storage.large-payload.path=${oat.data.path}large-payload/
oat.storage.static-source.inline-threshold-bytes=8192
oat.storage.snapshot-artifact.inline-threshold-bytes=8192
```

上传大小：

```properties
spring.servlet.multipart.max-file-size=2048MB
spring.servlet.multipart.max-request-size=2048MB
server.max-http-request-header-size=102400
```

覆盖率对象存储：

```properties
coverage.storage.enabled=true
coverage.storage.type=minIO
coverage.storage.endpoint=http://localhost:9000
coverage.storage.bucket=oat-coverage
coverage.storage.access-key=your-access-key
coverage.storage.secret-key=your-secret-key
```

存储迁移：

```properties
oat.storage.migration.api-enabled=false
oat.storage.migration.token=
oat.storage.migration.run-on-startup=false
oat.storage.migration.batch-size=100
oat.storage.migration.max-rounds=10000
oat.storage.migration.exit-on-complete=true
```

AI 配置写在同一文件中，详见 [oAT-ai README](../oAT-ai/README.md)。

## API 入口

| 前缀 | 说明 |
|---|---|
| `/api/auth/*` | 注册、登录、当前用户、退出。 |
| `/api/account/*` | 账号资料和密码。 |
| `/api/projects/*` | 项目、应用、成员、标签、监控、快照、版本、用例、图谱等主业务接口。 |
| `/api/projects/{projectId}/ai/*` | AI 页面上下文、问答、会话状态。 |
| `/api/projects/{projectId}/coverage/*` | 兼容保留的覆盖率报告接口。 |
| `/api/v2/ingest/coverage` | 统一覆盖率上送入口。 |
| `/api/v2/coverage/*` | 新覆盖率核心接口。 |
| `/api/storage-migration/*` | 覆盖率存储迁移和 ES 重建。 |
| `/share/api/*` | 快照和用例分享页接口。 |

覆盖率上送：

```text
POST /api/v2/ingest/coverage
POST /api/projects/{projectId}/apps/{appId}/coverage/frontend/report
POST /api/projects/{projectId}/apps/{appId}/coverage/universal/{CPP|GO|PYTHON}/report
```

覆盖率生成：

```text
POST /api/v2/coverage/apps/{appId}/generate-current
POST /api/v2/coverage/apps/{appId}/generate-incremental
POST /api/v2/coverage/apps/{appId}/frontend/generate
POST /api/v2/coverage/apps/{appId}/universal/{sourceType}/generate
GET  /api/v2/coverage/jobs/{jobId}
```

覆盖率查询：

```text
GET /api/v2/coverage/apps/{appId}/overview
GET /api/v2/coverage/reports/{reportId}
GET /api/v2/coverage/reports/{reportId}/units
GET /api/v2/coverage/reports/{reportId}/modules
GET /api/v2/coverage/reports/{reportId}/tree-nodes
GET /api/v2/coverage/reports/{reportId}/source
GET /api/v2/coverage/reports/{reportId}/methods
GET /api/v2/coverage/reports/{reportId}/test-gap
GET /api/v2/coverage/reports/{reportId}/quality-gate
GET /api/v2/coverage/reports/{reportId}/test-impact
```

## 覆盖率处理流程

```text
覆盖率上送
  └─ CoverageIngestApiControl
       └─ CoverageIngestFacade
            └─ MultiLanguageCoverageIngestService
                 └─ language adapter
                      └─ CoverageReportPersistenceService / ES index

覆盖率生成
  └─ CoverageOperationsApiControl
       └─ CoverageReportJobService
            ├─ JavaCoverageReportGenerationService
            ├─ MultiLanguageCoverageReportService
            ├─ CoverageDiffService
            └─ CoverageSourceColoringService
```

Java 覆盖率优先从 MinIO 加载 Agent 链路中的 `codeNodes`，缺失或关闭对象存储时回退到原有链路数据路径。

## 注意事项

- MySQL、Elasticsearch、Redis 应先于本服务启动。
- 首次部署执行全部 SQL；升级只执行新增 phase，执行前备份数据库。
- `oat.data.path` 会保存 Git 缓存、大载荷和静态源码包，服务进程需要读写权限。
- 大覆盖率文件或大链路上送时，需要同步调整后端、relay、Nginx/网关和容器平台的请求体限制。
- `oAT-relay` 只是 HTTP 转发层，不替代数据库、ES、Redis、MinIO 或 AI 配置。
