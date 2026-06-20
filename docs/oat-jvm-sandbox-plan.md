# oAT 专用 JVM Sandbox 落地方案

## 目标

为 oAT 自研一个适合精准测试、链路追踪、覆盖率分析的专用 JVM Sandbox。该方案不直接 fork 通用 jvm-sandbox，而是吸收成熟 Java Agent 工程的关键设计，重构现有 `oAT-agent` 为可动态治理、可失败隔离、可模块化扩展的运行时增强内核。

## 参考工程

| 工程 | 可借鉴点 |
|---|---|
| [Alibaba jvm-sandbox](https://github.com/alibaba/jvm-sandbox) | `spy / core / module` 分层、模块生命周期、动态 attach |
| [OpenTelemetry Java Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation) | instrumentation 开关、兼容性检查、自动插桩治理 |
| [SkyWalking Java Agent](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/java-plugin-development-guide/) | Entry / Local / Exit 调用分类、插件测试体系 |
| [Pinpoint Plugin Guide](https://github.com/pinpoint-apm/pinpoint-apm.github.io/blob/main/documents/plugin-dev-guide.md) | 插件注册增强点，由 agent core 统一 transformer |
| [JaCoCo Java Agent](https://www.eclemma.org/jacoco/trunk/doc/agent.html) | 覆盖率探针独立模型、on-the-fly instrumentation |
| [Oracle Instrumentation API](https://docs.oracle.com/javase/8/docs/api/java/lang/instrument/Instrumentation.html) | retransform 限制：不能新增/删除方法和字段 |

## 当前问题

1. `agentmain` 未真正启动 agent，动态 attach 实际不可用。
2. 每个 collect 自己注册 `ClassFileTransformer`，增强顺序和失败隔离不可控。
3. 当前 Javassist `setBody + $agent` 方式会新增方法，不适合动态 retransform。
4. 被增强业务类直接引用 agent 内部类，存在 ClassLoader 污染和卸载困难。
5. 配置命名不统一，README 与代码存在差异。
6. `AttachAgent` 当前启动 Arthas，不是 oAT 自己的 attach 能力。

## 推荐架构

```text
oAT-agent
├── oAT-sandbox-spy        # Bootstrap 可见，只放 OatSpy
├── oAT-sandbox-api        # OatModule / EventWatcher / Matcher / Listener
├── oAT-sandbox-core       # Launcher / ModuleManager / EnhanceManager / Transformer
├── oAT-agent-modules      # http / service / coverage / jdbc / redis / rpc / mq
├── oAT-client-model       # 保留现有 TraceNode / ClientSessionVo
└── oAT-agent-shaded       # 最终 javaagent 包
```

## 核心设计

### Spy 桥

业务类增强后只调用 Bootstrap 可见的 `OatSpy`：

```java
OatSpy.onBefore(namespace, listenerId, loader, className, methodName, desc, target, args);
OatSpy.onReturn(listenerId, invokeId, returnValue);
OatSpy.onThrows(listenerId, invokeId, throwable);
```

`OatSpy` 必须极小、无第三方依赖，只负责事件转发。

### 模块 API

```java
public interface OatModule {
    String id();
    void load(ModuleContext context);
    void active();
    void frozen();
    void unload();
}
```

```java
public interface EventWatcher {
    WatchId watch(ClassMatcher classMatcher,
                  MethodMatcher methodMatcher,
                  EnumSet<EventType> events,
                  EventListener listener);

    void delete(WatchId id);
}
```

### 增强方式

废弃 Javassist `setBody + $agent`，统一改为 ASM inline 注入：

```text
方法入口插入 before
正常 return 前插入 return
异常路径插入 throws
不新增方法
不新增字段
不改变方法签名
```

这样才能同时支持首次类加载和已加载类 retransform。

## 模块分类

| 类型 | oAT 模块 |
|---|---|
| Entry | HTTP Servlet、RPC Provider、MQ Consumer |
| Local | Service 方法 |
| Exit | JDBC、Redis、HTTP Client、Feign、RPC Consumer、MQ Producer |
| Coverage | 独立覆盖率探针模块 |

覆盖率不要强行走普通事件模型，应保留 JaCoCo 风格探针，由 sandbox core 统一调度 transformer。

## 兼容性检查

参考 OpenTelemetry Muzzle，实现简化版 `InstrumentationSpec`：

```java
new InstrumentationSpec("jdbc")
    .requireClass("java.sql.Driver")
    .requireMethod("java.sql.Driver", "connect",
        "(Ljava/lang/String;Ljava/util/Properties;)Ljava/sql/Connection;")
    .skipIfMissing();
```

不满足条件时跳过增强、记录原因、上报状态，不能影响业务进程。

## 服务端改造

保留现有 `/client/login`、`/client/heartbeat`、`/client/upload`。

已新增：

```text
POST /client/sandbox/status
```

后续可扩展：

```text
POST /client/sandbox/config/check
POST /client/sandbox/error
```

状态上报示例：

```json
{
  "sessionId": "xxx",
  "sandboxVersion": "1.0.0",
  "configVersion": 12,
  "modules": [
    {
      "id": "jdbc",
      "status": "ACTIVE",
      "enhancedClassCount": 2,
      "skippedClassCount": 0,
      "lastError": null
    }
  ]
}
```

## 迁移顺序

1. **sandbox core + spy**
   - 实现统一 launcher、单 transformer、模块生命周期、状态上报。
   - 当前已完成启动层、Spy 分发、监听器注册、基础 ASM transformer。

2. **JDBC 模块**
   - 目标稳定，适合作为 ASM inline 和事件模型的第一个验证模块。
   - 当前已完成基础 sandbox 模块，使用 `sandbox.jdbc.enabled=true` 开启。
   - 当前已完成 ClickHouse JDBC sandbox 模块，使用 `sandbox.clickhouse-jdbc.enabled=true` 开启。

3. **ServiceCollect**
   - 验证业务包过滤、方法过滤、Local TraceNode。
   - 当前已完成基础 sandbox 模块，使用 `sandbox.service.enabled=true` 开启。

4. **HttpServletCollect**
   - 验证 TraceSession 根节点、`javax/jakarta`、request/response 包装。
   - 当前已完成基础 Entry 模块，使用 `sandbox.http-servlet.enabled=true` 开启。

5. **Coverage**
   - 验证覆盖率探针与普通事件增强共存。
   - 当前已接入 sandbox 生命周期管理，使用 `sandbox.coverage.enabled=true` 开启；底层保留 JaCoCo 探针 transformer，并已支持 retransform 注册和 daemon 静态扫描线程。

6. **Redis / Feign / HttpClient / Dubbo / MQ**
   - 按使用优先级逐步迁移。
   - 当前已完成 Feign 基础 Exit 模块，使用 `sandbox.feign.enabled=true` 开启。
   - 当前已完成 Apache HttpClient v3/v4 基础 Exit 模块，分别使用 `sandbox.http-client-v3.enabled=true`、`sandbox.http-client-v4.enabled=true` 开启。
   - 当前已完成 Dubbo consumer/provider 模块，使用 `sandbox.dubbo.enabled=true` 开启。
   - 当前已完成 SOFA-RPC consumer/provider 模块，使用 `sandbox.sofa-rpc.enabled=true` 开启。
   - 当前已完成 RabbitMQ、RocketMQ、Kafka producer/consumer 模块，分别使用 `sandbox.mq-producer.enabled=true`、`sandbox.mq-consumer.enabled=true` 开启。
   - 当前已完成 Lettuce Redis 模块，使用 `sandbox.redis.enabled=true` 开启。
   - 当前已完成 Redisson 构造器切点模块，使用 `sandbox.redisson.enabled=true` 开启；为此 sandbox transformer 已支持显式 watch `<init>`。

7. **ThreadPoolCollect**
   - 风险最高，默认关闭。
   - 当前已接入 sandbox 生命周期管理，使用 `sandbox.thread-pool.enabled=true` 开启；底层仍使用专用 JDK 类 transformer。

## 当前落地状态

已落地：

- `premain` / `agentmain` 统一进入 `SandboxLauncher`。
- `AttachAgent` 改为使用 JDK Attach API 加载 oAT agent，不再启动 Arthas。
- `OatSpy` 注入 Bootstrap，业务类只调用 spy 静态入口。
- `SandboxRuntime`、`ModuleManager`、`EventWatcher`、`SandboxTransformer` 已具备模块注册、事件分发、已加载类 retransform。
- 服务端新增 `/client/sandbox/status` 状态接收入口。
- 旧采集器在对应 `sandbox.*.enabled=true` 时跳过注册，避免双 Transformer 增强。
- 核心采集模块已迁移到统一事件路径：HTTP Servlet、Service、JDBC、ClickHouse JDBC、Feign、HttpClient v3/v4、Dubbo、SOFA-RPC、MQ producer/consumer、Redis、Redisson。
- SystemLog、ThreadPool 已接入 sandbox 生命周期管理；SystemLog 无字节码增强，ThreadPool 底层保留专用 JDK 类 transformer。

保留回退：

- `collect.*` 旧开关仍可用；未开启对应 `sandbox.*` 时，继续走旧采集器。
- Coverage、ThreadPool 属于特殊 transformer，已纳入 sandbox 生命周期管理，但不强制并入普通 BEFORE/RETURN/THROWS 事件模型。

推荐试运行开关：

```properties
sandbox.http-servlet.enabled=true
sandbox.service.enabled=true
sandbox.jdbc.enabled=true
sandbox.clickhouse-jdbc.enabled=true
sandbox.feign.enabled=true
sandbox.http-client-v3.enabled=true
sandbox.http-client-v4.enabled=true
sandbox.dubbo.enabled=true
sandbox.sofa-rpc.enabled=true
sandbox.mq-producer.enabled=true
sandbox.mq-consumer.enabled=true
sandbox.redis.enabled=true
sandbox.redisson.enabled=true
sandbox.coverage.enabled=true
sandbox.system-log.enabled=true
sandbox.thread-pool.enabled=true
```

## 不兼容项

建议直接改掉：

- 不兼容旧的 `$agent` 方法增强方式。
- 不再用 Arthas 作为 oAT attach 实现。
- `agentmain` 必须进入 oAT sandbox launcher。
- `oAT-agnet-shaded` 目录名存在拼写问题；当前不影响构建，建议后续低风险窗口单独重命名并同步 CI/文档引用。
- 配置名统一，废弃混乱命名。
- 短期不支持第三方外部模块 jar。
- 短期不做通用 sandbox 命令控制台。

## 最终结论

oAT 应建设的是专用 JVM Sandbox，而不是通用诊断平台。

核心路线：

```text
借鉴 jvm-sandbox 的 spy/core/module
借鉴 OpenTelemetry 的 instrumentation 治理
借鉴 SkyWalking 的调用分类
借鉴 Pinpoint 的插件注册模型
借鉴 JaCoCo 的覆盖率探针模型
```

最终目标：

```text
统一增强
动态启停
失败隔离
稳定采集
服务于精准测试、链路追踪和覆盖率分析
```
