package com.oAT.web.domain;

import com.oAT.web.common.ClassUtil;
import com.oAT.web.esDao.entity.SystemSnapshot;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SnapshotRemoteLayer implements ImageLayer {
    List<SystemSnapshot> snapshots;

    public SnapshotRemoteLayer(List<SystemSnapshot> snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public List<ImageElement> elements() {
        List<ImageElement> root = new SnapshotLayer(snapshots).elements();
        List<ImageElement> elements = snapshots.stream()
                .flatMap(this::buildDubboNode)// 其它远程服务
                .collect(Collectors.toList());
        root.addAll(elements);
        return root;
    }

    private Stream<ImageElement> buildDubboNode(SystemSnapshot snapshot) {
        return Arrays.stream(snapshot.getRemotes())
                .filter(a -> a.getType().equals("dubbo"))
                .flatMap(a -> {
                    // 构建节点
                    String server = a.getInvokerInterface().split("#")[0];
                    String method = a.getInvokerInterface().split("#")[1];
                    DubboMethodImageData nodeData = new DubboMethodImageData(a.getInvokerInterface());
                    nodeData.interfaceName = server;
                    nodeData.methodName = method;
                    nodeData.name = ClassUtil.getClassSimpleName(server) + "#" + method;
                    nodeData.weight = 10;
                    ImageElement nodeElement = buildDefaultNode(nodeData);
                    nodeElement.classes = new String[]{"dubbo", "remote", "method"};

                    // 构建关联
                    ImageData edgeNode = new ImageData(generateTempId());
                    edgeNode.source = nodeData.id;
                    edgeNode.target = snapshot.getId();
                    edgeNode.name = "dubbo";
                    ImageElement edgeElement = buildDefaultEdge(edgeNode);
                    edgeElement.classes = new String[]{"dubbo", "remote"};
                    return Stream.of(nodeElement, edgeElement);
                });
    }

    //
    public static class DubboMethodImageData extends ImageData {
        public String interfaceName;
        public String methodName;

        public DubboMethodImageData(String id) {
            super(id);
        }
    }

}
