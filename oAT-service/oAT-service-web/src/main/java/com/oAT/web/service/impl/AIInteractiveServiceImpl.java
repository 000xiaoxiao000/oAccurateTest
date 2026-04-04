package com.oAT.web.service.impl;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AIInteractiveServiceImpl implements AIInteractiveService {

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
        return page;
    }

    @Override
    public AIInteractiveReplyVo ask(String projectId, UserVo user, String question, String pageContext) {
        ProjectVo project = projectService.getProject(projectId);
        List<AppVo> apps = loadApps(projectId);
        String cleanQuestion = question == null ? "" : question.trim();
        String contextSummary = normalizePageContext(pageContext);
        String topic = detectTopic(cleanQuestion + " " + contextSummary);

        AIInteractiveReplyVo reply = new AIInteractiveReplyVo();
        reply.setQuestion(cleanQuestion);
        reply.setTopic(topic);
        reply.setAnswer(buildAnswer(user, project, apps, cleanQuestion, topic, contextSummary));
        reply.setSuggestions(buildFollowUpSuggestions(apps, topic));
        reply.setQuickLinks(buildQuickLinks(projectId, apps, topic));
        return reply;
    }

    private List<AppVo> loadApps(String projectId) {
        List<AppVo> apps = appService.getAppList(projectId);
        for (AppVo app : apps) {
            app.setOnlineCount(clientSessionService.getOnlineSessionsByAppId(app.getId()).size());
        }
        return apps;
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
        StringBuilder summary = new StringBuilder();
        summary.append("这是项目 ").append(project.getName()).append(" 的专属 AI 工作台。");
        if (StringUtils.hasText(project.getDescribe())) {
            summary.append(" 当前项目描述为：").append(project.getDescribe()).append("。");
        }
        if (apps.isEmpty()) {
            summary.append(" 目前还没有接入应用，建议先添加应用后再进行链路排查和数据分析。");
        } else {
            summary.append(" 当前共接入 ").append(apps.size()).append(" 个应用");
            summary.append("，其中 ").append(countOnlineApps(apps)).append(" 个处于在线状态。");
        }
        return summary.toString();
    }

    private String buildWelcomeMessage(UserVo user, ProjectVo project, List<AppVo> apps, String mascotName) {
        StringBuilder message = new StringBuilder();
        message.append("你好，").append(user.getName()).append("。我是 ").append(mascotName).append("，");
        message.append("会结合项目 ").append(project.getName()).append(" 的上下文帮你做数据交互。");
        if (!apps.isEmpty()) {
            message.append("\n我已经看到了 ").append(apps.size()).append(" 个应用，可以先帮你总结项目概况、梳理应用状态，或者给出监控排查建议。");
        } else {
            message.append("\n当前项目还没有应用数据，我可以先带你熟悉平台入口，并告诉你接下来该补哪些数据。");
        }
        message.append("\n你可以直接提问，也可以点下面的快捷问题开始。");
        return message.toString();
    }

    private String buildMascotHint(ProjectVo project, List<AppVo> apps) {
        if (apps.isEmpty()) {
            return "先完成应用接入后，我会更擅长做链路与快照分析。";
        }
        return "我会优先围绕 " + project.getName() + " 的应用列表、监控、快照和搜索入口来回答。";
    }

    private List<String> buildStarterQuestions(List<AppVo> apps) {
        List<String> questions = new ArrayList<>();
        questions.add("帮我总结一下当前项目概况");
        questions.add("我现在应该先看哪些数据");
        if (!apps.isEmpty()) {
            questions.add("帮我看看当前应用接入情况");
            questions.add("如果线上有异常，排查顺序是什么");
        } else {
            questions.add("当前没有应用时，我应该先做什么");
            questions.add("这个平台里哪些入口最适合做数据分析");
        }
        return questions;
    }

    private List<AIAbilityCardVo> buildAbilityCards(ProjectVo project, List<AppVo> apps) {
        List<AIAbilityCardVo> cards = new ArrayList<>();
        cards.add(new AIAbilityCardVo("项目全景", project.getName(), "快速查看项目概况、应用接入和在线情况。"));
        cards.add(new AIAbilityCardVo("监控排查", countOnlineApps(apps) + " 个应用在线", "结合监控台与链路节点，定位问题入口。"));
        cards.add(new AIAbilityCardVo("快照协作", "沉淀关键现场", "将排查过程保存为快照，方便团队共享和复盘。"));
        cards.add(new AIAbilityCardVo("搜索分析", "按场景追数据", "从搜索、地图和应用中心切换到更细的分析视角。"));
        return cards;
    }

    private List<AIQuickLinkVo> buildQuickLinks(String projectId, List<AppVo> apps, String topic) {
        List<AIQuickLinkVo> links = new ArrayList<>();
        if ("troubleshoot".equals(topic)) {
            links.add(link("进入监控台", "查看最近几分钟的请求链路", "/p/" + projectId + "/monitor"));
            links.add(link("我的快照", "沉淀和复盘关键问题现场", "/p/" + projectId + "/snapshot/my"));
            links.add(link("在线应用", "先锁定当前在线服务范围", "/p/" + projectId + "/app/online"));
            return links;
        }
        if ("application".equals(topic)) {
            links.add(link("应用列表", "查看应用接入与配置情况", "/p/" + projectId + "/app/list"));
            links.add(link("在线应用", "查看当前活跃实例", "/p/" + projectId + "/app/online"));
            links.add(link("项目地图", "从项目视角看应用结构", "/p/" + projectId + "/map/home"));
            return links;
        }
        if ("search".equals(topic)) {
            links.add(link("搜索入口", "按关键词搜快照与链路", "/p/" + projectId + "/search"));
            links.add(link("项目地图", "从可视化视角切入", "/p/" + projectId + "/map/home"));
            links.add(link("快照列表", "回看历史沉淀内容", "/p/" + projectId + "/snapshot/list"));
            return links;
        }
        if ("snapshot".equals(topic)) {
            links.add(link("我的快照", "查看个人沉淀的问题现场", "/p/" + projectId + "/snapshot/my"));
            links.add(link("快照列表", "浏览项目沉淀的所有快照", "/p/" + projectId + "/snapshot/list"));
            links.add(link("监控台", "从实时请求继续创建快照", "/p/" + projectId + "/monitor"));
            return links;
        }
        links.add(link("项目主页", "快速建立当前项目上下文", "/p/" + projectId + "/home"));
        links.add(link("监控台", "查看实时请求与调用链", "/p/" + projectId + "/monitor"));
        links.add(link("搜索入口", "按关键词追踪数据线索", "/p/" + projectId + "/search"));
        if (!apps.isEmpty()) {
            links.add(link("应用列表", "查看接入应用与在线状态", "/p/" + projectId + "/app/list"));
        }
        return links;
    }

    private AIQuickLinkVo link(String title, String description, String url) {
        return new AIQuickLinkVo(title, description, url);
    }

    private List<String> buildFollowUpSuggestions(List<AppVo> apps, String topic) {
        if ("overview".equals(topic)) {
            return Arrays.asList("帮我看看当前应用接入情况", "我现在应该先看哪些数据", "给我一个排查入口建议");
        }
        if ("application".equals(topic)) {
            return Arrays.asList("哪些应用值得先关注", "如果线上有异常，排查顺序是什么", "帮我总结项目概况");
        }
        if ("troubleshoot".equals(topic)) {
            return Arrays.asList("先看监控还是快照", "帮我看看当前应用接入情况", "给我一个排查清单");
        }
        if ("snapshot".equals(topic)) {
            return Arrays.asList("快照适合解决什么问题", "如何从监控进入快照", "帮我给出复盘建议");
        }
        if ("search".equals(topic)) {
            return Arrays.asList("我现在应该先看哪些数据", "搜索和监控怎么配合", "帮我总结项目概况");
        }
        if (apps.isEmpty()) {
            return Arrays.asList("当前没有应用时，我应该先做什么", "这个平台里哪些入口最适合做数据分析");
        }
        return Arrays.asList("帮我总结一下当前项目概况", "我现在应该先看哪些数据", "如果线上有异常，排查顺序是什么");
    }

    private String detectTopic(String question) {
        if (containsAny(question, "概况", "总结", "简介", "全景", "项目")) {
            return "overview";
        }
        if (containsAny(question, "应用", "服务", "在线", "接入")) {
            return "application";
        }
        if (containsAny(question, "异常", "排查", "问题", "告警", "故障")) {
            return "troubleshoot";
        }
        if (containsAny(question, "快照", "复盘", "沉淀")) {
            return "snapshot";
        }
        if (containsAny(question, "搜索", "trace", "链路", "调用")) {
            return "search";
        }
        if (containsAny(question, "下一步", "建议", "怎么做", "看哪些")) {
            return "guide";
        }
        return "general";
    }

    private String buildAnswer(UserVo user, ProjectVo project, List<AppVo> apps, String question, String topic, String pageContext) {
        if ("overview".equals(topic)) {
            return prependPageContext(buildProjectOverviewAnswer(user, project, apps), pageContext);
        }
        if ("application".equals(topic)) {
            return prependPageContext(buildApplicationAnswer(apps), pageContext);
        }
        if ("troubleshoot".equals(topic)) {
            return prependPageContext(buildTroubleshootingAnswer(project, apps), pageContext);
        }
        if ("snapshot".equals(topic)) {
            return prependPageContext("你可以先进入监控台定位一次典型请求，再把关键链路保存成快照。\n"
                    + "快照适合沉淀现场、分派给团队成员复查，也适合后续做版本对比和问题复盘。\n"
                    + "如果你愿意，我下一步可以继续帮你整理一份“从监控到快照”的标准操作顺序。", pageContext);
        }
        if ("search".equals(topic)) {
            return prependPageContext("如果你已经知道想找哪类请求，建议先从搜索入口筛选目标，再进入监控台看实时链路。\n"
                    + "如果你还不确定问题落在哪个应用，可以先看应用中心和项目主页，再进入地图或搜索页缩小范围。\n"
                    + "我也可以基于当前项目，给你一条更具体的入口建议。", pageContext);
        }
        if ("guide".equals(topic)) {
            return prependPageContext(buildActionSuggestionAnswer(project, apps), pageContext);
        }
        return prependPageContext("我已经切换到项目 " + project.getName() + " 的上下文。\n"
                + buildProjectSummary(project, apps) + "\n"
                + "你可以继续问我这些方向：项目概况、应用接入、异常排查、快照沉淀，或者直接描述你当前遇到的数据问题。", pageContext);
    }

    private String normalizePageContext(String pageContext) {
        if (!StringUtils.hasText(pageContext)) {
            return "";
        }
        String normalized = pageContext.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 180) {
            normalized = normalized.substring(0, 180) + "...";
        }
        return normalized;
    }

    private String prependPageContext(String answer, String pageContext) {
        if (!StringUtils.hasText(pageContext)) {
            return answer;
        }
        return "我识别到你当前页面关注的是：" + pageContext + "\n" + answer;
    }

    private String buildProjectOverviewAnswer(UserVo user, ProjectVo project, List<AppVo> apps) {
        StringBuilder builder = new StringBuilder();
        builder.append(user.getName()).append("，我先帮你做一个项目概览：\n");
        builder.append("1. 项目名称：").append(project.getName()).append("\n");
        if (StringUtils.hasText(project.getDescribe())) {
            builder.append("2. 项目定位：").append(project.getDescribe()).append("\n");
        } else {
            builder.append("2. 项目定位：当前还没有补充项目描述，建议完善后更方便团队协作。\n");
        }
        builder.append("3. 应用接入数：").append(apps.size()).append("，在线应用数：").append(countOnlineApps(apps)).append("\n");
        builder.append("4. 当前建议：先看项目主页与监控台，再根据目标进入搜索、快照或应用中心。\n");
        if (!apps.isEmpty()) {
            builder.append("5. 代表应用：").append(appNames(apps, 4)).append("\n");
        }
        builder.append("如果你想要更细，我可以继续按“应用状态”或“排查路径”展开。");
        return builder.toString();
    }

    private String buildApplicationAnswer(List<AppVo> apps) {
        if (apps.isEmpty()) {
            return "当前项目还没有接入应用。\n"
                    + "建议先在“应用中心”添加应用，再逐步接入监控数据，这样我才能基于实时状态做更准确的交互分析。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("当前项目一共接入 ").append(apps.size()).append(" 个应用。\n");
        builder.append("在线应用数：").append(countOnlineApps(apps)).append("\n");
        builder.append("优先关注的应用可以从这些开始：").append(appNames(apps, 6)).append("\n");
        builder.append("建议动作：先看在线应用，再从监控台筛最近 3 分钟请求，最后把关键链路保存成快照。");
        return builder.toString();
    }

    private String buildTroubleshootingAnswer(ProjectVo project, List<AppVo> apps) {
        StringBuilder builder = new StringBuilder();
        builder.append("如果 ").append(project.getName()).append(" 出现线上异常，我建议按这个顺序排查：\n");
        builder.append("1. 先进入监控台，优先看最近几分钟内的实时请求。\n");
        builder.append("2. 按应用过滤，先聚焦在线应用");
        if (!apps.isEmpty()) {
            builder.append("，例如 ").append(appNames(apps, 3));
        }
        builder.append("。\n");
        builder.append("3. 打开调用链详情，判断问题发生在入口、服务内部、数据库还是 Redis 节点。\n");
        builder.append("4. 对关键请求保存快照，方便后续复盘和团队协作。\n");
        builder.append("如果你需要，我还可以继续把这套流程整理成“排查清单”。");
        return builder.toString();
    }

    private String buildActionSuggestionAnswer(ProjectVo project, List<AppVo> apps) {
        if (apps.isEmpty()) {
            return "我建议你先完成三件事：\n"
                    + "1. 在应用中心补充应用接入。\n"
                    + "2. 完善项目描述，方便后续 AI 给出更贴合业务的建议。\n"
                    + "3. 准备一个典型问题场景，接入后就可以直接从监控和快照开始分析。";
        }
        return "针对项目 " + project.getName() + "，我建议你先这样使用这个模块：\n"
                + "1. 先让我总结项目概况，快速建立当前上下文。\n"
                + "2. 再查看应用接入和在线状态，锁定重点应用。\n"
                + "3. 如果有异常，再切到监控台和快照做深挖。\n"
                + "4. 最后把高频排查路径沉淀为团队可复用的操作习惯。";
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
}
