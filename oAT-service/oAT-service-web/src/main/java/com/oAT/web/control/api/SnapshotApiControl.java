package com.oAT.web.control.api;

import com.oAT.web.common.DateUtil;
import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.Application;
import com.oAT.agent.model.CKSqlTraceNode;
import com.oAT.agent.model.DubboTraceNode;
import com.oAT.agent.model.FeignTraceNode;
import com.oAT.agent.model.HttpClientTraceNode;
import com.oAT.agent.model.KafkaMQTraceNode;
import com.oAT.agent.model.RabbitMQTraceNode;
import com.oAT.agent.model.RedisTraceNode;
import com.oAT.agent.model.RocketMQProducerTraceNode;
import com.oAT.agent.model.SofaRpcTraceNode;
import com.oAT.agent.model.SqlTraceNode;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.common.SqlParseInfo;
import com.oAT.web.control.TraceGraphParse;
import com.oAT.web.control.entity.ApplicationGraphNode;
import com.oAT.web.control.entity.ClientGraphNode;
import com.oAT.web.control.entity.DatabaseGraphNode;
import com.oAT.web.control.entity.GraphNode;
import com.oAT.web.control.entity.GraphView;
import com.oAT.web.control.entity.RedisGraphNode;
import com.oAT.web.control.entity.SqlTraceGroup;
import com.oAT.web.domain.RemoteCallResolver;
import com.oAT.web.esDao.ApiEndpointRepository;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.ChangeLog;
import com.oAT.web.esDao.entity.Comment;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.esDao.entity.SnapshotDirectory;
import com.oAT.web.esDao.entity.StaticSourceInfo;
import com.oAT.web.esDao.entity.StaticSourceMethodInfo;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.exceptions.BusinessException;
import com.oAT.web.service.AppService;
import com.oAT.web.service.CoverageService;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.SystemSnapshotService;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.UserService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.LableType;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.SnapshotVo;
import com.oAT.web.service.entity.UsecaseVo;
import com.oAT.web.service.entity.UserVo;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class SnapshotApiControl {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final SnapshotService snapshotService;
    private final SystemSnapshotService systemSnapshotService;
    private final ProjectService projectService;
    private final UserService userService;
    private final AppService appService;
    private final UsecaseService usecaseService;
    private final StaticInfoRepository staticInfoRepository;
    private final CoverageService coverageService;
    private final ApiEndpointRepository apiEndpointRepository;
    private final ClientSessionService clientSessionService;

    public SnapshotApiControl(SnapshotService snapshotService,
                              SystemSnapshotService systemSnapshotService,
                              ProjectService projectService,
                              UserService userService,
                              AppService appService,
                              UsecaseService usecaseService,
                              StaticInfoRepository staticInfoRepository,
                              CoverageService coverageService,
                              ApiEndpointRepository apiEndpointRepository,
                              ClientSessionService clientSessionService) {
        this.snapshotService = snapshotService;
        this.systemSnapshotService = systemSnapshotService;
        this.projectService = projectService;
        this.userService = userService;
        this.appService = appService;
        this.usecaseService = usecaseService;
        this.staticInfoRepository = staticInfoRepository;
        this.coverageService = coverageService;
        this.apiEndpointRepository = apiEndpointRepository;
        this.clientSessionService = clientSessionService;
    }


    @PostMapping("/snapshots/my/save")
    public ResultNotified<SnapshotVo> saveMySnapshot(@PathVariable String projectId,
                                                     @SessionAttribute UserVo user,
                                                     HttpSession session,
                                                     com.oAT.web.esDao.entity.Snapshot snapshot,
                                                     @RequestParam(value = "autoSave", required = false, defaultValue = "false") boolean autoSave) {
        ensureProjectAccess(projectId, user);
        try {
            Assert.notNull(snapshot, "snapshot不能为空");
            Assert.hasText(snapshot.getTraceId(), "traceId不能为空");
            snapshot.setProjectId(projectId);
            snapshot.setCreateUser(user.getId());
            Map<String, TraceNode> nodes = getTraceNodesForSave(snapshot.getTraceId(), session);
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
        ensureProjectAccess(projectId, user);
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");

        String currentDirectory = StringUtils.hasText(directoryId) ? directoryId : "root";
        String effectiveSort = StringUtils.hasText(sort) ? sort : "updateTime";

        List<SnapshotDirectory> dirs = Arrays.stream(ObjectUtils.isEmpty(app.getSnapshotDirs()) ? new SnapshotDirectory[0] : app.getSnapshotDirs())
                .filter(dir -> currentDirectory.equals(dir.getParentId()))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .collect(Collectors.toList());

        List<SystemSnapshot> snapshots = new ArrayList<>(systemSnapshotService.findBy(projectId, appId, currentDirectory, keyword));
        snapshots = snapshots.stream().sorted((a, b) -> {
            if ("name".equals(effectiveSort)) {
                return safeString(a.getTitle()).compareToIgnoreCase(safeString(b.getTitle()));
            }
            Date left = a.getVersionLastUpdate();
            Date right = b.getVersionLastUpdate();
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return 1;
            }
            if (right == null) {
                return -1;
            }
            return right.compareTo(left);
        }).collect(Collectors.toList());

        SystemSnapshotListPayload payload = new SystemSnapshotListPayload();
        payload.setApp(toAppSummary(app));
        payload.setApps(toAppSummaries(projectId));
        payload.setCurrentDirectory(currentDirectory);
        payload.setSort(effectiveSort);
        payload.setKeyword(keyword);
        payload.setDirectories(dirs.stream().map(this::toSnapshotDirectorySummary).collect(Collectors.toList()));
        payload.setDirectoryTiers(toSnapshotDirectorySummaries(appService.getDirectoryTiers(appId, currentDirectory)));
        payload.setSnapshots(snapshots.stream().map(this::toSystemSnapshotSummary).collect(Collectors.toList()));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        payload.setAllUsecases(collectAllProjectUsecases(projectId));
        return new ResultNotified<>(true, "获取系统快照列表成功", payload);
    }

    @GetMapping("/apps/{appId}/snapshots/{snapshotId}")
    public ResultNotified<SystemSnapshotDetailPayload> systemSnapshotDetail(@PathVariable String projectId,
                                                                            @PathVariable String appId,
                                                                            @PathVariable String snapshotId,
                                                                            @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        assertSystemSnapshotScope(projectId, appId, snapshot);

        SystemSnapshotDetailPayload payload = new SystemSnapshotDetailPayload();
        payload.setApp(toAppSummary(app));
        payload.setSnapshot(toSystemSnapshotSummary(snapshot));
        payload.setLabels(projectService.getLables(projectId, LableType.snapshot));
        payload.setSelectedLabelNames(optionalArray(snapshot.getLabels()));
        payload.setMembers(projectService.getProjectMembers(projectId));
        payload.setSelectedPrincipalIds(optionalArray(snapshot.getPrincipals()));
        payload.setUsecases(usecaseService.getUsecasesBySystemSnapshot(projectId, snapshotId));
        payload.setAllUsecases(collectAllProjectUsecases(projectId));
        payload.setDynamics(buildDynamics(snapshot));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取系统快照详情成功", payload);
    }

    @GetMapping("/apps/{appId}/snapshots/{snapshotId}/report")
    public ResultNotified<SystemSnapshotReportPayload> systemSnapshotReport(@PathVariable String projectId,
                                                                            @PathVariable String appId,
                                                                            @PathVariable String snapshotId,
                                                                            @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        assertSystemSnapshotScope(projectId, appId, snapshot);

        SystemSnapshotReportPayload payload = new SystemSnapshotReportPayload();
        payload.setApp(toAppSummary(app));
        payload.setSnapshot(toSystemSnapshotSummary(snapshot));
        payload.setReport(toCoverageReportSummary(snapshot.getCoverageReport()));
        payload.setClassStats(buildSystemSnapshotClassStats(appId, snapshot));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取系统快照覆盖率报告成功", payload);
    }

    @GetMapping("/apps/{appId}/snapshots/{snapshotId}/report/code")
    public ResultNotified<SystemSnapshotCodePayload> systemSnapshotCode(@PathVariable String projectId,
                                                                        @PathVariable String appId,
                                                                        @PathVariable String snapshotId,
                                                                        @SessionAttribute UserVo user,
                                                                        @RequestParam String className) {
        ensureProjectAccess(projectId, user);
        Assert.hasText(className, "className不能为空");
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        assertSystemSnapshotScope(projectId, appId, snapshot);

        ClassCoverageIndex classCoverage = buildSystemSnapshotClassCoverage(appId, snapshot, className);
        SystemSnapshotCodePayload payload = new SystemSnapshotCodePayload();
        payload.setApp(toAppSummary(app));
        payload.setSnapshot(toSystemSnapshotSummary(snapshot));
        payload.setClassName(className);
        payload.setMethods(toMethodCoverageSummaries(classCoverage.getMethods()));
        payload.setColoredSourceHtml(coverageService.getColoredSource(appId, classCoverage));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取系统快照源码详情成功", payload);
    }

    @GetMapping("/apps/{appId}/snapshots/{snapshotId}/graph")
    public ResultNotified<GraphView> systemSnapshotGraph(@PathVariable String projectId,
                                                         @PathVariable String appId,
                                                         @PathVariable String snapshotId,
                                                         @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        assertSystemSnapshotScope(projectId, appId, snapshot);
        GraphView graphView = new TraceGraphParse(buildTraceNodeMap(snapshot.getTraceId()), buildRemoteCallResolver(projectId)).getGraphView();
        return new ResultNotified<>(true, "获取系统快照链路图成功", graphView);
    }

    @GetMapping("/apps/{appId}/snapshots/{snapshotId}/graph/nodes/{nodeId}")
    public ResultNotified<GraphNodeDetailPayload> systemSnapshotGraphNode(@PathVariable String projectId,
                                                                          @PathVariable String appId,
                                                                          @PathVariable String snapshotId,
                                                                          @PathVariable String nodeId,
                                                                          @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        assertSystemSnapshotScope(projectId, appId, snapshot);
        TraceGraphParse parse = new TraceGraphParse(buildTraceNodeMap(snapshot.getTraceId()), buildRemoteCallResolver(projectId));
        GraphNode graphNode = parse.getGraphNode(nodeId);
        Assert.notNull(graphNode, "找不到节点 id=" + nodeId);
        return new ResultNotified<>(true, "获取链路节点详情成功", toGraphNodeDetail(graphNode));
    }

    @PostMapping("/apps/{appId}/snapshots/{snapshotId}/report/calculate")
    public ResultNotified<String> calculateSystemSnapshotReport(@PathVariable String projectId,
                                                                @PathVariable String appId,
                                                                @PathVariable String snapshotId,
                                                                @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        assertSystemSnapshotScope(projectId, appId, snapshot);
        systemSnapshotService.asyncCalculateCoverage(snapshotId);
        return new ResultNotified<>(true, "覆盖率计算任务已启动", snapshotId);
    }

    @PostMapping("/apps/{appId}/snapshots/{snapshotId}/basic")
    public ResultNotified<String> updateSystemSnapshotBasic(@PathVariable String projectId,
                                                            @PathVariable String appId,
                                                            @PathVariable String snapshotId,
                                                            @SessionAttribute UserVo user,
                                                            @RequestBody UpdateSystemSnapshotBasicRequest request) {
        ensureProjectAccess(projectId, user);
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
        ensureProjectAccess(projectId, user);
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
        ensureProjectAccess(projectId, user);
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
        ensureProjectAccess(projectId, user);
        appService.deleteSnapshot(snapshotId);
        return new ResultNotified<>(true, "删除快照成功", snapshotId);
    }

    @PostMapping("/apps/{appId}/snapshots/directories/save")
    public ResultNotified<String> saveSystemSnapshotDirectory(@PathVariable String projectId,
                                                              @PathVariable String appId,
                                                              @SessionAttribute UserVo user,
                                                              @RequestBody SaveSnapshotDirectoryRequest request) {
        ensureProjectAccess(projectId, user);
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
        ensureProjectAccess(projectId, user);
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
        ensureProjectAccess(projectId, user);
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        assertSystemSnapshotScope(projectId, appId, snapshot);
        String[] usecaseIds = normalizeArray(request.getUsecaseIds());
        usecaseService.bindSystemSnapshotToUsecases(projectId, user.getId(), snapshotId, usecaseIds);
        return new ResultNotified<>(true, "测试用例关联已更新", usecaseIds == null ? 0 : usecaseIds.length);
    }

    @PostMapping("/apps/{appId}/snapshots/usecases/batch-bind")
    public ResultNotified<Integer> batchBindSystemSnapshotUsecases(@PathVariable String projectId,
                                                                   @PathVariable String appId,
                                                                   @SessionAttribute UserVo user,
                                                                   @RequestBody BatchBindUsecasesRequest request) {
        ensureProjectAccess(projectId, user);
        String[] snapshotIds = normalizeArray(request.getSnapshotIds());
        Assert.isTrue(!ArrayUtils.isEmpty(snapshotIds), "snapshotIds不能为空");
        String[] usecaseIds = normalizeArray(request.getUsecaseIds());
        for (String snapshotId : snapshotIds) {
            SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
            assertSystemSnapshotScope(projectId, appId, snapshot);
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
        ensureProjectAccess(projectId, user);
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, user.getId(), StringUtils.hasText(sort) ? sort : null, keyword);
        String[] labelFilters = StringUtils.hasText(labels) ? labels.split(",") : new String[0];
        if (ArrayUtils.isNotEmpty(labelFilters)) {
            snapshots = snapshots.stream()
                    .filter(snapshot -> containsAllLabels(snapshot.getLabels(), labelFilters))
                    .collect(Collectors.toList());
        }
        MySnapshotListPayload payload = new MySnapshotListPayload();
        payload.setSnapshots(snapshots);
        payload.setAllUsecases(collectAllProjectUsecases(projectId));
        payload.setSnapshotLabels(projectService.getLables(projectId, LableType.snapshot));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取我的快照成功", payload);
    }

    @GetMapping("/snapshots/my/{snapshotId}")
    public ResultNotified<MySnapshotDetailPayload> mySnapshotDetail(@PathVariable String projectId,
                                                                    @PathVariable String snapshotId,
                                                                    @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        SnapshotVo snapshot = snapshotService.get(snapshotId);
        Assert.notNull(snapshot, "我的快照不存在");
        Assert.isTrue(projectId.equals(snapshot.getProjectId()), "快照不属于当前项目");
        MySnapshotDetailPayload payload = new MySnapshotDetailPayload();
        payload.setSnapshot(snapshot);
        payload.setCreateUser(toUserSummary(userService.getUser(snapshot.getCreateUser())));
        payload.setLabels(ArrayUtils.isNotEmpty(snapshot.getLabels())
                ? projectService.getLables(projectId, LableType.snapshot, snapshot.getLabels())
                : new ArrayList<>());
        payload.setUsecases(usecaseService.getUsecasesBySnapshot(projectId, snapshotId));
        payload.setAllUsecases(collectAllProjectUsecases(projectId));
        payload.setShareUrl("/share/snapshot/" + snapshotId);
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取我的快照详情成功", payload);
    }

    @GetMapping("/snapshots/my/{snapshotId}/report")
    public ResultNotified<MySnapshotReportPayload> mySnapshotReport(@PathVariable String projectId,
                                                                    @PathVariable String snapshotId,
                                                                    @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        SnapshotVo snapshot = snapshotService.get(snapshotId);
        Assert.notNull(snapshot, "快照不存在");
        Assert.isTrue(projectId.equals(snapshot.getProjectId()), "快照不属于当前项目");

        MySnapshotReportAggregate aggregate = buildMySnapshotReportAggregate(snapshot);
        MySnapshotReportPayload payload = new MySnapshotReportPayload();
        payload.setSnapshot(snapshot);
        payload.setReport(aggregate.getSummary());
        payload.setClassStats(aggregate.getClassStats());
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取我的快照覆盖率报告成功", payload);
    }

    @GetMapping("/snapshots/my/{snapshotId}/report/code")
    public ResultNotified<MySnapshotCodePayload> mySnapshotCode(@PathVariable String projectId,
                                                                @PathVariable String snapshotId,
                                                                @SessionAttribute UserVo user,
                                                                @RequestParam String className) {
        ensureProjectAccess(projectId, user);
        Assert.hasText(className, "className不能为空");
        SnapshotVo snapshot = snapshotService.get(snapshotId);
        Assert.notNull(snapshot, "快照不存在");
        Assert.isTrue(projectId.equals(snapshot.getProjectId()), "快照不属于当前项目");

        String appId = resolveMySnapshotAppId(snapshot);
        Assert.hasText(appId, "无法识别当前快照所属应用");

        ClassCoverageIndex classCoverage = buildMySnapshotClassCoverage(appId, snapshot, className);
        MySnapshotCodePayload payload = new MySnapshotCodePayload();
        payload.setSnapshot(snapshot);
        payload.setApp(toAppSummary(appService.getApp(appId)));
        payload.setClassName(className);
        payload.setMethods(toMethodCoverageSummaries(classCoverage.getMethods()));
        payload.setColoredSourceHtml(coverageService.getColoredSource(appId, classCoverage));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取我的快照源码详情成功", payload);
    }

    @GetMapping("/snapshots/my/code-report")
    public ResultNotified<MySnapshotCodeReportPayload> mySnapshotsCodeReport(@PathVariable String projectId,
                                                                             @SessionAttribute UserVo user,
                                                                             @RequestParam(required = false) String sort,
                                                                             @RequestParam(required = false) String keyword) {
        ensureProjectAccess(projectId, user);
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, user.getId(), StringUtils.hasText(sort) ? sort : null, keyword);
        MySnapshotCodeReportAggregate aggregate = buildMySnapshotsCodeReportAggregate(snapshots);

        MySnapshotCodeReportPayload payload = new MySnapshotCodeReportPayload();
        payload.setAppId(aggregate.getAppId());
        payload.setReport(aggregate.getSummary());
        payload.setClassStats(aggregate.getClassStats());
        payload.setCodeRelationships(aggregate.getCodeRelationships());
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取我的快照代码报告成功", payload);
    }

    @GetMapping("/snapshots/my/code")
    public ResultNotified<MySnapshotAggregateCodePayload> mySnapshotsCode(@PathVariable String projectId,
                                                                          @SessionAttribute UserVo user,
                                                                          @RequestParam String appId,
                                                                          @RequestParam String className) {
        ensureProjectAccess(projectId, user);
        Assert.hasText(className, "className不能为空");
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, user.getId(), null);
        ClassCoverageIndex classCoverage = buildMySnapshotsClassCoverage(appId, snapshots, className);

        MySnapshotAggregateCodePayload payload = new MySnapshotAggregateCodePayload();
        payload.setApp(toAppSummary(appService.getApp(appId)));
        payload.setClassName(className);
        payload.setMethods(toMethodCoverageSummaries(classCoverage.getMethods()));
        payload.setColoredSourceHtml(coverageService.getColoredSource(appId, classCoverage));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取我的快照源码详情成功", payload);
    }

    @GetMapping("/snapshots/my/{snapshotId}/graph")
    public ResultNotified<GraphView> mySnapshotGraph(@PathVariable String projectId,
                                                     @PathVariable String snapshotId,
                                                     @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        SnapshotVo snapshot = snapshotService.get(snapshotId);
        Assert.notNull(snapshot, "快照不存在");
        Assert.isTrue(projectId.equals(snapshot.getProjectId()), "快照不属于当前项目");
        GraphView graphView = new TraceGraphParse(buildTraceNodeMap(snapshot.getTraceId()), buildRemoteCallResolver(projectId)).getGraphView();
        return new ResultNotified<>(true, "获取我的快照链路图成功", graphView);
    }

    @GetMapping("/snapshots/my/{snapshotId}/graph/nodes/{nodeId}")
    public ResultNotified<GraphNodeDetailPayload> mySnapshotGraphNode(@PathVariable String projectId,
                                                                      @PathVariable String snapshotId,
                                                                      @PathVariable String nodeId,
                                                                      @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        SnapshotVo snapshot = snapshotService.get(snapshotId);
        Assert.notNull(snapshot, "快照不存在");
        Assert.isTrue(projectId.equals(snapshot.getProjectId()), "快照不属于当前项目");
        TraceGraphParse parse = new TraceGraphParse(buildTraceNodeMap(snapshot.getTraceId()), buildRemoteCallResolver(projectId));
        GraphNode graphNode = parse.getGraphNode(nodeId);
        Assert.notNull(graphNode, "找不到节点 id=" + nodeId);
        return new ResultNotified<>(true, "获取我的快照链路节点详情成功", toGraphNodeDetail(graphNode));
    }

    @PostMapping("/snapshots/my/{snapshotId}/basic")
    public ResultNotified<String> updateMySnapshotBasic(@PathVariable String projectId,
                                                        @PathVariable String snapshotId,
                                                        @SessionAttribute UserVo user,
                                                        @RequestBody UpdateMySnapshotBasicRequest request) {
        ensureProjectAccess(projectId, user);
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
        ensureProjectAccess(projectId, user);
        snapshotService.setShareState(user.getId(), snapshotId, Boolean.TRUE.equals(request.getShare()));
        return new ResultNotified<>(true, "共享状态已更新", snapshotId);
    }

    @PostMapping("/snapshots/my/{snapshotId}/usecases/bind")
    public ResultNotified<Integer> bindMySnapshotUsecases(@PathVariable String projectId,
                                                          @PathVariable String snapshotId,
                                                          @SessionAttribute UserVo user,
                                                          @RequestBody BindUsecasesRequest request) {
        ensureProjectAccess(projectId, user);
        SnapshotVo snapshotVo = snapshotService.get(snapshotId);
        Assert.notNull(snapshotVo, "找不到快照 id=" + snapshotId);
        Assert.isTrue(projectId.equals(snapshotVo.getProjectId()), "快照不属于当前项目");
        String[] usecaseIds = normalizeArray(request.getUsecaseIds());
        usecaseService.bindSnapshotToUsecases(projectId, user.getId(), snapshotId, usecaseIds);
        return new ResultNotified<>(true, "测试用例关联已更新", usecaseIds == null ? 0 : usecaseIds.length);
    }

    @PostMapping("/snapshots/my/usecases/batch-bind")
    public ResultNotified<Integer> batchBindMySnapshotUsecases(@PathVariable String projectId,
                                                               @SessionAttribute UserVo user,
                                                               @RequestBody BatchBindUsecasesRequest request) {
        ensureProjectAccess(projectId, user);
        String[] snapshotIds = normalizeArray(request.getSnapshotIds());
        String[] usecaseIds = normalizeArray(request.getUsecaseIds());
        usecaseService.batchAppendSnapshotsToUsecases(projectId, user.getId(), snapshotIds, usecaseIds);
        return new ResultNotified<>(true, "批量关联成功", snapshotIds == null ? 0 : snapshotIds.length);
    }

    @PostMapping("/snapshots/my/{snapshotId}/delete")
    public ResultNotified<String> deleteMySnapshot(@PathVariable String projectId,
                                                   @PathVariable String snapshotId,
                                                   @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        snapshotService.deleteById(snapshotId);
        return new ResultNotified<>(true, "快照删除成功", snapshotId);
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
        java.util.Collection<TraceNode> storedNodes = snapshotService.getTraceNodes(traceId);
        if (storedNodes != null) {
            for (TraceNode node : storedNodes) {
                if (node != null && StringUtils.hasText(node.getTraceNodeId())) {
                    nodes.put(node.getTraceNodeId(), node);
                }
            }
        }
        return nodes;
    }

    private ProjectVo ensureProjectAccess(String projectId, UserVo user) {
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        Assert.notNull(project, "找不到指定项目,或者您没有该项目的访问权限");
        return project;
    }

    private void assertSystemSnapshotScope(String projectId, String appId, SystemSnapshot snapshot) {
        Assert.notNull(snapshot, "系统快照不存在");
        Assert.isTrue(projectId.equals(snapshot.getProjectId()), "系统快照不属于当前项目");
        Assert.isTrue(appId.equals(snapshot.getAppId()), "系统快照不属于当前应用");
    }

    private String resolveUserRole(String projectId, UserVo user) {
        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);
        for (ProjectMemberVo member : members) {
            if (user.getName() != null && user.getName().equals(member.getMemberName()) && member.getRole() != null) {
                return member.getRole().name();
            }
        }
        return ProjectMemberVo.Role.visitor.name();
    }

    private List<UsecaseVo> collectAllProjectUsecases(String projectId) {
        LinkedHashMap<String, UsecaseVo> result = new LinkedHashMap<>();
        Deque<String> directoryQueue = new ArrayDeque<>();
        directoryQueue.add("root");
        while (!directoryQueue.isEmpty()) {
            String directoryId = directoryQueue.poll();
            for (UsecaseVo usecaseVo : usecaseService.getUsecases(projectId, directoryId, "updateTime", null)) {
                result.putIfAbsent(usecaseVo.getId(), usecaseVo);
            }
            List<com.oAT.web.service.entity.UsecaseDirectoryVo> childDirectories = usecaseService.getDirectory(projectId, directoryId);
            if (childDirectories == null) {
                continue;
            }
            for (com.oAT.web.service.entity.UsecaseDirectoryVo directoryVo : childDirectories) {
                if (directoryVo != null && StringUtils.hasText(directoryVo.getId())) {
                    directoryQueue.add(directoryVo.getId());
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private FrontendContextApiControl.AppSummary toAppSummary(AppVo app) {
        FrontendContextApiControl.AppSummary summary = new FrontendContextApiControl.AppSummary();
        summary.setId(app.getId());
        summary.setName(app.getName());
        summary.setSrcName(app.getSrcName());
        summary.setDescribe(app.getDescribe());
        summary.setRange(app.getRange());
        summary.setOnlineCount(app.getOnlineCount());
        summary.setCurrentVersion(app.getCurrentVersion());
        summary.setCurrentBranch(app.getCurrentBranch());
        summary.setCurrentCommitId(app.getCurrentCommitId());
        summary.setRepoConfigured(StringUtils.hasText(app.getRepoAddress()));
        summary.setProbeAlertEnabled(Boolean.TRUE.equals(app.getProbeAlertEnabled()));
        return summary;
    }

    private List<FrontendContextApiControl.AppSummary> toAppSummaries(String projectId) {
        return appService.getAppList(projectId).stream().map(this::toAppSummary).collect(Collectors.toList());
    }

    private SnapshotDirectorySummary toSnapshotDirectorySummary(SnapshotDirectory directory) {
        SnapshotDirectorySummary summary = new SnapshotDirectorySummary();
        summary.setId(directory.getId() == null ? null : String.valueOf(directory.getId()));
        summary.setName(directory.getName());
        summary.setParentId(directory.getParentId());
        return summary;
    }

    private List<SnapshotDirectorySummary> toSnapshotDirectorySummaries(List<SnapshotDirectory> directories) {
        if (directories == null) {
            return new ArrayList<>();
        }
        return directories.stream().filter(Objects::nonNull).map(this::toSnapshotDirectorySummary).collect(Collectors.toList());
    }

    private SystemSnapshotSummary toSystemSnapshotSummary(SystemSnapshot snapshot) {
        SystemSnapshotSummary summary = new SystemSnapshotSummary();
        summary.setId(snapshot.getId());
        summary.setProjectId(snapshot.getProjectId());
        summary.setAppId(snapshot.getAppId());
        summary.setTraceId(snapshot.getTraceId());
        summary.setTitle(snapshot.getTitle());
        summary.setSubTitle(snapshot.getSubTitle());
        summary.setTopicImage(snapshot.getTopicImage());
        summary.setDescribe(snapshot.getDescribe());
        summary.setDirectory(snapshot.getDirectory());
        summary.setVersion(snapshot.getVersion());
        summary.setVersionCycle(snapshot.getVersionCycle());
        summary.setVersionLastUpdate(snapshot.getVersionLastUpdate());
        summary.setVersionLastUpdateText(formatDateTime(snapshot.getVersionLastUpdate()));
        summary.setVersionLastUpdateRelativeText(formatRelativeTime(snapshot.getVersionLastUpdate()));
        summary.setLabels(optionalArray(snapshot.getLabels()));
        summary.setPrincipals(optionalArray(snapshot.getPrincipals()));
        summary.setReportStatus(snapshot.getReportStatus());
        summary.setCreateTime(snapshot.getCreateTime());
        summary.setUpdateTime(snapshot.getUpdateTime());
        return summary;
    }

    private List<DynamicItemSummary> buildDynamics(SystemSnapshot snapshot) {
        List<String> ids = new ArrayList<>();
        if (snapshot.getChangeLogs() != null) {
            ids.addAll(Arrays.stream(snapshot.getChangeLogs()).map(ChangeLog::getUserId).collect(Collectors.toList()));
        }
        if (snapshot.getComments() != null) {
            ids.addAll(Arrays.stream(snapshot.getComments()).map(Comment::getUserId).collect(Collectors.toList()));
        }
        Map<String, UserVo> userMap = userService.getUsers(ids.toArray(new String[0])).stream()
                .collect(Collectors.toMap(UserVo::getId, it -> it, (left, right) -> left));

        List<DynamicItemSummary> result = new ArrayList<>();
        if (snapshot.getChangeLogs() != null) {
            for (ChangeLog log : snapshot.getChangeLogs()) {
                DynamicItemSummary item = new DynamicItemSummary();
                item.setType(log.getType());
                item.setTitle(resolveUserName(userMap.get(log.getUserId())));
                item.setDescribe(log.getContent());
                item.setTime(DateUtil.timeDifference(log.getTime()));
                item.setDate(log.getTime());
                result.add(item);
            }
        }
        if (snapshot.getComments() != null) {
            for (Comment comment : snapshot.getComments()) {
                DynamicItemSummary item = new DynamicItemSummary();
                item.setType("comment");
                item.setTitle(resolveUserName(userMap.get(comment.getUserId())));
                item.setDescribe(comment.getContent());
                item.setTime(DateUtil.timeDifference(comment.getTime()));
                item.setDate(comment.getTime());
                result.add(item);
            }
        }
        result.sort((a, b) -> {
            if (a.getDate() == null && b.getDate() == null) {
                return 0;
            }
            if (a.getDate() == null) {
                return 1;
            }
            if (b.getDate() == null) {
                return -1;
            }
            return b.getDate().compareTo(a.getDate());
        });
        return result;
    }

    private CoverageReportSummary toCoverageReportSummary(CoverageReportIndex report) {
        if (report == null) {
            return null;
        }
        CoverageReportSummary summary = new CoverageReportSummary();
        summary.setId(report.getId());
        summary.setAppId(report.getAppId());
        summary.setVersionNumber(report.getVersionNumber());
        summary.setRepoBranch(report.getRepoBranch());
        summary.setRepoCommitId(report.getRepoCommitId());
        summary.setCreateTime(report.getCreateTime());
        summary.setCreateTimeText(formatDateTime(report.getCreateTime()));
        summary.setLastProcessedTime(report.getLastProcessedTime());
        summary.setTotalClasses(report.getTotalClasses());
        summary.setCoveredClasses(report.getCoveredClasses());
        summary.setTotalMethods(report.getTotalMethods());
        summary.setCoveredMethods(report.getCoveredMethods());
        summary.setTotalBranches(report.getTotalBranches());
        summary.setCoveredBranches(report.getCoveredBranches());
        summary.setTotalBranchTargets(report.getTotalBranchTargets());
        summary.setCoveredBranchTargets(report.getCoveredBranchTargets());
        summary.setTotalLines(report.getTotalLines());
        summary.setCoveredLines(report.getCoveredLines());
        summary.setTotalComplexity(report.getTotalComplexity());
        summary.setSnapshotCount(report.getSnapshotCount());
        return summary;
    }

    private List<ClassCoverageSummary> buildSystemSnapshotClassStats(String appId, SystemSnapshot snapshot) {
        if (!StringUtils.hasText(appId) || snapshot == null || !StringUtils.hasText(snapshot.getTraceId())) {
            return new ArrayList<>();
        }

        Map<String, Set<String>> classMethods = new LinkedHashMap<>();
        Map<String, Set<Integer>> methodCoveredLines = new HashMap<>();
        Map<String, Set<Integer>> methodTotalLines = new HashMap<>();
        Map<String, Integer> methodComplexity = new HashMap<>();
        Map<String, Set<Integer>> methodTotalBranches = new HashMap<>();
        Map<String, Set<Integer>> methodCoveredBranches = new HashMap<>();
        Map<String, Set<String>> methodTotalBranchTargets = new HashMap<>();
        Map<String, Set<String>> methodCoveredBranchTargets = new HashMap<>();

        for (com.oAT.agent.model.TraceNode node : snapshotService.getTraceNodes(snapshot.getTraceId())) {
            if (!(node instanceof com.oAT.agent.model.HttpTraceNode)) {
                continue;
            }
            com.oAT.agent.model.StackNodeVo[] codeNodes = ((com.oAT.agent.model.HttpTraceNode) node).getCodeNodes();
            if (codeNodes == null) {
                continue;
            }
            for (com.oAT.agent.model.StackNodeVo codeNode : codeNodes) {
                if (codeNode == null || !StringUtils.hasText(codeNode.getClassName())) {
                    continue;
                }
                String methodKey = codeNode.getMethodName() + "#" + codeNode.getMethodDescriptor();
                classMethods.computeIfAbsent(codeNode.getClassName(), key -> new LinkedHashSet<>()).add(methodKey);

                if (codeNode.getDoLines() != null) {
                    methodCoveredLines.computeIfAbsent(methodKey, key -> new LinkedHashSet<>()).addAll(codeNode.getDoLines());
                }
                if (codeNode.getExecuteBranch() != null) {
                    methodCoveredBranches.computeIfAbsent(methodKey, key -> new LinkedHashSet<>()).addAll(codeNode.getExecuteBranch());
                }
                addBranchTargetKeys(methodCoveredBranchTargets, methodKey, codeNode.getExecuteBranchTargetProbeMap(), null);
            }
        }

        for (com.oAT.web.esDao.entity.StaticSourceInfo staticInfo : staticInfoRepository.findByAppId(appId)) {
            if (staticInfo.getClassInfo() == null || staticInfo.getClassInfo().getMethodMaps() == null) {
                continue;
            }
            for (com.oAT.web.esDao.entity.StaticSourceMethodInfo methodInfo : staticInfo.getClassInfo().getMethodMaps().values()) {
                String methodKey = methodInfo.getMethodName() + "#" + methodInfo.getMethodDesc();
                if (!methodCoveredLines.containsKey(methodKey) && !methodCoveredBranches.containsKey(methodKey)) {
                    continue;
                }
                methodTotalLines.computeIfAbsent(methodKey, key -> new LinkedHashSet<>())
                        .addAll(methodInfo.getMethodLineNumberMap() != null ? methodInfo.getMethodLineNumberMap() : Collections.emptyList());
                methodComplexity.put(methodKey, methodInfo.getCyclomaticComplexityMap() != null ? methodInfo.getCyclomaticComplexityMap() : 0);
                methodTotalBranches.computeIfAbsent(methodKey, key -> new LinkedHashSet<>())
                        .addAll(methodInfo.getBranchLineNumberSet() != null ? methodInfo.getBranchLineNumberSet() : Collections.emptyList());
                Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                        methodInfo.getBranchLineAndTargetProbeMap(),
                        decodeBranchTargetKeys(methodCoveredBranchTargets.get(methodKey)));
                addBranchTargetKeys(methodTotalBranchTargets, methodKey, normalizedTotalBranchTargetProbeMap, null);
                if (methodCoveredBranchTargets.containsKey(methodKey)) {
                    Set<String> normalizedKeys = new LinkedHashSet<>();
                    addBranchTargetKeysToSet(normalizedKeys, normalizedTotalBranchTargetProbeMap,
                            decodeBranchTargetKeys(methodCoveredBranchTargets.get(methodKey)));
                    methodCoveredBranchTargets.put(methodKey, normalizedKeys);
                }
            }
        }

        List<ClassCoverageSummary> classStats = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : classMethods.entrySet()) {
            String className = entry.getKey();
            Set<String> methods = entry.getValue();

            long totalMethods = methods.size();
            long coveredMethods = 0;
            long totalLines = 0;
            long coveredLines = 0;
            long totalBranches = 0;
            long coveredBranches = 0;
            long totalBranchTargets = 0;
            long coveredBranchTargets = 0;
            int totalComplexity = 0;

            for (String methodKey : methods) {
                totalLines += methodTotalLines.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredLines += methodCoveredLines.getOrDefault(methodKey, Collections.emptySet()).size();
                if (methodCoveredLines.containsKey(methodKey) && !methodCoveredLines.get(methodKey).isEmpty()) {
                    coveredMethods++;
                }
                totalComplexity += methodComplexity.getOrDefault(methodKey, 0);
                totalBranches += methodTotalBranches.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredBranches += methodCoveredBranches.getOrDefault(methodKey, Collections.emptySet()).size();
                totalBranchTargets += methodTotalBranchTargets.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredBranchTargets += methodCoveredBranchTargets.getOrDefault(methodKey, Collections.emptySet()).size();
            }

            ClassCoverageSummary summary = new ClassCoverageSummary();
            summary.setClassName(className);
            summary.setCoveredMethods(coveredMethods);
            summary.setTotalMethods(totalMethods);
            summary.setCoveredLines(coveredLines);
            summary.setTotalLines(totalLines);
            summary.setCoveredBranches(coveredBranches);
            summary.setTotalBranches(totalBranches);
            summary.setCoveredBranchTargets(coveredBranchTargets);
            summary.setTotalBranchTargets(totalBranchTargets);
            summary.setBranchRate(totalBranchTargets > 0 ? coveredBranchTargets * 100.0 / totalBranchTargets : 0);
            summary.setTotalComplexity(totalComplexity);
            classStats.add(summary);
        }

        classStats.sort((left, right) -> {
            double leftRate = left.getTotalLines() > 0 ? (double) left.getCoveredLines() / left.getTotalLines() : 0;
            double rightRate = right.getTotalLines() > 0 ? (double) right.getCoveredLines() / right.getTotalLines() : 0;
            int compare = Double.compare(rightRate, leftRate);
            if (compare != 0) {
                return compare;
            }
            return safeString(left.getClassName()).compareToIgnoreCase(safeString(right.getClassName()));
        });
        return classStats;
    }

    private ClassCoverageIndex buildSystemSnapshotClassCoverage(String appId, SystemSnapshot snapshot, String className) {
        ClassCoverageIndex aggregatedClassCov = new ClassCoverageIndex();
        aggregatedClassCov.setClassName(className);
        aggregatedClassCov.setAppId(appId);

        List<StaticSourceInfo> staticInfos = staticInfoRepository.findByAppId(appId);
        Map<String, StaticSourceMethodInfo> classStaticMethods = new HashMap<>();
        for (StaticSourceInfo staticInfo : staticInfos) {
            if (staticInfo.getClassInfo() == null
                    || !className.equals(staticInfo.getClassInfo().getClassName())
                    || staticInfo.getClassInfo().getMethodMaps() == null) {
                continue;
            }
            for (StaticSourceMethodInfo methodInfo : staticInfo.getClassInfo().getMethodMaps().values()) {
                String methodKey = methodInfo.getMethodName() + "#" + methodInfo.getMethodDesc();
                classStaticMethods.put(methodKey, methodInfo);
            }
            break;
        }

        Map<String, ClassCoverageIndex.MethodCoverageDetail> methodMap = new LinkedHashMap<>();
        for (com.oAT.agent.model.TraceNode node : snapshotService.getTraceNodes(snapshot.getTraceId())) {
            if (!(node instanceof com.oAT.agent.model.HttpTraceNode)) {
                continue;
            }
            com.oAT.agent.model.StackNodeVo[] codeNodes = ((com.oAT.agent.model.HttpTraceNode) node).getCodeNodes();
            if (codeNodes == null) {
                continue;
            }
            for (com.oAT.agent.model.StackNodeVo codeNode : codeNodes) {
                if (!className.equals(codeNode.getClassName())) {
                    continue;
                }
                String methodKey = codeNode.getMethodName() + "#" + codeNode.getMethodDescriptor();
                ClassCoverageIndex.MethodCoverageDetail detail = methodMap.computeIfAbsent(methodKey, key -> {
                    ClassCoverageIndex.MethodCoverageDetail newDetail = new ClassCoverageIndex.MethodCoverageDetail();
                    newDetail.setMethodName(codeNode.getMethodName());
                    newDetail.setMethodDesc(codeNode.getMethodDescriptor());
                    StaticSourceMethodInfo staticMethod = classStaticMethods.get(methodKey);
                    List<Integer> totalLines = staticMethod != null && staticMethod.getMethodLineNumberMap() != null
                            ? staticMethod.getMethodLineNumberMap()
                            : Collections.emptyList();
                    newDetail.setTotalLineNumbers(new ArrayList<>(totalLines));
                    newDetail.setTotalLines(totalLines.size());
                    newDetail.setTotalBranches(staticMethod != null && staticMethod.getTotalBranchCount() != null
                            ? staticMethod.getTotalBranchCount()
                            : 0);
                    Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                            staticMethod != null ? staticMethod.getBranchLineAndTargetProbeMap() : null,
                            codeNode.getExecuteBranchTargetProbeMap());
                    newDetail.setTotalBranchTargetProbeMap(normalizedTotalBranchTargetProbeMap);
                    newDetail.setTotalBranchTargets(countBranchTargets(normalizedTotalBranchTargetProbeMap));
                    newDetail.setCoveredBranchTargetProbeMap(new LinkedHashMap<>());
                    newDetail.setCoveredBranchTargets(0);
                    newDetail.setBranchRate(0.0);
                    newDetail.setComplexity(staticMethod != null && staticMethod.getCyclomaticComplexityMap() != null
                            ? staticMethod.getCyclomaticComplexityMap()
                            : 0);
                    newDetail.setCoveredLineNumbers(new ArrayList<>());
                    newDetail.setCoveredBranchLines(new ArrayList<>());
                    return newDetail;
                });

                if (codeNode.getDoLines() != null) {
                    Set<Integer> covered = new LinkedHashSet<>(detail.getCoveredLineNumbers());
                    covered.addAll(codeNode.getDoLines());
                    detail.setCoveredLineNumbers(new ArrayList<>(covered));
                    detail.setCoveredLines(detail.getCoveredLineNumbers().size());
                    detail.setCovered(detail.getCoveredLines() > 0);
                }
                if (codeNode.getExecuteBranch() != null) {
                    Set<Integer> coveredBranchLines = new LinkedHashSet<>(detail.getCoveredBranchLines());
                    coveredBranchLines.addAll(codeNode.getExecuteBranch());
                    detail.setCoveredBranchLines(new ArrayList<>(coveredBranchLines));
                    detail.setCoveredBranches(detail.getCoveredBranchLines().size());
                }
                if (codeNode.getExecuteBranchTargetProbeMap() != null) {
                    Map<String, List<Integer>> coveredBranchTargetProbeMap = mergeBranchTargetProbeMap(
                            detail.getCoveredBranchTargetProbeMap(),
                            codeNode.getExecuteBranchTargetProbeMap());
                    Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                            detail.getTotalBranchTargetProbeMap(),
                            coveredBranchTargetProbeMap);
                    coveredBranchTargetProbeMap = normalizeCoveredBranchTargetProbeMap(
                            normalizedTotalBranchTargetProbeMap,
                            coveredBranchTargetProbeMap);
                    detail.setTotalBranchTargetProbeMap(normalizedTotalBranchTargetProbeMap);
                    detail.setTotalBranchTargets(countBranchTargets(normalizedTotalBranchTargetProbeMap));
                    detail.setCoveredBranchTargetProbeMap(coveredBranchTargetProbeMap);
                    detail.setCoveredBranchTargets(countBranchTargets(coveredBranchTargetProbeMap));
                    detail.setBranchRate(calculateBranchRate(detail.getCoveredBranchTargets(), detail.getTotalBranchTargets()));
                }
            }
        }

        List<ClassCoverageIndex.MethodCoverageDetail> methods = new ArrayList<>(methodMap.values());
        methods.sort((left, right) -> {
            double leftRate = left.getTotalLines() > 0 ? (double) left.getCoveredLines() / left.getTotalLines() : 0;
            double rightRate = right.getTotalLines() > 0 ? (double) right.getCoveredLines() / right.getTotalLines() : 0;
            int compare = Double.compare(rightRate, leftRate);
            if (compare != 0) {
                return compare;
            }
            return safeString(left.getMethodName()).compareToIgnoreCase(safeString(right.getMethodName()));
        });

        aggregatedClassCov.setMethods(methods);
        aggregatedClassCov.setTotalMethods(methods.size());
        aggregatedClassCov.setCoveredMethods((int) methods.stream().filter(ClassCoverageIndex.MethodCoverageDetail::isCovered).count());
        aggregatedClassCov.setTotalLines(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getTotalLines).sum());
        aggregatedClassCov.setCoveredLines(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getCoveredLines).sum());
        aggregatedClassCov.setTotalBranches(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getTotalBranches).sum());
        aggregatedClassCov.setCoveredBranches(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getCoveredBranches).sum());
        aggregatedClassCov.setTotalBranchTargets(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getTotalBranchTargets).sum());
        aggregatedClassCov.setCoveredBranchTargets(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getCoveredBranchTargets).sum());
        aggregatedClassCov.setTotalComplexity(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getComplexity).sum());
        aggregatedClassCov.setMethodRate(calculateRate(aggregatedClassCov.getCoveredMethods(), aggregatedClassCov.getTotalMethods()));
        aggregatedClassCov.setLineRate(calculateRate(aggregatedClassCov.getCoveredLines(), aggregatedClassCov.getTotalLines()));
        aggregatedClassCov.setBranchRate(calculateRate(aggregatedClassCov.getCoveredBranchTargets(), aggregatedClassCov.getTotalBranchTargets()));
        return aggregatedClassCov;
    }

    private Map<String, com.oAT.agent.model.TraceNode> buildTraceNodeMap(String traceId) {
        Map<String, com.oAT.agent.model.TraceNode> nodeMap = new LinkedHashMap<>();
        for (com.oAT.agent.model.TraceNode node : snapshotService.getTraceNodes(traceId)) {
            if (node != null && StringUtils.hasText(node.getTraceNodeId())) {
                nodeMap.put(node.getTraceNodeId(), node);
            }
        }
        return nodeMap;
    }

    private boolean containsAllLabels(String[] source, String[] filters) {
        if (ArrayUtils.isEmpty(filters)) {
            return true;
        }
        if (ArrayUtils.isEmpty(source)) {
            return false;
        }
        Set<String> labelSet = Arrays.stream(source)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return Arrays.stream(filters)
                .filter(StringUtils::hasText)
                .allMatch(labelSet::contains);
    }

    private RemoteCallResolver buildRemoteCallResolver(String projectId) {
        List<AppVo> apps = appService.getAppList(projectId);
        List<com.oAT.web.esDao.entity.ApiEndpointIndex> endpoints = apps.stream()
                .flatMap(app -> apiEndpointRepository.findByAppIdOrderByEndpointTypeAscUrlAsc(app.getId()).stream())
                .collect(Collectors.toList());
        return new RemoteCallResolver(apps, endpoints);
    }

    public static GraphNodeDetailPayload toGraphNodeDetail(GraphNode graphNode) {
        GraphNodeDetailPayload payload = new GraphNodeDetailPayload();
        payload.setId(graphNode.getId());
        payload.setName(graphNode.getName());
        payload.setType(graphNode.getType() == null ? null : graphNode.getType().name());
        payload.setIp(graphNode.getIp());

        if (graphNode instanceof ClientGraphNode) {
            ClientGraphNode clientNode = (ClientGraphNode) graphNode;
            payload.setTitle(clientNode.getTitle());
            HttpTraceNode node = clientNode.getTraceNode();
            if (node != null) {
                payload.getStats().put("requestUrl", node.getRequestUrl());
                payload.getStats().put("requestMethod", node.getRequestMethod());
                payload.getStats().put("responseCode", node.getResponseCode());
                payload.getStats().put("useTime", node.getUseTime());
                payload.getStats().put("addressIp", node.getAddressIp());
                payload.getStats().put("error", node.getError() != null);

                GraphNodeDetailSection basic = new GraphNodeDetailSection("基本信息");
                addField(basic, "路径", node.getRequestUrl());
                addField(basic, "请求方法", node.getRequestMethod());
                addField(basic, "状态码", node.getResponseCode());
                addField(basic, "客户端IP", node.getClientIp());
                addField(basic, "Cookie", node.getRequestHeader() == null ? null : node.getRequestHeader().getCookie());
                addField(basic, "user-agent", node.getRequestHeader() == null ? null : node.getRequestHeader().getUserAgent());
                addField(basic, "Referer", node.getRequestHeader() == null ? null : node.getRequestHeader().getReferer());
                addField(basic, "Authorization", node.getRequestHeader() == null ? null : node.getRequestHeader().getAuthorization());
                addField(basic, "服务端IP", node.getServerIp());
                addField(basic, "服务端端口", node.getServerPort());
                addField(basic, "日期时间", formatMillis(node.getBeginTime()));
                addField(basic, "总耗时ms", node.getUseTime());
                addField(basic, "响应类型", node.getResponseType());
                addSection(payload, basic);

                GraphNodeDetailSection params = new GraphNodeDetailSection("参数信息");
                String[] names = node.getRequestParamNames();
                String[] values = node.getRequestParamValues();
                if (names != null) {
                    for (int i = 0; i < names.length; i++) {
                        addField(params, names[i], values != null && values.length > i ? values[i] : null);
                    }
                }
                params.setContent(defaultText(node.getRequestBody(), "无请求体"));
                addSection(payload, params);
                payload.setRequest(buildHttpRequestPayload(node));
                if (node.getError() != null) {
                    payload.getErrors().add(toErrorSummary(node.getError()));
                }
            }
        } else if (graphNode instanceof ApplicationGraphNode) {
            ApplicationGraphNode applicationNode = (ApplicationGraphNode) graphNode;
            payload.setTitle(applicationNode.getApplication() == null ? graphNode.getName() : applicationNode.getApplication().getAppName());
            payload.getStats().put("sessionId", applicationNode.getSessionId());
            payload.getStats().put("sqlCount", applicationNode.getSqlNodes().size() + applicationNode.getCKSqlNodes().size());
            payload.getStats().put("redisCount", applicationNode.getRedisNodes().size());
            payload.getStats().put("remoteCount",
                    applicationNode.getDubboNodes().size()
                            + applicationNode.getHttpClientNodes().size()
                            + applicationNode.getFeignNodes().size()
                            + applicationNode.getSofaRpcNodes().size()
                            + applicationNode.getRabbitMQNodes().size()
                            + applicationNode.getRocketMQProducerNodes().size()
                            + applicationNode.getKafkaMQNodes().size());
            payload.getStats().put("errorCount", applicationNode.getErrors().size());

            GraphNodeDetailSection basic = new GraphNodeDetailSection("基本信息");
            Application app = applicationNode.getApplication();
            addField(basic, "应用名称", app == null ? graphNode.getName() : app.getAppName());
            addField(basic, "应用ID", app == null ? null : app.getAppId());
            addField(basic, "工程名称", app == null ? null : app.getProjectSrcName());
            addField(basic, "应用IP", graphNode.getIp());
            addField(basic, "跟踪ID", applicationNode.getSessionId());
            addField(basic, "SQL数", applicationNode.getSqlNodes().size() + applicationNode.getCKSqlNodes().size());
            addField(basic, "远程调用数", payload.getStats().get("remoteCount"));
            addField(basic, "Redis执行数", applicationNode.getRedisNodes().size());
            addField(basic, "异常数", applicationNode.getErrors().size());
            addSection(payload, basic);

            appendSqlGroups(payload, applicationNode.getSqlGroups());
            applicationNode.getSqlNodes().forEach(node -> payload.getSqlStatements().add(toSqlSummary(node)));
            applicationNode.getCKSqlNodes().forEach(node -> payload.getSqlStatements().add(toSqlSummary(node)));
            appendTableOperations(payload, "增", applicationNode.getAdds());
            appendTableOperations(payload, "删", applicationNode.getDeletes());
            appendTableOperations(payload, "改", applicationNode.getUpdates());
            appendTableOperations(payload, "查", applicationNode.getSelects());

            applicationNode.getDubboNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
            applicationNode.getHttpClientNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
            applicationNode.getFeignNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
            applicationNode.getSofaRpcNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
            applicationNode.getRabbitMQNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
            applicationNode.getRocketMQProducerNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
            applicationNode.getKafkaMQNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
            applicationNode.getRedisNodes().forEach(node -> payload.getRedisCommands().add(toRedisCommandSummary(node)));
            applicationNode.getErrors().forEach(error -> payload.getErrors().add(toErrorSummary(error)));
            if (StringUtils.hasText(applicationNode.getLog())) {
                payload.setLogPreview(limitText(applicationNode.getLog(), 800));
                GraphNodeDetailSection log = new GraphNodeDetailSection("系统日志");
                log.setKind("log");
                log.setContent(limitText(applicationNode.getLog(), 10000));
                addSection(payload, log);
            }
        } else if (graphNode instanceof DatabaseGraphNode) {
            DatabaseGraphNode databaseNode = (DatabaseGraphNode) graphNode;
            payload.setTitle(graphNode.getName());
            payload.getStats().put("jdbcUrl", databaseNode.getJdbcUrl());
            payload.getStats().put("sqlCount", databaseNode.getSqlNodes().size() + databaseNode.getCksqlNodes().size());
            payload.getStats().put("errorCount", databaseNode.getErrors().size());
            payload.getStats().put("selectCount", databaseNode.getSelects().size());
            payload.getStats().put("insertCount", databaseNode.getAdds().size());
            payload.getStats().put("updateCount", databaseNode.getUpdates().size());
            payload.getStats().put("deleteCount", databaseNode.getDeletes().size());

            GraphNodeDetailSection basic = new GraphNodeDetailSection("基本信息");
            addField(basic, "数据库名称", databaseNode.getDatabase() == null ? null : databaseNode.getDatabase().getName());
            addField(basic, "CK数据库名称", databaseNode.getCkdatabase() == null ? null : databaseNode.getCkdatabase().getName());
            addField(basic, "数据库地址", databaseNode.getDatabase() == null ? null : databaseNode.getDatabase().getAddressIp());
            addField(basic, "数据库端口", databaseNode.getDatabase() == null ? null : databaseNode.getDatabase().getPort());
            addField(basic, "数据库类型", databaseNode.getDatabase() == null ? null : databaseNode.getDatabase().getType());
            addField(basic, "JDBC URL", databaseNode.getJdbcUrl());
            addField(basic, "SQL数", databaseNode.getSqlNodes().size() + databaseNode.getCksqlNodes().size());
            addField(basic, "异常数", databaseNode.getErrors().size());
            addSection(payload, basic);

            databaseNode.getSqlNodes().forEach(node -> payload.getSqlStatements().add(toSqlSummary(node)));
            databaseNode.getCksqlNodes().forEach(node -> payload.getSqlStatements().add(toSqlSummary(node)));
            appendTableOperations(payload, "增", databaseNode.getAdds());
            appendTableOperations(payload, "删", databaseNode.getDeletes());
            appendTableOperations(payload, "改", databaseNode.getUpdates());
            appendTableOperations(payload, "查", databaseNode.getSelects());
            databaseNode.getErrors().forEach(error -> payload.getErrors().add(toErrorSummary(error)));
        } else if (graphNode instanceof RedisGraphNode) {
            RedisGraphNode redisNode = (RedisGraphNode) graphNode;
            payload.setTitle(graphNode.getName());
            payload.getStats().put("commandCount", redisNode.getRedisNodes().size());
            payload.getStats().put("errorCount", redisNode.getErrors().size());
            if (redisNode.getRedisTraceNode() != null) {
                payload.getStats().put("host", redisNode.getRedisTraceNode().getHost());
                payload.getStats().put("port", redisNode.getRedisTraceNode().getPort());
                payload.setLogPreview(limitText(redisNode.getRedisTraceNode().getCmd(), 800));
            }
            GraphNodeDetailSection basic = new GraphNodeDetailSection("基本信息");
            addField(basic, "Redis名称", graphNode.getName());
            addField(basic, "Host", redisNode.getRedisTraceNode() == null ? null : redisNode.getRedisTraceNode().getHost());
            addField(basic, "Port", redisNode.getRedisTraceNode() == null ? null : redisNode.getRedisTraceNode().getPort());
            addField(basic, "Redis执行数", redisNode.getRedisNodes().size());
            addField(basic, "异常数", redisNode.getErrors().size());
            addSection(payload, basic);
            redisNode.getRedisNodes().forEach(node -> payload.getRedisCommands().add(toRedisCommandSummary(node)));
            redisNode.getErrors().forEach(error -> payload.getErrors().add(toErrorSummary(error)));
        }
        return payload;
    }

    private static void addSection(GraphNodeDetailPayload payload, GraphNodeDetailSection section) {
        if (!section.getFields().isEmpty() || StringUtils.hasText(section.getContent())) {
            payload.getSections().add(section);
        }
    }

    private static void addField(GraphNodeDetailSection section, String label, Object value) {
        if (!StringUtils.hasText(label)) {
            return;
        }
        section.getFields().add(new GraphNodeDetailField(label, value == null ? "" : String.valueOf(value)));
    }

    private static Map<String, Object> buildHttpRequestPayload(HttpTraceNode node) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("url", node.getRequestUrl());
        request.put("method", node.getRequestMethod());
        request.put("body", node.getRequestBody());
        request.put("params", buildParamMap(node.getRequestParamNames(), node.getRequestParamValues()));
        Map<String, Object> headers = new LinkedHashMap<>();
        if (node.getRequestHeader() != null) {
            headers.put("cookie", node.getRequestHeader().getCookie());
            headers.put("userAgent", node.getRequestHeader().getUserAgent());
            headers.put("referer", node.getRequestHeader().getReferer());
            headers.put("authorization", node.getRequestHeader().getAuthorization());
            headers.put("userHeader", node.getRequestHeader().getUserHeader());
        }
        request.put("headers", headers);
        return request;
    }

    private static Map<String, String> buildParamMap(String[] names, String[] values) {
        Map<String, String> params = new LinkedHashMap<>();
        if (names == null) {
            return params;
        }
        for (int i = 0; i < names.length; i++) {
            params.put(names[i], values != null && values.length > i ? values[i] : null);
        }
        return params;
    }

    private static void appendSqlGroups(GraphNodeDetailPayload payload, List<SqlTraceGroup> groups) {
        if (groups == null) {
            return;
        }
        groups.forEach(group -> {
            GraphSqlSummary summary = new GraphSqlSummary();
            summary.setSql(group.getSql());
            summary.setJdbcUrl(group.getJdbcUrl());
            summary.setAddressIp(group.getAddressIp());
            summary.setPort(group.getPort());
            summary.setDatabaseName(group.getName());
            summary.setDatabaseType(group.getType());
            summary.setExecutes(optionalArray(group.getExecutes()));
            summary.setParams(group.getParams() == null ? new ArrayList<>() : group.getParams());
            summary.setCount(group.getCount());
            payload.getSqlGroups().add(summary);
        });
    }

    private static GraphSqlSummary toSqlSummary(SqlTraceNode node) {
        GraphSqlSummary summary = new GraphSqlSummary();
        summary.setType(node.toType());
        summary.setSql(node.getSql());
        summary.setJdbcUrl(node.getJdbcUrl());
        summary.setAddressIp(node.getDatabase() == null ? null : node.getDatabase().getAddressIp());
        summary.setPort(node.getDatabase() == null ? null : node.getDatabase().getPort());
        summary.setDatabaseName(node.getDatabase() == null ? null : node.getDatabase().getName());
        summary.setDatabaseType(node.getDatabase() == null ? null : node.getDatabase().getType());
        summary.setExecutes(optionalArray(node.getExecutes()));
        summary.setParams(singleParams(node.getParams()));
        summary.setUseTime(node.getUseTime());
        summary.setBeginTime(formatMillis(node.getBeginTime()));
        summary.setError(node.getError() == null ? null : toErrorSummary(node.getError()));
        return summary;
    }

    private static GraphSqlSummary toSqlSummary(CKSqlTraceNode node) {
        GraphSqlSummary summary = new GraphSqlSummary();
        summary.setType(node.toType());
        summary.setSql(node.getSql());
        summary.setJdbcUrl(node.getJdbcUrl());
        summary.setAddressIp(node.getDatabase() == null ? null : node.getDatabase().getAddressIp());
        summary.setPort(node.getDatabase() == null ? null : node.getDatabase().getPort());
        summary.setDatabaseName(node.getDatabase() == null ? null : node.getDatabase().getName());
        summary.setDatabaseType(node.getDatabase() == null ? null : node.getDatabase().getType());
        summary.setExecutes(optionalArray(node.getExecutes()));
        summary.setParams(singleParams(node.getParams()));
        summary.setUseTime(node.getUseTime());
        summary.setBeginTime(formatMillis(node.getBeginTime()));
        summary.setError(node.getError() == null ? null : toErrorSummary(node.getError()));
        return summary;
    }

    private static List<String[]> singleParams(String[] params) {
        List<String[]> list = new ArrayList<>();
        if (params != null) {
            list.add(params);
        }
        return list;
    }

    private static void appendTableOperations(GraphNodeDetailPayload payload, String action, List<SqlParseInfo> operations) {
        if (operations == null) {
            return;
        }
        operations.forEach(operation -> {
            GraphTableOperationSummary summary = new GraphTableOperationSummary();
            summary.setAction(action);
            summary.setModel(operation.getModel());
            summary.setTableName(operation.getTableName());
            summary.setColumns(operation.getColumns());
            summary.setSql(operation.getSql());
            payload.getTableOperations().add(summary);
        });
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(DubboTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "Dubbo");
        summary.setUrl(firstText(node.getRemoteUrl(), node.getRemoteIp()));
        summary.setInterfaceName(node.getServiceInterface());
        summary.setMethodName(node.getServiceMethodName());
        summary.setRequest(node.getInParam());
        summary.setResponse(node.getOutParam());
        return summary;
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(HttpClientTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "HTTP Client");
        summary.setMethodName(node.getServiceMethod());
        summary.setUrl(node.getServiceURL());
        summary.setHeaders(node.getServiceHeaders());
        summary.setRequest(node.getServiceBody());
        return summary;
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(FeignTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "Feign");
        summary.setTitle(firstText(node.getRemoteFeignTargetName(), node.getServiceURL()));
        summary.setMethodName(firstText(node.getRemoteMethod(), node.getServiceMethod()));
        summary.setUrl(firstText(node.getRemoteUrl(), node.getServiceURL()));
        summary.setHeaders(node.getServiceHeaders());
        summary.setRequest(firstText(node.getRemoteBody(), node.getServiceBody()));
        summary.setResponse(node.getRemoteResponse());
        summary.setRemoteApp(toApplicationSummary(node.getRemoteApp()));
        return summary;
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(SofaRpcTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "SofaRPC");
        summary.setUrl(node.getDirectUrl());
        summary.setInterfaceName(firstText(node.getInterfaceName(), node.getTargetServiceUniqueName()));
        summary.setMethodName(node.getMethodName());
        summary.setRequest(node.getInParam());
        summary.setResponse(node.getOutParam());
        summary.getExtra().put("invokeType", node.getInvokeType());
        summary.setRemoteApp(toApplicationSummary(node.getRemoteApp()));
        return summary;
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(RabbitMQTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "RabbitMQ");
        summary.setTitle(firstText(node.getExchange(), node.getRoutingKey()));
        summary.setRequest(node.getBody());
        summary.getExtra().put("exchange", node.getExchange());
        summary.getExtra().put("routingKey", node.getRoutingKey());
        summary.setRemoteApp(toApplicationSummary(node.getRemoteApp()));
        return summary;
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(RocketMQProducerTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "RocketMQ");
        summary.setTitle(node.getProducer());
        summary.setRequest(node.getMessage());
        summary.setRemoteApp(toApplicationSummary(node.getRemoteApp()));
        return summary;
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(KafkaMQTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "KafkaMQ");
        summary.setRequest(node.getProducerRecord());
        summary.setRemoteApp(toApplicationSummary(node.getRemoteApp()));
        return summary;
    }

    private static GraphRemoteCallSummary baseRemoteCallSummary(TraceNode node, String type) {
        GraphRemoteCallSummary summary = new GraphRemoteCallSummary();
        summary.setType(type);
        summary.setTraceNodeId(node.getTraceNodeId());
        summary.setBeginTime(formatMillis(node.getBeginTime()));
        summary.setUseTime(node.getUseTime());
        summary.setAddressIp(node.getAddressIp());
        if (node instanceof com.oAT.agent.model.StatementError) {
            com.oAT.agent.model.Error error = ((com.oAT.agent.model.StatementError) node).getError();
            summary.setError(error == null ? null : toErrorSummary(error));
        }
        return summary;
    }

    private static Map<String, String> toApplicationSummary(Application application) {
        Map<String, String> summary = new LinkedHashMap<>();
        if (application != null) {
            summary.put("appId", application.getAppId());
            summary.put("appName", application.getAppName());
            summary.put("projectSrcName", application.getProjectSrcName());
        }
        return summary;
    }

    private static GraphRedisCommandSummary toRedisCommandSummary(RedisTraceNode node) {
        GraphRedisCommandSummary summary = new GraphRedisCommandSummary();
        summary.setType(node.getType());
        summary.setCommand(node.getCmd());
        summary.setHost(node.getHost());
        summary.setPort(node.getPort());
        summary.setBeginTime(formatMillis(node.getBeginTime()));
        summary.setUseTime(node.getUseTime());
        summary.setError(node.getError() == null ? null : toErrorSummary(node.getError()));
        return summary;
    }

    private static GraphErrorSummary toErrorSummary(com.oAT.agent.model.Error error) {
        GraphErrorSummary summary = new GraphErrorSummary();
        summary.setType(error.getType());
        summary.setCode(error.getCode());
        summary.setMessage(error.getMessage());
        summary.setErrorStack(error.getErrorStack());
        return summary;
    }

    private static String formatMillis(Long millis) {
        if (millis == null || millis <= 0) {
            return "";
        }
        return new SimpleDateFormat(DATE_TIME_PATTERN).format(new Date(millis));
    }

    private static String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private MySnapshotReportAggregate buildMySnapshotReportAggregate(SnapshotVo snapshot) {
        Map<String, Set<Integer>> methodCoveredLines = new HashMap<>();
        Map<String, Set<Integer>> methodTotalLines = new HashMap<>();
        Map<String, Integer> methodComplexity = new HashMap<>();
        Map<String, Set<Integer>> methodTotalBranches = new HashMap<>();
        Map<String, Set<Integer>> methodCoveredBranches = new HashMap<>();
        Map<String, Set<String>> methodTotalBranchTargets = new HashMap<>();
        Map<String, Set<String>> methodCoveredBranchTargets = new HashMap<>();
        Map<String, Set<String>> classMethods = new LinkedHashMap<>();

        String appId = resolveMySnapshotAppId(snapshot);

        com.oAT.agent.model.TraceNode rootNode = snapshotService.getTraceNode(snapshot.getTraceId(), "0");
        if (rootNode instanceof com.oAT.agent.model.HttpTraceNode) {
            com.oAT.agent.model.StackNodeVo[] codeNodes = ((com.oAT.agent.model.HttpTraceNode) rootNode).getCodeNodes();
            if (codeNodes != null) {
                for (com.oAT.agent.model.StackNodeVo codeNode : codeNodes) {
                    String methodKey = codeNode.getMethodName() + "#" + codeNode.getMethodDescriptor();
                    classMethods.computeIfAbsent(codeNode.getClassName(), key -> new LinkedHashSet<>()).add(methodKey);
                    if (codeNode.getDoLines() != null) {
                        methodCoveredLines.computeIfAbsent(methodKey, key -> new LinkedHashSet<>()).addAll(codeNode.getDoLines());
                    }
                    if (codeNode.getExecuteBranch() != null) {
                        methodCoveredBranches.computeIfAbsent(methodKey, key -> new LinkedHashSet<>()).addAll(codeNode.getExecuteBranch());
                    }
                    addBranchTargetKeys(methodCoveredBranchTargets, methodKey, codeNode.getExecuteBranchTargetProbeMap(), null);
                }
            }
        }

        if (StringUtils.hasText(appId)) {
            for (StaticSourceInfo staticInfo : staticInfoRepository.findByAppId(appId)) {
                if (staticInfo.getClassInfo() == null || staticInfo.getClassInfo().getMethodMaps() == null) {
                    continue;
                }
                for (StaticSourceMethodInfo methodInfo : staticInfo.getClassInfo().getMethodMaps().values()) {
                    String methodKey = methodInfo.getMethodName() + "#" + methodInfo.getMethodDesc();
                    if (!methodCoveredLines.containsKey(methodKey) && !methodCoveredBranches.containsKey(methodKey)) {
                        continue;
                    }
                    methodTotalLines.computeIfAbsent(methodKey, key -> new LinkedHashSet<>())
                            .addAll(methodInfo.getMethodLineNumberMap() != null ? methodInfo.getMethodLineNumberMap() : Collections.emptyList());
                    methodComplexity.put(methodKey, methodInfo.getCyclomaticComplexityMap() != null ? methodInfo.getCyclomaticComplexityMap() : 0);
                    methodTotalBranches.computeIfAbsent(methodKey, key -> new LinkedHashSet<>())
                            .addAll(methodInfo.getBranchLineNumberSet() != null ? methodInfo.getBranchLineNumberSet() : Collections.emptyList());
                    Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                            methodInfo.getBranchLineAndTargetProbeMap(),
                            decodeBranchTargetKeys(methodCoveredBranchTargets.get(methodKey)));
                    addBranchTargetKeys(methodTotalBranchTargets, methodKey, normalizedTotalBranchTargetProbeMap, null);
                    if (methodCoveredBranchTargets.containsKey(methodKey)) {
                        Set<String> normalizedKeys = new LinkedHashSet<>();
                        addBranchTargetKeysToSet(normalizedKeys, normalizedTotalBranchTargetProbeMap,
                                decodeBranchTargetKeys(methodCoveredBranchTargets.get(methodKey)));
                        methodCoveredBranchTargets.put(methodKey, normalizedKeys);
                    }
                }
            }
        }

        List<ClassCoverageSummary> classStats = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : classMethods.entrySet()) {
            String className = entry.getKey();
            Set<String> methods = entry.getValue();
            long totalMethods = methods.size();
            long coveredMethods = 0;
            long totalLines = 0;
            long coveredLines = 0;
            long totalBranches = 0;
            long coveredBranches = 0;
            long totalBranchTargets = 0;
            long coveredBranchTargets = 0;
            int totalComplexity = 0;
            for (String methodKey : methods) {
                totalLines += methodTotalLines.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredLines += methodCoveredLines.getOrDefault(methodKey, Collections.emptySet()).size();
                if (methodCoveredLines.containsKey(methodKey) && !methodCoveredLines.get(methodKey).isEmpty()) {
                    coveredMethods++;
                }
                totalComplexity += methodComplexity.getOrDefault(methodKey, 0);
                totalBranches += methodTotalBranches.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredBranches += methodCoveredBranches.getOrDefault(methodKey, Collections.emptySet()).size();
                totalBranchTargets += methodTotalBranchTargets.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredBranchTargets += methodCoveredBranchTargets.getOrDefault(methodKey, Collections.emptySet()).size();
            }
            ClassCoverageSummary summary = new ClassCoverageSummary();
            summary.setClassName(className);
            summary.setTotalMethods(totalMethods);
            summary.setCoveredMethods(coveredMethods);
            summary.setTotalLines(totalLines);
            summary.setCoveredLines(coveredLines);
            summary.setTotalBranches(totalBranches);
            summary.setCoveredBranches(coveredBranches);
            summary.setTotalBranchTargets(totalBranchTargets);
            summary.setCoveredBranchTargets(coveredBranchTargets);
            summary.setBranchRate(totalBranchTargets > 0 ? coveredBranchTargets * 100.0 / totalBranchTargets : 0);
            summary.setTotalComplexity(totalComplexity);
            classStats.add(summary);
        }

        classStats.sort((left, right) -> {
            double leftRate = left.getTotalLines() > 0 ? (double) left.getCoveredLines() / left.getTotalLines() : 0;
            double rightRate = right.getTotalLines() > 0 ? (double) right.getCoveredLines() / right.getTotalLines() : 0;
            int compare = Double.compare(rightRate, leftRate);
            if (compare != 0) {
                return compare;
            }
            return safeString(left.getClassName()).compareToIgnoreCase(safeString(right.getClassName()));
        });

        CoverageReportSummary report = new CoverageReportSummary();
        report.setAppId(appId);
        report.setSnapshotCount(1);
        report.setTotalClasses(classStats.size());
        report.setCoveredClasses(classStats.size());
        report.setTotalMethods(methodTotalLines.size());
        report.setCoveredMethods(methodCoveredLines.values().stream().filter(lines -> !lines.isEmpty()).count());
        report.setTotalLines(methodTotalLines.values().stream().mapToLong(Set::size).sum());
        report.setCoveredLines(methodCoveredLines.values().stream().mapToLong(Set::size).sum());
        report.setTotalBranches(methodTotalBranches.values().stream().mapToLong(Set::size).sum());
        report.setCoveredBranches(methodCoveredBranches.values().stream().mapToLong(Set::size).sum());
        report.setTotalBranchTargets(methodTotalBranchTargets.values().stream().mapToLong(Set::size).sum());
        report.setCoveredBranchTargets(methodCoveredBranchTargets.values().stream().mapToLong(Set::size).sum());
        report.setTotalComplexity(methodComplexity.values().stream().mapToInt(Integer::intValue).sum());
        return new MySnapshotReportAggregate(report, classStats);
    }

    private MySnapshotCodeReportAggregate buildMySnapshotsCodeReportAggregate(List<SnapshotVo> snapshots) {
        Map<String, Map<String, List<MySnapshotCodeRelationshipMethodSummary>>> codeRelationships = new LinkedHashMap<>();
        Map<String, Set<Integer>> methodCoveredLines = new HashMap<>();
        Map<String, Set<Integer>> methodTotalLines = new HashMap<>();
        Map<String, Integer> methodComplexity = new HashMap<>();
        Map<String, Set<Integer>> methodTotalBranches = new HashMap<>();
        Map<String, Set<Integer>> methodCoveredBranches = new HashMap<>();
        Map<String, Set<String>> methodTotalBranchTargets = new HashMap<>();
        Map<String, Set<String>> methodCoveredBranchTargets = new HashMap<>();
        Map<String, Set<String>> classMethods = new LinkedHashMap<>();
        Map<String, String> classToAppId = new LinkedHashMap<>();

        for (SnapshotVo snapshot : snapshots) {
            TraceNode traceNode = snapshotService.getTraceNode(snapshot.getTraceId(), "0");
            if (!(traceNode instanceof HttpTraceNode)) {
                continue;
            }

            HttpTraceNode httpTraceNode = (HttpTraceNode) traceNode;
            StackNodeVo[] codeNodes = httpTraceNode.getCodeNodes();
            if (codeNodes == null || codeNodes.length == 0) {
                continue;
            }

            String requestUrl = httpTraceNode.getRequestUrl();
            Map<String, List<MySnapshotCodeRelationshipMethodSummary>> childNodes =
                    codeRelationships.computeIfAbsent(requestUrl, key -> new LinkedHashMap<>());

            String currentAppId = snapshot.getAppId();
            if (!StringUtils.hasText(currentAppId) && traceNode.getApp() != null) {
                currentAppId = traceNode.getApp().getAppId();
            }

            for (StackNodeVo node : codeNodes) {
                if (node == null) {
                    continue;
                }
                String methodKey = node.getMethodName() + "#" + node.getMethodDescriptor();
                classMethods.computeIfAbsent(node.getClassName(), key -> new LinkedHashSet<>()).add(methodKey);
                if (StringUtils.hasText(currentAppId)) {
                    classToAppId.putIfAbsent(node.getClassName(), currentAppId);
                }

                childNodes.computeIfAbsent(node.parentId(), key -> new ArrayList<>())
                        .add(new MySnapshotCodeRelationshipMethodSummary(
                                node.getClassName(),
                                node.getMethodName(),
                                node.getMethodDescriptor(),
                                node.getDoLines() == null ? new ArrayList<>() : new ArrayList<>(node.getDoLines())));

                if (node.getDoLines() != null) {
                    methodCoveredLines.computeIfAbsent(methodKey, key -> new LinkedHashSet<>()).addAll(node.getDoLines());
                }
                if (node.getExecuteBranch() != null) {
                    methodCoveredBranches.computeIfAbsent(methodKey, key -> new LinkedHashSet<>()).addAll(node.getExecuteBranch());
                }
                addBranchTargetKeys(methodCoveredBranchTargets, methodKey, node.getExecuteBranchTargetProbeMap(), null);
            }
        }

        for (String appId : new LinkedHashSet<>(classToAppId.values())) {
            if (!StringUtils.hasText(appId)) {
                continue;
            }
            for (StaticSourceInfo staticInfo : staticInfoRepository.findByAppId(appId)) {
                if (staticInfo.getClassInfo() == null || staticInfo.getClassInfo().getMethodMaps() == null) {
                    continue;
                }
                for (StaticSourceMethodInfo methodInfo : staticInfo.getClassInfo().getMethodMaps().values()) {
                    String methodKey = methodInfo.getMethodName() + "#" + methodInfo.getMethodDesc();
                    if (!methodCoveredLines.containsKey(methodKey) && !methodCoveredBranches.containsKey(methodKey)) {
                        continue;
                    }
                    methodTotalLines.computeIfAbsent(methodKey, key -> new LinkedHashSet<>())
                            .addAll(methodInfo.getMethodLineNumberMap() != null ? methodInfo.getMethodLineNumberMap() : Collections.emptyList());
                    methodComplexity.put(methodKey, methodInfo.getCyclomaticComplexityMap() != null ? methodInfo.getCyclomaticComplexityMap() : 0);
                    methodTotalBranches.computeIfAbsent(methodKey, key -> new LinkedHashSet<>())
                            .addAll(methodInfo.getBranchLineNumberSet() != null ? methodInfo.getBranchLineNumberSet() : Collections.emptyList());
                    Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                            methodInfo.getBranchLineAndTargetProbeMap(),
                            decodeBranchTargetKeys(methodCoveredBranchTargets.get(methodKey)));
                    addBranchTargetKeys(methodTotalBranchTargets, methodKey, normalizedTotalBranchTargetProbeMap, null);
                    if (methodCoveredBranchTargets.containsKey(methodKey)) {
                        Set<String> normalizedKeys = new LinkedHashSet<>();
                        addBranchTargetKeysToSet(normalizedKeys, normalizedTotalBranchTargetProbeMap,
                                decodeBranchTargetKeys(methodCoveredBranchTargets.get(methodKey)));
                        methodCoveredBranchTargets.put(methodKey, normalizedKeys);
                    }
                }
            }
        }

        for (Map<String, List<MySnapshotCodeRelationshipMethodSummary>> groups : codeRelationships.values()) {
            for (List<MySnapshotCodeRelationshipMethodSummary> methods : groups.values()) {
                for (MySnapshotCodeRelationshipMethodSummary method : methods) {
                    String methodKey = method.getMethodName() + "#" + method.getMethodDescriptor();
                    Set<Integer> totalLineSet = methodTotalLines.getOrDefault(methodKey, Collections.emptySet());
                    Set<Integer> coveredLineSet = methodCoveredLines.getOrDefault(methodKey, Collections.emptySet());
                    Set<Integer> totalBranchSet = methodTotalBranches.getOrDefault(methodKey, Collections.emptySet());
                    Set<Integer> coveredBranchSet = methodCoveredBranches.getOrDefault(methodKey, Collections.emptySet());
                    Set<String> totalBranchTargetSet = methodTotalBranchTargets.getOrDefault(methodKey, Collections.emptySet());
                    Set<String> coveredBranchTargetSet = methodCoveredBranchTargets.getOrDefault(methodKey, Collections.emptySet());

                    method.setLineTotalCount(totalLineSet.size());
                    method.setCoveredLineCount(coveredLineSet.size());
                    method.setBranchTotalCount(totalBranchSet.size());
                    method.setBranchCoveredCount(coveredBranchSet.size());
                    method.setBranchTargetTotalCount(totalBranchTargetSet.size());
                    method.setBranchTargetCoveredCount(coveredBranchTargetSet.size());
                }
            }
        }

        List<ClassCoverageSummary> classStats = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : classMethods.entrySet()) {
            String className = entry.getKey();
            Set<String> methods = entry.getValue();
            long totalMethods = methods.size();
            long coveredMethods = 0;
            long totalLines = 0;
            long coveredLines = 0;
            long totalBranches = 0;
            long coveredBranches = 0;
            long totalBranchTargets = 0;
            long coveredBranchTargets = 0;
            int totalComplexity = 0;

            for (String methodKey : methods) {
                totalLines += methodTotalLines.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredLines += methodCoveredLines.getOrDefault(methodKey, Collections.emptySet()).size();
                if (methodCoveredLines.containsKey(methodKey) && !methodCoveredLines.get(methodKey).isEmpty()) {
                    coveredMethods++;
                }
                totalComplexity += methodComplexity.getOrDefault(methodKey, 0);
                totalBranches += methodTotalBranches.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredBranches += methodCoveredBranches.getOrDefault(methodKey, Collections.emptySet()).size();
                totalBranchTargets += methodTotalBranchTargets.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredBranchTargets += methodCoveredBranchTargets.getOrDefault(methodKey, Collections.emptySet()).size();
            }

            ClassCoverageSummary summary = new ClassCoverageSummary();
            summary.setClassName(className);
            summary.setTotalMethods(totalMethods);
            summary.setCoveredMethods(coveredMethods);
            summary.setTotalLines(totalLines);
            summary.setCoveredLines(coveredLines);
            summary.setTotalBranches(totalBranches);
            summary.setCoveredBranches(coveredBranches);
            summary.setTotalBranchTargets(totalBranchTargets);
            summary.setCoveredBranchTargets(coveredBranchTargets);
            summary.setBranchRate(totalBranchTargets > 0 ? coveredBranchTargets * 100.0 / totalBranchTargets : 0);
            summary.setTotalComplexity(totalComplexity);
            classStats.add(summary);
        }

        classStats.sort((left, right) -> {
            double leftRate = left.getTotalLines() > 0 ? (double) left.getCoveredLines() / left.getTotalLines() : 0;
            double rightRate = right.getTotalLines() > 0 ? (double) right.getCoveredLines() / right.getTotalLines() : 0;
            int compare = Double.compare(rightRate, leftRate);
            if (compare != 0) {
                return compare;
            }
            return safeString(left.getClassName()).compareToIgnoreCase(safeString(right.getClassName()));
        });

        CoverageReportSummary report = new CoverageReportSummary();
        report.setAppId(classToAppId.values().stream().filter(StringUtils::hasText).findFirst().orElse(null));
        report.setSnapshotCount(snapshots.size());
        report.setTotalClasses(classStats.size());
        report.setCoveredClasses(classStats.size());
        report.setTotalMethods(methodTotalLines.size());
        report.setCoveredMethods(methodCoveredLines.values().stream().filter(lines -> !lines.isEmpty()).count());
        report.setTotalLines(methodTotalLines.values().stream().mapToLong(Set::size).sum());
        report.setCoveredLines(methodCoveredLines.values().stream().mapToLong(Set::size).sum());
        report.setTotalBranches(methodTotalBranches.values().stream().mapToLong(Set::size).sum());
        report.setCoveredBranches(methodCoveredBranches.values().stream().mapToLong(Set::size).sum());
        report.setTotalBranchTargets(methodTotalBranchTargets.values().stream().mapToLong(Set::size).sum());
        report.setCoveredBranchTargets(methodCoveredBranchTargets.values().stream().mapToLong(Set::size).sum());
        report.setTotalComplexity(methodComplexity.values().stream().mapToInt(Integer::intValue).sum());

        List<MySnapshotCodeRelationshipGroupSummary> groups = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<MySnapshotCodeRelationshipMethodSummary>>> entry : codeRelationships.entrySet()) {
            List<MySnapshotCodeRelationshipMethodSummary> methods = entry.getValue().values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            methods.sort((left, right) -> {
                double leftRate = left.getLineTotalCount() > 0 ? (double) left.getCoveredLineCount() / left.getLineTotalCount() : 0;
                double rightRate = right.getLineTotalCount() > 0 ? (double) right.getCoveredLineCount() / right.getLineTotalCount() : 0;
                int compare = Double.compare(rightRate, leftRate);
                if (compare != 0) {
                    return compare;
                }
                return safeString(left.getClassName()).compareToIgnoreCase(safeString(right.getClassName()));
            });
            MySnapshotCodeRelationshipGroupSummary group = new MySnapshotCodeRelationshipGroupSummary();
            group.setRequestUrl(entry.getKey());
            group.setMethods(methods);
            groups.add(group);
        }

        groups.sort((left, right) -> safeString(left.getRequestUrl()).compareToIgnoreCase(safeString(right.getRequestUrl())));
        return new MySnapshotCodeReportAggregate(report, classStats, groups, report.getAppId());
    }

    private ClassCoverageIndex buildMySnapshotsClassCoverage(String appId, List<SnapshotVo> snapshots, String className) {
        ClassCoverageIndex aggregatedClassCov = new ClassCoverageIndex();
        aggregatedClassCov.setClassName(className);
        aggregatedClassCov.setAppId(appId);

        List<StaticSourceInfo> staticInfos = StringUtils.hasText(appId) ? staticInfoRepository.findByAppId(appId) : Collections.emptyList();
        Map<String, StaticSourceMethodInfo> classStaticMethods = new HashMap<>();
        for (StaticSourceInfo staticInfo : staticInfos) {
            if (staticInfo.getClassInfo() == null
                    || !className.equals(staticInfo.getClassInfo().getClassName())
                    || staticInfo.getClassInfo().getMethodMaps() == null) {
                continue;
            }
            for (StaticSourceMethodInfo methodInfo : staticInfo.getClassInfo().getMethodMaps().values()) {
                String methodKey = methodInfo.getMethodName() + "#" + methodInfo.getMethodDesc();
                classStaticMethods.put(methodKey, methodInfo);
            }
            break;
        }

        Map<String, ClassCoverageIndex.MethodCoverageDetail> methodMap = new LinkedHashMap<>();
        for (SnapshotVo snapshot : snapshots) {
            TraceNode traceNode = snapshotService.getTraceNode(snapshot.getTraceId(), "0");
            if (!(traceNode instanceof HttpTraceNode)) {
                continue;
            }
            StackNodeVo[] codeNodes = ((HttpTraceNode) traceNode).getCodeNodes();
            if (codeNodes == null) {
                continue;
            }
            for (StackNodeVo codeNode : codeNodes) {
                if (codeNode == null || !className.equals(codeNode.getClassName())) {
                    continue;
                }
                String methodKey = codeNode.getMethodName() + "#" + codeNode.getMethodDescriptor();
                ClassCoverageIndex.MethodCoverageDetail detail = methodMap.computeIfAbsent(methodKey, key -> {
                    ClassCoverageIndex.MethodCoverageDetail newDetail = new ClassCoverageIndex.MethodCoverageDetail();
                    newDetail.setMethodName(codeNode.getMethodName());
                    newDetail.setMethodDesc(codeNode.getMethodDescriptor());
                    StaticSourceMethodInfo staticMethod = classStaticMethods.get(methodKey);
                    List<Integer> totalLines = staticMethod != null && staticMethod.getMethodLineNumberMap() != null
                            ? staticMethod.getMethodLineNumberMap()
                            : Collections.emptyList();
                    newDetail.setTotalLineNumbers(new ArrayList<>(totalLines));
                    newDetail.setTotalLines(totalLines.size());
                    newDetail.setTotalBranches(staticMethod != null && staticMethod.getTotalBranchCount() != null
                            ? staticMethod.getTotalBranchCount()
                            : 0);
                    Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                            staticMethod != null ? staticMethod.getBranchLineAndTargetProbeMap() : null,
                            codeNode.getExecuteBranchTargetProbeMap());
                    newDetail.setTotalBranchTargetProbeMap(normalizedTotalBranchTargetProbeMap);
                    newDetail.setTotalBranchTargets(countBranchTargets(normalizedTotalBranchTargetProbeMap));
                    newDetail.setCoveredBranchTargetProbeMap(new LinkedHashMap<>());
                    newDetail.setCoveredBranchTargets(0);
                    newDetail.setBranchRate(0.0);
                    newDetail.setComplexity(staticMethod != null && staticMethod.getCyclomaticComplexityMap() != null
                            ? staticMethod.getCyclomaticComplexityMap()
                            : 0);
                    newDetail.setCoveredLineNumbers(new ArrayList<>());
                    newDetail.setCoveredBranchLines(new ArrayList<>());
                    return newDetail;
                });

                if (codeNode.getDoLines() != null) {
                    Set<Integer> covered = new LinkedHashSet<>(detail.getCoveredLineNumbers());
                    covered.addAll(codeNode.getDoLines());
                    detail.setCoveredLineNumbers(new ArrayList<>(covered));
                    detail.setCoveredLines(detail.getCoveredLineNumbers().size());
                    detail.setCovered(detail.getCoveredLines() > 0);
                }
                if (codeNode.getExecuteBranch() != null) {
                    Set<Integer> coveredBranchLines = new LinkedHashSet<>(detail.getCoveredBranchLines());
                    coveredBranchLines.addAll(codeNode.getExecuteBranch());
                    detail.setCoveredBranchLines(new ArrayList<>(coveredBranchLines));
                    detail.setCoveredBranches(detail.getCoveredBranchLines().size());
                }
                if (codeNode.getExecuteBranchTargetProbeMap() != null) {
                    Map<String, List<Integer>> coveredBranchTargetProbeMap = mergeBranchTargetProbeMap(
                            detail.getCoveredBranchTargetProbeMap(),
                            codeNode.getExecuteBranchTargetProbeMap());
                    Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                            detail.getTotalBranchTargetProbeMap(),
                            coveredBranchTargetProbeMap);
                    coveredBranchTargetProbeMap = normalizeCoveredBranchTargetProbeMap(
                            normalizedTotalBranchTargetProbeMap,
                            coveredBranchTargetProbeMap);
                    detail.setTotalBranchTargetProbeMap(normalizedTotalBranchTargetProbeMap);
                    detail.setTotalBranchTargets(countBranchTargets(normalizedTotalBranchTargetProbeMap));
                    detail.setCoveredBranchTargetProbeMap(coveredBranchTargetProbeMap);
                    detail.setCoveredBranchTargets(countBranchTargets(coveredBranchTargetProbeMap));
                    detail.setBranchRate(calculateBranchRate(detail.getCoveredBranchTargets(), detail.getTotalBranchTargets()));
                }
            }
        }

        List<ClassCoverageIndex.MethodCoverageDetail> methods = new ArrayList<>(methodMap.values());
        methods.sort((left, right) -> {
            double leftRate = left.getTotalLines() > 0 ? (double) left.getCoveredLines() / left.getTotalLines() : 0;
            double rightRate = right.getTotalLines() > 0 ? (double) right.getCoveredLines() / right.getTotalLines() : 0;
            int compare = Double.compare(rightRate, leftRate);
            if (compare != 0) {
                return compare;
            }
            return safeString(left.getMethodName()).compareToIgnoreCase(safeString(right.getMethodName()));
        });

        aggregatedClassCov.setMethods(methods);
        aggregatedClassCov.setTotalMethods(methods.size());
        aggregatedClassCov.setCoveredMethods((int) methods.stream().filter(ClassCoverageIndex.MethodCoverageDetail::isCovered).count());
        aggregatedClassCov.setTotalLines(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getTotalLines).sum());
        aggregatedClassCov.setCoveredLines(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getCoveredLines).sum());
        aggregatedClassCov.setTotalBranches(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getTotalBranches).sum());
        aggregatedClassCov.setCoveredBranches(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getCoveredBranches).sum());
        aggregatedClassCov.setTotalBranchTargets(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getTotalBranchTargets).sum());
        aggregatedClassCov.setCoveredBranchTargets(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getCoveredBranchTargets).sum());
        aggregatedClassCov.setTotalComplexity(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getComplexity).sum());
        aggregatedClassCov.setMethodRate(calculateRate(aggregatedClassCov.getCoveredMethods(), aggregatedClassCov.getTotalMethods()));
        aggregatedClassCov.setLineRate(calculateRate(aggregatedClassCov.getCoveredLines(), aggregatedClassCov.getTotalLines()));
        aggregatedClassCov.setBranchRate(calculateRate(aggregatedClassCov.getCoveredBranchTargets(), aggregatedClassCov.getTotalBranchTargets()));
        return aggregatedClassCov;
    }

    private String resolveMySnapshotAppId(SnapshotVo snapshot) {
        String appId = snapshot.getAppId();
        if (StringUtils.hasText(appId)) {
            return appId;
        }
        com.oAT.agent.model.TraceNode traceNode = snapshotService.getTraceNode(snapshot.getTraceId(), "0");
        if (traceNode != null && traceNode.getApp() != null) {
            return traceNode.getApp().getAppId();
        }
        return null;
    }

    private ClassCoverageIndex buildMySnapshotClassCoverage(String appId, SnapshotVo snapshot, String className) {
        ClassCoverageIndex aggregatedClassCov = new ClassCoverageIndex();
        aggregatedClassCov.setClassName(className);
        aggregatedClassCov.setAppId(appId);

        List<StaticSourceInfo> staticInfos = StringUtils.hasText(appId)
                ? staticInfoRepository.findByAppId(appId)
                : Collections.emptyList();
        Map<String, StaticSourceMethodInfo> classStaticMethods = new HashMap<>();
        for (StaticSourceInfo staticInfo : staticInfos) {
            if (staticInfo.getClassInfo() == null
                    || !className.equals(staticInfo.getClassInfo().getClassName())
                    || staticInfo.getClassInfo().getMethodMaps() == null) {
                continue;
            }
            for (StaticSourceMethodInfo methodInfo : staticInfo.getClassInfo().getMethodMaps().values()) {
                String methodKey = methodInfo.getMethodName() + "#" + methodInfo.getMethodDesc();
                classStaticMethods.put(methodKey, methodInfo);
            }
            break;
        }

        Map<String, ClassCoverageIndex.MethodCoverageDetail> methodMap = new LinkedHashMap<>();
        for (com.oAT.agent.model.TraceNode node : snapshotService.getTraceNodes(snapshot.getTraceId())) {
            if (!(node instanceof com.oAT.agent.model.HttpTraceNode)) {
                continue;
            }
            com.oAT.agent.model.StackNodeVo[] codeNodes = ((com.oAT.agent.model.HttpTraceNode) node).getCodeNodes();
            if (codeNodes == null) {
                continue;
            }
            for (com.oAT.agent.model.StackNodeVo codeNode : codeNodes) {
                if (!className.equals(codeNode.getClassName())) {
                    continue;
                }
                String methodKey = codeNode.getMethodName() + "#" + codeNode.getMethodDescriptor();
                ClassCoverageIndex.MethodCoverageDetail detail = methodMap.computeIfAbsent(methodKey, key -> {
                    ClassCoverageIndex.MethodCoverageDetail newDetail = new ClassCoverageIndex.MethodCoverageDetail();
                    newDetail.setMethodName(codeNode.getMethodName());
                    newDetail.setMethodDesc(codeNode.getMethodDescriptor());
                    StaticSourceMethodInfo staticMethod = classStaticMethods.get(methodKey);
                    List<Integer> totalLines = staticMethod != null && staticMethod.getMethodLineNumberMap() != null
                            ? staticMethod.getMethodLineNumberMap()
                            : Collections.emptyList();
                    newDetail.setTotalLineNumbers(new ArrayList<>(totalLines));
                    newDetail.setTotalLines(totalLines.size());
                    newDetail.setTotalBranches(staticMethod != null && staticMethod.getTotalBranchCount() != null
                            ? staticMethod.getTotalBranchCount()
                            : 0);
                    Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                            staticMethod != null ? staticMethod.getBranchLineAndTargetProbeMap() : null,
                            codeNode.getExecuteBranchTargetProbeMap());
                    newDetail.setTotalBranchTargetProbeMap(normalizedTotalBranchTargetProbeMap);
                    newDetail.setTotalBranchTargets(countBranchTargets(normalizedTotalBranchTargetProbeMap));
                    newDetail.setCoveredBranchTargetProbeMap(new LinkedHashMap<>());
                    newDetail.setCoveredBranchTargets(0);
                    newDetail.setBranchRate(0.0);
                    newDetail.setComplexity(staticMethod != null && staticMethod.getCyclomaticComplexityMap() != null
                            ? staticMethod.getCyclomaticComplexityMap()
                            : 0);
                    newDetail.setCoveredLineNumbers(new ArrayList<>());
                    newDetail.setCoveredBranchLines(new ArrayList<>());
                    return newDetail;
                });

                if (codeNode.getDoLines() != null) {
                    Set<Integer> covered = new LinkedHashSet<>(detail.getCoveredLineNumbers());
                    covered.addAll(codeNode.getDoLines());
                    detail.setCoveredLineNumbers(new ArrayList<>(covered));
                    detail.setCoveredLines(detail.getCoveredLineNumbers().size());
                    detail.setCovered(detail.getCoveredLines() > 0);
                }
                if (codeNode.getExecuteBranch() != null) {
                    Set<Integer> coveredBranchLines = new LinkedHashSet<>(detail.getCoveredBranchLines());
                    coveredBranchLines.addAll(codeNode.getExecuteBranch());
                    detail.setCoveredBranchLines(new ArrayList<>(coveredBranchLines));
                    detail.setCoveredBranches(detail.getCoveredBranchLines().size());
                }
                if (codeNode.getExecuteBranchTargetProbeMap() != null) {
                    Map<String, List<Integer>> coveredBranchTargetProbeMap = mergeBranchTargetProbeMap(
                            detail.getCoveredBranchTargetProbeMap(),
                            codeNode.getExecuteBranchTargetProbeMap());
                    Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                            detail.getTotalBranchTargetProbeMap(),
                            coveredBranchTargetProbeMap);
                    coveredBranchTargetProbeMap = normalizeCoveredBranchTargetProbeMap(
                            normalizedTotalBranchTargetProbeMap,
                            coveredBranchTargetProbeMap);
                    detail.setTotalBranchTargetProbeMap(normalizedTotalBranchTargetProbeMap);
                    detail.setTotalBranchTargets(countBranchTargets(normalizedTotalBranchTargetProbeMap));
                    detail.setCoveredBranchTargetProbeMap(coveredBranchTargetProbeMap);
                    detail.setCoveredBranchTargets(countBranchTargets(coveredBranchTargetProbeMap));
                    detail.setBranchRate(calculateBranchRate(detail.getCoveredBranchTargets(), detail.getTotalBranchTargets()));
                }
            }
        }

        List<ClassCoverageIndex.MethodCoverageDetail> methods = new ArrayList<>(methodMap.values());
        methods.sort((left, right) -> {
            double leftRate = left.getTotalLines() > 0 ? (double) left.getCoveredLines() / left.getTotalLines() : 0;
            double rightRate = right.getTotalLines() > 0 ? (double) right.getCoveredLines() / right.getTotalLines() : 0;
            int compare = Double.compare(rightRate, leftRate);
            if (compare != 0) {
                return compare;
            }
            return safeString(left.getMethodName()).compareToIgnoreCase(safeString(right.getMethodName()));
        });

        aggregatedClassCov.setMethods(methods);
        aggregatedClassCov.setTotalMethods(methods.size());
        aggregatedClassCov.setCoveredMethods((int) methods.stream().filter(ClassCoverageIndex.MethodCoverageDetail::isCovered).count());
        aggregatedClassCov.setTotalLines(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getTotalLines).sum());
        aggregatedClassCov.setCoveredLines(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getCoveredLines).sum());
        aggregatedClassCov.setTotalBranches(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getTotalBranches).sum());
        aggregatedClassCov.setCoveredBranches(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getCoveredBranches).sum());
        aggregatedClassCov.setTotalBranchTargets(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getTotalBranchTargets).sum());
        aggregatedClassCov.setCoveredBranchTargets(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getCoveredBranchTargets).sum());
        aggregatedClassCov.setTotalComplexity(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getComplexity).sum());
        aggregatedClassCov.setMethodRate(calculateRate(aggregatedClassCov.getCoveredMethods(), aggregatedClassCov.getTotalMethods()));
        aggregatedClassCov.setLineRate(calculateRate(aggregatedClassCov.getCoveredLines(), aggregatedClassCov.getTotalLines()));
        aggregatedClassCov.setBranchRate(calculateRate(aggregatedClassCov.getCoveredBranchTargets(), aggregatedClassCov.getTotalBranchTargets()));
        return aggregatedClassCov;
    }

    private static String limitText(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
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
            normalized.put(branchLine, new ArrayList<>(totalSet.containsAll(coveredSet) ? coveredSet : totalSet));
        }
        return normalized.isEmpty() ? copyBranchTargetProbeMap(total) : normalized;
    }

    private Map<String, List<Integer>> copyBranchTargetProbeMap(Map<String, List<Integer>> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        Map<String, List<Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : source.entrySet()) {
            List<Integer> values = entry.getValue() == null
                    ? Collections.emptyList()
                    : new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
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
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            target.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).addAll(entry.getValue());
        }
    }

    private Map<String, List<Integer>> normalizeCoveredBranchTargetProbeMap(Map<String, List<Integer>> total,
                                                                            Map<String, List<Integer>> covered) {
        if (covered == null || covered.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, List<Integer>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : covered.entrySet()) {
            String branchLine = entry.getKey();
            List<Integer> coveredValues = entry.getValue();
            if (coveredValues == null || coveredValues.isEmpty()) {
                continue;
            }
            LinkedHashSet<Integer> coveredSet = new LinkedHashSet<>(coveredValues);
            List<Integer> totalValues = total == null ? null : total.get(branchLine);
            if (totalValues != null && !totalValues.isEmpty()) {
                coveredSet.retainAll(new LinkedHashSet<>(totalValues));
            }
            if (!coveredSet.isEmpty()) {
                normalized.put(branchLine, new ArrayList<>(coveredSet));
            }
        }
        return normalized;
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

    private double calculateRate(long covered, long total) {
        if (total <= 0) {
            return 0;
        }
        return covered * 100.0 / total;
    }

    private Double calculateBranchRate(int coveredBranchTargets, int totalBranchTargets) {
        return totalBranchTargets <= 0 ? 0.0 : coveredBranchTargets * 100.0 / totalBranchTargets;
    }

    private Map<String, List<Integer>> decodeBranchTargetKeys(Set<String> encodedKeys) {
        if (encodedKeys == null || encodedKeys.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, List<Integer>> decoded = new LinkedHashMap<>();
        for (String key : encodedKeys) {
            if (!StringUtils.hasText(key) || !key.contains(":")) {
                continue;
            }
            int index = key.indexOf(':');
            String branchLine = key.substring(0, index);
            String targetProbe = key.substring(index + 1);
            if (!StringUtils.hasText(branchLine) || !StringUtils.hasText(targetProbe)) {
                continue;
            }
            try {
                decoded.computeIfAbsent(branchLine, value -> new ArrayList<>()).add(Integer.parseInt(targetProbe));
            } catch (NumberFormatException ignored) {
            }
        }
        return decoded;
    }

    private void addBranchTargetKeys(Map<String, Set<String>> target,
                                     String methodKey,
                                     Map<String, List<Integer>> branchTargetProbeMap,
                                     Map<String, List<Integer>> fallbackBranchTargetProbeMap) {
        Map<String, List<Integer>> source = branchTargetProbeMap;
        if ((source == null || source.isEmpty()) && fallbackBranchTargetProbeMap != null && !fallbackBranchTargetProbeMap.isEmpty()) {
            source = fallbackBranchTargetProbeMap;
        }
        if (source == null || source.isEmpty()) {
            return;
        }
        Set<String> keys = target.computeIfAbsent(methodKey, key -> new LinkedHashSet<>());
        addBranchTargetKeysToSet(keys, source, null);
    }

    private void addBranchTargetKeysToSet(Set<String> target,
                                          Map<String, List<Integer>> branchTargetProbeMap,
                                          Map<String, List<Integer>> fallbackBranchTargetProbeMap) {
        Map<String, List<Integer>> source = branchTargetProbeMap;
        if ((source == null || source.isEmpty()) && fallbackBranchTargetProbeMap != null && !fallbackBranchTargetProbeMap.isEmpty()) {
            source = fallbackBranchTargetProbeMap;
        }
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<Integer>> entry : source.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            for (Integer probe : entry.getValue()) {
                if (probe != null) {
                    target.add(entry.getKey() + ":" + probe);
                }
            }
        }
    }

    private FrontendContextApiControl.UserSummary toUserSummary(UserVo user) {
        if (user == null) {
            return null;
        }
        FrontendContextApiControl.UserSummary summary = new FrontendContextApiControl.UserSummary();
        summary.setId(user.getId());
        summary.setName(user.getName());
        summary.setNickname(user.getNickname());
        summary.setEmail(user.getEmail());
        summary.setHeader(user.getHeader());
        summary.setPhone(user.getPhone());
        summary.setReadme(user.getReadme());
        return summary;
    }

    private String resolveUserName(UserVo user) {
        if (user == null) {
            return "未知用户";
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getName();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String formatDateTime(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat(DATE_TIME_PATTERN).format(date);
    }

    private String formatRelativeTime(Date date) {
        if (date == null) {
            return "";
        }
        return DateUtil.timeDifference(date);
    }

    private static List<String> optionalArray(String[] values) {
        if (values == null || values.length == 0) {
            return new ArrayList<>();
        }
        return Arrays.asList(values);
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

    private List<MethodCoverageSummary> toMethodCoverageSummaries(List<ClassCoverageIndex.MethodCoverageDetail> methods) {
        if (methods == null) {
            return new ArrayList<>();
        }
        List<MethodCoverageSummary> result = new ArrayList<>();
        for (ClassCoverageIndex.MethodCoverageDetail method : methods) {
            MethodCoverageSummary summary = new MethodCoverageSummary();
            summary.setMethodName(method.getMethodName());
            summary.setMethodDesc(method.getMethodDesc());
            summary.setTotalLines(method.getTotalLines());
            summary.setCoveredLines(method.getCoveredLines());
            summary.setTotalBranches(method.getTotalBranches());
            summary.setCoveredBranches(method.getCoveredBranches());
            summary.setComplexity(method.getComplexity());
            summary.setCovered(method.isCovered());
            summary.setTotalBranchTargets(method.getTotalBranchTargets());
            summary.setCoveredBranchTargets(method.getCoveredBranchTargets());
            summary.setBranchRate(method.getBranchRate());
            result.add(summary);
        }
        return result;
    }

    public static class SystemSnapshotListPayload {
        private FrontendContextApiControl.AppSummary app;
        private List<FrontendContextApiControl.AppSummary> apps;
        private String currentDirectory;
        private String sort;
        private String keyword;
        private List<SnapshotDirectorySummary> directories;
        private List<SnapshotDirectorySummary> directoryTiers;
        private List<SystemSnapshotSummary> snapshots;
        private List<UsecaseVo> allUsecases;
        private String currentUserRole;

        public FrontendContextApiControl.AppSummary getApp() { return app; }
        public void setApp(FrontendContextApiControl.AppSummary app) { this.app = app; }
        public List<FrontendContextApiControl.AppSummary> getApps() { return apps; }
        public void setApps(List<FrontendContextApiControl.AppSummary> apps) { this.apps = apps; }
        public String getCurrentDirectory() { return currentDirectory; }
        public void setCurrentDirectory(String currentDirectory) { this.currentDirectory = currentDirectory; }
        public String getSort() { return sort; }
        public void setSort(String sort) { this.sort = sort; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public List<SnapshotDirectorySummary> getDirectories() { return directories; }
        public void setDirectories(List<SnapshotDirectorySummary> directories) { this.directories = directories; }
        public List<SnapshotDirectorySummary> getDirectoryTiers() { return directoryTiers; }
        public void setDirectoryTiers(List<SnapshotDirectorySummary> directoryTiers) { this.directoryTiers = directoryTiers; }
        public List<SystemSnapshotSummary> getSnapshots() { return snapshots; }
        public void setSnapshots(List<SystemSnapshotSummary> snapshots) { this.snapshots = snapshots; }
        public List<UsecaseVo> getAllUsecases() { return allUsecases; }
        public void setAllUsecases(List<UsecaseVo> allUsecases) { this.allUsecases = allUsecases; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class SystemSnapshotDetailPayload {
        private FrontendContextApiControl.AppSummary app;
        private SystemSnapshotSummary snapshot;
        private List<LabelGroup.Label> labels;
        private List<String> selectedLabelNames;
        private List<ProjectMemberVo> members;
        private List<String> selectedPrincipalIds;
        private List<UsecaseVo> usecases;
        private List<UsecaseVo> allUsecases;
        private List<DynamicItemSummary> dynamics;
        private String currentUserRole;

        public FrontendContextApiControl.AppSummary getApp() { return app; }
        public void setApp(FrontendContextApiControl.AppSummary app) { this.app = app; }
        public SystemSnapshotSummary getSnapshot() { return snapshot; }
        public void setSnapshot(SystemSnapshotSummary snapshot) { this.snapshot = snapshot; }
        public List<LabelGroup.Label> getLabels() { return labels; }
        public void setLabels(List<LabelGroup.Label> labels) { this.labels = labels; }
        public List<String> getSelectedLabelNames() { return selectedLabelNames; }
        public void setSelectedLabelNames(List<String> selectedLabelNames) { this.selectedLabelNames = selectedLabelNames; }
        public List<ProjectMemberVo> getMembers() { return members; }
        public void setMembers(List<ProjectMemberVo> members) { this.members = members; }
        public List<String> getSelectedPrincipalIds() { return selectedPrincipalIds; }
        public void setSelectedPrincipalIds(List<String> selectedPrincipalIds) { this.selectedPrincipalIds = selectedPrincipalIds; }
        public List<UsecaseVo> getUsecases() { return usecases; }
        public void setUsecases(List<UsecaseVo> usecases) { this.usecases = usecases; }
        public List<UsecaseVo> getAllUsecases() { return allUsecases; }
        public void setAllUsecases(List<UsecaseVo> allUsecases) { this.allUsecases = allUsecases; }
        public List<DynamicItemSummary> getDynamics() { return dynamics; }
        public void setDynamics(List<DynamicItemSummary> dynamics) { this.dynamics = dynamics; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class MySnapshotListPayload {
        private List<SnapshotVo> snapshots;
        private List<UsecaseVo> allUsecases;
        private List<LabelGroup.Label> snapshotLabels;
        private String currentUserRole;

        public List<SnapshotVo> getSnapshots() { return snapshots; }
        public void setSnapshots(List<SnapshotVo> snapshots) { this.snapshots = snapshots; }
        public List<UsecaseVo> getAllUsecases() { return allUsecases; }
        public void setAllUsecases(List<UsecaseVo> allUsecases) { this.allUsecases = allUsecases; }
        public List<LabelGroup.Label> getSnapshotLabels() { return snapshotLabels; }
        public void setSnapshotLabels(List<LabelGroup.Label> snapshotLabels) { this.snapshotLabels = snapshotLabels; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class SystemSnapshotReportPayload {
        private FrontendContextApiControl.AppSummary app;
        private SystemSnapshotSummary snapshot;
        private CoverageReportSummary report;
        private List<ClassCoverageSummary> classStats;
        private String currentUserRole;

        public FrontendContextApiControl.AppSummary getApp() { return app; }
        public void setApp(FrontendContextApiControl.AppSummary app) { this.app = app; }
        public SystemSnapshotSummary getSnapshot() { return snapshot; }
        public void setSnapshot(SystemSnapshotSummary snapshot) { this.snapshot = snapshot; }
        public CoverageReportSummary getReport() { return report; }
        public void setReport(CoverageReportSummary report) { this.report = report; }
        public List<ClassCoverageSummary> getClassStats() { return classStats; }
        public void setClassStats(List<ClassCoverageSummary> classStats) { this.classStats = classStats; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class CoverageReportSummary {
        private String id;
        private String appId;
        private String versionNumber;
        private String repoBranch;
        private String repoCommitId;
        private Date createTime;
        private String createTimeText;
        private String lastProcessedTime;
        private long totalClasses;
        private long coveredClasses;
        private long totalMethods;
        private long coveredMethods;
        private long totalBranches;
        private long coveredBranches;
        private long totalBranchTargets;
        private long coveredBranchTargets;
        private long totalLines;
        private long coveredLines;
        private int totalComplexity;
        private Integer snapshotCount;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getRepoBranch() { return repoBranch; }
        public void setRepoBranch(String repoBranch) { this.repoBranch = repoBranch; }
        public String getRepoCommitId() { return repoCommitId; }
        public void setRepoCommitId(String repoCommitId) { this.repoCommitId = repoCommitId; }
        public Date getCreateTime() { return createTime; }
        public void setCreateTime(Date createTime) { this.createTime = createTime; }
        public String getCreateTimeText() { return createTimeText; }
        public void setCreateTimeText(String createTimeText) { this.createTimeText = createTimeText; }
        public String getLastProcessedTime() { return lastProcessedTime; }
        public void setLastProcessedTime(String lastProcessedTime) { this.lastProcessedTime = lastProcessedTime; }
        public long getTotalClasses() { return totalClasses; }
        public void setTotalClasses(long totalClasses) { this.totalClasses = totalClasses; }
        public long getCoveredClasses() { return coveredClasses; }
        public void setCoveredClasses(long coveredClasses) { this.coveredClasses = coveredClasses; }
        public long getTotalMethods() { return totalMethods; }
        public void setTotalMethods(long totalMethods) { this.totalMethods = totalMethods; }
        public long getCoveredMethods() { return coveredMethods; }
        public void setCoveredMethods(long coveredMethods) { this.coveredMethods = coveredMethods; }
        public long getTotalBranches() { return totalBranches; }
        public void setTotalBranches(long totalBranches) { this.totalBranches = totalBranches; }
        public long getCoveredBranches() { return coveredBranches; }
        public void setCoveredBranches(long coveredBranches) { this.coveredBranches = coveredBranches; }
        public long getTotalBranchTargets() { return totalBranchTargets; }
        public void setTotalBranchTargets(long totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
        public long getCoveredBranchTargets() { return coveredBranchTargets; }
        public void setCoveredBranchTargets(long coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
        public long getTotalLines() { return totalLines; }
        public void setTotalLines(long totalLines) { this.totalLines = totalLines; }
        public long getCoveredLines() { return coveredLines; }
        public void setCoveredLines(long coveredLines) { this.coveredLines = coveredLines; }
        public int getTotalComplexity() { return totalComplexity; }
        public void setTotalComplexity(int totalComplexity) { this.totalComplexity = totalComplexity; }
        public Integer getSnapshotCount() { return snapshotCount; }
        public void setSnapshotCount(Integer snapshotCount) { this.snapshotCount = snapshotCount; }
    }

    public static class SystemSnapshotCodePayload {
        private FrontendContextApiControl.AppSummary app;
        private SystemSnapshotSummary snapshot;
        private String className;
        private List<MethodCoverageSummary> methods;
        private String coloredSourceHtml;
        private String currentUserRole;

        public FrontendContextApiControl.AppSummary getApp() { return app; }
        public void setApp(FrontendContextApiControl.AppSummary app) { this.app = app; }
        public SystemSnapshotSummary getSnapshot() { return snapshot; }
        public void setSnapshot(SystemSnapshotSummary snapshot) { this.snapshot = snapshot; }
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public List<MethodCoverageSummary> getMethods() { return methods; }
        public void setMethods(List<MethodCoverageSummary> methods) { this.methods = methods; }
        public String getColoredSourceHtml() { return coloredSourceHtml; }
        public void setColoredSourceHtml(String coloredSourceHtml) { this.coloredSourceHtml = coloredSourceHtml; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class GraphNodeDetailPayload {
        private String id;
        private String title;
        private String name;
        private String type;
        private String ip;
        private String logPreview;
        private Map<String, Object> stats = new LinkedHashMap<>();
        private Map<String, Object> request = new LinkedHashMap<>();
        private List<GraphNodeDetailSection> sections = new ArrayList<>();
        private List<GraphSqlSummary> sqlGroups = new ArrayList<>();
        private List<GraphSqlSummary> sqlStatements = new ArrayList<>();
        private List<GraphTableOperationSummary> tableOperations = new ArrayList<>();
        private List<GraphRemoteCallSummary> remoteCalls = new ArrayList<>();
        private List<GraphRedisCommandSummary> redisCommands = new ArrayList<>();
        private List<GraphErrorSummary> errors = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public String getLogPreview() { return logPreview; }
        public void setLogPreview(String logPreview) { this.logPreview = logPreview; }
        public Map<String, Object> getStats() { return stats; }
        public void setStats(Map<String, Object> stats) { this.stats = stats; }
        public Map<String, Object> getRequest() { return request; }
        public void setRequest(Map<String, Object> request) { this.request = request; }
        public List<GraphNodeDetailSection> getSections() { return sections; }
        public void setSections(List<GraphNodeDetailSection> sections) { this.sections = sections; }
        public List<GraphSqlSummary> getSqlGroups() { return sqlGroups; }
        public void setSqlGroups(List<GraphSqlSummary> sqlGroups) { this.sqlGroups = sqlGroups; }
        public List<GraphSqlSummary> getSqlStatements() { return sqlStatements; }
        public void setSqlStatements(List<GraphSqlSummary> sqlStatements) { this.sqlStatements = sqlStatements; }
        public List<GraphTableOperationSummary> getTableOperations() { return tableOperations; }
        public void setTableOperations(List<GraphTableOperationSummary> tableOperations) { this.tableOperations = tableOperations; }
        public List<GraphRemoteCallSummary> getRemoteCalls() { return remoteCalls; }
        public void setRemoteCalls(List<GraphRemoteCallSummary> remoteCalls) { this.remoteCalls = remoteCalls; }
        public List<GraphRedisCommandSummary> getRedisCommands() { return redisCommands; }
        public void setRedisCommands(List<GraphRedisCommandSummary> redisCommands) { this.redisCommands = redisCommands; }
        public List<GraphErrorSummary> getErrors() { return errors; }
        public void setErrors(List<GraphErrorSummary> errors) { this.errors = errors; }
    }

    public static class GraphNodeDetailSection {
        private String title;
        private String kind = "fields";
        private String content;
        private List<GraphNodeDetailField> fields = new ArrayList<>();

        public GraphNodeDetailSection() {}
        public GraphNodeDetailSection(String title) { this.title = title; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<GraphNodeDetailField> getFields() { return fields; }
        public void setFields(List<GraphNodeDetailField> fields) { this.fields = fields; }
    }

    public static class GraphNodeDetailField {
        private String label;
        private String value;

        public GraphNodeDetailField() {}
        public GraphNodeDetailField(String label, String value) {
            this.label = label;
            this.value = value;
        }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public static class GraphSqlSummary {
        private String type;
        private String sql;
        private String jdbcUrl;
        private String addressIp;
        private String port;
        private String databaseName;
        private String databaseType;
        private List<String> executes = new ArrayList<>();
        private List<String[]> params = new ArrayList<>();
        private Integer count;
        private Long useTime;
        private String beginTime;
        private GraphErrorSummary error;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
        public String getAddressIp() { return addressIp; }
        public void setAddressIp(String addressIp) { this.addressIp = addressIp; }
        public String getPort() { return port; }
        public void setPort(String port) { this.port = port; }
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        public String getDatabaseType() { return databaseType; }
        public void setDatabaseType(String databaseType) { this.databaseType = databaseType; }
        public List<String> getExecutes() { return executes; }
        public void setExecutes(List<String> executes) { this.executes = executes; }
        public List<String[]> getParams() { return params; }
        public void setParams(List<String[]> params) { this.params = params; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public Long getUseTime() { return useTime; }
        public void setUseTime(Long useTime) { this.useTime = useTime; }
        public String getBeginTime() { return beginTime; }
        public void setBeginTime(String beginTime) { this.beginTime = beginTime; }
        public GraphErrorSummary getError() { return error; }
        public void setError(GraphErrorSummary error) { this.error = error; }
    }

    public static class GraphTableOperationSummary {
        private String action;
        private String model;
        private String tableName;
        private List<String> columns = new ArrayList<>();
        private String sql;

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
    }

    public static class GraphRemoteCallSummary {
        private String type;
        private String traceNodeId;
        private String title;
        private String interfaceName;
        private String methodName;
        private String url;
        private String headers;
        private String request;
        private String response;
        private String beginTime;
        private Long useTime;
        private String addressIp;
        private Map<String, String> remoteApp = new LinkedHashMap<>();
        private Map<String, Object> extra = new LinkedHashMap<>();
        private GraphErrorSummary error;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTraceNodeId() { return traceNodeId; }
        public void setTraceNodeId(String traceNodeId) { this.traceNodeId = traceNodeId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getInterfaceName() { return interfaceName; }
        public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getHeaders() { return headers; }
        public void setHeaders(String headers) { this.headers = headers; }
        public String getRequest() { return request; }
        public void setRequest(String request) { this.request = request; }
        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }
        public String getBeginTime() { return beginTime; }
        public void setBeginTime(String beginTime) { this.beginTime = beginTime; }
        public Long getUseTime() { return useTime; }
        public void setUseTime(Long useTime) { this.useTime = useTime; }
        public String getAddressIp() { return addressIp; }
        public void setAddressIp(String addressIp) { this.addressIp = addressIp; }
        public Map<String, String> getRemoteApp() { return remoteApp; }
        public void setRemoteApp(Map<String, String> remoteApp) { this.remoteApp = remoteApp; }
        public Map<String, Object> getExtra() { return extra; }
        public void setExtra(Map<String, Object> extra) { this.extra = extra; }
        public GraphErrorSummary getError() { return error; }
        public void setError(GraphErrorSummary error) { this.error = error; }
    }

    public static class GraphRedisCommandSummary {
        private String type;
        private String command;
        private String host;
        private String port;
        private String beginTime;
        private Long useTime;
        private GraphErrorSummary error;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public String getPort() { return port; }
        public void setPort(String port) { this.port = port; }
        public String getBeginTime() { return beginTime; }
        public void setBeginTime(String beginTime) { this.beginTime = beginTime; }
        public Long getUseTime() { return useTime; }
        public void setUseTime(Long useTime) { this.useTime = useTime; }
        public GraphErrorSummary getError() { return error; }
        public void setError(GraphErrorSummary error) { this.error = error; }
    }

    public static class GraphErrorSummary {
        private String type;
        private String code;
        private String message;
        private String errorStack;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getErrorStack() { return errorStack; }
        public void setErrorStack(String errorStack) { this.errorStack = errorStack; }
    }

    public static class MySnapshotReportPayload {
        private SnapshotVo snapshot;
        private CoverageReportSummary report;
        private List<ClassCoverageSummary> classStats;
        private String currentUserRole;

        public SnapshotVo getSnapshot() { return snapshot; }
        public void setSnapshot(SnapshotVo snapshot) { this.snapshot = snapshot; }
        public CoverageReportSummary getReport() { return report; }
        public void setReport(CoverageReportSummary report) { this.report = report; }
        public List<ClassCoverageSummary> getClassStats() { return classStats; }
        public void setClassStats(List<ClassCoverageSummary> classStats) { this.classStats = classStats; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class MySnapshotCodePayload {
        private FrontendContextApiControl.AppSummary app;
        private SnapshotVo snapshot;
        private String className;
        private List<MethodCoverageSummary> methods;
        private String coloredSourceHtml;
        private String currentUserRole;

        public FrontendContextApiControl.AppSummary getApp() { return app; }
        public void setApp(FrontendContextApiControl.AppSummary app) { this.app = app; }
        public SnapshotVo getSnapshot() { return snapshot; }
        public void setSnapshot(SnapshotVo snapshot) { this.snapshot = snapshot; }
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public List<MethodCoverageSummary> getMethods() { return methods; }
        public void setMethods(List<MethodCoverageSummary> methods) { this.methods = methods; }
        public String getColoredSourceHtml() { return coloredSourceHtml; }
        public void setColoredSourceHtml(String coloredSourceHtml) { this.coloredSourceHtml = coloredSourceHtml; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class MySnapshotCodeReportPayload {
        private String appId;
        private CoverageReportSummary report;
        private List<ClassCoverageSummary> classStats;
        private List<MySnapshotCodeRelationshipGroupSummary> codeRelationships;
        private String currentUserRole;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public CoverageReportSummary getReport() { return report; }
        public void setReport(CoverageReportSummary report) { this.report = report; }
        public List<ClassCoverageSummary> getClassStats() { return classStats; }
        public void setClassStats(List<ClassCoverageSummary> classStats) { this.classStats = classStats; }
        public List<MySnapshotCodeRelationshipGroupSummary> getCodeRelationships() { return codeRelationships; }
        public void setCodeRelationships(List<MySnapshotCodeRelationshipGroupSummary> codeRelationships) { this.codeRelationships = codeRelationships; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class MySnapshotCodeRelationshipGroupSummary {
        private String requestUrl;
        private List<MySnapshotCodeRelationshipMethodSummary> methods;

        public String getRequestUrl() { return requestUrl; }
        public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }
        public List<MySnapshotCodeRelationshipMethodSummary> getMethods() { return methods; }
        public void setMethods(List<MySnapshotCodeRelationshipMethodSummary> methods) { this.methods = methods; }
    }

    public static class MySnapshotCodeRelationshipMethodSummary {
        private String className;
        private String methodName;
        private String methodDescriptor;
        private List<Integer> doLines;
        private int lineTotalCount;
        private int coveredLineCount;
        private int branchTotalCount;
        private int branchCoveredCount;
        private int branchTargetTotalCount;
        private int branchTargetCoveredCount;

        public MySnapshotCodeRelationshipMethodSummary() {
        }

        public MySnapshotCodeRelationshipMethodSummary(String className,
                                                       String methodName,
                                                       String methodDescriptor,
                                                       List<Integer> doLines) {
            this.className = className;
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
            this.doLines = doLines;
        }

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getMethodDescriptor() { return methodDescriptor; }
        public void setMethodDescriptor(String methodDescriptor) { this.methodDescriptor = methodDescriptor; }
        public List<Integer> getDoLines() { return doLines; }
        public void setDoLines(List<Integer> doLines) { this.doLines = doLines; }
        public int getLineTotalCount() { return lineTotalCount; }
        public void setLineTotalCount(int lineTotalCount) { this.lineTotalCount = lineTotalCount; }
        public int getCoveredLineCount() { return coveredLineCount; }
        public void setCoveredLineCount(int coveredLineCount) { this.coveredLineCount = coveredLineCount; }
        public int getBranchTotalCount() { return branchTotalCount; }
        public void setBranchTotalCount(int branchTotalCount) { this.branchTotalCount = branchTotalCount; }
        public int getBranchCoveredCount() { return branchCoveredCount; }
        public void setBranchCoveredCount(int branchCoveredCount) { this.branchCoveredCount = branchCoveredCount; }
        public int getBranchTargetTotalCount() { return branchTargetTotalCount; }
        public void setBranchTargetTotalCount(int branchTargetTotalCount) { this.branchTargetTotalCount = branchTargetTotalCount; }
        public int getBranchTargetCoveredCount() { return branchTargetCoveredCount; }
        public void setBranchTargetCoveredCount(int branchTargetCoveredCount) { this.branchTargetCoveredCount = branchTargetCoveredCount; }
    }

    public static class MySnapshotAggregateCodePayload {
        private FrontendContextApiControl.AppSummary app;
        private String className;
        private List<MethodCoverageSummary> methods;
        private String coloredSourceHtml;
        private String currentUserRole;

        public FrontendContextApiControl.AppSummary getApp() { return app; }
        public void setApp(FrontendContextApiControl.AppSummary app) { this.app = app; }
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public List<MethodCoverageSummary> getMethods() { return methods; }
        public void setMethods(List<MethodCoverageSummary> methods) { this.methods = methods; }
        public String getColoredSourceHtml() { return coloredSourceHtml; }
        public void setColoredSourceHtml(String coloredSourceHtml) { this.coloredSourceHtml = coloredSourceHtml; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    private static class MySnapshotCodeReportAggregate {
        private final CoverageReportSummary summary;
        private final List<ClassCoverageSummary> classStats;
        private final List<MySnapshotCodeRelationshipGroupSummary> codeRelationships;
        private final String appId;

        private MySnapshotCodeReportAggregate(CoverageReportSummary summary,
                                              List<ClassCoverageSummary> classStats,
                                              List<MySnapshotCodeRelationshipGroupSummary> codeRelationships,
                                              String appId) {
            this.summary = summary;
            this.classStats = classStats;
            this.codeRelationships = codeRelationships;
            this.appId = appId;
        }

        public CoverageReportSummary getSummary() { return summary; }
        public List<ClassCoverageSummary> getClassStats() { return classStats; }
        public List<MySnapshotCodeRelationshipGroupSummary> getCodeRelationships() { return codeRelationships; }
        public String getAppId() { return appId; }
    }

    private static class MySnapshotReportAggregate {
        private final CoverageReportSummary summary;
        private final List<ClassCoverageSummary> classStats;

        private MySnapshotReportAggregate(CoverageReportSummary summary, List<ClassCoverageSummary> classStats) {
            this.summary = summary;
            this.classStats = classStats;
        }

        public CoverageReportSummary getSummary() {
            return summary;
        }

        public List<ClassCoverageSummary> getClassStats() {
            return classStats;
        }
    }

    public static class ClassCoverageSummary {
        private String className;
        private long totalMethods;
        private long coveredMethods;
        private long totalLines;
        private long coveredLines;
        private long totalBranches;
        private long coveredBranches;
        private long totalBranchTargets;
        private long coveredBranchTargets;
        private double branchRate;
        private int totalComplexity;

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public long getTotalMethods() { return totalMethods; }
        public void setTotalMethods(long totalMethods) { this.totalMethods = totalMethods; }
        public long getCoveredMethods() { return coveredMethods; }
        public void setCoveredMethods(long coveredMethods) { this.coveredMethods = coveredMethods; }
        public long getTotalLines() { return totalLines; }
        public void setTotalLines(long totalLines) { this.totalLines = totalLines; }
        public long getCoveredLines() { return coveredLines; }
        public void setCoveredLines(long coveredLines) { this.coveredLines = coveredLines; }
        public long getTotalBranches() { return totalBranches; }
        public void setTotalBranches(long totalBranches) { this.totalBranches = totalBranches; }
        public long getCoveredBranches() { return coveredBranches; }
        public void setCoveredBranches(long coveredBranches) { this.coveredBranches = coveredBranches; }
        public long getTotalBranchTargets() { return totalBranchTargets; }
        public void setTotalBranchTargets(long totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
        public long getCoveredBranchTargets() { return coveredBranchTargets; }
        public void setCoveredBranchTargets(long coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
        public double getBranchRate() { return branchRate; }
        public void setBranchRate(double branchRate) { this.branchRate = branchRate; }
        public int getTotalComplexity() { return totalComplexity; }
        public void setTotalComplexity(int totalComplexity) { this.totalComplexity = totalComplexity; }
    }

    public static class MethodCoverageSummary {
        private String methodName;
        private String methodDesc;
        private int totalLines;
        private int coveredLines;
        private int totalBranches;
        private int coveredBranches;
        private int complexity;
        private boolean covered;
        private int totalBranchTargets;
        private int coveredBranchTargets;
        private Double branchRate;

        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getMethodDesc() { return methodDesc; }
        public void setMethodDesc(String methodDesc) { this.methodDesc = methodDesc; }
        public int getTotalLines() { return totalLines; }
        public void setTotalLines(int totalLines) { this.totalLines = totalLines; }
        public int getCoveredLines() { return coveredLines; }
        public void setCoveredLines(int coveredLines) { this.coveredLines = coveredLines; }
        public int getTotalBranches() { return totalBranches; }
        public void setTotalBranches(int totalBranches) { this.totalBranches = totalBranches; }
        public int getCoveredBranches() { return coveredBranches; }
        public void setCoveredBranches(int coveredBranches) { this.coveredBranches = coveredBranches; }
        public int getComplexity() { return complexity; }
        public void setComplexity(int complexity) { this.complexity = complexity; }
        public boolean isCovered() { return covered; }
        public void setCovered(boolean covered) { this.covered = covered; }
        public int getTotalBranchTargets() { return totalBranchTargets; }
        public void setTotalBranchTargets(int totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
        public int getCoveredBranchTargets() { return coveredBranchTargets; }
        public void setCoveredBranchTargets(int coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
        public Double getBranchRate() { return branchRate; }
        public void setBranchRate(Double branchRate) { this.branchRate = branchRate; }
    }

    public static class MySnapshotDetailPayload {
        private SnapshotVo snapshot;
        private FrontendContextApiControl.UserSummary createUser;
        private List<LabelGroup.Label> labels;
        private List<UsecaseVo> usecases;
        private List<UsecaseVo> allUsecases;
        private String shareUrl;
        private String currentUserRole;

        public SnapshotVo getSnapshot() { return snapshot; }
        public void setSnapshot(SnapshotVo snapshot) { this.snapshot = snapshot; }
        public FrontendContextApiControl.UserSummary getCreateUser() { return createUser; }
        public void setCreateUser(FrontendContextApiControl.UserSummary createUser) { this.createUser = createUser; }
        public List<LabelGroup.Label> getLabels() { return labels; }
        public void setLabels(List<LabelGroup.Label> labels) { this.labels = labels; }
        public List<UsecaseVo> getUsecases() { return usecases; }
        public void setUsecases(List<UsecaseVo> usecases) { this.usecases = usecases; }
        public List<UsecaseVo> getAllUsecases() { return allUsecases; }
        public void setAllUsecases(List<UsecaseVo> allUsecases) { this.allUsecases = allUsecases; }
        public String getShareUrl() { return shareUrl; }
        public void setShareUrl(String shareUrl) { this.shareUrl = shareUrl; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class SnapshotDirectorySummary {
        private String id;
        private String parentId;
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getParentId() { return parentId; }
        public void setParentId(String parentId) { this.parentId = parentId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class SystemSnapshotSummary {
        private String id;
        private String projectId;
        private String appId;
        private String traceId;
        private String title;
        private String subTitle;
        private String topicImage;
        private String describe;
        private String directory;
        private String version;
        private Integer versionCycle;
        private Date versionLastUpdate;
        private String versionLastUpdateText;
        private String versionLastUpdateRelativeText;
        private List<String> labels;
        private List<String> principals;
        private Integer reportStatus;
        private Date createTime;
        private Date updateTime;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSubTitle() { return subTitle; }
        public void setSubTitle(String subTitle) { this.subTitle = subTitle; }
        public String getTopicImage() { return topicImage; }
        public void setTopicImage(String topicImage) { this.topicImage = topicImage; }
        public String getDescribe() { return describe; }
        public void setDescribe(String describe) { this.describe = describe; }
        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public Integer getVersionCycle() { return versionCycle; }
        public void setVersionCycle(Integer versionCycle) { this.versionCycle = versionCycle; }
        public Date getVersionLastUpdate() { return versionLastUpdate; }
        public void setVersionLastUpdate(Date versionLastUpdate) { this.versionLastUpdate = versionLastUpdate; }
        public String getVersionLastUpdateText() { return versionLastUpdateText; }
        public void setVersionLastUpdateText(String versionLastUpdateText) { this.versionLastUpdateText = versionLastUpdateText; }
        public String getVersionLastUpdateRelativeText() { return versionLastUpdateRelativeText; }
        public void setVersionLastUpdateRelativeText(String versionLastUpdateRelativeText) { this.versionLastUpdateRelativeText = versionLastUpdateRelativeText; }
        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }
        public List<String> getPrincipals() { return principals; }
        public void setPrincipals(List<String> principals) { this.principals = principals; }
        public Integer getReportStatus() { return reportStatus; }
        public void setReportStatus(Integer reportStatus) { this.reportStatus = reportStatus; }
        public Date getCreateTime() { return createTime; }
        public void setCreateTime(Date createTime) { this.createTime = createTime; }
        public Date getUpdateTime() { return updateTime; }
        public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    }

    public static class DynamicItemSummary {
        private String time;
        private String title;
        private String type;
        private String describe;
        private Date date;

        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDescribe() { return describe; }
        public void setDescribe(String describe) { this.describe = describe; }
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class SaveSnapshotDirectoryRequest {
        private Integer id;
        private String parentId;
        private String name;

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public String getParentId() { return parentId; }
        public void setParentId(String parentId) { this.parentId = parentId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class BindUsecasesRequest {
        private List<String> usecaseIds;

        public List<String> getUsecaseIds() { return usecaseIds; }
        public void setUsecaseIds(List<String> usecaseIds) { this.usecaseIds = usecaseIds; }
    }

    public static class BatchBindUsecasesRequest {
        private List<String> snapshotIds;
        private List<String> usecaseIds;

        public List<String> getSnapshotIds() { return snapshotIds; }
        public void setSnapshotIds(List<String> snapshotIds) { this.snapshotIds = snapshotIds; }
        public List<String> getUsecaseIds() { return usecaseIds; }
        public void setUsecaseIds(List<String> usecaseIds) { this.usecaseIds = usecaseIds; }
    }

    public static class UpdateSystemSnapshotBasicRequest {
        private String title;
        private String describe;
        private String version;
        private Integer versionCycle;
        private List<String> labels;
        private List<String> principals;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescribe() { return describe; }
        public void setDescribe(String describe) { this.describe = describe; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public Integer getVersionCycle() { return versionCycle; }
        public void setVersionCycle(Integer versionCycle) { this.versionCycle = versionCycle; }
        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }
        public List<String> getPrincipals() { return principals; }
        public void setPrincipals(List<String> principals) { this.principals = principals; }
    }

    public static class SnapshotCommentRequest {
        private String content;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class DeleteSnapshotCommentRequest {
        private String content;
        private String dateTime;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getDateTime() { return dateTime; }
        public void setDateTime(String dateTime) { this.dateTime = dateTime; }
    }

    public static class UpdateMySnapshotBasicRequest {
        private String name;
        private String describe;
        private List<String> labels;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescribe() { return describe; }
        public void setDescribe(String describe) { this.describe = describe; }
        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }
    }

    public static class UpdateShareRequest {
        private Boolean share;

        public Boolean getShare() { return share; }
        public void setShare(Boolean share) { this.share = share; }
    }
}
