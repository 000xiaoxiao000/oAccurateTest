package com.oAT.web.control;

import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.common.ClassStructure;
import com.oAT.web.common.ClassUtil;
import com.oAT.web.domain.*;
import com.oAT.web.esDao.ApiEndpointRepository;
import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.exceptions.BusinessException;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/p/{projectId}/map")
public class MapControl {

    private static final Logger logger = LoggerFactory.getLogger(MapControl.class);

    @Autowired
    SystemSnapshotService systemSnapshotService;
    @Autowired
    AppService appService;

    @Autowired
    VersionService versionService;

    @Autowired
    ResourceService resourceService;

    @Autowired
    SnapshotSearchService snapshotSearchService;

    @Autowired
    SnapshotService snapshotService;

    @Autowired
    ClientSessionService clientSessionService;

    @Autowired
    ProjectService projectService;

    @Autowired
    StaticInfoRepository staticInfoRepository;

    @Autowired
    CoverageReportRepository coverageReportRepository;

    @Autowired
    ClassCoverageRepository classCoverageRepository;

    @Autowired
    ApiEndpointRepository apiEndpointRepository;

    @Value("${rmi.server.port}")
    private int rmiPort;

    @RequestMapping("/app")
    public String openAppMapView(@PathVariable String projectId, String appId, String layers, Model model) {
        AppVo app = appService.getApp(appId);
        model.addAttribute("app", app);
        //设置显示的层
        layers = layers == null ? "" : layers;
        Arrays.stream(layers.split(",")).distinct().filter(a -> !a.trim().equals("")).forEach(a -> {
            model.addAttribute(a + "Active", "active");
        });
        model.addAttribute("layers", layers);
        // 数据URL
        model.addAttribute("dataUrl", String.format("/p/%s/map/app/data?appId=%s&layers=%s", projectId, appId, layers));
        model.addAttribute("visualAngle", "app");
        return "/map/mapVisual";
    }

    @RequestMapping("/app/data")
    @ResponseBody
    public List<ImageElement> getAppMapData(@PathVariable String projectId, String appId, String layers) throws BusinessException {
        layers = layers == null ? "" : layers;
        List<String> layerList = Arrays.stream(layers.split(",")).distinct().filter(a -> !a.trim().equals("")).collect(Collectors.toList());

        List<SystemSnapshot> snapshots = systemSnapshotService.findAll(projectId, appId);
        List<ImageElement> results = new ArrayList<>();
        results.addAll(new SnapshotLayer(snapshots).elements());
        if (layerList.contains("code")) {
            results.addAll(buildCodeLayerElements(projectId, appId));
        }
        if (layerList.contains("table")) {
            results.addAll(new SnapshotTableLayer(snapshots).elements());
        }
        // 冷热图层
        results = new HotLayer(results).elements();
        return results;
    }

    @RequestMapping("/home")
    public String openHomeMapView(@PathVariable String projectId, Model model, @SessionAttribute UserVo user) {
        String loginName = user.getName();
        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);

        // 登录用户权限，原则是最小权限（访客）
        String loginNameRole = "visitor";
        for (ProjectMemberVo member : members) {
            if (loginName.equals(member.getMemberName())) {
                loginNameRole = String.valueOf(member.getRole());
            }
        }

        model.addAttribute("loginNameRole", loginNameRole);

        //视角
        model.addAttribute("visualAngle", "home");
        model.addAttribute("dataUrl", String.format("/p/%s/map/home/data", projectId));
        return "/map/mapVisual";
    }

    @RequestMapping("/home/data")
    @ResponseBody
    public List<ImageElement> getHomeMapData(@PathVariable String projectId) {
        List<AppVo> appList = appService.getAppList(projectId);
        List<SystemSnapshot> snapshots = systemSnapshotService.findAll(projectId);
        Map<String, Collection<TraceNode>> traceNodesBySnapshotId = snapshots.stream()
                .filter(snapshot -> StringUtils.hasText(snapshot.getTraceId()))
                .collect(Collectors.toMap(SystemSnapshot::getId, this::getTraceNodesSafely, (left, right) -> left));
        Collection<Collection<TraceNode>> liveTraceNodeGroups = getRecentLiveTraceNodeGroups(appList);
        List<ApiEndpointIndex> endpoints = appList.stream()
                .flatMap(app -> apiEndpointRepository.findByAppIdOrderByEndpointTypeAscUrlAsc(app.getId()).stream())
                .collect(Collectors.toList());
        RemoteCallResolver remoteCallResolver = new RemoteCallResolver(appList, endpoints);
        return new AppRelationLayer(appList, snapshots, traceNodesBySnapshotId, liveTraceNodeGroups, remoteCallResolver).elements();
    }

    private Collection<Collection<TraceNode>> getRecentLiveTraceNodeGroups(List<AppVo> appList) {
        TraceItemSearchParam searchParam = new TraceItemSearchParam();
        searchParam.setAppIds(appList.stream().map(AppVo::getId).collect(Collectors.toList()));
        searchParam.setMaxSize(500);
        return clientSessionService.getTraceItemByTime(24 * 60 * 60, searchParam).stream()
                .map(TraceItemVo::getTraceId)
                .filter(StringUtils::hasText)
                .distinct()
                .map(clientSessionService::getTraceNodes)
                .filter(nodes -> nodes != null && !nodes.isEmpty())
                .map(Map::values)
                .collect(Collectors.toList());
    }

    private Collection<TraceNode> getTraceNodesSafely(SystemSnapshot snapshot) {
        try {
            return snapshotService.getTraceNodes(snapshot.getTraceId());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @RequestMapping("/code")
    public String openCodeMap(String traceId, Model model, @PathVariable String projectId) {
        model.addAttribute("dataUrl", String.format("/p/%s/map/code/data?traceId=%s", projectId, traceId));
        model.addAttribute("visualAngle", "code");
        return "/map/map";
    }

    /**
     * 获取指定用例堆栈代码 精确到类
     *
     * @param traceId
     * @param projectId return List<ImageElement>
     */
    @RequestMapping("/code/data")
    @ResponseBody
    public List<ImageElement> getMapStackCodeNode(String traceId, @PathVariable String projectId) {
        String snapshotId = "root";
        TraceNode traceNode = snapshotService.getTraceNode(traceId, "0");
        if (traceNode instanceof HttpTraceNode) {
            //构建 root 节点
            ImageData imageData = new ImageData("root");
            imageData.name = ((HttpTraceNode) traceNode).getRequestUrl();
            imageData.weight = 40;
            ImageElement element = new ImageElement(imageData);
            element.group = "nodes";
            element.classes = new String[]{"code_class", "stack_code", "entry_code"};
            List<ImageElement> imageElements = new ArrayList<>();
            imageElements.add(element);

            String appId = traceNode.getApp() != null ? traceNode.getApp().getAppId() : null;
            StackCodeLayer codeLayer = new StackCodeLayer(((HttpTraceNode) traceNode).getCodeNodes(), snapshotId,
                    buildStaticMethodLookup(appId));
            imageElements.addAll(codeLayer.elements());

            return imageElements;
        } else {
            throw new RuntimeException(String.format("暂不支持 %s 类型获取源码堆栈", traceNode.getClass().getSimpleName()));
        }
    }

    // 获取指定快照图层
    @RequestMapping("/layer/snapshot")
    @ResponseBody
    public List<ImageElement> getSnapshotNode(@PathVariable String projectId, String id) {
        SystemSnapshot snapshot = systemSnapshotService.getById(id);
        return new SnapshotLayer(Arrays.asList(snapshot)).elements();
    }

    //获取应用快照图层 节点
    @RequestMapping("/layer/appSnapshot")
    @ResponseBody
    public List<ImageElement> getAppSnapshotNode(@PathVariable String projectId, String appId) {
        AppVo app = appService.getApp(appId);
        List<SystemSnapshot> snapshots = systemSnapshotService.findAll(projectId, appId);
        return new AppSnapshotLayer(snapshots, app).elements();
    }

    // 获取快照表结构
    @RequestMapping("/layer/snapshotTable")
    @ResponseBody
    public List<ImageElement> getSnapshotTableNode(@PathVariable String projectId, String snapshotId) {
        SystemSnapshot snapshots = systemSnapshotService.getById(snapshotId);
        return new SnapshotTableLayer(Arrays.asList(snapshots)).elements();
    }

    // 获取快照远程服务
    @RequestMapping("/layer/snapshotRemote")
    @ResponseBody
    public List<ImageElement> getSnapshotRemoteNode(@PathVariable String projectId, String snapshotId) {
        SystemSnapshot snapshots = systemSnapshotService.getById(snapshotId);
        return new SnapshotRemoteLayer(Arrays.asList(snapshots)).elements();
    }

    @RequestMapping("/layer/tableSnapshot")
    @ResponseBody
    public List<ImageElement> getTableSnapshotNode(@PathVariable String projectId, DatabaseTable db) {
        Assert.notNull(db, "参数 db 不能为空");
        Assert.notNull(db.getDatabase(), "参数 DataBase 不能为空");
        Assert.notNull(db.getTable(), "参数 table 不能为空");

        List<SystemSnapshot> snapshots = snapshotSearchService.searchByTable(projectId, db.getDatabase(), db.getTable());
        return new TableSnapshotLayer(snapshots, db).elements();
    }

    // 基于远程服务获取 节点层
    @RequestMapping("/layer/dubboSnapshot")
    @ResponseBody
    public List<ImageElement> getDubboSnapshotNode(@PathVariable String projectId, String interfaceName, String methodName) {
//        searchByDubbo
        List<SystemSnapshot> snapshots = snapshotSearchService.searchByDubbo(projectId, interfaceName, methodName);
        return new DubboSnapshotLayer(snapshots, interfaceName, methodName).elements();
    }

    @RequestMapping("/layer/stack/code")
    @ResponseBody
    public List<ImageElement> getStackCodeNode(@PathVariable String projectId, String snapshotId) {
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        TraceNode traceNode = snapshotService.getTraceNode(snapshot.getTraceId(), "0");
        if (traceNode instanceof HttpTraceNode) {
            StackCodeLayer codeLayer = new StackCodeLayer(((HttpTraceNode) traceNode).getCodeNodes(), snapshotId,
                    buildStaticMethodLookup(snapshot.getAppId()));
            return codeLayer.elements();
        } else {
            throw new RuntimeException(String.format("暂不支持 %s 类型获取源码堆栈", traceNode.getClass().getSimpleName()));
        }
    }

    private List<ImageElement> buildCodeLayerElements(String projectId, String appId) {
        String unavailableMessage = null;
        try {
            List<ImageElement> elements = buildCodeLayer(projectId, appId).elements();
            if (!elements.isEmpty()) {
                enrichCodeClassMetrics(appId, elements);
                return elements;
            }
            unavailableMessage = "未解析到版本源码调用关系，已展示快照覆盖类";
        } catch (BusinessException | IllegalStateException e) {
            logger.warn("源码图层构建失败，已跳过代码图层, projectId={}, appId={}, reason={}", projectId, appId, e.getMessage());
            unavailableMessage = e.getMessage();
        }
        List<ImageElement> runtimeCodeElements = buildRuntimeSnapshotCodeLayerElements(projectId, appId);
        if (!runtimeCodeElements.isEmpty()) {
            enrichCodeClassMetrics(appId, runtimeCodeElements);
            return runtimeCodeElements;
        }
        return Collections.singletonList(buildNoticeNode("code-layer-unavailable", unavailableMessage));
    }

    private List<ImageElement> buildRuntimeSnapshotCodeLayerElements(String projectId, String appId) {
        List<SystemSnapshot> snapshots = systemSnapshotService.findAll(projectId, appId);
        return snapshots.stream()
                .flatMap(SystemSnapshot::getCodeToClass)
                .map(this::normalizeCodeClassName)
                .filter(StringUtils::hasText)
                .distinct()
                .map(this::buildRuntimeCodeClassNode)
                .collect(Collectors.toList());
    }

    private String normalizeCodeClassName(String className) {
        if (!StringUtils.hasText(className)) {
            return null;
        }
        String normalized = className.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.replace('/', '.');
    }

    private ImageElement buildRuntimeCodeClassNode(String className) {
        ImageData data = new ImageData(className);
        data.name = ClassUtil.getClassSimpleName(className);
        data.describe = className;
        data.weight = 20;
        ImageElement element = new ImageElement(data);
        element.group = "nodes";
        element.classes = new String[]{"code_class", "runtime-code"};
        return element;
    }

    private void enrichCodeClassMetrics(String appId, List<ImageElement> elements) {
        if (!StringUtils.hasText(appId) || elements == null || elements.isEmpty()) {
            return;
        }
        Map<String, ClassMetric> metrics = buildClassMetrics(appId);
        elements.stream()
                .filter(element -> element.group != null && element.group.equals("nodes"))
                .filter(element -> element.classes != null && Arrays.asList(element.classes).contains("code_class"))
                .forEach(element -> {
                    ClassMetric metric = metrics.get(normalizeCodeClassName(element.data.id));
                    if (metric == null) {
                        metric = metrics.get(normalizeCodeClassName(element.data.describe));
                    }
                    if (metric == null) {
                        return;
                    }
                    if (metric.totalLines > 0) {
                        element.data.lineTotal = new ArrayList<>(Collections.singletonList(metric.totalLines));
                        element.data.doLines = new ArrayList<>(Collections.singletonList(metric.coveredLines));
                        element.data.coverageRate = metric.coverageRate;
                    }
                    if (metric.complexity > 0) {
                        element.data.cyclo = metric.complexity;
                    }
                    if (metric.totalMethods > 0) {
                        element.data.executeMethodTotal = new ArrayList<>(Collections.singletonList(metric.coveredMethods));
                        element.data.methodTotal = new ArrayList<>(Collections.singletonList(metric.totalMethods));
                    }
                });
    }

    private Map<String, ClassMetric> buildClassMetrics(String appId) {
        Map<String, ClassMetric> metrics = new HashMap<>();
        enrichStaticClassMetrics(appId, metrics);
        enrichCoverageClassMetrics(appId, metrics);
        return metrics;
    }

    private void enrichStaticClassMetrics(String appId, Map<String, ClassMetric> metrics) {
        List<StaticSourceInfo> staticInfos = staticInfoRepository.findByAppId(appId);
        for (StaticSourceInfo staticInfo : staticInfos) {
            if (staticInfo.getClassInfo() == null || !StringUtils.hasText(staticInfo.getClassInfo().getClassName())) {
                continue;
            }
            String className = normalizeCodeClassName(staticInfo.getClassInfo().getClassName());
            ClassMetric metric = metrics.computeIfAbsent(className, key -> new ClassMetric());
            if (staticInfo.getClassInfo().getMethodMaps() == null) {
                continue;
            }
            metric.totalMethods = staticInfo.getClassInfo().getMethodMaps().size();
            Set<Integer> lines = new HashSet<>();
            int complexity = 0;
            for (StaticSourceMethodInfo methodInfo : staticInfo.getClassInfo().getMethodMaps().values()) {
                if (methodInfo.getMethodLineNumberMap() != null) {
                    lines.addAll(methodInfo.getMethodLineNumberMap());
                }
                if (methodInfo.getCyclomaticComplexityMap() != null) {
                    complexity += methodInfo.getCyclomaticComplexityMap();
                }
            }
            metric.totalLines = Math.max(metric.totalLines, lines.size());
            metric.complexity = Math.max(metric.complexity, complexity);
        }
    }

    private void enrichCoverageClassMetrics(String appId, Map<String, ClassMetric> metrics) {
        List<CoverageReportIndex> reports = coverageReportRepository.findByAppId(appId);
        CoverageReportIndex latestReport = reports.stream()
                .max(Comparator.comparing(CoverageReportIndex::getCreateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        if (latestReport == null || !StringUtils.hasText(latestReport.getId())) {
            return;
        }
        List<ClassCoverageIndex> classCoverages = classCoverageRepository.findByReportId(latestReport.getId());
        for (ClassCoverageIndex classCoverage : classCoverages) {
            String className = normalizeCodeClassName(classCoverage.getClassName());
            if (!StringUtils.hasText(className)) {
                continue;
            }
            ClassMetric metric = metrics.computeIfAbsent(className, key -> new ClassMetric());
            metric.totalMethods = Math.max(metric.totalMethods, classCoverage.getTotalMethods());
            metric.coveredMethods = Math.max(metric.coveredMethods, classCoverage.getCoveredMethods());
            metric.totalLines = Math.max(metric.totalLines, classCoverage.getTotalLines());
            metric.coveredLines = Math.max(metric.coveredLines, classCoverage.getCoveredLines());
            metric.complexity = Math.max(metric.complexity, classCoverage.getTotalComplexity());
            metric.coverageRate = classCoverage.getLineRate() != null
                    ? classCoverage.getLineRate().floatValue()
                    : calculateRate(metric.coveredLines, metric.totalLines);
        }
    }

    private float calculateRate(int covered, int total) {
        if (total <= 0) {
            return 0F;
        }
        return Math.round(covered * 10000F / total) / 100F;
    }

    private static class ClassMetric {
        private int totalMethods;
        private int coveredMethods;
        private int totalLines;
        private int coveredLines;
        private int complexity;
        private float coverageRate;
    }

    private ImageElement buildNoticeNode(String id, String message) {
        ImageData data = new ImageData(id);
        data.name = message;
        data.weight = 20;
        ImageElement element = new ImageElement(data);
        element.group = "nodes";
        element.classes = new String[]{"notice"};
        return element;
    }

    private SourceCodeLayer buildCodeLayer(String projectId, String appId) throws BusinessException {
        VersionItemVo lastVersion = versionService.getLastVersionItem(projectId, appId);
        if (lastVersion == null || !StringUtils.hasText(lastVersion.getProgramFile())) {
            throw new BusinessException("找不到版本程序文件，无法构建源码图层");
        }
        List<ClassStructure> sources;
        String path = resourceService.getCacheRoot() + lastVersion.getProgramFile();
        if (!new File(path).isFile()) {
            throw new IllegalStateException(String.format("版本文件数据丢失 %s", path));
        }
        try {
            sources = ClassUtil.getClassByWar(path, "*", null);
        } catch (IOException e) {
            throw new IllegalStateException(String.format("版本文件读取失败 %s", path), e);
        }
        return new SourceCodeLayer(sources);
    }

    @RequestMapping("/search")
    @ResponseBody
    public SearchResult doSearch(@PathVariable String projectId, String q) {
        if (!StringUtils.hasText(q)) {
            return new SearchResult();
        }
        Assert.hasText(q, "param 'q' must be not null");
        SearchPage<SnapshotSearchResult> page = snapshotSearchService.doSearch(projectId, q);
        SearchResult searchResult = new SearchResult();
        page.getContents().stream().forEach(a -> {
            String title = Optional.ofNullable(a.getTitleFragment()).orElse(a.getTitle());
            String describe;
            if (a.getDescribeFragments() != null) {
                describe = Arrays.stream(a.getDescribeFragments()).collect(Collectors.joining("</br>"));
            } else if (a.getSqlContentFragments() != null) {
                describe = Arrays.stream(a.getSqlContentFragments()).collect(Collectors.joining("</br>"));
            } else if (a.getRemoteContentFragments() != null) {
                describe = Arrays.stream(a.getRemoteContentFragments()).collect(Collectors.joining("</br>"));
            } else {
                describe = a.getSubTitle();
            }

            SearchResult.Result result = new SearchResult.Result(a.getId(), title);
            result.description = describe;
            searchResult.addResult(result);
        });
        return searchResult;
    }

    private Map<String, Map<String, StaticSourceMethodInfo>> buildStaticMethodLookup(String appId) {
        Map<String, Map<String, StaticSourceMethodInfo>> lookup = new HashMap<>();
        if (!StringUtils.hasText(appId)) {
            return lookup;
        }
        List<StaticSourceInfo> staticInfos = staticInfoRepository.findByAppId(appId);
        for (StaticSourceInfo si : staticInfos) {
            if (si.getClassInfo() == null || si.getClassInfo().getMethodMaps() == null) continue;
            String className = si.getClassInfo().getClassName();
            Map<String, StaticSourceMethodInfo> methodMap = new HashMap<>();
            for (StaticSourceMethodInfo mInfo : si.getClassInfo().getMethodMaps().values()) {
                String mKey = mInfo.getMethodName() + "#" + mInfo.getMethodDesc();
                methodMap.put(mKey, mInfo);
            }
            lookup.put(className, methodMap);
        }
        return lookup;
    }

}
