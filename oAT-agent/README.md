# oAT-agent

oAT-agent 是 oAccurateTest 平台的探针模块，以 JavaAgent 方式无侵入地注入目标 Java 应用，采集运行时链路数据并上报到 oAT-service-web。

---

## 模块结构

```
oAT-agent/
├── oAT-client-model/   # Agent 与服务端共用的数据模型（Java 8）
├── oAT-agent-core/     # 字节码增强、链路采集、数据上报（Java 8）
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

产出物：`oAT-agnet-shaded/target/oAT-agent-core-1.0-SNAPSHOT.jar`

---

## 接入目标应用

在目标应用的 JVM 启动参数中添加 `-javaagent`：

```bash
java -javaagent:/path/to/oAT-agent-core-1.0-SNAPSHOT.jar=appKey=your-app-key \
     -jar your-application.jar
```

`appKey` 必须与平台中登记的应用标识一致。

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

**中间件采集开关**（默认全部开启，按需关闭）：

```properties
# collect.HttpServlet=false
# collect.httpRequestParams=false
# collect.httpRequestBody=false
# collect.httpResponseBody=false
# collect.systemLog=false
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
| HTTP Servlet（javax / jakarta） | `collect.HttpServlet` |
| HTTP 请求参数 / 请求体 / 响应体 | `collect.httpRequestParams` 等 |
| Apache HttpClient v3 / v4 | `collect.httpClientV3 / V4` |
| Feign | `collect.feignInvoker` |
| Dubbo（调用方 / 提供方） | `collect.dubboInvoker / dubboReceive` |
| SOFA-RPC（Consumer / Provider） | `collect.sofaProviderInvoker` 等 |
| JDBC（MySQL、通用） | `collect.jdbc` |
| ClickHouse JDBC | `collect.clickHouseJdbc` |
| Redis（Lettuce / Jedis） | `collect.redis` |
| Redisson | `collect.redisson` |
| RabbitMQ（发送 / 接收） | `collect.rabbitMq / rabbitMqReceive` |
| RocketMQ（生产者 / 消费者） | `collect.rocketMq / rocketMqReceive` |
| Kafka（生产者 / 消费者） | `collect.kafkaMq / kafkaMqReceive` |
| 业务日志 | `collect.systemLog` |

---

## 注意事项

- 配置文件修改后**必须重启目标应用**才能生效。
- `appKey` 必须与平台应用配置中的标识完全一致，大小写敏感。
- 采集范围不宜过大，整包采集（如 `com.*`）会显著增加请求耗时和数据量。
- 若目标容器（如 Kubernetes、PaaS 平台）不允许自定义 JVM 参数，则无法接入 Agent。
- 平台服务（`oAT-service-web`）需先于目标应用启动，否则 Agent 在建立会话阶段会重试连接。
- Tomcat 默认 POST 限制为 2MB，数据量较大时需在平台侧配置 `server.tomcat.max-http-post-size=100MB`。
