package com.oAT.web.api.snapshot;

import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.api.monitor.CoverageMonitorEventService;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.SystemSnapshotService;
import com.oAT.web.service.entity.SnapshotVo;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SnapshotSaveAsSystemService {

    private final SnapshotService snapshotService;
    private final SystemSnapshotService systemSnapshotService;
    private final ClientSessionService clientSessionService;
    private final CoverageMonitorEventService coverageMonitorEventService;

    public SnapshotSaveAsSystemService(SnapshotService snapshotService,
                                       SystemSnapshotService systemSnapshotService,
                                       ClientSessionService clientSessionService,
                                       CoverageMonitorEventService coverageMonitorEventService) {
        this.snapshotService = snapshotService;
        this.systemSnapshotService = systemSnapshotService;
        this.clientSessionService = clientSessionService;
        this.coverageMonitorEventService = coverageMonitorEventService;
    }

    public SystemSnapshot saveAsSystemSnapshot(String projectId,
                                               String userId,
                                               String snapshotId,
                                               HttpSession session,
                                               SaveAsSystemSnapshotRequest request) {
        SnapshotVo mySnapshot = snapshotService.get(snapshotId);
        SystemSnapshot sourceSystemSnapshot = null;
        if (mySnapshot == null) {
            sourceSystemSnapshot = systemSnapshotService.getById(snapshotId);
            Assert.notNull(sourceSystemSnapshot, "找不到快照 id=" + snapshotId);
            Assert.isTrue(sourceSystemSnapshot.getProjectId() == null || projectId.equals(sourceSystemSnapshot.getProjectId()), "快照不属于当前项目");
        } else {
            Assert.isTrue(mySnapshot.getProjectId() == null || projectId.equals(mySnapshot.getProjectId()), "快照不属于当前项目");
        }

        String traceId = mySnapshot != null ? mySnapshot.getTraceId() : sourceSystemSnapshot.getTraceId();
        Assert.hasText(traceId, "快照缺少 traceId");

        Map<String, TraceNode> nodes = coverageMonitorEventService.isCoverageTraceId(traceId)
                ? coverageMonitorEventService.buildTraceNodes(projectId, traceId)
                : getTraceNodesForSave(traceId, session);
        if (nodes.isEmpty()) {
            java.util.Collection<TraceNode> persistedNodes = snapshotService.getTraceNodes(traceId);
            if (persistedNodes != null) {
                for (TraceNode node : persistedNodes) {
                    if (node != null && StringUtils.hasText(node.getTraceNodeId())) {
                        nodes.put(node.getTraceNodeId(), node);
                    }
                }
            }
        }
        Assert.isTrue(!nodes.isEmpty(), "找不到对应链路数据，请确认快照数据完整");

        TraceNode rootNode = resolveRootTraceNode(nodes);
        Assert.notNull(rootNode, "找不到主调用节点");

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setTraceId(traceId);
        systemSnapshot.setAppId(resolveAppId(request, mySnapshot, sourceSystemSnapshot));
        systemSnapshot.setDirectory(resolveDirectory(request, sourceSystemSnapshot));
        systemSnapshot.setTitle(resolveTitle(request, mySnapshot, sourceSystemSnapshot));
        systemSnapshot.setDescribe(resolveDescribe(request, mySnapshot, sourceSystemSnapshot));
        systemSnapshot.setTopicImage(resolveTopicImage(request, sourceSystemSnapshot));
        systemSnapshot.setVersionCycle(resolveVersionCycle(request, sourceSystemSnapshot));
        systemSnapshot.setLabels(resolveLabels(request, mySnapshot, sourceSystemSnapshot));
        systemSnapshot.setPrincipals(resolvePrincipals(request, sourceSystemSnapshot));
        systemSnapshot.setSubTitle(sourceSystemSnapshot == null ? null : sourceSystemSnapshot.getSubTitle());

        if (rootNode instanceof HttpTraceNode && !StringUtils.hasText(systemSnapshot.getSubTitle())) {
            systemSnapshot.setSubTitle(((HttpTraceNode) rootNode).getRequestUrl());
        }

        return systemSnapshotService.create(projectId, userId, systemSnapshot, nodes.values());
    }

    public Map<String, TraceNode> getTraceNodesForSave(String traceId, HttpSession session) {
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

    private TraceNode resolveRootTraceNode(Map<String, TraceNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        TraceNode rootNode = nodes.get("0");
        if (rootNode != null) {
            return rootNode;
        }
        return nodes.values().stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String resolveAppId(SaveAsSystemSnapshotRequest request, SnapshotVo sourceSnapshot, SystemSnapshot sourceSystemSnapshot) {
        if (request != null && StringUtils.hasText(request.getAppId())) {
            return request.getAppId();
        }
        if (sourceSnapshot != null && StringUtils.hasText(sourceSnapshot.getAppId())) {
            return sourceSnapshot.getAppId();
        }
        return sourceSystemSnapshot == null ? null : sourceSystemSnapshot.getAppId();
    }

    private String resolveDirectory(SaveAsSystemSnapshotRequest request, SystemSnapshot sourceSystemSnapshot) {
        if (request != null && StringUtils.hasText(request.getDirectory())) {
            return request.getDirectory();
        }
        if (sourceSystemSnapshot != null && StringUtils.hasText(sourceSystemSnapshot.getDirectory())) {
            return sourceSystemSnapshot.getDirectory();
        }
        return "root";
    }

    private String resolveTitle(SaveAsSystemSnapshotRequest request, SnapshotVo sourceSnapshot, SystemSnapshot sourceSystemSnapshot) {
        if (request != null && StringUtils.hasText(request.getTitle())) {
            return request.getTitle();
        }
        if (sourceSnapshot != null && StringUtils.hasText(sourceSnapshot.getName())) {
            return sourceSnapshot.getName();
        }
        return sourceSystemSnapshot == null ? null : sourceSystemSnapshot.getTitle();
    }

    private String resolveDescribe(SaveAsSystemSnapshotRequest request, SnapshotVo sourceSnapshot, SystemSnapshot sourceSystemSnapshot) {
        if (request != null && StringUtils.hasText(request.getDescribe())) {
            return request.getDescribe();
        }
        if (sourceSnapshot != null && StringUtils.hasText(sourceSnapshot.getDescribe())) {
            return sourceSnapshot.getDescribe();
        }
        return sourceSystemSnapshot == null ? null : sourceSystemSnapshot.getDescribe();
    }

    private String resolveTopicImage(SaveAsSystemSnapshotRequest request, SystemSnapshot sourceSystemSnapshot) {
        if (request != null && StringUtils.hasText(request.getTopicImage())) {
            return request.getTopicImage();
        }
        return sourceSystemSnapshot == null ? null : sourceSystemSnapshot.getTopicImage();
    }

    private Integer resolveVersionCycle(SaveAsSystemSnapshotRequest request, SystemSnapshot sourceSystemSnapshot) {
        if (request != null && request.getVersionCycle() != null) {
            return request.getVersionCycle();
        }
        if (sourceSystemSnapshot != null && sourceSystemSnapshot.getVersionCycle() != null) {
            return sourceSystemSnapshot.getVersionCycle();
        }
        return 30;
    }

    private String[] resolveLabels(SaveAsSystemSnapshotRequest request, SnapshotVo sourceSnapshot, SystemSnapshot sourceSystemSnapshot) {
        if (request != null && request.getLabels() != null) {
            return normalizeArray(request.getLabels());
        }
        if (sourceSnapshot != null) {
            return sourceSnapshot.getLabels();
        }
        return sourceSystemSnapshot == null ? null : sourceSystemSnapshot.getLabels();
    }

    private String[] resolvePrincipals(SaveAsSystemSnapshotRequest request, SystemSnapshot sourceSystemSnapshot) {
        if (request != null && request.getPrincipals() != null) {
            return normalizeArray(request.getPrincipals());
        }
        return sourceSystemSnapshot == null ? null : sourceSystemSnapshot.getPrincipals();
    }

    private String[] normalizeArray(List<String> values) {
        if (values == null) {
            return null;
        }
        List<String> normalized = values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return normalized.isEmpty() ? null : normalized.toArray(new String[0]);
    }
}
