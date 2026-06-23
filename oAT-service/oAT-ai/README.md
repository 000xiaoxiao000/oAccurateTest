# oAT-ai

中文 | [English](#english)

oAT-ai 是 oAccurateTest 平台的 AI 智能分析模块，基于 LangChain4j 框架构建，提供对话式 AI 助手、多工具编排、语义缓存、对话记忆管理等能力，作为 `oAT-service-web` 的依赖模块运行。

---

## 模块结构

```
oAT-ai/src/main/java/com/oAT/
├── ai/
│   ├── agent/
│   │   ├── tools/          # AI 工具集（每个工具对应一个分析能力）
│   │   ├── cache/          # 语义缓存（EnhancedToolCallCache、SemanticCacheService）
│   │   ├── parallel/       # 工具并行执行器
│   │   ├── AIAgent.java              # 核心 AI Agent，工具注册与调度
│   │   ├── AIAgentService.java       # Agent 服务接口
│   │   ├── AgentContext.java         # 请求上下文（项目、应用、用户等）
│   │   ├── AgentDataProvider.java    # 数据提供接口（由 service-web 实现）
│   │   ├── ConversationMemoryService.java  # 对话记忆管理
│   │   ├── DynamicLLMSwitcher.java   # 运行时模型切换
│   │   ├── FeedbackPersistenceService.java # 用户反馈持久化
│   │   └── ToolRecommender.java      # 工具推荐
│   ├── config/
│   │   ├── AIAutoConfiguration.java  # Spring Boot 自动装配
│   │   ├── AIConfig.java             # 基础 LLM Bean 配置
│   │   ├── AIConfigProperties.java   # 配置属性绑定
│   │   └── AIEnhancedConfig.java     # 增强功能配置（缓存、记忆、学习）
│   └── service/
│       ├── LLMService.java           # LLM 调用接口
│       └── impl/LLMServiceImpl.java
└── agent/
    └── AISelfLearningService.java    # 自学习（从反馈数据中提炼知识）
```

---

## 构建

`oAT-ai` 作为 jar 被 `oAT-service-web` 依赖，需先于 `oAT-service-web` 构建：

```bash
cd oAT-service/oAT-ai
mvn clean install
```

---

## 内置 AI 工具

每个工具对应一类分析能力，由 `AIAgent` 统一注册，根据用户问题自动路由调用：

| 工具类 | 分析能力 |
|---|---|
| `CoverageTool` | 覆盖率报告查询与分析 |
| `CoverageWorkflowTool` | 覆盖率生成流程指引 |
| `BugDetectTool` | 缺陷检测与定位 |
| `DefectStatisticsTool` | 缺陷统计与趋势分析 |
| `PerformanceAnalysisTool` | 性能瓶颈识别 |
| `CallChainAnalysisTool` | 调用链路分析 |
| `CallChainCompareTool` | 跨版本调用链对比 |
| `CodeQualityTool` | 代码质量评估 |
| `CodeRelationTool` | 代码调用关系分析 |
| `SnapshotTool` | 系统快照查询 |
| `TraceQueryTool` | 链路节点查询 |
| `TestcaseRecommendationTool` | 测试用例推荐 |
| `AppStatusTool` | 应用在线状态查询 |
| `ProjectInfoTool` | 项目基本信息查询 |

---

## 配置

所有配置在 `oAT-service-web` 的 `application.properties` 中设置。

### 基础 LLM 配置

```properties
# 启用 AI
ai.llm.enabled=true

# 模型提供商：ollama / openai / deepseek / custom
ai.llm.provider=deepseek

# API 地址
# OpenAI: https://api.openai.com
# Ollama 本地: http://localhost:11434
# DeepSeek: https://api.deepseek.com
ai.llm.base-url=https://api.deepseek.com

# API Key（Ollama 不需要）
ai.llm.api-key=your-api-key

# 模型名称（需支持 Function Calling / Tools）
ai.llm.model=deepseek-chat

# 最大输出 token 数
ai.llm.max-tokens=8192

# 温度（0-2，越低越确定性）
ai.llm.temperature=0.7

# 请求超时（秒）
ai.llm.timeout=300

# 调试日志（生产环境建议关闭）
ai.llm.log-requests=false
ai.llm.log-responses=false

# 系统提示词前缀
ai.llm.system-prompt-prefix=你是一个专业的代码覆盖率分析助手...
```

### 增强功能配置

```properties
# 语义缓存（相同语义问题复用历史结果）
ai.enhanced.semantic-cache.enabled=true
ai.enhanced.semantic-cache.threshold=0.85   # 相似度阈值（0-1）

# 对话记忆（保留最近 N 轮上下文）
ai.enhanced.conversation.max-rounds=20

# 自学习（从用户反馈中提炼知识）
ai.enhanced.self-learning.enabled=true
ai.enhanced.self-learning.interval-hours=6
ai.enhanced.self-learning.knowledge-hit-enabled=true
ai.enhanced.self-learning.knowledge-hit-threshold=0.7
ai.enhanced.self-learning.dynamic-guide-enabled=true

# 反馈数据保留天数
ai.enhanced.feedback.retention-days=30
```

### 页面上下文路由配置

AI 对话会根据前端页面上下文自动补充分析范围，关键字可在 `application.properties` 中调整：

```properties
ai.interactive.route.coverage.keywords=覆盖率页,coverage,覆盖率详情,覆盖率报告
ai.interactive.route.trace.keywords=监控页,monitor,调用链页,链路页
ai.interactive.route.snapshot.keywords=快照页,snapshot,我的快照,快照列表
ai.interactive.route.app.keywords=应用页,应用中心,app/list,app/online
ai.interactive.route.code-relation.keywords=代码关系,类关系,callgraph,关系图
```

### 支持的模型

| 提供商 | `ai.llm.provider` | 推荐模型 | 备注 |
|---|---|---|---|
| OpenAI | `openai` | `gpt-4o`、`gpt-4-turbo` | 需 API Key |
| DeepSeek | `deepseek` | `deepseek-chat`、`deepseek-v3` | 需 API Key |
| Ollama（本地） | `ollama` | `qwen2.5-coder:7b`、`qwen2.5:7b` | 无需 Key，需先启动 Ollama |
| 自定义 | `custom` | — | 兼容 OpenAI 协议的接口均可 |

> 模型必须支持 **Function Calling / Tools** 能力，否则工具路由不可用。Ollama 推荐使用 `qwen2.5-coder:7b` 或 `qwen2.5:7b`。

---

## 扩展工具

在 `oAT-service-web` 中创建一个 Spring Bean，通过 LangChain4j 的 `@Tool` 注解声明工具：

```java
@Component
public class MyCustomTool {

    @Tool("描述这个工具的能力，让 AI 知道何时调用它")
    public String analyze(@P("参数说明") String input) {
        // 实现分析逻辑
        return "分析结果";
    }
}
```

在 `AIAgent` 中将此 Bean 注入并注册到工具列表即可自动参与路由。

---

## 注意事项

- `oAT-ai` 作为 Spring Boot AutoConfiguration 模块，仅提供接口和配置类，实际数据查询由 `oAT-service-web` 中的 `AgentDataProviderImpl` 实现。
- AI 分析质量高度依赖基础数据完整性：快照、链路、静态源码均需完整入库。
- 本地 Ollama 模型响应较慢，`ai.llm.timeout` 建议设为 300 秒或更高。
- 语义缓存依赖 Redis，Redis 不可用时缓存会降级跳过，不影响正常功能。

---

## English

[中文](#oat-ai) | English

`oAT-ai` is the AI analysis module of oAccurateTest. It is built on LangChain4j and provides a conversational AI assistant, tool orchestration, semantic cache, and conversation memory. It runs as a dependency of `oAT-service-web`.

## Module Structure

```text
oAT-ai/src/main/java/com/oAT/
├── ai/
│   ├── agent/      # Tools, cache, parallel executor, Agent services, memory, feedback
│   ├── config/     # Spring Boot auto-configuration and AI properties
│   └── service/    # LLM service interface and implementation
└── agent/          # Self-learning service based on feedback data
```

## Build

`oAT-ai` is packaged as a jar and used by `oAT-service-web`, so build it before the Web service:

```bash
cd oAT-service/oAT-ai
mvn clean install
```

## Built-in AI Tools

Tools are registered by `AIAgent` and are selected automatically based on user questions.

| Tool Class | Capability |
|---|---|
| `CoverageTool` | Coverage report query and analysis |
| `CoverageWorkflowTool` | Coverage generation workflow guidance |
| `BugDetectTool` | Defect detection and localization |
| `DefectStatisticsTool` | Defect statistics and trends |
| `PerformanceAnalysisTool` | Performance bottleneck detection |
| `CallChainAnalysisTool` | Call-chain analysis |
| `CallChainCompareTool` | Cross-version call-chain comparison |
| `CodeQualityTool` | Code quality evaluation |
| `CodeRelationTool` | Code relation analysis |
| `SnapshotTool` | System snapshot query |
| `TraceQueryTool` | Trace node query |
| `TestcaseRecommendationTool` | Test case recommendation |
| `AppStatusTool` | Application online status |
| `ProjectInfoTool` | Project information |

## Configuration

All configuration is set in `oAT-service-web` `application.properties`.

### Basic LLM Configuration

```properties
ai.llm.enabled=true
ai.llm.provider=deepseek
ai.llm.base-url=https://api.deepseek.com
ai.llm.api-key=your-api-key
ai.llm.model=deepseek-chat
ai.llm.max-tokens=8192
ai.llm.temperature=0.7
ai.llm.timeout=300
ai.llm.log-requests=false
ai.llm.log-responses=false
ai.llm.system-prompt-prefix=You are a professional code coverage analysis assistant...
```

Supported providers include `ollama`, `openai`, `deepseek`, and `custom`. Custom providers must be compatible with the OpenAI protocol.

### Enhanced Features

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

### Page Context Routing

The AI chat can enrich analysis scope based on frontend page context. Keywords can be adjusted in `application.properties`:

```properties
ai.interactive.route.coverage.keywords=coverage,coverage detail,coverage report
ai.interactive.route.trace.keywords=monitor,trace,call chain
ai.interactive.route.snapshot.keywords=snapshot,my snapshots,snapshot list
ai.interactive.route.app.keywords=app,app center,app/list,app/online
ai.interactive.route.code-relation.keywords=code relation,class relation,callgraph
```

## Supported Models

| Provider | `ai.llm.provider` | Recommended Models | Notes |
|---|---|---|---|
| OpenAI | `openai` | `gpt-4o`, `gpt-4-turbo` | API key required |
| DeepSeek | `deepseek` | `deepseek-chat`, `deepseek-v3` | API key required |
| Ollama | `ollama` | `qwen2.5-coder:7b`, `qwen2.5:7b` | Local Ollama required, no API key |
| Custom | `custom` | — | Any OpenAI-compatible endpoint |

Models must support Function Calling / Tools. Otherwise, tool routing is unavailable.

## Extend Tools

Create a Spring Bean in `oAT-service-web` and annotate methods with LangChain4j `@Tool`:

```java
@Component
public class MyCustomTool {

    @Tool("Describe this tool so AI knows when to call it")
    public String analyze(@P("Parameter description") String input) {
        return "analysis result";
    }
}
```

Inject and register the Bean in `AIAgent` to participate in routing.

## Notes

- `oAT-ai` is a Spring Boot AutoConfiguration module. Actual data access is implemented by `AgentDataProviderImpl` in `oAT-service-web`.
- AI quality depends on complete platform data: snapshots, traces, and static source metadata must be available.
- Local Ollama models can be slow. Set `ai.llm.timeout` to 300 seconds or higher.
- Semantic cache depends on Redis. If Redis is unavailable, cache is skipped without affecting normal AI calls.
