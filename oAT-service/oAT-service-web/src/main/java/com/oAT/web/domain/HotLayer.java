package com.oAT.web.domain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HotLayer implements ImageLayer {

    private final List<ImageElement> elements;

    public HotLayer(List<ImageElement> elements) {
        this.elements = elements;
    }

    @Override
    public List<ImageElement> elements() {
        List<SnapshotLayer.SnapshotImageData> snapshotNodes = elements.stream()
                .filter(a -> a.group.equals("nodes") && a.data instanceof SnapshotLayer.SnapshotImageData)
                .map(a -> (SnapshotLayer.SnapshotImageData) a.data)
                .collect(Collectors.toList());
        elements.stream().filter(a -> a.group.equals("nodes")).map(a -> a.data).forEach(
                a -> {
                    // 基于Id计算引用
                    List<String> candidateIds = buildReferenceCandidates(a);
                    long count = snapshotNodes.stream()
                            .filter(s -> Arrays.stream(s.references).anyMatch(r -> candidateIds.contains(normalizeReference(r))))
                            .count();
                    int unionCount = Math.toIntExact(count);
                    a.unionCount = unionCount;
                    if (unionCount > 0 && a.name != null) {
                        a.hotName = a.name + "（热度" + unionCount + "）";
                    }
                }
        );
        return elements;

    }

    private List<String> buildReferenceCandidates(ImageData data) {
        return Stream.of(data.id, data.name)
                .filter(a -> a != null && !a.trim().isEmpty())
                .map(this::normalizeReference)
                .distinct()
                .collect(Collectors.toList());
    }

    private String normalizeReference(String reference) {
        return reference == null ? "" : reference.trim().replace('\\', '/').replace('/', '.').toLowerCase();
    }
}
