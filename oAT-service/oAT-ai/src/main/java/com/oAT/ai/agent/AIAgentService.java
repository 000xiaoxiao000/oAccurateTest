package com.oAT.ai.agent;

import com.oAT.agent.AISelfLearningService;
import com.oAT.ai.agent.cache.SemanticCacheService;
import com.oAT.ai.agent.tools.*;
import com.oAT.ai.config.AIConfig;
import com.oAT.ai.config.AIConfigProperties;
import com.oAT.ai.config.AIEnhancedConfig;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Agent 服务
 * 整合所有工具，提供智能对话能力
 *
 * <p>当 LLM 模型不支持/不兼容结构化 Tool Calling 时（如部分 Ollama 模型将函数调用作为纯文本返回），
 * 本服务会自动识别原始工具调用格式并直接执行对应工具，确保用户总能获得数据结果。</p>
 */
@Service
public class AIAgentService {

    private static final Logger logger = LoggerFactory.getLogger(AIAgentService.class);

    private static final int MAX_SEQUENTIAL_TOOL_INVOCATIONS = 8;

    private static final int MAX_RELEVANT_TOOLS_PER_REQUEST = 8;

    private static final List<DateTimeFormatter> LOCAL_DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    private static final List<DateTimeFormatter> LOCAL_DATE_TIME_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    );

    private static final Set<String> NULL_LIKE_VALUES = new HashSet<>(Arrays.asList(
            "", "null", "none", "nil", "n/a", "na", "undefined", "empty"
    ));

    private static final Map<String, List<String>> SAFE_FALLBACK_TOOL_CANDIDATES = createSafeFallbackToolCandidates();

    /** 匹配 {"name": "xxx", "arguments": {...}} 格式的工具调用（支持 ```json 代码块包裹） */
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "(?:```\\s*json\\s*)?\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{.*?})\\s*\\}(?:```)?",
            Pattern.DOTALL);

    private final AIAgent aiAgent;
    private final AIConfigProperties configProperties;
    private final AgentDataProvider dataProvider;
    private final String initializationStatus;

    /** 工具方法映射：兼容别名/规范化名称 → 方法绑定 */
    private final Map<String, ToolMethodBinding> toolMethodBindings = new LinkedHashMap<>();

    /** 工具 schema 缓存：规范化工具名 → 参数结构信息 */
    private final Map<String, ToolMethodSchema> toolMethodSchemas = new LinkedHashMap<>();

    /** LangChain4j 动态工具提供器缓存：规范化工具名 → 工具定义/执行器 */
    private final Map<String, ToolProviderEntry> toolProviderEntries = new LinkedHashMap<>();

    /** 最近的 fallback 执行报告缓存 */
    private final Deque<FallbackReport> recentFallbackReports = new ArrayDeque<>();

    private static final int MAX_FALLBACK_REPORTS = 20;

    /** 语义缓存服务（用于相似问题命中） */
    private final SemanticCacheService semanticCacheService;

    /** 是否启用语义缓存 */
    private final boolean semanticCacheEnabled;

    /** 智能工具推荐器 */
    private final ToolRecommender toolRecommender;

    /** 动态LLM切换器 */
    private final DynamicLLMSwitcher llmSwitcher;

    /** 多轮对话记忆服务 */
    private final ConversationMemoryService conversationMemory;

    /** 自主学习服务 */
    private volatile AISelfLearningService selfLearningService;

    @Autowired
    public AIAgentService(@Autowired(required = false) ChatModel chatLanguageModel,
                          AIConfigProperties configProperties,
                          AgentDataProvider dataProvider,
                          AIConfig aiConfig,
                          AIEnhancedConfig enhancedConfig) {
        this.configProperties = configProperties;
        this.dataProvider = dataProvider;

        // 初始化增强服务
        this.semanticCacheEnabled = enhancedConfig.getSemanticCache().isEnabled();
        this.semanticCacheService = new SemanticCacheService(enhancedConfig.getSemanticCache().getThreshold(), 500);
        this.toolRecommender = new ToolRecommender();
        this.llmSwitcher = new DynamicLLMSwitcher(aiConfig);
        this.conversationMemory = new ConversationMemoryService(enhancedConfig.getConversation().getMaxRounds(),
                Math.max(1, enhancedConfig.getConversation().getMaxRounds() / 2));

        // 注册所有内置工具到推荐器
        toolRecommender.registerAllBuiltInTools();

        if (chatLanguageModel == null) {
            this.initializationStatus = buildUnavailableReason("chat model bean was not created", aiConfig);
            logger.warn("{}", this.initializationStatus);
            this.aiAgent = null;
        } else {
            List<Object> tools = createTools();
            this.aiAgent = AiServices.builder(AIAgent.class)
                    .chatModel(chatLanguageModel)
                    .toolProvider(this::provideRelevantTools)
                    .maxSequentialToolsInvocations(MAX_SEQUENTIAL_TOOL_INVOCATIONS)
                    .build();
            this.initializationStatus = String.format(
                    "AI Agent initialized successfully with LangChain4j %s and %d tools",
                    AiServices.class.getPackage().getImplementationVersion(), tools.size());
            logger.info("{}", this.initializationStatus);
        }
    }

    private List<Object> createTools() {
        List<Object> tools = new ArrayList<>();

        // 创建工具实例并注册到映射表（用于兜底执行）
        ProjectInfoTool projectInfoTool = new ProjectInfoTool(dataProvider);
        AppStatusTool appStatusTool = new AppStatusTool(dataProvider);
        CoverageTool coverageTool = new CoverageTool(dataProvider);
        TraceQueryTool traceQueryTool = new TraceQueryTool(dataProvider);
        SnapshotTool snapshotTool = new SnapshotTool(dataProvider);
        CodeRelationTool codeRelationTool = new CodeRelationTool(dataProvider);
        PerformanceAnalysisTool performanceTool = new PerformanceAnalysisTool(dataProvider);
        DefectStatisticsTool defectTool = new DefectStatisticsTool(dataProvider);
        TestcaseRecommendationTool testcaseTool = new TestcaseRecommendationTool(dataProvider);
        CodeQualityTool codeQualityTool = new CodeQualityTool(dataProvider);
        BugDetectTool bugDetectTool = new BugDetectTool(dataProvider);
        CallChainAnalysisTool callChainAnalysisTool = new CallChainAnalysisTool(dataProvider);
        CallChainCompareTool callChainCompareTool = new CallChainCompareTool(dataProvider);

        tools.add(projectInfoTool);
        tools.add(appStatusTool);
        tools.add(coverageTool);
        tools.add(traceQueryTool);
        tools.add(snapshotTool);
        tools.add(codeRelationTool);
        tools.add(performanceTool);
        tools.add(defectTool);
        tools.add(testcaseTool);
        tools.add(codeQualityTool);
        tools.add(bugDetectTool);
        tools.add(callChainAnalysisTool);
        tools.add(callChainCompareTool);

        // 注册工具实例，用于兜底执行
        registerTool(projectInfoTool);
        registerTool(appStatusTool);
        registerTool(coverageTool);
        registerTool(traceQueryTool);
        registerTool(snapshotTool);
        registerTool(codeRelationTool);
        registerTool(performanceTool);
        registerTool(defectTool);
        registerTool(testcaseTool);
        registerTool(codeQualityTool);
        registerTool(bugDetectTool);
        registerTool(callChainAnalysisTool);
        registerTool(callChainCompareTool);

        return tools;
    }

    /**
     * 注册工具实例到映射表
     * 扫描工具类中所有带 @Tool 注解的方法，建立 方法名/别名→工具绑定 的映射
     */
    private void registerTool(Object toolInstance) {
        for (Method method : toolInstance.getClass().getDeclaredMethods()) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation == null) {
                continue;
            }

            ToolMethodBinding binding = new ToolMethodBinding(toolInstance, method);
            ToolMethodSchema schema = buildToolMethodSchema(method);
            registerToolBinding(method.getName(), binding, schema);
            registerToolBinding(toolInstance.getClass().getSimpleName() + "." + method.getName(), binding, schema);
            registerToolProviderEntry(toolInstance, method);

            logger.debug("Registered fallback tool: {} -> {}", method.getName(), toolInstance.getClass().getSimpleName());
        }
    }

    private void registerToolProviderEntry(Object toolInstance, Method method) {
        ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
        ToolExecutor executor = new DefaultToolExecutor(toolInstance, method);
        ToolProviderEntry entry = new ToolProviderEntry(specification, executor);
        registerToolProviderEntry(method.getName(), entry);
        registerToolProviderEntry(specification.name(), entry);
    }

    private void registerToolProviderEntry(String alias, ToolProviderEntry entry) {
        String normalizedAlias = normalizeName(alias);
        if (!normalizedAlias.isEmpty()) {
            toolProviderEntries.put(normalizedAlias, entry);
        }
    }

    private ToolProviderResult provideRelevantTools(ToolProviderRequest request) {
        String question = extractUserMessageText(request.userMessage());
        ToolRecommender.Recommendation recommendation = toolRecommender.recommend(question);
        LinkedHashSet<String> relevantToolNames = resolveRelevantToolNames(question, recommendation);

        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        int added = 0;
        for (String toolName : relevantToolNames) {
            ToolProviderEntry entry = toolProviderEntries.get(normalizeName(toolName));
            if (entry == null) {
                continue;
            }
            builder.add(entry.specification, entry.executor);
            added++;
            if (added >= MAX_RELEVANT_TOOLS_PER_REQUEST) {
                break;
            }
        }

        if (added == 0) {
            addToolIfPresent(builder, "getProjectOverview");
            addToolIfPresent(builder, "getProjectInfo");
            addToolIfPresent(builder, "getProjectStatistics");
            added = 3;
        }

        logger.debug("Provided {} relevant AI tools for intent={}, primary={}, question={}",
                added, recommendation.detectedIntent, recommendation.primaryTool, abbreviate(question, 80));
        return builder.build();
    }

    private void addToolIfPresent(ToolProviderResult.Builder builder, String toolName) {
        ToolProviderEntry entry = toolProviderEntries.get(normalizeName(toolName));
        if (entry != null) {
            builder.add(entry.specification, entry.executor);
        }
    }

    private LinkedHashSet<String> resolveRelevantToolNames(String question, ToolRecommender.Recommendation recommendation) {
        LinkedHashSet<String> toolNames = new LinkedHashSet<>();
        if (recommendation != null) {
            addIfNotBlank(toolNames, recommendation.primaryTool);
            if (recommendation.secondaryTools != null) {
                recommendation.secondaryTools.forEach(toolName -> addIfNotBlank(toolNames, toolName));
            }
        }

        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "业务需求", "业务逻辑", "业务规则", "业务场景", "处理什么业务", "需求分析", "功能逻辑", "方法职责")) {
            addAll(toolNames, "searchCodeRelation", "analyzeBusinessRequirement", "getClassCallGraph", "detectBugsInMethod");
        } else if (containsAny(normalized, "bug", "可能存在", "潜在bug", "潜在问题", "代码缺陷", "源码缺陷", "空指针", "资源泄漏", "并发问题", "逻辑错误")) {
            addAll(toolNames, "searchCodeRelation", "detectBugsInMethod", "detectBugs", "batchDetectBugs", "getCodeQualityReport");
        } else if (containsAny(normalized, "覆盖", "coverage", "低覆盖", "未覆盖", "覆盖率")) {
            addAll(toolNames, "getProjectCoverageOverview", "getAppCoverageReport", "getLowCoverageClasses", "getCoverageImprovementSuggestions", "getApps", "searchAppByName");
        } else if (containsAny(normalized, "性能", "performance", "慢接口", "响应时间", "p95", "p99", "耗时", "退化")) {
            addAll(toolNames, "getAppPerformanceOverview", "getSlowEndpoints", "getEndpointCallFrequency", "compareOverTime", "getApps", "searchAppByName");
        } else if (containsAny(normalized, "缺陷", "defect", "错误", "error", "异常", "exception", "根因")) {
            addAll(toolNames, "getDefectOverview", "getRecentExceptions", "getAppErrorDetails", "getRecentTraces", "locateRootCause", "getApps");
        } else if (containsAny(normalized, "链路", "trace", "调用链", "span", "链路详情")) {
            addAll(toolNames, "getRecentTraces", "getTracesByAppName", "getTraceDetail", "analyzeCallChain", "getApps", "searchAppByName");
        } else if (containsAny(normalized, "快照", "snapshot", "版本", "上线", "发布")) {
            addAll(toolNames, "getSnapshots", "getMySnapshots", "getSnapshotDetail", "getProjectCoverageOverview");
        } else if (containsAny(normalized, "应用", "app", "在线", "运行状态", "状态")) {
            addAll(toolNames, "getApps", "getOnlineApps", "searchAppByName", "getAppDetail");
        } else if (containsAny(normalized, "代码", "类", "方法", "调用关系", "调用图", "上下游", "依赖")) {
            addAll(toolNames, "searchCodeRelation", "getClassCallGraph", "getCallGraph");
        } else {
            addAll(toolNames, "getProjectOverview", "getProjectInfo", "getProjectStatistics", "getApps", "getProjectCoverageOverview");
        }

        return toolNames;
    }

    private void addAll(Set<String> target, String... values) {
        for (String value : values) {
            addIfNotBlank(target, value);
        }
    }

    private void addIfNotBlank(Set<String> target, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.add(value.trim());
        }
    }

    private String extractUserMessageText(UserMessage userMessage) {
        if (userMessage == null) {
            return "";
        }
        try {
            if (userMessage.hasSingleText()) {
                return userMessage.singleText();
            }
        } catch (Exception ignored) {
        }
        return userMessage.toString();
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private void registerToolBinding(String alias, ToolMethodBinding binding, ToolMethodSchema schema) {
        String normalizedAlias = normalizeName(alias);
        if (normalizedAlias.isEmpty()) {
            return;
        }
        toolMethodBindings.put(normalizedAlias, binding);
        toolMethodSchemas.put(normalizedAlias, schema);
    }

    private ToolMethodSchema buildToolMethodSchema(Method method) {
        List<ToolParameterSchema> parameters = new ArrayList<>();
        for (java.lang.reflect.Parameter parameter : method.getParameters()) {
            if (isInjectableParameter(parameter)) {
                continue;
            }
            String annotatedName = getAnnotatedParameterName(parameter);
            List<String> candidates = buildCandidateNames(parameter.getName(), annotatedName);
            parameters.add(new ToolParameterSchema(
                    parameter.getName(),
                    annotatedName,
                    parameter.getType(),
                    parameter.getParameterizedType(),
                    candidates
            ));
        }
        return new ToolMethodSchema(method.getName(), parameters);
    }

    public boolean isAvailable() {
        return aiAgent != null && configProperties.isEnabled();
    }

    public String getInitializationStatus() {
        return initializationStatus;
    }

    private String buildUnavailableReason(String detail, AIConfig aiConfig) {
        if (!configProperties.isEnabled()) {
            return "AI Agent initialization skipped: ai.llm.enabled=false";
        }
        return String.format(
                "AI Agent initialization failed: %s (provider=%s, model=%s, baseUrl=%s)",
                detail,
                safeValue(aiConfig.getProvider()),
                safeValue(aiConfig.getModel()),
                safeValue(aiConfig.getBaseUrl()));
    }

    private String safeValue(String value) {
        return value == null || value.trim().isEmpty() ? "<empty>" : value;
    }

    /**
     * 与AI Agent对话（带工具调用兜底）
     * 集成语义缓存、智能工具推荐、多轮对话记忆
     */
    public String chat(AgentContext context, String question) {
        if (isToolCatalogQuestion(question)) {
            return buildToolCatalogResponse();
        }
        if (!isAvailable()) {
            return null;
        }
        try {
            AgentContext.setContext(context);

            // 1. 语义缓存检查（相似问题命中直接返回）
            if (semanticCacheEnabled) {
                SemanticCacheService.CachedResponse cached = semanticCacheService.get(question);
                if (cached != null && cached.isFromCache() && cached.getAnswer() != null) {
                    logger.info("Semantic cache hit for question: {}",
                            question.length() > 50 ? question.substring(0, 50) + "..." : question);
                    return cached.getAnswer();
                }
            }

            // 2. 智能工具推荐（日志记录，供后续分析）
            ToolRecommender.Recommendation recommendation = toolRecommender.recommend(question);
            logger.debug("Tool recommendation: primary={}, intent={}, confidence={}",
                    recommendation.primaryTool, recommendation.detectedIntent, recommendation.confidence);

            // 3. 构建增强的上下文（包含多轮对话摘要）
            String enhancedQuestion = buildEnhancedQuestion(context, question, recommendation);

            long startTime = System.currentTimeMillis();
            String response = aiAgent.chat(enhancedQuestion, context.getProjectId(), context.getUserName());
            long responseTime = System.currentTimeMillis() - startTime;

            // 4. 兜底检查
            String fallbackResult = tryFallbackToolExecution(response);
            if (fallbackResult != null) {
                logger.info("Detected raw tool call in AI response, executed via fallback");
                response = fallbackResult;
            }

            // 5. 将结果存入语义缓存
            if (response != null && !response.isEmpty()) {
                if (semanticCacheEnabled) {
                    semanticCacheService.put(question, response, null);
                }
                // 记录工具推荐结果（用于学习优化）
                toolRecommender.recordToolCall(recommendation.primaryTool, true);
                llmSwitcher.recordResult(llmSwitcher.getDefaultModelName(), true, responseTime);
            } else {
                llmSwitcher.recordResult(llmSwitcher.getDefaultModelName(), false, responseTime);
            }

            logger.info("AI Agent response: {}ms, length={}",
                    responseTime, response != null ? response.length() : 0);
            return response;
        } catch (Exception e) {
            if (isToolInvocationLoopLimit(e)) {
                logger.warn("AI Agent stopped because tool invocation loop limit was reached: {}", e.getMessage());
                return "抱歉，AI 助手连续调用工具次数过多，已自动停止以避免无效循环。请把问题缩小到一个具体目标（例如覆盖率概览、某个应用的慢接口、某条调用链详情），我会重新查询。";
            }
            logger.error("AI Agent chat failed", e);
            return null;
        } finally {
            AgentContext.clearContext();
        }
    }

    /**
     * 带页面上下文的对话（同样带兜底）
     */
    public String chatWithContext(AgentContext context, String question, String pageContext) {
        if (isToolCatalogQuestion(question)) {
            return buildToolCatalogResponse();
        }
        if (!isAvailable()) {
            return null;
        }
        try {
            context.setPageContext(pageContext);
            AgentContext.setContext(context);
            ToolRecommender.Recommendation recommendation = toolRecommender.recommend(question);
            String enhancedQuestion = buildEnhancedQuestion(context, question, recommendation);
            String response = aiAgent.chatWithContext(enhancedQuestion, context.getProjectId(), context.getUserName(), pageContext);

            String fallbackResult = tryFallbackToolExecution(response);
            if (fallbackResult != null) {
                logger.info("Detected raw tool call in AI response (withContext), executed via fallback");
                return fallbackResult;
            }

            return response;
        } catch (Exception e) {
            if (isToolInvocationLoopLimit(e)) {
                logger.warn("AI Agent with context stopped because tool invocation loop limit was reached: {}", e.getMessage());
                return "抱歉，AI 助手连续调用工具次数过多，已自动停止以避免无效循环。请把问题缩小到当前页面中的一个具体分析目标后重试。";
            }
            logger.error("AI Agent chat with context failed", e);
            return null;
        } finally {
            AgentContext.clearContext();
        }
    }

    /**
     * 带图片的多模态对话（同样带兜底）
     */
    public String chatWithImage(AgentContext context, String question, String pageContext, String imageData) {
        if (isToolCatalogQuestion(question)) {
            return buildToolCatalogResponse();
        }
        if (!isAvailable()) {
            return null;
        }
        try {
            context.setPageContext(pageContext);
            AgentContext.setContext(context);
            // 截断过长的 base64 数据，防止超过模型上下文限制（保留前 500KB）
            String truncatedImage = imageData != null && imageData.length() > 680000
                    ? imageData.substring(0, 680000) + "... [图片数据已截断]"
                    : imageData;
            ToolRecommender.Recommendation recommendation = toolRecommender.recommend(question);
            String enhancedQuestion = buildEnhancedQuestion(context, question, recommendation);
            String response = aiAgent.chatWithImage(enhancedQuestion, context.getProjectId(), context.getUserName(),
                    pageContext, truncatedImage);

            String fallbackResult = tryFallbackToolExecution(response);
            if (fallbackResult != null) {
                logger.info("Detected raw tool call in AI response (withImage), executed via fallback");
                return fallbackResult;
            }

            return response;
        } catch (Exception e) {
            if (isToolInvocationLoopLimit(e)) {
                logger.warn("AI Agent with image stopped because tool invocation loop limit was reached: {}", e.getMessage());
                return "抱歉，AI 助手连续调用工具次数过多，已自动停止以避免无效循环。请结合截图指定一个更具体的问题后重试。";
            }
            logger.error("AI Agent chat with image failed", e);
            return null;
        } finally {
            AgentContext.clearContext();
        }
    }

    private boolean isToolInvocationLoopLimit(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("exceeded") && message.contains("sequential tool invocations")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public String getProjectOverview(AgentContext context) {
        if (!isAvailable()) {
            return null;
        }
        try {
            AgentContext.setContext(context);
            return aiAgent.getProjectOverview(context.getProjectId(), context.getUserName());
        } catch (Exception e) {
            logger.error("AI Agent get project overview failed", e);
            return null;
        } finally {
            AgentContext.clearContext();
        }
    }

    // ==================== 工具调用兜底逻辑 ====================

    /**
     * 尝试检测并执行原始工具调用
     *
     * <p>当 LLM 不支持结构化 Tool Calling 时，它可能将函数调用以纯文本 JSON 格式输出，
     * 例如：<pre>{"name": "getProjectCoverageOverview", "arguments": {}}</pre>
     * 此方法检测这种模式并通过反射直接调用对应的工具方法。</p>
     *
     * @param response AI 的原始响应文本
     * @return 工具执行结果字符串；如果不需要兜底则返回 null
     */
    @SuppressWarnings("unchecked")
    private String tryFallbackToolExecution(String response) {
        FallbackReport report = new FallbackReport();
        report.startedAt = System.currentTimeMillis();
        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = TOOL_CALL_PATTERN.matcher(response.trim());
        if (!matcher.find()) {
            return null;
        }

        String toolName = matcher.group(1);
        String argsJson = matcher.group(2);
        String normalizedToolName = normalizeName(toolName);
        report.toolName = toolName;

        ToolMethodBinding binding = toolMethodBindings.get(normalizedToolName);
        if (binding == null) {
            logger.debug("Response contains tool-like format but '{}' is not a registered tool, treating as normal response", toolName);
            return null;
        }

        ToolMethodSchema schema = toolMethodSchemas.get(normalizedToolName);
        report.schemaDescription = schema != null ? schema.describe() : null;

        logger.info("Fallback: executing tool '{}' with raw arguments: {}", toolName, argsJson);

        try {
            Map<String, Object> args = parseArgsJson(argsJson);
            if (schema != null) {
                logger.debug("Fallback schema [{}]: {}", toolName, schema.describe());
            }
            FallbackExecutionResult executionResult = executeFallbackBinding(binding, args, schema, false, report);
            if (!executionResult.success && isBindingFailure(executionResult.error)) {
                FallbackExecutionResult candidateResult = tryFallbackCandidateTools(toolName, args, report);
                if (candidateResult.success) {
                    executionResult = candidateResult;
                }
            }
            if (!executionResult.success) {
                report.retried = true;
                logger.warn("Fallback strict binding failed for tool '{}', retry with relaxed strategy: {}",
                        toolName, executionResult.errorMessage);
                executionResult = executeFallbackBinding(binding, args, schema, true, report);
            }
            if (!executionResult.success) {
                throw executionResult.error != null ? executionResult.error
                        : new IllegalArgumentException(executionResult.errorMessage);
            }

            String resultStr = executionResult.result != null ? executionResult.result.toString() : "（无返回数据）";
            report.success = true;
            report.resultLength = resultStr.length();
            report.finishedAt = System.currentTimeMillis();
            logger.info("Fallback: tool '{}' executed successfully, result length: {}", toolName, resultStr.length());
            storeFallbackReport(report);
            logger.debug("Fallback report: {}", report.describe());
            return resultStr;

        } catch (Exception e) {
            report.success = false;
            report.errorMessage = e.getMessage();
            report.finishedAt = System.currentTimeMillis();
            logger.error("Fallback: failed to execute tool '{}': {}", toolName, e.getMessage(), e);
            storeFallbackReport(report);
            logger.debug("Fallback report: {}", report.describe());
            return "抱歉，AI 助手在查询数据时遇到了错误：" + e.getMessage();
        }
    }

    private FallbackExecutionResult tryFallbackCandidateTools(String originalToolName, Map<String, Object> args,
                                                              FallbackReport report) {
        List<String> candidates = SAFE_FALLBACK_TOOL_CANDIDATES.get(normalizeName(originalToolName));
        if (candidates == null || candidates.isEmpty()) {
            return FallbackExecutionResult.failure(new IllegalArgumentException("no fallback candidate"));
        }
        for (String candidateToolName : candidates) {
            ToolMethodBinding candidateBinding = toolMethodBindings.get(normalizeName(candidateToolName));
            ToolMethodSchema candidateSchema = toolMethodSchemas.get(normalizeName(candidateToolName));
            if (candidateBinding == null) {
                continue;
            }
            FallbackReport candidateReport = report != null ? report.copy() : null;
            if (candidateReport != null) {
                candidateReport.candidateToolName = candidateToolName;
            }
            FallbackExecutionResult candidateResult = executeFallbackBinding(candidateBinding, args,
                    candidateSchema, false, candidateReport);
            if (candidateResult.success) {
                if (report != null && candidateReport != null) {
                    report.candidateToolName = candidateToolName;
                    report.strategy = candidateReport.strategy;
                    report.parameterReports.clear();
                    report.parameterReports.addAll(candidateReport.parameterReports);
                }
                return candidateResult;
            }
        }
        return FallbackExecutionResult.failure(new IllegalArgumentException("fallback candidates failed"));
    }

    private boolean isBindingFailure(Throwable error) {
        if (error == null) {
            return false;
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof IllegalArgumentException || current instanceof NumberFormatException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Map<String, List<String>> createSafeFallbackToolCandidates() {
        Map<String, List<String>> candidates = new LinkedHashMap<>();
        candidates.put(normalizeStaticName("getProjectOverview"), Arrays.asList("getProjectInfo", "getProjectStatistics"));
        candidates.put(normalizeStaticName("getAppCoverageReport"), Collections.singletonList("getAppCoverageTrend"));
        candidates.put(normalizeStaticName("getAppCoverageTrend"), Collections.singletonList("getAppCoverageReport"));
        candidates.put(normalizeStaticName("getTracesByApp"), Collections.singletonList("getRecentTraces"));
        candidates.put(normalizeStaticName("getTracesByAppName"), Collections.singletonList("getRecentTraces"));
        candidates.put(normalizeStaticName("getMySnapshots"), Collections.singletonList("getSnapshots"));
        return candidates;
    }

    private static String normalizeStaticName(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private void storeFallbackReport(FallbackReport report) {
        if (report == null) {
            return;
        }
        synchronized (recentFallbackReports) {
            recentFallbackReports.addFirst(report.copy());
            while (recentFallbackReports.size() > MAX_FALLBACK_REPORTS) {
                recentFallbackReports.removeLast();
            }
        }
    }

    public FallbackReport getLatestFallbackReport() {
        synchronized (recentFallbackReports) {
            FallbackReport latest = recentFallbackReports.peekFirst();
            return latest != null ? latest.copy() : null;
        }
    }

    public List<FallbackReport> getRecentFallbackReports(int limit) {
        synchronized (recentFallbackReports) {
            List<FallbackReport> reports = new ArrayList<>();
            int count = 0;
            for (FallbackReport report : recentFallbackReports) {
                if (limit > 0 && count >= limit) {
                    break;
                }
                reports.add(report.copy());
                count++;
            }
            return reports;
        }
    }

    /**
     * 使用标准 JSON 解析参数，支持嵌套对象、数组和转义字符。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgsJson(String argsJson) {
        if (argsJson == null || argsJson.trim().isEmpty() || "{}".equals(argsJson.trim())) {
            return new LinkedHashMap<>();
        }
        Object parsed = new JsonParser(argsJson).parseValue();
        if (!(parsed instanceof Map<?, ?> parsedMap)) {
            throw new IllegalArgumentException("工具参数必须是 JSON 对象");
        }
        return (Map<String, Object>) parsedMap;
    }

    /**
     * 解析工具名，兼容大小写、连接符、前缀变化
     */
    private String normalizeName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String normalized = rawName.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
    }

    /**
     * 判断参数是否为可注入的上下文参数（如 @V、@MemoryId 等）
     */
    private boolean isInjectableParameter(java.lang.reflect.Parameter param) {
        return param.isAnnotationPresent(dev.langchain4j.service.V.class)
                || param.isAnnotationPresent(dev.langchain4j.service.MemoryId.class);
    }

    private Object[] buildMethodArguments(Method method, Map<String, Object> args,
                                          ToolMethodSchema schema, boolean relaxedMode,
                                          FallbackReport report) {
        java.lang.reflect.Parameter[] params = method.getParameters();
        Object[] invokeArgs = new Object[params.length];
        Map<String, Object> normalizedArgs = new LinkedHashMap<>();
        Map<String, Object> originalArgs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            originalArgs.put(entry.getKey(), entry.getValue());
            normalizedArgs.put(normalizeName(entry.getKey()), entry.getValue());
        }

        Set<String> consumedKeys = new LinkedHashSet<>();
        List<String> bindingDiagnostics = new ArrayList<>();
        for (int i = 0; i < params.length; i++) {
            java.lang.reflect.Parameter param = params[i];
            String paramName = param.getName();
            Class<?> paramType = param.getType();

            if (isInjectableParameter(param)) {
                invokeArgs[i] = null;
                bindingDiagnostics.add(paramName + "=<injectable>");
                continue;
            }

            String annotatedName = getAnnotatedParameterName(param);
            List<String> candidates = schema != null
                    ? schema.getCandidatesFor(paramName, annotatedName)
                    : buildCandidateNames(paramName, annotatedName);
            MatchedArgument matchedArgument = getArgumentValue(originalArgs, normalizedArgs, candidates,
                    relaxedMode, consumedKeys, schema, paramType);
            Object value = matchedArgument.value;
            if (value == null && (containsArgument(originalArgs, normalizedArgs, paramName)
                    || containsArgument(originalArgs, normalizedArgs, annotatedName))) {
                invokeArgs[i] = null;
                bindingDiagnostics.add(paramName + "=<explicit-null via " + matchedArgument.matchedKey + ">"
                        + (matchedArgument.strategy != null ? " [" + matchedArgument.strategy + "]" : ""));
                if (report != null) {
                    report.parameterReports.add(FallbackParameterReport.explicitNull(
                            paramName, matchedArgument.matchedKey, matchedArgument.strategy, paramType.getSimpleName()));
                }
                if (matchedArgument.matchedKey != null && !matchedArgument.matchedKey.startsWith("<")) {
                    consumedKeys.add(normalizeName(matchedArgument.matchedKey));
                }
                continue;
            }

            if (value != null) {
                try {
                    invokeArgs[i] = convertType(value, paramType, param.getParameterizedType());
                    bindingDiagnostics.add(paramName + "<-" + matchedArgument.matchedKey
                            + " [" + matchedArgument.strategy + "] "
                            + describeConversion(value, invokeArgs[i], paramType));
                    if (report != null) {
                        report.parameterReports.add(FallbackParameterReport.success(
                                paramName, matchedArgument.matchedKey, matchedArgument.strategy,
                                safeTypeName(value), paramType.getSimpleName(), false, false,
                                describeConversion(value, invokeArgs[i], paramType)));
                    }
                    if (matchedArgument.matchedKey != null && !matchedArgument.matchedKey.startsWith("<")) {
                        consumedKeys.add(normalizeName(matchedArgument.matchedKey));
                    }
                } catch (Exception conversionError) {
                    bindingDiagnostics.add(paramName + "<-" + matchedArgument.matchedKey
                            + " [" + matchedArgument.strategy + "] conversion-failed: "
                            + describeConversionFailure(value, paramType, conversionError));
                    if (report != null) {
                        report.parameterReports.add(FallbackParameterReport.failure(
                                paramName, matchedArgument.matchedKey, matchedArgument.strategy,
                                safeTypeName(value), paramType.getSimpleName(),
                                describeConversionFailure(value, paramType, conversionError)));
                    }
                    throw conversionError;
                }
            } else {
                Object fallbackValue = null;
                String fallbackStrategy = null;
                if (paramType.isPrimitive()) {
                    if (!relaxedMode) {
                        String message = "缺少必填基础类型参数: " + paramName + " (" + paramType.getSimpleName() + ")";
                        if (report != null) {
                            report.parameterReports.add(FallbackParameterReport.failure(
                                    paramName, "<missing>", "missing-required-primitive",
                                    null, paramType.getSimpleName(), message));
                        }
                        throw new IllegalArgumentException(message);
                    }
                    fallbackValue = getSemanticDefaultValue(param, schema, paramName, annotatedName, paramType);
                    fallbackStrategy = fallbackValue != null ? "semantic-default" : "primitive-default";
                }
                if (report != null) {
                    report.parameterReports.add(FallbackParameterReport.defaulted(
                            paramName, paramType.getSimpleName(), fallbackStrategy,
                            fallbackValue != null ? String.valueOf(fallbackValue) : null));
                }
                invokeArgs[i] = fallbackValue != null ? fallbackValue : getDefaultForType(paramType);
                bindingDiagnostics.add(paramName + "=<default:" + (fallbackStrategy != null ? fallbackStrategy : "default")
                        + "> => " + String.valueOf(invokeArgs[i]));
            }
        }

        logger.debug("Fallback binding diagnostics [{}][{}]: {}", method.getName(),
                relaxedMode ? "relaxed" : "strict", String.join(", ", bindingDiagnostics));
        return invokeArgs;
    }

    private String describeConversion(Object originalValue, Object convertedValue, Class<?> targetType) {
        return "convert " + safeTypeName(originalValue) + " -> " + targetType.getSimpleName()
                + " => " + safeTypeName(convertedValue);
    }

    private String describeConversionFailure(Object originalValue, Class<?> targetType, Exception error) {
        return safeTypeName(originalValue) + " -> " + targetType.getSimpleName() + " failed: " + error.getMessage();
    }

    private String safeTypeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private String getAnnotatedParameterName(java.lang.reflect.Parameter param) {
        P annotation = param.getAnnotation(P.class);
        if (annotation == null) {
            return null;
        }
        return annotation.value();
    }

    private boolean containsArgument(Map<String, Object> originalArgs, Map<String, Object> normalizedArgs, String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }
            if (originalArgs.containsKey(candidate) || normalizedArgs.containsKey(normalizeName(candidate))) {
                return true;
            }
        }
        return false;
    }

    private MatchedArgument getArgumentValue(Map<String, Object> originalArgs, Map<String, Object> normalizedArgs,
                                             List<String> candidates, boolean relaxedMode,
                                             Set<String> consumedKeys, ToolMethodSchema schema, Class<?> targetType) {
        List<ArgumentMatchScore> scoredMatches = scoreArgumentMatches(originalArgs, normalizedArgs, candidates,
                consumedKeys, schema, targetType);
        if (!scoredMatches.isEmpty()) {
            ArgumentMatchScore bestMatch = scoredMatches.get(0);
            return MatchedArgument.of(bestMatch.value, bestMatch.matchedKey, bestMatch.strategy);
        }
        if (!relaxedMode) {
            if (candidates.size() == 1 && !originalArgs.isEmpty()) {
                return MatchedArgument.of(originalArgs.values().iterator().next(), "<single-arg>", "single-value-fallback");
            }
            return MatchedArgument.notMatched();
        }

        String candidateKey = selectRelaxedArgumentKey(normalizedArgs.keySet(), candidates, consumedKeys);
        if (candidateKey != null) {
            return MatchedArgument.of(normalizedArgs.get(candidateKey), candidateKey, "relaxed-contains");
        }
        if (normalizedArgs.size() == 1) {
            return MatchedArgument.of(normalizedArgs.values().iterator().next(), "<single-normalized-arg>", "relaxed-single-value");
        }
        return MatchedArgument.notMatched();
    }

    private List<ArgumentMatchScore> scoreArgumentMatches(Map<String, Object> originalArgs,
                                                          Map<String, Object> normalizedArgs,
                                                          List<String> candidates,
                                                          Set<String> consumedKeys,
                                                          ToolMethodSchema schema,
                                                          Class<?> targetType) {
        List<ArgumentMatchScore> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (originalArgs.containsKey(candidate)) {
                String normalizedKey = normalizeName(candidate);
                if (!consumedKeys.contains(normalizedKey)) {
                    matches.add(new ArgumentMatchScore(originalArgs.get(candidate), candidate, "exact",
                            scoreMatch(candidate, targetType, schema, true, false)));
                }
            }
            String normalizedCandidate = normalizeName(candidate);
            if (!normalizedCandidate.isEmpty() && normalizedArgs.containsKey(normalizedCandidate)
                    && !consumedKeys.contains(normalizedCandidate)) {
                matches.add(new ArgumentMatchScore(normalizedArgs.get(normalizedCandidate), normalizedCandidate,
                        "normalized", scoreMatch(normalizedCandidate, targetType, schema, false, true)));
            }
        }
        matches.sort(Comparator.comparingInt(ArgumentMatchScore::score).reversed());
        return matches;
    }

    private int scoreMatch(String key, Class<?> targetType, ToolMethodSchema schema,
                           boolean exact, boolean normalized) {
        int score = exact ? 100 : 70;
        if (normalized) {
            score += 5;
        }
        if (schema != null && schema.matchesTypeHint(key, targetType)) {
            score += 20;
        }
        return score;
    }

    private List<String> buildCandidateNames(String paramName, String annotatedName) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addCandidateName(candidates, paramName);
        addCandidateName(candidates, annotatedName);
        return new ArrayList<>(candidates);
    }

    private void addCandidateName(Set<String> candidates, String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return;
        }
        String trimmed = rawName.trim();
        candidates.add(trimmed);
        candidates.add(toSnakeCase(trimmed));
        candidates.add(toKebabCase(trimmed));
        candidates.addAll(extractSemanticAliases(trimmed));
    }

    private Set<String> extractSemanticAliases(String rawName) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        String compact = rawName.replaceAll("[（(].*?[)）]", " ")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+", " ")
                .trim();
        if (compact.isEmpty()) {
            return aliases;
        }
        aliases.add(compact);
        String[] segments = compact.split("\\s+");
        for (String segment : segments) {
            if (!segment.isEmpty()) {
                aliases.add(segment);
                aliases.add(toSnakeCase(segment));
                aliases.add(toKebabCase(segment));
            }
        }
        return aliases;
    }

    private String toSnakeCase(String value) {
        return splitCamelCase(value, "_");
    }

    private String toKebabCase(String value) {
        return splitCamelCase(value, "-");
    }

    private String splitCamelCase(String value, String delimiter) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.replaceAll("([a-z0-9])([A-Z])", "$1" + delimiter + "$2").toLowerCase(Locale.ROOT);
    }

    private String selectRelaxedArgumentKey(Set<String> availableKeys, List<String> candidates, Set<String> consumedKeys) {
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeName(candidate);
            for (String availableKey : availableKeys) {
                if (consumedKeys.contains(availableKey)) {
                    continue;
                }
                if (availableKey.contains(normalizedCandidate) || normalizedCandidate.contains(availableKey)) {
                    return availableKey;
                }
            }
        }
        return null;
    }

    private FallbackExecutionResult executeFallbackBinding(ToolMethodBinding binding, Map<String, Object> args,
                                                           ToolMethodSchema schema, boolean relaxedMode,
                                                           FallbackReport report) {
        try {
            if (report != null) {
                report.strategy = relaxedMode ? "relaxed" : "strict";
            }
            Object[] invokeArgs = buildMethodArguments(binding.method, args, schema, relaxedMode, report);
            binding.method.setAccessible(true);
            Object result = binding.method.invoke(binding.toolInstance, invokeArgs);
            return FallbackExecutionResult.success(result);
        } catch (Exception e) {
            String strategy = relaxedMode ? "relaxed" : "strict";
            logger.debug("Fallback {} binding failed for tool {}: {}",
                    strategy, binding.method.getName(), e.getMessage(), e);
            return FallbackExecutionResult.failure(e);
        }
    }

    /**
     * 将参数值转换为目标类型
     */
    private Object convertType(Object value, Class<?> targetType) {
        return convertType(value, targetType, targetType);
    }

    private Object convertType(Object value, Class<?> targetType, Type genericType) {
        if (value == null) {
            return null;
        }
        Object emptyStructureValue = convertEmptyStructureString(value, targetType, genericType);
        if (emptyStructureValue != null) {
            return emptyStructureValue;
        }
        if (isNullLikeValue(value, targetType)) {
            return null;
        }
        if (targetType == Object.class) {
            return value;
        }
        if (Optional.class == targetType) {
            return convertOptional(value, genericType);
        }
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        if (targetType == String.class) {
            return value.toString();
        }
        if (targetType == int.class || targetType == Integer.class) {
            return convertInteger(value);
        }
        if (targetType == long.class || targetType == Long.class) {
            return convertLong(value);
        }
        if (targetType == double.class || targetType == Double.class) {
            return convertDouble(value);
        }
        if (targetType == float.class || targetType == Float.class) {
            return convertFloat(value);
        }
        if (targetType == short.class || targetType == Short.class) {
            return convertShort(value);
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return convertByte(value);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return convertBoolean(value);
        }
        if (targetType == char.class || targetType == Character.class) {
            String str = value.toString();
            return str.isEmpty() ? '\0' : str.charAt(0);
        }
        if (targetType == BigDecimal.class) {
            return value instanceof BigDecimal ? value : new BigDecimal(value.toString());
        }
        if (targetType == BigInteger.class) {
            return value instanceof BigInteger ? value : new BigInteger(value.toString());
        }
        if (targetType == LocalDate.class) {
            return convertToLocalDate(value);
        }
        if (targetType == LocalDateTime.class) {
            return convertToLocalDateTime(value);
        }
        if (targetType == Instant.class) {
            return convertToInstant(value);
        }
        if (targetType == OffsetDateTime.class) {
            return convertToOffsetDateTime(value);
        }
        if (targetType == ZonedDateTime.class) {
            return convertToZonedDateTime(value);
        }
        if (targetType.isEnum()) {
            return convertEnum(value, targetType);
        }
        if (targetType.isArray() && value instanceof List<?> listValue) {
            return convertArray(listValue, targetType.getComponentType());
        }
        if (Collection.class.isAssignableFrom(targetType) && value instanceof List<?> listValue) {
            return convertCollection(listValue, targetType, genericType);
        }
        if (Map.class.isAssignableFrom(targetType) && value instanceof Map<?, ?> mapValue) {
            return convertMap(mapValue, genericType);
        }
        if (value instanceof String stringValue) {
            Object convertedFromString = convertStructuredString(stringValue, targetType, genericType);
            if (convertedFromString != null) {
                return convertedFromString;
            }
            Object splitConverted = convertDelimitedString(stringValue, targetType, genericType);
            if (splitConverted != null) {
                return splitConverted;
            }
        }
        if (value instanceof Map<?, ?> mapValue) {
            return convertBean(mapValue, targetType);
        }
        if (value instanceof List<?> listValue && targetType.isArray()) {
            return convertArray(listValue, targetType.getComponentType());
        }
        if (Number.class.isAssignableFrom(targetType) && value instanceof Number) {
            return value;
        }
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertEnum(Object value, Class<?> targetType) {
        String enumName = value.toString();
        for (Object constant : targetType.getEnumConstants()) {
            if (((Enum) constant).name().equalsIgnoreCase(enumName)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("无法转换枚举值: " + enumName + " -> " + targetType.getSimpleName());
    }

    private Boolean convertBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value == null ? null : value.toString().trim();
        if (text == null || text.isEmpty()) {
            return null;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (Arrays.asList("true", "yes", "y", "on", "1", "enabled", "enable", "ok").contains(normalized)) {
            return Boolean.TRUE;
        }
        if (Arrays.asList("false", "no", "n", "off", "0", "disabled", "disable").contains(normalized)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("无法解析布尔值: " + text);
    }

    private Integer convertInteger(Object value) {
        return toIntegralNumber(normalizeNumber(value), Integer.MIN_VALUE, Integer.MAX_VALUE, "Integer").intValueExact();
    }

    private Long convertLong(Object value) {
        return toIntegralNumber(normalizeNumber(value), Long.MIN_VALUE, Long.MAX_VALUE, "Long").longValueExact();
    }

    private Double convertDouble(Object value) {
        BigDecimal decimal = normalizeNumber(value);
        double doubleValue = decimal.doubleValue();
        if (Double.isInfinite(doubleValue)) {
            throw new IllegalArgumentException("数值超出 Double 范围: " + decimal);
        }
        return doubleValue;
    }

    private Float convertFloat(Object value) {
        BigDecimal decimal = normalizeNumber(value);
        float floatValue = decimal.floatValue();
        if (Float.isInfinite(floatValue)) {
            throw new IllegalArgumentException("数值超出 Float 范围: " + decimal);
        }
        return floatValue;
    }

    private Short convertShort(Object value) {
        return toIntegralNumber(normalizeNumber(value), Short.MIN_VALUE, Short.MAX_VALUE, "Short").shortValueExact();
    }

    private Byte convertByte(Object value) {
        return toIntegralNumber(normalizeNumber(value), Byte.MIN_VALUE, Byte.MAX_VALUE, "Byte").byteValueExact();
    }

    private BigDecimal toIntegralNumber(BigDecimal decimal, long min, long max, String targetType) {
        try {
            BigDecimal normalized = decimal.stripTrailingZeros();
            if (normalized.scale() > 0) {
                throw new IllegalArgumentException("数值包含非零小数部分，无法转换为 " + targetType + ": " + decimal);
            }
            long value = normalized.longValueExact();
            if (value < min || value > max) {
                throw new IllegalArgumentException("数值超出 " + targetType + " 范围: " + decimal);
            }
            return BigDecimal.valueOf(value);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("数值无法精确转换为 " + targetType + ": " + decimal, e);
        }
    }

    private BigDecimal normalizeNumber(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = value == null ? null : value.toString().trim();
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("无法解析数字：空值");
        }
        String normalized = text.replaceAll(",", "").replaceAll("_", "");
        if (normalized.matches("^-?\\d+\\.0+$")) {
            normalized = normalized.substring(0, normalized.indexOf('.'));
        }
        return new BigDecimal(normalized);
    }

    private boolean isNullLikeValue(Object value, Class<?> targetType) {
        if (!(value instanceof String stringValue)) {
            return false;
        }
        if (targetType == String.class || targetType == Object.class || targetType.isEnum()) {
            return false;
        }
        String normalized = stringValue.trim().toLowerCase(Locale.ROOT);
        if (NULL_LIKE_VALUES.contains(normalized)) {
            return true;
        }
        return false;
    }

    private Object convertEmptyStructureString(Object value, Class<?> targetType, Type genericType) {
        if (!(value instanceof String stringValue)) {
            return null;
        }
        String normalized = stringValue.trim().toLowerCase(Locale.ROOT);
        if ("[]".equals(normalized)) {
            if (targetType.isArray()) {
                return Array.newInstance(targetType.getComponentType(), 0);
            }
            if (Collection.class.isAssignableFrom(targetType)) {
                return convertCollection(Collections.emptyList(), targetType, genericType);
            }
        }
        if ("{}".equals(normalized) && Map.class.isAssignableFrom(targetType)) {
            return convertMap(Collections.emptyMap(), genericType);
        }
        return null;
    }

    private Object convertArray(List<?> listValue, Class<?> componentType) {
        if (componentType == null) {
            return listValue.toArray();
        }
        Object array = Array.newInstance(componentType, listValue.size());
        for (int i = 0; i < listValue.size(); i++) {
            Array.set(array, i, convertType(listValue.get(i), componentType));
        }
        return array;
    }

    private Object convertCollection(List<?> listValue, Class<?> targetType, Type genericType) {
        Collection<Object> collection = instantiateCollection(targetType);
        Class<?> elementType = extractCollectionElementType(genericType);
        Type elementGenericType = extractCollectionElementGenericType(genericType);
        for (Object item : listValue) {
            collection.add(elementType != null ? convertType(item, elementType,
                    elementGenericType != null ? elementGenericType : elementType) : item);
        }
        return collection;
    }

    private Map<Object, Object> convertMap(Map<?, ?> mapValue, Type genericType) {
        Map<Object, Object> result = new LinkedHashMap<>();
        Type keyType = extractMapKeyType(genericType);
        Type valueType = extractMapValueType(genericType);
        Class<?> keyClass = resolveRawClass(keyType);
        Class<?> valueClass = resolveRawClass(valueType);
        for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
            Object convertedKey = keyClass != null
                    ? convertType(entry.getKey(), keyClass, keyType != null ? keyType : keyClass)
                    : entry.getKey();
            Object convertedValue = valueClass != null
                    ? convertType(entry.getValue(), valueClass, valueType != null ? valueType : valueClass)
                    : entry.getValue();
            result.put(convertedKey, convertedValue);
        }
        return result;
    }

    private Optional<?> convertOptional(Object value, Type genericType) {
        Type wrappedType = extractOptionalWrappedType(genericType);
        Class<?> wrappedClass = resolveRawClass(wrappedType);
        if (wrappedClass == null) {
            return Optional.ofNullable(value);
        }
        return Optional.ofNullable(convertType(value, wrappedClass, wrappedType != null ? wrappedType : wrappedClass));
    }

    private Object convertStructuredString(String stringValue, Class<?> targetType, Type genericType) {
        String trimmed = stringValue == null ? null : stringValue.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return null;
        }
        try {
            Object parsed = new JsonParser(trimmed).parseValue();
            return convertType(parsed, targetType, genericType);
        } catch (Exception e) {
            logger.debug("Structured string conversion skipped for target {}: {}",
                    targetType.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private Object convertDelimitedString(String stringValue, Class<?> targetType, Type genericType) {
        String trimmed = stringValue == null ? null : stringValue.trim();
        if (trimmed == null || trimmed.isEmpty() || !(trimmed.contains(",") || trimmed.contains(";"))) {
            return null;
        }
        List<String> tokens = splitDelimitedValues(trimmed);
        if (tokens.isEmpty()) {
            return null;
        }
        if (targetType.isArray()) {
            return convertArray(tokens, targetType.getComponentType());
        }
        if (Collection.class.isAssignableFrom(targetType)) {
            return convertCollection(tokens, targetType, genericType);
        }
        return null;
    }

    private List<String> splitDelimitedValues(String input) {
        List<String> values = new ArrayList<>();
        for (String part : input.split("\\s*[;,]\\s*")) {
            if (!part.isEmpty()) {
                values.add(part.trim());
            }
        }
        return values;
    }

    private LocalDate convertToLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Number number) {
            return instantFromEpoch(number.longValue()).atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (isEpochText(text)) {
            return instantFromEpoch(Long.parseLong(text)).atZone(ZoneId.systemDefault()).toLocalDate();
        }
        for (DateTimeFormatter formatter : LOCAL_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return convertToLocalDateTime(text).toLocalDate();
    }

    private LocalDateTime convertToLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Number number) {
            return LocalDateTime.ofInstant(instantFromEpoch(number.longValue()), ZoneId.systemDefault());
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (isEpochText(text)) {
            return LocalDateTime.ofInstant(instantFromEpoch(Long.parseLong(text)), ZoneId.systemDefault());
        }
        for (DateTimeFormatter formatter : LOCAL_DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(text).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(text).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException("无法解析日期时间: " + text);
    }

    private Instant convertToInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            return instantFromEpoch(number.longValue());
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (isEpochText(text)) {
            return instantFromEpoch(Long.parseLong(text));
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        return convertToLocalDateTime(text).atZone(ZoneId.systemDefault()).toInstant();
    }

    private OffsetDateTime convertToOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Number number) {
            return instantFromEpoch(number.longValue()).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (isEpochText(text)) {
            return instantFromEpoch(Long.parseLong(text)).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(text).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(text).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
        }
        return convertToLocalDateTime(text).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private ZonedDateTime convertToZonedDateTime(Object value) {
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime;
        }
        if (value instanceof Number number) {
            return instantFromEpoch(number.longValue()).atZone(ZoneId.systemDefault());
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (isEpochText(text)) {
            return instantFromEpoch(Long.parseLong(text)).atZone(ZoneId.systemDefault());
        }
        try {
            return ZonedDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toZonedDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(text).atZone(ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
        }
        return convertToLocalDateTime(text).atZone(ZoneId.systemDefault());
    }

    private boolean isEpochText(String text) {
        return text.matches("^-?\\d{10,17}$");
    }

    private Instant instantFromEpoch(long epochValue) {
        long normalized = Math.abs(epochValue) < 100_000_000_000L ? epochValue * 1000 : epochValue;
        return Instant.ofEpochMilli(normalized);
    }

    private Collection<Object> instantiateCollection(Class<?> targetType) {
        if (targetType.isInterface()) {
            if (Set.class.isAssignableFrom(targetType)) {
                return new LinkedHashSet<>();
            }
            return new ArrayList<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Collection<Object> collection = (Collection<Object>) targetType.getDeclaredConstructor().newInstance();
            return collection;
        } catch (Exception e) {
            if (Set.class.isAssignableFrom(targetType)) {
                return new LinkedHashSet<>();
            }
            return new ArrayList<>();
        }
    }

    private Class<?> extractCollectionElementType(Type genericType) {
        return resolveRawClass(extractCollectionElementGenericType(genericType));
    }

    private Type extractCollectionElementGenericType(Type genericType) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return null;
        }
        Type[] actualTypes = parameterizedType.getActualTypeArguments();
        return actualTypes.length == 0 ? null : actualTypes[0];
    }

    private Type extractMapKeyType(Type genericType) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return null;
        }
        Type[] actualTypes = parameterizedType.getActualTypeArguments();
        return actualTypes.length > 0 ? actualTypes[0] : null;
    }

    private Type extractMapValueType(Type genericType) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return null;
        }
        Type[] actualTypes = parameterizedType.getActualTypeArguments();
        return actualTypes.length > 1 ? actualTypes[1] : null;
    }

    private Type extractOptionalWrappedType(Type genericType) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return null;
        }
        Type[] actualTypes = parameterizedType.getActualTypeArguments();
        return actualTypes.length > 0 ? actualTypes[0] : null;
    }

    private Class<?> resolveRawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> rawClass) {
            return rawClass;
        }
        return null;
    }

    private Object convertBean(Map<?, ?> mapValue, Class<?> targetType) {
        try {
            Object bean = targetType.getDeclaredConstructor().newInstance();
            Map<String, Object> normalizedSource = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (entry.getKey() != null) {
                    registerAlias(normalizedSource, String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            Set<String> assignedFields = new HashSet<>();
            for (Field field : getAllFields(targetType)) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String normalizedFieldName = normalizeName(field.getName());
                if (!normalizedSource.containsKey(normalizedFieldName)) {
                    continue;
                }
                Object rawFieldValue = normalizedSource.get(normalizedFieldName);
                field.setAccessible(true);
                field.set(bean, convertType(rawFieldValue, field.getType(), field.getGenericType()));
                assignedFields.add(normalizedFieldName);
            }
            applySetterValues(bean, targetType, normalizedSource, assignedFields);
            return bean;
        } catch (Exception e) {
            throw new IllegalArgumentException("复杂参数类型转换失败: " + targetType.getSimpleName(), e);
        }
    }

    private void registerAlias(Map<String, Object> target, String rawKey, Object value) {
        if (rawKey == null || rawKey.trim().isEmpty()) {
            return;
        }
        String trimmed = rawKey.trim();
        target.putIfAbsent(normalizeName(trimmed), value);
        target.putIfAbsent(normalizeName(toSnakeCase(trimmed)), value);
        target.putIfAbsent(normalizeName(toKebabCase(trimmed)), value);
    }

    private List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    private void applySetterValues(Object bean, Class<?> targetType, Map<String, Object> normalizedSource,
                                   Set<String> assignedFields) throws ReflectiveOperationException {
        for (Method method : targetType.getMethods()) {
            if (!isSetter(method)) {
                continue;
            }
            String propertyName = normalizeName(method.getName().substring(3));
            if (assignedFields.contains(propertyName) || !normalizedSource.containsKey(propertyName)) {
                continue;
            }
            Object rawValue = normalizedSource.get(propertyName);
            Object convertedValue = convertType(rawValue, method.getParameterTypes()[0], method.getGenericParameterTypes()[0]);
            method.invoke(bean, convertedValue);
            assignedFields.add(propertyName);
        }
    }

    private boolean isSetter(Method method) {
        return method.getName().startsWith("set")
                && method.getName().length() > 3
                && method.getParameterCount() == 1
                && method.getReturnType() == void.class;
    }

    /**
     * 获取指定类型的默认值
     */
    private Object getDefaultForType(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == double.class) return 0.0;
            if (type == float.class) return 0f;
            if (type == short.class) return (short) 0;
            if (type == byte.class) return (byte) 0;
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
        }
        return null;
    }

    private Object getSemanticDefaultValue(java.lang.reflect.Parameter param, ToolMethodSchema schema,
                                           String paramName, String annotatedName, Class<?> paramType) {
        String semanticName = resolveSemanticParameterName(param, schema, paramName, annotatedName);
        String normalizedName = normalizeName(semanticName);
        if (normalizedName.isEmpty()) {
            normalizedName = normalizeName(paramName);
        }
        if (paramType == int.class || paramType == Integer.class) {
            if (normalizedName.contains("limit") || normalizedName.contains("size")
                    || normalizedName.contains("count") || normalizedName.contains("top")
                    || normalizedName.contains("pageSize".toLowerCase(Locale.ROOT))) {
                Integer parsed = extractIntegerDefault(semanticName);
                return parsed != null ? parsed : 10;
            }
            if (normalizedName.contains("page") || normalizedName.contains("index")) {
                Integer parsed = extractIntegerDefault(semanticName);
                return parsed != null ? parsed : 1;
            }
        }
        if (paramType == long.class || paramType == Long.class) {
            if (normalizedName.contains("limit") || normalizedName.contains("size") || normalizedName.contains("count")) {
                Integer parsed = extractIntegerDefault(semanticName);
                return parsed != null ? parsed.longValue() : 10L;
            }
        }
        if (paramType == boolean.class || paramType == Boolean.class) {
            if (normalizedName.contains("enable") || normalizedName.contains("enabled")
                    || normalizedName.contains("include") || normalizedName.contains("with")) {
                return false;
            }
        }
        return null;
    }

    private String resolveSemanticParameterName(java.lang.reflect.Parameter param, ToolMethodSchema schema,
                                                String paramName, String annotatedName) {
        if (annotatedName != null && !annotatedName.trim().isEmpty()) {
            return annotatedName.trim();
        }
        if (schema != null) {
            List<String> candidates = schema.getCandidatesFor(paramName, annotatedName);
            if (candidates != null && !candidates.isEmpty()) {
                return candidates.get(0);
            }
        }
        if (param != null && param.isNamePresent() && param.getName() != null && !param.getName().trim().isEmpty()) {
            return param.getName().trim();
        }
        return paramName;
    }

    private Integer extractIntegerDefault(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = Pattern.compile("默认\\s*(\\d+)|default\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (matcher.find()) {
            String matched = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (matched != null && !matched.isEmpty()) {
                return Integer.parseInt(matched);
            }
        }
        Matcher firstNumber = Pattern.compile("(\\d+)").matcher(text);
        if (firstNumber.find()) {
            return Integer.parseInt(firstNumber.group(1));
        }
        return null;
    }

    /**
     * 轻量 JSON 解析器，避免额外依赖。
     */
    private static final class JsonParser {
        private final String text;
        private int index;

        private JsonParser(String text) {
            this.text = text;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw new IllegalArgumentException("JSON 内容为空");
            }
            char current = text.charAt(index);
            return switch (current) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> {
                    if (current == '-' || Character.isDigit(current)) {
                        yield parseNumber();
                    }
                    throw new IllegalArgumentException("非法 JSON 字符: " + current);
                }
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == '"') {
                    return sb.toString();
                }
                if (current == '\\') {
                    if (index >= text.length()) {
                        throw new IllegalArgumentException("非法 JSON 转义");
                    }
                    char escaped = text.charAt(index++);
                    switch (escaped) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> sb.append(parseUnicode());
                        default -> throw new IllegalArgumentException("未知 JSON 转义: \\" + escaped);
                    }
                    continue;
                }
                sb.append(current);
            }
            throw new IllegalArgumentException("JSON 字符串未闭合");
        }

        private char parseUnicode() {
            if (index + 4 > text.length()) {
                throw new IllegalArgumentException("非法 Unicode 转义");
            }
            String hex = text.substring(index, index + 4);
            index += 4;
            return (char) Integer.parseInt(hex, 16);
        }

        private Object parseNumber() {
            int start = index;
            if (text.charAt(index) == '-') {
                index++;
            }
            consumeDigits();
            boolean floating = false;
            if (peek('.')) {
                floating = true;
                index++;
                consumeDigits();
            }
            if (peek('e') || peek('E')) {
                floating = true;
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                consumeDigits();
            }
            String numberText = text.substring(start, index);
            if (floating) {
                return Double.parseDouble(numberText);
            }
            long longValue = Long.parseLong(numberText);
            return (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE)
                    ? (int) longValue
                    : longValue;
        }

        private Object parseLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) {
                throw new IllegalArgumentException("非法 JSON 字面量");
            }
            index += literal.length();
            return value;
        }

        private void consumeDigits() {
            int start = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("非法数字格式");
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != expected) {
                throw new IllegalArgumentException("期望字符 '" + expected + "'");
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }
    }

    public static final class FallbackParameterReport {
        private final String parameterName;
        private final String matchedKey;
        private final String strategy;
        private final String sourceType;
        private final String targetType;
        private final boolean usedDefault;
        private final boolean explicitNull;
        private final String message;

        private FallbackParameterReport(String parameterName, String matchedKey, String strategy,
                                        String sourceType, String targetType,
                                        boolean usedDefault, boolean explicitNull, String message) {
            this.parameterName = parameterName;
            this.matchedKey = matchedKey;
            this.strategy = strategy;
            this.sourceType = sourceType;
            this.targetType = targetType;
            this.usedDefault = usedDefault;
            this.explicitNull = explicitNull;
            this.message = message;
        }

        private FallbackParameterReport copy() {
            return new FallbackParameterReport(parameterName, matchedKey, strategy, sourceType, targetType,
                    usedDefault, explicitNull, message);
        }

        private static FallbackParameterReport success(String parameterName, String matchedKey, String strategy,
                                                       String sourceType, String targetType,
                                                       boolean usedDefault, boolean explicitNull, String message) {
            return new FallbackParameterReport(parameterName, matchedKey, strategy, sourceType, targetType,
                    usedDefault, explicitNull, message);
        }

        private static FallbackParameterReport failure(String parameterName, String matchedKey, String strategy,
                                                       String sourceType, String targetType, String message) {
            return new FallbackParameterReport(parameterName, matchedKey, strategy, sourceType, targetType,
                    false, false, message);
        }

        private static FallbackParameterReport defaulted(String parameterName, String targetType,
                                                         String strategy, String defaultValue) {
            String resolvedStrategy = strategy != null ? strategy : "default";
            String message = defaultValue != null
                    ? "used default value: " + defaultValue
                    : "used default value";
            return new FallbackParameterReport(parameterName, "<default>", resolvedStrategy,
                    null, targetType, true, false, message);
        }

        private static FallbackParameterReport explicitNull(String parameterName, String matchedKey,
                                                            String strategy, String targetType) {
            return new FallbackParameterReport(parameterName, matchedKey, strategy,
                    null, targetType, false, true, "explicit null");
        }

        private String describe() {
            return parameterName + "<-" + matchedKey + " [" + strategy + "] " + message;
        }

        public String getParameterName() {
            return parameterName;
        }

        public String getMatchedKey() {
            return matchedKey;
        }

        public String getStrategy() {
            return strategy;
        }

        public String getSourceType() {
            return sourceType;
        }

        public String getTargetType() {
            return targetType;
        }

        public boolean isUsedDefault() {
            return usedDefault;
        }

        public boolean isExplicitNull() {
            return explicitNull;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class FallbackReport {
        private String toolName;
        private String candidateToolName;
        private String strategy;
        private boolean retried;
        private boolean success;
        private String errorMessage;
        private String schemaDescription;
        private int resultLength;
        private long startedAt;
        private long finishedAt;
        private final List<FallbackParameterReport> parameterReports = new ArrayList<>();

        private FallbackReport copy() {
            FallbackReport copied = new FallbackReport();
            copied.toolName = toolName;
            copied.candidateToolName = candidateToolName;
            copied.strategy = strategy;
            copied.retried = retried;
            copied.success = success;
            copied.errorMessage = errorMessage;
            copied.schemaDescription = schemaDescription;
            copied.resultLength = resultLength;
            copied.startedAt = startedAt;
            copied.finishedAt = finishedAt;
            for (FallbackParameterReport parameterReport : parameterReports) {
                copied.parameterReports.add(parameterReport.copy());
            }
            return copied;
        }

        private String describe() {
            List<String> paramDescriptions = new ArrayList<>();
            for (FallbackParameterReport parameterReport : parameterReports) {
                paramDescriptions.add(parameterReport.describe());
            }
            return "tool=" + toolName
                    + ", candidateTool=" + candidateToolName
                    + ", strategy=" + strategy
                    + ", retried=" + retried
                    + ", success=" + success
                    + ", durationMs=" + Math.max(0, finishedAt - startedAt)
                    + ", resultLength=" + resultLength
                    + ", error=" + errorMessage
                    + ", schema=" + schemaDescription
                    + ", params=" + paramDescriptions;
        }

        public String getToolName() {
            return toolName;
        }

        public String getCandidateToolName() {
            return candidateToolName;
        }

        public String getEffectiveToolName() {
            return candidateToolName != null && !candidateToolName.isEmpty() ? candidateToolName : toolName;
        }

        public String getStrategy() {
            return strategy;
        }

        public boolean isRetried() {
            return retried;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getSchemaDescription() {
            return schemaDescription;
        }

        public int getResultLength() {
            return resultLength;
        }

        public long getStartedAt() {
            return startedAt;
        }

        public long getFinishedAt() {
            return finishedAt;
        }

        public long getDurationMs() {
            return Math.max(0, finishedAt - startedAt);
        }

        public List<FallbackParameterReport> getParameterReports() {
            List<FallbackParameterReport> copies = new ArrayList<>();
            for (FallbackParameterReport parameterReport : parameterReports) {
                copies.add(parameterReport.copy());
            }
            return Collections.unmodifiableList(copies);
        }
    }

    private static final class ToolParameterSchema {
        private final String paramName;
        private final String annotatedName;
        private final Class<?> paramType;
        private final Type genericType;
        private final List<String> candidates;

        private ToolParameterSchema(String paramName, String annotatedName, Class<?> paramType,
                                    Type genericType, List<String> candidates) {
            this.paramName = paramName;
            this.annotatedName = annotatedName;
            this.paramType = paramType;
            this.genericType = genericType;
            this.candidates = candidates;
        }
    }

    private static final class ToolMethodSchema {
        private final String methodName;
        private final List<ToolParameterSchema> parameters;

        private ToolMethodSchema(String methodName, List<ToolParameterSchema> parameters) {
            this.methodName = methodName;
            this.parameters = parameters;
        }

        private List<String> getCandidatesFor(String paramName, String annotatedName) {
            for (ToolParameterSchema parameter : parameters) {
                boolean sameParamName = Objects.equals(parameter.paramName, paramName);
                boolean sameAnnotatedName = Objects.equals(parameter.annotatedName, annotatedName);
                if (sameParamName || sameAnnotatedName) {
                    return parameter.candidates;
                }
            }
            return Collections.emptyList();
        }

        private boolean matchesTypeHint(String key, Class<?> targetType) {
            String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if ((normalizedKey.contains("time") || normalizedKey.contains("date"))
                    && (targetType == LocalDate.class || targetType == LocalDateTime.class
                    || targetType == Instant.class || targetType == OffsetDateTime.class
                    || targetType == ZonedDateTime.class)) {
                return true;
            }
            if ((normalizedKey.contains("count") || normalizedKey.contains("limit") || normalizedKey.contains("size")
                    || normalizedKey.contains("num"))
                    && Number.class.isAssignableFrom(targetType)) {
                return true;
            }
            if ((normalizedKey.contains("flag") || normalizedKey.contains("enabled") || normalizedKey.contains("disable")
                    || normalizedKey.contains("switch") || normalizedKey.contains("is"))
                    && (targetType == boolean.class || targetType == Boolean.class)) {
                return true;
            }
            if ((normalizedKey.contains("list") || normalizedKey.contains("ids") || normalizedKey.contains("names"))
                    && (targetType.isArray() || Collection.class.isAssignableFrom(targetType))) {
                return true;
            }
            return false;
        }

        private String describe() {
            List<String> parameterDescriptions = new ArrayList<>();
            for (ToolParameterSchema parameter : parameters) {
                parameterDescriptions.add(parameter.paramName + ":" + parameter.paramType.getSimpleName()
                        + " candidates=" + parameter.candidates);
            }
            return methodName + " -> " + String.join("; ", parameterDescriptions);
        }
    }

    private static final class ArgumentMatchScore {
        private final Object value;
        private final String matchedKey;
        private final String strategy;
        private final int score;

        private ArgumentMatchScore(Object value, String matchedKey, String strategy, int score) {
            this.value = value;
            this.matchedKey = matchedKey;
            this.strategy = strategy;
            this.score = score;
        }

        private int score() {
            return score;
        }
    }

    private static final class MatchedArgument {
        private final Object value;
        private final String matchedKey;
        private final String strategy;

        private MatchedArgument(Object value, String matchedKey, String strategy) {
            this.value = value;
            this.matchedKey = matchedKey;
            this.strategy = strategy;
        }

        private static MatchedArgument of(Object value, String matchedKey, String strategy) {
            return new MatchedArgument(value, matchedKey, strategy);
        }

        private static MatchedArgument notMatched() {
            return new MatchedArgument(null, "<unmatched>", "none");
        }
    }

    private static final class FallbackExecutionResult {
        private final boolean success;
        private final Object result;
        private final Exception error;
        private final String errorMessage;

        private FallbackExecutionResult(boolean success, Object result, Exception error, String errorMessage) {
            this.success = success;
            this.result = result;
            this.error = error;
            this.errorMessage = errorMessage;
        }

        private static FallbackExecutionResult success(Object result) {
            return new FallbackExecutionResult(true, result, null, null);
        }

        private static FallbackExecutionResult failure(Exception error) {
            return new FallbackExecutionResult(false, null, error, error != null ? error.getMessage() : null);
        }
    }

    /**
     * 工具方法绑定
     */
    private static final class ToolMethodBinding {
        private final Object toolInstance;
        private final Method method;

        private ToolMethodBinding(Object toolInstance, Method method) {
            this.toolInstance = toolInstance;
            this.method = method;
        }
    }

    private static final class ToolProviderEntry {
        private final ToolSpecification specification;
        private final ToolExecutor executor;

        private ToolProviderEntry(ToolSpecification specification, ToolExecutor executor) {
            this.specification = specification;
            this.executor = executor;
        }
    }

    // ==================== 增强功能方法 ====================

    /**
     * 构建增强的问题（包含多轮对话上下文摘要）
     */
    private String buildEnhancedQuestion(AgentContext context, String question) {
        return buildEnhancedQuestion(context, question, toolRecommender.recommend(question));
    }

    private String buildEnhancedQuestion(AgentContext context, String question,
                                         ToolRecommender.Recommendation recommendation) {
        StringBuilder enhanced = new StringBuilder();

        // 尝试获取会话上下文
        ConversationMemoryService.ConversationSession activeSession = conversationMemory.getActiveSession(context.getUserId());
        if (activeSession != null && activeSession.getMessageCount() > 2) {
            String ctxSummary = conversationMemory.buildContextForLLM(activeSession.getSessionId(), 5);
            if (ctxSummary != null && !ctxSummary.isEmpty()) {
                enhanced.append("[之前的对话上下文]\n").append(ctxSummary).append("\n\n");
                logger.debug("Added conversation context for user {}", context.getUserId());
            }
        }

        String scenarioGuide = buildScenarioGuide(question, recommendation);
        if (scenarioGuide != null && !scenarioGuide.isEmpty()) {
            enhanced.append(scenarioGuide).append("\n\n");
        }

        if (context != null && context.getPageContext() != null && !context.getPageContext().trim().isEmpty()) {
            enhanced.append("[当前页面上下文]\n").append(context.getPageContext()).append("\n");
            enhanced.append("[页面上下文使用要求]\n");
            enhanced.append("如果当前问题只给出方法名或部分类名，必须优先从当前页面上下文、覆盖率详情、代码关系页面或上一轮对话中识别真实类名/方法名；仍无法唯一确定时先说明无法确定，不要使用示例类、示例方法或猜测包名。\n\n");
        }

        enhanced.append("[当前问题]\n").append(question);

        // 记录到对话记忆（异步）
        conversationMemory.addUserMessage(
                activeSession != null ? activeSession.getSessionId() : createOrGetSession(context),
                question);

        return enhanced.toString();
    }

    private String buildScenarioGuide(String question, ToolRecommender.Recommendation recommendation) {
        if (question == null || question.trim().isEmpty() || recommendation == null) {
            return "";
        }

        String normalized = question.toLowerCase(Locale.ROOT);
        List<String> preferredTools = new ArrayList<>();
        String scenario = null;
        String answerFocus = null;

        if (containsAny(normalized, "业务需求", "业务逻辑", "业务规则", "业务场景", "处理什么业务", "需求分析", "功能逻辑", "方法职责")) {
            scenario = "源码业务逻辑分析";
            preferredTools.addAll(Arrays.asList("searchCodeRelation", "analyzeBusinessRequirement", "getClassCallGraph", "detectBugsInMethod", "detectBugs"));
            answerFocus = "必须先用 searchCodeRelation 定位源码中的真实类/方法；再用 analyzeBusinessRequirement 分析真实源码，必要时用 getClassCallGraph 补充真实上下游。只能基于源码中的真实类名、方法名、参数、分支和返回值分析业务规则。无法从源码确认的需求要明确说明，禁止使用示例类名、示例链接或猜测的业务流程。";
        } else if (containsAny(normalized, "bug", "可能存在", "潜在bug", "潜在问题", "代码缺陷", "源码缺陷", "空指针", "资源泄漏", "并发问题", "逻辑错误")) {
            scenario = "源码 Bug 检测";
            preferredTools.addAll(Arrays.asList("searchCodeRelation", "detectBugsInMethod", "batchDetectBugs", "detectBugs", "getCodeQualityReport"));
            answerFocus = "必须先用 searchCodeRelation 定位源码中的真实类/方法；用户问方法必须优先调用 detectBugsInMethod，用户问多个类/批量扫描必须优先调用 batchDetectBugs，用户问单个类再调用 detectBugs。直接输出潜在 bug、触发条件、影响和修复建议；不要改查调用链，也不要回答“未找到调用链数据”。";
        } else if (containsAny(normalized, "版本上线", "上线前", "发布前", "精准回归", "回归范围", "回归策略")) {
            scenario = "版本上线前的精准回归";
            preferredTools.addAll(Arrays.asList("getProjectCoverageOverview", "getLowCoverageClasses", "recommendTestcases", "compareCoverage", "searchCodeRelation", "getSnapshots"));
            answerFocus = "输出变更影响面、覆盖缺口、高风险模块、推荐回归用例、上线前准入风险；缺少版本/应用/接口信息时，先用项目级覆盖率、快照、低覆盖类等数据给出可执行排查路径，不要只回答方法论。";
        } else if (containsAny(normalized, "调用链", "调用关系", "上下游", "谁调用", "调用了谁", "依赖关系")
                && containsAny(normalized, "方法", "method", "函数")) {
            scenario = "方法真实调用关系分析";
            preferredTools.addAll(Arrays.asList("searchCodeRelation", "getClassCallGraph", "getCallGraph", "analyzeMethodCallChain", "analyzeCallChain"));
            answerFocus = "必须先用 searchCodeRelation 根据类名/方法名定位真实代码对象；类级调用图优先用 getClassCallGraph，方法级调用关系用 getCallGraph 或 analyzeMethodCallChain。只输出工具返回的真实调用方、被调用方和 Trace 数据；如果工具返回无真实调用关系数据，要明确说明暂无真实数据，禁止生成 example.com 链接、示意图链接、methodA/helperMethod 或任何源码中不存在的方法。";
        } else if (containsAny(normalized, "覆盖率是多少", "这个项目的代码覆盖率", "项目覆盖率", "整体覆盖率", "代码覆盖情况", "覆盖率概览")) {
            scenario = "项目覆盖率概览";
            preferredTools.addAll(Arrays.asList("getProjectCoverageOverview", "getCoverageReports", "getAppCoverageReport", "getAppCoverageTrend"));
            answerFocus = "直接输出项目总体覆盖率结果、报告时间、行/分支/方法覆盖率；如果用户继续追问模块或类，则切换到低覆盖类或高复杂度分析，不要改答成提升建议。";
        } else if (containsAny(normalized, "哪个模块的覆盖率最低", "模块覆盖率最低", "覆盖率最低的模块", "低覆盖模块", "低覆盖类", "未覆盖类")) {
            scenario = "低覆盖模块定位";
            preferredTools.addAll(Arrays.asList("getLowCoverageClasses", "getClassCoverageList", "recommendTestcases", "getCoverageImprovementSuggestions"));
            answerFocus = "输出覆盖率最低的模块/类清单、覆盖率数值、风险原因和优先补测建议；不要泛泛给出提升覆盖率的通用方法。";
        } else if (containsAny(normalized, "复杂度", "高复杂度", "圈复杂度", "代码复杂度", "复杂度高")) {
            scenario = "高复杂度分析";
            preferredTools.addAll(Arrays.asList("getHighComplexityMethods", "getCodeQualityReport", "getLowCoverageClasses"));
            answerFocus = "输出高复杂度模块或方法清单、复杂度值、关联覆盖率和重构优先级；如果用户问的是模块则按模块回答，不要跳成覆盖率提升方法。";
        } else if (containsAny(normalized, "线上缺陷", "快速定位", "故障定位", "根因定位", "异常定位")) {
            scenario = "线上缺陷快速定位";
            preferredTools.addAll(Arrays.asList("getDefectOverview", "getRecentExceptions", "getAppErrorDetails", "getRecentTraces", "locateRootCause", "analyzeCallChain"));
            answerFocus = "输出错误现象、异常分布、相关调用链、疑似根因、验证步骤和下一步处置建议；无法拿到成功/失败 TraceID 时，先用最近异常和错误请求缩小范围。";
        } else if (containsAny(normalized, "低覆盖", "高风险", "补测", "测试盲区", "覆盖缺口")) {
            scenario = "低覆盖高风险模块补测";
            preferredTools.addAll(Arrays.asList("getLowCoverageClasses", "recommendTestcases", "getCoverageImprovementSuggestions", "getHighComplexityMethods", "compareCoverage"));
            answerFocus = "输出模块优先级、风险原因、缺失场景、补测用例建议、预期覆盖提升；优先结合低覆盖、复杂度、调用关系和缺陷数据排序。";
        } else if (containsAny(normalized, "性能回归", "性能退化", "耗时变慢", "基线对比", "回归分析")) {
            scenario = "性能回归分析";
            preferredTools.addAll(Arrays.asList("compareOverTime", "getAppPerformanceOverview", "getSlowEndpoints", "getEndpointCallFrequency", "analyzeUrlCallPattern"));
            answerFocus = "输出基线对比、退化接口、P95/P99/平均耗时变化、慢链路节点、可能原因和验证建议；缺少接口时先找慢接口和性能概览。";
        }

        if (scenario == null) {
            return "[AI工具推荐]\n" + formatToolRecommendation(recommendation)
                    + "\n请优先调用最相关的1个工具获取实时数据；如果已经足够回答，立即停止工具调用并生成最终答案。最多连续调用3个工具；如果数据缺失，说明缺失项并给出下一步，不要重复调用相同或相似工具。";
        }

        LinkedHashSet<String> tools = new LinkedHashSet<>();
        tools.add(recommendation.primaryTool);
        tools.addAll(recommendation.secondaryTools);
        tools.addAll(preferredTools);

        StringBuilder guide = new StringBuilder();
        guide.append("[识别到的精准测试场景]\n").append(scenario).append('\n');
        guide.append("[AI工具推荐]\n").append(formatToolRecommendation(recommendation)).append('\n');
        guide.append("[建议优先尝试的工具]\n");
        int count = 0;
        for (String toolName : tools) {
            if (toolName == null || toolName.trim().isEmpty()) {
                continue;
            }
            guide.append("- ").append(describeTool(toolName)).append('\n');
            if (++count >= 6) {
                break;
            }
        }
        guide.append("[回答要求]\n").append(answerFocus).append('\n');
        guide.append("请按『结论摘要 / 关键数据 / 风险排序 / 建议动作』组织回答；优先使用最相关的1个工具，最多连续调用3个工具；拿到足够数据后必须立即停止工具调用并回答；不要要求用户提供内部ID、JSON或工具参数。");
        return guide.toString();
    }

    private String formatToolRecommendation(ToolRecommender.Recommendation recommendation) {
        StringBuilder sb = new StringBuilder();
        sb.append("意图=").append(recommendation.detectedIntent)
                .append("，首选=").append(describeTool(recommendation.primaryTool));
        if (recommendation.secondaryTools != null && !recommendation.secondaryTools.isEmpty()) {
            sb.append("，辅助=");
            for (int i = 0; i < recommendation.secondaryTools.size(); i++) {
                if (i > 0) {
                    sb.append("、");
                }
                sb.append(describeTool(recommendation.secondaryTools.get(i)));
            }
        }
        if (recommendation.reasoning != null && !recommendation.reasoning.isEmpty()) {
            sb.append("，原因=").append(recommendation.reasoning);
        }
        return sb.toString();
    }

    private String describeTool(String toolName) {
        return toolRecommender.getToolMeta(toolName)
                .map(meta -> meta.getDisplayName() + "：" + meta.getDescription())
                .orElse(toolName);
    }

    private boolean isToolCatalogQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return containsAny(normalized,
                "支持哪些工具", "有哪些工具", "有什么工具", "工具列表", "工具清单",
                "支持什么工具", "能用哪些工具", "可以用哪些工具", "你有哪些工具",
                "你有什么工具", "ai助手有哪些工具", "平台助手有哪些工具");
    }

    private String buildToolCatalogResponse() {
        Map<String, String> categoryNames = new LinkedHashMap<>();
        categoryNames.put("project_info", "项目与应用");
        categoryNames.put("app_status", "项目与应用");
        categoryNames.put("coverage", "覆盖率分析");
        categoryNames.put("trace", "调用链路");
        categoryNames.put("performance", "性能分析");
        categoryNames.put("defect", "缺陷与异常");
        categoryNames.put("testcase", "测试推荐");
        categoryNames.put("code_relation", "代码关系");
        categoryNames.put("code_quality", "代码质量");
        categoryNames.put("snapshot", "快照数据");
        categoryNames.put("bug_detect", "AI Bug检测");
        categoryNames.put("business_logic", "源码业务逻辑");

        Map<String, List<String>> groupedTools = new LinkedHashMap<>();
        for (String category : new LinkedHashSet<>(categoryNames.values())) {
            groupedTools.put(category, new ArrayList<>());
        }
        groupedTools.put("其他能力", new ArrayList<>());

        for (ToolRecommender.ToolMeta meta : toolRecommender.getAllTools()) {
            String category = "其他能力";
            if (meta.relatedIntents != null) {
                for (String intent : meta.relatedIntents) {
                    if (categoryNames.containsKey(intent)) {
                        category = categoryNames.get(intent);
                        break;
                    }
                }
            }
            String item = meta.getDisplayName() + "：" + meta.getDescription();
            List<String> items = groupedTools.get(category);
            if (!items.contains(item)) {
                items.add(item);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("我支持以下工具能力（展示的是工具名称，不是内部方法名）：\n");
        for (Map.Entry<String, List<String>> entry : groupedTools.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            sb.append("\n**").append(entry.getKey()).append("**\n");
            for (String item : entry.getValue()) {
                sb.append("- ").append(item).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String createOrGetSession(AgentContext context) {
        ConversationMemoryService.ConversationSession existing = conversationMemory.getActiveSession(context.getUserId());
        if (existing != null) return existing.getSessionId();
        return conversationMemory.createSession(context.getUserId(), context.getProjectId());
    }

    /**
     * 记录AI回复到对话记忆
     */
    public void recordAssistantResponse(AgentContext context, String answer) {
        ConversationMemoryService.ConversationSession session = conversationMemory.getActiveSession(context.getUserId());
        if (session != null) {
            conversationMemory.addAssistantMessage(session.getSessionId(), answer, null);
        }
    }

    // ==================== 公开访问接口 ====================

    public com.oAT.ai.agent.ToolRecommender getToolRecommender() { return toolRecommender; }
    public DynamicLLMSwitcher getLlmSwitcher() { return llmSwitcher; }

    /**
     * 获取自主学习服务（懒加载）
     */
    public AISelfLearningService getSelfLearningService() {
        if (selfLearningService == null) {
            synchronized (this) {
                if (selfLearningService == null) {
                    // 使用系统属性获取数据路径
                    String dataPath = System.getProperty("oat.data.path",
                            System.getProperty("user.home") + "/oAT/codeData");
                    FeedbackPersistenceService fps = new FeedbackPersistenceService(dataPath);
                    selfLearningService = new AISelfLearningService(fps,
                            Math.max(1, Integer.getInteger("ai.enhanced.self-learning.interval-hours", 6)));
                    selfLearningService.setToolRecommender(toolRecommender);
                }
            }
        }
        return selfLearningService;
    }

    /**
     * 获取 AI 增强服务综合统计
     */
    public Map<String, Object> getEnhancedStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("semanticCache", semanticCacheService.getStats());
        stats.put("toolRecommender", toolRecommender.getStats());
        stats.put("llmSwitcher", llmSwitcher.getStats());
        stats.put("conversationMemory", conversationMemory.getStats());
        stats.put("fallbackReports", getRecentFallbackReports(5));
        stats.put("agentAvailable", isAvailable());
        return stats;
    }
}
