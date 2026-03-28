package com.oAT.web.domain;

import com.oAT.web.esDao.entity.SystemSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SnapshotTableLayer implements ImageLayer {

    List<SystemSnapshot> snapshots;
    private final Integer Weight_init = 10;

    public SnapshotTableLayer(List<SystemSnapshot> snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public List<ImageElement> elements() {
        return snapshots.stream().flatMap(snapshot ->
                Stream.concat(buildNode(snapshot).stream(), buildEdges(snapshot).stream())
        ).collect(Collectors.toList());
    }

    public List<ImageElement> buildNode(SystemSnapshot snapshot) {
        if (snapshot.getSqls() == null) {
            return new ArrayList<>(0);
        }
        return Arrays.stream(snapshot.getSqls()).flatMap(sql -> Arrays.stream(sql.getActions()).map(a -> {
            TableImageData data = new TableImageData(sql.getDatabase() + "." + a.getTable());
            data.dataBaseType = sql.getDatabaseType();
            data.database = sql.getDatabase();
            data.name = a.getTable();
            data.weight = Weight_init;
            return data;
        })).map(d -> {
            ImageElement element = new ImageElement(d);
            element.classes = new String[]{"table"};
            element.group = "nodes";
            return element;
        }).collect(Collectors.toList());
    }

    public List<ImageElement> buildEdges(SystemSnapshot snapshot) {
        if (snapshot.getSqls() == null) {
            return new ArrayList<>(0);
        }
        return Arrays.stream(snapshot.getSqls()).flatMap(sql ->
                Arrays.stream(sql.getActions())
                        .map(a -> new TableEdges(a.getType(),
                                snapshot.getId() + "." + a.getType() + "." + a.getTable(),
                                snapshot.getId(),
                                sql.getDatabase() + "." + a.getTable()
                        ))
        ).map(d -> {
            ImageElement element = new ImageElement(d);
            element.classes = new String[]{"table", d.action};
            element.group = "edges";
            return element;
        }).collect(Collectors.toList());
    }

    public static class TableEdges extends ImageData {
        public String action;
        public TableEdges(String action, String id, String source, String target) {
            super(id);
            super.source = source;
            super.target = target;
            this.action = action;
        }
    }
}
