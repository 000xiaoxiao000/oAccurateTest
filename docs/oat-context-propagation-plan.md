# oAT Context Propagation 落地方案

## 目标

将旧 `ThreadPoolCollect` 改造为 sandbox core 可治理的上下文传播能力。现有 HTTP、RPC、MQ、DB、Redis 等 `collect.*` 采集器体系不迁移、不隔离，继续保留并做 JDK6 兼容改造；只有线程池异步传播从特殊 transformer 路径切到统一的 `sandbox.context-propagation` 模块。

该能力用于解决目标应用中的异步调用上下文传播，包括 HTTP、RPC、MQ、Job 等入口创建的 trace / coverage context 在进程内线程池、定时任务、异步任务中的传递。

## 关键结论

- `ThreadPoolCollect` 可以不要，线程池上下文传播应由 sandbox core 管理。
- context-propagation 只传播当前线程已有 context，不创建 HTTP/RPC/MQ root。
- HTTP 不能作为唯一入口假设，RPC provider、MQ consumer、Job 都必须能作为 root entry。
- 目标应用可能运行在 JDK6，因此 agent runtime、bootstrap helper、shaded 依赖必须是 JDK6 可加载的 classfile。
- 不采用迁移或隔离旧 collector 的路线；旧 collector 保留，改造其 JDK6 兼容性和运行时依赖。

## 已实施状态

### JDK6 兼容前置

已完成：

- agent 编译目标改为按被测应用探针包选择：默认 `1.7`，`probe-jdk6` profile 使用 `1.6`。
- `oAT-agent/pom.xml`、`oAT-agent-core/pom.xml`、`oAT-client-model/pom.xml`、`oAT-agnet-shaded/pom.xml` 均已改为 `${maven.compiler.source}` / `${maven.compiler.target}` 属性驱动。
- `oAT-agent` reactor 顺序改为 `client-model -> agnet-shaded -> agent-core`，`agent-core` 不再通过 `systemPath` 读取旧 `lib/oAT-agent-shaded-1.0.0.jar`，避免 profile 构建时误用旧 shaded jar。
- shaded Javassist 从 `3.29.0-GA` 降为 `3.23.2-GA`，避免 JDK6 无法加载 major 52 class。
- shaded 依赖已收窄到实际使用的 Javassist、ASM、Commons Codec；移除未使用的 HttpClient5、zt-zip、slf4j，避免 JDK6 探针包混入 Java8+ classfile。
- shaded ASM 默认 `9.7`；`probe-jdk6` profile 使用 ASM `5.2`、Commons Codec `1.10`。
- agent core 中显性的 JDK7+ 语法/API 已降级：try-with-resources、multi-catch、diamond、`StandardCharsets`、`java.nio.file.Files`、`java.time.Instant`、`java.lang.invoke`。
- ASM API 选择已从硬编码 `Opcodes.ASM9` 改为运行时探测，JDK6 探针包可使用低版本 ASM，现代探针包继续使用 ASM9。
- `collect.threadPool` fallback 已删除，旧 `ThreadPoolCollect` 文件已删除。

仍需验证：

- JDK17+ 不支持 `target=1.6`，JDK6 探针包必须使用 JDK8/7/6 编译器执行 `-Pprobe-jdk6` 构建。
- 需要重新构建 `oAT-agent-shaded-1.0.0.jar`，并用 `javap -verbose` 确认 Javassist/agent helper classfile major <= 50。
- 需要用现有 collector 样例验证 Javassist 3.23.2-GA API 对 `AgentByteBuild` 和各 collector 的兼容性。
- JDK6 探针包还必须确认 shaded jar 中所有 classfile major <= 50；现代 JDK 探针包可继续使用 ASM 9.x。

构建方式：

```bash
# 默认：JDK7+ 探针包，本地 JDK17 可编译
mvn -f oAT-agent/pom.xml clean package

# JDK6 被测应用：必须用 JDK8/7/6 编译器，JDK17 不能构建 target 1.6
mvn -f oAT-agent/pom.xml -Pprobe-jdk6 clean package

# JDK8 被测应用
mvn -f oAT-agent/pom.xml -Pprobe-jdk8 clean package
```

### Sandbox Core

已新增：

```text
oAT-agent-core/src/main/java/com/oAT/agent/sandbox/core/BootstrapEnhanceManager.java
oAT-agent-core/src/main/java/com/oAT/agent/sandbox/core/BootstrapEnhanceDefinition.java
oAT-agent-core/src/main/java/com/oAT/agent/sandbox/core/BootstrapEnhanceStatus.java
oAT-agent-core/src/main/java/com/oAT/agent/sandbox/core/BootstrapMethodEnhancer.java
oAT-agent-core/src/main/java/com/oAT/agent/sandbox/core/BootstrapBridgeInstaller.java
```

职责：

- 由 sandbox core 统一注册 JDK/bootstrap 增强定义。
- 按 JDK 版本筛选增强点。
- 管理 bootstrap transformer。
- 对已加载 JDK 类执行 retransform。
- 记录增强状态：`REGISTERED`、`ACTIVE`、`FROZEN`、`ERROR`。
- frozen / unload 时 fail-open，不影响业务线程池执行。

### Context Propagation 模块

已新增：

```text
oAT-agent-core/src/main/java/com/oAT/agent/bootstrap/OatAsyncBridge.java
oAT-agent-core/src/main/java/com/oAT/agent/bootstrap/OatContextBridge.java
oAT-agent-core/src/main/java/com/oAT/agent/sandbox/modules/context/AgentContextBridge.java
oAT-agent-core/src/main/java/com/oAT/agent/sandbox/modules/context/ContextPropagationSandboxModule.java
```

当前增强点：

```text
java.util.concurrent.ThreadPoolExecutor.execute(Runnable)
java.util.concurrent.AbstractExecutorService.submit(Runnable)
java.util.concurrent.AbstractExecutorService.submit(Runnable, Object)
java.util.concurrent.AbstractExecutorService.submit(Callable)
java.util.concurrent.AbstractExecutorService.invokeAll(Collection)
java.util.concurrent.AbstractExecutorService.invokeAll(Collection, long, TimeUnit)
java.util.concurrent.AbstractExecutorService.invokeAny(Collection)
java.util.concurrent.AbstractExecutorService.invokeAny(Collection, long, TimeUnit)
java.util.concurrent.ScheduledThreadPoolExecutor.schedule(Runnable, long, TimeUnit)
java.util.concurrent.ScheduledThreadPoolExecutor.schedule(Callable, long, TimeUnit)
java.util.concurrent.ScheduledThreadPoolExecutor.scheduleAtFixedRate(Runnable, long, long, TimeUnit)
java.util.concurrent.ScheduledThreadPoolExecutor.scheduleWithFixedDelay(Runnable, long, long, TimeUnit)
org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.execute(Runnable)
org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.execute(Runnable, long)
org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.submit(Runnable)
org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.submit(Callable)
org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.submitListenable(Runnable)
org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.submitListenable(Callable)
org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.schedule(Runnable, Date)
org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.schedule(Runnable, Trigger)
```

当前实现方式：

- JDK 类只调用 bootstrap 可见的 `OatAsyncBridge.wrap(...)`。
- `OatAsyncBridge` fail-open；bridge 未初始化、frozen、异常时直接返回原始任务。
- agent 侧 `AgentContextBridge` 回调 `AgentContext.wrap(Runnable/Callable)`。
- wrapper 执行时 restore 上下文，并在 finally 中 rollback。
- wrapper 在提交时注册 pending async task，在执行完成、提交阶段抛异常时通过 `OatAsyncBridge.release(...)` 做一次性补偿释放，避免入口永久等待异步任务。
- `invokeAll` / `invokeAny` 复制 `Collection<Callable>` 后包装元素，不修改调用方传入集合。
- `SandboxStatusReporter` 已在 `/client/sandbox/status` 原有 JSON 中追加 `bootstrapEnhancements`，包含 moduleId、className、methodName、descriptor、state、errorMessage、updatedAt。

## 配置

新增配置：

```properties
sandbox.context-propagation.enabled=true
sandbox.context-propagation.executor=true
sandbox.context-propagation.scheduled=true
sandbox.context-propagation.spring=true
sandbox.context-propagation.forkjoin=false
sandbox.context-propagation.completable-future=false
```

已删除配置：

```properties
collect.threadPool
sandbox.thread-pool.enabled
```

## 多入口模型

入口模块统一负责 root context 生命周期：

```text
HTTP Servlet Entry
RPC Provider Entry
MQ Consumer Entry
Job Entry
Manual Entry
```

规则：

- 有上游 context：恢复为 remote child root。
- 无上游 context：创建本地 root。
- 设置 `rootEntryType`、`rootEntryName`、`sessionId`、`caseId`。
- 初始化 coverage context。
- 入口结束时 close / flush。
- context-propagation 只传播已有 context；无 context 的后台任务不创建 trace。
- `AgentContext` 传播 trace、coverage collector、async task count、async completion listener；最后一个异步任务结束时由入口 wrapper 触发延迟 coverage flush。

已接入异步 coverage 延迟汇总的入口：

```text
HTTP Servlet: HttpServletCollect / HttpServletSandboxModule
Service/RPC: ServiceCollect
SOFA RPC Provider: SofaServerCollect
Dubbo Provider: DubboReceiveCollect
Kafka Consumer: KafkaMqReceiveCollects
RabbitMQ Consumer: RabbitMqReceiveCollects
RocketMQ Consumer: RocketMqReceiveCollects
```

RPC/MQ receive 无上游 trace header 时创建本地 root，`traceNodeId=0`；有上游 header 时继续作为 remote child。

针对 `LoggingRunnable: run end ... jettoheader=未知header` 但覆盖率未上报的问题，当前修复点：

- sandbox HTTP 和旧 `HttpServletCollect` 都会在入口开始时初始化 `coverageCollector`、`activeAsyncTaskCount`、`asyncCompletionListener`。
- HTTP/RPC/MQ 入口主线程结束时，如果 `activeAsyncTaskCount > 0`，不立即 `collectSnapshots()` / `saveNode()` / `closeTraceSession()`，而是将入口 wrapper 标记为 `deferred` 并从当前线程移除 context。
- 最后一个异步任务执行完成时恢复提交时的 `AgentContext`，调用入口 wrapper 的 `onAsyncComplete()`，再统一 `collectSnapshots()`、设置 `CodeNodeBean.codeNodes`、`saveNode()`、关闭 session。
- `ServiceCollect` / `SofaServerCollect` 如果发现当前线程已经存在入口传播来的 `CoverageCollector`，不会再重开 collector、不会覆盖入口 async 计数器和 completion listener。这个点是异步线程执行到 service/runner 后 coverage 未补报的关键修正。
- 所有入口 wrapper 的 `onAsyncComplete()` 都只在 `deferred=true` 时保存节点，防止异步任务比入口主线程更早结束时提前上传未完成节点。
- JDK 线程池增强的 `execute` / `submit` / `schedule` / `invokeAll` / `invokeAny` 如果提交阶段抛异常，会释放已注册的 pending async task，防止计数泄漏。
- Spring `ThreadPoolTaskExecutor` / `ThreadPoolTaskScheduler` 由同一个 context-propagation 模块治理，作为 JDK 线程池增强未命中或框架先包装任务时的兜底，不恢复旧 `ThreadPoolCollect`。
- debug 日志中应能看到 `[AgentContext] register async task`、`complete async task`、`last async task completed`。如果 HTTP/RPC/MQ 入口结束前没有 register 日志，说明线程池增强没有命中，需要查看 `/client/sandbox/status` 的 `bootstrapEnhancements` 是否 ACTIVE/ERROR。

Exit 模块负责跨进程注入：

```text
HTTP Client
Feign
RPC Consumer
MQ Producer
```

Entry 模块负责跨进程恢复：

```text
HTTP Servlet
RPC Provider
MQ Consumer
```

建议统一字段：

```text
oat-trace-id
oat-parent-node-id
oat-session-id
oat-case-id
oat-entry-type
```

## oAT-service-web 配合改造

当前 `oAT-service-web` 可以通过 `/client/upload` 接收任意 `TraceNode` 子类，并通过 `TraceNodeIndex` 保存 Dubbo、SOFA、RabbitMQ、RocketMQ、Kafka 等节点。但服务端仍有多处 HTTP root 假设，不改会导致 RPC/MQ 作为入口时链路上传成功但展示、快照、图谱或覆盖率合并失败。

必须改造：

- `TraceNodeCache.put()`：root 节点不能只接受 `HttpTraceNode`，任意入口 root 都要创建实时 `TraceItemVo`。
- `TraceItemVo`：增加或复用 `entryType`、`entryName`、`displayName`、`clientIp`、`appId`、`status`。
- `SnapshotServiceImpl.buildTraceSummary()`：先获取 root node，再按节点类型填通用入口字段，不能只找 HTTP root。
- `TraceSummaryIndex` 和 `trace_summary_template.json`：增加 `entryType`、`entryName`、`entryProtocol`、`entryAppId`、`entryAppName`、`entryClientIp`、`entryTopic`、`entryInterface`、`entryMethod`。
- `TraceNodeIndex` 和 `trace_node_template.json`：增加通用 entry 字段。
- `GraphViewHelp` / `TraceGraphParse`：去掉 root 必须是 `HttpTraceNode` 的断言和强转，新增通用 `EntryGraphNode`。
- `CoverageServiceImpl.mergeSnapshotTraceCoverage()`：fallback 扫描所有 `CodeNodeBean` 节点，不能只读 `HttpTraceNode.codeNodes`。
- 新增独立覆盖率上报接口：`POST /client/coverage/upload`，agent 上报固化后的 `CoverageUploadVo`，服务端按 `traceId` 写入 `CoverageStorage`。
- `/client/sandbox/status`：保留字符串兼容，同时允许 context-propagation 上报结构化增强状态。

涉及文件：

```text
oAT-service-web/src/main/java/com/oAT/web/service/TraceNodeCache.java
oAT-service-web/src/main/java/com/oAT/web/service/entity/TraceItemVo.java
oAT-service-web/src/main/java/com/oAT/web/service/impl/SnapshotServiceImpl.java
oAT-service-web/src/main/java/com/oAT/web/esDao/entity/TraceSummaryIndex.java
oAT-service-web/src/main/java/com/oAT/web/esDao/entity/TraceNodeIndex.java
oAT-service-web/src/main/java/com/oAT/web/control/GraphViewHelp.java
oAT-service-web/src/main/java/com/oAT/web/control/TraceGraphParse.java
oAT-service-web/src/main/java/com/oAT/web/control/entity/ClientGraphNode.java
oAT-service-web/src/main/java/com/oAT/web/control/ClientSessionControl.java
oAT-service-web/src/main/java/com/oAT/web/coverage/CoverageStorage.java
oAT-service-web/src/main/java/com/oAT/web/service/impl/CoverageServiceImpl.java
oAT-service-web/src/main/java/com/oAT/web/service/impl/SystemSnapshotServiceImpl.java
oAT-service-web/src/main/resources/elasticsearch/trace_summary_template.json
oAT-service-web/src/main/resources/elasticsearch/trace_node_template.json
oAT-service-web/src/main/resources/db/elasticsearch/trace_node_template.json
```

## 分阶段计划

### 阶段 1：JDK6 兼容基线

状态：已完成源码层第一轮降级，待 toolchain 编译验证。

验收：

- JDK6/7 toolchain 可编译 agent core。
- shaded jar 中 Javassist/ASM/helper 均可被 JDK6 加载。
- 旧 collector 在样例应用中仍可正常增强。

### 阶段 2：Sandbox Context Propagation

状态：已实施 executor / scheduled 第一版。

已完成：

- `invokeAll` / `invokeAny` 集合包装。
- context-propagation 状态上报接入 `SandboxStatusReporter`。
- `ThreadPoolSandboxModule` 文件已删除。
- `AbstractByteTransformCollect` 中 thread-pool bootstrap 白名单已删除。

验收：

- HTTP 入口提交线程池任务，异步 coverage 归属同一 session。
- RPC provider 入口提交线程池任务，异步 coverage 归属同一 session。
- MQ consumer 入口提交线程池任务，异步 coverage 归属同一 session。
- Spring `ThreadPoolTaskExecutor` 提交任务，HTTP/RPC/MQ 入口能等待异步任务结束后再 coverage upload。
- 无入口 context 的后台任务不产生错误 context。

### 阶段 3：Coverage 上报解耦

已完成：

- coverage event / batch DTO 固化 `sessionId`、`caseId`、`traceId`、`entryType`、`entryName`、`appId`、`codeNodes`。
- service-web 新增 coverage batch 接收接口。
- `CoverageServiceImpl` 不再依赖 HTTP root fallback。
- `SystemSnapshotServiceImpl` 覆盖率计算优先读取 `CoverageStorage`，再回退所有 `CodeNodeBean`。
- `SnapshotApiControl` 覆盖率统计、代码层和关系构建优先读取 `CoverageStorage`，再回退 `CodeNodeBean`，不再要求 root 是 HTTP。
- `DubboRemoteTraceNode`、`KafkaMQRemoteTraceNode`、`RabbitMQRemoteTraceNode`、`RocketMQConsumerTraceNode` 已实现 `CodeNodeBean`，可通过 `TraceSession.saveNode()` 统一触发独立 coverage upload。

说明：

- 当前 agent 复用已有异步 HTTP 传输线程池上传 coverage batch，没有新增独立 daemon queue；后续只有在吞吐压测证明 `/client/coverage/upload` 需要单独限流时再拆。

### 阶段 4：service-web 多入口展示与索引

已完成：

- 实时列表支持 RPC/MQ root。
- snapshot summary 支持通用 entry。
- trace graph root 支持通用 entry node。
- ES template 增加通用入口字段。

### 阶段 5：JDK7/JDK8+ 扩展

待完成：

- JDK7+ 支持 ForkJoin。
- JDK8+ 支持 CompletableFuture async。
- 按运行时 JDK 版本动态注册增强点。

## 测试矩阵

JDK：

```text
6
7
8
11
17
21
最新 LTS
```

入口：

```text
HTTP Servlet
Dubbo Provider
SOFA Provider
RabbitMQ Consumer
RocketMQ Consumer
Kafka Consumer
Job
```

异步方式：

```text
ThreadPoolExecutor.execute
ExecutorService.submit
ScheduledThreadPoolExecutor.schedule
invokeAll / invokeAny
ForkJoinPool
CompletableFuture
```

验证点：

- traceId 不丢。
- caseId / sessionId 不丢。
- parentNodeId 正确。
- 异步任务 finally 后线程上下文恢复。
- 无入口 context 时不误建 root。
- 模块 frozen 后不继续传播新 context。
- 业务异常不被 wrapper 吞掉。
- wrapper 自身异常不影响业务执行。

## 风险与处理

| 风险 | 处理 |
|---|---|
| JDK 类增强失败 | fail-open，记录状态，不影响业务 |
| 新 JDK 模块限制 | 不反射 JDK 私有字段，只改公开方法参数 |
| 重复包装 | `AgentContext.wrap` 已判断已包装任务 |
| 上下文泄漏 | wrapper finally rollback |
| 不可变 Collection | `invokeAll` / `invokeAny` 创建新集合 |
| 长周期定时任务上下文陈旧 | schedule 时捕获提交时上下文，符合异步语义 |
| JDK17+ 不支持 target 1.6 | 默认构建使用 target 1.7；JDK6 被测应用使用 `-Pprobe-jdk6` 且必须由 JDK8/7/6 编译器构建 |
| JDK6 classfile 不兼容 | JDK6 探针包对 agent core/shaded/helper 均做 classfile major <= 50 验证 |
| Javassist 降版本 API 差异 | 用现有 collector 样例逐项验证，不迁移、不隔离 |

## 最终状态

完成后应达到：

- JDK6 到新 JDK 可运行。
- HTTP / RPC / MQ / Job 多入口统一。
- 线程池传播由 sandbox core 治理。
- 覆盖率上报与业务线程池解耦。
- `ThreadPoolCollect` 已删除，不再参与运行路径。
- JDK 类增强状态可观测、可冻结、可 fail-open。
