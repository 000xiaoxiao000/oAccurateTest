package com.oAT.web.api.ai;

import com.oAT.web.api.ai.AIInteractiveRouteService.RouteContext;
import com.oAT.web.coveragecore.report.CoverageReportCommandService;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.entity.AIActionVo;
import com.oAT.web.service.entity.AIQuickLinkVo;
import com.oAT.web.service.entity.AppVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIInteractiveNavigationService {

    @Autowired
    private CoverageReportCommandService coverageReportCommandService;

    @Autowired
    private AIInteractiveRouteService routeService;

    public List<AIQuickLinkVo> buildQuickLinks(String projectId, List<AppVo> apps, RouteContext routeContext) {
        List<AIQuickLinkVo> links = new ArrayList<>();
        links.add(new AIQuickLinkVo("项目主页", "回到项目整体概况", "/p/" + projectId + "/home"));
        links.add(new AIQuickLinkVo("在线实例", "查看当前在线应用实例", "/p/" + projectId + "/apps/online"));
        if (isRoute(routeContext, "coverage", "coverage.overview", "coverage.app", "coverage.trend", "coverage.low", "quality.lowComplexity", "quality.report")) {
            links.add(new AIQuickLinkVo("覆盖率概览", "查看项目整体覆盖率", "/p/" + projectId + "/coverage"));
        }
        if (apps != null && !apps.isEmpty() && isRoute(routeContext, "coverage", "coverage.overview", "coverage.app", "coverage.trend", "coverage.low", "quality.lowComplexity", "quality.report")) {
            links.add(new AIQuickLinkVo("覆盖率报告", "查看代码覆盖率详情", buildCoverageUrl(projectId, apps)));
        } else if (isRoute(routeContext, "coverage", "coverage.overview", "coverage.app", "coverage.trend", "coverage.low", "quality.lowComplexity", "quality.report")) {
            links.add(new AIQuickLinkVo("覆盖率报告", "查看代码覆盖率详情", "/p/" + projectId + "/coverage"));
        }
        if (isRoute(routeContext, "snapshot", "snapshot.list", "snapshot.detail", "snapshot.my")) {
            links.add(new AIQuickLinkVo("快照列表", "浏览项目沉淀的全部快照", buildSystemSnapshotUrl(projectId, apps, null)));
        }
        if (isRoute(routeContext, "snapshot", "snapshot.my")) {
            links.add(new AIQuickLinkVo("我的快照", "只看当前用户保存的快照", "/p/" + projectId + "/my-snapshots"));
        }
        if (isRoute(routeContext, "trace", "trace.recent", "trace.detail", "trace.app")) {
            links.add(new AIQuickLinkVo("链路地图", "查看调用关系与链路分布", "/p/" + projectId + "/map/home"));
        }
        if (isRoute(routeContext, "trace", "trace.app")) {
            links.add(new AIQuickLinkVo("按应用看链路", "查看当前应用相关调用关系", "/p/" + projectId + "/map/home"));
        }
        if (isRoute(routeContext, "quality", "quality.lowComplexity", "quality.report")) {
            links.add(new AIQuickLinkVo("代码质量", "查看复杂度与质量分析", "/p/" + projectId + "/coverage"));
        }
        return links;
    }

    public List<AIActionVo> buildActions(String projectId, List<AppVo> apps, String question, String pageContext, RouteContext routeContext) {
        List<AIActionVo> actions = new ArrayList<>();
        String questionText = (question == null ? "" : question).toLowerCase();
        String text = (questionText + " " + (pageContext == null ? "" : pageContext)).toLowerCase();
        boolean onMonitorPage = containsAny(pageContext, "监控页助手", "页面类型:监控页", "实时监控", "自动保存我的快照");
        boolean onCoveragePage = containsAny(pageContext, "覆盖率页", "页面类型:覆盖率页", "覆盖率详情", "coverage", "覆盖率报告");
        boolean onSnapshotPage = containsAny(pageContext, "快照页", "页面类型:快照页", "snapshot", "我的快照", "快照列表");

        if (onCoveragePage && !containsAny(text, "快照", "链路", "调用链")
                && containsAny(text, "看", "查看", "打开", "跳转", "链接", "连接", "去")) {
            addCoverageActions(actions, projectId, apps, questionText);
        } else if (isRoute(routeContext, "coverage", "coverage.overview", "coverage.app", "coverage.trend", "coverage.low", "quality.lowComplexity", "quality.report")
                && containsAny(text, "看", "查看", "打开", "跳转", "链接", "连接", "去")) {
            addCoverageActions(actions, projectId, apps, questionText);
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
                actions.add(new AIActionVo("link", "打开实时监控", "进入新前端实时监控页查看调用关系并保存快照。", "/p/" + projectId + "/monitor", false, null));
            }
            actions.add(new AIActionVo("link", "我的快照", "查看已保存的个人快照", "/p/" + projectId + "/my-snapshots", false, null));
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
                        "refreshMonitorList".equals(actionName) ? "刷新监控列表" : (disableRefresh ? "关闭自动刷新" : "开启自动刷新"),
                        "refreshMonitorList".equals(actionName) ? "已刷新监控列表。" : "已" + (disableRefresh ? "关闭" : "开启") + "自动刷新。"));
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

    private void addCoverageActions(List<AIActionVo> actions, String projectId, List<AppVo> apps, String questionText) {
        String url = buildCoverageUrl(projectId, apps, questionText);
        actions.add(new AIActionVo("navigate", "打开覆盖率报告", "跳转到当前项目的覆盖率报告页面", url, false, null));
        actions.add(new AIActionVo("link", "查看覆盖率入口", "在快捷入口中保留覆盖率报告链接", url, false, null));
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
                return "/p/" + projectId + "/apps/" + encodeQueryParam(targetApp.getId())
                        + "/coverage?versionNumber=" + encodeQueryParam(targetApp.getCurrentVersion());
            }
        }
        return "/p/" + projectId + "/coverage";
    }

    private String buildCoverageOverviewUrl(String projectId, String appId, CoverageReportIndex report) {
        String targetAppId = StringUtils.hasText(report.getAppId()) ? report.getAppId() : appId;
        StringBuilder url = new StringBuilder("/p/").append(projectId).append("/apps/")
                .append(encodeQueryParam(targetAppId)).append("/coverage?versionNumber=");
        if (StringUtils.hasText(report.getVersionNumber())) {
            url.append(encodeQueryParam(report.getVersionNumber()));
        }
        url.append("&reportId=").append(encodeQueryParam(report.getId()));
        if (StringUtils.hasText(report.getRepoCommitId())) {
            url.append("&commitId=").append(encodeQueryParam(report.getRepoCommitId()));
        }
        return url.toString();
    }

    private String buildSystemSnapshotUrl(String projectId, List<AppVo> apps, String questionText) {
        AppVo targetApp = findTargetApp(apps, questionText);
        if (targetApp == null && apps != null && !apps.isEmpty()) {
            targetApp = apps.get(0);
        }
        if (targetApp != null && StringUtils.hasText(targetApp.getId())) {
            return "/p/" + projectId + "/apps/" + targetApp.getId() + "/snapshots";
        }
        return "/p/" + projectId + "/apps";
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
        List<CoverageReportIndex> reports = coverageReportCommandService.getReportsByAppId(appId);
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
            actions.add(buildAutoNavigateAction("打开搜索", "/p/" + projectId + "/search"));
        } else if (containsAny(questionText, "监控台", "实时监控", "监控页")) {
            actions.add(buildAutoNavigateAction("打开实时监控", "/p/" + projectId + "/monitor"));
        } else if (containsAny(questionText, "ai interactive", "ai工作台", "ai interactive", "工作台")) {
            actions.add(buildAutoNavigateAction("打开 AI Interactive", "/p/" + projectId + "/ai"));
        } else if (containsAny(questionText, "应用中心", "应用列表")) {
            actions.add(buildAutoNavigateAction("打开应用中心", "/p/" + projectId + "/apps"));
        } else if (containsAny(questionText, "在线应用")) {
            actions.add(buildAutoNavigateAction("打开在线应用", "/p/" + projectId + "/apps/online"));
        } else if (containsAny(questionText, "添加应用", "新增应用", "创建应用")) {
            actions.add(buildAutoNavigateAction("打开添加应用", "/p/" + projectId + "/apps?create=1"));
        } else if (containsAny(questionText, "创建新项目", "新建项目", "创建项目")) {
            actions.add(buildAutoNavigateAction("打开创建新项目", "/projects?create=1"));
        } else if (containsAny(questionText, "项目设置", "设置")) {
            actions.add(buildAutoNavigateAction("打开项目设置", "/projects?edit=" + projectId));
        } else if (containsAny(questionText, "我的项目", "项目列表", "切换项目")) {
            actions.add(buildAutoNavigateAction("打开我的项目列表", "/projects"));
        } else if (containsAny(questionText, "用户设置", "个人设置", "账号设置")) {
            actions.add(buildAutoNavigateAction("打开用户设置", "/account"));
        } else if (containsAny(questionText, "注销", "退出登录", "登出")) {
            actions.add(new AIActionVo("logout", "注销退出", "通过新前端退出当前账号", "/api/auth/logout", true, "确认要注销退出吗？"));
        } else if (containsAny(questionText, "我的快照")) {
            actions.add(buildAutoNavigateAction("打开我的快照", "/p/" + projectId + "/my-snapshots"));
        } else if (appId != null && containsAny(questionText, "系统快照")) {
            actions.add(buildAutoNavigateAction("打开系统快照", "/p/" + projectId + "/apps/" + appId + "/snapshots"));
        } else if (appId != null && containsAny(questionText, "版本比对", "版本比较")) {
            actions.add(buildAutoNavigateAction("打开版本比对", "/p/" + projectId + "/apps/" + appId + "/compare"));
        } else if (appId != null && containsAny(questionText, "覆盖率报告", "覆盖率")) {
            actions.add(buildAutoNavigateAction("打开覆盖率报告", buildCoverageUrl(projectId, apps, questionText)));
        } else if (appId != null) {
            actions.add(buildAutoNavigateAction("打开应用系统快照", "/p/" + projectId + "/apps/" + appId + "/snapshots"));
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
        String normalizedQuestion = questionText == null ? "" : questionText.toLowerCase();
        for (AppVo app : apps) {
            if (app != null && StringUtils.hasText(app.getName()) && normalizedQuestion.contains(app.getName().toLowerCase())) {
                return app;
            }
        }
        if (containsAny(normalizedQuestion, "应用", "系统快照", "版本比对", "覆盖率")) {
            return apps.get(0);
        }
        return null;
    }

    private boolean isRoute(RouteContext routeContext, String topicKey, String... routeKeys) {
        return routeService.isRoute(routeContext, topicKey, routeKeys);
    }

    private boolean containsAny(String text, String... keywords) {
        return routeService.containsAny(text, keywords);
    }
}
