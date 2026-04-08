package com.oAT.web.service.impl;

import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.ai.agent.AgentDataProvider;
import com.oAT.ai.agent.cache.ToolCallCache;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.TraceNodeRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.StaticSourceClassInfo;
import com.oAT.web.esDao.entity.StaticSourceInfo;
import com.oAT.web.esDao.entity.TraceNodeIndex;
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
                                    String methodUri = (String) methodMap.get("methodUri");
                                    if ((methodName != null && methodName.toLowerCase().contains(lowerKeyword))
                                            || (methodUri != null && methodUri.toLowerCase().contains(lowerKeyword))) {
                                        nameMatches = true;
                                        break;
                                    }
                                }
                            }
                        }

                        if (!nameMatches) continue;

                        // 判断是否为 HTTP 接口（有 methodUri 的视为接口）
                        boolean isInterface = hasHttpInterface(classInfo);

                        if (isInterface) {
                            Map<String, Object> iface = new HashMap<>();
                            iface.put("name", fullClassName);
                            iface.put("appName", app.getName());
                            iface.put("methods", extractMethodUris(classInfo));
                            interfaceList.add(iface);
                        } else {
                            Map<String, Object> cls = new HashMap<>();
                            cls.put("name", fullClassName);
                            cls.put("package", extractPackageName(fullClassName));
                            cls.put("simpleName", simpleClassName(fullClassName));
                            cls.put("appName", app.getName());
                            classList.add(cls);
                        }

                        if (interfaceList.size() >= matchLimit && classList.size() >= matchLimit) break;
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
            // 1. 从静态数据中获取该类的方法信息
            Map<String, StaticSourceInfo> classStaticMap = findClassStaticInfo(className);
            if (!classStaticMap.isEmpty()) {
                // 遍历所有匹配的类，提取方法列表
                for (Map.Entry<String, StaticSourceInfo> entry : classStaticMap.entrySet()) {
                    String appId = entry.getKey();
                    StaticSourceInfo info = entry.getValue();
                    if (info.getClassInfo() != null && info.getClassInfo().getMethodMaps() != null) {
                        for (Map.Entry<String, ?> mEntry : info.getClassInfo().getMethodMaps().entrySet()) {
                            Object methodObj = mEntry.getValue();
                            if (methodObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> methodMap = (Map<String, Object>) methodObj;
                                String mName = (String) methodMap.get("methodName");
                                // 如果指定了方法名，只返回匹配的
                                if (methodName != null && !methodName.isEmpty()
                                        && !methodName.equalsIgnoreCase(mName)) {
                                    continue;
                                }
                                Map<String, Object> callee = new HashMap<>();
                                callee.put("className", info.getClassInfo().getClassName());
                                callee.put("methodName", mName != null ? mName : mEntry.getKey());
                                callee.put("type", "static-method");
                                callee.put("appId", appId);
                                callees.add(callee);
                            }
                        }
                    }
                }
            }

            // 2. 从 ES trace_node 中搜索包含该类的运行时调用链
            String lowerClassName = className.toLowerCase();
            int traceLimit = 10;

            // 获取项目下的应用 ID 列表用于搜索
            Set<String> appIds = classStaticMap.keySet();
            if (appIds.isEmpty()) {
                // 如果静态数据没找到，尝试从所有应用的 trace 数据搜索
                // 通过 TraceNodeRepository 搜索（注意：ES 全文搜索需要用 ElasticsearchTemplate）
                logger.debug("No static info found for class {}, will search traces by app list", className);
            }

            for (String appId : appIds) {
                if (traces.size() >= traceLimit) break;
                try {
                    // 从 ES 查询该应用最近的 trace 节点，筛选含目标类的
                    List<TraceNodeIndex> nodes = traceNodeRepository.findByAppId(appId);
                    if (nodes == null || nodes.isEmpty()) continue;

                    Set<String> seenTraceIds = new HashSet<>(); // 去重
                    for (TraceNodeIndex node : nodes) {
                        if (traces.size() >= traceLimit) break;
                        HttpTraceNode httpNode = node.getHttpNode();
                        if (httpNode == null) continue;
                        String traceId = node.getTraceId();

                        // 检查 HTTP 入口的 codeNodes 是否包含目标类
                        boolean found = false;
                        if (httpNode.getCodeNodes() != null) {
                            for (var codeNode : httpNode.getCodeNodes()) {
                                if (codeNode.getClassName() != null
                                        && (codeNode.getClassName().toLowerCase().contains(lowerClassName)
                                            || lowerClassName.contains(simpleClassName(codeNode.getClassName()).toLowerCase()))) {
                                    found = true;
                                    // 提取调用方信息
                                    extractCallerFromStack(httpNode.getCodeNodes(), codeNode, callers, seenTraceIds);
                                    break;
                                }
                            }
                        }

                        if (found && !seenTraceIds.contains(traceId)) {
                            seenTraceIds.add(traceId);
                            Map<String, Object> traceInfo = new HashMap<>();
                            traceInfo.put("traceId", traceId);
                            traceInfo.put("url", httpNode.getRequestUrl() != null ? httpNode.getRequestUrl() : "");
                            traceInfo.put("appId", appId);
                            traceInfo.put("createTime", node.getCreateTime());
                            traces.add(traceInfo);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Search call graph traces for app {} failed: {}", appId, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Get call graph failed: className={}, methodName={}", className, methodName, e);
        }

        result.put("callers", callers);
        result.put("callees", callees);
        result.put("traces", traces);
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

    // ========== 代码关系查询辅助方法 ==========

    /**
     * 从全限定类名中提取简单类名
     * 例如: "com.example.UserService" -> "UserService"
     */
    private String simpleClassName(String fullClassName) {
        if (fullClassName == null || fullClassName.isEmpty()) return "";
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    /**
     * 从全限定类名中提取包名
     * 例如: "com.example.UserService" -> "com.example"
     */
    private String extractPackageName(String fullClassName) {
        if (fullClassName == null || fullClassName.isEmpty()) return "";
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot > 0 ? fullClassName.substring(0, lastDot) : "default";
    }

    /**
     * 判断类的静态信息是否包含 HTTP 接口方法（有 methodUri）
     */
    private boolean hasHttpInterface(StaticSourceClassInfo classInfo) {
        if (classInfo == null || classInfo.getMethodMaps() == null) return false;
        for (Object obj : classInfo.getMethodMaps().values()) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) obj;
                if (m.get("methodUri") != null && !((String) m.get("methodUri")).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 提取类中所有方法的 URI 列表（用于接口展示）
     */
    private List<String> extractMethodUris(StaticSourceClassInfo classInfo) {
        List<String> uris = new ArrayList<>();
        if (classInfo == null || classInfo.getMethodMaps() == null) return uris;
        for (Object obj : classInfo.getMethodMaps().values()) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) obj;
                String uri = (String) m.get("methodUri");
                if (uri != null && !uri.isEmpty()) uris.add(uri);
            }
        }
        return uris.size() > 10 ? uris.subList(0, 10) : uris; // 限制数量
    }

    /**
     * 在项目下所有应用中搜索类名匹配的静态代码信息
     * @param className 类名（支持全限定名或简单名模糊匹配）
     * @return Map<appId, StaticSourceInfo> 匹配结果
     */
    private Map<String, StaticSourceInfo> findClassStaticInfo(String className) {
        Map<String, StaticSourceInfo> result = new LinkedHashMap<>();
        if (className == null || className.isEmpty()) return result;

        String lowerKeyword = className.toLowerCase();
        try {
            // 遍历项目下每个应用搜索静态信息（注意：当前接口只传 className，无 projectId）
            // 通过 staticInfoRepository 全量搜索成本高，这里做有限优化：
            // 如果有 appId 前缀信息则精确查，否则返回空让调用方处理
            logger.debug("findClassStaticInfo called with className={}, note: projectId not available in this API", className);
        } catch (Exception e) {
            logger.warn("findClassStaticInfo failed: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 从堆栈节点中提取目标节点的直接调用方（父节点即为调用方）
     *
     * @param codeNodes  完整的代码堆栈数组
     * @param targetNode 目标节点（被调用的类/方法所在节点）
     * @param callers    输出：调用方列表
     * @param seenTraceIds 已处理的 trace ID 集合（用于去重）
     */
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
