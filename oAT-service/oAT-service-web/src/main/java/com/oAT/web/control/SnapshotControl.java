package com.oAT.web.control;

import com.alibaba.druid.sql.SQLUtils;
import com.oAT.agent.model.*;
import com.oAT.web.common.CoverageMethodKeyUtil;
import com.oAT.web.control.entity.GraphView;
import com.oAT.web.control.entity.Param;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.control.entity.StackItem;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.esDao.entity.Snapshot;
import com.oAT.web.esDao.entity.StaticSourceInfo;
import com.oAT.web.esDao.entity.StaticSourceMethodInfo;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/p/{projectId}/snapshot")
public class SnapshotControl {

    static final Logger logger = LoggerFactory.getLogger(SnapshotControl.class);

    @Autowired
    private SnapshotService snapshotService;
    @Autowired
    ClientSessionService clientSessionService;

    @Autowired
    ProjectService projectService;

    @Autowired
    UserService userService;

    @Autowired
    private AppService appService;

    @Autowired
    private VersionService versionService;

    @Autowired
    private CoverageService coverageService;

    @Autowired
    private StaticInfoRepository staticInfoRepository;

    @RequestMapping("/save")
    @ResponseBody
    public ResultNotified<SnapshotVo> doSave(@PathVariable String projectId, @SessionAttribute UserVo user, HttpSession session, Snapshot snapshot) {
        Assert.notNull(snapshot, "参数snapshot不能为空");
        Assert.notNull(snapshot.getTraceId(), "参数'traceId 不能为空'");
        Assert.notNull(snapshot.getProjectId(), "参数'projectId 不能为空'");
        snapshot.setProjectId(projectId);
        snapshot.setCreateUser(user.getId());
        Object sessionNodes = session.getAttribute("model-" + snapshot.getTraceId());
        Map<String, TraceNode> nodes = new LinkedHashMap<>();
        if (sessionNodes instanceof Map) {
            Map<?, ?> rawNodes = (Map<?, ?>) sessionNodes;
            for (Map.Entry<?, ?> entry : rawNodes.entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof TraceNode) {
                    nodes.put((String) entry.getKey(), (TraceNode) entry.getValue());
                }
            }
        }
        Assert.notNull(nodes, "参数'traceId 找不到'");
        Assert.isTrue(!nodes.isEmpty(), "参数'traceId 找不到'");

        SnapshotVo vo = snapshotService.addSnapshot(snapshot, nodes.values());
        ResultNotified<SnapshotVo> result = new ResultNotified<>(true, "快照保存成功");
        result.setData(vo);
        return result;
    }

    /**
     * 打开我的快照列表 显示全部
     *
     * @return
     */
    @RequestMapping("/list")
    public String openList(@PathVariable String projectId, @SessionAttribute UserVo user, String[] labels, String sort, Model model) {

        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, user.getId(), StringUtils.hasText(sort) ? sort : null);

        // 基于标签过滤
        if (ArrayUtils.isNotEmpty(labels)) {
            for (SnapshotVo snapshotVo : snapshots.toArray(new SnapshotVo[0])) {
                if (snapshotVo.getLabels() == null || !in(snapshotVo.getLabels(), labels)) {
                    snapshots.remove(snapshotVo);
                }
            }
        }

        model.addAttribute("snapshots", snapshots);
        List<LabelGroup.Label> snapshotLabels = projectService.getLables(projectId, LableType.snapshot);
        model.addAttribute("snapshotLabels", snapshotLabels);
        model.addAttribute("filterLabels", StringUtils.arrayToDelimitedString(labels, ","));
        model.addAttribute("sort", sort);
        return "/snapshot/snapshotList";
    }

    @RequestMapping("/my")
    public String mySnapshotList(@PathVariable String projectId, @SessionAttribute UserVo user, String[] labels, String sort, Model model) {
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, user.getId(), StringUtils.hasText(sort) ? sort : null);
        // 基于标签过滤
        if (ArrayUtils.isNotEmpty(labels)) {
            for (SnapshotVo snapshotVo : snapshots.toArray(new SnapshotVo[0])) {
                if (snapshotVo.getLabels() == null || !in(snapshotVo.getLabels(), labels)) {
                    snapshots.remove(snapshotVo);
                }
            }
        }
        model.addAttribute("snapshots", snapshots);
        List<LabelGroup.Label> snapshotLabels = projectService.getLables(projectId, LableType.snapshot);
        model.addAttribute("snapshotLabels", snapshotLabels);
        model.addAttribute("filterLabels", StringUtils.arrayToDelimitedString(labels, ","));
        model.addAttribute("sort", sort);

        return "/snapshot/mySnapshot";
    }

    private boolean in(String[] source, String[] target) {
        for (String s : source) {
            if (s == null) {
                continue;
            }
            for (String t : target) {
                if (s.equals(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    @RequestMapping("/mySnapshotsCodeReport")
    public String mySnapshotsCodeReport(@PathVariable String projectId, @SessionAttribute UserVo user, String sort, Model model) {
        Map<String, Map<String, List<StackNodeVo>>> codeRelationships = new HashMap<>();
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, user.getId(), StringUtils.hasText(sort) ? sort : null);

        // 用于计算聚合指标
        long totalMethods = 0;
        long coveredMethods = 0;
        long totalLines = 0;
        long coveredLines = 0;
        long totalBranches = 0;
        long coveredBranches = 0;
        int totalComplexity = 0;

        // 全局去重用，确保同一方法在不同快照中被统计多次时，覆盖行能合并
        // Key: className + methodDescriptor
        Map<String, Set<Integer>> methodCoveredLines = new HashMap<>();
        Map<String, Set<Integer>> methodTotalLines = new HashMap<>();
        Map<String, Integer> methodComplexity = new HashMap<>();
        Map<String, Set<Integer>> methodTotalBranches = new HashMap<>();
        Map<String, Set<Integer>> methodCoveredBranches = new HashMap<>();
        Map<String, Set<String>> methodTotalBranchConditions = new HashMap<>();
        Map<String, Set<String>> methodCoveredBranchConditions = new HashMap<>();

        // 用于类级汇总
        Map<String, Set<String>> classMethods = new HashMap<>();
        Map<String, String> classToAppId = new HashMap<>();

        for (SnapshotVo snapshot : snapshots) {
            String traceId = snapshot.getTraceId();
            //得到代码关系
            TraceNode traceNode = snapshotService.getTraceNode(traceId, "0");
            if (traceNode instanceof HttpTraceNode) {
                HttpTraceNode httpTraceNode = (HttpTraceNode) traceNode;
                String requestUrl = httpTraceNode.getRequestUrl();

                StackNodeVo[] codeNodes = httpTraceNode.getCodeNodes();
                if (codeNodes != null) {
                    Map<String, List<StackNodeVo>> childNodes =
                            Arrays.stream(codeNodes).collect(Collectors.groupingBy(StackNodeVo::parentId));
                    codeRelationships.put(requestUrl, childNodes);

                    // 获取 appId
                    String currentAppId = snapshot.getAppId();
                    if (!StringUtils.hasText(currentAppId) && traceNode.getApp() != null) {
                        currentAppId = traceNode.getApp().getAppId();
                    }

                    if (StringUtils.hasText(currentAppId)) {
                        classToAppId.putIfAbsent(null, currentAppId); // 用于后续静态数据加载标记
                    }

                    for (StackNodeVo node : codeNodes) {
                        String methodKey = node.getMethodName() + "#" + node.getMethodDescriptor();
                        classMethods.computeIfAbsent(node.getClassName(), k -> new HashSet<>()).add(methodKey);

                        if (StringUtils.hasText(currentAppId)) {
                            classToAppId.putIfAbsent(node.getClassName(), currentAppId);
                        }

                        // 行覆盖
                        if (node.getDoLines() != null) {
                            methodCoveredLines.computeIfAbsent(methodKey, k -> new HashSet<>()).addAll(node.getDoLines());
                        }

                        // 分支覆盖
                        if (node.getExecuteBranch() != null) {
                            methodCoveredBranches.computeIfAbsent(methodKey, k -> new HashSet<>()).addAll(node.getExecuteBranch());
                        }
                        addBranchConditionKeys(methodCoveredBranchConditions, methodKey, node.getExecuteBranchConditionMap());
                    }
                }
            }
        }

        // 从全量静态数据补充总数
        for (String appId : new HashSet<>(classToAppId.values())) {
            if (!StringUtils.hasText(appId)) continue;
            List<StaticSourceInfo> staticInfos = staticInfoRepository.findByAppId(appId);
            for (StaticSourceInfo si : staticInfos) {
                if (si.getClassInfo() == null || si.getClassInfo().getMethodMaps() == null) continue;
                for (StaticSourceMethodInfo mInfo : si.getClassInfo().getMethodMaps().values()) {
                    String mKey = mInfo.getMethodName() + "#" + mInfo.getMethodDesc();
                    // 只统计被覆盖过的方法的总数
                    if (methodCoveredLines.containsKey(mKey) || methodCoveredBranches.containsKey(mKey)) {
                        methodTotalLines.computeIfAbsent(mKey, k -> new HashSet<>())
                                .addAll(mInfo.getMethodLineNumberMap() != null ? mInfo.getMethodLineNumberMap() : Collections.emptyList());
                        methodComplexity.put(mKey, mInfo.getCyclomaticComplexityMap() != null ? mInfo.getCyclomaticComplexityMap() : 0);
                        methodTotalBranches.computeIfAbsent(mKey, k -> new HashSet<>())
                                .addAll(mInfo.getBranchLineNumberSet() != null ? mInfo.getBranchLineNumberSet() : Collections.emptyList());
                        addBranchConditionKeys(methodTotalBranchConditions, mKey, mInfo.getBranchLineAndConditionNumberMap());
                    }
                }
            }
        }

        // 计算汇总
        List<Map<String, Object>> classStats = new ArrayList<>();
        totalMethods = methodTotalLines.size();
        for (String mKey : methodTotalLines.keySet()) {
            totalLines += methodTotalLines.get(mKey).size();
            coveredLines += methodCoveredLines.getOrDefault(mKey, Collections.emptySet()).size();
            if (methodCoveredLines.containsKey(mKey) && !methodCoveredLines.get(mKey).isEmpty()) {
                coveredMethods++;
            }
            totalComplexity += methodComplexity.getOrDefault(mKey, 0);
            totalBranches += methodTotalBranches.getOrDefault(mKey, Collections.emptySet()).size();
            coveredBranches += methodCoveredBranches.getOrDefault(mKey, Collections.emptySet()).size();
        }
        long totalBranchConditions = 0;
        long coveredBranchConditions = 0;
        for (String mKey : methodTotalLines.keySet()) {
            totalBranchConditions += methodTotalBranchConditions.getOrDefault(mKey, Collections.emptySet()).size();
            coveredBranchConditions += methodCoveredBranchConditions.getOrDefault(mKey, Collections.emptySet()).size();
        }

        // 生成类级详细统计，供页面 Table 展示 (参考 CoverageReportIndex 结构)
        for (Map.Entry<String, Set<String>> entry : classMethods.entrySet()) {
            String className = entry.getKey();
            Set<String> methods = entry.getValue();
            String classAppId = classToAppId.get(className);

            long cTotalMethods = methods.size();
            long cCoveredMethods = 0;
            long cTotalLines = 0;
            long cCoveredLines = 0;
            long cTotalBranches = 0;
            long cCoveredBranches = 0;
            long cTotalBranchConditions = 0;
            long cCoveredBranchConditions = 0;
            int cTotalComplexity = 0;

            for (String mKey : methods) {
                cTotalLines += methodTotalLines.get(mKey).size();
                cCoveredLines += methodCoveredLines.getOrDefault(mKey, Collections.emptySet()).size();
                if (methodCoveredLines.containsKey(mKey) && !methodCoveredLines.get(mKey).isEmpty()) {
                    cCoveredMethods++;
                }
                cTotalComplexity += methodComplexity.getOrDefault(mKey, 0);
                cTotalBranches += methodTotalBranches.getOrDefault(mKey, Collections.emptySet()).size();
                cCoveredBranches += methodCoveredBranches.getOrDefault(mKey, Collections.emptySet()).size();
                cTotalBranchConditions += methodTotalBranchConditions.getOrDefault(mKey, Collections.emptySet()).size();
                cCoveredBranchConditions += methodCoveredBranchConditions.getOrDefault(mKey, Collections.emptySet()).size();
            }

            Map<String, Object> cStat = new HashMap<>();
            cStat.put("className", className);
            cStat.put("appId", classAppId);
            cStat.put("totalMethods", cTotalMethods);
            cStat.put("coveredMethods", cCoveredMethods);
            cStat.put("totalLines", cTotalLines);
            cStat.put("coveredLines", cCoveredLines);
            cStat.put("totalBranches", cTotalBranches);
            cStat.put("coveredBranches", cCoveredBranches);
            cStat.put("branchRate", cTotalBranchConditions > 0 ? cCoveredBranchConditions * 100.0 / cTotalBranchConditions : 0);
            cStat.put("totalComplexity", cTotalComplexity);
            classStats.add(cStat);
        }

        // 获取一个代表性的 appId (如果有的话)
        String appId = snapshots.isEmpty() ? null : snapshots.get(0).getAppId();

        // 模拟一个 CoverageReportIndex 对象传递给模板以复用样式
        CoverageReportIndex summary = new CoverageReportIndex();
        summary.setTotalMethods(totalMethods);
        summary.setCoveredMethods(coveredMethods);
        summary.setTotalLines(totalLines);
        summary.setCoveredLines(coveredLines);
        summary.setTotalBranches(totalBranches);
        summary.setCoveredBranches(coveredBranches);
        summary.setTotalBranchConditions(totalBranchConditions);
        summary.setCoveredBranchConditions(coveredBranchConditions);
        summary.setTotalComplexity(totalComplexity);
        // 类覆盖率在快照中较难准确统计全量（因为不知道没碰到的类），这里取触达过的类
        summary.setTotalClasses(classMethods.size());
        summary.setCoveredClasses(summary.getTotalClasses());

        model.addAttribute("report", summary);
        model.addAttribute("classStats", classStats);
        model.addAttribute("codeRelationships", codeRelationships);
        model.addAttribute("codeRelatSize", codeRelationships.size());
        model.addAttribute("projectId", projectId);
        model.addAttribute("appId", appId);

        return "/snapshot/mySnapshotsCodeReport";
    }

    @RequestMapping("/my/code")
    public String snapshotCodeView(@PathVariable String projectId, String appId, String className, @SessionAttribute UserVo user, Model model) {
        // 1. 获取该用户在该项目下的所有快照
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, user.getId(), null);

        // 2. 聚合该类的覆盖率数据 (参考 ClassCoverageIndex)
        ClassCoverageIndex aggregatedClassCov = new ClassCoverageIndex();
        aggregatedClassCov.setClassName(className);
        aggregatedClassCov.setAppId(appId);

        // 加载该类的全量静态数据
        List<StaticSourceInfo> staticInfos = StringUtils.hasText(appId) ? staticInfoRepository.findByAppId(appId) : Collections.emptyList();
        Map<String, StaticSourceMethodInfo> classStaticMethods = new HashMap<>();
        for (StaticSourceInfo si : staticInfos) {
            if (si.getClassInfo() != null && className.equals(si.getClassInfo().getClassName()) && si.getClassInfo().getMethodMaps() != null) {
                for (StaticSourceMethodInfo mInfo : si.getClassInfo().getMethodMaps().values()) {
                    String mKey = CoverageMethodKeyUtil.buildMethodKey(mInfo.getMethodName(), mInfo.getMethodDesc());
                    classStaticMethods.put(mKey, mInfo);
                }
                break;
            }
        }

        Map<String, ClassCoverageIndex.MethodCoverageDetail> methodMap = new HashMap<>(); // name#desc -> detail

        for (SnapshotVo snap : snapshots) {
            String traceId = snap.getTraceId();
            TraceNode traceNode = snapshotService.getTraceNode(traceId, "0");
            if (traceNode instanceof HttpTraceNode) {
                StackNodeVo[] codeNodes = ((HttpTraceNode) traceNode).getCodeNodes();
                if (codeNodes != null) {
                    for (StackNodeVo sn : codeNodes) {
                        if (!sn.getClassName().equals(className)) continue;

                        // 如果进入页面时 appId 为空（如历史快照未记录 appId），则从当前包含该类的链路节点中推断 appId
                        if (!StringUtils.hasText(appId) && traceNode.getApp() != null) {
                            appId = traceNode.getApp().getAppId();
                        }

                        String methodKey = CoverageMethodKeyUtil.buildMethodKey(sn.getMethodName(), sn.getMethodDescriptor());
                        ClassCoverageIndex.MethodCoverageDetail md = methodMap.computeIfAbsent(methodKey, k -> {
                            ClassCoverageIndex.MethodCoverageDetail newMd = new ClassCoverageIndex.MethodCoverageDetail();
                            newMd.setMethodName(sn.getMethodName());
                            newMd.setMethodDesc(sn.getMethodDescriptor());
                            // 从全量静态数据获取总数
                            StaticSourceMethodInfo staticMethod = classStaticMethods.get(methodKey);
                            List<Integer> totalLines = staticMethod != null && staticMethod.getMethodLineNumberMap() != null
                                    ? staticMethod.getMethodLineNumberMap() : Collections.emptyList();
                            newMd.setTotalLineNumbers(new ArrayList<>(totalLines));
                            newMd.setTotalLines(totalLines.size());
                            newMd.setTotalBranches(staticMethod != null && staticMethod.getTotalBranchCount() != null
                                    ? staticMethod.getTotalBranchCount() : 0);
                            newMd.setTotalBranchConditionNumbers(staticMethod != null
                                    ? staticMethod.getBranchLineAndConditionNumberMap() : null);
                            newMd.setTotalBranchConditions(countBranchConditions(newMd.getTotalBranchConditionNumbers()));
                            newMd.setCoveredBranchConditionNumbers(new LinkedHashMap<>());
                            newMd.setCoveredBranchConditions(0);
                            newMd.setBranchRate(0.0);
                            newMd.setComplexity(staticMethod != null && staticMethod.getCyclomaticComplexityMap() != null
                                    ? staticMethod.getCyclomaticComplexityMap() : 0);
                            newMd.setCoveredLineNumbers(new ArrayList<>());
                            newMd.setCoveredBranchIds(new ArrayList<>());
                            return newMd;
                        });

                        if (sn.getDoLines() != null) {
                            Set<Integer> covered = new HashSet<>(md.getCoveredLineNumbers());
                            covered.addAll(sn.getDoLines());
                            md.setCoveredLineNumbers(new ArrayList<>(covered));
                            md.setCoveredLines(md.getCoveredLineNumbers().size());
                            md.setCovered(md.getCoveredLines() > 0);
                        }
                        if (sn.getExecuteBranch() != null) {
                            Set<Integer> coveredBranchIds = new LinkedHashSet<>(md.getCoveredBranchIds());
                            coveredBranchIds.addAll(sn.getExecuteBranch());
                            md.setCoveredBranchIds(new ArrayList<>(coveredBranchIds));
                            md.setCoveredBranches(md.getCoveredBranchIds().size());
                        }
                        if (sn.getExecuteBranchConditionMap() != null) {
                            Map<String, List<Integer>> coveredBranchConditionNumbers = mergeBranchConditionNumbers(
                                    md.getCoveredBranchConditionNumbers(), sn.getExecuteBranchConditionMap());
                            md.setCoveredBranchConditionNumbers(coveredBranchConditionNumbers);
                            md.setCoveredBranchConditions(countBranchConditions(coveredBranchConditionNumbers));
                            md.setBranchRate(calculateBranchRate(md.getCoveredBranchConditions(), md.getTotalBranchConditions()));
                        }
                    }
                }
            }
        }

        List<ClassCoverageIndex.MethodCoverageDetail> methods = new ArrayList<>(methodMap.values());
        aggregatedClassCov.setMethods(methods);
        aggregatedClassCov.setTotalMethods(methods.size());
        aggregatedClassCov.setCoveredMethods((int) methods.stream().filter(ClassCoverageIndex.MethodCoverageDetail::isCovered).count());
        aggregatedClassCov.setTotalLines(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getTotalLines).sum());
        aggregatedClassCov.setCoveredLines(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getCoveredLines).sum());

        // 3. 先排序方法（页面渲染列表的顺序），然后应用着色逻辑，这样生成的锚点与页面方法列表索引一致
        methods.sort((a, b) -> {
            double rateA = a.getTotalLines() > 0 ? (double) a.getCoveredLines() / a.getTotalLines() : 0;
            double rateB = b.getTotalLines() > 0 ? (double) b.getCoveredLines() / b.getTotalLines() : 0;
            if (rateA != rateB) return Double.compare(rateB, rateA);
            return a.getMethodName().compareTo(b.getMethodName());
        });

        // 应用着色逻辑 (复用通用的 getColoredSource)
        String coloredSource = coverageService.getColoredSource(appId, aggregatedClassCov);

        model.addAttribute("coloredSource", coloredSource);
        model.addAttribute("classCov", aggregatedClassCov);
        model.addAttribute("className", className);
        model.addAttribute("projectId", projectId);
        model.addAttribute("appId", appId);

        AppVo appVo = appService.getApp(appId);
        model.addAttribute("appName", appVo != null ? appVo.getName() : appId);
        model.addAttribute("project", projectService.getProject(projectId));

        // 获取最新版本信息
        VersionItemVo lastVersion = versionService.getLastVersionItem(projectId, appId);
        model.addAttribute("lastVersion", lastVersion);

        return "snapshot/snapshotCodeView";
    }

    @RequestMapping("/node")
    public String openNodeDetail(String traceId, String nodeId, Model model) {
        TraceNode node = snapshotService.getTraceNode(traceId, nodeId);
        if (node instanceof HttpTraceNode) {
            model.addAttribute("node", node);
            HttpTraceNode httpNode = (HttpTraceNode) node;
            List<Param> params = Stream.iterate(0, i -> i + 1)
                    //生成队列数组
                    .limit(httpNode.getRequestParamNames().length)
                    //转换成Param对象
                    .map(i -> new Param(httpNode.getRequestParamNames()[i], httpNode.getRequestParamValues()[i])).collect(Collectors.toList());
            model.addAttribute("params", params);
            return "snapshot/webNodeDetail";
        } else if (node instanceof SqlTraceNode) {
            model.addAttribute("node", node);
            if (((SqlTraceNode) node).getSql() != null) {
                String formatSql = SQLUtils.format(((SqlTraceNode) node).getSql(), ((SqlTraceNode) node).getDatabase().getType());
                model.addAttribute("sql", formatSql);
            }
            return "snapshot/sqlNodeDetail";
        } else if (node instanceof CKSqlTraceNode) {
            model.addAttribute("node", node);
            if (((CKSqlTraceNode) node).getSql() != null) {
                String formatSql = SQLUtils.format(((CKSqlTraceNode) node).getSql(), ((CKSqlTraceNode) node).getDatabase().getType());
                model.addAttribute("sql", formatSql);
            }
            return "snapshot/sqlNodeDetail";
        } else if (node instanceof DubboTraceNode) {
            model.addAttribute("node", node);
            URI uri = GraphViewHelp.buildURI(node, ((DubboTraceNode) node).getRemoteUrl());
            model.addAttribute("remoteIp", uri.getHost());
            return "snapshot/dubboNodeDetail";
        } else if (node instanceof RedisTraceNode) {
            model.addAttribute("connectionName", ((RedisTraceNode) node).getHost() + "@" + ((RedisTraceNode) node).getPort());
            model.addAttribute("cmd", ((RedisTraceNode) node).getCmd().replace("<", "&lt;").replace(">", "&gt;"));
            return "snapshot/redisNodeDetail";
        }
        return null;
    }

    @RequestMapping("/detail/graph/{traceId}")
    @ResponseBody
    public GraphView getGraphView(@PathVariable String projectId, @PathVariable String traceId) {
        Collection<TraceNode> nodes = snapshotService.getTraceNodes(traceId);
        GraphViewHelp graphViewHelp = new GraphViewHelp(nodes);
        return graphViewHelp.buildGraphView();
    }

    @RequestMapping("/detail/stack/{traceId}")
    public String openTraceTable(@PathVariable String projectId, @PathVariable String traceId, Model model) {
        Collection<TraceNode> nodes = snapshotService.getTraceNodes(traceId);
        StackItemHelp help = new StackItemHelp(nodes);
        List<StackItem> stacks = help.buildItems();
        model.addAttribute("stacks", stacks);
        return "/snapshot/stackTable";
    }

    @RequestMapping("/edit")
    public String openEdit(@PathVariable String projectId, @SessionAttribute UserVo user, String id, Model model) {
        SnapshotVo snapshotVo = snapshotService.get(id);
        model.addAttribute("snapshot", snapshotVo);
        model.addAttribute("snapshotLabel", StringUtils.arrayToDelimitedString(snapshotVo.getLabels(), ","));
        List<LabelGroup.Label> labels = projectService.getLables(projectId, LableType.snapshot);
        model.addAttribute("labels", labels);
        return "/snapshot/snapshotEdit";
    }

    /**
     * 打开快照节点详情
     *
     * @return
     */
    @RequestMapping("/detail/{id}")
    public String openDetail(@PathVariable String projectId, @PathVariable String id, Model model, HttpServletRequest request) {
        SnapshotVo snapshotVo = snapshotService.get(id);
        model.addAttribute("snapshot", snapshotVo);
        UserVo user = userService.getUser(snapshotVo.getCreateUser());
        model.addAttribute("createUser", user);
        if (ArrayUtils.isNotEmpty(snapshotVo.getLabels())) {
            model.addAttribute("labels", projectService.getLables(projectId, LableType.snapshot, snapshotVo.getLabels()));
        } else {
            model.addAttribute("labels", new ArrayList<>());
        }
        // 跳转至快照共享页
        Boolean share = (Boolean) request.getAttribute("_share");
        if (BooleanUtils.isTrue(share)) {
            return "/snapshot/shareSnapshotDetail";
        }
        return "/snapshot/snapshotDetail";
    }

    @RequestMapping("/doDelete")
    @ResponseBody
    public ResultNotified<Serializable> doDelete(@PathVariable String projectId, @SessionAttribute UserVo user, String id) {
        snapshotService.deleteById(id);
        return new ResultNotified<>(true, "快照删除成功");
    }


    @RequestMapping("/doUpdate")
    @ResponseBody
    public ResultNotified<Serializable> doUpdate(@PathVariable String projectId, String id, Snapshot snapshot) {
        snapshotService.doUpdate(id, snapshot);
        return new ResultNotified<>(true, "快照更新成功");
    }

    private Map<String, List<Integer>> mergeBranchConditionNumbers(Map<String, List<Integer>> current,
                                                                   Map<String, List<Integer>> incoming) {
        Map<String, LinkedHashSet<Integer>> merged = new LinkedHashMap<>();
        appendBranchConditionNumbers(merged, current);
        appendBranchConditionNumbers(merged, incoming);
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<Integer>> entry : merged.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    private void appendBranchConditionNumbers(Map<String, LinkedHashSet<Integer>> target,
                                              Map<String, List<Integer>> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<Integer>> entry : source.entrySet()) {
            LinkedHashSet<Integer> values = target.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>());
            if (entry.getValue() != null) {
                values.addAll(entry.getValue());
            }
        }
    }

    private int countBranchConditions(Map<String, List<Integer>> branchConditionNumbers) {
        if (branchConditionNumbers == null || branchConditionNumbers.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (List<Integer> values : branchConditionNumbers.values()) {
            total += values == null ? 0 : new LinkedHashSet<>(values).size();
        }
        return total;
    }

    private double calculateBranchRate(int coveredBranchConditions, int totalBranchConditions) {
        return totalBranchConditions > 0 ? (double) coveredBranchConditions / totalBranchConditions * 100 : 0.0;
    }

    private void addBranchConditionKeys(Map<String, Set<String>> target,
                                        String methodKey,
                                        Map<String, List<Integer>> branchConditionNumbers) {
        if (branchConditionNumbers == null || branchConditionNumbers.isEmpty()) {
            return;
        }
        Set<String> keys = target.computeIfAbsent(methodKey, key -> new LinkedHashSet<>());
        for (Map.Entry<String, List<Integer>> entry : branchConditionNumbers.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            for (Integer conditionNumber : entry.getValue()) {
                if (conditionNumber != null) {
                    keys.add(entry.getKey() + "#" + conditionNumber);
                }
            }
        }
    }

    @RequestMapping("/getTraceGraph")
    @ResponseBody
    public GraphView getTraceGraph(String traceId, HttpSession session) {
        HashMap<String, TraceNode> nodes = new HashMap<>();
        Collection<TraceNode> list = snapshotService.getTraceNodes(traceId);
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("未找到Trace Node traceId=" + traceId);
        }
        for (TraceNode node : list) {
            nodes.put(node.getTraceNodeId(), node);
        }
        return new TraceGraphParse(nodes).getGraphView();
    }


    // 共享快照，开启
    @RequestMapping("openShare/{id}")
    @ResponseBody
    public ResultNotified<Serializable> doShareSnapshot(@SessionAttribute UserVo user, @PathVariable String id) {
        snapshotService.setShareState(user.getId(), id, true);
        return new ResultNotified<>(true);
    }

    // 共享快照，关闭
    @RequestMapping("closeShare/{id}")
    @ResponseBody
    public ResultNotified<Serializable> doCloseSnapshot(@SessionAttribute UserVo user, @PathVariable String id) {
        snapshotService.setShareState(user.getId(), id, false);
        return new ResultNotified<>(true);
    }

}
