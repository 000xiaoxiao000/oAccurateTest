package com.oAT.web.service.impl;

import com.oAT.ai.agent.AgentContext;
import com.oAT.ai.agent.AIAgentService;
import com.oAT.web.service.AIInteractiveService;
import com.oAT.web.service.AppService;
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
        reply.setActions(buildActions(projectId, apps, cleanQuestion, contextSummary, topic));
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

    private List<AIActionVo> buildActions(String projectId, List<AppVo> apps, String question, String pageContext, String topic) {
        List<AIActionVo> actions = new ArrayList<>();
        String questionText = (question == null ? "" : question).toLowerCase();
        String text = (questionText + " " + (pageContext == null ? "" : pageContext)).toLowerCase();
        boolean onMonitorPage = containsAny(pageContext, "监控页助手", "页面类型:监控页", "实时监控", "自动保存我的快照");

        if (containsAny(text, "覆盖率", "覆盖", "coverage", "报告")
                && containsAny(text, "看", "查看", "打开", "跳转", "链接", "连接", "去")) {
            String url = buildCoverageUrl(projectId, apps);
            actions.add(new AIActionVo("navigate", "打开覆盖率报告", "跳转到当前项目的覆盖率报告页面", url, false, null));
            actions.add(new AIActionVo("link", "查看覆盖率入口", "在快捷入口中保留覆盖率报告链接", url, false, null));
        }

        actions.addAll(buildHeaderNavigationActions(projectId, apps, questionText));

        if (containsAny(questionText, "自动保存", "自动快照", "保存我的快照", "自动沉淀")
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

        if (onMonitorPage && containsAny(questionText, "自动刷新", "刷新列表", "刷新监控", "最新数据", "获取最新")) {
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

        if (onMonitorPage && !containsAny(questionText, "自动保存")
                && containsAny(questionText, "我的快照", "快照列表")
                && containsAny(questionText, "打开", "跳转", "去", "查看", "进入")) {
            actions.add(buildMonitorPageAction("openMySnapshots", "打开我的快照", "正在打开我的快照页面。"));
        }

        if (onMonitorPage && (containsAny(questionText, "保存快照", "创建快照", "新建快照", "手动保存")
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

        if (onMonitorPage && containsAny(text, "清空", "清除") && containsAny(text, "列表", "监控", "请求")) {
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

        if (actions.isEmpty() && containsAny(topic, "coverage")) {
            actions.add(new AIActionVo("link", "打开覆盖率报告", "查看代码覆盖率详情", buildCoverageUrl(projectId, apps), false, null));
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
        if (apps != null && !apps.isEmpty()) {
            return "/p/" + projectId + "/coverage/details?appId=" + apps.get(0).getId();
        }
        return "/p/" + projectId + "/coverage/overview";
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
            actions.add(buildAutoNavigateAction("打开覆盖率报告", "/p/" + projectId + "/" + appId + "/version/report/list?tab=coverage"));
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
