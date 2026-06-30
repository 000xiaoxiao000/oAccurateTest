package com.oAT.web.service.impl;

import com.oAT.ai.agent.AgentDataProvider;
import com.oAT.ai.agent.cache.ToolCallCache;
import com.oAT.web.api.ai.AgentCoverageDataService;
import com.oAT.web.api.ai.AgentStaticSourceLookupService;
import com.oAT.web.api.ai.AgentTraceSnapshotDataService;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Agent 数据提供者实现
 * 为 AI Agent 提供项目数据查询能力
 */
@Service
public class AgentDataProviderImpl implements AgentDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(AgentDataProviderImpl.class);

    private final ToolCallCache cache = ToolCallCache.getInstance();

    @Autowired
    private ProjectService projectService;

    @Autowired
    private AppService appService;

    @Autowired
    private ClientSessionService clientSessionService;

    @Autowired
    private GitService gitService;

    @Autowired
    private AgentStaticSourceLookupService agentStaticSourceLookupService;

    @Autowired
    private AgentCoverageDataService agentCoverageDataService;

    @Autowired
    private AgentTraceSnapshotDataService agentTraceSnapshotDataService;

    @Override
    public Map<String, Object> getProjectInfo(String projectId) {
        // 尝试从缓存获取
        String cacheKey = "project:" + projectId;
        String cached = cache.get(cacheKey);
        if (cached != null) {
            logger.debug("Returning cached project info for: {}", projectId);
            // 这里简化处理，实际应该缓存Map对象
        }

        Map<String, Object> result = new HashMap<>();
        try {
            ProjectVo project = projectService.getProject(projectId);
            if (project != null) {
                result.put("id", project.getId());
                result.put("name", project.getName());
                result.put("describe", project.getDescribe());
                result.put("create", project.getCreate());
                result.put("memberCount", project.getMemberCount());
                result.put("createTime", project.getCreateTime());
                result.put("updateTime", project.getUpdateTime());

                // 缓存10分钟
                cache.put(cacheKey, "cached", 10 * 60 * 1000);
            }
        } catch (Exception e) {
            logger.error("Get project info failed: {}", projectId, e);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getApps(String projectId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<AppVo> apps = appService.getAppList(projectId);
            if (apps != null) {
                for (AppVo app : apps) {
                    Map<String, Object> appMap = convertAppToMap(app);
                    // 获取在线会话数
                    int onlineCount = clientSessionService.getOnlineSessionsByAppId(app.getId()).size();
                    appMap.put("online", onlineCount > 0);
                    appMap.put("onlineCount", onlineCount);
                    result.add(appMap);
                }
            }
        } catch (Exception e) {
            logger.error("Get apps failed: {}", projectId, e);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getOnlineApps(String projectId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<AppVo> apps = appService.getAppList(projectId);
            if (apps != null) {
                for (AppVo app : apps) {
                    int onlineCount = clientSessionService.getOnlineSessionsByAppId(app.getId()).size();
                    if (onlineCount > 0) {
                        Map<String, Object> appMap = convertAppToMap(app);
                        appMap.put("online", true);
                        appMap.put("onlineCount", onlineCount);
                        result.add(appMap);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Get online apps failed: {}", projectId, e);
        }
        return result;
    }

    @Override
    public Map<String, Object> getAppDetail(String appId) {
        Map<String, Object> result = new HashMap<>();
        try {
            AppVo app = appService.getApp(appId);
            if (app != null) {
                result = convertAppToMap(app);
                int onlineCount = clientSessionService.getOnlineSessionsByAppId(appId).size();
                result.put("online", onlineCount > 0);
                result.put("onlineCount", onlineCount);
            }
        } catch (Exception e) {
            logger.error("Get app detail failed: {}", appId, e);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getCoverageReports(String appId) {
        // 尝试从缓存获取
        String cacheKey = "coverage:reports:" + appId;
        String cached = cache.get(cacheKey);
        if (cached != null) {
            logger.debug("Returning cached coverage reports for: {}", appId);
            // 简化处理
        }

        List<Map<String, Object>> result = agentCoverageDataService.getCoverageReports(appId);
        cache.put(cacheKey, "cached", 3 * 60 * 1000);
        return result;
    }

    @Override
    public Map<String, Object> getCoverageReportDetail(String reportId) {
        return agentCoverageDataService.getCoverageReportDetail(reportId);
    }

    @Override
    public List<Map<String, Object>> getCoverageTrend(String appId, int limit) {
        return agentCoverageDataService.getCoverageTrend(appId, limit);
    }

    @Override
    public List<Map<String, Object>> getClassCoverageList(String reportId, Double minRate, Double maxRate) {
        return agentCoverageDataService.getClassCoverageList(reportId, minRate, maxRate);
    }

    @Override
    public List<Map<String, Object>> getTraceList(String projectId, String appId, int limit) {
        return agentTraceSnapshotDataService.getTraceList(projectId, appId, limit);
    }

    @Override
    public Map<String, Object> getTraceDetail(String traceId) {
        return agentTraceSnapshotDataService.getTraceDetail(traceId);
    }

    @Override
    public List<Map<String, Object>> getSnapshots(String projectId, String userId) {
        return agentTraceSnapshotDataService.getSnapshots(projectId, userId);
    }

    @Override
    public Map<String, Object> getSnapshotDetail(String snapshotId) {
        return agentTraceSnapshotDataService.getSnapshotDetail(snapshotId);
    }

    @Override
    public Map<String, Object> searchCodeRelation(String projectId, String keyword) {
        return agentStaticSourceLookupService.searchCodeRelation(projectId, keyword);
    }

    @Override
    public Map<String, Object> getCallGraph(String className, String methodName) {
        return agentStaticSourceLookupService.getCallGraph(className, methodName);
    }

    @Override
    public Map<String, Object> getProjectStatistics(String projectId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<AppVo> apps = appService.getAppList(projectId);
            int appCount = apps != null ? apps.size() : 0;
            int onlineAppCount = 0;
            if (apps != null) {
                for (AppVo app : apps) {
                    int onlineCount = clientSessionService.getOnlineSessionsByAppId(app.getId()).size();
                    if (onlineCount > 0) {
                        onlineAppCount++;
                    }
                }
            }
            result.put("appCount", appCount);
            result.put("onlineAppCount", onlineAppCount);

            result.put("snapshotCount", agentTraceSnapshotDataService.countSnapshots(projectId));

            result.put("traceCount", 0);
        } catch (Exception e) {
            logger.error("Get project statistics failed: {}", projectId, e);
        }
        return result;
    }

    @Override
    public String getSourceCode(String className) {
        if (className == null || className.trim().isEmpty()) {
            return null;
        }
        try {
            return agentStaticSourceLookupService.findSourceCode(className);
        } catch (Exception e) {
            logger.error("Get source code failed: className={}", className, e);
            return null;
        }
    }

    @Override
    public Map<String, String> getSourceCodes(List<String> classNames) {
        Map<String, String> result = new LinkedHashMap<>();
        if (classNames == null || classNames.isEmpty()) {
            return result;
        }
        for (String className : classNames) {
            if (className != null && !className.trim().isEmpty()) {
                String code = getSourceCode(className.trim());
                if (code != null) {
                    result.put(className.trim(), code);
                }
            }
        }
        return result;
    }

    @Override
    public String startCoverageGenerationJob(String appId, String versionNumber, String branch, String commitId) {
        return agentCoverageDataService.startCoverageGenerationJob(appId, versionNumber, branch, commitId);
    }

    @Override
    public Map<String, Object> getJobStatus(String jobId) {
        return agentCoverageDataService.getJobStatus(jobId);
    }

    @Override
    public String generateReportDownloadUrl(String reportId) {
        return agentCoverageDataService.generateReportDownloadUrl(reportId);
    }

    @Override
    public boolean validateGitAccess(String repoUrl, String username, String password, String branch) {
        try {
            gitService.checkGitPull(repoUrl, username, password, branch, null);
            return true;
        } catch (Exception e) {
            logger.warn("Git access validation failed: repoUrl={}, branch={}, error={}",
                    repoUrl, branch, e.getMessage());
            return false;
        }
    }

    private Map<String, Object> convertAppToMap(AppVo app) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", app.getId());
        map.put("name", app.getName());
        map.put("describe", app.getDescribe());
        map.put("srcName", app.getSrcName());
        map.put("createTime", app.getCreateTime());
        map.put("currentVersion", app.getCurrentVersion());
        map.put("currentBranch", app.getCurrentBranch());
        map.put("currentCommitId", app.getCurrentCommitId());
        // Git 仓库配置（供覆盖率工作流工具使用）
        map.put("repoUrl", app.getRepoAddress());
        map.put("gitUsername", app.getRepoUserName());
        map.put("gitPassword", app.getRepoPassword());
        return map;
    }

}
