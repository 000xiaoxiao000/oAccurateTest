package com.oAT.web.domain;

import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.entity.AppVo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AppSnapshotLayer implements ImageLayer{

    List<SystemSnapshot> snapshots;
    AppVo app;

    public AppSnapshotLayer(List<SystemSnapshot> snapshots, AppVo app) {
        this.snapshots = snapshots;
        this.app = app;
    }

    @Override
    public List<ImageElement> elements() {
        List<ImageElement> result = new ArrayList<>(snapshots.size() + 1);
        List<ImageElement> snapshotElements = new SnapshotLayer(snapshots).elements();
        ImageElement appElement = new AppLayer(Arrays.asList(app)).elements().get(0);
        result.addAll(snapshotElements);
        result.add(appElement);
        snapshotElements.stream().forEach(a -> {
            result.add(buildEdge(a.data, appElement.data));
        });
        // 构建关系
        return result;
    }

    private ImageElement buildEdge(ImageData source, ImageData target) {
        String id = UUID.nameUUIDFromBytes((source.id + target.id)
                        .getBytes())
                .toString()
                .split("-")[0];
        ImageData imageData = new ImageData(id);
        imageData.source = source.id;
        imageData.target = target.id;
        imageData.name = "快照所属应用";
        return buildDefaultEdge(imageData);
    }

}
