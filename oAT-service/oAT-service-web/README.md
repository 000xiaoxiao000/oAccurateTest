# oAT-service-web

`oAT-service-web` 是 oAccurateTest 的后端主服务。它负责接收 Agent 上报、管理项目和应用、维护系统快照、生成覆盖率报告、处理版本和用例数据，并加载 `oAT-ai` 提供智能分析能力。

## 模块结构

```text
oAT-service-web/src/main/java/com/oAT/web/
├── control/          # Controller，包含 /api/* 前端接口
├── service/          # 业务服务接口与实现
├── esDao/            # Elasticsearch Repository 与索引实体
├── coverage/         # Java/前端/多语言覆盖率解析、存储与报告生成
├── config/           # Spring 配置、异步线程池、Redis、ES 初始化
├── security/         # 登录拦截与安全相关代码
├── domain/           # 图谱和视图领域模型
├── common/           # 通用工具
├── dto/              # DTO
└── exceptions/       # 业务异常

src/main/resources/
├── application.properties
├── db/mysql/          # MySQL 初始化脚本
├── db/elasticsearch/  # 兼容保留的旧 ES 模板
└── elasticsearch/     # 当前 ES ILM 与索引模板
```

## 构建

先构建 `oAT-agent` 和 `oAT-ai`，再构建本模块：

```bash
cd oAT-agent
mvn clean install

cd ../oAT-service/oAT-ai
mvn clean install

cd ../oAT-service-web
mvn clean package
```

产物：

```text
target/oAT-service-web-1.0.0-SNAPSHOT.war
```

## 依赖服务

| 服务 | 用途 |
|---|---|
| MySQL | 项目、应用、版本、用例、成员、配置等结构化数据 |
| Elasticsearch | 链路、快照、静态源码、覆盖率报告索引 |
| Redis | 登录会话、缓存、AI 语义缓存 |
| MinIO | 覆盖率 codeNodes 对象存储，可关闭 |
| Git | 版本源码、Commit、Diff 和静态源码分析 |

## 数据库初始化

首次部署时，按文件名顺序执行 `src/main/resources/db/mysql/` 下的 SQL：

```text
phase1_snapshot_probe.sql
phase2_api_endpoint.sql
phase2_coverage_report.sql
phase2_probe_alert_event.sql
phase2_version_center.sql
phase3_case_center.sql
phase3_system_snapshot.sql
phase4_class_coverage.sql
phase4_static_source_info.sql
phase5_normalized_core.sql
phase6_frontend_coverage.sql
phase7_universal_coverage.sql
phase8_multilang_dimension.sql
```

Elasticsearch 模板由服务启动时的初始化逻辑自动创建，通常不需要手动执行。

## 关键配置

配置文件：

```text
src/main/resources/application.properties
```

### 服务端口

```properties
server.port=8899
```

如通过 `oAT-relay` 暴露服务，保持本服务端口不变，在 relay 中配置：

```properties
oat.relay.target-base-url=http://127.0.0.1:8899
```

### MySQL

```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/oaccurate_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=123456
```

### Elasticsearch

```properties
elasticsearch.gatewayIpPorts=localhost:9200
elasticsearch.username=
elasticsearch.password=
```

### Redis

```properties
spring.data.redis.host=127.0.0.1
spring.data.redis.port=6379
spring.data.redis.database=0
```

### 本地数据目录

```properties
oat.data.path=${user.home}/oAT/codeData/
```

该目录用于 Git 源码缓存、大载荷、静态源码压缩包等，服务进程需要读写权限。

### 上传大小

```properties
spring.servlet.multipart.max-file-size=2048MB
spring.servlet.multipart.max-request-size=2048MB
```

如果前面有 Nginx、网关或外部 Tomcat，也需要同步调整对应请求体限制。

### 覆盖率对象存储

```properties
coverage.storage.enabled=true
coverage.storage.type=minIO
coverage.storage.endpoint=http://localhost:9000
coverage.storage.bucket=oat-coverage
coverage.storage.access-key=root
coverage.storage.secret-key=12345678
```

启用后，服务会把 Agent 链路中的 `codeNodes` 提取出来，以 MessagePack 写入 MinIO，减少 Elasticsearch 存储压力。生成覆盖率报告时优先从 MinIO 读取，缺失或不可用时回退到 ES 链路数据。

关闭 MinIO：

```properties
coverage.storage.enabled=false
```

### 用例链接模板

```properties
oat.usecase.defect-link-template=https://jira.example.com/browse/{id}
oat.usecase.prd-link-template=https://prd.example.com/doc/{id}
```

### AI 配置

`ai.*` 配置写在本模块的 `application.properties`，具体说明见 [oAT-ai README](../oAT-ai/README.md)。

## 启动

```bash
java -jar target/oAT-service-web-1.0.0-SNAPSHOT.war
```

后台启动示例：

```bash
nohup java -jar target/oAT-service-web-1.0.0-SNAPSHOT.war > oat.log 2>&1 &
```

外部 Tomcat 部署时需使用 Tomcat 10+，以匹配 Spring Boot 3.x 的 Servlet 版本要求。

## 覆盖率接口

统一覆盖率上送入口，多语言 SDK 默认使用该接口：

```text
POST /api/v2/ingest/coverage
```

前端 Istanbul：

```text
POST /api/projects/{projectId}/apps/{appId}/coverage/frontend/report
POST /api/projects/{projectId}/apps/{appId}/coverage/frontend/generate
```

Go / Python / C/C++：

```text
POST /api/projects/{projectId}/apps/{appId}/coverage/universal/{CPP|GO|PYTHON}/report
POST /api/projects/{projectId}/apps/{appId}/coverage/universal/{CPP|GO|PYTHON}/generate
```

多语言 SDK 与本地中继见 `oAT-traffic-capture/sdk/coverage/` 和 `oAT-traffic-capture/sdk/oat-coverage-reporter.ts`。

## 覆盖率生成流程

1. 前端选择应用、版本、分支、目标 Commit；增量报告还需选择基线 Commit。
2. Controller 发起异步任务。
3. `CoverageService` 读取应用配置、静态源码结构和版本数据。
4. 增量报告通过 `GitService` 获取 Commit Diff。
5. 查询目标版本下的快照列表。
6. 按 `traceId` 优先从 MinIO 加载 `codeNodes`，缺失时从 ES 链路数据回退。
7. 聚合类、方法、行、分支覆盖率。
8. 保存报告头和类覆盖率明细。
9. 前端展示概览、详情、源码着色并支持 Excel 导出。

## 主要服务

| Service | 职责 |
|---|---|
| `CoverageService` | Java 覆盖率报告生成、源码着色、导出 |
| `FrontendCoverageService` | 前端 Istanbul 覆盖率上报与报告生成 |
| `UniversalCoverageIngestService` | Go / Python / C/C++ 覆盖率上报与解析 |
| `SystemSnapshotService` | 系统快照管理 |
| `SnapshotService` | 个人快照管理 |
| `VersionService` | 版本、分支、Commit 和 Diff |
| `UsecaseService` | 用例目录、详情和关联 |
| `ApiEndpointAnalysisService` | API 端点识别和覆盖分析 |
| `AIInteractiveService` | AI 对话、上下文路由和流式输出 |
| `ClientSessionService` | Agent 会话和心跳 |
| `ProbeAlertEventService` | 探针告警事件 |

## 注意事项

- MySQL、Elasticsearch、Redis 应先于本服务启动。
- 首次部署执行全部 SQL；升级时只执行新增 phase。
- `oat.data.path` 会自动创建，但磁盘空间和权限需要提前确认。
- `oAT-relay` 只是转发层，不替代本服务的数据库、ES、Redis、MinIO 或 AI 配置。
