# oAT-ai

`oAT-ai` 是 oAccurateTest 的 AI 智能分析模块。它基于 LangChain4j 实现 LLM 接入、工具调用、对话记忆、语义缓存、反馈学习、运行时模型切换和工具调用 fallback，并以 jar 方式被 `oAT-service-web` 加载。

本模块不直接启动进程，真实平台数据由 `oAT-service-web` 中的 `AgentDataProviderImpl` 注入。

## 模块结构

```text
oAT-ai/src/main/java/com/oAT/
├── ai/
│   ├── agent/
│   │   ├── tools/       # 覆盖率、缺陷、链路、快照、测试推荐等工具
│   │   ├── cache/       # 工具缓存、语义缓存、增强缓存
│   │   ├── fallback/    # 模型返回 tool-like JSON 时的兜底执行和参数绑定
│   │   ├── parallel/    # 工具并行执行
│   │   ├── AIAgent.java
│   │   ├── AIAgentService.java
│   │   ├── AIPromptGuideBuilder.java
│   │   ├── AIToolProviderRouter.java
│   │   ├── AgentContext.java
│   │   ├── AgentDataProvider.java
│   │   ├── ConversationMemoryService.java
│   │   ├── DynamicLLMSwitcher.java
│   │   ├── FeedbackPersistenceService.java
│   │   └── ToolRecommender.java
│   ├── config/          # 自动装配与 ai.* / ai.enhanced.* 配置
│   └── service/         # LLMService 与实现
└── agent/
    └── AISelfLearningService.java
```

## 技术栈

| 技术 | 用途 |
|---|---|
| Java 17 | 编译与运行要求 |
| LangChain4j 1.12.2 | AI Service、工具调用、OpenAI/Ollama 接入 |
| Spring Boot AutoConfigure | 被 `oAT-service-web` 加载时自动注册 Bean |
| Redis | 语义缓存、会话状态和反馈学习依赖的外部缓存 |

## 构建

```bash
cd oAT-service/oAT-ai
mvn clean install
```

产物：

```text
target/oAT-ai-1.0-SNAPSHOT.jar
```

`oAT-service-web` 单独构建前，必须先把本模块安装到本地 Maven 仓库。

## 运行方式

本模块不独立运行。`oAT-service-web` 启动后会：

1. 读取 `application.properties` 中的 `ai.*` 和 `ai.enhanced.*` 配置。
2. 通过 `AIAutoConfiguration` 注册 LLM、Agent、工具、缓存和增强能力。
3. 通过 `AgentDataProvider` 从平台读取项目、应用、快照、链路、覆盖率、静态源码等数据。
4. 由 `AIInteractiveApiControl` 对外提供 `/api/projects/{projectId}/ai/*` 接口。

## 核心组件

| 组件 | 职责 |
|---|---|
| `AIAgent` | LangChain4j AI Service，定义普通对话、页面上下文对话、项目概览和图片对话。 |
| `AIAgentService` | Agent 调用编排，处理上下文、工具调用、缓存、fallback 和返回内容。 |
| `LLMService` / `LLMServiceImpl` | provider、模型、API Key、超时等底层 LLM 调用封装。 |
| `DynamicLLMSwitcher` | 支持运行时模型/provider 切换。 |
| `ConversationMemoryService` | 控制对话记忆轮次。 |
| `SemanticCacheService` | 语义缓存命中和相似度判断。 |
| `CachedToolExecutor` | 工具调用缓存执行器。 |
| `ParallelToolExecutor` | 多工具并行执行。 |
| `FallbackToolExecutionService` | 当模型返回工具 JSON 但未走标准 Function Calling 时，解析并兜底执行工具。 |
| `ToolRecommender` | 根据上下文和问题推荐工具。 |
| `AIPromptGuideBuilder` | 注入动态提示词和学习指南。 |
| `FeedbackPersistenceService` | 反馈数据持久化入口。 |
| `AISelfLearningService` | 周期性反馈学习、知识命中和动态引导。 |

## 内置工具

| 工具类 | 能力 |
|---|---|
| `CoverageTool` | 覆盖率报告、趋势、低覆盖项、方法/类覆盖数据查询。 |
| `CoverageWorkflowTool` | 覆盖率生成流程、任务进度、报告下载、Git 配置检查。 |
| `BugDetectTool` | 基于真实源码进行类级和方法级缺陷检测。 |
| `DefectStatisticsTool` | 缺陷、异常、错误请求统计。 |
| `PerformanceAnalysisTool` | 性能相关分析入口；无可靠指标时不做在线状态推断。 |
| `CallChainAnalysisTool` | 单链路、多链路、方法调用链和 URL 调用模式分析。 |
| `CallChainCompareTool` | 正常/异常链路、版本链路、覆盖范围差异对比。 |
| `CodeQualityTool` | 代码质量、复杂度和风险分析。 |
| `CodeRelationTool` | 接口、类、方法调用关系和代码图谱查询。 |
| `SnapshotTool` | 系统快照、个人快照和快照详情查询。 |
| `TraceQueryTool` | 链路列表、链路节点和调用详情查询。 |
| `TestcaseRecommendationTool` | 基于覆盖率、链路和变更推荐测试用例。 |
| `AppStatusTool` | 应用列表、在线状态和探针状态查询。 |
| `ProjectInfoTool` | 项目信息、应用概览和统计查询。 |

## 配置

所有配置写在 `oAT-service-web/src/main/resources/application.properties` 中。

### LLM 基础配置

```properties
ai.llm.enabled=true
ai.llm.provider=deepseek
ai.llm.base-url=https://api.deepseek.com
ai.llm.api-key=${AI_LLM_API_KEY:your-api-key}
ai.llm.model=${AI_LLM_MODEL:deepseek-chat}
ai.llm.max-tokens=8192
ai.llm.temperature=0.7
ai.llm.timeout=300
ai.llm.log-requests=false
ai.llm.log-responses=false
ai.llm.system-prompt-prefix=你是一个专业的代码覆盖率分析助手...
```

支持的 provider：

| provider | 说明 |
|---|---|
| `openai` | OpenAI API。 |
| `deepseek` | DeepSeek API。 |
| `ollama` | 本地 Ollama，无需 API Key。 |
| `custom` | 兼容 OpenAI 协议的自定义服务。 |

模型需要支持 Function Calling / Tools。若模型不稳定返回标准工具调用，本模块会尝试 fallback 解析 tool-like JSON 并执行安全候选工具。

### 增强配置

```properties
ai.enhanced.semantic-cache.enabled=true
ai.enhanced.semantic-cache.threshold=0.85
ai.enhanced.conversation.max-rounds=20
ai.enhanced.self-learning.enabled=true
ai.enhanced.self-learning.interval-hours=6
ai.enhanced.self-learning.knowledge-hit-enabled=true
ai.enhanced.self-learning.knowledge-hit-threshold=0.7
ai.enhanced.self-learning.dynamic-guide-enabled=true
ai.enhanced.feedback.retention-days=30
```

### 页面上下文路由

```properties
ai.interactive.route.coverage.keywords=覆盖率页,coverage,覆盖率详情,覆盖率报告
ai.interactive.route.trace.keywords=监控页,monitor,调用链页,链路页
ai.interactive.route.snapshot.keywords=快照页,snapshot,我的快照,快照列表
ai.interactive.route.app.keywords=应用页,应用中心,app/list,app/online
ai.interactive.route.code-relation.keywords=代码关系,类关系,callgraph,关系图
```

## 对外入口

AI HTTP 接口位于 `oAT-service-web`：

```text
GET  /api/projects/{projectId}/ai/context
POST /api/projects/{projectId}/ai/ask
POST /api/projects/{projectId}/ai/session-state
POST /api/projects/{projectId}/ai/session-state/clear
```

`ask` 支持页面上下文、会话状态和流式响应能力，具体请求体由 `oAT-service-web` 的 `AIInteractiveApiPayloads` 定义。

## 扩展工具

新增工具时，优先放在 `com.oAT.ai.agent.tools` 或 `oAT-service-web` 中可被注入的 Spring Bean 中，并使用 LangChain4j `@Tool` 描述能力。

```java
@Component
public class MyCustomTool {

    @Tool("描述这个工具的能力，让 AI 知道何时调用它")
    public String analyze(@P("参数说明") String input) {
        return "分析结果";
    }
}
```

扩展后需要确认：

- 工具 Bean 能被 Spring 扫描或手动注册。
- 工具描述清楚说明适用场景。
- 参数名和 `@P` 描述足够明确，便于 Function Calling 和 fallback 参数绑定。
- 返回值不要暴露平台内部源码、配置、密钥或不必要的实现细节。

## 注意事项

- AI 分析质量依赖平台数据完整性，快照、链路、覆盖率、版本和静态源码需要先入库。
- 语义缓存和反馈学习依赖 Redis；Redis 不可用时应降级为普通对话和工具调用。
- 本地 Ollama 或自定义 provider 响应较慢时，建议把 `ai.llm.timeout` 设置为 300 秒或更高。
- 工具回答必须基于真实工具数据，不能根据应用在线状态、覆盖率节点或页面上下文虚构调用关系。
- API Key 建议通过环境变量注入，不要把真实密钥提交到仓库。
