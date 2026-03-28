package com.oAT.web.domain;

import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.entity.DatabaseTable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TableSnapshotLayer implements ImageLayer {

    List<SystemSnapshot> snapshots;
    DatabaseTable table;

    public TableSnapshotLayer(List<SystemSnapshot> snapshots, DatabaseTable table) {
        this.snapshots = snapshots;
        this.table = table;
    }

    @Override
    public List<ImageElement> elements() {
        List<ImageElement> elements = new SnapshotLayer(snapshots).elements();
        List<ImageElement> tableElements = new SnapshotTableLayer(snapshots).elements().stream().filter(this::check).collect(Collectors.toList());
        elements.addAll(tableElements);
        return elements;
    }

    private boolean check(ImageElement element) {
        if (isNodeElement(element)) {
            TableImageData data = (TableImageData) element.data;
            return Objects.equals(data.database, table.getDatabase()) && Objects.equals(data.name, table.table);
        } else {
            return element.data.target.equals(table.toName());
        }
    }

}
