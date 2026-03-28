package com.oAT.web.domain;

import com.oAT.web.esDao.entity.SystemSnapshot;
import java.util.List;

public class SnapshotAppLayer implements ImageLayer{

    List<SystemSnapshot> snapshots;

    public SnapshotAppLayer(List<SystemSnapshot> snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public List<ImageElement> elements() {
        return null;
    }

}
