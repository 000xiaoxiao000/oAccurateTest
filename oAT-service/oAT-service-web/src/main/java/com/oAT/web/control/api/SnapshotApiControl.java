package com.oAT.web.control.api;

import com.oAT.web.api.common.ApiSummaries.*;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.api.snapshot.BatchBindUsecasesRequest;
import com.oAT.web.api.snapshot.BindUsecasesRequest;
import com.oAT.web.api.snapshot.DeleteSnapshotCommentRequest;
import com.oAT.web.api.snapshot.DynamicItemSummary;
import com.oAT.web.api.snapshot.GraphNodeDetailMapper;
import com.oAT.web.api.snapshot.GraphNodeDetailPayload;
import com.oAT.web.api.snapshot.MySnapshotAggregateCodePayload;
import com.oAT.web.api.snapshot.MySnapshotCodePayload;
import com.oAT.web.api.snapshot.MySnapshotCodeReportPayload;
import com.oAT.web.api.snapshot.MySnapshotDetailPayload;
import com.oAT.web.api.snapshot.MySnapshotListPayload;
import com.oAT.web.api.snapshot.MySnapshotReportPayload;
import com.oAT.web.api.snapshot.MySnapshotPayloadService;
import com.oAT.web.api.snapshot.SaveAsSystemSnapshotRequest;
import com.oAT.web.api.snapshot.SaveSnapshotDirectoryRequest;
import com.oAT.web.api.snapshot.SnapshotCommentRequest;
import com.oAT.web.api.snapshot.SnapshotApiSupportService;
import com.oAT.web.api.snapshot.SnapshotSaveAsSystemService;
import com.oAT.web.api.snapshot.SystemSnapshotCodePayload;
import com.oAT.web.api.snapshot.SystemSnapshotDetailPayload;
import com.oAT.web.api.snapshot.SystemSnapshotListPayload;
import com.oAT.web.api.snapshot.SystemSnapshotPayloadService;
import com.oAT.web.api.snapshot.SystemSnapshotReportPayload;
import com.oAT.web.api.snapshot.TraceGraphViewService;
import com.oAT.web.api.snapshot.UpdateMySnapshotBasicRequest;
import com.oAT.web.api.snapshot.UpdateShareRequest;
import com.oAT.web.api.snapshot.UpdateSystemSnapshotBasicRequest;
import com.oAT.web.api.monitor.CoverageMonitorEventService;
import com.oAT.web.control.entity.GraphNode;
import com.oAT.web.control.entity.GraphView;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.esDao.entity.SnapshotDirectory;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.exceptions.BusinessException;
import com.oAT.web.service.AppService;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.SystemSnapshotService;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.SnapshotVo;
import com.oAT.web.service.entity.UserVo;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class SnapshotApiControl {

    private final SnapshotService snapshotService;
    private final SystemSnapshotService systemSnapshotService;
    private final AppService appService;
    private final UsecaseService usecaseService;
    private final TraceGraphViewService traceGraphViewService;
    private final SnapshotApiSupportService snapshotApiSupport;
    private final SnapshotSaveAsSystemService snapshotSaveAsSystemService;
    private final SystemSnapshotPayloadService systemSnapshotPayloadService;
    private final MySnapshotPayloadService mySnapshotPayloadService;
    private final CoverageMonitorEventService coverageMonitorEventService;

    public SnapshotApiControl(SnapshotService snapshotService,
                              SystemSnapshotService systemSnapshotService,
                              AppService appService,
                              UsecaseService usecaseService,
                              TraceGraphViewService traceGraphViewService,
                              SnapshotApiSupportService snapshotApiSupport,
                              SnapshotSaveAsSystemService snapshotSaveAsSystemService,
                              SystemSnapshotPayloadService systemSnapshotPayloadService,
                              MySnapshotPayloadService mySnapshotPayloadService,
                              CoverageMonitorEventService coverageMonitorEventService) {
        this.snapshotService = snapshotService;
        this.systemSnapshotService = systemSnapshotService;
        this.appService = appService;
        this.usecaseService = usecaseService;
        this.traceGraphViewService = traceGraphViewService;
        this.snapshotApiSupport = snapshotApiSupport;
        this.snapshotSaveAsSystemService = snapshotSaveAsSystemService;
        this.systemSnapshotPayloadService = systemSnapshotPayloadService;
        this.mySnapshotPayloadService = mySnapshotPayloadService;
        this.coverageMonitorEventService = coverageMonitorEventService;
    }


    @PostMapping("/snapshots/my/save")
    public ResultNotified<SnapshotVo> saveMySnapshot(@PathVariable String projectId,
                                                     @SessionAttribute UserVo user,
                                                     HttpSession session,
                                                     com.oAT.web.esDao.entity.Snapshot snapshot,
                                                     @RequestParam(value = "autoSave", required = false, defaultValue = "false") boolean autoSave) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        try {
            Assert.notNull(snapshot, "snapshot不能为空");
            Assert.hasText(snapshot.getTraceId(), "traceId不能为空");
            snapshot.setProjectId(projectId);
            snapshot.setCreateUser(user.getId());
            Map<String, TraceNode> nodes = coverageMonitorEventService.isCoverageTraceId(snapshot.getTraceId())
                    ? coverageMonitorEventService.buildTraceNodes(projectId, snapshot.getTraceId())
                    : snapshotSaveAsSystemService.getTraceNodesForSave(snapshot.getTraceId(), session);
            Assert.isTrue(!nodes.isEmpty(), "找不到对应链路，请刷新监控后重试");
            if (autoSave) {
                Assert.hasText(snapshot.getName(), "自动保存时快照名称不能为空");
                if (snapshotService instanceof com.oAT.web.service.impl.SnapshotServiceImpl snapshotServiceImpl
                        && snapshotServiceImpl.existsByProjectUserAndTraceId(projectId, user.getId(), snapshot.getTraceId())) {
                    return new ResultNotified<>(true, "我的快照已自动保存过");
                }
            }
            SnapshotVo vo = snapshotService.addSnapshot(snapshot, nodes.values());
            return new ResultNotified<>(true, autoSave ? "已自动保存我的快照" : "快照保存成功", vo);
        } catch (Exception e) {
            ResultNotified<SnapshotVo> result = new ResultNotified<>(false, "快照保存失败");
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    @GetMapping("/apps/{appId}/snapshots")
    public ResultNotified<SystemSnapshotListPayload> systemSnapshotList(@PathVariable String projectId,
                                                                        @PathVariable String appId,
                                                                        @SessionAttribute UserVo user,
                                                                        @RequestParam(required = false) String directoryId,
                                                                        @RequestParam(required = false) String sort,
                                                                        @RequestParam(required = false) String keyword) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        SystemSnapshotListPayload payload = systemSnapshotPayloadService.buildListPayload(projectId, appId, user, directoryId, sort, keyword);
        return new ResultNotified<>(true, "获取系统快照列表成功", payload);
    }

    @GetMapping("/apps/{appId}/snapshots/{snapshotId}")
    public ResultNotified<SystemSnapshotDetailPayload> systemSnapshotDetail(@PathVariable String projectId,
                                                                            @PathVariable String appId,
                                                                            @PathVariable String snapshotId,
                                                                            @SessionAttribute UserVo user) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        SystemSnapshotDetailPayload payload = systemSnapshotPayloadService.buildDetailPayload(projectId, appId, snapshotId, user);
        return new ResultNotified<>(true, "获取系统快照详情成功", payload);
    }

    @GetMapping("/apps/{appId}/snapshots/{snapshotId}/report")
    public ResultNotified<SystemSnapshotReportPayload> systemSnapshotReport(@PathVariable String projectId,
                                                                            @PathVariable String appId,
                                                                            @PathVariable String snapshotId,
                                                                            @SessionAttribute UserVo user) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        SystemSnapshotReportPayload payload = systemSnapshotPayloadService.buildReportPayload(projectId, appId, snapshotId, user);
        return new ResultNotified<>(true, "获取系统快照覆盖率报告成功", payload);
    }

    @GetMapping("/apps/{appId}/snapshots/{snapshotId}/report/code")
    public ResultNotified<SystemSnapshotCodePayload> systemSnapshotCode(@PathVariable String projectId,
                                                                        @PathVariable String appId,
                                                                        @PathVariable String snapshotId,
                                                                        @SessionAttribute UserVo user,
        @RequestParam String className) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        SystemSnapshotCodePayload payload = systemSnapshotPayloadService.buildCodePayload(projectId, appId, snapshotId, user, className);
        return new ResultNotified<>(true, "获取系统快照源码详情成功", payload);
    }

    @GetMapping("/apps/{appId}/snapshots/{snapshotId}/graph")
    public ResultNotified<GraphView> systemSnapshotGraph(@PathVariable String projectId,
                                                         @PathVariable String appId,
                                                         @PathVariable String snapshotId,
                                                         @SessionAttribute UserVo user) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        snapshotApiSupport.assertSystemSnapshotScope(projectId, appId, snapshot);
        GraphView graphView = traceGraphViewService.buildGraphView(projectId, snapshot.getTraceId());
        return new ResultNotified<>(true, "获取系统快照链路图成功", graphView);
    }

    @GetMapping("/apps/{appId}/snapshots/{snapshotId}/graph/nodes/{nodeId}")
    public ResultNotified<GraphNodeDetailPayload> systemSnapshotGraphNode(@PathVariable String projectId,
                                                                          @PathVariable String appId,
                                                                          @PathVariable String snapshotId,
                                                                          @PathVariable String nodeId,
                                                                          @SessionAttribute UserVo user) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        snapshotApiSupport.assertSystemSnapshotScope(projectId, appId, snapshot);
        GraphNode graphNode = traceGraphViewService.getGraphNode(projectId, snapshot.getTraceId(), nodeId);
        Assert.notNull(graphNode, "找不到节点 id=" + nodeId);
        return new ResultNotified<>(true, "获取链路节点详情成功", GraphNodeDetailMapper.toGraphNodeDetail(graphNode));
    }

    @PostMapping("/apps/{appId}/snapshots/commit-mapping/backfill")
    public ResultNotified<Integer> backfillSnapshotCommitMapping(@PathVariable String projectId,
                                                                  @PathVariable String appId,
                                                                  @SessionAttribute UserVo user,
                                                                  @RequestParam(required = false) String versionNumber) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        try {
            int count = systemSnapshotService.backfillCommitMapping(appId, versionNumber);
            return new ResultNotified<>(true, "补录完成，共补录 " + count + " 条关联记录", count);
        } catch (Exception e) {
            ResultNotified<Integer> result = new ResultNotified<>(false, "补录失败: " + e.getMessage());
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    @PostMapping("/apps/{appId}/snapshots/{snapshotId}/report/calculate")
    public ResultNotified<String> calculateSystemSnapshotReport(@PathVariable String projectId,
                                                                @PathVariable String appId,
                                                                @PathVariable String snapshotId,
                                                                @SessionAttribute UserVo user) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        snapshotApiSupport.assertSystemSnapshotScope(projectId, appId, snapshot);
        systemSnapshotService.asyncCalculateCoverage(snapshotId);
        return new ResultNotified<>(true, "覆盖率计算任务已启动", snapshotId);
    }

    @PostMapping("/apps/{appId}/snapshots/{snapshotId}/basic")
    public ResultNotified<String> updateSystemSnapshotBasic(@PathVariable String projectId,
                                                            @PathVariable String appId,
                                                            @PathVariable String snapshotId,
                                                            @SessionAttribute UserVo user,
                                                            @RequestBody UpdateSystemSnapshotBasicRequest request) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        SystemSnapshot snapshot = new SystemSnapshot();
        snapshot.setId(snapshotId);
        snapshot.setAppId(appId);
        snapshot.setProjectId(projectId);
        snapshot.setTitle(request.getTitle());
        snapshot.setDescribe(request.getDescribe());
        snapshot.setVersion(request.getVersion());
        snapshot.setVersionCycle(request.getVersionCycle());
        snapshot.setLabels(normalizeArray(request.getLabels()));
        snapshot.setPrincipals(normalizeArray(request.getPrincipals()));
        systemSnapshotService.saveBasic(projectId, user.getId(), snapshot);
        return new ResultNotified<>(true, "保存成功", snapshotId);
    }

    @PostMapping("/apps/{appId}/snapshots/{snapshotId}/comments")
    public ResultNotified<String> addSystemSnapshotComment(@PathVariable String projectId,
                                                           @PathVariable String appId,
                                                           @PathVariable String snapshotId,
                                                           @SessionAttribute UserVo user,
                                                           @RequestBody SnapshotCommentRequest request) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        Assert.hasText(request.getContent(), "评论内容不能为空");
        appService.addDescribe(snapshotId, user.getId(), request.getContent().trim());
        return new ResultNotified<>(true, "添加成功", snapshotId);
    }

    @PostMapping("/apps/{appId}/snapshots/{snapshotId}/comments/delete")
    public ResultNotified<String> deleteSystemSnapshotComment(@PathVariable String projectId,
                                                              @PathVariable String appId,
                                                              @PathVariable String snapshotId,
                                                              @SessionAttribute UserVo user,
                                                              @RequestBody DeleteSnapshotCommentRequest request) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        Assert.hasText(request.getContent(), "评论内容不能为空");
        Assert.hasText(request.getDateTime(), "评论时间不能为空");
        appService.delDescribe(snapshotId, user.getId(), request.getContent(), request.getDateTime());
        return new ResultNotified<>(true, "评论删除成功", snapshotId);
    }

    @PostMapping("/apps/{appId}/snapshots/{snapshotId}/delete")
    public ResultNotified<String> deleteSystemSnapshot(@PathVariable String projectId,
                                                       @PathVariable String appId,
                                                       @PathVariable String snapshotId,
                                                       @SessionAttribute UserVo user) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        appService.deleteSnapshot(snapshotId);
        return new ResultNotified<>(true, "删除快照成功", snapshotId);
    }

    @PostMapping("/apps/{appId}/snapshots/directories/save")
    public ResultNotified<String> saveSystemSnapshotDirectory(@PathVariable String projectId,
                                                              @PathVariable String appId,
                                                              @SessionAttribute UserVo user,
                                                              @RequestBody SaveSnapshotDirectoryRequest request) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        SnapshotDirectory directory = new SnapshotDirectory();
        directory.setId(request.getId());
        directory.setParentId(StringUtils.hasText(request.getParentId()) ? request.getParentId() : "root");
        directory.setName(request.getName());
        SnapshotDirectory saved = appService.saveSnapshotDirectory(projectId, appId, directory);
        return new ResultNotified<>(true, "目录保存成功", saved.getId() == null ? null : String.valueOf(saved.getId()));
    }

    @PostMapping("/apps/{appId}/snapshots/directories/{directoryId}/delete")
    public ResultNotified<String> deleteSystemSnapshotDirectory(@PathVariable String projectId,
                                                                @PathVariable String appId,
                                                                @PathVariable Integer directoryId,
                                                                @SessionAttribute UserVo user) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        try {
            appService.deleteSnapshotDirectory(projectId, appId, directoryId);
            return new ResultNotified<>(true, "目录删除成功", String.valueOf(directoryId));
        } catch (BusinessException e) {
            ResultNotified<String> result = new ResultNotified<>(false, e.getMessage());
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    @PostMapping("/apps/{appId}/snapshots/{snapshotId}/usecases/bind")
    public ResultNotified<Integer> bindSystemSnapshotUsecases(@PathVariable String projectId,
                                                              @PathVariable String appId,
                                                              @PathVariable String snapshotId,
                                                              @SessionAttribute UserVo user,
                                                              @RequestBody BindUsecasesRequest request) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        snapshotApiSupport.assertSystemSnapshotScope(projectId, appId, snapshot);
        String[] usecaseIds = normalizeArray(request.getUsecaseIds());
        usecaseService.bindSystemSnapshotToUsecases(projectId, user.getId(), snapshotId, usecaseIds);
        return new ResultNotified<>(true, "测试用例关联已更新", usecaseIds == null ? 0 : usecaseIds.length);
    }

    @PostMapping("/apps/{appId}/snapshots/usecases/batch-bind")
    public ResultNotified<Integer> batchBindSystemSnapshotUsecases(@PathVariable String projectId,
                                                                   @PathVariable String appId,
                                                                   @SessionAttribute UserVo user,
                                                                   @RequestBody BatchBindUsecasesRequest request) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        String[] snapshotIds = normalizeArray(request.getSnapshotIds());
        Assert.isTrue(!ArrayUtils.isEmpty(snapshotIds), "snapshotIds不能为空");
        String[] usecaseIds = normalizeArray(request.getUsecaseIds());
        for (String snapshotId : snapshotIds) {
            SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
            snapshotApiSupport.assertSystemSnapshotScope(projectId, appId, snapshot);
            usecaseService.bindSystemSnapshotToUsecases(projectId, user.getId(), snapshotId, usecaseIds);
        }
        return new ResultNotified<>(true, "批量关联成功", snapshotIds.length);
    }

    @GetMapping("/snapshots/my")
    public ResultNotified<MySnapshotListPayload> mySnapshots(@PathVariable String projectId,
                                                             @SessionAttribute UserVo user,
                                                             @RequestParam(required = false) String sort,
                                                             @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String labels) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        MySnapshotListPayload payload = mySnapshotPayloadService.buildListPayload(projectId, user, sort, keyword, labels);
        return new ResultNotified<>(true, "获取我的快照成功", payload);
    }

    @GetMapping("/snapshots/my/{snapshotId}")
    public ResultNotified<MySnapshotDetailPayload> mySnapshotDetail(@PathVariable String projectId,
                                                                    @PathVariable String snapshotId,
        @SessionAttribute UserVo user) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        MySnapshotDetailPayload payload = mySnapshotPayloadService.buildDetailPayload(projectId, snapshotId, user);
        return new ResultNotified<>(true, "获取我的快照详情成功", payload);
    }

    @GetMapping("/snapshots/my/{snapshotId}/report")
    public ResultNotified<MySnapshotReportPayload> mySnapshotReport(@PathVariable String projectId,
                                                                    @PathVariable String snapshotId,
        @SessionAttribute UserVo user) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        MySnapshotReportPayload payload = mySnapshotPayloadService.buildReportPayload(projectId, snapshotId, user);
        return new ResultNotified<>(true, "获取我的快照覆盖率报告成功", payload);
    }

    @GetMapping("/snapshots/my/{snapshotId}/report/code")
    public ResultNotified<MySnapshotCodePayload> mySnapshotCode(@PathVariable String projectId,
                                                                @PathVariable String snapshotId,
                                                                @SessionAttribute UserVo user,
        @RequestParam String className) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        MySnapshotCodePayload payload = mySnapshotPayloadService.buildCodePayload(projectId, snapshotId, user, className);
        return new ResultNotified<>(true, "获取我的快照源码详情成功", payload);
    }

    @GetMapping("/snapshots/my/code-report")
    public ResultNotified<MySnapshotCodeReportPayload> mySnapshotsCodeReport(@PathVariable String projectId,
                                                                             @SessionAttribute UserVo user,
                                                                             @RequestParam(required = false) String sort,
        @RequestParam(required = false) String keyword) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        MySnapshotCodeReportPayload payload = mySnapshotPayloadService.buildCodeReportPayload(projectId, user, sort, keyword);
        return new ResultNotified<>(true, "获取我的快照代码报告成功", payload);
    }

    @GetMapping("/snapshots/my/code")
    public ResultNotified<MySnapshotAggregateCodePayload> mySnapshotsCode(@PathVariable String projectId,
                                                                          @SessionAttribute UserVo user,
                                                                          @RequestParam String appId,
        @RequestParam String className) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        MySnapshotAggregateCodePayload payload = mySnapshotPayloadService.buildAggregateCodePayload(projectId, user, appId, className);
        return new ResultNotified<>(true, "获取我的快照源码详情成功", payload);
    }

    @GetMapping("/snapshots/my/{snapshotId}/graph")
    public ResultNotified<GraphView> mySnapshotGraph(@PathVariable String projectId,
                                                     @PathVariable String snapshotId,
                                                     @SessionAttribute UserVo user) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        SnapshotVo snapshot = snapshotService.get(snapshotId);
        Assert.notNull(snapshot, "快照不存在");
        Assert.isTrue(snapshot.getProjectId() == null || projectId.equals(snapshot.getProjectId()), "快照不属于当前项目");
        GraphView graphView = traceGraphViewService.buildGraphView(projectId, snapshot.getTraceId());
        return new ResultNotified<>(true, "获取我的快照链路图成功", graphView);
    }

    @GetMapping("/snapshots/my/{snapshotId}/graph/nodes/{nodeId}")
    public ResultNotified<GraphNodeDetailPayload> mySnapshotGraphNode(@PathVariable String projectId,
                                                                      @PathVariable String snapshotId,
                                                                      @PathVariable String nodeId,
                                                                      @SessionAttribute UserVo user) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        SnapshotVo snapshot = snapshotService.get(snapshotId);
        Assert.notNull(snapshot, "快照不存在");
        Assert.isTrue(snapshot.getProjectId() == null || projectId.equals(snapshot.getProjectId()), "快照不属于当前项目");
        GraphNode graphNode = traceGraphViewService.getGraphNode(projectId, snapshot.getTraceId(), nodeId);
        Assert.notNull(graphNode, "找不到节点 id=" + nodeId);
        return new ResultNotified<>(true, "获取我的快照链路节点详情成功", GraphNodeDetailMapper.toGraphNodeDetail(graphNode));
    }

    @PostMapping("/snapshots/my/{snapshotId}/basic")
    public ResultNotified<String> updateMySnapshotBasic(@PathVariable String projectId,
                                                        @PathVariable String snapshotId,
                                                        @SessionAttribute UserVo user,
                                                        @RequestBody UpdateMySnapshotBasicRequest request) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        com.oAT.web.esDao.entity.Snapshot snapshot = new com.oAT.web.esDao.entity.Snapshot();
        snapshot.setName(request.getName());
        snapshot.setDescribe(request.getDescribe());
        snapshot.setLabels(normalizeArray(request.getLabels()));
        snapshotService.doUpdate(snapshotId, snapshot);
        return new ResultNotified<>(true, "快照更新成功", snapshotId);
    }

    @PostMapping("/snapshots/my/{snapshotId}/share")
    public ResultNotified<String> updateMySnapshotShare(@PathVariable String projectId,
                                                        @PathVariable String snapshotId,
                                                        @SessionAttribute UserVo user,
                                                        @RequestBody UpdateShareRequest request) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        snapshotService.setShareState(user.getId(), snapshotId, Boolean.TRUE.equals(request.getShare()));
        return new ResultNotified<>(true, "共享状态已更新", snapshotId);
    }

    @PostMapping("/snapshots/my/{snapshotId}/usecases/bind")
    public ResultNotified<Integer> bindMySnapshotUsecases(@PathVariable String projectId,
                                                          @PathVariable String snapshotId,
                                                          @SessionAttribute UserVo user,
                                                          @RequestBody BindUsecasesRequest request) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        SnapshotVo snapshotVo = snapshotService.get(snapshotId);
        Assert.notNull(snapshotVo, "找不到快照 id=" + snapshotId);
        Assert.isTrue(snapshotVo.getProjectId() == null || projectId.equals(snapshotVo.getProjectId()), "快照不属于当前项目");
        String[] usecaseIds = normalizeArray(request.getUsecaseIds());
        usecaseService.bindSnapshotToUsecases(projectId, user.getId(), snapshotId, usecaseIds);
        return new ResultNotified<>(true, "测试用例关联已更新", usecaseIds == null ? 0 : usecaseIds.length);
    }

    @PostMapping("/snapshots/my/usecases/batch-bind")
    public ResultNotified<Integer> batchBindMySnapshotUsecases(@PathVariable String projectId,
                                                               @SessionAttribute UserVo user,
                                                               @RequestBody BatchBindUsecasesRequest request) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        String[] snapshotIds = normalizeArray(request.getSnapshotIds());
        String[] usecaseIds = normalizeArray(request.getUsecaseIds());
        usecaseService.batchAppendSnapshotsToUsecases(projectId, user.getId(), snapshotIds, usecaseIds);
        return new ResultNotified<>(true, "批量关联成功", snapshotIds == null ? 0 : snapshotIds.length);
    }

    @PostMapping("/snapshots/my/{snapshotId}/delete")
    public ResultNotified<String> deleteMySnapshot(@PathVariable String projectId,
                                                   @PathVariable String snapshotId,
                                                   @SessionAttribute UserVo user) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        snapshotService.deleteById(snapshotId);
        return new ResultNotified<>(true, "快照删除成功", snapshotId);
    }

    @PostMapping("/snapshots/my/{snapshotId}/save-as-system-snapshot")
    public ResultNotified<String> saveMySnapshotAsSystemSnapshot(@PathVariable String projectId,
                                                                  @PathVariable String snapshotId,
                                                                  @SessionAttribute UserVo user,
                                                                  HttpSession session,
                                                                  @RequestBody SaveAsSystemSnapshotRequest request) {
        snapshotApiSupport.ensureProjectAccess(projectId, user);
        try {
            SystemSnapshot saved = snapshotSaveAsSystemService.saveAsSystemSnapshot(projectId, user.getId(), snapshotId, session, request);
            return new ResultNotified<>(true, "系统快照已保存", saved.getId());
        } catch (Exception e) {
            ResultNotified<String> result = new ResultNotified<>(false, "保存系统快照失败");
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String[] normalizeArray(List<String> values) {
        if (values == null) {
            return null;
        }
        List<String> normalized = values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        return normalized.isEmpty() ? null : normalized.toArray(new String[0]);
    }

}
