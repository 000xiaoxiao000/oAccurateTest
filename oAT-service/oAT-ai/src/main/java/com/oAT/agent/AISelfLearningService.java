package com.oAT.agent;

import com.oAT.ai.agent.FeedbackPersistenceService;
import com.oAT.ai.agent.ToolRecommender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AI 自主学习服务
 * 通过分析用户反馈数据持续优化 AI 的回答质量
 *
 * <p>学习维度：</p>
 * <ol>
 *   <li><strong>回答质量评估</strong>：基于评分/点赞/踩识别高质量回答模式</li>
 *   <li><strong>问题-回答对库</strong>：积累优质 Q&A 对，用于语义缓存命中</li>
 *   <li><strong>系统提示词优化</strong>：根据高频错误类型自动调整提示词</li>
 *   <li><strong>工具调用效率</strong>：识别低效工具链路并推荐更优方案</li>
 *   <li><strong>知识缺口发现</strong>：找出用户常问但AI回答不佳的领域</li>
 * </ol>
 */
public class AISelfLearningService {

    private static final Logger logger = LoggerFactory.getLogger(AISelfLearningService.class);

    /** 学习报告周期（小时） */
    private final int learningReportIntervalHours;

    /** 知识库条目最大数量 */
    private static final int MAX_KNOWLEDGE_ENTRIES = 200;

    /** 优质回答模板库：问题模式 → 推荐的回答结构 */
    private final ConcurrentHashMap<String, KnowledgeEntry> knowledgeBase = new ConcurrentHashMap<>();

    /** 问题分类统计：topic → 统计信息 */
    private final ConcurrentHashMap<TopicKey, TopicStats> topicStatsMap = new ConcurrentHashMap<>();

    /** 常见失败模式 */
    private final ConcurrentHashMap<String, Integer> failurePatterns = new ConcurrentHashMap<>();

    /** 优化建议队列 */
    private final List<OptimizationSuggestion> suggestions = Collections.synchronizedList(new ArrayList<>());

    /** 定时任务调度器 */
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "ai-self-learning");
                t.setDaemon(true);
                return t;
            });

    /** 数据源 */
    private final FeedbackPersistenceService feedbackPersistence;

    /** 工具推荐器引用（用于学习工具调用模式） */
    private ToolRecommender toolRecommender;

    public AISelfLearningService(FeedbackPersistenceService feedbackPersistence, int learningReportIntervalHours) {
        this.feedbackPersistence = feedbackPersistence;
        this.learningReportIntervalHours = learningReportIntervalHours;
        startPeriodicLearning();
        logger.info("AISelfLearningService initialized, intervalHours={}", learningReportIntervalHours);
    }

    public void setToolRecommender(ToolRecommender recommender) {
        this.toolRecommender = recommender;
    }

    /**
     * 处理新的反馈数据（实时学习入口）
     */
    public void onNewFeedback(FeedbackPersistenceService.FeedbackRecord record) {
        if (record == null || record.getQuestion() == null) return;

        // 1. 如果是好评，加入知识库
        if (isPositiveFeedback(record)) {
            addToKnowledgeBase(record);
        }

        // 2. 更新话题统计
        updateTopicStats(record);

        // 3. 分析负面反馈的模式
        if (isNegativeFeedback(record)) {
            analyzeFailurePattern(record);
        }
    }

    /**
     * 执行一轮完整学习
     */
    public LearningReport runLearningCycle() {
        logger.info("--- Starting learning cycle ---");

        FeedbackPersistenceService.SelfLearningDataset dataset = feedbackPersistence.getLearningDataset();

        // 1. 从正面样本中提炼知识
        learnFromPositives(dataset.positiveSamples);

        // 2. 从负面样本中发现改进点
        learnFromNegatives(dataset.negativeSamples);

        // 3. 生成优化建议
        generateOptimizationSuggestions();

        LearningReport report = buildReport();
        logger.info("--- Learning cycle completed: {} knowledge entries, {} suggestions ---",
                knowledgeBase.size(), suggestions.size());

        return report;
    }

    /**
     * 检查是否有相似的历史优质回答
     *
     * @return 匹配到的优质回答；未匹配返回 null
     */
    public String findBestPracticeAnswer(String question) {
        if (question == null || knowledgeBase.isEmpty()) return null;

        String normalizedQ = normalizeQuestion(question);
        double bestScore = 0.7; // 最低匹配阈值
        String bestAnswer = null;

        for (KnowledgeEntry entry : knowledgeBase.values()) {
            double score = computeSimilarity(normalizedQ, entry.normalizedQuestionPattern);
            if (score > bestScore) {
                bestScore = score;
                bestAnswer = entry.recommendedAnswerTemplate;
            }
        }

        if (bestAnswer != null) {
            logger.debug("Knowledge base hit for question (score={}): {}", bestScore,
                    question.length() > 50 ? question.substring(0, 50) + "..." : question);
        }

        return bestAnswer;
    }

    /**
     * 获取当前所有优化建议
     */
    public List<OptimizationSuggestion> getSuggestions() {
        synchronized (suggestions) {
            return new ArrayList<>(suggestions);
        }
    }

    /**
     * 获取学习状态概览
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("knowledgeBaseSize", knowledgeBase.size());
        status.put("trackedTopics", topicStatsMap.size());
        status.put("failurePatterns", failurePatterns.size());
        status.put("pendingSuggestions", suggestions.size());

        // 各主题满意度分布
        Map<String, Object> topicHealth = new HashMap<>();
        for (Map.Entry<TopicKey, TopicStats> entry : topicStatsMap.entrySet()) {
            TopicStats ts = entry.getValue();
            double rate = ts.totalFeedbacks > 0 ? (double) ts.positiveCount / ts.totalFeedbacks : 0;
            topicHealth.put(entry.getKey().toString(),
                    String.format("满意度%.0f%%(%d评)", rate * 100, ts.totalFeedbacks));
        }
        status.put("topicHealth", topicHealth);

        return status;
    }

    // ==================== 内部学习方法 ====================

    private void learnFromPositives(List<FeedbackPersistenceService.SelfLearningSample> positives) {
        for (FeedbackPersistenceService.SelfLearningSample sample : positives) {
            addToKnowledgeBase(sample.question, sample.answer, sample.topic);
        }
    }

    private void learnFromNegatives(List<FeedbackPersistenceService.SelfLearningSample> negatives) {
        for (FeedbackPersistenceService.SelfLearningSample sample : negatives) {
            // 分析负面样本的共同特征
            String pattern = extractPattern(sample.question);
            failurePatterns.merge(pattern, 1, Integer::sum);
        }
    }

    private void addToKnowledgeBase(FeedbackPersistenceService.FeedbackRecord record) {
        addToKnowledgeBase(record.getQuestion(), record.getAnswer(), record.getTopic());
    }

    private void addToKnowledgeBase(String question, String answer, String topic) {
        if (question == null || answer == null) return;

        String pattern = extractPattern(question);
        String normalizedQ = normalizeQuestion(question);

        KnowledgeEntry existing = knowledgeBase.get(pattern);
        if (existing == null) {
            if (knowledgeBase.size() >= MAX_KNOWLEDGE_ENTRIES) {
                evictWeakestEntry();
            }

            knowledgeBase.put(pattern, new KnowledgeEntry(pattern, normalizedQ,
                    truncateAnswer(answer), topic, 1, System.currentTimeMillis()));
        } else {
            // 增加置信度
            existing.confirmations++;
            existing.lastUpdated = System.currentTimeMillis();
            // 保留更好的回答模板
            if (answer.length() > existing.recommendedAnswerTemplate.length()) {
                existing.recommendedAnswerTemplate = truncateAnswer(answer);
            }
        }
    }

    private void updateTopicStats(FeedbackPersistenceService.FeedbackRecord record) {
        TopicKey key = new TopicKey(
                record.getTopic() != null ? record.getTopic() : "general",
                record.getProjectId() != null ? record.getProjectId() : "unknown"
        );

        topicStatsMap.computeIfAbsent(key, k -> new TopicStats()).update(record);
    }

    private void analyzeFailurePattern(FeedbackPersistenceService.FeedbackRecord record) {
        String pattern = extractPattern(record.getQuestion());
        failurePatterns.merge(pattern, 1, Integer::sum);

        // 如果某模式频繁出现负面反馈，生成建议
        int count = failurePatterns.getOrDefault(pattern, 0);
        if (count >= 3) {
            addSuggestion(OptimizationSuggestion.Priority.HIGH,
                    "高频负面反馈模式检测到",
                    "问题模式「" + truncate(pattern, 50) + "」已收到 " + count + " 条负面反馈。"
                            + "\n建议检查该类问题的处理逻辑，或补充相关工具数据。",
                    "failure_pattern_" + count);
        }
    }

    private void generateOptimizationSuggestions() {
        // 1. 低满意度主题检测
        for (Map.Entry<TopicKey, TopicStats> entry : topicStatsMap.entrySet()) {
            TopicStats ts = entry.getValue();
            if (ts.totalFeedbacks >= 5 && ts.satisfactionRate() < 0.6) {
                addSuggestion(OptimizationSuggestion.Priority.MEDIUM,
                        "主题「" + entry.getKey().topic + "」满意度偏低",
                        String.format("该主题满意度仅 %.0f%% (%d/%d)，建议优化相关提示词或增强数据获取能力。",
                                ts.satisfactionRate() * 100, ts.positiveCount, ts.totalFeedbacks),
                        "topic_quality_" + entry.getKey().topic);
            }
        }

        // 2. 高频失败模式
        failurePatterns.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .forEach(e -> {
                    if (e.getValue() >= 2) {
                        addSuggestion(OptimizationSuggestion.Priority.MEDIUM,
                                "需要关注的失败模式",
                                "「" + truncate(e.getKey(), 40) + "」出现 " + e.getValue() + " 次负面反馈",
                                "pattern_" + e.getValue());
                    }
                });
    }

    private LearningReport buildReport() {
        LearningReport report = new LearningReport();
        report.timestamp = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        report.knowledgeBaseEntries = knowledgeBase.size();
        report.trackedTopics = topicStatsMap.size();
        report.failurePatternsAnalyzed = failurePatterns.size();
        report.suggestionsGenerated = suggestions.size();

        // 主题健康度
        report.topicHealthScores = new HashMap<>();
        for (Map.Entry<TopicKey, TopicStats> entry : topicStatsMap.entrySet()) {
            report.topicHealthScores.put(entry.getKey().topic,
                    entry.getValue().satisfactionRate());
        }

        // 清理旧建议（保留最近20条）
        synchronized (suggestions) {
            while (suggestions.size() > 20) {
                suggestions.remove(0);
            }
        }

        // 将当前建议列表附到报告中
        report.suggestions = getSuggestions();

        return report;
    }

    private String extractPattern(String question) {
        if (question == null) return "";
        // 提取关键词作为模式（去除数字、停用词等）
        return normalizeQuestion(question).replaceAll("\\d+", "#")
                .replaceAll("(\\s+)\\1+", "$1").trim();
    }

    private String normalizeQuestion(String q) {
        return q.toLowerCase()
                .replaceAll("[\\p{Punct}&&[^?]]+", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private String truncateAnswer(String answer) {
        if (answer == null) return "";
        // 截取核心部分（前500字符），用于模板参考
        return answer.length() > 500 ? answer.substring(0, 500) + "..." : answer;
    }

    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private double computeSimilarity(String a, String b) {
        if (a == null || b == null) return 0;
        if (a.equals(b)) return 1.0;

        // 简化的 Jaccard 相似度
        Set<String> wordsA = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> wordsB = new HashSet<>(Arrays.asList(b.split("\\s+")));

        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);

        Set<String> union = new HashSet<>(wordsA);
        union.addAll(wordsB);

        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private boolean isPositiveFeedback(FeedbackPersistenceService.FeedbackRecord record) {
        return "helpful".equals(record.getFeedbackType()) ||
               (record.getRating() != null && record.getRating() >= 4);
    }

    private boolean isNegativeFeedback(FeedbackPersistenceService.FeedbackRecord record) {
        return "not_helpful".equals(record.getFeedbackType()) ||
               "incorrect".equals(record.getFeedbackType()) ||
               "incomplete".equals(record.getFeedbackType()) ||
               (record.getRating() != null && record.getRating() <= 2);
    }

    private void evictWeakestEntry() {
        String weakest = null;
        long oldest = Long.MAX_VALUE;
        int lowestConfirmations = Integer.MAX_VALUE;

        for (Map.Entry<String, KnowledgeEntry> entry : knowledgeBase.entrySet()) {
            KnowledgeEntry v = entry.getValue();
            if (v.confirmations < lowestConfirmations ||
                    (v.confirmations == lowestConfirmations && v.lastUpdated < oldest)) {
                weakest = entry.getKey();
                lowestConfirmations = v.confirmations;
                oldest = v.lastUpdated;
            }
        }
        if (weakest != null) {
            knowledgeBase.remove(weakest);
        }
    }

    private void addSuggestion(OptimizationSuggestion.Priority priority,
                              String title, String description, String id) {
        // 避免重复
        for (OptimizationSuggestion s : getSuggestions()) {
            if (id.equals(s.id)) return;
        }
        suggestions.add(new OptimizationSuggestion(priority, title, description, id));
    }

    private void startPeriodicLearning() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                runLearningCycle();
            } catch (Exception e) {
                logger.error("Periodic learning cycle failed", e);
            }
        }, learningReportIntervalHours, learningReportIntervalHours, TimeUnit.HOURS);
    }

    // ==================== 内部类 ====================

    private static class KnowledgeEntry implements Serializable {
        final String pattern;
        final String normalizedQuestionPattern;
        volatile String recommendedAnswerTemplate;
        final String topic;
        volatile int confirmations;
        volatile long lastUpdated;

        KnowledgeEntry(String pattern, String normalizedQuestionPattern,
                      String recommendedAnswerTemplate, String topic,
                      int confirmations, long lastUpdated) {
            this.pattern = pattern;
            this.normalizedQuestionPattern = normalizedQuestionPattern;
            this.recommendedAnswerTemplate = recommendedAnswerTemplate;
            this.topic = topic;
            this.confirmations = confirmations;
            this.lastUpdated = lastUpdated;
        }
    }

    private static class TopicStats implements Serializable {
        int totalFeedbacks = 0;
        int positiveCount = 0;
        int negativeCount = 0;

        synchronized void update(FeedbackPersistenceService.FeedbackRecord record) {
            totalFeedbacks++;
            if ("helpful".equals(record.getFeedbackType()) ||
                    (record.getRating() != null && record.getRating() >= 4)) {
                positiveCount++;
            } else if (record.getRating() != null && record.getRating() <= 2) {
                negativeCount++;
            }
        }

        double satisfactionRate() {
            return totalFeedbacks > 0 ? (double) positiveCount / totalFeedbacks : 0;
        }
    }

    private static class TopicKey implements Serializable {
        final String topic;
        final String projectId;

        TopicKey(String topic, String projectId) {
            this.topic = topic;
            this.projectId = projectId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TopicKey)) return false;
            TopicKey that = (TopicKey) o;
            return Objects.equals(topic, that.topic) && Objects.equals(projectId, that.projectId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(topic, projectId);
        }

        @Override
        public String toString() {
            return topic + "@" + projectId.substring(0, Math.min(8, projectId.length()));
        }
    }

    // ==================== 公开数据结构 ====================

    /**
     * 学习报告
     */
    public static class LearningReport implements Serializable {
        public String timestamp;
        public int knowledgeBaseEntries;
        public int trackedTopics;
        public int failurePatternsAnalyzed;
        public int suggestionsGenerated;
        public Map<String, Double> topicHealthScores;
        public List<OptimizationSuggestion> suggestions;
    }

    /**
     * 优化建议
     */
    public static class OptimizationSuggestion implements Serializable {
        public enum Priority { HIGH, MEDIUM, LOW }

        public final Priority priority;
        public final String title;
        public final String description;
        public final String id;
        public final long createdTime;

        public OptimizationSuggestion(Priority priority, String title, String description, String id) {
            this.priority = priority;
            this.title = title;
            this.description = description;
            this.id = id;
            this.createdTime = System.currentTimeMillis();
        }
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("AISelfLearningService shutdown");
    }
}
