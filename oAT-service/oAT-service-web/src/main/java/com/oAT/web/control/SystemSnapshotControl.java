package com.oAT.web.control;

import com.alibaba.druid.sql.SQLUtils;
import com.oAT.agent.model.*;
import com.oAT.web.common.CoverageMethodKeyUtil;
import com.oAT.web.common.DateUtil;
import com.oAT.web.control.entity.*;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.exceptions.BusinessException;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.LableType;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.UserVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/p/{projectId}/{appId}/snapshot/")
public class SystemSnapshotControl {

    final Logger logger = LoggerFactory.getLogger(SystemSnapshotControl.class);
    @Autowired
    SystemSnapshotService systemSnapshotService;

    @Autowired
    ProjectService projectService;

    @Autowired
    UserService userService;

    @Autowired
    AppService appService;

    @Autowired
    SnapshotService snapshotService;

    @Autowired
    CoverageService coverageService;

    @Autowired
    StaticInfoRepository staticInfoRepository;

    // 打开系统快照列表
    @RequestMapping("/list")
    public String openList(@PathVariable String projectId, @PathVariable String appId, String directoryId, String sort,
                           @SessionAttribute UserVo user, Model model) {
        AppVo app = appService.getApp(appId);
        directoryId = StringUtils.hasText(directoryId) ? directoryId : "root";
        final String finalDirId = directoryId;
        List<SnapshotDirectory> dirs =
                // 默认基于名称排序
                Arrays.stream(Optional.ofNullable(app.getSnapshotDirs()).orElse(new SnapshotDirectory[0])).filter(a -> a.getParentId().equals(finalDirId)).sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).collect(Collectors.toList());
        model.addAttribute("dirs", dirs);
        model.addAttribute("app", app);
        List<AppVo> apps = appService.getAppList(projectId);
        model.addAttribute("apps", apps);
        List<SystemSnapshot> snapshots = systemSnapshotService.findBy(projectId, appId, directoryId);
        //  排序
        snapshots = snapshots.stream().sorted((a, b) -> {
            if ("name".equals(sort)) {
                return a.getTitle().compareToIgnoreCase(b.getTitle());
            } else {// 默认排序 updateTime
                return a.parse(a.getVersionLastUpdate()).compareTo(b.parse(b.getVersionLastUpdate()));
            }
        }).collect(Collectors.toList());

        String loginName = user.getName();
        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);

        // 登录用户权限，原则是最小权限（访客）
        String loginNameRole = "visitor";
        for (ProjectMemberVo member : members) {
            if(loginName.equals(member.getMemberName())){
                loginNameRole = String.valueOf(member.getRole());
            }
        }

        model.addAttribute("loginNameRole", loginNameRole);

        model.addAttribute("sort", sort);
        model.addAttribute("snapshots", snapshots);
        model.addAttribute("currentDir", directoryId);
        model.addAttribute("dirTiers", appService.getDirectoryTiers(appId, directoryId));
        return "/snapshot/systemSnapshotList";
    }

    /**
     * 保存路径
     *
     * @param projectId
     * @param appId
     * @param dir
     * @return
     */
    @RequestMapping(value = "/directory", method = RequestMethod.POST)
    @ResponseBody
    public ResultNotified<Serializable> saveDirectory(@PathVariable String projectId, @PathVariable String appId, SnapshotDirectory dir) {
        //参数directory不能为空
        Assert.notNull(dir, "路径不能为空");
        //参数dir.name不能为空
        Assert.hasText(dir.getName(), "快照名称不能为空");
        appService.saveSnapshotDirectory(projectId, appId, dir);
        return new ResultNotified<>(true, "目录保存成功");
    }

    /**
     * 删除路径
     *
     * @param projectId
     * @param appId
     * @param directoryId
     * @return
     */
    @RequestMapping(value = "/directory", method = RequestMethod.DELETE)
    @ResponseBody
    public ResultNotified<Serializable> deleteDirectory(@PathVariable String projectId, @PathVariable String appId, Integer directoryId) {
        //参数directoryId不能为空
        Assert.isTrue(directoryId != null, "路径不能为空");
        try {
            appService.deleteSnapshotDirectory(projectId, appId, directoryId);
        } catch (BusinessException e) {
            logger.info("删除目录失败", e);
            return new ResultNotified<>(false, e.getMessage());
        }
        return new ResultNotified<>(true, "目录删除成功");
    }

    /**
     * 展示快照详情节点
     *
     * @param projectId
     * @param id
     * @param model
     * @return
     */
    @RequestMapping("/detail/{id}")
    public String open(@PathVariable String projectId, @PathVariable String id, Model model) {
        SystemSnapshot snapshot = systemSnapshotService.getById(id);
        model.addAttribute("snapshot", snapshot);
        // 加载所有标签
        List<LabelGroup.Label> labels = projectService.getLables(projectId, LableType.snapshot);
        model.addAttribute("labels", labels);

        String selectLabel = Arrays.stream(snapshot.getLabels() == null ? new String[0] : snapshot.getLabels()).collect(Collectors.joining(","));
        model.addAttribute("selectLabel", selectLabel);

        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);
        model.addAttribute("members", members);
        String principals = Arrays.stream(Optional.ofNullable(snapshot.getPrincipals()).orElse(new String[0])).collect(Collectors.joining(","));
        model.addAttribute("principals", principals);

        // 所属应用
        AppVo app = appService.getApp(snapshot.getAppId());
        model.addAttribute("app", app);

        // 构建动态信息
        // 变更日志
        List<String> ids = Arrays.stream(snapshot.getChangeLogs()).map(ChangeLog::getUserId).collect(Collectors.toList());
        // 评论
        ids.addAll(Arrays.stream(snapshot.getComments()).map(Comment::getUserId).collect(Collectors.toList()));
        Map<String, UserVo> userMap = userService.getUsers(ids.toArray(new String[0])).stream().collect(Collectors.toMap(UserVo::getId, k -> k, (v1
                , v2) -> v1));

        List<DynamicItem> dynamics = Arrays.stream(snapshot.getChangeLogs()).map(a -> {
            String time = DateUtil.timeDifference(a.parse(a.getTime()));
            String title = userMap.get(a.getUserId()).getName();
            DynamicItem dynamicItem = new DynamicItem(time, title, a.getType());
            dynamicItem.setDescribe(a.getContent());
            dynamicItem.setDate(a.parse(a.getTime()));
            return dynamicItem;
        }).collect(Collectors.toList());

        List<DynamicItem> comments = Arrays.stream(snapshot.getComments()).map(a -> {
            String time = DateUtil.timeDifference(a.parse(a.getTime()));
            String title = userMap.get(a.getUserId()).getName();
            DynamicItem dynamicItem = new DynamicItem(time, title, "comment");
            dynamicItem.setDescribe(a.getContent());
            dynamicItem.setDate(a.parse(a.getTime()));
            return dynamicItem;
        }).collect(Collectors.toList());

        dynamics.addAll(comments);
        // 排序
        dynamics = dynamics.stream().sorted((a, b) -> a.getDate().after(b.getDate()) ? 1 : -1).collect(Collectors.toList());
        model.addAttribute("dynamics", dynamics);

        // 构建堆栈节点列表
        Collection<TraceNode> traceNodes = snapshotService.getTraceNodes(snapshot.getTraceId());
        StackItemHelp stackItemHelp = new StackItemHelp(traceNodes);
        model.addAttribute("stackItems", stackItemHelp.buildItems());
        return "/snapshot/systemSnapshotDetail";
    }

    /**
     * 将 TraceNode 转换成前台堆栈列表树节点
     */
    @RequestMapping("/detail/graph/{id}")
    @ResponseBody
    public GraphView getGraphView(@PathVariable String projectId, @PathVariable String id) {
        SystemSnapshot snapshot = systemSnapshotService.getById(id);
        Collection<TraceNode> nodes = snapshotService.getTraceNodes(snapshot.getTraceId());
        GraphViewHelp graphViewHelp = new GraphViewHelp(nodes);
        return graphViewHelp.buildGraphView();
    }

    @RequestMapping("/node/{snapshotId}")
    public String openNodeDetail(@PathVariable String snapshotId, String traceId, String nodeId, Model model) {
        TraceNode node = snapshotService.getTraceNode(traceId, nodeId);
        if (node instanceof HttpTraceNode) {
            model.addAttribute("node", node);
            HttpTraceNode httpNode = (HttpTraceNode) node;
            List<Param> params = Stream.iterate(0, i -> i + 1).limit(httpNode.getRequestParamNames().length)// 生成队列数组
                    .map(i -> new Param(httpNode.getRequestParamNames()[i], httpNode.getRequestParamValues()[i])) // 转换成Param对象
                    .collect(Collectors.toList());
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

    /**
     * 更新快照标题
     */
    @RequestMapping("/update")
    @ResponseBody
    public ResultNotified<Serializable> update(@PathVariable String projectId, @SessionAttribute UserVo user, SystemSnapshot snapshot) {
        systemSnapshotService.saveBasic(projectId, user.getId(), snapshot);
        return new ResultNotified<>(true, "保存成功");
    }

    /**
     * 添加评论
     */
    @RequestMapping(value = "/addDescribe", method = RequestMethod.POST)
    @ResponseBody
    public ResultNotified<Serializable> addDescribe(@SessionAttribute UserVo user, String id, String content) {
        appService.addDescribe(id, user.getId(), content);
        return new ResultNotified<>(true, "添加成功");
    }

    /**
     * 删除评论
     */
    @RequestMapping(value = "/delDescribe")
    @ResponseBody
    public ResultNotified<Serializable> delDescribe(@SessionAttribute UserVo user, String id, String content, String dateTime) {
        appService.delDescribe(id, user.getId(), content, dateTime);
        return new ResultNotified<>(true, "评论删除成功");
    }

    /**
     * 删除快照
     */
    @RequestMapping("/doDelete")
    @ResponseBody
    public ResultNotified<Serializable> deleteSystemSnapshot(String id) {
        //当前快照id
        appService.deleteSnapshot(id);
        return new ResultNotified<>(true, "删除快照成功");
    }

    /**
     * 系统快照覆盖率报告
     */
    @RequestMapping("/report/{id}")
    public String systemSnapshotCodeReport(@PathVariable String projectId, @PathVariable String appId, @PathVariable String id, Model model) {
        SystemSnapshot snapshot = systemSnapshotService.getById(id);
        if (snapshot == null) {
            throw new RuntimeException("系统快照不存在");
        }

        // 所有的代码层级关系还是通过实时拉取 TraceNode 的 codeNodes 聚合，因为这部分数据量在单词快照内可控
        Map<String, Map<String, List<StackNodeVo>>> codeRelationships = new HashMap<>();
        Collection<TraceNode> traceNodes = snapshotService.getTraceNodes(snapshot.getTraceId());

        // 用于类级汇总
        Map<String, Set<String>> classMethods = new HashMap<>();
        Map<String, Set<Integer>> methodCoveredLines = new HashMap<>();
        Map<String, Set<Integer>> methodTotalLines = new HashMap<>();
        Map<String, Integer> methodComplexity = new HashMap<>();
        Map<String, Set<Integer>> methodTotalBranches = new HashMap<>();
        Map<String, Set<Integer>> methodCoveredBranches = new HashMap<>();
        Map<String, Set<String>> methodTotalBranchConditions = new HashMap<>();
        Map<String, Set<String>> methodCoveredBranchConditions = new HashMap<>();

        for (TraceNode traceNode : traceNodes) {
            if (traceNode instanceof HttpTraceNode) {
                HttpTraceNode httpTraceNode = (HttpTraceNode) traceNode;
                String requestUrl = httpTraceNode.getRequestUrl();

                StackNodeVo[] codeNodes = httpTraceNode.getCodeNodes();
                if (codeNodes != null) {
                    Map<String, List<StackNodeVo>> childNodes =
                            Arrays.stream(codeNodes).collect(Collectors.groupingBy(StackNodeVo::parentId));
                    codeRelationships.put(requestUrl, childNodes);

                    for (StackNodeVo node : codeNodes) {
                        String methodKey = node.getMethodName() + "#" + node.getMethodDescriptor();
                        classMethods.computeIfAbsent(node.getClassName(), k -> new HashSet<>()).add(methodKey);

                        if (node.getDoLines() != null) {
                            methodCoveredLines.computeIfAbsent(methodKey, k -> new HashSet<>()).addAll(node.getDoLines());
                        }

                        if (node.getExecuteBranch() != null) {
                            methodCoveredBranches.computeIfAbsent(methodKey, k -> new HashSet<>()).addAll(node.getExecuteBranch());
                        }
                        addBranchConditionKeys(methodCoveredBranchConditions, methodKey, node.getExecuteBranchConditionMap());
                    }
                }
            }
        }

        // 从全量静态数据补充总数
        List<StaticSourceInfo> staticInfos = staticInfoRepository.findByAppId(appId);
        for (StaticSourceInfo si : staticInfos) {
            if (si.getClassInfo() == null || si.getClassInfo().getMethodMaps() == null) continue;
            for (StaticSourceMethodInfo mInfo : si.getClassInfo().getMethodMaps().values()) {
                String mKey = mInfo.getMethodName() + "#" + mInfo.getMethodDesc();
                if (methodCoveredLines.containsKey(mKey) || methodCoveredBranches.containsKey(mKey)) {
                    methodTotalLines.computeIfAbsent(mKey, k -> new HashSet<>())
                            .addAll(mInfo.getMethodLineNumberMap() != null ? mInfo.getMethodLineNumberMap() : Collections.emptyList());
                    methodComplexity.put(mKey, mInfo.getCyclomaticComplexityMap() != null ? mInfo.getCyclomaticComplexityMap() : 0);
                    methodTotalBranches.computeIfAbsent(mKey, k -> new HashSet<>())
                            .addAll(mInfo.getBranchLineNumberSet() != null ? mInfo.getBranchLineNumberSet() : Collections.emptyList());
                    addBranchConditionKeys(methodTotalBranchConditions, mKey, mInfo.getBranchLineAndConditionNumberMap());
                    if (methodCoveredBranchConditions.containsKey(mKey)) {
                        Set<String> normalizedKeys = new LinkedHashSet<>();
                        addBranchConditionKeysToSet(normalizedKeys, mInfo.getBranchLineAndConditionNumberMap(),
                                decodeBranchConditionKeys(methodCoveredBranchConditions.get(mKey)));
                        methodCoveredBranchConditions.put(mKey, normalizedKeys);
                    }
                }
            }
        }

        // 计算汇总
        List<Map<String, Object>> classStats = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : classMethods.entrySet()) {
            String className = entry.getKey();
            Set<String> methods = entry.getValue();

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

        model.addAttribute("report", snapshot.getCoverageReport()); // 使用预计算的汇总
        model.addAttribute("classStats", classStats);
        model.addAttribute("codeRelationships", codeRelationships);
        model.addAttribute("projectId", projectId);
        model.addAttribute("appId", appId);
        model.addAttribute("snapshotId", id);
        model.addAttribute("fromSystemSnapshot", true); // 用于模板识别

        return "/snapshot/mySnapshotsCodeReport";
    }

    @RequestMapping("/report/calculate/{id}")
    @ResponseBody
    public ResultNotified<Serializable> calculateReport(@PathVariable String id) {
        systemSnapshotService.asyncCalculateCoverage(id);
        return new ResultNotified<>(true, "覆盖率计算任务已启动");
    }

    @RequestMapping("/report/status/{id}")
    @ResponseBody
    public ResultNotified<SystemSnapshot> getReportStatus(@PathVariable String id) {
        SystemSnapshot snapshot = systemSnapshotService.getById(id);
        return new ResultNotified<>(true, "获取成功", snapshot);
    }

    @RequestMapping("/report/code")
    public String systemSnapshotCodeView(@PathVariable String projectId, @PathVariable String appId, String snapshotId, String className, Model model) {
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        Collection<TraceNode> traceNodes = snapshotService.getTraceNodes(snapshot.getTraceId());

        ClassCoverageIndex aggregatedClassCov = new ClassCoverageIndex();
        aggregatedClassCov.setClassName(className);
        aggregatedClassCov.setAppId(appId);

        // 加载该类的全量静态数据
        List<StaticSourceInfo> staticInfos = staticInfoRepository.findByAppId(appId);
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

        Map<String, ClassCoverageIndex.MethodCoverageDetail> methodMap = new HashMap<>();

        for (TraceNode node : traceNodes) {
            if (node instanceof HttpTraceNode) {
                StackNodeVo[] codeNodes = ((HttpTraceNode) node).getCodeNodes();
                if (codeNodes != null) {
                    for (StackNodeVo sn : codeNodes) {
                        if (!sn.getClassName().equals(className)) continue;

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
                            coveredBranchConditionNumbers = normalizeCoveredBranchConditionNumbers(
                                    md.getTotalBranchConditionNumbers(), coveredBranchConditionNumbers);
                            md.setCoveredBranchConditionNumbers(coveredBranchConditionNumbers);
                            md.setCoveredBranchConditions(countBranchConditions(coveredBranchConditionNumbers));
                            md.setBranchRate(calculateBranchRate(md.getCoveredBranchConditions(), md.getTotalBranchConditions()));
                        }
                    }
                }
            }
        }

        aggregatedClassCov.setMethods(new ArrayList<>(methodMap.values()));
        String coloredSource = coverageService.getColoredSource(appId, aggregatedClassCov);
        model.addAttribute("classCov", aggregatedClassCov);
        model.addAttribute("coloredSource", coloredSource);
        model.addAttribute("className", className);

        return "/snapshot/snapshotCodeView";
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

    private Map<String, List<Integer>> normalizeCoveredBranchConditionNumbers(Map<String, List<Integer>> total,
                                                                              Map<String, List<Integer>> covered) {
        if (total == null || total.isEmpty() || covered == null || covered.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, List<Integer>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : total.entrySet()) {
            List<Integer> totalValues = entry.getValue();
            if (totalValues == null || totalValues.isEmpty()) {
                continue;
            }
            Set<Integer> allowed = new LinkedHashSet<>(totalValues);
            List<Integer> coveredValues = covered.get(entry.getKey());
            if (coveredValues == null || coveredValues.isEmpty()) {
                continue;
            }
            LinkedHashSet<Integer> matched = new LinkedHashSet<>();
            for (Integer value : coveredValues) {
                if (value != null && allowed.contains(value)) {
                    matched.add(value);
                }
            }
            if (!matched.isEmpty()) {
                normalized.put(entry.getKey(), new ArrayList<>(matched));
            }
        }
        return normalized;
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

    private void addBranchConditionKeysToSet(Set<String> target,
                                             Map<String, List<Integer>> allowedBranchConditionNumbers,
                                             Map<String, List<Integer>> branchConditionNumbers) {
        Map<String, List<Integer>> effective = normalizeCoveredBranchConditionNumbers(
                allowedBranchConditionNumbers, branchConditionNumbers);
        for (Map.Entry<String, List<Integer>> entry : effective.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            for (Integer conditionNumber : entry.getValue()) {
                if (conditionNumber != null) {
                    target.add(entry.getKey() + "#" + conditionNumber);
                }
            }
        }
    }

    private Map<String, List<Integer>> decodeBranchConditionKeys(Set<String> keys) {
        Map<String, List<Integer>> decoded = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty()) {
            return decoded;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            int split = key.lastIndexOf('#');
            if (split <= 0 || split >= key.length() - 1) {
                continue;
            }
            try {
                int conditionNumber = Integer.parseInt(key.substring(split + 1));
                decoded.computeIfAbsent(key.substring(0, split), k -> new ArrayList<>()).add(conditionNumber);
            } catch (NumberFormatException ignore) {
            }
        }
        return decoded;
    }

}
