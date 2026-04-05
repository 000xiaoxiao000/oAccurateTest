package com.oAT.web.service.impl;

import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.ai.agent.AgentDataProvider;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.SnapshotVo;
import com.oAT.web.service.entity.TraceItemVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Agent 数据提供者实现
 * 为 AI Agent 提供项目数据查询能力
 */
@Service
public class AgentDataProviderImpl implements AgentDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(AgentDataProviderImpl.class);

    @Autowired
    private ProjectService projectService;

    @Autowired
    private AppService appService;

    @Autowired
    private ClientSessionService clientSessionService;

    @Autowired
    private CoverageService coverageService;

    @Autowired
    private SnapshotService snapshotService;

    @Override
    public Map<String, Object> getProjectInfo(String projectId) {
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
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<CoverageReportIndex> reports = coverageService.getReportsByAppId(appId);
            if (reports != null) {
                for (CoverageReportIndex report : reports) {
                    Map<String, Object> reportMap = new HashMap<>();
                    reportMap.put("id", report.getId());
                    reportMap.put("createTime", report.getCreateTime());
                    // 计算覆盖率
                    reportMap.put("lineRate", calculateRate(report.getCoveredLines(), report.getTotalLines()));
                    reportMap.put("branchRate", calculateRate(report.getCoveredBranchConditions(), report.getTotalBranchConditions()));
                    reportMap.put("methodRate", calculateRate(report.getCoveredMethods(), report.getTotalMethods()));
                    reportMap.put("reportType", report.getReportType());
                    result.add(reportMap);
                }
                // 按创建时间倒序排序
                result.sort((a, b) -> {
                    Object timeA = a.get("createTime");
                    Object timeB = b.get("createTime");
                    if (timeA == null && timeB == null) return 0;
                    if (timeA == null) return 1;
                    if (timeB == null) return -1;
                    return timeB.toString().compareTo(timeA.toString());
                });
            }
        } catch (Exception e) {
            logger.error("Get coverage reports failed: {}", appId, e);
        }
        return result;
    }

    @Override
    public Map<String, Object> getCoverageReportDetail(String reportId) {
        Map<String, Object> result = new HashMap<>();
        try {
            CoverageReportIndex report = coverageService.getReport(reportId);
            if (report != null) {
                result.put("id", report.getId());
                result.put("appId", report.getAppId());
                result.put("appName", getAppName(report.getAppId()));
                result.put("createTime", report.getCreateTime());
                result.put("lineRate", calculateRate(report.getCoveredLines(), report.getTotalLines()));
                result.put("branchRate", calculateRate(report.getCoveredBranchConditions(), report.getTotalBranchConditions()));
                result.put("methodRate", calculateRate(report.getCoveredMethods(), report.getTotalMethods()));
                result.put("reportType", report.getReportType());
                result.put("classCount", report.getTotalClasses());
                result.put("methodCount", report.getTotalMethods());
                result.put("branch", report.getRepoBranch());
                result.put("commitId", report.getRepoCommitId());
                result.put("versionNumber", report.getVersionNumber());
            }
        } catch (Exception e) {
            logger.error("Get coverage report detail failed: {}", reportId, e);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getCoverageTrend(String appId, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            // getTrendData需要versionNumber参数，这里暂时返回空列表
            // 实际使用时需要根据应用配置获取版本号
            List<Map<String, Object>> trendData = coverageService.getTrendData(appId, null);
            if (trendData != null) {
                int count = 0;
                for (Map<String, Object> data : trendData) {
                    if (count >= limit) break;
                    Map<String, Object> trendMap = new HashMap<>();
                    trendMap.put("createTime", data.get("createTime"));
                    trendMap.put("lineRate", data.get("lineRate"));
                    trendMap.put("branchRate", data.get("branchRate"));
                    trendMap.put("methodRate", data.get("methodRate"));
                    result.add(trendMap);
                    count++;
                }
            }
        } catch (Exception e) {
            logger.error("Get coverage trend failed: {}", appId, e);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getClassCoverageList(String reportId, Double minRate, Double maxRate) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            Page<ClassCoverageIndex> page = coverageService.getClassCoveragePage(
                    reportId, null, null, minRate, maxRate, null, null, null, null, null, null,
                    PageRequest.of(0, 50));
            if (page != null && page.hasContent()) {
                for (ClassCoverageIndex cls : page.getContent()) {
                    Map<String, Object> classMap = new HashMap<>();
                    classMap.put("className", cls.getClassName());
                    classMap.put("lineRate", cls.getLineRate());
                    classMap.put("branchRate", cls.getBranchRate());
                    classMap.put("methodRate", cls.getMethodRate());
                    classMap.put("complexity", cls.getTotalComplexity());
                    result.add(classMap);
                }
            }
        } catch (Exception e) {
            logger.error("Get class coverage list failed: {}", reportId, e);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getTraceList(String projectId, String appId, int limit) {
        // TraceNodeCache不是Spring Bean，无法直接注入
        // 暂时返回空列表，实际实现需要通过其他方式获取调用链数据
        return new ArrayList<>();
    }

    @Override
    public Map<String, Object> getTraceDetail(String traceId) {
        Map<String, Object> result = new HashMap<>();
        // 暂不实现，需要通过SnapshotService或其他方式获取
        return result;
    }

    @Override
    public List<Map<String, Object>> getSnapshots(String projectId, String userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, userId);
            if (snapshots != null) {
                for (SnapshotVo snapshot : snapshots) {
                    Map<String, Object> snapshotMap = new HashMap<>();
                    snapshotMap.put("id", snapshot.getId());
                    snapshotMap.put("name", snapshot.getName());
                    snapshotMap.put("creator", snapshot.getCreateUser());
                    snapshotMap.put("createTime", snapshot.getCreateTime());
                    snapshotMap.put("shared", snapshot.getShare());
                    result.add(snapshotMap);
                }
            }
        } catch (Exception e) {
            logger.error("Get snapshots failed: {}", projectId, e);
        }
        return result;
    }

    @Override
    public Map<String, Object> getSnapshotDetail(String snapshotId) {
        Map<String, Object> result = new HashMap<>();
        try {
            SnapshotVo snapshot = snapshotService.get(snapshotId);
            if (snapshot != null) {
                result.put("id", snapshot.getId());
                result.put("name", snapshot.getName());
                result.put("creator", snapshot.getCreateUser());
                result.put("createTime", snapshot.getCreateTime());
                result.put("shared", snapshot.getShare());
                result.put("traceId", snapshot.getTraceId());
            }
        } catch (Exception e) {
            logger.error("Get snapshot detail failed: {}", snapshotId, e);
        }
        return result;
    }

    @Override
    public Map<String, Object> searchCodeRelation(String projectId, String keyword) {
        Map<String, Object> result = new HashMap<>();
        // TODO: 实现代码关系搜索，需要调用相关的代码分析服务
        result.put("interfaces", Collections.emptyList());
        result.put("classes", Collections.emptyList());
        return result;
    }

    @Override
    public Map<String, Object> getCallGraph(String className, String methodName) {
        Map<String, Object> result = new HashMap<>();
        // TODO: 实现调用关系图查询，需要调用相关的代码分析服务
        result.put("callers", Collections.emptyList());
        result.put("callees", Collections.emptyList());
        result.put("traces", Collections.emptyList());
        return result;
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

            // 获取快照数量
            List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, null);
            result.put("snapshotCount", snapshots != null ? snapshots.size() : 0);

            result.put("traceCount", 0);
        } catch (Exception e) {
            logger.error("Get project statistics failed: {}", projectId, e);
        }
        return result;
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
        return map;
    }

    private String getAppName(String appId) {
        if (appId == null) return "";
        try {
            AppVo app = appService.getApp(appId);
            return app != null ? app.getName() : appId;
        } catch (Exception e) {
            return appId;
        }
    }

    private double calculateRate(long covered, long total) {
        if (total <= 0) return 0.0;
        return (double) covered / total;
    }
}
