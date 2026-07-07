# oAT-agent

`oAT-agent` 是 oAccurateTest 的 JavaAgent 探针模块。它以无侵入方式注入目标 Java 应用，通过字节码增强采集 HTTP、RPC、SQL、缓存、消息队列、业务方法和代码执行栈，并将数据上报到 `oAT-service-web`。

## 模块结构

```text
oAT-agent/
├── oAT-client-model/   # Agent 与服务端共用数据模型，服务端构建前需先 install
├── oAT-agent-core/     # JavaAgent 入口、字节码增强、链路采集、HTTP 上报
│   └── conf/oAT.conf   # 默认配置文件
└── oAT-agnet-shaded/   # shaded 依赖包，供 agent-core 使用
```

## 构建

```bash
cd oAT-agent
mvn clean install
```

默认编译目标是 Java 7 字节码，并提供以下 profile：

```bash
mvn clean install -Pprobe-jdk6
mvn clean install -Pprobe-jdk7
mvn clean install -Pprobe-jdk8
```

主要产物：

```text
oAT-agent-core/target/oAT-agent-core-1.0-SNAPSHOT.jar
```

`mvn install` 会把 `oAT-client-model` 安装到本地 Maven 仓库，`oAT-service-web` 依赖该包，所以服务端构建前必须先构建本模块。

## 接入方式

### JVM 启动时挂载

```bash
java -javaagent:/path/to/oAT-agent-core-1.0-SNAPSHOT.jar=appKey=your-app-key \
  -jar your-application.jar
```

`appKey` 必须与平台中登记的应用标识一致，区分大小写。

### 对运行中的 JVM attach

```bash
java -jar /path/to/oAT-agent-core-1.0-SNAPSHOT.jar <pid> "appKey=your-app-key"
```

不传 `<pid>` 时，Agent 会列出可附加的 Java 进程并提示选择。

## 基础配置

配置文件放在 Agent 安装目录的 `conf/` 下：

- `oAT.conf`：通用配置。
- `oAT_<appKey>.conf`：应用级配置，优先级高于通用配置。

```properties
# oAT-service-web 或 oAT-relay 地址
server=127.0.0.1:8899

# 心跳与会话
heartbeatTime=20
sessionTimeout=60

# 日志
log.level=info
# log.path=/your/path/logs/
```

如果目标应用无法直连 `oAT-service-web`，可将 `server` 指向 `oAT-relay`：

```properties
server=127.0.0.1:18089
```

## 采集范围

采集范围越大，运行时开销和上报数据量越大。建议只配置核心业务包。

```properties
# 服务层方法采集，主要入口，多个规则用 & 分隔
conf_service.include=com.example.order.*&com.example.payment.*
conf_service.exclude=com.example.*.test.*
conf_service.includeMethod=
conf_service.excludeMethod=

# 代码栈采集
conf_codeStack.include=com.example.*
conf_codeStack.includeMethod=
conf_codeStack.exclude=
conf_codeStack.excludeMethod=

# URL 排除：支持精确匹配、* 前缀匹配、regex: 正则
exclude.urls=/actuator/*,regex:^/internal/.+$
```

## 中间件采集

推荐优先使用 `sandbox.*` 统一增强开关。开启对应 sandbox 后，旧的 `collect.*` Transformer 会自动跳过，避免重复增强。

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

兼容旧开关示例：

```properties
# collect.HttpServlet=false
# collect.httpRequestParams=false
# collect.httpRequestBody=false
# collect.httpResponseBody=false
# collect.jdbc=false
# collect.redis=false
# collect.feignInvoker=false
# collect.dubboInvoker=false
# collect.rabbitMq=false
# collect.rocketMq=false
# collect.kafkaMq=false
# collect.systemLog=true
```

## 支持的协议和框架

| 类型 | 能力 |
|---|---|
| HTTP | Servlet `javax` / `jakarta`、请求参数、请求体、响应体 |
| HTTP Client | Apache HttpClient v3 / v4、Feign |
| RPC | Dubbo、SOFA-RPC |
| 数据库 | JDBC、ClickHouse JDBC |
| 缓存 | Redis、Redisson |
| MQ | RabbitMQ、RocketMQ、Kafka 生产与消费 |
| 业务代码 | Service 方法、代码栈、系统日志 |
| 异步上下文 | Executor、Scheduled、ForkJoin、CompletableFuture 传播开关 |

## 性能参数

以下参数通过目标应用 JVM 参数设置：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `oAT.jacoco.stack.maxrecursiondepth` | `1` | 最大递归深度 |
| `oAT.jacoco.stack.maxsize` | 自动计算 | 最大堆栈节点数 |
| `oAT.agent.http.max.queue.size` | `8192` | 上报队列大小 |
| `oAT.agent.http.core.pool.size` | `20` | 上报线程池核心线程数 |
| `oAT.agent.http.max.pool.size` | `160` | 上报线程池最大线程数 |
| `oAT.agent.http.base.timeout.ms` | `10000` | HTTP 基础超时 |
| `oAT.agent.http.read.timeout.extra.ms` | `5000` | 读取超时附加值 |

普通业务可使用 `-Xss1m`；深递归或超大方法场景建议使用 `-Xss2m` 到 `-Xss4m`。

## 注意事项

- 修改 Agent 配置后必须重启目标应用。
- 目标容器或 PaaS 不允许自定义 JVM 参数时，无法通过启动参数挂载 Agent。
- 采集范围不要配置为 `com.*` 这类过宽规则。
- 平台服务未启动时，Agent 会在建立会话阶段重试。
- 大上报体场景需同步调整服务端、relay 和网关的请求体大小限制。

