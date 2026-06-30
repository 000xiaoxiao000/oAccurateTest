package com.oAT.web.api.snapshot;

import com.oAT.agent.model.CodeNodeBean;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.control.TraceGraphParse;
import com.oAT.web.control.entity.GraphNode;
import com.oAT.web.control.entity.GraphView;
import com.oAT.web.coverage.CoverageStorage;
import com.oAT.web.domain.RemoteCallResolver;
import com.oAT.web.domain.RemoteCallResolverService;
import com.oAT.web.service.SnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class TraceGraphViewService {
    private final SnapshotService snapshotService;
    private final RemoteCallResolverService remoteCallResolverService;
    private final CoverageStorage coverageStorage;

    public TraceGraphViewService(SnapshotService snapshotService,
                                 RemoteCallResolverService remoteCallResolverService,
                                 CoverageStorage coverageStorage) {
        this.snapshotService = snapshotService;
        this.remoteCallResolverService = remoteCallResolverService;
        this.coverageStorage = coverageStorage;
    }

    public GraphView buildGraphView(String projectId, String traceId) {
        GraphView graphView = buildParser(projectId, traceId).getGraphView();
        enrichCodeLayer(graphView, traceId);
        return graphView;
    }

    public GraphView buildGraphView(String projectId, Map<String, TraceNode> nodes) {
        return new TraceGraphParse(nodes, buildRemoteCallResolver(projectId)).getGraphView();
    }

    public GraphNode getGraphNode(String projectId, String traceId, String nodeId) {
        return buildParser(projectId, traceId).getGraphNode(nodeId);
    }

    public GraphNode getGraphNode(String projectId, Map<String, TraceNode> nodes, String nodeId) {
        return new TraceGraphParse(nodes, buildRemoteCallResolver(projectId)).getGraphNode(nodeId);
    }

    public Map<String, TraceNode> buildTraceNodeMap(String traceId) {
        Collection<TraceNode> nodes = snapshotService.getTraceNodes(traceId);
        Map<String, TraceNode> nodeMap = new LinkedHashMap<>();
        if (nodes == null) {
            return nodeMap;
        }
        nodes.stream()
                .filter(Objects::nonNull)
                .filter(node -> StringUtils.hasText(node.getTraceNodeId()))
                .forEach(node -> nodeMap.put(node.getTraceNodeId(), node));
        return nodeMap;
    }

    public void enrichCodeLayer(GraphView graphView, String traceId) {
        graphView.setTraceId(traceId);
        graphView.setHasCodeLayer(false);
        if (!StringUtils.hasText(traceId)) {
            return;
        }
        TraceNode traceNode = snapshotService.getTraceNode(traceId, "0");
        StackNodeVo[] codeNodes = loadCodeNodes(traceId, traceNode);
        graphView.setHasCodeLayer(codeNodes != null && codeNodes.length > 0);
    }

    private StackNodeVo[] loadCodeNodes(String traceId, TraceNode node) {
        if (StringUtils.hasText(traceId)) {
            java.util.List<StackNodeVo> stored = coverageStorage.load(traceId);
            if (stored != null && !stored.isEmpty()) {
                if (node == null || "0".equals(node.getTraceNodeId())) {
                    return stored.toArray(new StackNodeVo[0]);
                }
                return null;
            }
        }
        if (node instanceof CodeNodeBean) {
            return ((CodeNodeBean) node).getCodeNodes();
        }
        return null;
    }

    private TraceGraphParse buildParser(String projectId, String traceId) {
        return new TraceGraphParse(buildTraceNodeMap(traceId), buildRemoteCallResolver(projectId));
    }

    private RemoteCallResolver buildRemoteCallResolver(String projectId) {
        return remoteCallResolverService.build(projectId);
    }
}
