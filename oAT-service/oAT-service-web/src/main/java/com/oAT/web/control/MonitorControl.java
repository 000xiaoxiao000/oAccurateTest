package com.oAT.web.control;

import com.oAT.web.config.FrontendProperties;
import com.oAT.agent.model.*;
import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.common.DateUtil;
import com.oAT.web.control.api.SnapshotApiControl;
import com.oAT.web.control.entity.*;
import com.oAT.web.domain.RemoteCallResolver;
import com.oAT.web.esDao.ApiEndpointRepository;
import com.oAT.web.esDao.entity.ApiEndpointIndex;
import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/p/{projectId}/monitor")
public class MonitorControl {

    @Autowired
    FrontendProperties frontendProperties;


    private static final int DEFAULT_UP_TO_TIME_SECONDS = 180;

    @org.springframework.beans.factory.annotation.Value("${traceNode.monitor.maxSize:200}")
    private Integer defaultMaxSize;

    @Autowired
    AppService appService;

    @Autowired
    ClientSessionService clientSessionService;

    @Autowired
    SnapshotService snapshotService;

    @Autowired
    ProjectService projectService;

    @Autowired
    SystemSnapshotService systemSnapshotService;

    @Autowired
    ApiEndpointRepository apiEndpointRepository;

    @RequestMapping("/probeStatus")
    @ResponseBody
    public List<ClientSessionVo> getProbeStatus(@PathVariable String projectId) {
        List<String> appIds = getAppIds(projectId);
        List<ClientSessionVo> onlineSessions = new ArrayList<>();
        for (ClientSessionVo session : clientSessionService.getOnlineSessions()) {
            String sessionAppId = session.getClientInfo() == null ? null : session.getClientInfo().getAppKey();
            if (!StringUtils.hasText(sessionAppId) || appIds.contains(sessionAppId)) {
                session.setOnlineTime(DateUtil.timeDifference(session.getLoginTime(), new Date()));
                onlineSessions.add(session);
            }
        }
        return onlineSessions;
    }

    /**
     * @param lastIndex 最后更新索引
     * @param maxSize   最大值
     * @return 基于最后更新索引获取最新监控项
     */
    @RequestMapping("/getNodeByIndex")
    @ResponseBody
    public List<TraceItemVo> getNodeByIndex(@PathVariable String projectId, Integer lastIndex, Integer maxSize) {
        List<String> appIds = getAppIds(projectId);
        return clientSessionService.getTraceItemByIndex(lastIndex, true, maxSize == null ? 50 : maxSize, appIds);
    }

    private RemoteCallResolver buildRemoteCallResolver(String projectId) {
        List<AppVo> apps = appService.getAppList(projectId);
        List<ApiEndpointIndex> endpoints = apps.stream()
                .flatMap(app -> apiEndpointRepository.findByAppIdOrderByEndpointTypeAscUrlAsc(app.getId()).stream())
                .collect(java.util.stream.Collectors.toList());
        return new RemoteCallResolver(apps, endpoints);
    }

    private List<String> getAppIds(String projectId) {
        List<AppVo> appList = appService.getAppList(projectId);
        List<String> appIds = new ArrayList<>(appList.size());
        for (AppVo appVo : appList) {
            appIds.add(appVo.getId());
        }
        return appIds;
    }

    // 获取指定时间内的 监控跟踪项,

    /**
     * @param upToTime 截至时间
     * @param filter   综合过滤条件
     * @return 跟踪监控项，基于时间到序
     */
    @RequestMapping("/getNodeByTime")
    @ResponseBody
    public TraceItemVo[] getNodeByTime(@PathVariable String projectId, Integer upToTime, TraceItemSearchParam filter) {
        if (filter == null) {
            filter = new TraceItemSearchParam();
        }
        List<String> projectAppIds = getAppIds(projectId);
        if (filter.getAppIds() == null || filter.getAppIds().isEmpty()) {
            filter.setAppIds(projectAppIds);
        } else {
            filter.getAppIds().removeIf(appId -> !projectAppIds.contains(appId));
        }
        // 默认值
        if (filter.getMaxSize() == null) {
            filter.setMaxSize(defaultMaxSize);
        }
        int queryUpToTime = upToTime == null || upToTime <= 0 ? DEFAULT_UP_TO_TIME_SECONDS : upToTime;
        List<TraceItemVo> list = clientSessionService.getTraceItemByTime(queryUpToTime, filter);
        // 基于时间到序排列
        TraceItemVo[] items = new TraceItemVo[list.size()];
        for (int i = list.size() - 1, k = 0; i >= 0; i--, k++) {
            items[k] = list.get(i);
        }
        return items;
    }

    @RequestMapping("/getTraceGraph")
    @ResponseBody
    public GraphView getTraceGraph(@PathVariable String projectId, String traceId) {
        return new TraceGraphParse(getTraceNode(traceId), buildRemoteCallResolver(projectId)).getGraphView();
    }

    @RequestMapping("/getTraceGraphNode")
    @ResponseBody
    public ResultNotified<SnapshotApiControl.GraphNodeDetailPayload> getTraceGraphNode(@PathVariable String projectId,
                                                                                       String traceId,
                                                                                       String nodeId) {
        TraceGraphParse parse = new TraceGraphParse(getTraceNode(traceId), buildRemoteCallResolver(projectId));
        GraphNode node = parse.getGraphNode(nodeId);
        Assert.notNull(node, "not found GraphNode: " + nodeId);
        return new ResultNotified<>(true, "获取监控链路节点详情成功", SnapshotApiControl.toGraphNodeDetail(node));
    }

    /**
     * 基于TraceId 查找 TraceNode ，查找顺序为：
     * 1 clientSessionService
     * 2 snapshotService
     *
     * @param traceId
     * @return
     */
    private Map<String, TraceNode> getTraceNode(String traceId) {
        Map<String, TraceNode> nodes = clientSessionService.getTraceNodes(traceId);
        if (nodes == null || nodes.isEmpty()) {
            Collection<TraceNode> list = snapshotService.getTraceNodes(traceId);
            nodes = new HashMap<>();
            for (TraceNode node : list) {
                nodes.put(node.getTraceNodeId(), node);
            }
        }
        Assert.notNull(nodes, "找不到traceNode traceId=" + traceId);
        return nodes;
    }

    @RequestMapping("/{traceId}/{nodeId}.html")
    public String getNodeDetailView(@PathVariable String projectId,
                                    @PathVariable String traceId,
                                    @PathVariable String nodeId) {
        String query = StringUtils.hasText(traceId)
                ? "?traceId=" + traceId + (StringUtils.hasText(nodeId) ? "&nodeId=" + nodeId : "")
                : "";
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/monitor" + query);
    }

    @RequestMapping("/openSystemSnapshot")
    public String openSystemSnapshot(@PathVariable String projectId, String traceId) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/monitor" + (StringUtils.hasText(traceId) ? "?traceId=" + traceId + "&snapshot=system" : ""));
    }

    @GetMapping("/systemSnapshotContext")
    @ResponseBody
    public ResultNotified<SystemSnapshotContextPayload> getSystemSnapshotContext(@PathVariable String projectId,
                                                                                 @SessionAttribute UserVo user,
                                                                                 @RequestParam String traceId) {
        Map<String, TraceNode> nodes = getTraceNode(traceId);
        TraceNode rootNode = nodes.get("0");
        Assert.notNull(rootNode, "找不到主调用节点");
        Application app = rootNode.getApp();
        Assert.notNull(app, "找不到应用信息");

        SystemSnapshotContextPayload payload = new SystemSnapshotContextPayload();
        payload.setTraceId(traceId);
        payload.setAppId(app.getAppId());
        payload.setAppName(app.getAppName());
        payload.setProjectSrcName(app.getProjectSrcName());
        payload.setDirectories(appService.getAppSnapshotDirs(app.getAppId()));
        payload.setLabels(projectService.getLables(projectId, LableType.snapshot));
        payload.setMembers(projectService.getProjectMembers(projectId));
        payload.setCurrentUserId(user.getId());
        payload.setDefaultTitle(rootNode instanceof HttpTraceNode ? ((HttpTraceNode) rootNode).getRequestUrl() : traceId);
        payload.setSubTitle(rootNode instanceof HttpTraceNode ? ((HttpTraceNode) rootNode).getRequestUrl() : "");
        return new ResultNotified<>(true, "获取系统快照保存上下文成功", payload);
    }

    @PostMapping("/doSaveSystemSnapshot")
    @ResponseBody
    public ResultNotified<String> doSaveSystemSnapshot(SystemSnapshot snapshot, @PathVariable String projectId,
                                                       @SessionAttribute UserVo user,
                                                       String traceId) {
        try {
            Map<String, TraceNode> nodes = getTraceNode(traceId);
            snapshot.setSubTitle(((HttpTraceNode) nodes.get("0")).getRequestUrl());
            systemSnapshotService.create(projectId, user.getId(), snapshot, nodes.values());
            return new ResultNotified<>(true, "保存成功");
        } catch (Exception e) {
            ResultNotified<String> result = new ResultNotified<>(false, "系统快照保存失败");
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    @PostMapping("/autoSaveSystemSnapshot")
    @ResponseBody
    public ResultNotified<String> autoSaveSystemSnapshot(@PathVariable String projectId,
                                                         @SessionAttribute UserVo user,
                                                         @RequestParam String traceId,
                                                         @RequestParam(required = false) String title) {
        try {
            Map<String, TraceNode> nodes = getTraceNode(traceId);
            TraceNode rootNode = nodes.get("0");
            Assert.notNull(rootNode, "找不到主调用节点");
            Application app = rootNode.getApp();
            Assert.notNull(app, "找不到应用信息");

            List<SystemSnapshot> existingSnapshots = systemSnapshotService.findAll(projectId, app.getAppId());
            boolean exists = existingSnapshots.stream().anyMatch(item -> traceId.equals(item.getTraceId()));
            if (exists) {
                return new ResultNotified<>(true, "系统快照已自动保存过");
            }

            SystemSnapshot snapshot = new SystemSnapshot();
            snapshot.setTraceId(traceId);
            snapshot.setAppId(app.getAppId());
            snapshot.setDirectory("root");
            snapshot.setTitle(StringUtils.hasText(title) ? title : "自动系统快照");
            snapshot.setDescribe("由实时监控自动生成");
            snapshot.setLabels(new String[]{"自动保存", "系统快照"});
            snapshot.setPrincipals(new String[]{user.getId()});
            if (rootNode instanceof HttpTraceNode) {
                snapshot.setSubTitle(((HttpTraceNode) rootNode).getRequestUrl());
            }
            systemSnapshotService.create(projectId, user.getId(), snapshot, nodes.values());
            return new ResultNotified<>(true, "已自动保存系统快照");
        } catch (Exception e) {
            ResultNotified<String> result = new ResultNotified<>(false, "系统快照保存失败");
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    public static class SystemSnapshotContextPayload {
        private String traceId;
        private String appId;
        private String appName;
        private String projectSrcName;
        private List<Directory> directories;
        private List<LabelGroup.Label> labels;
        private List<ProjectMemberVo> members;
        private String currentUserId;
        private String defaultTitle;
        private String subTitle;

        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getProjectSrcName() { return projectSrcName; }
        public void setProjectSrcName(String projectSrcName) { this.projectSrcName = projectSrcName; }
        public List<Directory> getDirectories() { return directories; }
        public void setDirectories(List<Directory> directories) { this.directories = directories; }
        public List<LabelGroup.Label> getLabels() { return labels; }
        public void setLabels(List<LabelGroup.Label> labels) { this.labels = labels; }
        public List<ProjectMemberVo> getMembers() { return members; }
        public void setMembers(List<ProjectMemberVo> members) { this.members = members; }
        public String getCurrentUserId() { return currentUserId; }
        public void setCurrentUserId(String currentUserId) { this.currentUserId = currentUserId; }
        public String getDefaultTitle() { return defaultTitle; }
        public void setDefaultTitle(String defaultTitle) { this.defaultTitle = defaultTitle; }
        public String getSubTitle() { return subTitle; }
        public void setSubTitle(String subTitle) { this.subTitle = subTitle; }
    }

}
