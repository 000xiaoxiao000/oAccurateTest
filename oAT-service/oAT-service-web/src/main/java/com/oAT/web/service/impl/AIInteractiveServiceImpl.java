package com.oAT.web.service.impl;

import com.oAT.ai.agent.AgentContext;
import com.oAT.ai.agent.AIAgentService;
import com.oAT.web.service.AIInteractiveService;
import com.oAT.web.service.AppService;
import com.oAT.web.service.CoverageService;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.entity.AIAbilityCardVo;
import com.oAT.web.service.entity.AIActionVo;
import com.oAT.web.service.entity.AIInteractivePageVo;
import com.oAT.web.service.entity.AIInteractiveReplyVo;
import com.oAT.web.service.entity.AIQuickLinkVo;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import com.oAT.web.config.AIInteractiveRouteConfig;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
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
    private CoverageService coverageService;

    @Autowired
    private ClientSessionService clientSessionService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private AIAgentService aiAgentService;

    @Autowired(required = false)
    private AIInteractiveRouteConfig routeConfig;

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
        page.setQuickLinks(buildQuickLinks(projectId, apps, new RouteContext("project", "project.overview")));
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
        RouteContext routeContext = detectRoute(cleanQuestion, contextSummary);

        AIInteractiveReplyVo reply = new AIInteractiveReplyVo();
        reply.setQuestion(cleanQuestion);
        reply.setTopic(routeContext.topicKey);

        String answer = callAIAgent(project, apps, user, cleanQuestion, contextSummary, imageData);
        reply.setAnswer(answer);

        long responseTime = System.currentTimeMillis() - startTime;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("responseTime", responseTime);
        metadata.put("topic", routeContext.topicKey);
        metadata.put("route", routeContext.routeKey);
        reply.setMetadata(metadata);
        reply.setSuggestions(buildFollowUpSuggestions(apps, routeContext));
        reply.setQuickLinks(buildQuickLinks(projectId, apps, routeContext));
        reply.setActions(buildActions(projectId, apps, cleanQuestion, contextSummary, routeContext));
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

    @Override
    public String clearSessionMemory(String projectId, UserVo user) {
        if (user == null || !StringUtils.hasText(projectId)) {
            return "";
        }
        if (aiAgentService != null) {
            aiAgentService.clearConversationMemory(user.getId(), projectId);
        }
        String cacheKey = buildSessionStoreKey(projectId, user.getId());
        String legacyCacheKey = buildLegacySessionStoreKey(projectId, user.getId());
        redisTemplate.delete(cacheKey);
        redisTemplate.delete(legacyCacheKey);
        logger.info("{} action=clear status=success projectId={} userId={}", SESSION_LOG_PREFIX, projectId, user.getId());
        return "";
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
        if (text == null) {
            return false;
        }
        String normalized = text.toLowerCase();
        for (String keyword : keywords) {
            if (keyword != null && normalized.contains(keyword.toLowerCase())) {
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

    private List<AIQuickLinkVo> buildQuickLinks(String projectId, List<AppVo> apps, RouteContext routeContext) {
        List<AIQuickLinkVo> links = new ArrayList<>();
        links.add(new AIQuickLinkVo("项目主页", "回到项目整体概况", "/p/" + projectId + "/home"));
        links.add(new AIQuickLinkVo("监控台", "查看实时请求与调用链", "/p/" + projectId + "/monitor"));
        if (isRoute(routeContext, "coverage", "coverage.overview", "coverage.app", "coverage.trend", "coverage.low", "quality.lowComplexity", "quality.report")) {
            links.add(new AIQuickLinkVo("覆盖率概览", "查看项目整体覆盖率", "/p/" + projectId + "/coverage/overview"));
        }
        if (!apps.isEmpty() && isRoute(routeContext, "coverage", "coverage.overview", "coverage.app", "coverage.trend", "coverage.low", "quality.lowComplexity", "quality.report")) {
            String coverageUrl = buildCoverageUrl(projectId, apps);
            links.add(new AIQuickLinkVo("覆盖率报告", "查看代码覆盖率详情", coverageUrl));
        } else if (isRoute(routeContext, "coverage", "coverage.overview", "coverage.app", "coverage.trend", "coverage.low", "quality.lowComplexity", "quality.report")) {
            links.add(new AIQuickLinkVo("覆盖率报告", "查看代码覆盖率详情", "/p/" + projectId + "/coverage/overview"));
        }
        if (isRoute(routeContext, "snapshot", "snapshot.list", "snapshot.detail", "snapshot.my")) {
            links.add(new AIQuickLinkVo("快照列表", "浏览项目沉淀的全部快照", "/p/" + projectId + "/snapshot/list"));
        }
        if (isRoute(routeContext, "snapshot", "snapshot.my")) {
            links.add(new AIQuickLinkVo("我的快照", "只看当前用户保存的快照", "/p/" + projectId + "/snapshot/my"));
        }
        if (isRoute(routeContext, "trace", "trace.recent", "trace.detail", "trace.app")) {
            links.add(new AIQuickLinkVo("最近调用链", "查看最近的链路数据", "/p/" + projectId + "/monitor"));
        }
        if (isRoute(routeContext, "trace", "trace.app")) {
            links.add(new AIQuickLinkVo("按应用查链路", "查看当前应用相关链路", "/p/" + projectId + "/monitor"));
        }
        if (isRoute(routeContext, "quality", "quality.lowComplexity", "quality.report")) {
            links.add(new AIQuickLinkVo("代码质量", "查看复杂度与质量分析", "/p/" + projectId + "/coverage/overview"));
        }
        return links;
    }

    private List<AIActionVo> buildActions(String projectId, List<AppVo> apps, String question, String pageContext, RouteContext routeContext) {
        List<AIActionVo> actions = new ArrayList<>();
        String questionText = (question == null ? "" : question).toLowerCase();
        String text = (questionText + " " + (pageContext == null ? "" : pageContext)).toLowerCase();
        boolean onMonitorPage = containsAny(pageContext, "监控页助手", "页面类型:监控页", "实时监控", "自动保存我的快照");
        boolean onCoveragePage = containsAny(pageContext, "覆盖率页", "页面类型:覆盖率页", "覆盖率详情", "coverage", "覆盖率报告");
        boolean onSnapshotPage = containsAny(pageContext, "快照页", "页面类型:快照页", "snapshot", "我的快照", "快照列表");

        if (onCoveragePage && !containsAny(text, "快照", "链路", "调用链")
                && containsAny(text, "看", "查看", "打开", "跳转", "链接", "连接", "去")) {
            String url = buildCoverageUrl(projectId, apps, questionText);
            actions.add(new AIActionVo("navigate", "打开覆盖率报告", "跳转到当前项目的覆盖率报告页面", url, false, null));
            actions.add(new AIActionVo("link", "查看覆盖率入口", "在快捷入口中保留覆盖率报告链接", url, false, null));
        } else if (isRoute(routeContext, "coverage", "coverage.overview", "coverage.app", "coverage.trend", "coverage.low", "quality.lowComplexity", "quality.report")
                && containsAny(text, "看", "查看", "打开", "跳转", "链接", "连接", "去")) {
            String url = buildCoverageUrl(projectId, apps, questionText);
            actions.add(new AIActionVo("navigate", "打开覆盖率报告", "跳转到当前项目的覆盖率报告页面", url, false, null));
            actions.add(new AIActionVo("link", "查看覆盖率入口", "在快捷入口中保留覆盖率报告链接", url, false, null));
        }

        actions.addAll(buildHeaderNavigationActions(projectId, apps, questionText));

        if ((onSnapshotPage || isRoute(routeContext, "snapshot", "snapshot.my", "snapshot.list", "snapshot.detail"))
                && containsAny(questionText, "自动保存", "自动快照", "保存我的快照", "自动沉淀")
                && containsAny(questionText, "快照", "snapshot")) {
            boolean disableAutoSave = containsAny(questionText, "关闭", "取消", "停止", "停用", "禁用", "不要", "不再", "关掉", "取消自动", "关闭自动");
            boolean systemSnapshot = containsAny(questionText, "系统快照", "system snapshot", "系统");
            if (onMonitorPage) {
                actions.add(buildMonitorPageAction(
                        disableAutoSave
                                ? (systemSnapshot ? "disableAutoSaveSystem" : "disableAutoSaveMy")
                                : (systemSnapshot ? "enableAutoSaveSystem" : "enableAutoSaveMy"),
                        (disableAutoSave ? "关闭" : "开启") + (systemSnapshot ? "自动保存系统快照" : "自动保存我的快照"),
                        "已" + (disableAutoSave ? "关闭" : "开启") + (systemSnapshot ? "自动保存系统快照" : "自动保存我的快照") + "。"));
            } else {
                actions.add(new AIActionVo("navigate", "前往实时监控", "自动保存快照属于监控页能力，请先进入实时监控页面确认采集范围", "/p/" + projectId + "/monitor", true,
                        disableAutoSave ? "即将跳转到实时监控页面。进入页面后可让我直接关闭对应自动保存开关。" : "即将跳转到实时监控页面。进入页面后可让我直接开启对应自动保存开关。"));
            }
            actions.add(new AIActionVo("link", "我的快照", "查看已保存的个人快照", "/p/" + projectId + "/snapshot/my", false, null));
        }

        if ((onMonitorPage || isRoute(routeContext, "trace", "trace.recent", "trace.app", "trace.detail"))
                && containsAny(questionText, "自动刷新", "刷新列表", "刷新监控", "最新数据", "获取最新", "刷新链路", "更新链路")) {
            boolean disableRefresh = containsAny(questionText, "关闭", "取消", "停止", "停用", "禁用", "不要", "不再", "关掉");
            Integer refreshSeconds = extractRefreshSeconds(questionText);
            if (refreshSeconds != null) {
                actions.add(buildMonitorPageAction("setAutoRefreshSeconds", "设置自动刷新间隔", "已设置自动刷新间隔。", "seconds", refreshSeconds));
            } else {
                String actionName = containsAny(questionText, "最新", "刷新列表", "刷新监控", "获取") && !containsAny(questionText, "自动刷新")
                        ? "refreshMonitorList"
                        : (disableRefresh ? "disableAutoRefresh" : "enableAutoRefresh");
                actions.add(buildMonitorPageAction(actionName,
                        actionName.equals("refreshMonitorList") ? "刷新监控列表" : (disableRefresh ? "关闭自动刷新" : "开启自动刷新"),
                        actionName.equals("refreshMonitorList") ? "已刷新监控列表。" : "已" + (disableRefresh ? "关闭" : "开启") + "自动刷新。"));
            }
        }

        if ((onMonitorPage || onSnapshotPage)
                && !containsAny(questionText, "自动保存")
                && containsAny(questionText, "我的快照", "快照列表")
                && containsAny(questionText, "打开", "跳转", "去", "查看", "进入")) {
            actions.add(buildMonitorPageAction("openMySnapshots", "打开我的快照", "正在打开我的快照页面。"));
        }

        if ((onMonitorPage || onSnapshotPage)
                && (containsAny(questionText, "保存快照", "创建快照", "新建快照", "手动保存")
                || (containsAny(questionText, "保存", "存起来") && containsAny(questionText, "快照"))) && !containsAny(questionText, "自动保存")) {
            boolean systemSnapshot = containsAny(questionText, "系统快照", "系统");
            boolean batchSave = containsAny(questionText, "全部", "所有", "都", "批量", "一次性", "一起", "列表里", "当前列表", "当前监控数据", "监控列表");
            if (batchSave) {
                actions.add(buildMonitorPageAction(systemSnapshot ? "batchSaveSystemSnapshots" : "batchSaveMySnapshots",
                        systemSnapshot ? "批量保存系统快照" : "批量保存我的快照",
                        systemSnapshot ? "已开始批量保存系统快照。" : "已开始批量保存我的快照。"));
            } else {
                actions.add(buildMonitorPageAction(systemSnapshot ? "openCreateSystemSnapshot" : "openCreateMySnapshot",
                        systemSnapshot ? "打开系统快照保存弹窗" : "打开我的快照保存弹窗",
                        systemSnapshot ? "已尝试打开系统快照保存弹窗。" : "已尝试打开我的快照保存弹窗。"));
            }
        }

        if ((onMonitorPage || onSnapshotPage) && containsAny(text, "清空", "清除") && containsAny(text, "列表", "快照", "监控", "请求")) {
            actions.add(buildMonitorPageAction("clearMonitorList", "清空监控列表", "已清空当前监控列表。"));
        }

        if (onMonitorPage && containsAny(text, "刷新探针", "探针状态", "在线探针")) {
            actions.add(buildMonitorPageAction("refreshProbeStatus", "刷新探针状态", "已刷新探针状态。"));
        }

        if (onMonitorPage && containsAny(text, "全部探针", "聚合", "所有探针")) {
            actions.add(buildMonitorPageAction("setScopeAggregate", "切换到全部探针", "已切换到全部探针视图。"));
        } else if (onMonitorPage && containsAny(text, "当前探针", "单探针")) {
            actions.add(buildMonitorPageAction("setScopeCurrent", "切换到当前探针", "已尝试切换到当前探针视图。"));
        } else if (onMonitorPage && containsAny(text, "多探针泳道", "泳道")) {
            actions.add(buildMonitorPageAction("setScopeLanes", "切换到多探针泳道", "已切换到多探针泳道视图。"));
        }

        if (actions.isEmpty() && (onCoveragePage || isRoute(routeContext, "coverage", "coverage.overview", "coverage.app", "coverage.trend", "coverage.low", "quality.lowComplexity", "quality.report"))) {
            actions.add(new AIActionVo("link", "打开覆盖率报告", "查看代码覆盖率详情", buildCoverageUrl(projectId, apps, questionText), false, null));
        }
        return actions;
    }

    private AIActionVo buildMonitorPageAction(String actionName, String title, String description) {
        return buildMonitorPageAction(actionName, title, description, null, null);
    }

    private AIActionVo buildMonitorPageAction(String actionName, String title, String description, String payloadKey, Object payloadValue) {
        AIActionVo action = new AIActionVo("monitorPageAction", title, description, null, false, null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", actionName);
        if (payloadKey != null) {
            payload.put(payloadKey, payloadValue);
        }
        action.setPayload(payload);
        return action;
    }

    private Integer extractRefreshSeconds(String text) {
        if (!containsAny(text, "秒", "s") || !containsAny(text, "自动刷新", "刷新间隔", "间隔")) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,3})\\s*(秒|s)").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        int seconds = Integer.parseInt(matcher.group(1));
        return Math.max(1, Math.min(120, seconds));
    }

    private String buildCoverageUrl(String projectId, List<AppVo> apps) {
        return buildCoverageUrl(projectId, apps, null);
    }

    private String buildCoverageUrl(String projectId, List<AppVo> apps, String questionText) {
        AppVo targetApp = findCoverageTargetApp(apps, questionText);
        if (targetApp != null && StringUtils.hasText(targetApp.getId())) {
            CoverageReportIndex latestReport = findLatestCoverageReport(targetApp.getId());
            if (latestReport != null && StringUtils.hasText(latestReport.getId())) {
                return buildCoverageOverviewUrl(projectId, targetApp.getId(), latestReport);
            }
            if (StringUtils.hasText(targetApp.getCurrentVersion())) {
                return "/p/" + projectId + "/coverage/overview?appId=" + encodeQueryParam(targetApp.getId())
                        + "&versionNumber=" + encodeQueryParam(targetApp.getCurrentVersion());
            }
        }
        return "/p/" + projectId + "/coverage/overview";
    }

    private String buildCoverageOverviewUrl(String projectId, String appId, CoverageReportIndex report) {
        String targetAppId = StringUtils.hasText(report.getAppId()) ? report.getAppId() : appId;
        StringBuilder url = new StringBuilder("/p/").append(projectId).append("/coverage/overview?appId=")
                .append(encodeQueryParam(targetAppId));
        if (StringUtils.hasText(report.getVersionNumber())) {
            url.append("&versionNumber=").append(encodeQueryParam(report.getVersionNumber()));
        }
        url.append("&reportId=").append(encodeQueryParam(report.getId()));
        if (StringUtils.hasText(report.getRepoCommitId())) {
            url.append("&commitId=").append(encodeQueryParam(report.getRepoCommitId()));
        }
        return url.toString();
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private AppVo findCoverageTargetApp(List<AppVo> apps, String questionText) {
        AppVo targetApp = findExplicitTargetApp(apps, questionText);
        if (targetApp != null) {
            return targetApp;
        }
        if (apps == null || apps.isEmpty()) {
            return null;
        }

        AppVo fallback = apps.get(0);
        AppVo newestApp = null;
        CoverageReportIndex newestReport = null;
        for (AppVo app : apps) {
            if (app == null || !StringUtils.hasText(app.getId())) {
                continue;
            }
            CoverageReportIndex latestReport = findLatestCoverageReport(app.getId());
            if (latestReport == null) {
                continue;
            }
            if (newestReport == null || isReportNewer(latestReport, newestReport)) {
                newestReport = latestReport;
                newestApp = app;
            }
        }
        return newestApp != null ? newestApp : fallback;
    }

    private AppVo findExplicitTargetApp(List<AppVo> apps, String questionText) {
        if (apps == null || apps.isEmpty() || !StringUtils.hasText(questionText)) {
            return null;
        }
        String normalizedQuestion = questionText.toLowerCase();
        for (AppVo app : apps) {
            if (app != null && StringUtils.hasText(app.getName()) && normalizedQuestion.contains(app.getName().toLowerCase())) {
                return app;
            }
        }
        return null;
    }

    private CoverageReportIndex findLatestCoverageReport(String appId) {
        List<CoverageReportIndex> reports = coverageService.getReportsByAppId(appId);
        if (reports == null || reports.isEmpty()) {
            return null;
        }
        return reports.stream()
                .filter(report -> report != null && StringUtils.hasText(report.getId()))
                .max(Comparator.comparing(CoverageReportIndex::getCreateTime, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private boolean isReportNewer(CoverageReportIndex candidate, CoverageReportIndex current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        if (candidate.getCreateTime() == null) {
            return false;
        }
        if (current.getCreateTime() == null) {
            return true;
        }
        return candidate.getCreateTime().after(current.getCreateTime());
    }

    private List<AIActionVo> buildHeaderNavigationActions(String projectId, List<AppVo> apps, String questionText) {
        List<AIActionVo> actions = new ArrayList<>();
        if (!containsAny(questionText, "跳转", "打开", "进入", "去", "切换到", "访问", "查看")) {
            return actions;
        }

        AppVo targetApp = findTargetApp(apps, questionText);
        String appId = targetApp == null ? null : targetApp.getId();

        if (containsAny(questionText, "项目主页", "首页", "项目首页", "概览")) {
            actions.add(buildAutoNavigateAction("打开项目主页", "/p/" + projectId + "/home"));
        } else if (containsAny(questionText, "搜索")) {
            actions.add(buildAutoNavigateAction("打开搜索", "/p/" + projectId + "/map/home"));
        } else if (containsAny(questionText, "监控台", "实时监控", "监控页")) {
            actions.add(buildAutoNavigateAction("打开监控台", "/p/" + projectId + "/monitor"));
        } else if (containsAny(questionText, "ai interactive", "ai工作台", "ai interactive", "工作台")) {
            actions.add(buildAutoNavigateAction("打开 AI Interactive", "/p/" + projectId + "/AIInteractive"));
        } else if (containsAny(questionText, "应用中心", "应用列表")) {
            actions.add(buildAutoNavigateAction("打开应用中心", "/p/" + projectId + "/app/list"));
        } else if (containsAny(questionText, "在线应用")) {
            actions.add(buildAutoNavigateAction("打开在线应用", "/p/" + projectId + "/app/online"));
        } else if (containsAny(questionText, "添加应用", "新增应用", "创建应用")) {
            actions.add(buildAutoNavigateAction("打开添加应用", "/p/" + projectId + "/app/create"));
        } else if (containsAny(questionText, "创建新项目", "新建项目", "创建项目")) {
            actions.add(buildAutoNavigateAction("打开创建新项目", "/project/create"));
        } else if (containsAny(questionText, "项目设置", "设置")) {
            actions.add(buildAutoNavigateAction("打开项目设置", "/p/" + projectId + "/edit"));
        } else if (containsAny(questionText, "我的项目", "项目列表", "切换项目")) {
            actions.add(buildAutoNavigateAction("打开我的项目列表", "/myProjects"));
        } else if (containsAny(questionText, "用户设置", "个人设置", "账号设置")) {
            actions.add(buildAutoNavigateAction("打开用户设置", "/user/info"));
        } else if (containsAny(questionText, "注销", "退出登录", "登出")) {
            actions.add(new AIActionVo("navigate", "注销退出", "即将注销当前账号", "/user/logout", true, "确认要注销退出吗？"));
        } else if (containsAny(questionText, "我的快照")) {
            actions.add(buildAutoNavigateAction("打开我的快照", "/p/" + projectId + "/snapshot/my"));
        } else if (appId != null && containsAny(questionText, "系统快照")) {
            actions.add(buildAutoNavigateAction("打开系统快照", "/p/" + projectId + "/" + appId + "/snapshot/list"));
        } else if (appId != null && containsAny(questionText, "版本比对", "版本比较")) {
            actions.add(buildAutoNavigateAction("打开版本比对", "/p/" + projectId + "/" + appId + "/version/compare"));
        } else if (appId != null && containsAny(questionText, "覆盖率报告", "覆盖率")) {
            actions.add(buildAutoNavigateAction("打开覆盖率报告", buildCoverageUrl(projectId, apps, questionText)));
        } else if (appId != null) {
            actions.add(buildAutoNavigateAction("打开应用系统快照", "/p/" + projectId + "/" + appId + "/snapshot/list"));
        }
        return actions;
    }

    private AIActionVo buildAutoNavigateAction(String title, String url) {
        AIActionVo action = new AIActionVo("navigate", title, "正在跳转到" + title.replace("打开", "") + "。", url, false, null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("autoExecute", true);
        action.setPayload(payload);
        return action;
    }

    private AppVo findTargetApp(List<AppVo> apps, String questionText) {
        if (apps == null || apps.isEmpty()) {
            return null;
        }
        for (AppVo app : apps) {
            if (app != null && StringUtils.hasText(app.getName()) && questionText.contains(app.getName().toLowerCase())) {
                return app;
            }
        }
        if (containsAny(questionText, "应用", "系统快照", "版本比对", "覆盖率")) {
            return apps.get(0);
        }
        return null;
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

    private RouteContext detectRoute(String question, String contextSummary) {
        String text = ((question == null ? "" : question) + " " + (contextSummary == null ? "" : contextSummary)).toLowerCase();

        if (isBugInspectionQuestion(text)) {
            if (containsAny(text, "方法", "method", "函数")) {
                return new RouteContext("bug_detect", "bug.method");
            }
            return new RouteContext("bug_detect", "bug.class");
        }

        if (isBusinessLogicQuestion(text)) {
            if (containsAny(text, "方法", "method", "函数")) {
                return new RouteContext("business_logic", "business_logic.method");
            }
            return new RouteContext("business_logic", "business_logic.class");
        }

        if (isMethodCallGraphQuestion(text)) {
            return new RouteContext("code_relation", "code_relation.callGraph");
        }

        String pageRoute = detectPageRoute(contextSummary);

        if (StringUtils.hasText(pageRoute)) {
            return routeByPageFirst(pageRoute, text);
        }

        if (containsAny(text, "覆盖率是多少", "这个项目的代码覆盖率", "项目覆盖率", "整体覆盖率", "覆盖率概览", "代码覆盖情况")) {
            return new RouteContext("coverage", "coverage.overview");
        }
        if (containsAny(text, "哪个模块的覆盖率最低", "模块覆盖率最低", "覆盖率最低的模块", "低覆盖模块", "低覆盖类", "未覆盖类", "漏测")) {
            return new RouteContext("coverage", "coverage.low");
        }
        if (containsAny(text, "覆盖率趋势", "趋势", "最近覆盖率", "历史覆盖率", "变化趋势")) {
            return new RouteContext("coverage", "coverage.trend");
        }
        if (containsAny(text, "应用覆盖率", "某个应用覆盖率", "应用的覆盖率", "app覆盖率")) {
            return new RouteContext("coverage", "coverage.app");
        }
        if (containsAny(text, "高复杂度", "圈复杂度", "复杂度高", "代码复杂度", "复杂度")) {
            return new RouteContext("quality", "quality.lowComplexity");
        }
        if (containsAny(text, "代码质量", "质量报告", "代码质量分析")) {
            return new RouteContext("quality", "quality.report");
        }
        if (containsAny(text, "根因", "异常定位", "线上缺陷", "故障定位", "异常分析", "出错链路")) {
            return new RouteContext("defect", "defect.rootCause");
        }
        if (containsAny(text, "慢接口", "性能回归", "性能退化", "耗时", "p95", "p99", "平均响应", "性能")) {
            return new RouteContext("performance", "performance.overview");
        }
        if (containsAny(text, "调用链", "链路", "trace", "请求链", "上下游")) {
            if (isMethodCallGraphQuestion(text)) {
                return new RouteContext("code_relation", "code_relation.callGraph");
            }
            if (containsAny(text, "最近", "最新", "列表")) {
                return new RouteContext("trace", "trace.recent");
            }
            if (containsAny(text, "应用", "app")) {
                return new RouteContext("trace", "trace.app");
            }
            return new RouteContext("trace", "trace.detail");
        }
        if (containsAny(text, "快照", "snapshot", "版本比对", "版本比较", "发布前", "上线前")) {
            if (containsAny(text, "我的", "个人")) {
                return new RouteContext("snapshot", "snapshot.my");
            }
            if (containsAny(text, "详情", "明细")) {
                return new RouteContext("snapshot", "snapshot.detail");
            }
            return new RouteContext("snapshot", "snapshot.list");
        }
        if (containsAny(text, "应用", "app", "在线", "状态", "列表")) {
            return new RouteContext("app", "app.status");
        }
        if (containsAny(text, "测试", "test", "补测", "回归")) {
            return new RouteContext("testcase", "testcase.recommend");
        }
        if (containsAny(text, "项目", "project", "概览", "统计", "总览")) {
            return new RouteContext("project", "project.overview");
        }
        if (containsAny(text, "代码", "关系", "依赖", "调用关系", "类关系")) {
            return new RouteContext("code_relation", "code_relation.search");
        }
        return new RouteContext("general", "general");
    }

    private boolean isBugInspectionQuestion(String text) {
        return containsAny(text, "bug", "缺陷", "代码缺陷", "源码缺陷", "潜在问题", "可能存在", "风险", "空指针", "资源泄漏", "并发问题", "逻辑错误")
                && containsAny(text, "类", "方法", "method", "函数", "源码", "代码", "controller", "service", "branch");
    }

    private boolean isBusinessLogicQuestion(String text) {
        return containsAny(text, "业务需求", "业务逻辑", "业务规则", "业务场景", "业务含义", "需求分析",
                "功能逻辑", "功能需求", "方法职责", "类职责", "实现什么", "干什么", "做什么",
                "处理什么业务", "分析业务", "梳理业务");
    }

    private boolean isMethodCallGraphQuestion(String text) {
        return containsAny(text, "调用链", "调用关系", "上下游", "谁调用", "调用了谁", "依赖关系", "关系图", "调用图")
                && containsAny(text, "方法", "method", "函数");
    }

    private String detectPageRoute(String pageContext) {
        if (!StringUtils.hasText(pageContext)) {
            return null;
        }
        String text = pageContext.toLowerCase();
        if (matchesRoute(text, routeConfig != null ? routeConfig.getCoverage().getKeywords() : null,
                "覆盖率页", "页面类型:覆盖率页", "coverage", "覆盖率详情", "覆盖率报告")) {
            return "coverage";
        }
        if (matchesRoute(text, routeConfig != null ? routeConfig.getTrace().getKeywords() : null,
                "监控页", "页面类型:监控页", "实时监控", "monitor")) {
            return "trace";
        }
        if (matchesRoute(text, routeConfig != null ? routeConfig.getSnapshot().getKeywords() : null,
                "快照页", "页面类型:快照页", "snapshot", "我的快照", "快照列表")) {
            return "snapshot";
        }
        if (matchesRoute(text, routeConfig != null ? routeConfig.getApp().getKeywords() : null,
                "应用页", "页面类型:应用页", "应用中心", "app/list", "app/online")) {
            return "app";
        }
        if (matchesRoute(text, routeConfig != null ? routeConfig.getCodeRelation().getKeywords() : null,
                "代码关系", "类关系", "callgraph", "关系图")) {
            return "code_relation";
        }
        return null;
    }

    private boolean matchesRoute(String text, List<String> configuredKeywords, String... fallbackKeywords) {
        if (configuredKeywords != null) {
            for (String keyword : configuredKeywords) {
                if (StringUtils.hasText(keyword) && text.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        }
        return containsAny(text, fallbackKeywords);
    }

    private RouteContext routeByPageFirst(String pageRoute, String text) {
        if ("coverage".equals(pageRoute)) {
            if (containsAny(text, "趋势", "历史", "最近")) {
                return new RouteContext("coverage", "coverage.trend");
            }
            if (containsAny(text, "低覆盖", "漏测", "未覆盖")) {
                return new RouteContext("coverage", "coverage.low");
            }
            if (containsAny(text, "应用", "app")) {
                return new RouteContext("coverage", "coverage.app");
            }
            if (containsAny(text, "复杂度", "高复杂度", "代码质量")) {
                return new RouteContext("quality", "quality.lowComplexity");
            }
            return new RouteContext("coverage", "coverage.overview");
        }
        if ("trace".equals(pageRoute)) {
            if (isMethodCallGraphQuestion(text)) {
                return new RouteContext("code_relation", "code_relation.callGraph");
            }
            if (containsAny(text, "最近", "最新", "列表")) {
                return new RouteContext("trace", "trace.recent");
            }
            if (containsAny(text, "应用", "app")) {
                return new RouteContext("trace", "trace.app");
            }
            if (containsAny(text, "对比", "差异", "正常", "异常")) {
                return new RouteContext("trace", "trace.detail");
            }
            return new RouteContext("trace", "trace.detail");
        }
        if ("snapshot".equals(pageRoute)) {
            if (containsAny(text, "我的", "个人")) {
                return new RouteContext("snapshot", "snapshot.my");
            }
            if (containsAny(text, "详情", "明细")) {
                return new RouteContext("snapshot", "snapshot.detail");
            }
            return new RouteContext("snapshot", "snapshot.list");
        }
        if ("app".equals(pageRoute)) {
            if (containsAny(text, "在线", "运行中")) {
                return new RouteContext("app", "app.status");
            }
            if (containsAny(text, "详情")) {
                return new RouteContext("app", "app.detail");
            }
            return new RouteContext("app", "app.status");
        }
        if ("code_relation".equals(pageRoute)) {
            if (isMethodCallGraphQuestion(text)) {
                return new RouteContext("code_relation", "code_relation.callGraph");
            }
            return new RouteContext("code_relation", "code_relation.search");
        }
        return new RouteContext("general", "general");
    }

    private List<String> buildFollowUpSuggestions(List<AppVo> apps, RouteContext routeContext) {
        List<String> suggestions = new ArrayList<>();
        switch (routeContext.routeKey) {
            case "bug.class":
                suggestions.add("请分析这个类的空指针和逻辑风险");
                suggestions.add("继续分析这个类的高风险方法");
                break;
            case "bug.method":
                suggestions.add("请逐行分析这个方法的潜在 bug");
                suggestions.add("给出这个方法的修复建议");
                break;
            case "business_logic.class":
                suggestions.add("继续分析这个类的核心业务规则");
                suggestions.add("这个类有哪些潜在 bug？");
                break;
            case "business_logic.method":
                suggestions.add("继续分析这个方法的分支含义");
                suggestions.add("显示这个方法的真实调用关系");
                break;
            case "coverage.overview":
                suggestions.add("哪个模块的覆盖率最低？");
                suggestions.add("最近覆盖率趋势如何？");
                break;
            case "coverage.low":
                suggestions.add("这些低覆盖模块对应哪些测试用例？");
                suggestions.add("有没有高复杂度且低覆盖的模块？");
                break;
            case "coverage.trend":
                suggestions.add("这个应用的最新覆盖率如何？");
                suggestions.add("哪个模块的覆盖率最低？");
                break;
            case "coverage.app":
                suggestions.add("这个应用有哪些低覆盖类？");
                suggestions.add("这个应用最近覆盖率变化如何？");
                break;
            case "quality.lowComplexity":
                suggestions.add("哪些方法复杂度最高？");
                suggestions.add("哪些高复杂度类同时覆盖率最低？");
                break;
            case "quality.report":
                suggestions.add("找出高复杂度模块");
                suggestions.add("查看低覆盖模块");
                break;
            case "trace.recent":
            case "trace.detail":
                suggestions.add("分析这条链路的慢点和异常根因");
                suggestions.add("对比最近两条链路差异");
                break;
            case "trace.app":
                suggestions.add("查看最近的调用链路");
                suggestions.add("分析慢接口");
                break;
            case "snapshot.list":
            case "snapshot.detail":
            case "snapshot.my":
                suggestions.add("查看当前项目的覆盖率报告");
                suggestions.add("对比两个版本的快照");
                break;
            case "app.status":
                suggestions.add("查看应用详情");
                suggestions.add("分析在线应用状态");
                break;
            case "testcase.recommend":
                suggestions.add("基于低覆盖模块推荐测试用例");
                suggestions.add("查看覆盖率提升建议");
                break;
            case "project.overview":
                suggestions.add("查看项目覆盖率概览");
                suggestions.add("有哪些应用在线？");
                break;
            case "code_relation.search":
                suggestions.add("查看这个类的调用关系图");
                suggestions.add("搜索相关接口或方法");
                break;
            case "code_relation.callGraph":
                suggestions.add("继续分析这个方法的上下游影响面");
                suggestions.add("分析这个方法可能存在的 bug");
                break;
            default:
                suggestions.add("帮我分析项目状态");
                suggestions.add("查看覆盖率报告");
                suggestions.add("有哪些应用在线？");
        }
        return suggestions;
    }

    private boolean isRoute(RouteContext routeContext, String topicKey, String... routeKeys) {
        if (routeContext == null) {
            return false;
        }
        if (topicKey != null && topicKey.equals(routeContext.topicKey)) {
            return true;
        }
        if (routeKeys != null) {
            for (String routeKey : routeKeys) {
                if (routeKey != null && routeKey.equals(routeContext.routeKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class RouteContext {
        private final String topicKey;
        private final String routeKey;

        private RouteContext(String topicKey, String routeKey) {
            this.topicKey = topicKey;
            this.routeKey = routeKey;
        }
    }
}
