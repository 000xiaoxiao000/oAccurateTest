package com.oAT.web.control.api;

import com.oAT.web.control.TraceGraphParse;
import com.oAT.web.control.entity.GraphNode;
import com.oAT.web.control.entity.GraphView;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.domain.RemoteCallResolver;
import com.oAT.web.esDao.ApiEndpointRepository;
import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.entity.ApiEndpointIndex;
import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.SystemSnapshotService;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.UserService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.LableType;
import com.oAT.web.service.entity.SimpleRelationOption;
import com.oAT.web.service.entity.SnapshotVo;
import com.oAT.web.service.entity.UsecaseDetailVo;
import com.oAT.web.service.entity.UsecaseVo;
import com.oAT.web.service.entity.UserVo;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/share/api")
public class ShareApiControl {

    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder().build();

    private final SnapshotService snapshotService;
    private final UsecaseService usecaseService;
    private final SystemSnapshotService systemSnapshotService;
    private final ProjectService projectService;
    private final UserService userService;
    private final AppService appService;
    private final ApiEndpointRepository apiEndpointRepository;
    private final CaseCenterRepository caseCenterRepository;

    public ShareApiControl(SnapshotService snapshotService,
                           UsecaseService usecaseService,
                           SystemSnapshotService systemSnapshotService,
                           ProjectService projectService,
                           UserService userService,
                           AppService appService,
                           ApiEndpointRepository apiEndpointRepository,
                           CaseCenterRepository caseCenterRepository) {
        this.snapshotService = snapshotService;
        this.usecaseService = usecaseService;
        this.systemSnapshotService = systemSnapshotService;
        this.projectService = projectService;
        this.userService = userService;
        this.appService = appService;
        this.apiEndpointRepository = apiEndpointRepository;
        this.caseCenterRepository = caseCenterRepository;
    }

    @GetMapping("/snapshot/{id}")
    public ResultNotified<PublicSnapshotPayload> snapshot(@PathVariable String id) {
        SnapshotVo snapshot = snapshotService.get(id);
        ResultNotified<PublicSnapshotPayload> accessDenied = validateSnapshotShare(snapshot);
        if (accessDenied != null) {
            return accessDenied;
        }

        PublicSnapshotPayload payload = new PublicSnapshotPayload();
        payload.setProjectId(snapshot.getProjectId());
        payload.setSnapshot(snapshot);
        payload.setCreateUser(toUserSummary(userService.getUser(snapshot.getCreateUser())));
        payload.setLabels(ArrayUtils.isNotEmpty(snapshot.getLabels())
                ? projectService.getLables(snapshot.getProjectId(), LableType.snapshot, snapshot.getLabels())
                : new ArrayList<>());
        payload.setUsecases(usecaseService.getUsecasesBySnapshot(snapshot.getProjectId(), id));
        payload.setAllUsecases(new ArrayList<>());
        payload.setShareUrl("/share/snapshot/" + id);
        payload.setCurrentUserRole("visitor");
        return new ResultNotified<>(true, "获取共享快照成功", payload);
    }

    @GetMapping("/snapshot/{id}/graph")
    public ResultNotified<GraphView> snapshotGraph(@PathVariable String id) {
        SnapshotVo snapshot = snapshotService.get(id);
        ResultNotified<GraphView> accessDenied = validateSnapshotShare(snapshot);
        if (accessDenied != null) {
            return accessDenied;
        }
        return new ResultNotified<>(true, "获取共享快照链路图成功", buildTraceGraph(snapshot));
    }

    @GetMapping("/snapshot/{id}/graph/nodes/{nodeId}")
    public ResultNotified<SnapshotApiControl.GraphNodeDetailPayload> snapshotGraphNode(@PathVariable String id,
                                                                                        @PathVariable String nodeId) {
        SnapshotVo snapshot = snapshotService.get(id);
        ResultNotified<SnapshotApiControl.GraphNodeDetailPayload> accessDenied = validateSnapshotShare(snapshot);
        if (accessDenied != null) {
            return accessDenied;
        }
        TraceGraphParse parse = new TraceGraphParse(buildTraceNodeMap(snapshot.getTraceId()), buildRemoteCallResolver(snapshot.getProjectId()));
        GraphNode graphNode = parse.getGraphNode(nodeId);
        if (graphNode == null) {
            return new ResultNotified<>(false, "找不到节点 id=" + nodeId);
        }
        return new ResultNotified<>(true, "获取共享快照节点成功", SnapshotApiControl.toGraphNodeDetail(graphNode));
    }

    @GetMapping("/usecase/{id}")
    public ResultNotified<PublicUsecasePayload> usecase(@PathVariable String id) {
        CaseCenterIndex index = caseCenterRepository.findById(id).orElse(null);
        if (index == null || index.getUsecase() == null) {
            return new ResultNotified<>(false, "找不到指定用例");
        }
        if (!BooleanUtils.isTrue(index.getUsecase().getShare())) {
            return new ResultNotified<>(false, "当前用例未开放共享");
        }
        String projectId = index.getUsecase().getProjectId();
        UsecaseDetailVo usecase = usecaseService.getUsecaseDetail(projectId, id);

        PublicUsecasePayload payload = new PublicUsecasePayload();
        payload.setProjectId(projectId);
        payload.setUsecase(usecase);
        payload.setContentHtml(StringUtils.hasText(usecase.getContent()) ? MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(usecase.getContent())) : "");
        payload.setCurrentUserRole("visitor");

        UserVo lastUpdateAuthor = userService.getUser(usecase.getLastUpdateAuthor());
        if (lastUpdateAuthor != null) {
            payload.setLastUpdateAuthor(toUserSummary(lastUpdateAuthor));
        }
        if (!ObjectUtils.isEmpty(usecase.getSnapshots())) {
            payload.setSnapshots(snapshotService.getByIds(usecase.getSnapshots()));
        }
        if (!ObjectUtils.isEmpty(usecase.getSystemSnapshots())) {
            payload.setSystemSnapshots(Arrays.stream(usecase.getSystemSnapshots())
                    .map(this::getSystemSnapshotOption)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }
        if (!ObjectUtils.isEmpty(usecase.getLabels())) {
            payload.setLabels(projectService.getLables(projectId, LableType.usecase, usecase.getLabels()));
        }
        return new ResultNotified<>(true, "获取共享用例成功", payload);
    }

    private <T> ResultNotified<T> validateSnapshotShare(SnapshotVo snapshot) {
        if (snapshot == null) {
            return new ResultNotified<>(false, "找不到指定快照");
        }
        if (BooleanUtils.isTrue(snapshot.getDisable())) {
            return new ResultNotified<>(false, "快照已被删除");
        }
        if (!BooleanUtils.isTrue(snapshot.getShare())) {
            return new ResultNotified<>(false, "当前快照未开放共享");
        }
        return null;
    }

    private GraphView buildTraceGraph(SnapshotVo snapshot) {
        return new TraceGraphParse(buildTraceNodeMap(snapshot.getTraceId()), buildRemoteCallResolver(snapshot.getProjectId())).getGraphView();
    }

    private Map<String, com.oAT.agent.model.TraceNode> buildTraceNodeMap(String traceId) {
        Collection<com.oAT.agent.model.TraceNode> nodes = snapshotService.getTraceNodes(traceId);
        Map<String, com.oAT.agent.model.TraceNode> nodeMap = new LinkedHashMap<>();
        if (nodes == null) {
            return nodeMap;
        }
        for (com.oAT.agent.model.TraceNode node : nodes) {
            if (node != null && StringUtils.hasText(node.getTraceNodeId())) {
                nodeMap.put(node.getTraceNodeId(), node);
            }
        }
        return nodeMap;
    }

    private RemoteCallResolver buildRemoteCallResolver(String projectId) {
        List<AppVo> apps = appService.getAppList(projectId);
        List<ApiEndpointIndex> endpoints = apps.stream()
                .flatMap(app -> apiEndpointRepository.findByAppIdOrderByEndpointTypeAscUrlAsc(app.getId()).stream())
                .collect(Collectors.toList());
        return new RemoteCallResolver(apps, endpoints);
    }

    private SimpleRelationOption getSystemSnapshotOption(String snapshotId) {
        if (!StringUtils.hasText(snapshotId)) {
            return null;
        }
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        if (snapshot == null) {
            return null;
        }
        AppVo app = appService.getApp(snapshot.getAppId());
        if (app == null) {
            return null;
        }
        return new SimpleRelationOption(
                snapshot.getId(),
                app.getName() + " / " + snapshot.getTitle(),
                "/p/" + snapshot.getProjectId() + "/apps/" + app.getId() + "/snapshots/" + snapshot.getId(),
                false
        );
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

    private String limitText(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    public static class PublicSnapshotPayload extends SnapshotApiControl.MySnapshotDetailPayload {
        private String projectId;

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
    }

    public static class PublicUsecasePayload extends UsecaseApiControl.UsecaseDetailPayload {
        private String projectId;

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
    }
}
