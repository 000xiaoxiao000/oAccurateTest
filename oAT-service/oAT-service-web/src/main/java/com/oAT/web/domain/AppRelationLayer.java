package com.oAT.web.domain;

import com.oAT.web.esDao.entity.Remote;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.entity.AppVo;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class AppRelationLayer implements ImageLayer {

    private List<AppVo> apps;
    private List<SystemSnapshot> snapshots;

    public AppRelationLayer(List<AppVo> apps, List<SystemSnapshot> snapshots) {
        this.apps = apps;
        this.snapshots = snapshots;
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
                .forEach(snapshot -> Arrays.stream(snapshot.getRemotes() == null ? new Remote[0] : snapshot.getRemotes())
                        .filter(remote -> StringUtils.hasText(remote.getAppId()))
                        .filter(remote -> appIds.contains(remote.getAppId()))
                        .filter(remote -> !snapshot.getAppId().equals(remote.getAppId()))
                        .forEach(remote -> addRelation(relations, snapshot.getAppId(), remote)));
        return relations.values().stream()
                .map(this::buildEdge)
                .collect(Collectors.toList());
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
                return "调用 x" + count;
            }
            return types.entrySet().stream()
                    .map(entry -> entry.getKey() + " x" + entry.getValue())
                    .collect(Collectors.joining(","));
        }
    }
}
