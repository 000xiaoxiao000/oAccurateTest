package com.oAT.web.control;

import com.oAT.agent.model.*;
import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.control.entity.*;
import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/p/{projectId}/monitor")
public class MonitorControl {

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

    // 默认打开视图
    @RequestMapping("")
    public String openMonitorPlatformView(@PathVariable String projectId, String appId, Model model) {
        model.addAttribute("projectId", projectId);
        List<AppVo> appList = appService.getAppList(projectId);
        List<LabelGroup.Label> labels = projectService.getLables(projectId, LableType.snapshot);

        model.addAttribute("labels", labels);
        model.addAttribute("apps", appList);
        model.addAttribute("appId", appId);
        return "/monitor/MonitorPlatform";
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
        List<String> appIds = getAppIds(projectId);
        filter.setAppIds(appIds);
        // 默认值
        if (filter.getMaxSize() == null) {
            filter.setMaxSize(defaultMaxSize);
        }
        List<TraceItemVo> list = clientSessionService.getTraceItemByTime(upToTime, filter);
        // 基于时间到序排列
        TraceItemVo[] items = new TraceItemVo[list.size()];
        for (int i = list.size() - 1, k = 0; i >= 0; i--, k++) {
            items[k] = list.get(i);
        }
        return items;
    }

    @RequestMapping("/getTraceGraph")
    @ResponseBody
    public GraphView getTraceGraph(String traceId) {
        return new TraceGraphParse(getTraceNode(traceId)).getGraphView();
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
    public String getNodeDetailView(@PathVariable String traceId, @PathVariable String nodeId, Model model) {
        TraceGraphParse parse = new TraceGraphParse(getTraceNode(traceId));
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

    @RequestMapping("/openSystemSnapshot")
    public String openSystemSnapshot(@PathVariable String projectId, String traceId, @SessionAttribute UserVo user,
                                           Model model) {
        model.addAttribute("projectId", projectId);
        // 所属应用
        Map<String, TraceNode> nodes = getTraceNode(traceId);
        Application app = nodes.get("0").getApp();
        model.addAttribute("app", app);

        // 加载标签
        List<LabelGroup.Label> labels = projectService.getLables(projectId, LableType.snapshot);
        model.addAttribute("labels", labels);

        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);
        model.addAttribute("members", members);
        // 当前用户
        model.addAttribute("user", user);
        model.addAttribute("traceId", traceId);
        // 加载负责人
        // 快照目录
        List<Directory> dirs = appService.getAppSnapshotDirs(app.getAppId());
        model.addAttribute("dirs", dirs);
        return "/monitor/createSystemSnapshot";
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

}
