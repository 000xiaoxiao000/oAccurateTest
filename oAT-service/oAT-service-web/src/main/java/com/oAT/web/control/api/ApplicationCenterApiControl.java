package com.oAT.web.control.api;

import com.oAT.web.api.common.ApiSummaries.*;
import com.oAT.web.api.app.ApplicationCenterApiPayloads.*;
import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.collector.CollectorSource;
import com.oAT.web.collector.CollectorSourceService;
import com.oAT.web.common.DateUtil;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.GitService;
import com.oAT.web.service.ProbeAlertDashboardService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProbeAlertDashboardVo;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ApplicationCenterApiControl {

    private final AppService appService;
    private final ProjectService projectService;
    private final ClientSessionService clientSessionService;
    private final GitService gitService;
    private final ProbeAlertDashboardService probeAlertDashboardService;
    private final CollectorSourceService collectorSourceService;

    public ApplicationCenterApiControl(AppService appService,
                                       ProjectService projectService,
                                       ClientSessionService clientSessionService,
                                       GitService gitService,
                                       ProbeAlertDashboardService probeAlertDashboardService,
                                       CollectorSourceService collectorSourceService) {
        this.appService = appService;
        this.projectService = projectService;
        this.clientSessionService = clientSessionService;
        this.gitService = gitService;
        this.probeAlertDashboardService = probeAlertDashboardService;
        this.collectorSourceService = collectorSourceService;
    }

    @GetMapping("/collector-sources")
    public ResultNotified<CollectorSourcesPayload> collectorSources(@PathVariable String projectId,
                                                                    @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        List<CollectorSource> sources = collectorSourceService.listProjectSources(projectId);
        CollectorSourcesPayload payload = new CollectorSourcesPayload();
        payload.setSources(sources);
        payload.setTotal(sources.size());
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取采集源健康度成功", payload);
    }

    @GetMapping("/online-sessions")
    public ResultNotified<OnlineSessionsPayload> onlineSessions(@PathVariable String projectId,
                                                                @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        List<ClientSessionVo> sessions = deduplicateSessions(getProjectOnlineSessions(projectId));
        List<OnlineSessionSummary> items = new ArrayList<>();
        for (ClientSessionVo session : sessions) {
            OnlineSessionSummary item = new OnlineSessionSummary();
            item.setSessionId(session.getSessionId());
            item.setAddressIp(session.getClientInfo().getAddressIp());
            item.setAgentVersion(session.getClientInfo().getAgentVersion());
            item.setSystemDir(session.getClientInfo().getSystemDir());
            item.setPid(session.getClientInfo().getPid());
            item.setJvmVersion(session.getClientInfo().getJvmVersion());
            item.setJvmOption(session.getClientInfo().getJvmOption());
            item.setOnlineTime(session.getOnlineTime());
            item.setSandboxStatus(session.getSandboxStatus());
            item.setAppId(session.getClientInfo().getAppKey());
            item.setAppName(session.getApplication() == null ? "未定义" : session.getApplication().getAppName());
            item.setProjectSrcName(session.getApplication() == null ? "" : session.getApplication().getProjectSrcName());
            items.add(item);
        }
        OnlineSessionsPayload payload = new OnlineSessionsPayload();
        payload.setSessions(items);
        payload.setTotal(items.size());
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取在线应用成功", payload);
    }

    @PostMapping("/online-sessions/{sessionId}/sandbox-command")
    public ResultNotified<String> sandboxCommand(@PathVariable String projectId,
                                                 @PathVariable String sessionId,
                                                 @SessionAttribute UserVo user,
                                                 @RequestBody SandboxCommandRequest request) {
        ensureProjectAccess(projectId, user);
        Assert.notNull(request, "请求不能为空");
        Assert.hasText(request.getCommand(), "command 不能为空");
        String command = request.getCommand().trim();
        Assert.isTrue("start".equals(command) || "stop".equals(command)
                || "restart".equals(command) || "status".equals(command), "不支持的 sandbox 命令");
        boolean belongsToProject = getProjectOnlineSessions(projectId).stream()
                .anyMatch(session -> sessionId.equals(session.getSessionId()));
        Assert.isTrue(belongsToProject, "在线会话不存在或不属于当前项目");
        clientSessionService.putSandboxCommand(sessionId, command);
        return new ResultNotified<>(true, "Sandbox 命令已下发", command);
    }

    @GetMapping("/apps/{appId}/settings")
    public ResultNotified<AppSettingsPayload> appSettings(@PathVariable String projectId,
                                                          @PathVariable String appId,
                                                          @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        AppSettingsPayload payload = new AppSettingsPayload();
        payload.setApp(toAppSettingsSummary(app));
        payload.setApps(toAppSummaries(appService.getAppList(projectId)));
        payload.setProbeAlertDashboard(probeAlertDashboardService.getDashboard(appId, 10));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取应用设置成功", payload);
    }

    @GetMapping("/apps/{appId}/probe-alerts")
    public ResultNotified<ProbeAlertsPayload> probeAlerts(@PathVariable String projectId,
                                                          @PathVariable String appId,
                                                          @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        ProbeAlertsPayload payload = new ProbeAlertsPayload();
        payload.setApp(toAppSummary(app));
        payload.setApps(toAppSummaries(appService.getAppList(projectId)));
        payload.setProbeAlertDashboard(probeAlertDashboardService.getDashboard(appId));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取探针告警成功", payload);
    }

    @PostMapping("/apps/{appId}/settings")
    public ResultNotified<AppSettingsPayload> saveAppSettings(@PathVariable String projectId,
                                                              @PathVariable String appId,
                                                              @SessionAttribute UserVo user,
                                                              @RequestBody SaveAppSettingsRequest request) {
        ensureProjectAccess(projectId, user);
        AppVo existingApp = appService.getApp(appId);
        Assert.notNull(existingApp, "应用不存在");
        Assert.isTrue(appId.equals(existingApp.getId()), "参数非法");

        existingApp.setName(request.getName());
        existingApp.setSrcName(request.getSrcName());
        existingApp.setLanguage(request.getLanguage());
        existingApp.setLanguageConfig(request.getLanguageConfig());
        existingApp.setRange(request.getRange());
        existingApp.setDescribe(request.getDescribe());
        existingApp.setProperties(request.getProperties());
        existingApp.setCurrentVersion(request.getCurrentVersion());
        existingApp.setCurrentBranch(request.getCurrentBranch());
        existingApp.setCurrentCommitId(request.getCurrentCommitId());

        boolean probeAlertEnabled = Boolean.TRUE.equals(request.getProbeAlertEnabled());
        boolean probeAlertOnOffline = Boolean.TRUE.equals(request.getProbeAlertOnOffline());
        boolean probeAlertOnRecovered = Boolean.TRUE.equals(request.getProbeAlertOnRecovered());
        boolean probeAlertOnOnline = Boolean.TRUE.equals(request.getProbeAlertOnOnline());
        if (probeAlertEnabled && !probeAlertOnOffline && !probeAlertOnRecovered && !probeAlertOnOnline) {
            probeAlertOnOffline = true;
            probeAlertOnRecovered = true;
        }

        if (probeAlertEnabled) {
            Assert.isTrue(StringUtils.hasText(request.getProbeWebhookUrl()), "启用告警时必须填写 Webhook 地址");
        }

        existingApp.setProbeAlertEnabled(probeAlertEnabled);
        existingApp.setProbeAlertOnOffline(probeAlertOnOffline);
        existingApp.setProbeAlertOnRecovered(probeAlertOnRecovered);
        existingApp.setProbeAlertOnOnline(probeAlertOnOnline);
        existingApp.setProbeWebhookUrl(request.getProbeWebhookUrl());

        Integer threshold = request.getProbeOfflineThresholdSeconds();
        if (threshold != null && threshold > 0 && threshold < 30) {
            threshold = 30;
        }
        existingApp.setProbeOfflineThresholdSeconds(threshold);

        appService.updateApp(projectId, existingApp);
        return appSettings(projectId, appId, user);
    }

    @GetMapping("/apps/{appId}/repository")
    public ResultNotified<RepositoryConfigPayload> repository(@PathVariable String projectId,
                                                              @PathVariable String appId,
                                                              @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        RepositoryConfigPayload payload = new RepositoryConfigPayload();
        payload.setApp(toRepositorySummary(app));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取仓库配置成功", payload);
    }

    @PostMapping("/apps/{appId}/repository")
    public ResultNotified<RepositoryConfigPayload> saveRepository(@PathVariable String projectId,
                                                                  @PathVariable String appId,
                                                                  @SessionAttribute UserVo user,
                                                                  @RequestBody SaveRepositoryRequest request) {
        ensureProjectAccess(projectId, user);
        AppVo existingApp = appService.getApp(appId);
        Assert.notNull(existingApp, "应用不存在");
        existingApp.setRepoAddress(request.getRepoAddress());
        existingApp.setRepoUserName(request.getRepoUserName());
        existingApp.setRepoPassword(request.getRepoPassword());
        appService.updateApp(projectId, existingApp);
        return repository(projectId, appId, user);
    }

    @GetMapping("/apps/{appId}/repository/branches")
    public ResultNotified<List<String>> repositoryBranches(@PathVariable String projectId,
                                                           @PathVariable String appId,
                                                           @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        try {
            AppVo app = appService.getApp(appId);
            Assert.notNull(app, "应用不存在");
            Assert.isTrue(StringUtils.hasText(app.getRepoAddress()), "Git仓库地址未配置");
            List<String> branches = gitService.getRemoteBranches(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword());
            return new ResultNotified<>(true, "获取分支成功", branches);
        } catch (Exception e) {
            return new ResultNotified<>(false, e.getMessage(), Collections.emptyList());
        }
    }

    @GetMapping("/repository/branches")
    public ResultNotified<List<String>> repositoryBranchesPreview(@PathVariable String projectId,
                                                                  @SessionAttribute UserVo user,
                                                                  @RequestParam String repoUrl,
                                                                  @RequestParam(required = false) String username,
                                                                  @RequestParam(required = false) String password) {
        ensureProjectAccess(projectId, user);
        try {
            Assert.isTrue(StringUtils.hasText(repoUrl), "Git仓库地址不能为空");
            List<String> branches = gitService.getRemoteBranches(repoUrl, username, password);
            return new ResultNotified<>(true, "获取分支成功", branches);
        } catch (Exception e) {
            return new ResultNotified<>(false, e.getMessage(), Collections.emptyList());
        }
    }

    private ProjectVo ensureProjectAccess(String projectId, UserVo user) {
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        Assert.notNull(project, "找不到指定项目,或者您没有该项目的访问权限");
        return project;
    }

    private String resolveUserRole(String projectId, UserVo user) {
        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);
        for (ProjectMemberVo member : members) {
            if (user.getName() != null && user.getName().equals(member.getMemberName()) && member.getRole() != null) {
                return member.getRole().name();
            }
        }
        return ProjectMemberVo.Role.visitor.name();
    }

    private List<ClientSessionVo> getProjectOnlineSessions(String projectId) {
        List<ClientSessionVo> list = new ArrayList<>();
        List<AppVo> apps = appService.getAppList(projectId);
        List<String> appIds = new ArrayList<>();
        for (AppVo appVo : apps) {
            appIds.add(appVo.getId());
        }
        for (ClientSessionVo onlineSession : clientSessionService.getOnlineSessions()) {
            if (!StringUtils.hasText(onlineSession.getClientInfo().getAppKey())
                    || appIds.contains(onlineSession.getClientInfo().getAppKey())) {
                onlineSession.setOnlineTime(DateUtil.timeDifference(onlineSession.getLoginTime(), new Date()));
                list.add(onlineSession);
            }
        }
        return list;
    }

    private List<ClientSessionVo> deduplicateSessions(List<ClientSessionVo> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, ClientSessionVo> latest = new HashMap<>();
        for (ClientSessionVo session : sessions) {
            if (session == null || session.getClientInfo() == null) {
                continue;
            }
            String key = String.join("|",
                    firstText(session.getClientInfo().getAppKey()),
                    firstText(session.getClientInfo().getPid()),
                    firstText(session.getClientInfo().getSystemDir()));
            ClientSessionVo existing = latest.get(key);
            if (existing == null || safeTime(session.getLastHeartbeatTime()) >= safeTime(existing.getLastHeartbeatTime())) {
                latest.put(key, session);
            }
        }
        return new ArrayList<>(latest.values());
    }

    private static long safeTime(Long time) {
        return time == null ? 0L : time;
    }

    private static String firstText(String value) {
        return value == null ? "" : value;
    }

    private List<AppSummary> toAppSummaries(List<AppVo> apps) {
        List<AppSummary> result = new ArrayList<>();
        for (AppVo app : apps) {
            app.setOnlineCount(clientSessionService.getOnlineSessionsByAppId(app.getId()).size());
            AppSummary summary = new AppSummary();
            summary.setId(app.getId());
            summary.setName(app.getName());
            summary.setSrcName(app.getSrcName());
            summary.setLanguage(app.getLanguage());
            summary.setLanguageConfig(app.getLanguageConfig());
            summary.setDescribe(app.getDescribe());
            summary.setRange(app.getRange());
            summary.setOnlineCount(app.getOnlineCount());
            summary.setCurrentVersion(app.getCurrentVersion());
            summary.setCurrentBranch(app.getCurrentBranch());
            summary.setCurrentCommitId(app.getCurrentCommitId());
            summary.setRepoConfigured(StringUtils.hasText(app.getRepoAddress()));
            summary.setProbeAlertEnabled(Boolean.TRUE.equals(app.getProbeAlertEnabled()));
            summary.setSourceType(app.getLanguage());
            result.add(summary);
        }
        return result;
    }

    private AppSettingsSummary toAppSettingsSummary(AppVo app) {
        AppSettingsSummary summary = new AppSettingsSummary();
        summary.setId(app.getId());
        summary.setName(app.getName());
        summary.setSrcName(app.getSrcName());
        summary.setLanguage(app.getLanguage());
        summary.setLanguageConfig(app.getLanguageConfig());
        summary.setRange(app.getRange());
        summary.setDescribe(app.getDescribe());
        summary.setProperties(app.getProperties());
        summary.setCurrentVersion(app.getCurrentVersion());
        summary.setCurrentBranch(app.getCurrentBranch());
        summary.setCurrentCommitId(app.getCurrentCommitId());
        summary.setProbeAlertEnabled(Boolean.TRUE.equals(app.getProbeAlertEnabled()));
        summary.setProbeOfflineThresholdSeconds(app.getProbeOfflineThresholdSeconds());
        summary.setProbeWebhookUrl(app.getProbeWebhookUrl());
        summary.setProbeAlertOnOffline(Boolean.TRUE.equals(app.getProbeAlertOnOffline()));
        summary.setProbeAlertOnRecovered(Boolean.TRUE.equals(app.getProbeAlertOnRecovered()));
        summary.setProbeAlertOnOnline(Boolean.TRUE.equals(app.getProbeAlertOnOnline()));
        return summary;
    }

    private AppSummary toAppSummary(AppVo app) {
        app.setOnlineCount(clientSessionService.getOnlineSessionsByAppId(app.getId()).size());
        AppSummary summary = new AppSummary();
        summary.setId(app.getId());
        summary.setName(app.getName());
        summary.setSrcName(app.getSrcName());
        summary.setLanguage(app.getLanguage());
        summary.setLanguageConfig(app.getLanguageConfig());
        summary.setDescribe(app.getDescribe());
        summary.setRange(app.getRange());
        summary.setOnlineCount(app.getOnlineCount());
        summary.setCurrentVersion(app.getCurrentVersion());
        summary.setCurrentBranch(app.getCurrentBranch());
        summary.setCurrentCommitId(app.getCurrentCommitId());
        summary.setRepoConfigured(StringUtils.hasText(app.getRepoAddress()));
        summary.setProbeAlertEnabled(Boolean.TRUE.equals(app.getProbeAlertEnabled()));
        summary.setSourceType(app.getLanguage());
        return summary;
    }

    private RepositorySummary toRepositorySummary(AppVo app) {
        RepositorySummary summary = new RepositorySummary();
        summary.setAppId(app.getId());
        summary.setAppName(app.getName());
        summary.setRepoAddress(app.getRepoAddress());
        summary.setRepoUserName(app.getRepoUserName());
        summary.setRepoPassword(app.getRepoPassword());
        summary.setConfigured(StringUtils.hasText(app.getRepoAddress()));
        return summary;
    }

}
