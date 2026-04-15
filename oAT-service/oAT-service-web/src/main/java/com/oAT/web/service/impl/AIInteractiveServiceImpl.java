package com.oAT.web.service.impl;

import com.oAT.ai.agent.AgentContext;
import com.oAT.ai.agent.AIAgentService;
import com.oAT.web.service.AIInteractiveService;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.entity.AIAbilityCardVo;
import com.oAT.web.service.entity.AIInteractivePageVo;
import com.oAT.web.service.entity.AIInteractiveReplyVo;
import com.oAT.web.service.entity.AIQuickLinkVo;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class AIInteractiveServiceImpl implements AIInteractiveService {

    private static final Logger logger = LoggerFactory.getLogger(AIInteractiveServiceImpl.class);

    private static final String[] MASCOT_NAMES = {"知秋", "阿涌", "小溯", "言希", "跃链", "拾一"};
    private static final String[] MASCOT_ROLES = {"数据侦察员", "链路向导", "项目陪跑员", "交互分析官", "洞察助手"};
    private static final String[] MASCOT_MOODS = {"专注", "活跃", "机敏", "稳健", "可靠"};
    private static final String[] PRIMARY_COLORS = {"#5865f2", "#00b5ad", "#ff8a65", "#7e57c2", "#26a69a", "#42a5f5"};
    private static final String[] ACCENT_COLORS = {"#8ea1ff", "#65e5dd", "#ffb58a", "#b39ddb", "#80cbc4", "#90caf9"};
    private static final String[] HALO_COLORS = {"rgba(88,101,242,0.18)", "rgba(0,181,173,0.18)", "rgba(255,138,101,0.18)",
            "rgba(126,87,194,0.18)", "rgba(38,166,154,0.18)", "rgba(66,165,245,0.18)"};
    private static final String SESSION_STORE_KEY_PREFIX = "oAT:ai-interactive:v1:sessions:";
    private static final String LEGACY_SESSION_STORE_KEY_PREFIX = "oAT:ai-interactive:sessions:";
    private static final long SESSION_STORE_TTL_DAYS = 30L;
    private static final String SESSION_LOG_PREFIX = "[AI-INTERACTIVE-SESSION]";
    private static final AtomicLong SESSION_RESTORE_COUNTER = new AtomicLong();
    private static final AtomicLong SESSION_SAVE_COUNTER = new AtomicLong();
    private static final AtomicLong SESSION_MISS_COUNTER = new AtomicLong();

    @Autowired
    private ProjectService projectService;

    @Autowired
    private AppService appService;

    @Autowired
    private ClientSessionService clientSessionService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private AIAgentService aiAgentService;

    @Value("${ai.llm.timeout:300}")
    private int aiTimeout;

    @Override
    public AIInteractivePageVo buildPage(String projectId, UserVo user) {
        ProjectVo project = projectService.getProject(projectId);
        List<AppVo> apps = loadApps(projectId);
        Map<String, String> mascot = buildMascot(project);

        AIInteractivePageVo page = new AIInteractivePageVo();
        page.setProjectId(projectId);
        page.setProjectName(project.getName());
        page.setProjectSummary(buildProjectSummary(project, apps));
        page.setWelcomeMessage(buildWelcomeMessage(user, project, apps, mascot.get("mascotName")));
        page.setStarterQuestions(buildStarterQuestions(apps));
        page.setAbilityCards(buildAbilityCards(project, apps));
        page.setOnlineAppCount(countOnlineApps(apps));
        page.setAppCount(apps.size());
        page.setAppNames(apps.stream().map(AppVo::getName).collect(Collectors.toList()));
        page.setMascotHint(buildMascotHint(project, apps));
        page.setQuickLinks(buildQuickLinks(projectId, apps, "overview"));
        page.setSessionState(loadSessionState(projectId, user));
        page.setMascot(mascot);
        page.setAiTimeout(aiTimeout);
        return page;
    }

    @Override
    public AIInteractiveReplyVo ask(String projectId, UserVo user, String question, String pageContext, String imageData,
                                    String sessionState, String activeSessionId, String sessionSortMode,
                                    Boolean timelineExpanded) {
        long startTime = System.currentTimeMillis();
        ProjectVo project = projectService.getProject(projectId);
        List<AppVo> apps = loadApps(projectId);
        String cleanQuestion = question == null ? "" : question.trim();
        if (!StringUtils.hasText(cleanQuestion) && StringUtils.hasText(imageData)) {
            cleanQuestion = "[图片提问] 请分析这张图片";
        }
        String contextSummary = normalizePageContext(pageContext);
        String topic = detectTopic(cleanQuestion + " " + contextSummary);

        AIInteractiveReplyVo reply = new AIInteractiveReplyVo();
        reply.setQuestion(cleanQuestion);
        reply.setTopic(topic);

        String answer = callAIAgent(project, apps, user, cleanQuestion, contextSummary, imageData);
        reply.setAnswer(answer);

        long responseTime = System.currentTimeMillis() - startTime;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("responseTime", responseTime);
        metadata.put("topic", topic);
        reply.setMetadata(metadata);
        reply.setSuggestions(buildFollowUpSuggestions(apps, topic));
        reply.setQuickLinks(buildQuickLinks(projectId, apps, topic));
        reply.setSessionState(loadSessionState(projectId, user));
        return reply;
    }

    @Override
    public String saveSessionState(String projectId, UserVo user, String sessionState) {
        if (!StringUtils.hasText(sessionState)) {
            logger.info("{} action=save status=skipped projectId={} userId={} reason=empty_payload", SESSION_LOG_PREFIX,
                    projectId, user.getId());
            return loadSessionState(projectId, user);
        }
        String cacheKey = buildSessionStoreKey(projectId, user.getId());
        redisTemplate.opsForValue().set(cacheKey, sessionState, SESSION_STORE_TTL_DAYS, TimeUnit.DAYS);
        long saveCount = SESSION_SAVE_COUNTER.incrementAndGet();
        logger.info("{} action=save status=success projectId={} userId={} payloadLength={} ttlDays={} saveCount={}",
                SESSION_LOG_PREFIX, projectId, user.getId(), sessionState.length(), SESSION_STORE_TTL_DAYS, saveCount);
        return sessionState;
    }

    private String callAIAgent(ProjectVo project, List<AppVo> apps, UserVo user,
                               String question, String pageContext, String imageData) {
        if (aiAgentService == null) {
            logger.warn("AI Agent bean is not available in Spring context");
            return "AI 服务暂不可用：AI Agent Bean 未加载，请检查模块装配与 Spring 配置。";
        }
        if (!aiAgentService.isAvailable()) {
            logger.warn("AI Agent is unavailable: {}", aiAgentService.getInitializationStatus());
            return "AI 服务暂不可用：" + aiAgentService.getInitializationStatus();
        }

        try {
            AgentContext context = new AgentContext(project.getId(), user.getId(), user.getName());
            logger.info("Calling AI Agent for question: {} (hasImage: {})", question, StringUtils.hasText(imageData));

            if (StringUtils.hasText(imageData)) {
                return aiAgentService.chatWithImage(context, question, pageContext, imageData);
            }
            if (StringUtils.hasText(pageContext)) {
                return aiAgentService.chatWithContext(context, question, pageContext);
            }
            return aiAgentService.chat(context, question);
        } catch (Exception e) {
            logger.error("Failed to call AI Agent", e);
            return "AI 服务调用失败：" + e.getMessage();
        }
    }

    private List<AppVo> loadApps(String projectId) {
        List<AppVo> apps = appService.getAppList(projectId);
        for (AppVo app : apps) {
            app.setOnlineCount(clientSessionService.getOnlineSessionsByAppId(app.getId()).size());
        }
        return apps;
    }

    private int countOnlineApps(List<AppVo> apps) {
        int count = 0;
        for (AppVo app : apps) {
            if (app.getOnlineCount() > 0) {
                count++;
            }
        }
        return count;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private int positiveHash(String value) {
        int hash = value == null ? 0 : value.hashCode();
        if (hash == Integer.MIN_VALUE) {
            return 0;
        }
        return Math.abs(hash);
    }

    private String pick(String[] values, int seed) {
        if (values.length == 0) {
            return "";
        }
        return values[seed % values.length];
    }

    private Map<String, String> buildMascot(ProjectVo project) {
        int seed = positiveHash(project.getId() + ":" + project.getName());
        Map<String, String> mascot = new HashMap<>();
        mascot.put("mascotName", pick(MASCOT_NAMES, seed));
        mascot.put("mascotRole", pick(MASCOT_ROLES, seed / 2 + 7));
        mascot.put("mascotMood", pick(MASCOT_MOODS, seed / 3 + 11));
        mascot.put("mascotPrimary", pick(PRIMARY_COLORS, seed / 5 + 13));
        mascot.put("mascotAccent", pick(ACCENT_COLORS, seed / 7 + 17));
        mascot.put("mascotHalo", pick(HALO_COLORS, seed / 11 + 19));
        return mascot;
    }

    private String buildProjectSummary(ProjectVo project, List<AppVo> apps) {
        int online = countOnlineApps(apps);
        return String.format("项目「%s」当前有 %d 个应用，其中 %d 个在线。",
                project.getName(), apps.size(), online);
    }

    private String buildWelcomeMessage(UserVo user, ProjectVo project, List<AppVo> apps, String mascotName) {
        int online = countOnlineApps(apps);
        return String.format("你好，%s！我是 %s，你的精准测试助手。当前项目「%s」有 %d 个应用，%d 个在线运行。有什么我可以帮助你的吗？",
                user.getName(), StringUtils.hasText(mascotName) ? mascotName : "AI", project.getName(), apps.size(), online);
    }

    private List<String> buildStarterQuestions(List<AppVo> apps) {
        List<String> questions = new ArrayList<>();
        questions.add("这个项目的代码覆盖率是多少？");
        questions.add("有哪些应用正在运行？");
        questions.add("帮我分析一下最近的测试情况");
        if (!apps.isEmpty()) {
            questions.add("应用 " + apps.get(0).getName() + " 的覆盖率如何？");
        }
        return questions;
    }

    private List<AIAbilityCardVo> buildAbilityCards(ProjectVo project, List<AppVo> apps) {
        List<AIAbilityCardVo> cards = new ArrayList<>();
        int online = countOnlineApps(apps);
        cards.add(new AIAbilityCardVo("在线应用", String.valueOf(online), "当前在线运行的应用数量"));
        cards.add(new AIAbilityCardVo("总应用数", String.valueOf(apps.size()), "项目下的应用总数"));
        cards.add(new AIAbilityCardVo("项目状态", online > 0 ? "活跃" : "空闲", "根据在线应用判断的项目状态"));
        return cards;
    }

    private String buildMascotHint(ProjectVo project, List<AppVo> apps) {
        int online = countOnlineApps(apps);
        if (online == 0) {
            return "当前没有应用在线，你可以启动一些应用来开始测试。";
        } else if (online < 3) {
            return "有少量应用在线，可以开始进行测试分析了。";
        } else {
            return "多个应用在线运行中，随时可以进行分析和查询。";
        }
    }

    private List<AIQuickLinkVo> buildQuickLinks(String projectId, List<AppVo> apps, String topic) {
        List<AIQuickLinkVo> links = new ArrayList<>();
        links.add(new AIQuickLinkVo("项目主页", "回到项目整体概况", "/p/" + projectId + "/home"));
        links.add(new AIQuickLinkVo("监控台", "查看实时请求与调用链", "/p/" + projectId + "/monitor"));
        if (!apps.isEmpty()) {
            String appId = apps.get(0).getId();
            links.add(new AIQuickLinkVo("覆盖率报告", "查看代码覆盖率详情", "/p/" + projectId + "/coverage/details?appId=" + appId));
        } else {
            links.add(new AIQuickLinkVo("覆盖率报告", "查看代码覆盖率详情", "/p/" + projectId + "/coverage/overview"));
        }
        links.add(new AIQuickLinkVo("快照列表", "浏览项目沉淀的全部快照", "/p/" + projectId + "/snapshot/list"));
        return links;
    }

    private String normalizePageContext(String pageContext) {
        return pageContext == null ? "" : pageContext.trim();
    }

    private String loadSessionState(String projectId, UserVo user) {
        String cacheKey = buildSessionStoreKey(projectId, user.getId());
        Object stored = redisTemplate.opsForValue().get(cacheKey);
        if (stored instanceof String && StringUtils.hasText((String) stored)) {
            long restoreCount = SESSION_RESTORE_COUNTER.incrementAndGet();
            logger.info("{} action=restore status=hit source=current projectId={} userId={} payloadLength={} restoreCount={}",
                    SESSION_LOG_PREFIX, projectId, user.getId(), ((String) stored).length(), restoreCount);
            return (String) stored;
        }

        String legacyCacheKey = buildLegacySessionStoreKey(projectId, user.getId());
        Object legacyStored = redisTemplate.opsForValue().get(legacyCacheKey);
        if (legacyStored instanceof String && StringUtils.hasText((String) legacyStored)) {
            String migratedPayload = (String) legacyStored;
            redisTemplate.opsForValue().set(cacheKey, migratedPayload, SESSION_STORE_TTL_DAYS, TimeUnit.DAYS);
            long restoreCount = SESSION_RESTORE_COUNTER.incrementAndGet();
            logger.info("{} action=restore status=hit source=legacy_migrated projectId={} userId={} payloadLength={} restoreCount={} ttlDays={}",
                    SESSION_LOG_PREFIX, projectId, user.getId(), migratedPayload.length(), restoreCount, SESSION_STORE_TTL_DAYS);
            return migratedPayload;
        }

        long missCount = SESSION_MISS_COUNTER.incrementAndGet();
        logger.info("{} action=restore status=miss source=all projectId={} userId={} missCount={}",
                SESSION_LOG_PREFIX, projectId, user.getId(), missCount);
        return "";
    }

    private String buildSessionStoreKey(String projectId, String userId) {
        return SESSION_STORE_KEY_PREFIX + projectId + ":" + userId;
    }

    private String buildLegacySessionStoreKey(String projectId, String userId) {
        return LEGACY_SESSION_STORE_KEY_PREFIX + projectId + ":" + userId;
    }

    private String detectTopic(String text) {
        if (containsAny(text, "覆盖", "coverage")) {
            return "coverage";
        } else if (containsAny(text, "链路", "trace", "调用")) {
            return "trace";
        } else if (containsAny(text, "应用", "app", "在线")) {
            return "app";
        } else if (containsAny(text, "测试", "test")) {
            return "test";
        } else if (containsAny(text, "快照", "snapshot")) {
            return "snapshot";
        }
        return "general";
    }

    private List<String> buildFollowUpSuggestions(List<AppVo> apps, String topic) {
        List<String> suggestions = new ArrayList<>();
        switch (topic) {
            case "coverage":
                suggestions.add("哪个模块的覆盖率最低？");
                suggestions.add("如何提高覆盖率？");
                break;
            case "trace":
                suggestions.add("查看最近的调用链路");
                suggestions.add("分析慢接口");
                break;
            case "app":
                suggestions.add("查看应用详情");
                suggestions.add("分析应用状态");
                break;
            default:
                suggestions.add("帮我分析项目状态");
                suggestions.add("查看覆盖率报告");
                suggestions.add("有哪些应用在线？");
        }
        return suggestions;
    }
}
