package com.oAT.web.service.impl;

import com.oAT.ai.agent.AgentContext;
import com.oAT.ai.agent.AgentDataProvider;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AIInteractiveServiceImpl implements AIInteractiveService {

    private static final Logger logger = LoggerFactory.getLogger(AIInteractiveServiceImpl.class);

    private static final String[] MASCOT_NAMES = {"小准", "探探", "跃跃", "灵灵", "星仔", "阿AT"};
    private static final String[] MASCOT_ROLES = {"数据侦察员", "链路向导", "项目陪跑员", "交互分析官", "洞察助手"};
    private static final String[] MASCOT_MOODS = {"专注", "活跃", "机敏", "稳健", "可靠"};
    private static final String[] PRIMARY_COLORS = {"#5865f2", "#00b5ad", "#ff8a65", "#7e57c2", "#26a69a", "#42a5f5"};
    private static final String[] ACCENT_COLORS = {"#8ea1ff", "#65e5dd", "#ffb58a", "#b39ddb", "#80cbc4", "#90caf9"};
    private static final String[] HALO_COLORS = {"rgba(88,101,242,0.18)", "rgba(0,181,173,0.18)", "rgba(255,138,101,0.18)",
            "rgba(126,87,194,0.18)", "rgba(38,166,154,0.18)", "rgba(66,165,245,0.18)"};

    @Autowired
    private ProjectService projectService;

    @Autowired
    private AppService appService;

    @Autowired
    private ClientSessionService clientSessionService;

    // AI Agent 服务 (JDK 17+), 支持自主工具调用
    @Autowired(required = false)
    private AIAgentService aiAgentService;

    @Autowired(required = false)
    private AgentDataProvider agentDataProvider;

    @Value("${ai.llm.timeout:120}")
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
        page.setMascot(mascot);
        page.setAiTimeout(aiTimeout);
        return page;
    }

    @Override
    public AIInteractiveReplyVo ask(String projectId, UserVo user, String question, String pageContext, String imageData) {
        ProjectVo project = projectService.getProject(projectId);
        List<AppVo> apps = loadApps(projectId);
        String cleanQuestion = question == null ? "" : question.trim();
        // 如果有图片但没有文字，生成默认提示
        if (!StringUtils.hasText(cleanQuestion) && StringUtils.hasText(imageData)) {
            cleanQuestion = "[图片提问] 请分析这张图片";
        }
        String contextSummary = normalizePageContext(pageContext);
        String topic = detectTopic(cleanQuestion + " " + contextSummary);

        AIInteractiveReplyVo reply = new AIInteractiveReplyVo();
        reply.setQuestion(cleanQuestion);
        reply.setTopic(topic);

        // 只使用 AI Agent (支持自主工具调用)
        String answer = callAIAgent(project, apps, user, cleanQuestion, contextSummary, imageData);
        reply.setAnswer(answer);

        reply.setSuggestions(buildFollowUpSuggestions(apps, topic));
        reply.setQuickLinks(buildQuickLinks(projectId, apps, topic));
        return reply;
    }

    /**
     * 调用 AI Agent 获取回复
     * AI Agent 可以自主调用工具获取实时数据
     */
    private String callAIAgent(ProjectVo project, List<AppVo> apps, UserVo user,
                                String question, String pageContext, String imageData) {
        // AI Agent 仅在 JDK 17+ 环境下可用
        if (aiAgentService == null || !aiAgentService.isAvailable()) {
            logger.warn("AI Agent service is not available (JDK 17+ required or LLM not configured)");
            return "AI 服务暂不可用，请确保已配置大模型服务（如 Ollama）并启用 AI 功能。";
        }

        try {
            // 创建会话上下文
            AgentContext context = new AgentContext(project.getId(), user.getId(), user.getName());

            logger.info("Calling AI Agent for question: {} (hasImage: {})", question, StringUtils.hasText(imageData));

            String response;
            if (StringUtils.hasText(imageData)) {
                // 图片模式：将 base64 图片数据传递给 AI Agent（多模态）
                response = aiAgentService.chatWithImage(context, question, pageContext, imageData);
            } else if (StringUtils.hasText(pageContext)) {
                response = aiAgentService.chatWithContext(context, question, pageContext);
            } else {
                response = aiAgentService.chat(context, question);
            }

            if (response != null) {
                logger.info("AI Agent response received successfully");
                return response;
            } else {
                return "AI 服务暂时无法响应，请稍后重试。";
            }
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

    private String appNames(List<AppVo> apps, int limit) {
        return apps.stream()
                .map(AppVo::getName)
                .limit(limit)
                .collect(Collectors.joining("、"));
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
                user.getName(), mascotName, project.getName(), apps.size(), online);
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
        links.add(new AIQuickLinkVo("项目概览", "查看项目整体情况", "/project/" + projectId + "/overview"));
        links.add(new AIQuickLinkVo("覆盖率报告", "查看代码覆盖率详情", "/project/" + projectId + "/coverage"));
        links.add(new AIQuickLinkVo("链路追踪", "查看调用链路", "/project/" + projectId + "/trace"));
        return links;
    }

    private String normalizePageContext(String pageContext) {
        if (pageContext == null) {
            return "";
        }
        return pageContext.trim();
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
