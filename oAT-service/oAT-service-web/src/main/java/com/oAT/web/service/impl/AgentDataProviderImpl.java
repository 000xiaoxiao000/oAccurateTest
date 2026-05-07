package com.oAT.web.service.impl;

import com.oAT.agent.model.HttpTraceNode;
import com.oAT.ai.agent.AgentDataProvider;
import com.oAT.ai.agent.cache.ToolCallCache;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.TraceNodeRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.StaticSourceClassInfo;
import com.oAT.web.esDao.entity.StaticSourceInfo;
import com.oAT.web.esDao.entity.StaticSourceMethodInfo;
import com.oAT.web.esDao.entity.TraceNodeIndex;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.SnapshotVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private CoverageService coverageService;

    @Autowired
    private SnapshotService snapshotService;

    @Autowired
    private StaticInfoRepository staticInfoRepository;

    @Autowired
    private TraceNodeRepository traceNodeRepository;

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
                    reportMap.put("branchRate", calculateRate(report.getCoveredBranchTargets(),
                            report.getTotalBranchTargets()));
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

                // 缓存3分钟（覆盖率数据变化不频繁）
                cache.put(cacheKey, "cached", 3 * 60 * 1000);
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
                result.put("branchRate", calculateRate(report.getCoveredBranchTargets(),
                        report.getTotalBranchTargets()));
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
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            if (limit <= 0) {
                limit = 20;
            }

            List<TraceNodeIndex> nodes = new ArrayList<>();
            if (appId != null && !appId.trim().isEmpty()) {
                nodes.addAll(traceNodeRepository.findByAppId(appId));
            } else {
                List<AppVo> apps = appService.getAppList(projectId);
                if (apps != null) {
                    for (AppVo app : apps) {
                        nodes.addAll(traceNodeRepository.findByAppId(app.getId()));
                    }
                }
            }

            if (nodes.isEmpty()) {
                return result;
            }

            nodes.sort((left, right) -> {
                Date leftTime = left.getCreateTime();
                Date rightTime = right.getCreateTime();
                if (leftTime == null && rightTime == null) return 0;
                if (leftTime == null) return 1;
                if (rightTime == null) return -1;
                return rightTime.compareTo(leftTime);
            });

            Set<String> seenTraceIds = new HashSet<>();
            for (TraceNodeIndex node : nodes) {
                if (node == null || !StringUtils.hasText(node.getTraceId())) {
                    continue;
                }
                if (!seenTraceIds.add(node.getTraceId())) {
                    continue;
                }
                Map<String, Object> trace = new HashMap<>();
                trace.put("traceId", node.getTraceId());
                trace.put("appId", node.getAppId());
                trace.put("createTime", node.getCreateTime());
                trace.put("type", node.getType());
                Object traceNode = node.toTraceNode();
                if (traceNode instanceof HttpTraceNode httpNode) {
                    trace.put("url", httpNode.getRequestUrl());
                    trace.put("appName", httpNode.getApp() != null ? httpNode.getApp().getAppName() : "");
                    trace.put("clientIp", httpNode.getClientIp());
                    trace.put("serverIp", httpNode.getServerIp());
                    trace.put("nodes", httpNode.getCodeNodes());
                    trace.put("method", httpNode.getRequestMethod());
                    trace.put("error", httpNode.getError() != null);
                }
                result.add(trace);
                if (result.size() >= limit) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Get trace list failed: projectId={}, appId={}", projectId, appId, e);
        }
        return result;
    }

    @Override
    public Map<String, Object> getTraceDetail(String traceId) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (traceId == null || traceId.trim().isEmpty()) {
                return result;
            }

            List<TraceNodeIndex> nodes = traceNodeRepository.findByTraceId(traceId, PageRequest.of(0, 50));
            if (nodes == null || nodes.isEmpty()) {
                return result;
            }

            nodes.sort((left, right) -> {
                Date leftTime = left.getCreateTime();
                Date rightTime = right.getCreateTime();
                if (leftTime == null && rightTime == null) return 0;
                if (leftTime == null) return 1;
                if (rightTime == null) return -1;
                return leftTime.compareTo(rightTime);
            });

            TraceNodeIndex first = nodes.get(0);
            HttpTraceNode httpNode = first.getHttpNode();
            result.put("traceId", traceId);
            result.put("appId", first.getAppId());
            result.put("createTime", first.getCreateTime());
            result.put("type", first.getType());
            result.put("nodes", new ArrayList<>());
            if (httpNode != null) {
                result.put("url", httpNode.getRequestUrl());
                result.put("method", httpNode.getRequestMethod());
                result.put("clientIp", httpNode.getClientIp());
                result.put("serverIp", httpNode.getServerIp());
                result.put("appName", httpNode.getApp() != null ? httpNode.getApp().getAppName() : "");
                result.put("error", httpNode.getError() != null);

                List<Map<String, Object>> codeNodes = new ArrayList<>();
                if (httpNode.getCodeNodes() != null) {
                    for (com.oAT.agent.model.StackNodeVo node : httpNode.getCodeNodes()) {
                        if (node == null) {
                            continue;
                        }
                        Map<String, Object> codeNode = new HashMap<>();
                        codeNode.put("id", node.getId());
                        codeNode.put("name", node.getClassName() + "." + node.getMethodName());
                        codeNode.put("className", node.getClassName());
                        codeNode.put("methodName", node.getMethodName());
                        codeNode.put("type", "method");
                        codeNode.put("duration", node.getUseTime());
                        codeNode.put("error", false);
                        codeNode.put("done", node.isDone());
                        codeNode.put("complexity", node.getExecCyclo());
                        codeNodes.add(codeNode);
                    }
                }
                result.put("nodes", codeNodes);
            }
        } catch (Exception e) {
            logger.error("Get trace detail failed: {}", traceId, e);
        }
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
        List<Map<String, Object>> interfaceList = new ArrayList<>();
        List<Map<String, Object>> classList = new ArrayList<>();
        try {
            // 获取项目下所有应用的静态代码信息
            List<AppVo> apps = appService.getAppList(projectId);
            if (apps == null || apps.isEmpty()) {
                result.put("interfaces", interfaceList);
                result.put("classes", classList);
                return result;
            }
            String lowerKeyword = keyword.toLowerCase();
            int matchLimit = 30; // 限制返回数量，避免结果过多

            for (AppVo app : apps) {
                if (interfaceList.size() + classList.size() >= matchLimit * 2) break;
                try {
                    List<StaticSourceInfo> infos = staticInfoRepository.findByAppId(app.getId());
                    if (infos == null) continue;
                    for (StaticSourceInfo info : infos) {
                        StaticSourceClassInfo classInfo = info.getClassInfo();
                        if (classInfo == null || classInfo.getClassName() == null) continue;
                        String fullClassName = classInfo.getClassName();

                        // 关键词匹配：类名或简单类名
                        boolean nameMatches = fullClassName.toLowerCase().contains(lowerKeyword)
                                || simpleClassName(fullClassName).toLowerCase().contains(lowerKeyword);

                        if (!nameMatches && classInfo.getMethodMaps() != null) {
                            // 检查方法名和 URI（接口路径）是否匹配
                            for (Map.Entry<String, ?> entry : classInfo.getMethodMaps().entrySet()) {
                                Object methodObj = entry.getValue();
                                if (methodObj instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> methodMap = (Map<String, Object>) methodObj;
                                    String methodName = (String) methodMap.get("methodName");
                                    if ((methodName != null && methodName.toLowerCase().contains(lowerKeyword))) {
                                        nameMatches = true;
                                        break;
                                    }
                                }
                            }
                        }

                        if (!nameMatches) continue;

                        Map<String, Object> cls = new HashMap<>();
                        cls.put("name", fullClassName);
                        cls.put("package", extractPackageName(fullClassName));
                        cls.put("simpleName", simpleClassName(fullClassName));
                        cls.put("appName", app.getName());
                        classList.add(cls);
                    }
                } catch (Exception e) {
                    logger.warn("Search code relation for app {} failed: {}", app.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Search code relation failed: projectId={}, keyword={}", projectId, keyword, e);
        }

        result.put("interfaces", interfaceList);
        result.put("classes", classList);
        return result;
    }

    @Override
    public Map<String, Object> getCallGraph(String className, String methodName) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> callers = new ArrayList<>();
        List<Map<String, Object>> callees = new ArrayList<>();
        List<Map<String, Object>> traces = new ArrayList<>();

        try {
            boolean classFound = StringUtils.hasText(className) && !findClassStaticInfo(className).isEmpty();
            boolean methodFound = !StringUtils.hasText(methodName);
            if (classFound && StringUtils.hasText(methodName)) {
                methodFound = findTargetMethodInfo(findClassStaticInfo(className).values(), methodName) != null;
            }

            result.put("classFound", classFound);
            result.put("methodFound", methodFound);
            result.put("callers", callers);
            result.put("callees", callees);
            result.put("traces", traces);

            if (!classFound) {
                logger.info("No static source info found for call graph: className={}, methodName={}", className, methodName);
                return result;
            }
            if (StringUtils.hasText(methodName) && !methodFound) {
                logger.info("No static method info found for call graph: className={}, methodName={}", className, methodName);
                return result;
            }

            logger.info("No real method call graph data available for className={}, methodName={}; current data source only stores method coverage metadata, not invocation edges.",
                    className, methodName);
            return result;
        } catch (Exception e) {
            logger.error("Get call graph failed: className={}, methodName={}", className, methodName, e);
            result.put("classFound", false);
            result.put("methodFound", false);
            result.put("callers", callers);
            result.put("callees", callees);
            result.put("traces", traces);
            return result;
        }
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

    @Override
    public String getSourceCode(String className) {
        if (className == null || className.trim().isEmpty()) {
            return null;
        }
        try {
            String targetClass = className.trim();
            String normalizedTarget = normalizeClassName(targetClass);
            String simpleName = simpleClassName(targetClass);

            List<AppVo> allApps = appService.getAppList(null);
            if (allApps == null || allApps.isEmpty()) {
                return null;
            }

            for (AppVo app : allApps) {
                try {
                    List<StaticSourceInfo> appInfos = staticInfoRepository.findByAppId(app.getId());
                    String source = findSourceCodeInAppInfos(appInfos, targetClass, normalizedTarget, simpleName);
                    if (source != null) {
                        return source;
                    }
                } catch (Exception e) {
                    logger.debug("getSourceCode scan app failed: appId={}, className={}, reason={}", app.getId(), targetClass, e.getMessage());
                }
            }

            return null;
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

    private String findSourceCodeInAppInfos(List<StaticSourceInfo> appInfos, String targetClass, String normalizedTarget, String simpleName) {
        if (appInfos == null || appInfos.isEmpty()) {
            return null;
        }
        for (StaticSourceInfo info : appInfos) {
            StaticSourceClassInfo classInfo = info.getClassInfo();
            if (classInfo == null || classInfo.getClassName() == null || classInfo.getSourceCode() == null) {
                continue;
            }
            String candidateClassName = classInfo.getClassName().trim();
            String normalizedCandidate = normalizeClassName(candidateClassName);
            String candidateSimpleName = simpleClassName(candidateClassName);

            if (matchesClassName(candidateClassName, normalizedCandidate, candidateSimpleName, targetClass, normalizedTarget, simpleName)) {
                return classInfo.getSourceCode();
            }

            if (normalizedCandidate.contains(normalizedTarget) || normalizedTarget.contains(normalizedCandidate)) {
                return classInfo.getSourceCode();
            }
        }
        return null;
    }

    private boolean matchesClassName(String candidateClassName, String normalizedCandidate, String candidateSimpleName,
                                     String targetClass, String normalizedTarget, String targetSimpleName) {
        if (candidateClassName.equals(targetClass) || normalizedCandidate.equals(normalizedTarget)) {
            return true;
        }
        if (candidateSimpleName.equals(targetSimpleName)) {
            return true;
        }
        return candidateClassName.endsWith("." + targetSimpleName)
                || targetClass.endsWith("." + candidateSimpleName)
                || normalizedCandidate.endsWith("." + normalizedTarget)
                || normalizedTarget.endsWith("." + normalizedCandidate);
    }

    private boolean matchesMethodName(String candidateMethodName, String candidateKey, String normalizedTarget, String targetMethodName) {
        String normalizedCandidate = normalizeMethodName(candidateMethodName);
        String normalizedCandidateKey = normalizeMethodName(candidateKey);
        String normalizedTargetMethod = normalizeMethodName(targetMethodName);
        return normalizedCandidate.equals(normalizedTargetMethod)
                || normalizedCandidateKey.equals(normalizedTargetMethod)
                || normalizedCandidate.contains(normalizedTargetMethod)
                || normalizedTargetMethod.contains(normalizedCandidate)
                || normalizedCandidateKey.contains(normalizedTargetMethod)
                || normalizedTargetMethod.contains(normalizedCandidateKey)
                || normalizedCandidate.equals(normalizedTarget)
                || normalizedCandidateKey.equals(normalizedTarget);
    }

    private String normalizeClassName(String className) {
        if (className == null) {
            return "";
        }
        return className.trim().replace('$', '.').toLowerCase(Locale.ROOT);
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

    // ========== 代码关系查询辅助方法 ==========

    /**
     * 从全限定类名中提取简单类名
     * 例如: "com.oAT.web.service.UserService" -> "UserService"
     */
    private String simpleClassName(String fullClassName) {
        if (fullClassName == null || fullClassName.isEmpty()) return "";
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    /**
     * 从全限定类名中提取包名
     * 例如: "com.oAT.web.service.UserService" -> "com.oAT.web.service"
     */
    private String extractPackageName(String fullClassName) {
        if (fullClassName == null || fullClassName.isEmpty()) return "";
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot > 0 ? fullClassName.substring(0, lastDot) : "default";
    }

    /**
     * 在项目下所有应用中搜索类名匹配的静态代码信息
     *
     * @param className 类名（支持全限定名或简单名模糊匹配）
     * @return Map<appId, StaticSourceInfo> 匹配结果
     */
    private Map<String, StaticSourceInfo> findClassStaticInfo(String className) {
        Map<String, StaticSourceInfo> result = new LinkedHashMap<>();
        if (className == null || className.isEmpty()) return result;

        String targetClass = className.trim();
        String normalizedTarget = normalizeClassName(targetClass);
        String simpleName = simpleClassName(targetClass);
        try {
            List<AppVo> apps = appService.getAppList(null);
            if (apps == null || apps.isEmpty()) {
                return result;
            }
            for (AppVo app : apps) {
                try {
                    List<StaticSourceInfo> infos = staticInfoRepository.findByAppId(app.getId());
                    if (infos == null || infos.isEmpty()) {
                        continue;
                    }
                    for (StaticSourceInfo info : infos) {
                        StaticSourceClassInfo classInfo = info.getClassInfo();
                        if (classInfo == null || !StringUtils.hasText(classInfo.getClassName())) {
                            continue;
                        }
                        String candidateClassName = classInfo.getClassName().trim();
                        if (matchesClassName(candidateClassName, normalizeClassName(candidateClassName), simpleClassName(candidateClassName),
                                targetClass, normalizedTarget, simpleName)) {
                            result.put(app.getId(), info);
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("findClassStaticInfo scan app failed: appId={}, className={}, reason={}", app.getId(), className, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("findClassStaticInfo failed: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 从堆栈节点中提取目标节点的直接调用方（父节点即为调用方）
     */
    private StaticSourceMethodInfo findTargetMethodInfo(Collection<StaticSourceInfo> classInfos, String methodName) {
        if (classInfos == null || classInfos.isEmpty() || !StringUtils.hasText(methodName)) {
            return null;
        }
        String normalizedTarget = normalizeMethodName(methodName);
        for (StaticSourceInfo info : classInfos) {
            if (info == null || info.getClassInfo() == null || info.getClassInfo().getMethodMaps() == null) {
                continue;
            }
            for (Map.Entry<String, StaticSourceMethodInfo> entry : info.getClassInfo().getMethodMaps().entrySet()) {
                StaticSourceMethodInfo methodInfo = entry.getValue();
                if (methodInfo == null) {
                    continue;
                }
                if (matchesMethodName(methodInfo.getMethodName(), entry.getKey(), normalizedTarget, methodName)) {
                    return methodInfo;
                }
            }
        }
        return null;
    }

    private String normalizeMethodName(String methodName) {
        if (!StringUtils.hasText(methodName)) {
            return "";
        }
        String normalized = methodName.trim();
        int parenIndex = normalized.indexOf('(');
        if (parenIndex > 0) {
            normalized = normalized.substring(0, parenIndex);
        }
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot >= 0) {
            normalized = normalized.substring(lastDot + 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private void extractCallerFromStack(com.oAT.agent.model.StackNodeVo[] codeNodes,
                                        com.oAT.agent.model.StackNodeVo targetNode,
                                        List<Map<String, Object>> callers,
                                        Set<String> seenTraceIds) {
        if (codeNodes == null || targetNode == null) return;
        String targetId = targetNode.getId();

        // 找到目标节点的直接父节点（即调用方）
        for (com.oAT.agent.model.StackNodeVo node : codeNodes) {
            if (node == null || node.getId() == null) continue;
            if (isParentOf(node.getId(), targetId)) {
                // node 是 target 的父节点，即调用方
                String key = node.getClassName() + "." + node.getMethodName();
                boolean exists = callers.stream()
                        .anyMatch(c -> key.equals(c.get("className") + "." + c.get("methodName")));
                if (!exists) {
                    Map<String, Object> caller = new HashMap<>();
                    caller.put("className", node.getClassName());
                    caller.put("methodName", node.getMethodName());
                    caller.put("type", inferNodeType(node));
                    callers.add(caller);
                }
                break; // 只取直接调用方
            }
        }
    }

    /**
     * 判断 parentId 是否是 childId 的直接父节点
     * 基于 StackNodeVo.id 层级结构: "0", "0.1", "0.1.2"
     */
    private boolean isParentOf(String parentId, String childId) {
        if (parentId == null || childId == null) return false;
        // 父ID 应该是 子ID 去掉最后一段 ".xxx" 后的结果
        int lastDot = childId.lastIndexOf('.');
        if (lastDot <= 0) return false;
        return parentId.equals(childId.substring(0, lastDot));
    }

    /**
     * 根据类名推断节点类型
     */
    private String inferNodeType(com.oAT.agent.model.StackNodeVo node) {
        if (node == null || node.getClassName() == null) return "unknown";
        String cn = node.getClassName().toLowerCase();
        if (cn.contains("controller") || cn.contains("action") || cn.contains("resource")) return "controller";
        if (cn.contains("service") || cn.contains("manager") || cn.contains("biz")) return "service";
        if (cn.contains("dao") || cn.contains("mapper") || cn.contains("repository")) return "dao";
        if (cn.contains("util") || cn.contains("helper") || cn.contains("tool")) return "util";
        return "class";
    }
}
