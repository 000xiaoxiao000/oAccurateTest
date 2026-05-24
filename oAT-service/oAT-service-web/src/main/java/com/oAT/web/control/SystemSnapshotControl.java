package com.oAT.web.control;

import com.oAT.web.config.FrontendProperties;
import com.oAT.agent.model.*;
import com.oAT.web.common.CoverageMethodKeyUtil;
import com.oAT.web.common.DateUtil;
import com.oAT.web.control.entity.*;
import com.oAT.web.domain.RemoteCallResolver;
import com.oAT.web.esDao.ApiEndpointRepository;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.exceptions.BusinessException;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.LableType;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.UserVo;
import com.oAT.server.model.ClientSessionVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/p/{projectId}/{appId}/snapshot/")
public class SystemSnapshotControl {

    @Autowired
    FrontendProperties frontendProperties;


    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

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
    ClientSessionService clientSessionService;

    @Autowired
    StaticInfoRepository staticInfoRepository;

    @Autowired
    UsecaseService usecaseService;

    @Autowired
    private ApiEndpointAnalysisService apiEndpointAnalysisService;

    @Autowired
    private ApiEndpointRepository apiEndpointRepository;

    // 打开系统快照列表
    @RequestMapping("/list")
    public String openList(@PathVariable String projectId, @PathVariable String appId, String directoryId, String sort,
                           String keyword, String missingSnapshotId, @SessionAttribute UserVo user, Model model) {
        StringBuilder target = new StringBuilder("/p/")
                .append(projectId)
                .append("/apps/")
                .append(appId)
                .append("/snapshots");
        List<String> query = new ArrayList<>();
        if (StringUtils.hasText(directoryId) && !"root".equals(directoryId)) {
            query.add("directoryId=" + directoryId);
        }
        if (StringUtils.hasText(sort)) {
            query.add("sort=" + sort);
        }
        if (StringUtils.hasText(keyword)) {
            query.add("keyword=" + keyword);
        }
        if (!query.isEmpty()) {
            target.append("?").append(String.join("&", query));
        }
        return "redirect:" + frontendProperties.url(target.toString());
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
    public String open(@PathVariable String projectId, @PathVariable String appId, @PathVariable String id,
                       @RequestParam(value = "tab", required = false) String tab,
                       Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/snapshots/" + id);
    }

    @RequestMapping("/{id}/usecase/bind")
    @ResponseBody
    public ResultNotified<Integer> bindUsecases(@PathVariable String projectId,
                                                @PathVariable String appId,
                                                @PathVariable String id,
                                                @SessionAttribute UserVo user,
                                                String[] usecaseIds) {
        try {
            SystemSnapshot snapshot = systemSnapshotService.getById(id);
            Assert.notNull(snapshot, "找不到系统快照 id=" + id);
            Assert.isTrue(projectId.equals(snapshot.getProjectId()), "系统快照不属于当前项目");
            usecaseService.bindSystemSnapshotToUsecases(projectId, user.getId(), id, usecaseIds);
            ResultNotified<Integer> result = new ResultNotified<>(true, "测试用例关联已更新");
            result.setData(usecaseIds == null ? 0 : usecaseIds.length);
            return result;
        } catch (Exception e) {
            logger.warn("更新系统快照关联测试用例失败, projectId={}, appId={}, snapshotId={}", projectId, appId, id, e);
            ResultNotified<Integer> result = new ResultNotified<>(false, "测试用例关联更新失败");
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    @RequestMapping("/usecase/batchBind")
    @ResponseBody
    public ResultNotified<Integer> batchBindUsecases(@PathVariable String projectId,
                                                     @PathVariable String appId,
                                                     @SessionAttribute UserVo user,
                                                     String[] snapshotIds,
                                                     String[] usecaseIds) {
        try {
            Assert.isTrue(!org.springframework.util.ObjectUtils.isEmpty(snapshotIds), "snapshotIds不能为空");
            for (String snapshotId : snapshotIds) {
                if (!StringUtils.hasText(snapshotId)) {
                    continue;
                }
                SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId.trim());
                Assert.notNull(snapshot, "找不到系统快照 id=" + snapshotId);
                Assert.isTrue(projectId.equals(snapshot.getProjectId()), "系统快照不属于当前项目");
                Assert.isTrue(appId.equals(snapshot.getAppId()), "系统快照不属于当前应用");
            }
            for (String snapshotId : snapshotIds) {
                if (!StringUtils.hasText(snapshotId)) {
                    continue;
                }
                usecaseService.bindSystemSnapshotToUsecases(projectId, user.getId(), snapshotId.trim(), usecaseIds);
            }
            ResultNotified<Integer> result = new ResultNotified<>(true, "批量关联成功");
            result.setData(snapshotIds.length);
            return result;
        } catch (Exception e) {
            logger.warn("批量关联系统快照测试用例失败, projectId={}, appId={}", projectId, appId, e);
            ResultNotified<Integer> result = new ResultNotified<>(false, "批量关联失败");
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    private RemoteCallResolver buildRemoteCallResolver(String projectId) {
        List<AppVo> apps = appService.getAppList(projectId);
        List<ApiEndpointIndex> endpoints = apps.stream()
                .flatMap(app -> apiEndpointRepository.findByAppIdOrderByEndpointTypeAscUrlAsc(app.getId()).stream())
                .collect(Collectors.toList());
        return new RemoteCallResolver(apps, endpoints);
    }

    /**
     * 将 TraceNode 转换成前台堆栈列表树节点
     */
    @RequestMapping("/detail/graph/{id}")
    @ResponseBody
    public GraphView getGraphView(@PathVariable String projectId, @PathVariable String id) {
        SystemSnapshot snapshot = systemSnapshotService.getById(id);
        Assert.notNull(snapshot, "找不到系统快照id=" + id);
        Collection<TraceNode> nodes = snapshotService.getTraceNodes(snapshot.getTraceId());
        Map<String, TraceNode> nodeMap = nodes.stream()
                .filter(Objects::nonNull)
                .filter(node -> StringUtils.hasText(node.getTraceNodeId()))
                .collect(Collectors.toMap(TraceNode::getTraceNodeId, node -> node, (left, right) -> left, LinkedHashMap::new));
        return new TraceGraphParse(nodeMap, buildRemoteCallResolver(projectId)).getGraphView();
    }

    @RequestMapping("/node/{snapshotId}")
    public String openNodeDetail(@PathVariable String projectId, @PathVariable String appId, @PathVariable String snapshotId, String traceId, String nodeId, Model model) {
        if (System.currentTimeMillis() >= 0) {
            return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/snapshots/" + snapshotId);
        }
        Collection<TraceNode> nodes = snapshotService.getTraceNodes(traceId);
        Map<String, TraceNode> nodeMap = nodes.stream()
                .filter(Objects::nonNull)
                .filter(node -> StringUtils.hasText(node.getTraceNodeId()))
                .collect(Collectors.toMap(TraceNode::getTraceNodeId, node -> node, (left, right) -> left, LinkedHashMap::new));
        TraceGraphParse parse = new TraceGraphParse(nodeMap, buildRemoteCallResolver(projectId));
        GraphNode node = parse.getGraphNode(nodeId);
        Assert.notNull(node, "not found GraphNode: " + nodeId);

        if (node instanceof ClientGraphNode) {
            model.addAttribute("node", ((ClientGraphNode) node).getTraceNode());
            return "/monitor/httpNodeDetails";
        } else if (node instanceof ApplicationGraphNode) {
            String sessionId = ((ApplicationGraphNode) node).getSessionId();
            ClientSessionVo clientSession = clientSessionService.getClientSession(sessionId);
            model.addAttribute("appSession", clientSession);
            model.addAttribute("data", node);
            model.addAttribute("traceId", traceId);
            return "/monitor/serverDetails";
        } else if (node instanceof DatabaseGraphNode) {
            if (((DatabaseGraphNode) node).getCkdatabase() != null) {
                model.addAttribute("database", ((DatabaseGraphNode) node).getCkdatabase());
                model.addAttribute("data", node);
                model.addAttribute("traceId", traceId);
                return "/monitor/ckdatabaseDetails";
            }

            model.addAttribute("database", ((DatabaseGraphNode) node).getDatabase());
            model.addAttribute("data", node);
            model.addAttribute("traceId", traceId);
            return "/monitor/databaseDetails";

        } else if (node instanceof RedisGraphNode) {
            model.addAttribute("redis", node);
            model.addAttribute("traceId", traceId);
            return "/monitor/redisNodeDetails";
        } else {
            throw new RuntimeException("Failed to resolve graph model: " + node.getClass().getName());
        }
    }

    private Map<String, String> buildSnapshotTimeTextMap(List<SystemSnapshot> snapshots) {
        Map<String, String> result = new HashMap<>();
        for (SystemSnapshot snapshot : snapshots) {
            result.put(snapshot.getId(), formatDateTime(snapshot.getVersionLastUpdate()));
        }
        return result;
    }

    private Map<String, String> buildSnapshotRelativeTimeTextMap(List<SystemSnapshot> snapshots) {
        Map<String, String> result = new HashMap<>();
        for (SystemSnapshot snapshot : snapshots) {
            result.put(snapshot.getId(), formatRelativeTime(snapshot.getVersionLastUpdate()));
        }
        return result;
    }

    private String buildSnapshotApiCoverageSummaryText(String appId, List<SystemSnapshot> snapshots) {
        if (!StringUtils.hasText(appId)) {
            return "0 / 0";
        }
        List<String> traceIds = snapshots.stream()
                .filter(Objects::nonNull)
                .map(SystemSnapshot::getTraceId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        com.oAT.web.service.entity.ApiEndpointCoverageVo coverage = apiEndpointAnalysisService.calculateCoverage(appId, traceIds);
        return coverage.getDisplayText();
    }

    private String formatDateTime(Date date) {
        if (date == null) {
            return "-";
        }
        return new SimpleDateFormat(DATE_TIME_PATTERN).format(date);
    }

    private List<com.oAT.web.service.entity.UsecaseVo> collectAllProjectUsecases(String projectId) {
        LinkedHashMap<String, com.oAT.web.service.entity.UsecaseVo> result = new LinkedHashMap<>();
        Deque<String> directoryQueue = new ArrayDeque<>();
        directoryQueue.add("root");
        while (!directoryQueue.isEmpty()) {
            String directoryId = directoryQueue.poll();
            for (com.oAT.web.service.entity.UsecaseVo usecaseVo : usecaseService.getUsecases(projectId, directoryId, "updateTime", null)) {
                result.putIfAbsent(usecaseVo.getId(), usecaseVo);
            }
            for (com.oAT.web.service.entity.UsecaseDirectoryVo directoryVo : usecaseService.getDirectory(projectId, directoryId)) {
                if (directoryVo != null && StringUtils.hasText(directoryVo.getId())) {
                    directoryQueue.add(directoryVo.getId());
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private String formatRelativeTime(Date date) {
        if (date == null) {
            return "-";
        }
        return DateUtil.timeDifference(date) + "前";
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
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/snapshots/" + id + "/report");
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
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/snapshots/" + snapshotId + "/report/code?className=" + className);
    }

    private Map<String, List<Integer>> normalizeMethodBranchTargetProbeMap(Map<String, List<Integer>> total,
                                                                            Map<String, List<Integer>> covered) {
        if (total == null || total.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (covered == null || covered.isEmpty()) {
            return copyBranchTargetProbeMap(total);
        }
        Map<String, List<Integer>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : total.entrySet()) {
            String branchLine = entry.getKey();
            List<Integer> totalValues = entry.getValue();
            if (totalValues == null || totalValues.isEmpty()) {
                continue;
            }
            LinkedHashSet<Integer> totalSet = new LinkedHashSet<>(totalValues);
            List<Integer> coveredValues = covered.get(branchLine);
            if (coveredValues == null || coveredValues.isEmpty()) {
                normalized.put(branchLine, new ArrayList<>(totalSet));
                continue;
            }
            LinkedHashSet<Integer> coveredSet = new LinkedHashSet<>(coveredValues);
            if (totalSet.containsAll(coveredSet)) {
                normalized.put(branchLine, new ArrayList<>(coveredSet));
            } else {
                normalized.put(branchLine, new ArrayList<>(totalSet));
            }
        }
        return normalized.isEmpty() ? copyBranchTargetProbeMap(total) : normalized;
    }

    private Map<String, List<Integer>> copyBranchTargetProbeMap(Map<String, List<Integer>> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        Map<String, List<Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : source.entrySet()) {
            List<Integer> values = entry.getValue() == null ? Collections.emptyList() : new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
            copy.put(entry.getKey(), values);
        }
        return copy;
    }

    private Map<String, List<Integer>> mergeBranchTargetProbeMap(Map<String, List<Integer>> current,
                                                                 Map<String, List<Integer>> incoming) {
        Map<String, LinkedHashSet<Integer>> merged = new LinkedHashMap<>();
        appendBranchTargetProbeMap(merged, current);
        appendBranchTargetProbeMap(merged, incoming);
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<Integer>> entry : merged.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    private void appendBranchTargetProbeMap(Map<String, LinkedHashSet<Integer>> target,
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

    private int countBranchTargets(Map<String, List<Integer>> branchTargetProbeMap) {
        if (branchTargetProbeMap == null || branchTargetProbeMap.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (List<Integer> values : branchTargetProbeMap.values()) {
            total += values == null ? 0 : new LinkedHashSet<>(values).size();
        }
        return total;
    }

    private Map<String, List<Integer>> normalizeCoveredBranchTargetProbeMap(Map<String, List<Integer>> total,
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

    private double calculateBranchRate(int coveredBranchTargets, int totalBranchTargets) {
        return totalBranchTargets > 0 ? (double) coveredBranchTargets / totalBranchTargets * 100 : 0.0;
    }

    private void addBranchTargetKeys(Map<String, Set<String>> target,
                                     String methodKey,
                                     Map<String, List<Integer>> branchTargetProbeMap) {
        if (branchTargetProbeMap == null || branchTargetProbeMap.isEmpty()) {
            return;
        }
        Set<String> keys = target.computeIfAbsent(methodKey, key -> new LinkedHashSet<>());
        for (Map.Entry<String, List<Integer>> entry : branchTargetProbeMap.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            for (Integer branchTarget : entry.getValue()) {
                if (branchTarget != null) {
                    keys.add(entry.getKey() + "#" + branchTarget);
                }
            }
        }
    }

    private void addBranchTargetKeysToSet(Set<String> target,
                                          Map<String, List<Integer>> allowedBranchTargetProbeMap,
                                          Map<String, List<Integer>> branchTargetProbeMap) {
        Map<String, List<Integer>> effective = normalizeCoveredBranchTargetProbeMap(
                allowedBranchTargetProbeMap, branchTargetProbeMap);
        for (Map.Entry<String, List<Integer>> entry : effective.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            for (Integer branchTarget : entry.getValue()) {
                if (branchTarget != null) {
                    target.add(entry.getKey() + "#" + branchTarget);
                }
            }
        }
    }

    private Map<String, List<Integer>> decodeBranchTargetKeys(Set<String> keys) {
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
                int branchTarget = Integer.parseInt(key.substring(split + 1));
                decoded.computeIfAbsent(key.substring(0, split), k -> new ArrayList<>()).add(branchTarget);
            } catch (NumberFormatException ignore) {
            }
        }
        return decoded;
    }

}
