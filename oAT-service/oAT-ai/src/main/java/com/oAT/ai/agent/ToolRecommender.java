package com.oAT.ai.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能工具推荐器
 * 根据用户问题内容，分析用户意图并推荐最可能需要的工具组合
 *
 * <p>功能：
 * 1. 意图识别：通过关键词匹配和模式识别判断用户意图
 * 2. 工具排序：根据意图对工具进行相关性评分
 * 3. 组合推荐：复杂问题可能需要多个工具协作
 * 4. 学习优化：根据工具调用成功率动态调整推荐权重
 * </p>
 */
public class ToolRecommender {

    private static final Logger logger = LoggerFactory.getLogger(ToolRecommender.class);

    /** 工具元数据 */
    public static class ToolMeta {
        final String toolName;          // 工具方法名
        final String displayName;       // 显示名称
        final String description;       // 功能描述
        final String[] keywords;        // 关联关键词
        final String[] relatedIntents;  // 相关意图标签
        double successRate = 1.0;       // 成功率（用于学习调整）
        int callCount = 0;              // 调用次数
        volatile long lastCalledTime = 0;

        public ToolMeta(String toolName, String displayName, String description,
                       String[] keywords, String[] relatedIntents) {
            this.toolName = toolName;
            this.displayName = displayName;
            this.description = description;
            this.keywords = keywords;
            this.relatedIntents = relatedIntents;
        }

        public String getToolName() { return toolName; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /** 推荐结果 */
    public static class Recommendation {
        public final String primaryTool;           // 首选工具
        public final List<String> secondaryTools;  // 辅助工具列表
        public final String detectedIntent;        // 检测到的意图
        public final double confidence;            // 置信度 (0-1)
        public final String reasoning;             // 推荐理由

        public Recommendation(String primaryTool, List<String> secondaryTools,
                            String intent, double confidence, String reasoning) {
            this.primaryTool = primaryTool;
            this.secondaryTools = secondaryTools != null ? secondaryTools : Collections.emptyList();
            this.detectedIntent = intent;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
    }

    /** 已注册的工具 */
    private final Map<String, ToolMeta> tools = new ConcurrentHashMap<>();

    /** 意图 → 关键词映射 */
    private static final Map<String, Set<String>> INTENT_KEYWORDS = new LinkedHashMap<>();

    static {
        INTENT_KEYWORDS.put("coverage", Set.of("覆盖", "coverage", "行覆盖", "分支覆盖",
                "mcdc", "测试率", "代码覆盖率", "未覆盖"));
        INTENT_KEYWORDS.put("performance", Set.of("性能", "performance", "慢接口",
                "响应时间", "p50", "p95", "p99", "延迟", "吞吐量", "调用频率", "回归", "退化", "基线"));
        INTENT_KEYWORDS.put("defect", Set.of("缺陷", "defect", "错误", "error",
                "异常", "exception", "bug", "故障", "HTTP错误", "5xx", "4xx", "定位", "根因", "线上"));
        INTENT_KEYWORDS.put("testcase", Set.of("测试", "test", "用例", " testcase",
                "测试建议", "补充测试", "补测", "覆盖率提升", "回归", "精准回归", "高风险"));
        INTENT_KEYWORDS.put("app_status", Set.of("应用", "application", "app",
                "在线", "offline", "运行状态", "部署", "启动"));
        INTENT_KEYWORDS.put("trace", Set.of("链路", "trace", "调用链", "请求链路",
                "span", "上下游", "依赖关系"));
        INTENT_KEYWORDS.put("snapshot", Set.of("快照", "snapshot", "版本比对",
                "历史数据", "快照详情", "版本", "上线", "发布"));
        INTENT_KEYWORDS.put("code_relation", Set.of("代码", "code", "类依赖",
                "调用关系", "callgraph", "接口关系", "影响分析"));
        INTENT_KEYWORDS.put("project_info", Set.of("项目", "project", "概览",
                "overview", "统计", "汇总", "总览"));
        INTENT_KEYWORDS.put("code_quality", Set.of("质量", "quality", "复杂度",
                "圈复杂度", "代码审查", "规范", "技术债"));
    }

    /**
     * 注册工具到推荐器
     */
    public void registerTool(ToolMeta meta) {
        tools.put(meta.getToolName(), meta);
        logger.debug("Registered tool recommender: {} ({})", meta.toolName, meta.displayName);
    }

    /**
     * 分析问题并返回推荐结果
     */
    public Recommendation recommend(String question) {
        if (question == null || question.trim().isEmpty()) {
            return new Recommendation("getProjectOverview", Collections.emptyList(),
                    "general", 0.3, "空问题，使用默认工具");
        }

        String lowerQ = question.toLowerCase();
        Map<String, Double> intentScores = scoreIntents(lowerQ);
        applyScenarioBoosts(lowerQ, intentScores);
        String topIntent = getTopIntent(intentScores);

        if (topIntent == null) {
            return new Recommendation("getProjectOverview", Collections.emptyList(),
                    "general", 0.2, "无法识别明确意图，使用项目概览");
        }

        // 根据意图找最佳工具
        List<ToolRanking> rankedTools = rankToolsByIntent(topIntent, intentScores.getOrDefault(topIntent, 0.0), lowerQ);

        if (rankedTools.isEmpty()) {
            return new Recommendation("getProjectOverview", Collections.emptyList(),
                    topIntent, 0.3, "无匹配工具，回退到概览");
        }

        String primary = rankedTools.get(0).meta.getToolName();
        List<String> secondary = new ArrayList<>();
        StringBuilder reason = new StringBuilder();

        for (int i = 0; i < Math.min(rankedTools.size(), 3); i++) {
            ToolRanking tr = rankedTools.get(i);
            if (i == 0) {
                reason.append("首选「").append(tr.meta.displayName).append("」")
                      .append(String.format("(相关度%.0f%%)", tr.score * 100));
            } else {
                secondary.add(tr.meta.getToolName());
                reason.append(", 辅助「").append(tr.meta.displayName).append("」");
            }
        }

        double confidence = rankedTools.get(0).score;
        return new Recommendation(primary, secondary, topIntent, confidence, reason.toString());
    }

    /**
     * 记录工具调用结果（用于学习优化）
     */
    public void recordToolCall(String toolName, boolean success) {
        ToolMeta meta = tools.get(toolName);
        if (meta == null) return;

        synchronized (meta) {
            meta.callCount++;
            meta.lastCalledTime = System.currentTimeMillis();
            if (success) {
                // 指数移动平均更新成功率
                meta.successRate = meta.successRate * 0.9 + 0.1;
            } else {
                meta.successRate = meta.successRate * 0.9 + 0.0 * 0.1;
            }
        }
    }

    /**
     * 获取所有已注册工具的列表
     */
    public Collection<ToolMeta> getAllTools() { return tools.values(); }

    public Optional<ToolMeta> getToolMeta(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    /**
     * 获取推荐器统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("registeredTools", tools.size());

        int totalCalls = 0;
        List<Map<String, Object>> toolList = new java.util.ArrayList<>();
        for (ToolMeta meta : tools.values()) {
            totalCalls += meta.callCount;
            Map<String, Object> t = new java.util.HashMap<>();
            t.put("name", meta.toolName);
            t.put("displayName", meta.displayName);
            t.put("calls", meta.callCount);
            t.put("successRate", String.format("%.2f", meta.successRate));
            toolList.add(t);
        }
        stats.put("totalToolCalls", totalCalls);
        stats.put("toolDetails", toolList);

        return stats;
    }

    // ==================== 内部方法 ====================

    /**
     * 对各意图进行评分
     */
    private Map<String, Double> scoreIntents(String lowerQuestion) {
        Map<String, Double> scores = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry : INTENT_KEYWORDS.entrySet()) {
            String intent = entry.getKey();
            Set<String> keywords = entry.getValue();

            double score = 0;
            for (String kw : keywords) {
                if (lowerQuestion.contains(kw)) {
                    // 完全匹配加分更多
                    score += kw.length() > 3 ? 1.5 : 0.8;
                }
            }
            // 归一化：按关键词命中比例计算
            if (!keywords.isEmpty()) {
                long hitCount = keywords.stream().filter(lowerQuestion::contains).count();
                score += (double) hitCount / keywords.size() * 2.0;
            }

            if (score > 0) {
                scores.put(intent, score);
            }
        }

        return scores;
    }

    private void applyScenarioBoosts(String lowerQuestion, Map<String, Double> scores) {
        if (containsAny(lowerQuestion, "版本上线", "上线前", "发布前", "精准回归", "回归范围", "回归策略")) {
            boost(scores, "snapshot", 3.0);
            boost(scores, "coverage", 2.5);
            boost(scores, "testcase", 2.5);
            boost(scores, "code_relation", 1.5);
        }
        if (containsAny(lowerQuestion, "线上缺陷", "快速定位", "故障定位", "根因定位", "异常定位")) {
            boost(scores, "defect", 3.5);
            boost(scores, "trace", 3.0);
            boost(scores, "performance", 1.0);
        }
        if (containsAny(lowerQuestion, "低覆盖", "高风险", "补测", "测试盲区", "覆盖缺口")) {
            boost(scores, "coverage", 3.0);
            boost(scores, "testcase", 3.0);
            boost(scores, "code_quality", 1.5);
        }
        if (containsAny(lowerQuestion, "性能回归", "性能退化", "耗时变慢", "基线对比", "回归分析")) {
            boost(scores, "performance", 3.5);
            boost(scores, "trace", 2.5);
        }
    }

    private void boost(Map<String, Double> scores, String intent, double delta) {
        scores.merge(intent, delta, Double::sum);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取得分最高的意图
     */
    private String getTopIntent(Map<String, Double> scores) {
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 根据意图对工具进行排名
     */
    private List<ToolRanking> rankToolsByIntent(String topIntent, double intentScore, String lowerQuestion) {
        List<ToolRanking> rankings = new ArrayList<>();

        for (ToolMeta meta : tools.values()) {
            double score = 0;

            // 基于意图匹配
            boolean intentMatched = false;
            if (meta.relatedIntents != null) {
                for (String ri : meta.relatedIntents) {
                    if (ri.equals(topIntent)) {
                        score += 10.0;
                        intentMatched = true;
                        break;
                    }
                }
            }

            // 基于关键词匹配
            if (meta.keywords != null) {
                for (String keyword : meta.keywords) {
                    if (keyword != null && lowerQuestion.contains(keyword.toLowerCase())) {
                        score += keyword.length() > 3 ? 2.0 : 1.0;
                    }
                }
            }

            score += scenarioToolBoost(meta.toolName, lowerQuestion);

            // 基于历史成功率微调
            score *= (0.7 + meta.successRate * 0.3);

            if (score > 0) {
                rankings.add(new ToolRanking(meta, score));
            }
        }

        rankings.sort((a, b) -> Double.compare(b.score, a.score));
        return rankings;
    }

    private double scenarioToolBoost(String toolName, String lowerQuestion) {
        if (containsAny(lowerQuestion, "版本上线", "上线前", "发布前", "精准回归", "回归范围", "回归策略")) {
            if (Set.of("getProjectCoverageOverview", "getLowCoverageClasses", "recommendTestcases", "compareCoverage", "searchCodeRelation", "getCallGraph", "getSnapshots").contains(toolName)) {
                return 8.0;
            }
        }
        if (containsAny(lowerQuestion, "线上缺陷", "快速定位", "故障定位", "根因定位", "异常定位")) {
            if (Set.of("getDefectOverview", "getRecentExceptions", "getAppErrorDetails", "getRecentTraces", "locateRootCause", "analyzeCallChain").contains(toolName)) {
                return 8.0;
            }
        }
        if (containsAny(lowerQuestion, "低覆盖", "高风险", "补测", "测试盲区", "覆盖缺口")) {
            if (Set.of("getLowCoverageClasses", "recommendTestcases", "getCoverageImprovementSuggestions", "compareCoverage", "getHighComplexityMethods").contains(toolName)) {
                return 8.0;
            }
        }
        if (containsAny(lowerQuestion, "性能回归", "性能退化", "耗时变慢", "基线对比", "回归分析")) {
            if (Set.of("compareOverTime", "getAppPerformanceOverview", "getSlowEndpoints", "getEndpointCallFrequency", "analyzeUrlCallPattern").contains(toolName)) {
                return 8.0;
            }
        }
        return 0.0;
    }

    private static class ToolRanking {
        final ToolMeta meta;
        final double score;
        ToolRanking(ToolMeta meta, double score) {
            this.meta = meta;
            this.score = score;
        }
    }

    // ==================== 预定义工具元数据 ====================

    /**
     * 创建所有内置工具的元数据并注册
     * 在 AIAgentService 初始化时调用
     */
    public void registerAllBuiltInTools() {
        registerTool(new ToolMeta("getProjectInfo", "项目信息查询",
                "获取项目基本信息和配置", new String[]{"项目", "project", "信息"}, new String[]{"project_info"}));

        registerTool(new ToolMeta("getProjectStatistics", "项目统计",
                "获取项目的整体统计数据", new String[]{"统计", "statistics", "数据"}, new String[]{"project_info"}));

        registerTool(new ToolMeta("getApps", "应用列表",
                "获取项目下所有应用的列表", new String[]{"应用", "app", "列表"}, new String[]{"app_status"}));

        registerTool(new ToolMeta("getOnlineApps", "在线应用",
                "获取当前在线运行的应用", new String[]{"在线", "online", "运行中"}, new String[]{"app_status"}));

        registerTool(new ToolMeta("getAppDetail", "应用详情",
                "获取指定应用的详细信息", new String[]{"应用详情", "detail", "appId"}, new String[]{"app_status"}));

        registerTool(new ToolMeta("getProjectCoverageOverview", "项目覆盖率概览",
                "获取整个项目的覆盖率汇总", new String[]{"覆盖率", "coverage", "overview"}, new String[]{"coverage"}));

        registerTool(new ToolMeta("getAppCoverageReport", "应用覆盖率报告",
                "获取指定应用的详细覆盖率报告", new String[]{"应用覆盖率", "report"}, new String[]{"coverage"}));

        registerTool(new ToolMeta("getAppCoverageTrend", "覆盖率趋势",
                "获取应用的历史覆盖率趋势数据", new String[]{"趋势", "trend", "变化"}, new String[]{"coverage"}));

        registerTool(new ToolMeta("getCoverageReports", "覆盖率报告列表",
                "获取所有覆盖率报告的列表", new String[]{"报告列表", "reports"}, new String[]{"coverage"}));

        registerTool(new ToolMeta("getLowCoverageClasses", "低覆盖率类查询",
                "找出覆盖率最低的类", new String[]{"低覆盖", "low", "未覆盖"}, new String[]{"coverage", "testcase"}));

        registerTool(new ToolMeta("getClassCoverageList", "类覆盖率列表",
                "查看具体类的覆盖率明细", new String[]{"类覆盖", "class", "明细"}, new String[]{"coverage"}));

        registerTool(new ToolMeta("getRecentTraces", "最近调用链",
                "查询最近的调用链记录", new String[]{"最近链路", "recent", "traces"}, new String[]{"trace"}));

        registerTool(new ToolMeta("getTraceDetail", "调用链详情",
                "查看单条调用链的详细信息", new String[]{"链路详情", "traceDetail", "span"}, new String[]{"trace"}));

        registerTool(new ToolMeta("getTracesByAppName", "按应用查链路",
                "查询指定应用的调用链", new String[]{"应用链路", "tracesByApp"}, new String[]{"trace", "app_status"}));

        registerTool(new ToolMeta("getSnapshots", "快照列表",
                "获取项目下的快照列表", new String[]{"快照", "snapshot", "列表"}, new String[]{"snapshot"}));

        registerTool(new ToolMeta("getSnapshotDetail", "快照详情",
                "获取单个快照的详细数据", new String[]{"快照详情", "snapshotDetail"}, new String[]{"snapshot"}));

        registerTool(new ToolMeta("searchCodeRelation", "代码关系搜索",
                "搜索代码中的调用/引用关系", new String[]{"代码关系", "searchRelation"}, new String[]{"code_relation"}));

        registerTool(new ToolMeta("getCallGraph", "调用图",
                "获取方法的完整调用图", new String[]{"调用图", "callGraph", "依赖"}, new String[]{"code_relation"}));

        registerTool(new ToolMeta("getAppPerformanceOverview", "性能概览",
                "获取应用的整体性能指标", new String[]{"性能", "performance", "概览"}, new String[]{"performance"}));

        registerTool(new ToolMeta("getSlowEndpoints", "慢接口分析",
                "找出响应时间最长的接口", new String[]{"慢接口", "slow", "endpoint"}, new String[]{"performance"}));

        registerTool(new ToolMeta("getEndpointCallFrequency", "接口调用频次",
                "统计各接口的调用次数", new String[]{"调用频率", "frequency", "热点"}, new String[]{"performance"}));

        registerTool(new ToolMeta("getDefectOverview", "缺陷概览",
                "获取缺陷和错误的总体情况", new String[]{"缺陷", "defect", "概览"}, new String[]{"defect"}));

        registerTool(new ToolMeta("getAppErrorDetails", "错误详情",
                "查看具体的 HTTP 错误请求", new String[]{"错误", "error", "details"}, new String[]{"defect"}));

        registerTool(new ToolMeta("getRecentExceptions", "最近异常",
                "获取最近发生的异常列表", new String[]{"异常", "exception", "最近"}, new String[]{"defect"}));

        registerTool(new ToolMeta("recommendTestcases", "测试用例推荐",
                "根据覆盖率推荐需要补充的测试", new String[]{"测试推荐", "recommendTest"}, new String[]{"testcase"}));

        registerTool(new ToolMeta("getEndpointTestSuggestions", "接口测试建议",
                "针对特定接口给出测试场景建议", new String[]{"接口测试", "suggestions"}, new String[]{"testcase"}));

        registerTool(new ToolMeta("getCoverageImprovementSuggestions", "覆盖率提升建议",
                "系统性提升覆盖率的方案", new String[]{"提升建议", "improvement"}, new String[]{"testcase", "coverage"}));

        registerTool(new ToolMeta("compareOverTime", "性能回归分析",
                "对比同一接口在不同时间段的调用链，识别性能退化", new String[]{"性能回归", "退化", "基线"}, new String[]{"performance", "trace"}));

        registerTool(new ToolMeta("compareCoverage", "覆盖差异比对",
                "对比两条调用链的代码覆盖差异，发现测试盲区", new String[]{"覆盖差异", "测试盲区", "精准回归"}, new String[]{"coverage", "testcase", "trace"}));

        registerTool(new ToolMeta("locateRootCause", "异常根因定位",
                "对比正常和异常调用链，定位线上缺陷根因", new String[]{"根因", "线上缺陷", "异常定位"}, new String[]{"defect", "trace"}));

        registerTool(new ToolMeta("analyzeCallChain", "单链路深度分析",
                "分析单条调用链的拓扑、性能瓶颈和异常根因", new String[]{"链路分析", "trace", "瓶颈", "根因"}, new String[]{"trace", "defect", "performance"}));

        registerTool(new ToolMeta("analyzeUrlCallPattern", "URL调用模式分析",
                "分析接口URL的典型路径、慢请求规律和异常情况", new String[]{"URL", "接口", "调用模式", "慢请求"}, new String[]{"trace", "performance"}));

        registerTool(new ToolMeta("getCodeQualityReport", "代码质量报告",
                "评估代码的整体质量状况", new String[]{"质量报告", "quality", "report"}, new String[]{"code_quality"}));

        registerTool(new ToolMeta("getHighComplexityMethods", "高复杂度方法",
                "找出圈复杂度过高的方法", new String[]{"复杂度", "complexity", "高复杂"}, new String[]{"code_quality"}));

        registerTool(new ToolMeta("detectBugs", "类级别 Bug 检测",
                "分析指定 Java 类源码中的空指针、资源泄漏、并发和逻辑风险", new String[]{"Bug检测", "源码缺陷", "空指针"}, new String[]{"bug_detect", "code_quality"}));

        registerTool(new ToolMeta("detectBugsInMethod", "方法级深度 Bug 检测",
                "对指定方法或代码片段进行逐行级缺陷分析", new String[]{"方法Bug", "代码审查", "逐行分析"}, new String[]{"bug_detect", "code_quality"}));

        registerTool(new ToolMeta("batchDetectBugs", "批量 Bug 检测",
                "对多个 Java 类进行批量缺陷扫描并生成汇总报告", new String[]{"批量Bug", "批量扫描", "缺陷报告"}, new String[]{"bug_detect", "code_quality"}));

        registerTool(new ToolMeta("searchAppByName", "搜索应用",
                "按名称搜索应用", new String[]{"搜索应用", "searchApp"}, new String[]{"app_status"}));

        logger.info("Registered {} built-in tools in recommender", tools.size());
    }
}
