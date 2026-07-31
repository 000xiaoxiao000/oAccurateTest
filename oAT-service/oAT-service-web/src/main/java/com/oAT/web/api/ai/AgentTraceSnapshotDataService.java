package com.oAT.web.api.ai;

import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.web.esDao.TraceNodeRepository;
import com.oAT.web.esDao.TraceSummaryRepository;
import com.oAT.web.esDao.entity.TraceNodeIndex;
import com.oAT.web.esDao.entity.TraceSummaryIndex;
import com.oAT.web.service.AppService;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.SnapshotVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentTraceSnapshotDataService {

    private static final Logger logger = LoggerFactory.getLogger(AgentTraceSnapshotDataService.class);

    private final AppService appService;
    private final SnapshotService snapshotService;
    private final TraceNodeRepository traceNodeRepository;
    private final TraceSummaryRepository traceSummaryRepository;
    private final com.oAT.web.coverage.CoverageStorage coverageStorage;

    public AgentTraceSnapshotDataService(AppService appService,
                                         SnapshotService snapshotService,
                                         TraceNodeRepository traceNodeRepository,
                                         TraceSummaryRepository traceSummaryRepository,
                                         com.oAT.web.coverage.CoverageStorage coverageStorage) {
        this.appService = appService;
        this.snapshotService = snapshotService;
        this.traceNodeRepository = traceNodeRepository;
        this.traceSummaryRepository = traceSummaryRepository;
        this.coverageStorage = coverageStorage;
    }

    public List<Map<String, Object>> getTraceList(String projectId, String appId, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            int normalizedLimit = limit <= 0 ? 20 : limit;
            if (StringUtils.hasText(appId)) {
                result.addAll(getTraceListForApp(appId, normalizedLimit));
            } else {
                List<AppVo> apps = appService.getAppList(projectId);
                if (apps != null && !apps.isEmpty()) {
                    int perApp = Math.max(1, normalizedLimit / apps.size());
                    for (AppVo app : apps) {
                        result.addAll(getTraceListForApp(app.getId(), perApp));
                        if (result.size() >= normalizedLimit) {
                            break;
                        }
                    }
                }
            }

            result.sort((a, b) -> compareCreateTime(a.get("createTime"), b.get("createTime")));
            if (result.size() > normalizedLimit) {
                return result.subList(0, normalizedLimit);
            }
        } catch (Exception e) {
            logger.error("Get trace list failed: projectId={}, appId={}", projectId, appId, e);
        }
        return result;
    }

    public Map<String, Object> getTraceDetail(String traceId) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!StringUtils.hasText(traceId)) {
                return result;
            }

            List<TraceNodeIndex> nodes = traceNodeRepository.findByTraceId(traceId, PageRequest.of(0, 50));
            if (nodes == null || nodes.isEmpty()) {
                return result;
            }

            nodes.sort((left, right) -> compareDateAsc(left.getCreateTime(), right.getCreateTime()));
            TraceNodeIndex first = nodes.get(0);
            HttpTraceNode httpNode = first.getHttpNode();
            result.put("traceId", traceId);
            result.put("appId", first.getAppId());
            result.put("createTime", first.getCreateTime());
            result.put("time", first.getCreateTime());
            result.put("startTime", first.getCreateTime());
            result.put("type", first.getType());
            result.put("status", first.getStatus());
            result.put("useTime", first.getUseTime());
            result.put("duration", first.getUseTime());
            result.put("hasError", first.getHasError());
            result.put("error", Boolean.TRUE.equals(first.getHasError()));
            result.put("nodes", new ArrayList<>());
            if (httpNode != null) {
                fillHttpTrace(result, traceId, httpNode);
            }
        } catch (Exception e) {
            logger.error("Get trace detail failed: {}", traceId, e);
        }
        return result;
    }

    public List<Map<String, Object>> getSnapshots(String projectId, String userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, userId);
            if (snapshots != null) {
                for (SnapshotVo snapshot : snapshots) {
                    result.add(toSnapshotMap(snapshot));
                }
            }
        } catch (Exception e) {
            logger.error("Get snapshots failed: {}", projectId, e);
        }
        return result;
    }

    public Map<String, Object> getSnapshotDetail(String snapshotId) {
        Map<String, Object> result = new HashMap<>();
        try {
            SnapshotVo snapshot = snapshotService.get(snapshotId);
            if (snapshot != null) {
                result.putAll(toSnapshotMap(snapshot));
                result.put("traceId", snapshot.getTraceId());
            }
        } catch (Exception e) {
            logger.error("Get snapshot detail failed: {}", snapshotId, e);
        }
        return result;
    }

    public int countSnapshots(String projectId) {
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, null);
        return snapshots != null ? snapshots.size() : 0;
    }

    private List<Map<String, Object>> getTraceListForApp(String appId, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            Page<TraceSummaryIndex> page = traceSummaryRepository.findByAppId(appId, PageRequest.of(0, limit));
            for (TraceSummaryIndex summary : page.getContent()) {
                if (summary == null || !StringUtils.hasText(summary.getTraceId())) {
                    continue;
                }
                result.add(toTraceSummaryMap(summary));
            }
        } catch (Exception e) {
            logger.error("Get trace list for app failed: appId={}", appId, e);
        }
        return result;
    }

    private void fillHttpTrace(Map<String, Object> result, String traceId, HttpTraceNode httpNode) {
        result.put("url", httpNode.getRequestUrl());
        result.put("method", httpNode.getRequestMethod());
        result.put("clientIp", httpNode.getClientIp());
        result.put("serverIp", httpNode.getServerIp());
        result.put("appName", httpNode.getApp() != null ? httpNode.getApp().getAppName() : "");
        result.put("statusCode", httpNode.getResponseCode());
        result.put("responseCode", httpNode.getResponseCode());
        result.put("duration", httpNode.getUseTime());
        result.put("useTime", httpNode.getUseTime());
        result.put("hasError", httpNode.getError() != null);
        result.put("error", httpNode.getError() != null);
        if (httpNode.getError() != null) {
            result.put("exception", httpNode.getError().toString());
        }

        List<StackNodeVo> storedNodes = coverageStorage.load(traceId);
        List<StackNodeVo> codeNodesSource = storedNodes.isEmpty()
                ? (httpNode.getCodeNodes() != null ? Arrays.asList(httpNode.getCodeNodes()) : Collections.emptyList())
                : storedNodes;
        List<Map<String, Object>> codeNodesList = new ArrayList<>();
        for (StackNodeVo node : codeNodesSource) {
            if (node != null) {
                codeNodesList.add(toCodeNodeMap(node));
            }
        }
        result.put("nodes", codeNodesList);
    }

    private Map<String, Object> toTraceSummaryMap(TraceSummaryIndex summary) {
        Map<String, Object> trace = new HashMap<>();
        trace.put("traceId", summary.getTraceId());
        trace.put("appId", summary.getAppId());
        trace.put("appName", summary.getAppName() != null ? summary.getAppName() : "");
        trace.put("createTime", summary.getCreateTime());
        trace.put("type", "http");
        trace.put("hasError", Boolean.TRUE.equals(summary.getHasError()));
        trace.put("error", Boolean.TRUE.equals(summary.getHasError()));
        trace.put("useTime", summary.getUseTime());
        trace.put("duration", summary.getUseTime());
        trace.put("url", summary.getHttpUrl());
        trace.put("method", summary.getHttpMethod());
        trace.put("clientIp", summary.getHttpClientIp());
        trace.put("serverIp", summary.getHttpServerIp());
        trace.put("responseCode", summary.getHttpResponseCode());
        trace.put("statusCode", summary.getHttpResponseCode());
        trace.put("status", summary.getStatus());
        trace.put("time", summary.getCreateTime());
        trace.put("nodeCount", summary.getNodeCount());
        trace.put("sqlCount", summary.getSqlCount());
        trace.put("remoteCount", summary.getRemoteCount());
        return trace;
    }

    private Map<String, Object> toCodeNodeMap(StackNodeVo node) {
        Map<String, Object> codeNode = new HashMap<>();
        codeNode.put("id", node.getId());
        codeNode.put("name", node.getClassName() + "." + node.getMethodName());
        codeNode.put("className", node.getClassName());
        codeNode.put("methodName", node.getMethodName());
        codeNode.put("type", "method");
        codeNode.put("duration", node.getUseTime());
        codeNode.put("error", false);
        codeNode.put("done", node.isDone());
        codeNode.put("complexity", node.getExecCyclo());
        return codeNode;
    }

    private Map<String, Object> toSnapshotMap(SnapshotVo snapshot) {
        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("id", snapshot.getId());
        snapshotMap.put("name", snapshot.getName());
        snapshotMap.put("creator", snapshot.getCreateUser());
        snapshotMap.put("createTime", snapshot.getCreateTime());
        snapshotMap.put("shared", snapshot.getShare());
        return snapshotMap;
    }

    private int compareCreateTime(Object timeA, Object timeB) {
        if (timeA == null && timeB == null) return 0;
        if (timeA == null) return 1;
        if (timeB == null) return -1;
        if (timeA instanceof Date && timeB instanceof Date) {
            return ((Date) timeB).compareTo((Date) timeA);
        }
        return timeB.toString().compareTo(timeA.toString());
    }

    private int compareDateAsc(Date leftTime, Date rightTime) {
        if (leftTime == null && rightTime == null) return 0;
        if (leftTime == null) return 1;
        if (rightTime == null) return -1;
        return leftTime.compareTo(rightTime);
    }
}
