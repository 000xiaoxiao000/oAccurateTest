package com.oAT.ai.agent;

import com.oAT.ai.agent.cache.RedisCacheService;
import com.oAT.ai.agent.cache.SemanticCacheService;
import com.oAT.ai.agent.tools.*;
import com.oAT.ai.config.AIConfig;
import com.oAT.ai.config.AIConfigProperties;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.security.MessageDigest;
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

    /** 匹配 {"name": "xxx", "arguments": {...}} 格式的工具调用（支持 ```json 代码块包裹） */
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "(?:```\\s*json\\s*)?\\{\\s*\"name\"\\s*:\\s*\"(\\w+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{.*?})\\s*\\}(?:```)?",
            Pattern.DOTALL);

    private final AIAgent aiAgent;
    private final AIConfigProperties configProperties;
    private final AgentDataProvider dataProvider;

    /** 工具实例映射：方法名 → 工具对象 */
    private final Map<String, Object> toolInstances = new LinkedHashMap<>();

    /** 语义缓存服务（用于相似问题命中） */
    private final SemanticCacheService semanticCacheService;

    /** 智能工具推荐器 */
    private final ToolRecommender toolRecommender;

    /** 动态LLM切换器 */
    private final DynamicLLMSwitcher llmSwitcher;

    /** 多轮对话记忆服务 */
    private final ConversationMemoryService conversationMemory;

    /** 自主学习服务 */
    private volatile AISelfLearningService selfLearningService;

    @Autowired
    public AIAgentService(ChatLanguageModel chatLanguageModel,
                          AIConfigProperties configProperties,
                          AgentDataProvider dataProvider,
                          AIConfig aiConfig) {
        this.configProperties = configProperties;
        this.dataProvider = dataProvider;

        // 初始化增强服务
        this.semanticCacheService = new SemanticCacheService(0.85, 500, null);
        this.toolRecommender = new ToolRecommender();
        this.llmSwitcher = new DynamicLLMSwitcher(aiConfig);
        this.conversationMemory = new ConversationMemoryService();

        // 注册所有内置工具到推荐器
        toolRecommender.registerAllBuiltInTools();

        if (chatLanguageModel == null) {
            logger.warn("ChatLanguageModel is null, AI Agent will not be available");
            this.aiAgent = null;
        } else {
            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
            List<Object> tools = createTools();
            this.aiAgent = AiServices.builder(AIAgent.class)
                    .chatLanguageModel(chatLanguageModel)
                    .chatMemory(chatMemory)
                    .tools(tools.toArray())
                    .build();
            logger.info("AI Agent initialized with {} tools", tools.size());
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
        registerTool(bugDetectTool);
        registerTool(callChainAnalysisTool);
        registerTool(callChainCompareTool);

        return tools;
    }

    /**
     * 注册工具实例到映射表
     * 扫描工具类中所有带 @Tool 注解的方法，建立 方法名→工具对象 的映射
     */
    private void registerTool(Object toolInstance) {
        for (Method method : toolInstance.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                toolInstances.put(method.getName(), toolInstance);
                logger.debug("Registered fallback tool: {} -> {}", method.getName(), toolInstance.getClass().getSimpleName());
            }
        }
    }

    public boolean isAvailable() {
        return aiAgent != null && configProperties.isEnabled();
    }

    /**
     * 与AI Agent对话（带工具调用兜底）
     * 集成语义缓存、智能工具推荐、多轮对话记忆
     */
    public String chat(AgentContext context, String question) {
        if (!isAvailable()) {
            return null;
        }
        try {
            AgentContext.setContext(context);

            // 1. 语义缓存检查（相似问题命中直接返回）
            SemanticCacheService.CachedResponse cached = semanticCacheService.get(question);
            if (cached != null && cached.isFromCache() && cached.getAnswer() != null) {
                logger.info("Semantic cache hit for question: {}",
                        question.length() > 50 ? question.substring(0, 50) + "..." : question);
                return cached.getAnswer();
            }

            // 2. 智能工具推荐（日志记录，供后续分析）
            ToolRecommender.Recommendation recommendation = toolRecommender.recommend(question);
            logger.debug("Tool recommendation: primary={}, intent={}, confidence={}",
                    recommendation.primaryTool, recommendation.detectedIntent, recommendation.confidence);

            // 3. 构建增强的上下文（包含多轮对话摘要）
            String enhancedQuestion = buildEnhancedQuestion(context, question);

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
                semanticCacheService.put(question, response, null);
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
        if (!isAvailable()) {
            return null;
        }
        try {
            context.setPageContext(pageContext);
            AgentContext.setContext(context);
            String response = aiAgent.chatWithContext(question, context.getProjectId(), context.getUserName(), pageContext);

            String fallbackResult = tryFallbackToolExecution(response);
            if (fallbackResult != null) {
                logger.info("Detected raw tool call in AI response (withContext), executed via fallback");
                return fallbackResult;
            }

            return response;
        } catch (Exception e) {
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
            String response = aiAgent.chatWithImage(question, context.getProjectId(), context.getUserName(),
                    pageContext, truncatedImage);

            String fallbackResult = tryFallbackToolExecution(response);
            if (fallbackResult != null) {
                logger.info("Detected raw tool call in AI response (withImage), executed via fallback");
                return fallbackResult;
            }

            return response;
        } catch (Exception e) {
            logger.error("AI Agent chat with image failed", e);
            return null;
        } finally {
            AgentContext.clearContext();
        }
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
        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        // 快速判断：如果包含 {"name": ... 且匹配已知工具名，则可能是原始工具调用
        Matcher matcher = TOOL_CALL_PATTERN.matcher(response.trim());
        if (!matcher.find()) {
            return null;
        }

        String toolName = matcher.group(1);
        String argsJson = matcher.group(2);

        // 验证工具名是否已注册
        Object toolInstance = toolInstances.get(toolName);
        if (toolInstance == null) {
            logger.debug("Response contains tool-like format but '{}' is not a registered tool, treating as normal response", toolName);
            return null;
        }

        logger.info("Fallback: executing tool '{}' with raw arguments: {}", toolName, argsJson);

        try {
            // 解析参数 JSON
            Map<String, Object> args = parseArgsJson(argsJson);

            // 查找目标方法
            Method targetMethod = findTargetMethod(toolInstance.getClass(), toolName, args);
            if (targetMethod == null) {
                logger.error("Fallback: cannot find matching method for tool '{}' with args {}", toolName, args.keySet());
                return "抱歉，AI 助手在查询数据时遇到了问题（方法签名不匹配），请稍后重试。";
            }

            // 构造方法参数
            Object[] invokeArgs = buildMethodArguments(targetMethod, args);

            // 执行方法
            targetMethod.setAccessible(true);
            Object result = targetMethod.invoke(toolInstance, invokeArgs);

            String resultStr = result != null ? result.toString() : "（无返回数据）";
            logger.info("Fallback: tool '{}' executed successfully, result length: {}", toolName, resultStr.length());
            return resultStr;

        } catch (Exception e) {
            logger.error("Fallback: failed to execute tool '{}': {}", toolName, e.getMessage(), e);
            return "抱歉，AI 助手在查询数据时遇到了错误：" + e.getMessage();
        }
    }

    /**
     * 简单的 JSON 参数解析（仅支持简单的键值对）
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> parseArgsJson(String argsJson) {
        Map<String, Object> args = new HashMap<>();
        if (argsJson == null || "{}".equals(argsJson.trim())) {
            return args;
        }
        try {
            // 去除首尾空白和花括号
            String inner = argsJson.trim();
            if (inner.startsWith("{")) inner = inner.substring(1);
            if (inner.endsWith("}")) inner = inner.substring(0, inner.length() - 1);

            // 简单解析 key:value 对（不依赖外部JSON库）
            String[] pairs = inner.split(",\\s*");
            for (String pair : pairs) {
                int colonIdx = pair.indexOf(':');
                if (colonIdx <= 0) continue;
                String key = pair.substring(0, colonIdx).trim().replace("\"", "");
                String value = pair.substring(colonIdx + 1).trim();

                // 解析值类型
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    args.put(key, value.substring(1, value.length() - 1));
                } else if ("true".equals(value)) {
                    args.put(key, Boolean.TRUE);
                } else if ("false".equals(value)) {
                    args.put(key, Boolean.FALSE);
                } else if ("null".equals(value)) {
                    args.put(key, null);
                } else {
                    try {
                        // 尝试解析为数字（支持整数和小数）
                        if (value.contains(".")) {
                            args.put(key, Double.parseDouble(value));
                        } else {
                            long lv = Long.parseLong(value);
                            // 如果是 int 范围内，用 Integer
                            args.put(key, (int) lv);
                        }
                    } catch (NumberFormatException nfe) {
                        args.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse tool arguments JSON: {}, error: {}", argsJson, e.getMessage());
        }
        return args;
    }

    /**
     * 查找与参数匹配的目标方法
     */
    private Method findTargetMethod(Class<?> toolClass, String methodName, Map<String, Object> args) {
        for (Method method : toolClass.getDeclaredMethods()) {
            if (!methodName.equals(method.getName())) {
                continue;
            }
            // 检查参数数量是否匹配（忽略非 String/基本类型的参数）
            java.lang.reflect.Parameter[] params = method.getParameters();
            int requiredParams = 0;
            for (java.lang.reflect.Parameter p : params) {
                if (!isInjectableParameter(p)) {
                    requiredParams++;
                }
            }
            if (args.size() <= requiredParams) {
                return method;
            }
        }
        // 回退：按名称查找第一个匹配的
        for (Method method : toolClass.getDeclaredMethods()) {
            if (methodName.equals(method.getName())) {
                return method;
            }
        }
        return null;
    }

    /**
     * 判断参数是否为可注入的上下文参数（如 @V、@MemoryId 等）
     */
    private boolean isInjectableParameter(java.lang.reflect.Parameter param) {
        return param.isAnnotationPresent(dev.langchain4j.service.V.class)
                || param.isAnnotationPresent(dev.langchain4j.service.MemoryId.class);
    }

    /**
     * 根据参数映射构建方法调用的实参数组
     */
    private Object[] buildMethodArguments(Method method, Map<String, Object> args) {
        java.lang.reflect.Parameter[] params = method.getParameters();
        Object[] invokeArgs = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            java.lang.reflect.Parameter param = params[i];
            String paramName = param.getName();
            Class<?> paramType = param.getType();

            // 跳过 LangChain4j 框架注入的参数 (@V 等)
            if (isInjectableParameter(param)) {
                invokeArgs[i] = null; // 框架会自动注入，反射调用时传 null 即可
                continue;
            }

            // 从 args 中获取参数值
            Object value = args.get(paramName);
            if (value == null && args.containsKey(paramName)) {
                // 显式传入的 null 值
                invokeArgs[i] = null;
                continue;
            }

            // 类型转换
            if (value != null) {
                invokeArgs[i] = convertType(value, paramType);
            } else {
                // 未提供该参数，使用默认值或 null
                invokeArgs[i] = getDefaultForType(paramType);
            }
        }

        return invokeArgs;
    }

    /**
     * 将参数值转换为目标类型
     */
    private Object convertType(Object value, Class<?> targetType) {
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        if (targetType == String.class) {
            return value.toString();
        }
        if (targetType == int.class || targetType == Integer.class) {
            if (value instanceof Number) return ((Number) value).intValue();
            return Integer.parseInt(value.toString());
        }
        if (targetType == long.class || targetType == Long.class) {
            if (value instanceof Number) return ((Number) value).longValue();
            return Long.parseLong(value.toString());
        }
        if (targetType == double.class || targetType == Double.class) {
            if (value instanceof Number) return ((Number) value).doubleValue();
            return Double.parseDouble(value.toString());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean) return value;
            return Boolean.parseBoolean(value.toString());
        }
        // 对于包装类型，尝试数字转换
        if (Number.class.isAssignableFrom(targetType) && value instanceof Number) {
            return value;
        }
        return value;
    }

    /**
     * 获取指定类型的默认值
     */
    private Object getDefaultForType(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == double.class) return 0.0;
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
        }
        return null;
    }

    // ==================== 增强功能方法 ====================

    /**
     * 构建增强的问题（包含多轮对话上下文摘要）
     */
    private String buildEnhancedQuestion(AgentContext context, String question) {
        StringBuilder enhanced = new StringBuilder();
        String sessionKey = context.getUserId() + ":" + context.getProjectId();

        // 尝试获取会话上下文
        ConversationMemoryService.ConversationSession activeSession = conversationMemory.getActiveSession(context.getUserId());
        if (activeSession != null && activeSession.getMessageCount() > 2) {
            String ctxSummary = conversationMemory.buildContextForLLM(activeSession.getSessionId(), 5);
            if (ctxSummary != null && !ctxSummary.isEmpty()) {
                enhanced.append("[之前的对话上下文]\n").append(ctxSummary).append("\n\n[当前问题]\n");
                logger.debug("Added conversation context for user {}", context.getUserId());
            }
        }

        enhanced.append(question);

        // 记录到对话记忆（异步）
        conversationMemory.addUserMessage(
                activeSession != null ? activeSession.getSessionId() : createOrGetSession(context),
                question);

        return enhanced.toString();
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

    public SemanticCacheService getSemanticCacheService() { return semanticCacheService; }
    public ToolRecommender getToolRecommender() { return toolRecommender; }
    public DynamicLLMSwitcher getLlmSwitcher() { return llmSwitcher; }
    public ConversationMemoryService getConversationMemory() { return conversationMemory; }

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
                    selfLearningService = new AISelfLearningService(fps);
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
        stats.put("agentAvailable", isAvailable());
        return stats;
    }
}
