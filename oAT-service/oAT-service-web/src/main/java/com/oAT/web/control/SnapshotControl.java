package com.oAT.web.control;

import com.oAT.web.config.FrontendProperties;
import com.oAT.agent.model.*;
import com.oAT.web.api.snapshot.TraceGraphViewService;
import com.oAT.web.control.entity.GraphView;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.esDao.entity.Snapshot;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/p/{projectId}/snapshot")
public class SnapshotControl {

    @Autowired
    FrontendProperties frontendProperties;


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
    private UsecaseService usecaseService;

    @Autowired
    private TraceGraphViewService traceGraphViewService;

    @PostMapping("/save")
    @ResponseBody
    public ResultNotified<SnapshotVo> doSave(@PathVariable String projectId, @SessionAttribute UserVo user, HttpSession session, Snapshot snapshot,
                                             @RequestParam(value = "autoSave", required = false, defaultValue = "false") boolean autoSave) {
        try {
            Assert.notNull(snapshot, "参数snapshot不能为空");
            Assert.hasText(snapshot.getTraceId(), "参数'traceId'不能为空");
            snapshot.setProjectId(projectId);
            snapshot.setCreateUser(user.getId());
            Map<String, TraceNode> nodes = getTraceNodesForSave(snapshot.getTraceId(), session);
            Assert.isTrue(!nodes.isEmpty(), "找不到对应链路，请刷新监控后重试");

            if (autoSave) {
                Assert.hasText(snapshot.getName(), "自动保存时快照名称不能为空");
                if (snapshotService instanceof com.oAT.web.service.impl.SnapshotServiceImpl snapshotServiceImpl
                        && snapshotServiceImpl.existsByProjectUserAndTraceId(projectId, user.getId(), snapshot.getTraceId())) {
                    ResultNotified<SnapshotVo> duplicated = new ResultNotified<>(true, "我的快照已自动保存过");
                    return duplicated;
                }
            }

            SnapshotVo vo = snapshotService.addSnapshot(snapshot, nodes.values());
            ResultNotified<SnapshotVo> result = new ResultNotified<>(true, autoSave ? "已自动保存我的快照" : "快照保存成功");
            result.setData(vo);
            return result;
        } catch (Exception e) {
            logger.warn("保存我的快照失败, projectId={}, traceId={}", projectId, snapshot == null ? null : snapshot.getTraceId(), e);
            ResultNotified<SnapshotVo> result = new ResultNotified<>(false, "快照保存失败");
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    private Map<String, TraceNode> getTraceNodesForSave(String traceId, HttpSession session) {
        Map<String, TraceNode> nodes = new LinkedHashMap<>();

        Map<String, TraceNode> cachedNodes = clientSessionService.getTraceNodes(traceId);
        if (cachedNodes != null && !cachedNodes.isEmpty()) {
            nodes.putAll(cachedNodes);
            return nodes;
        }

        Object sessionNodes = session.getAttribute("model-" + traceId);
        if (sessionNodes instanceof Map) {
            Map<?, ?> rawNodes = (Map<?, ?>) sessionNodes;
            for (Map.Entry<?, ?> entry : rawNodes.entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof TraceNode) {
                    nodes.put((String) entry.getKey(), (TraceNode) entry.getValue());
                }
            }
            if (!nodes.isEmpty()) {
                return nodes;
            }
        }

        Collection<TraceNode> storedNodes = snapshotService.getTraceNodes(traceId);
        if (storedNodes != null) {
            for (TraceNode node : storedNodes) {
                if (node != null && StringUtils.hasText(node.getTraceNodeId())) {
                    nodes.put(node.getTraceNodeId(), node);
                }
            }
        }
        return nodes;
    }

    /**
     * 打开我的快照列表 显示全部
     *
     * @return
     */
    @RequestMapping("/list")
    public String openList(@PathVariable String projectId, String[] labels, String sort) {
        StringBuilder target = new StringBuilder("/p/").append(projectId).append("/my-snapshots");
        List<String> query = new ArrayList<>();
        if (StringUtils.hasText(sort)) query.add("sort=" + sort);
        if (ArrayUtils.isNotEmpty(labels)) query.add("labels=" + StringUtils.arrayToDelimitedString(labels, ","));
        if (!query.isEmpty()) target.append("?").append(String.join("&", query));
        return "redirect:" + frontendProperties.url(target.toString());
    }

    @RequestMapping("/my")
    public String mySnapshotList(@PathVariable String projectId, @SessionAttribute UserVo user, String[] labels, String sort, String keyword,
                                 String snapshotId, String missingSnapshotId, Model model) {
        StringBuilder target = new StringBuilder("/p/").append(projectId).append("/my-snapshots");
        List<String> query = new ArrayList<>();
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

    @RequestMapping("/mySnapshotsCodeReport")
    public String mySnapshotsCodeReport(@PathVariable String projectId, String sort) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/my-snapshots/code-report" + (StringUtils.hasText(sort) ? "?sort=" + sort : ""));
    }

    @RequestMapping("/mySnapshotCodeReport")
    public String mySnapshotCodeReport(@PathVariable String projectId, String snapshotId) {
        Assert.hasText(snapshotId, "参数'snapshotId'不能为空");
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/my-snapshots/" + snapshotId + "/report");
    }

    @RequestMapping("/usecase/batchBind")
    @ResponseBody
    public ResultNotified<Integer> batchBindUsecases(@PathVariable String projectId,
                                                     @SessionAttribute UserVo user,
                                                     String[] snapshotIds,
                                                     String[] usecaseIds) {
        try {
            usecaseService.batchAppendSnapshotsToUsecases(projectId, user.getId(), snapshotIds, usecaseIds);
            ResultNotified<Integer> result = new ResultNotified<>(true, "批量关联成功");
            result.setData(snapshotIds == null ? 0 : snapshotIds.length);
            return result;
        } catch (Exception e) {
            logger.warn("批量关联快照测试用例失败, projectId={}", projectId, e);
            ResultNotified<Integer> result = new ResultNotified<>(false, "批量关联失败");
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    @RequestMapping("/my/code")
    public String snapshotCodeView(@PathVariable String projectId, String appId, String className) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/my-snapshots/code?appId=" + appId + "&className=" + className);
    }

    @RequestMapping("/node")
    public String openNodeDetail(@PathVariable String projectId, String traceId, String nodeId) {
        StringBuilder target = new StringBuilder("/p/").append(projectId).append("/my-snapshots");
        List<String> query = new ArrayList<>();
        if (StringUtils.hasText(traceId)) query.add("traceId=" + traceId);
        if (StringUtils.hasText(nodeId)) query.add("nodeId=" + nodeId);
        if (!query.isEmpty()) target.append("?").append(String.join("&", query));
        return "redirect:" + frontendProperties.url(target.toString());
    }













    @RequestMapping("/detail/graph/{traceId}")
    @ResponseBody
    public GraphView getGraphView(@PathVariable String projectId, @PathVariable String traceId) {
        return traceGraphViewService.buildGraphView(projectId, traceId);
    }

    @RequestMapping("/detail/stack/{traceId}")
    public String openTraceTable(@PathVariable String projectId, @PathVariable String traceId) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/my-snapshots?traceId=" + traceId);
    }


    @RequestMapping("/edit")
    public String openEdit(@PathVariable String projectId, @SessionAttribute UserVo user, String id, Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/my-snapshots/" + id);
    }

    /**
     * 打开快照节点详情
     *
     * @return
     */
    @RequestMapping("/detail/{id}")
    public String openDetail(@PathVariable String projectId, @PathVariable String id, HttpServletRequest request) {
        Boolean share = (Boolean) request.getAttribute("_share");
        if (BooleanUtils.isTrue(share)) {
            return "redirect:" + frontendProperties.url("/share/snapshot/" + id);
        }
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/my-snapshots/" + id);
    }


    @RequestMapping("/{snapshotId}/usecase/bind")
    @ResponseBody
    public ResultNotified<Integer> bindUsecases(@PathVariable String projectId,
                                                @PathVariable String snapshotId,
                                                @SessionAttribute UserVo user,
                                                String[] usecaseIds) {
        try {
            SnapshotVo snapshotVo = snapshotService.get(snapshotId);
            Assert.notNull(snapshotVo, "找不到快照 id=" + snapshotId);
            Assert.isTrue(projectId.equals(snapshotVo.getProjectId()), "快照不属于当前项目");
            usecaseService.bindSnapshotToUsecases(projectId, user.getId(), snapshotId, usecaseIds);
            ResultNotified<Integer> result = new ResultNotified<>(true, "测试用例关联已更新");
            result.setData(usecaseIds == null ? 0 : usecaseIds.length);
            return result;
        } catch (Exception e) {
            logger.warn("更新快照关联测试用例失败, projectId={}, snapshotId={}", projectId, snapshotId, e);
            ResultNotified<Integer> result = new ResultNotified<>(false, "测试用例关联更新失败");
            result.setErrorMessage(e.getMessage());
            return result;
        }
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

    @RequestMapping("/getTraceGraph")
    @ResponseBody
    public GraphView getTraceGraph(@PathVariable String projectId, String traceId, HttpSession session) {
        HashMap<String, TraceNode> nodes = new HashMap<>();
        Collection<TraceNode> list = snapshotService.getTraceNodes(traceId);
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("未找到Trace Node traceId=" + traceId);
        }
        for (TraceNode node : list) {
            nodes.put(node.getTraceNodeId(), node);
        }
        return traceGraphViewService.buildGraphView(projectId, nodes);
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
