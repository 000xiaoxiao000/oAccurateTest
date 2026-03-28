package com.oAT.web.domain;

import com.oAT.web.esDao.entity.SystemSnapshot;

import java.util.List;
import java.util.stream.Collectors;

public class DubboSnapshotLayer implements ImageLayer{

    List<SystemSnapshot> snapshots;
    public String interfaceName;
    public String methodName;

    public DubboSnapshotLayer(List<SystemSnapshot> snapshots, String interfaceName, String methodName) {
        this.snapshots = snapshots;
        this.interfaceName = interfaceName;
        this.methodName = methodName;
    }

    @Override
    public List<ImageElement> elements() {
        final String methodNodeId = interfaceName + "#" + methodName;
        List<ImageElement> snapshotElements = new SnapshotLayer(snapshots).elements();
        List<ImageElement> edgesElements = snapshots.stream().map(a -> {
            ImageData edgeNode = new ImageData(generateTempId());
            edgeNode.source = a.getId();
            edgeNode.target = methodNodeId;
            edgeNode.name = "dubbo";
            ImageElement edgeElement = buildDefaultEdge(edgeNode);
            edgeElement.classes = new String[]{"dubbo", "remote"};
            return buildDefaultEdge(edgeNode);
        }).collect(Collectors.toList());
        // 构建关联
        snapshotElements.addAll(edgesElements);
        return snapshotElements;
    }

}
