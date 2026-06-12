# oAT-service-web

oAT-service-web 是 oAccurateTest 平台的 Web 主服务，负责接收 Agent 上报的链路数据、管理系统快照、生成覆盖率报告、提供版本管理和用例中心，并对接 `oAT-ai` 模块提供 AI 对话能力。

---

## 模块结构

```
oAT-service-web/src/main/java/com/oAT/web/
├── control/          # Controller 层（页面接口 + API 接口）
│   └── api/          # 前端 API 接口（/api/* 路径）
├── service/          # Service 接口与实现
│   └── impl/
├── esDao/            # Elasticsearch Repository（Spring Data ES）
│   └── entity/       # ES 索引实体
├── domain/           # 图谱视图领域模型
├── common/           # 工具类（ClassStructure、Zip、Compare 等）
├── config/           # Spring 配置（异步、Redis、ES 初始化等）
├── security/         # 登录拦截器
├── exceptions/       # 业务异常类
└── dto/              # 数据传输对象

src/main/resources/
├── application.properties       # 应用配置
├── db/mysql/                    # MySQL 初始化 SQL（按阶段分文件）
└── elasticsearch/               # ES 索引模板 JSON
```

---

## 构建

需要先完成 `oAT-agent` 和 `oAT-ai` 的构建，再构建本模块：

```bash
cd oAT-service/oAT-service-web
mvn clean package
```

产出物：`target/oAT-service-web-1.0.0-SNAPSHOT.war`

---

## 数据库初始化

### MySQL

按顺序执行 `src/main/resources/db/mysql/` 下的 SQL 文件：

```
phase1_snapshot_probe.sql      # 快照与探针基础表
phase2_api_endpoint.sql        # API 端点
phase2_coverage_report.sql     # 覆盖率报告
phase2_probe_alert_event.sql   # 探针告警事件
phase2_version_center.sql      # 版本中心
phase3_case_center.sql         # 用例中心
phase3_system_snapshot.sql     # 系统快照
phase4_class_coverage.sql      # 类覆盖率明细
phase4_static_source_info.sql  # 静态源码信息
phase5_normalized_core.sql     # 核心表规范化
```

### Elasticsearch

ES 索引模板在服务启动时由 `ElasticsearchTemplateInitializer` 自动创建，无需手动执行。对应模板文件位于 `src/main/resources/elasticsearch/`。

---

## 配置

配置文件：`src/main/resources/application.properties`

### 服务端口

```properties
server.port=8899
```

### Elasticsearch

```properties
elasticsearch.gatewayIpPorts=localhost:9200
elasticsearch.username=
elasticsearch.password=
```

### MySQL

```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/oaccurate_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=123456
```

### Redis

```properties
spring.data.redis.host=127.0.0.1
spring.data.redis.port=6379
spring.data.redis.database=0
```

### 本地数据目录

```properties
# 用于 Git 源码缓存、大载荷存储、静态源码压缩包等
oat.data.path=${user.home}/oAT/codeData/
```

服务进程需要对此目录有读写权限。

### 上传大小限制

```properties
spring.servlet.multipart.max-file-size=2048MB
spring.servlet.multipart.max-request-size=2048MB
```

若通过 Nginx 或外部 Tomcat 部署，还需相应调整 `client_max_body_size` / `LimitRequestBody`。

### 链路监控缓存

```properties
# 监控最大保留节点数
traceNode.monitor.maxSize=200

# 链路节点本地缓存容量
traceNode.cache.capacity=5000

# 缓存有效期（秒）
traceNode.cache.validityTime=300
```

### 覆盖率数据存储（MinIO）

```properties
# 是否启用 MinIO 对象存储（默认 true）
coverage.storage.enabled=true
coverage.storage.type=minIO
coverage.storage.endpoint=http://localhost:9000
coverage.storage.bucket=oat-coverage
coverage.storage.access-key=root
coverage.storage.secret-key=12345678
```

MinIO 用于存储覆盖率 codeNodes 数据（从链路节点中剥离的代码执行栈），以 MessagePack 格式序列化后异步写入对象存储，减轻 Elasticsearch 存储压力。

**工作机制**：
- 写入：Agent 上报 TraceNode 时异步提取 codeNodes → 序列化为 MessagePack → 写入 MinIO
- 读取：生成覆盖率报告时按 traceId 从 MinIO 加载 → 反序列化为 StackNodeVo[]
- 降级：MinIO 不可用或对象不存在时，自动回退到 ES 中的旧数据（兼容模式）

**注意事项**：
- 服务启动时会自动创建 Bucket，无需手动初始化
- 设置 `coverage.storage.enabled=false` 可完全禁用 MinIO，回退到纯 ES 模式
- 内置有界队列（最多积压 500 个任务），队列满时降级为同步写，保证不丢数据

### 用例关联链接模板

```properties
# 用例中缺陷/PRD 链接模板，{id} 为编号占位符
oat.usecase.defect-link-template=https://jira.example.com/browse/{id}
oat.usecase.prd-link-template=https://prd.example.com/doc/{id}
```

### AI 配置

详见 [oAT-ai README](../oAT-ai/README.md)，所有 `ai.*` 前缀的配置均在本文件中统一设置。

---

## 启动

### 直接启动

```bash
# 前台启动
java -jar target/oAT-service-web-1.0.0-SNAPSHOT.war

# 后台启动并输出日志
nohup java -jar target/oAT-service-web-1.0.0-SNAPSHOT.war > oat.log 2>&1 &
```

### 外部 Tomcat 部署

将 war 包放入 Tomcat `webapps/` 目录，确认 Servlet 版本与 Spring Boot 3.x 兼容（需 Tomcat 10+）。

---

## 主要服务说明

| Service | 职责 |
|---|---|
| `CoverageService` | 生成全量/增量覆盖率报告，源码着色，导出 Excel |
| `SystemSnapshotService` | 系统快照管理，目录与版本归档 |
| `SnapshotService` | 个人快照管理 |
| `VersionService` | 版本登记，Git 分支/Commit 管理，版本差异对比 |
| `AppService` | 应用配置管理 |
| `ProjectService` | 项目与成员管理 |
| `GitService` | JGit 集成，拉取源码、获取 Diff、下载压缩包 |
| `ResourceService` | 本地缓存管理，Zip 解析与源码文件查找 |
| `UsecaseService` | 用例目录与详情管理，关联快照与缺陷/PRD |
| `ApiEndpointAnalysisService` | HTTP 接口识别与覆盖分析 |
| `AIInteractiveService` | AI 对话会话管理，上下文路由，流式输出 |
| `ClientSessionService` | Agent 连接会话管理，心跳处理 |
| `ProbeAlertEventService` | 探针告警事件存储与推送 |

---

## 覆盖率生成流程

1. 前端选择应用、版本、分支、目标 Commit（增量还需选基线 Commit）
2. 控制层发起异步任务（`@Async`，Spring 线程池）
3. `CoverageService` 读取应用配置、静态源码结构、版本信息
4. 增量报告：通过 `GitService` 获取两个 Commit 之间的 Diff
5. 查询该版本下的系统快照列表
6. 遍历每个快照的 `traceId`，优先从 MinIO 加载 codeNodes，MinIO 无数据时降级从 ES 链路节点中提取
7. 聚合类 / 方法 / 行 / 分支覆盖率，合并到 `ClassCoverageIndex`
8. 保存覆盖率报告头（`CoverageReportIndex`）和类明细
9. 前端可查看概览、详情、树形结构、源码着色，导出 Excel

---

## 注意事项

- ES、MySQL、Redis 需先于本服务启动。
- `oat.data.path` 目录如不存在，服务启动时会自动创建，但所在磁盘须有足够空间。
- 首次部署时按顺序执行全部 MySQL DDL 脚本；升级时只执行新增的阶段脚本。
- MacOS 下已声明 Netty 本地 DNS 依赖（`netty-resolver-dns-native-macos`），Linux 部署时无需此依赖，也不影响运行。
- AI 功能依赖 `oAT-ai` jar，若未提前构建 `oAT-ai` 则编译失败。
