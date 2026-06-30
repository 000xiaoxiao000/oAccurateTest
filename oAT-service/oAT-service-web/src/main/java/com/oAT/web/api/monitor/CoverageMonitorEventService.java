package com.oAT.web.api.monitor;

import com.oAT.agent.model.Application;
import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.api.snapshot.GraphNodeDetailPayload;
import com.oAT.web.control.entity.GraphView;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.coveragecore.report.CoverageFootprintSnapshotService;
import com.oAT.web.service.AppService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.TraceItemSearchParam;
import com.oAT.web.service.entity.TraceItemVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CoverageMonitorEventService {

    public static final String TRACE_ID_PREFIX = "coverage:";
    public static final String NODE_ID = "coverage";

    private final CoverageFootprintSnapshotService coverageSnapshotService;
    private final AppService appService;

    public CoverageMonitorEventService(CoverageFootprintSnapshotService coverageSnapshotService,
                                       AppService appService) {
        this.coverageSnapshotService = coverageSnapshotService;
        this.appService = appService;
    }

    public boolean isCoverageTraceId(String traceId) {
        return StringUtils.hasText(traceId) && traceId.startsWith(TRACE_ID_PREFIX);
    }

    public List<TraceItemVo> listEvents(String projectId, int upToTimeSeconds, TraceItemSearchParam filter) {
        List<CoverageFootprintSnapshotService.CoverageFootprintSnapshot> snapshots =
                coverageSnapshotService.listFootprints(projectId, null, null, null, null);
        long threshold = System.currentTimeMillis() - (Math.max(1, upToTimeSeconds) * 1000L);
        int maxSize = filter == null || filter.getMaxSize() == null ? 200 : filter.getMaxSize();
        List<TraceItemVo> result = new ArrayList<>();
        for (CoverageFootprintSnapshotService.CoverageFootprintSnapshot snapshot : snapshots) {
            if (snapshot == null || !StringUtils.hasText(snapshot.getRawReportId())) {
                continue;
            }
            Long timestamp = snapshot.getTimestamp();
            if (timestamp != null && timestamp > 0 && timestamp < threshold) {
                continue;
            }
            if (filter != null && filter.getAppIds() != null && !filter.getAppIds().isEmpty()
                    && !filter.getAppIds().contains(snapshot.getAppId())) {
                continue;
            }
            result.add(toTraceItem(snapshot));
            if (result.size() >= maxSize) {
                break;
            }
        }
        return result;
    }

    public Map<String, TraceNode> buildTraceNodes(String projectId, String traceId) {
        CoverageFootprintSnapshotService.CoverageFootprintSnapshot snapshot = getSnapshot(projectId, traceId);
        HttpTraceNode node = new HttpTraceNode();
        node.setTraceId(toTraceId(snapshot));
        node.setTraceNodeId("0");
        node.setSessionId(snapshot.getRawReportId());
        node.setStatus(TraceNode.Status.succeed.name());
        node.setBeginTime(snapshot.getTimestamp() == null ? System.currentTimeMillis() : snapshot.getTimestamp());
        node.setEndTime(node.getBeginTime());
        node.setUseTime(0L);
        node.setRequestMethod("COVERAGE");
        node.setRequestUrl(defaultTitle(snapshot));
        node.setResponseCode("200");
        node.setResponseType("coverage");
        node.setLog(buildDescription(snapshot));
        node.setApp(buildApplication(snapshot));
        Map<String, TraceNode> nodes = new LinkedHashMap<>();
        nodes.put("0", node);
        return nodes;
    }

    public GraphView buildGraph(String projectId, String traceId) {
        CoverageFootprintSnapshotService.CoverageFootprintSnapshot snapshot = getSnapshot(projectId, traceId);
        GraphView graph = new GraphView();
        graph.setTraceId(toTraceId(snapshot));
        graph.setTitle(defaultTitle(snapshot));
        graph.setEdges(Collections.emptyList());

        GraphView.Nodes node = new GraphView.Nodes();
        node.setId(NODE_ID);
        node.setTitle(snapshot.getAppName() == null ? snapshot.getAppId() : snapshot.getAppName());
        node.setSubTitle(languageText(snapshot) + " 覆盖率上报");
        node.setIcon("coverage");
        node.setType("coverage");
        node.setState("normal");
        node.setTips(buildDescription(snapshot));
        node.setData(toData(snapshot));
        graph.setNodes(List.of(node));
        graph.setShowDefaultNode(node);
        graph.setHasCodeLayer(true);
        return graph;
    }

    public ResultNotified<GraphNodeDetailPayload> buildNodeDetail(String projectId, String traceId, String nodeId) {
        CoverageFootprintSnapshotService.CoverageFootprintSnapshot snapshot = getSnapshot(projectId, traceId);
        GraphNodeDetailPayload payload = new GraphNodeDetailPayload();
        payload.setId(StringUtils.hasText(nodeId) ? nodeId : NODE_ID);
        payload.setTitle(defaultTitle(snapshot));
        payload.setName(snapshot.getAppName() == null ? snapshot.getAppId() : snapshot.getAppName());
        payload.setType("coverage");
        payload.getStats().put("语言", languageText(snapshot));
        payload.getStats().put("版本", emptyDash(snapshot.getVersionNumber()));
        payload.getStats().put("分支", emptyDash(snapshot.getBranch()));
        payload.getStats().put("Commit", emptyDash(snapshot.getCommitId()));
        payload.getStats().put("Build", emptyDash(snapshot.getBuildId()));
        payload.getStats().put("阶段", emptyDash(snapshot.getTestStage()));
        payload.getStats().put("用例", emptyDash(snapshot.getCaseName()));

        GraphNodeDetailPayload.GraphNodeDetailSection section = new GraphNodeDetailPayload.GraphNodeDetailSection("覆盖率上报");
        section.getFields().add(new GraphNodeDetailPayload.GraphNodeDetailField("应用", emptyDash(snapshot.getAppName())));
        section.getFields().add(new GraphNodeDetailPayload.GraphNodeDetailField("语言", languageText(snapshot)));
        section.getFields().add(new GraphNodeDetailPayload.GraphNodeDetailField("版本", emptyDash(snapshot.getVersionNumber())));
        section.getFields().add(new GraphNodeDetailPayload.GraphNodeDetailField("分支", emptyDash(snapshot.getBranch())));
        section.getFields().add(new GraphNodeDetailPayload.GraphNodeDetailField("Commit", emptyDash(snapshot.getCommitId())));
        section.getFields().add(new GraphNodeDetailPayload.GraphNodeDetailField("Build", emptyDash(snapshot.getBuildId())));
        section.getFields().add(new GraphNodeDetailPayload.GraphNodeDetailField("阶段", emptyDash(snapshot.getTestStage())));
        section.getFields().add(new GraphNodeDetailPayload.GraphNodeDetailField("用例", emptyDash(snapshot.getCaseName())));
        payload.getSections().add(section);
        return new ResultNotified<>(true, "获取覆盖率监控详情成功", payload);
    }

    public CoverageFootprintSnapshotService.CoverageFootprintSnapshot getSnapshot(String projectId, String traceId) {
        String key = rawReportId(traceId);
        try {
            return coverageSnapshotService.getFootprint(projectId, key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("覆盖率上报不存在: " + key);
        }
    }

    public String toTraceId(CoverageFootprintSnapshotService.CoverageFootprintSnapshot snapshot) {
        return TRACE_ID_PREFIX + snapshot.getRawReportId();
    }

    public String defaultTitle(CoverageFootprintSnapshotService.CoverageFootprintSnapshot snapshot) {
        return String.join(" · ",
                nonEmpty(snapshot.getAppName(), snapshot.getAppId(), "应用"),
                languageText(snapshot),
                nonEmpty(snapshot.getCaseName(), snapshot.getBuildId(), "覆盖率上报"));
    }

    public String buildDescription(CoverageFootprintSnapshotService.CoverageFootprintSnapshot snapshot) {
        List<String> parts = new ArrayList<>();
        addPart(parts, snapshot.getVersionNumber());
        addPart(parts, snapshot.getBranch());
        addPart(parts, snapshot.getCommitId());
        addPart(parts, snapshot.getBuildId());
        addPart(parts, snapshot.getTestStage());
        return parts.isEmpty() ? "覆盖率上报" : String.join(" / ", parts);
    }

    private TraceItemVo toTraceItem(CoverageFootprintSnapshotService.CoverageFootprintSnapshot snapshot) {
        TraceItemVo item = new TraceItemVo();
        item.setTraceId(toTraceId(snapshot));
        item.setTitle(defaultTitle(snapshot));
        item.setCacheTime(snapshot.getTimestamp() == null ? System.currentTimeMillis() : snapshot.getTimestamp());
        item.setValidity(3600);
        item.setAppId(snapshot.getAppId());
        item.setAddressIp(languageText(snapshot));
        item.setClientIp(snapshot.getBuildId());
        item.setEntryType("coverage");
        item.setEntryName(languageText(snapshot));
        item.setDisplayName(defaultTitle(snapshot));
        item.setStatus(TraceNode.Status.succeed.name());
        return item;
    }

    private Application buildApplication(CoverageFootprintSnapshotService.CoverageFootprintSnapshot snapshot) {
        AppVo app = StringUtils.hasText(snapshot.getAppId()) ? appService.getApp(snapshot.getAppId()) : null;
        String appName = nonEmpty(snapshot.getAppName(), app == null ? null : app.getName(), snapshot.getAppId());
        String srcName = app == null ? null : app.getSrcName();
        return new Application(snapshot.getAppId(), appName, srcName);
    }

    private Map<String, Object> toData(CoverageFootprintSnapshotService.CoverageFootprintSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rawReportId", snapshot.getRawReportId());
        data.put("appId", snapshot.getAppId());
        data.put("language", snapshot.getLanguage());
        data.put("versionNumber", snapshot.getVersionNumber());
        data.put("branch", snapshot.getBranch());
        data.put("commitId", snapshot.getCommitId());
        data.put("buildId", snapshot.getBuildId());
        data.put("testStage", snapshot.getTestStage());
        data.put("caseName", snapshot.getCaseName());
        data.put("timestamp", snapshot.getTimestamp());
        return data;
    }

    private String rawReportId(String traceId) {
        if (!isCoverageTraceId(traceId)) {
            return traceId;
        }
        return traceId.substring(TRACE_ID_PREFIX.length());
    }

    private String languageText(CoverageFootprintSnapshotService.CoverageFootprintSnapshot snapshot) {
        return nonEmpty(snapshot.getLanguage(), "COVERAGE");
    }

    private String emptyDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String nonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private void addPart(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value);
        }
    }
}
