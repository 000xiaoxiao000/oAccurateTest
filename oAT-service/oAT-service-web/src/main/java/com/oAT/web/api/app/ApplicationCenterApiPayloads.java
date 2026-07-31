package com.oAT.web.api.app;

import com.oAT.web.api.common.ApiSummaries.*;
import com.oAT.web.collector.CollectorSource;
import com.oAT.web.service.entity.ProbeAlertDashboardVo;

import java.util.List;

public final class ApplicationCenterApiPayloads {

    private ApplicationCenterApiPayloads() {
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

    public static class CollectorSourcesPayload {
        private List<CollectorSource> sources;
        private int total;
        private String currentUserRole;

        public List<CollectorSource> getSources() { return sources; }
        public void setSources(List<CollectorSource> sources) { this.sources = sources; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class OnlineSessionSummary {
        private String sessionId;
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
        private String sandboxStatus;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
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
        public String getSandboxStatus() { return sandboxStatus; }
        public void setSandboxStatus(String sandboxStatus) { this.sandboxStatus = sandboxStatus; }
    }

    public static class SandboxCommandRequest {
        private String command;

        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
    }

    public static class AppSettingsPayload {
        private AppSettingsSummary app;
        private List<AppSummary> apps;
        private ProbeAlertDashboardVo probeAlertDashboard;
        private String currentUserRole;

        public AppSettingsSummary getApp() { return app; }
        public void setApp(AppSettingsSummary app) { this.app = app; }
        public List<AppSummary> getApps() { return apps; }
        public void setApps(List<AppSummary> apps) { this.apps = apps; }
        public ProbeAlertDashboardVo getProbeAlertDashboard() { return probeAlertDashboard; }
        public void setProbeAlertDashboard(ProbeAlertDashboardVo probeAlertDashboard) { this.probeAlertDashboard = probeAlertDashboard; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class ProbeAlertsPayload {
        private AppSummary app;
        private List<AppSummary> apps;
        private ProbeAlertDashboardVo probeAlertDashboard;
        private String currentUserRole;

        public AppSummary getApp() { return app; }
        public void setApp(AppSummary app) { this.app = app; }
        public List<AppSummary> getApps() { return apps; }
        public void setApps(List<AppSummary> apps) { this.apps = apps; }
        public ProbeAlertDashboardVo getProbeAlertDashboard() { return probeAlertDashboard; }
        public void setProbeAlertDashboard(ProbeAlertDashboardVo probeAlertDashboard) { this.probeAlertDashboard = probeAlertDashboard; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class AppSettingsSummary {
        private String id;
        private String name;
        private String srcName;
        private String language;
        private String languageConfig;
        private String range;
        private String describe;
        private String properties;
        private String currentVersion;
        private String currentBranch;
        private String currentCommitId;
        private boolean probeAlertEnabled;
        private Integer probeOfflineThresholdSeconds;
        private boolean probeAlertOnOnline;
        private boolean probeAlertOnOffline;
        private boolean probeAlertOnRecovered;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSrcName() { return srcName; }
        public void setSrcName(String srcName) { this.srcName = srcName; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getLanguageConfig() { return languageConfig; }
        public void setLanguageConfig(String languageConfig) { this.languageConfig = languageConfig; }
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
        private String language;
        private String languageConfig;
        private String range;
        private String describe;
        private String properties;
        private String currentVersion;
        private String currentBranch;
        private String currentCommitId;
        private Boolean probeAlertEnabled;
        private Integer probeOfflineThresholdSeconds;
        private Boolean probeAlertOnOnline;
        private Boolean probeAlertOnOffline;
        private Boolean probeAlertOnRecovered;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSrcName() { return srcName; }
        public void setSrcName(String srcName) { this.srcName = srcName; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getLanguageConfig() { return languageConfig; }
        public void setLanguageConfig(String languageConfig) { this.languageConfig = languageConfig; }
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
