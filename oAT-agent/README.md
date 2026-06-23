# oAT-agent

中文 | [English](#english)

oAT-agent 是 oAccurateTest 平台的探针模块，以 JavaAgent 方式无侵入地注入目标 Java 应用，采集运行时链路数据并上报到 oAT-service-web。

---

## 模块结构

```
oAT-agent/
├── oAT-client-model/   # Agent 与服务端共用的数据模型
├── oAT-agent-core/     # 字节码增强、链路采集、数据上报
│   └── conf/
│       └── oAT.conf    # Agent 本地配置文件
└── oAT-agnet-shaded/   # 依赖 shaded 打包，产出独立可用的 jar
```

---

## 构建

```bash
cd oAT-agent
mvn clean install
```

`mvn install` 会将 `oAT-client-model` 安装到本地 Maven 仓库，`oAT-service-web` 依赖此包，必须先于服务端构建。

父 POM 默认以 Java 7 字节码编译，并提供 `probe-jdk6`、`probe-jdk7`、`probe-jdk8` profile：

```bash
mvn clean install -Pprobe-jdk8
```

产出物：`oAT-agent-core/target/oAT-agent-core-1.0-SNAPSHOT.jar`

---

## 接入目标应用

在目标应用的 JVM 启动参数中添加 `-javaagent`：

```bash
java -javaagent:/path/to/oAT-agent-core-1.0-SNAPSHOT.jar=appKey=your-app-key \
     -jar your-application.jar
```

`appKey` 必须与平台中登记的应用标识一致。

也可以在目标 JVM 已启动后使用 attach 方式加载探针：

```bash
java -jar /path/to/oAT-agent-core-1.0-SNAPSHOT.jar <pid> "appKey=your-app-key"
```

不传 `<pid>` 时会列出当前可附加的 Java 进程并提示选择。attach 方式使用 oAT 自己的 `agentmain` 启动链路，不再启动 Arthas。

---

## 配置

Agent 配置文件位于探针安装目录的 `conf/` 下，支持两种命名：

- `oAT.conf`：通用配置，所有应用共用
- `oAT_<appKey>.conf`：按 appKey 的独立配置，优先级高于通用配置

### 连接配置

```properties
# 平台服务地址（必填）
server=127.0.0.1:8899

# 心跳间隔（秒）
heartbeatTime=20

# 会话超时（秒）
sessionTimeout=60

# 日志级别：debug / info / warn / error
log.level=info

# 日志文件路径（默认为当前目录下的 logs/）
# log.path=/your/path/logs/
```

### 采集范围配置

> 采集范围直接影响性能开销，建议精准配置，只覆盖核心业务包。

**服务层采集**（追踪到方法级别，是主要的采集入口）：

```properties
# 按包名包含，多个用 & 分隔，支持 * 和 ? 通配符
conf_service.include=com.example.order.*&com.example.payment.*

# 按包名排除
conf_service.exclude=com.example.*.test.*

# 按方法名包含，支持 test() / test(*) / test(java.lang.String) 等格式
conf_service.includeMethod=

# 按方法名排除（默认排除 get/set/add/hashCode/toString/equals 等）
conf_service.excludeMethod=
```

**代码栈追踪**（仅追踪到类，平台未连接时使用）：

```properties
# 按包名包含（只追踪到类，不含方法）
conf_codeStack.include=com.example.*

# 需先开启 conf_codeStack.include，才可追踪到方法
conf_codeStack.includeMethod=

conf_codeStack.exclude=
conf_codeStack.excludeMethod=
```

**URL 过滤**（排除不需要采集的 HTTP 请求）：

```properties
# 精确匹配、前缀匹配（以 * 结尾）、正则匹配（以 regex: 开头）
# exclude.urls=/actuator/*,regex:^/internal/.+$
exclude.urls=
```

**中间件采集开关**（按需关闭或显式开启；`systemLog`、`threadPool` 默认关闭）：

```properties
# collect.HttpServlet=false
# collect.httpRequestParams=false
# collect.httpRequestBody=false
# collect.httpResponseBody=false
# collect.systemLog=true
# collect.feignInvoker=false
# collect.dubboInvoker=false
# collect.dubboReceive=false
# collect.sofaProviderInvoker=false
# collect.sofaConsumerInvoker=false
# collect.jdbc=false
# collect.clickHouseJdbc=false
# collect.redis=false
# collect.redisson=false
# collect.rabbitMq=false
# collect.rabbitMqReceive=false
# collect.rocketMq=false
# collect.rocketMqReceive=false
# collect.kafkaMq=false
# collect.kafkaMqReceive=false
# collect.httpClientV3=false
# collect.httpClientV4=false
```

**Sandbox 统一增强开关**（推荐优先试用；开启后对应旧 `collect.*` Transformer 会自动跳过）：

```properties
# sandbox.http-servlet.enabled=true
# sandbox.service.enabled=true
# sandbox.jdbc.enabled=true
# sandbox.clickhouse-jdbc.enabled=true
# sandbox.feign.enabled=true
# sandbox.http-client-v3.enabled=true
# sandbox.http-client-v4.enabled=true
# sandbox.dubbo.enabled=true
# sandbox.sofa-rpc.enabled=true
# sandbox.mq-producer.enabled=true
# sandbox.mq-consumer.enabled=true
# sandbox.redis.enabled=true
# sandbox.redisson.enabled=true
# sandbox.coverage.enabled=true
# sandbox.system-log.enabled=true
# sandbox.context-propagation.enabled=true
# sandbox.context-propagation.executor=true
# sandbox.context-propagation.scheduled=true
# sandbox.context-propagation.forkjoin=false
# sandbox.context-propagation.completable-future=false
```

> 开启 `conf_service.include` 后，上述中间件采集会自动跟随 service 采集范围，一般无需单独开启，以避免冲突。

### 性能调优参数（JVM 启动参数）

| 参数 | 默认值 | 建议范围 | 说明 |
|---|---|---|---|
| `oAT.jacoco.stack.maxrecursiondepth` | 1 | 1–5 | 最大递归深度限制 |
| `oAT.jacoco.stack.maxsize` | 自动计算 | 10000–100000 | 最大堆栈节点数 |
| `oAT.agent.http.max.queue.size` | 8192 | 按并发量调整 | HTTP 上报队列大小 |
| `oAT.agent.http.core.pool.size` | 20 | 按并发量调整 | HTTP 线程池核心数 |
| `oAT.agent.http.max.pool.size` | 160 | 按并发量调整 | HTTP 线程池最大数 |
| `oAT.agent.http.keep.alive.time` | 60 | — | 线程保活时间（秒） |
| `oAT.agent.http.base.timeout.ms` | 10000 | — | 基础超时（毫秒） |
| `oAT.agent.http.read.timeout.extra.ms` | 5000 | — | 读取超时附加值（毫秒） |

JVM 栈大小参考：

```bash
# 普通业务
-Xss1m

# 有深递归或超大方法
-Xss2m ~ -Xss4m
```

---

## 支持的采集协议

| 协议 / 框架 | 配置开关 |
|---|---|
| HTTP Servlet（javax / jakarta） | `sandbox.http-servlet.enabled` / `collect.HttpServlet` |
| HTTP 请求参数 / 请求体 / 响应体 | `collect.httpRequestParams` 等 |
| Apache HttpClient v3 / v4 | `sandbox.http-client-v3.enabled / sandbox.http-client-v4.enabled` |
| Feign | `sandbox.feign.enabled` / `collect.feignInvoker` |
| Dubbo（调用方 / 提供方） | `sandbox.dubbo.enabled` |
| SOFA-RPC（Consumer / Provider） | `sandbox.sofa-rpc.enabled` |
| JDBC（MySQL、通用） | `sandbox.jdbc.enabled` / `collect.jdbc` |
| ClickHouse JDBC | `sandbox.clickhouse-jdbc.enabled` / `collect.clickHouseJdbc` |
| Redis（Lettuce / Jedis） | `sandbox.redis.enabled` / `collect.redis` |
| Redisson | `sandbox.redisson.enabled` / `collect.redisson` |
| RabbitMQ（发送 / 接收） | `sandbox.mq-producer.enabled / sandbox.mq-consumer.enabled` |
| RocketMQ（生产者 / 消费者） | `sandbox.mq-producer.enabled / sandbox.mq-consumer.enabled` |
| Kafka（生产者 / 消费者） | `sandbox.mq-producer.enabled / sandbox.mq-consumer.enabled` |
| 业务日志 | `sandbox.system-log.enabled` / `collect.systemLog` |
| 线程池上下文传播 | `sandbox.context-propagation.enabled` |

---

## 注意事项

- 配置文件修改后**必须重启目标应用**才能生效。
- `appKey` 必须与平台应用配置中的标识完全一致，大小写敏感。
- 采集范围不宜过大，整包采集（如 `com.*`）会显著增加请求耗时和数据量。
- 若目标容器（如 Kubernetes、PaaS 平台）不允许自定义 JVM 参数，则无法接入 Agent。
- 平台服务（`oAT-service-web`）需先于目标应用启动，否则 Agent 在建立会话阶段会重试连接。
- Tomcat 默认 POST 限制为 2MB，数据量较大时需在平台侧配置 `server.tomcat.max-http-post-size=100MB`。

---

## English

[中文](#oat-agent) | English

`oAT-agent` is the probe module for oAccurateTest. It is injected into target Java applications as a JavaAgent, collects runtime trace data without application code changes, and reports data to `oAT-service-web`.

## Module Structure

```text
oAT-agent/
├── oAT-client-model/   # Shared data model for Agent and server
├── oAT-agent-core/     # Bytecode enhancement, trace collection, data upload
│   └── conf/
│       └── oAT.conf    # Local Agent configuration
└── oAT-agnet-shaded/   # Shaded packaging for standalone jar output
```

## Build

```bash
cd oAT-agent
mvn clean install
```

`mvn install` installs `oAT-client-model` into the local Maven repository. `oAT-service-web` depends on this package, so build the Agent before building the server.

The parent POM compiles to Java 7 bytecode by default and provides `probe-jdk6`, `probe-jdk7`, and `probe-jdk8` profiles:

```bash
mvn clean install -Pprobe-jdk8
```

Output: `oAT-agent-core/target/oAT-agent-core-1.0-SNAPSHOT.jar`

## Attach to a Target Application

Add `-javaagent` to the target JVM startup parameters:

```bash
java -javaagent:/path/to/oAT-agent-core-1.0-SNAPSHOT.jar=appKey=your-app-key \
     -jar your-application.jar
```

`appKey` must match the application identifier registered in the platform.

You can also attach the probe to an already running JVM:

```bash
java -jar /path/to/oAT-agent-core-1.0-SNAPSHOT.jar <pid> "appKey=your-app-key"
```

When `<pid>` is omitted, the Agent lists attachable Java processes and prompts for selection. The attach mode uses oAT's own `agentmain` startup path and does not start Arthas.

## Configuration

Agent configuration files live under the probe installation `conf/` directory:

- `oAT.conf`: common configuration for all applications
- `oAT_<appKey>.conf`: app-specific configuration with higher priority

### Connection

```properties
server=127.0.0.1:8899
heartbeatTime=20
sessionTimeout=60
log.level=info
# log.path=/your/path/logs/
```

### Collection Scope

Keep the collection scope precise because it directly affects overhead.

```properties
conf_service.include=com.example.order.*&com.example.payment.*
conf_service.exclude=com.example.*.test.*
conf_service.includeMethod=
conf_service.excludeMethod=

conf_codeStack.include=com.example.*
conf_codeStack.includeMethod=
conf_codeStack.exclude=
conf_codeStack.excludeMethod=

exclude.urls=
```

`conf_service.include` traces service methods and is the main collection entry. `conf_codeStack.include` traces classes and can trace methods only when `conf_codeStack.includeMethod` is enabled. `exclude.urls` supports exact match, prefix match with `*`, and regex match with the `regex:` prefix.

### Middleware Switches

Middleware collection can be disabled or explicitly enabled through `collect.*` switches. `systemLog` and `threadPool` are disabled by default. The newer sandbox switches are recommended for unified enhancement:

```properties
# sandbox.http-servlet.enabled=true
# sandbox.service.enabled=true
# sandbox.jdbc.enabled=true
# sandbox.clickhouse-jdbc.enabled=true
# sandbox.feign.enabled=true
# sandbox.http-client-v3.enabled=true
# sandbox.http-client-v4.enabled=true
# sandbox.dubbo.enabled=true
# sandbox.sofa-rpc.enabled=true
# sandbox.mq-producer.enabled=true
# sandbox.mq-consumer.enabled=true
# sandbox.redis.enabled=true
# sandbox.redisson.enabled=true
# sandbox.coverage.enabled=true
# sandbox.system-log.enabled=true
# sandbox.context-propagation.enabled=true
```

When `conf_service.include` is enabled, middleware collection usually follows the service scope automatically, so separate enabling is normally unnecessary.

### Performance Tuning JVM Parameters

| Parameter | Default | Suggested Range | Description |
|---|---|---|---|
| `oAT.jacoco.stack.maxrecursiondepth` | 1 | 1-5 | Maximum recursion depth |
| `oAT.jacoco.stack.maxsize` | Auto | 10000-100000 | Maximum stack node count |
| `oAT.agent.http.max.queue.size` | 8192 | Adjust by concurrency | HTTP upload queue size |
| `oAT.agent.http.core.pool.size` | 20 | Adjust by concurrency | HTTP core pool size |
| `oAT.agent.http.max.pool.size` | 160 | Adjust by concurrency | HTTP max pool size |
| `oAT.agent.http.keep.alive.time` | 60 | — | Thread keep-alive seconds |
| `oAT.agent.http.base.timeout.ms` | 10000 | — | Base timeout in milliseconds |
| `oAT.agent.http.read.timeout.extra.ms` | 5000 | — | Extra read timeout in milliseconds |

Typical JVM stack sizes:

```bash
-Xss1m
-Xss2m
```

Use larger stack sizes such as `-Xss2m` to `-Xss4m` for deep recursion or very large methods.

## Supported Protocols and Frameworks

| Protocol / Framework | Switch |
|---|---|
| HTTP Servlet (`javax` / `jakarta`) | `sandbox.http-servlet.enabled` / `collect.HttpServlet` |
| HTTP request params / body / response body | `collect.httpRequestParams` and related switches |
| Apache HttpClient v3 / v4 | `sandbox.http-client-v3.enabled` / `sandbox.http-client-v4.enabled` |
| Feign | `sandbox.feign.enabled` / `collect.feignInvoker` |
| Dubbo provider / consumer | `sandbox.dubbo.enabled` |
| SOFA-RPC consumer / provider | `sandbox.sofa-rpc.enabled` |
| JDBC and ClickHouse JDBC | `sandbox.jdbc.enabled`, `sandbox.clickhouse-jdbc.enabled` |
| Redis and Redisson | `sandbox.redis.enabled`, `sandbox.redisson.enabled` |
| RabbitMQ, RocketMQ, Kafka | `sandbox.mq-producer.enabled`, `sandbox.mq-consumer.enabled` |
| Business logs | `sandbox.system-log.enabled` / `collect.systemLog` |
| Thread pool context propagation | `sandbox.context-propagation.enabled` |

## Notes

- Restart the target application after changing the configuration file.
- `appKey` must exactly match the platform app key and is case-sensitive.
- Avoid broad collection scopes such as `com.*`; they significantly increase latency and data volume.
- If the target environment does not allow custom JVM parameters, the Agent cannot be attached through startup arguments.
- Start `oAT-service-web` before the target application. Otherwise, the Agent retries during session creation.
- If payloads are large, configure the platform side with `server.tomcat.max-http-post-size=100MB` or an equivalent limit.
