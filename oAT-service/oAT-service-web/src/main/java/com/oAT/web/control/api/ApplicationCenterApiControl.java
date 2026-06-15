package com.oAT.web.control.api;

import com.oAT.server.model.ClientSessionVo;
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

    public ApplicationCenterApiControl(AppService appService,
                                       ProjectService projectService,
                                       ClientSessionService clientSessionService,
                                       GitService gitService,
                                       ProbeAlertDashboardService probeAlertDashboardService) {
        this.appService = appService;
        this.projectService = projectService;
        this.clientSessionService = clientSessionService;
        this.gitService = gitService;
        this.probeAlertDashboardService = probeAlertDashboardService;
    }

    @GetMapping("/online-sessions")
    public ResultNotified<OnlineSessionsPayload> onlineSessions(@PathVariable String projectId,
                                                                @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        List<ClientSessionVo> sessions = getProjectOnlineSessions(projectId);
        List<OnlineSessionSummary> items = new ArrayList<>();
        for (ClientSessionVo session : sessions) {
            OnlineSessionSummary item = new OnlineSessionSummary();
            item.setAddressIp(session.getClientInfo().getAddressIp());
            item.setAgentVersion(session.getClientInfo().getAgentVersion());
            item.setSystemDir(session.getClientInfo().getSystemDir());
            item.setPid(session.getClientInfo().getPid());
            item.setJvmVersion(session.getClientInfo().getJvmVersion());
            item.setJvmOption(session.getClientInfo().getJvmOption());
            item.setOnlineTime(session.getOnlineTime());
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

    private List<FrontendContextApiControl.AppSummary> toAppSummaries(List<AppVo> apps) {
        List<FrontendContextApiControl.AppSummary> result = new ArrayList<>();
        for (AppVo app : apps) {
            app.setOnlineCount(clientSessionService.getOnlineSessionsByAppId(app.getId()).size());
            FrontendContextApiControl.AppSummary summary = new FrontendContextApiControl.AppSummary();
            summary.setId(app.getId());
            summary.setName(app.getName());
            summary.setSrcName(app.getSrcName());
            summary.setDescribe(app.getDescribe());
            summary.setRange(app.getRange());
            summary.setOnlineCount(app.getOnlineCount());
            summary.setCurrentVersion(app.getCurrentVersion());
            summary.setCurrentBranch(app.getCurrentBranch());
            summary.setCurrentCommitId(app.getCurrentCommitId());
            summary.setRepoConfigured(StringUtils.hasText(app.getRepoAddress()));
            summary.setProbeAlertEnabled(Boolean.TRUE.equals(app.getProbeAlertEnabled()));
            summary.setSourceType("JAVA");
            result.add(summary);
        }
        return result;
    }

    private AppSettingsSummary toAppSettingsSummary(AppVo app) {
        AppSettingsSummary summary = new AppSettingsSummary();
        summary.setId(app.getId());
        summary.setName(app.getName());
        summary.setSrcName(app.getSrcName());
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

    private FrontendContextApiControl.AppSummary toAppSummary(AppVo app) {
        app.setOnlineCount(clientSessionService.getOnlineSessionsByAppId(app.getId()).size());
        FrontendContextApiControl.AppSummary summary = new FrontendContextApiControl.AppSummary();
        summary.setId(app.getId());
        summary.setName(app.getName());
        summary.setSrcName(app.getSrcName());
        summary.setDescribe(app.getDescribe());
        summary.setRange(app.getRange());
        summary.setOnlineCount(app.getOnlineCount());
        summary.setCurrentVersion(app.getCurrentVersion());
        summary.setCurrentBranch(app.getCurrentBranch());
        summary.setCurrentCommitId(app.getCurrentCommitId());
        summary.setRepoConfigured(StringUtils.hasText(app.getRepoAddress()));
        summary.setProbeAlertEnabled(Boolean.TRUE.equals(app.getProbeAlertEnabled()));
        summary.setSourceType("JAVA");
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

    public static class OnlineSessionsPayload {
        private List<OnlineSessionSummary> sessions;
        private int total;
        private String currentUserRole;

        public List<OnlineSessionSummary> getSessions() { return sessions; }
        public void setSessions(List<OnlineSessionSummary> sessions) { this.sessions = sessions; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class OnlineSessionSummary {
        private String appId;
        private String addressIp;
        private String agentVersion;
        private String systemDir;
        private String pid;
        private String jvmVersion;
        private String jvmOption;
        private String onlineTime;
        private String appName;
        private String projectSrcName;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getAddressIp() { return addressIp; }
        public void setAddressIp(String addressIp) { this.addressIp = addressIp; }
        public String getAgentVersion() { return agentVersion; }
        public void setAgentVersion(String agentVersion) { this.agentVersion = agentVersion; }
        public String getSystemDir() { return systemDir; }
        public void setSystemDir(String systemDir) { this.systemDir = systemDir; }
        public String getPid() { return pid; }
        public void setPid(String pid) { this.pid = pid; }
        public String getJvmVersion() { return jvmVersion; }
        public void setJvmVersion(String jvmVersion) { this.jvmVersion = jvmVersion; }
        public String getJvmOption() { return jvmOption; }
        public void setJvmOption(String jvmOption) { this.jvmOption = jvmOption; }
        public String getOnlineTime() { return onlineTime; }
        public void setOnlineTime(String onlineTime) { this.onlineTime = onlineTime; }
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getProjectSrcName() { return projectSrcName; }
        public void setProjectSrcName(String projectSrcName) { this.projectSrcName = projectSrcName; }
    }

    public static class AppSettingsPayload {
        private AppSettingsSummary app;
        private List<FrontendContextApiControl.AppSummary> apps;
        private ProbeAlertDashboardVo probeAlertDashboard;
        private String currentUserRole;

        public AppSettingsSummary getApp() { return app; }
        public void setApp(AppSettingsSummary app) { this.app = app; }
        public List<FrontendContextApiControl.AppSummary> getApps() { return apps; }
        public void setApps(List<FrontendContextApiControl.AppSummary> apps) { this.apps = apps; }
        public ProbeAlertDashboardVo getProbeAlertDashboard() { return probeAlertDashboard; }
        public void setProbeAlertDashboard(ProbeAlertDashboardVo probeAlertDashboard) { this.probeAlertDashboard = probeAlertDashboard; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class ProbeAlertsPayload {
        private FrontendContextApiControl.AppSummary app;
        private List<FrontendContextApiControl.AppSummary> apps;
        private ProbeAlertDashboardVo probeAlertDashboard;
        private String currentUserRole;

        public FrontendContextApiControl.AppSummary getApp() { return app; }
        public void setApp(FrontendContextApiControl.AppSummary app) { this.app = app; }
        public List<FrontendContextApiControl.AppSummary> getApps() { return apps; }
        public void setApps(List<FrontendContextApiControl.AppSummary> apps) { this.apps = apps; }
        public ProbeAlertDashboardVo getProbeAlertDashboard() { return probeAlertDashboard; }
        public void setProbeAlertDashboard(ProbeAlertDashboardVo probeAlertDashboard) { this.probeAlertDashboard = probeAlertDashboard; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class AppSettingsSummary {
        private String id;
        private String name;
        private String srcName;
        private String range;
        private String describe;
        private String properties;
        private String currentVersion;
        private String currentBranch;
        private String currentCommitId;
        private boolean probeAlertEnabled;
        private Integer probeOfflineThresholdSeconds;
        private String probeWebhookUrl;
        private boolean probeAlertOnOnline;
        private boolean probeAlertOnOffline;
        private boolean probeAlertOnRecovered;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSrcName() { return srcName; }
        public void setSrcName(String srcName) { this.srcName = srcName; }
        public String getRange() { return range; }
        public void setRange(String range) { this.range = range; }
        public String getDescribe() { return describe; }
        public void setDescribe(String describe) { this.describe = describe; }
        public String getProperties() { return properties; }
        public void setProperties(String properties) { this.properties = properties; }
        public String getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }
        public String getCurrentBranch() { return currentBranch; }
        public void setCurrentBranch(String currentBranch) { this.currentBranch = currentBranch; }
        public String getCurrentCommitId() { return currentCommitId; }
        public void setCurrentCommitId(String currentCommitId) { this.currentCommitId = currentCommitId; }
        public boolean isProbeAlertEnabled() { return probeAlertEnabled; }
        public void setProbeAlertEnabled(boolean probeAlertEnabled) { this.probeAlertEnabled = probeAlertEnabled; }
        public Integer getProbeOfflineThresholdSeconds() { return probeOfflineThresholdSeconds; }
        public void setProbeOfflineThresholdSeconds(Integer probeOfflineThresholdSeconds) { this.probeOfflineThresholdSeconds = probeOfflineThresholdSeconds; }
        public String getProbeWebhookUrl() { return probeWebhookUrl; }
        public void setProbeWebhookUrl(String probeWebhookUrl) { this.probeWebhookUrl = probeWebhookUrl; }
        public boolean isProbeAlertOnOnline() { return probeAlertOnOnline; }
        public void setProbeAlertOnOnline(boolean probeAlertOnOnline) { this.probeAlertOnOnline = probeAlertOnOnline; }
        public boolean isProbeAlertOnOffline() { return probeAlertOnOffline; }
        public void setProbeAlertOnOffline(boolean probeAlertOnOffline) { this.probeAlertOnOffline = probeAlertOnOffline; }
        public boolean isProbeAlertOnRecovered() { return probeAlertOnRecovered; }
        public void setProbeAlertOnRecovered(boolean probeAlertOnRecovered) { this.probeAlertOnRecovered = probeAlertOnRecovered; }
    }

    public static class SaveAppSettingsRequest {
        private String name;
        private String srcName;
        private String range;
        private String describe;
        private String properties;
        private String currentVersion;
        private String currentBranch;
        private String currentCommitId;
        private Boolean probeAlertEnabled;
        private Integer probeOfflineThresholdSeconds;
        private String probeWebhookUrl;
        private Boolean probeAlertOnOnline;
        private Boolean probeAlertOnOffline;
        private Boolean probeAlertOnRecovered;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSrcName() { return srcName; }
        public void setSrcName(String srcName) { this.srcName = srcName; }
        public String getRange() { return range; }
        public void setRange(String range) { this.range = range; }
        public String getDescribe() { return describe; }
        public void setDescribe(String describe) { this.describe = describe; }
        public String getProperties() { return properties; }
        public void setProperties(String properties) { this.properties = properties; }
        public String getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }
        public String getCurrentBranch() { return currentBranch; }
        public void setCurrentBranch(String currentBranch) { this.currentBranch = currentBranch; }
        public String getCurrentCommitId() { return currentCommitId; }
        public void setCurrentCommitId(String currentCommitId) { this.currentCommitId = currentCommitId; }
        public Boolean getProbeAlertEnabled() { return probeAlertEnabled; }
        public void setProbeAlertEnabled(Boolean probeAlertEnabled) { this.probeAlertEnabled = probeAlertEnabled; }
        public Integer getProbeOfflineThresholdSeconds() { return probeOfflineThresholdSeconds; }
        public void setProbeOfflineThresholdSeconds(Integer probeOfflineThresholdSeconds) { this.probeOfflineThresholdSeconds = probeOfflineThresholdSeconds; }
        public String getProbeWebhookUrl() { return probeWebhookUrl; }
        public void setProbeWebhookUrl(String probeWebhookUrl) { this.probeWebhookUrl = probeWebhookUrl; }
        public Boolean getProbeAlertOnOnline() { return probeAlertOnOnline; }
        public void setProbeAlertOnOnline(Boolean probeAlertOnOnline) { this.probeAlertOnOnline = probeAlertOnOnline; }
        public Boolean getProbeAlertOnOffline() { return probeAlertOnOffline; }
        public void setProbeAlertOnOffline(Boolean probeAlertOnOffline) { this.probeAlertOnOffline = probeAlertOnOffline; }
        public Boolean getProbeAlertOnRecovered() { return probeAlertOnRecovered; }
        public void setProbeAlertOnRecovered(Boolean probeAlertOnRecovered) { this.probeAlertOnRecovered = probeAlertOnRecovered; }
    }

    public static class RepositoryConfigPayload {
        private RepositorySummary app;
        private String currentUserRole;

        public RepositorySummary getApp() { return app; }
        public void setApp(RepositorySummary app) { this.app = app; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class RepositorySummary {
        private String appId;
        private String appName;
        private String repoAddress;
        private String repoUserName;
        private String repoPassword;
        private boolean configured;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getRepoAddress() { return repoAddress; }
        public void setRepoAddress(String repoAddress) { this.repoAddress = repoAddress; }
        public String getRepoUserName() { return repoUserName; }
        public void setRepoUserName(String repoUserName) { this.repoUserName = repoUserName; }
        public String getRepoPassword() { return repoPassword; }
        public void setRepoPassword(String repoPassword) { this.repoPassword = repoPassword; }
        public boolean isConfigured() { return configured; }
        public void setConfigured(boolean configured) { this.configured = configured; }
    }

    public static class SaveRepositoryRequest {
        private String repoAddress;
        private String repoUserName;
        private String repoPassword;

        public String getRepoAddress() { return repoAddress; }
        public void setRepoAddress(String repoAddress) { this.repoAddress = repoAddress; }
        public String getRepoUserName() { return repoUserName; }
        public void setRepoUserName(String repoUserName) { this.repoUserName = repoUserName; }
        public String getRepoPassword() { return repoPassword; }
        public void setRepoPassword(String repoPassword) { this.repoPassword = repoPassword; }
    }
}
