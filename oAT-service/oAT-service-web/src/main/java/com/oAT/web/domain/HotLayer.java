package com.oAT.web.domain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
                    long count = snapshotNodes.stream()
                            .filter(s -> Arrays.stream(s.references).anyMatch(r -> r.equals(a.id))).count();
                    a.unionCount = new Long(count).intValue();
                }
        );
        return elements;

    }
}
