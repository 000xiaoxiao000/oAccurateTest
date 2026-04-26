package com.oAT.web.domain;

import com.oAT.agent.model.TraceNode;
import com.oAT.web.esDao.entity.Remote;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.entity.AppVo;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class AppRelationLayer implements ImageLayer {

    private List<AppVo> apps;
    private List<SystemSnapshot> snapshots;
    private Map<String, Collection<TraceNode>> traceNodesBySnapshotId;
    private Collection<Collection<TraceNode>> liveTraceNodeGroups;
    private RemoteCallResolver remoteCallResolver;

    public AppRelationLayer(List<AppVo> apps, List<SystemSnapshot> snapshots) {
        this(apps, snapshots, Collections.emptyMap());
    }

    public AppRelationLayer(List<AppVo> apps, List<SystemSnapshot> snapshots, Map<String, Collection<TraceNode>> traceNodesBySnapshotId) {
        this(apps, snapshots, traceNodesBySnapshotId, Collections.emptyList());
    }

    public AppRelationLayer(List<AppVo> apps, List<SystemSnapshot> snapshots, Map<String, Collection<TraceNode>> traceNodesBySnapshotId,
                            Collection<Collection<TraceNode>> liveTraceNodeGroups) {
        this(apps, snapshots, traceNodesBySnapshotId, liveTraceNodeGroups, new RemoteCallResolver(apps, Collections.emptyList()));
    }

    public AppRelationLayer(List<AppVo> apps, List<SystemSnapshot> snapshots, Map<String, Collection<TraceNode>> traceNodesBySnapshotId,
                            Collection<Collection<TraceNode>> liveTraceNodeGroups, RemoteCallResolver remoteCallResolver) {
        this.apps = apps;
        this.snapshots = snapshots;
        this.traceNodesBySnapshotId = traceNodesBySnapshotId;
        this.liveTraceNodeGroups = liveTraceNodeGroups;
        this.remoteCallResolver = remoteCallResolver;
    }

    @Override
    public List<ImageElement> elements() {
        List<ImageElement> result = new ArrayList<>();
        result.addAll(new AppLayer(apps).elements());
        result.addAll(buildEdges());
        return result;
    }

    private List<ImageElement> buildEdges() {
        Set<String> appIds = apps.stream()
                .map(AppVo::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, AppRelation> relations = new LinkedHashMap<>();
        snapshots.stream()
                .filter(snapshot -> StringUtils.hasText(snapshot.getAppId()))
                .filter(snapshot -> appIds.contains(snapshot.getAppId()))
                .forEach(snapshot -> {
                    Arrays.stream(snapshot.getRemotes() == null ? new Remote[0] : snapshot.getRemotes())
                            .filter(remote -> StringUtils.hasText(remote.getAppId()))
                            .filter(remote -> appIds.contains(remote.getAppId()))
                            .filter(remote -> !snapshot.getAppId().equals(remote.getAppId()))
                            .forEach(remote -> addRelation(relations, snapshot.getAppId(), remote));
                    addTraceRemoteInvokeRelations(relations, appIds, traceNodesBySnapshotId.getOrDefault(snapshot.getId(), Collections.emptyList()));
                });
        liveTraceNodeGroups.forEach(traceNodes -> addTraceRemoteInvokeRelations(relations, appIds, traceNodes));
        return relations.values().stream()
                .map(this::buildEdge)
                .collect(Collectors.toList());
    }

    private void addTraceRemoteInvokeRelations(Map<String, AppRelation> relations, Set<String> appIds, Collection<TraceNode> traceNodes) {
        addTraceAppRelations(relations, appIds, traceNodes);
        traceNodes.stream()
                .map(traceNode -> remoteCallResolver.resolve(traceNode, traceNodes))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(relation -> appIds.contains(relation.getSourceAppId()))
                .filter(relation -> appIds.contains(relation.getTargetAppId()))
                .forEach(relation -> addRelation(relations, relation.getSourceAppId(), relation.getTargetAppId(), relation.getType()));
    }

    private void addTraceAppRelations(Map<String, AppRelation> relations, Set<String> appIds, Collection<TraceNode> traceNodes) {
        Optional<String> sourceAppId = traceNodes.stream()
                .filter(traceNode -> "0".equals(traceNode.getTraceNodeId()))
                .map(this::getTraceNodeAppId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(appIds::contains)
                .findFirst();
        if (!sourceAppId.isPresent()) {
            sourceAppId = traceNodes.stream()
                    .map(this::getTraceNodeAppId)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(appIds::contains)
                    .findFirst();
        }
        if (!sourceAppId.isPresent()) {
            return;
        }
        String source = sourceAppId.get();
        traceNodes.stream()
                .map(this::getTraceNodeAppId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(appIds::contains)
                .filter(appId -> !source.equals(appId))
                .distinct()
                .forEach(appId -> addRelation(relations, source, appId, "调用"));
    }

    private void addRelation(Map<String, AppRelation> relations, String sourceAppId, Remote remote) {
        String targetAppId = remote.getAppId();
        String key = sourceAppId + "->" + targetAppId;
        AppRelation relation = relations.computeIfAbsent(key, k -> new AppRelation(sourceAppId, targetAppId));
        relation.count++;
        if (StringUtils.hasText(remote.getType())) {
            relation.types.put(remote.getType(), relation.types.getOrDefault(remote.getType(), 0) + 1);
        }
    }

    private void addRelation(Map<String, AppRelation> relations, String sourceAppId, String targetAppId, String type) {
        String key = sourceAppId + "->" + targetAppId;
        AppRelation relation = relations.computeIfAbsent(key, k -> new AppRelation(sourceAppId, targetAppId));
        relation.count++;
        if (StringUtils.hasText(type)) {
            relation.types.put(type, relation.types.getOrDefault(type, 0) + 1);
        }
    }

    private Optional<String> getTraceNodeAppId(TraceNode traceNode) {
        if (traceNode == null || traceNode.getApp() == null || !StringUtils.hasText(traceNode.getApp().getAppId())) {
            return Optional.empty();
        }
        return Optional.of(traceNode.getApp().getAppId());
    }

    private ImageElement buildEdge(AppRelation relation) {
        ImageData imageData = new ImageData(buildEdgeId(relation.sourceAppId, relation.targetAppId));
        imageData.source = relation.sourceAppId;
        imageData.target = relation.targetAppId;
        imageData.name = relation.label();
        imageData.describe = "应用调用次数：" + relation.count;
        ImageElement edge = buildDefaultEdge(imageData);
        edge.classes = new String[]{"app-relation"};
        return edge;
    }

    private String buildEdgeId(String sourceAppId, String targetAppId) {
        return UUID.nameUUIDFromBytes((sourceAppId + "->" + targetAppId).getBytes())
                .toString()
                .split("-")[0];
    }

    private static class AppRelation {
        private String sourceAppId;
        private String targetAppId;
        private int count;
        private Map<String, Integer> types = new LinkedHashMap<>();

        private AppRelation(String sourceAppId, String targetAppId) {
            this.sourceAppId = sourceAppId;
            this.targetAppId = targetAppId;
        }

        private String label() {
            if (types.isEmpty()) {
                return "调用 ×" + count;
            }
            return types.entrySet().stream()
                    .map(entry -> entry.getKey() + " ×" + entry.getValue())
                    .collect(Collectors.joining(","));
        }
    }
}
