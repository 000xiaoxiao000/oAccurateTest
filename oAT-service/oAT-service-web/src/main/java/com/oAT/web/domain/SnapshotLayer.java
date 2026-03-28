package com.oAT.web.domain;

import com.oAT.web.esDao.entity.SystemSnapshot;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SnapshotLayer implements ImageLayer {

    List<SystemSnapshot> snapshots;

    public SnapshotLayer(List<SystemSnapshot> snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public List<ImageElement> elements() {
        return snapshots.stream().map(this::buildNode).collect(Collectors.toList());
    }

    public ImageElement buildNode(SystemSnapshot snapshot) {
        SnapshotImageData data = new SnapshotImageData(snapshot.getId());
        data.name = snapshot.getTitle();
        // 普通权重
        data.weight = 40;
        if (snapshot.getLabels() != null) {
            if (Arrays.stream(snapshot.getLabels()).anyMatch("核心"::equalsIgnoreCase)) {
                // 最高权重
                data.weight = 60;
            }
        }
        data.image = snapshot.getTopicImage();
        data.describe = snapshot.getDescribe();
        data.appId = snapshot.getAppId();

        List<String> codes = snapshot.getCodeToClass().distinct().collect(Collectors.toList());
        if (snapshot.getSqls() != null) {
            List<String> sqls =
                    Arrays.stream(snapshot.getSqls()).flatMap(sql -> Arrays.stream(sql.getActions()).map(a -> sql.getDatabase() + "." + a.getTable())).distinct().collect(Collectors.toList());
            codes.addAll(sqls);
        }
        // 依赖项
        data.references = codes.toArray(new String[codes.size()]);
        return buildNodeElement(data);
    }

    public ImageElement buildNodeElement(ImageData data) {
        ImageElement element = new ImageElement(data);
        element.classes = new String[]{"snapshot"};
        element.group = "nodes";
        return element;
    }


    public static class SnapshotImageData extends ImageData {
        public String[] references;
        public String image;
        public String appId;

        public SnapshotImageData(String id) {
            super(id);
        }
    }

}
