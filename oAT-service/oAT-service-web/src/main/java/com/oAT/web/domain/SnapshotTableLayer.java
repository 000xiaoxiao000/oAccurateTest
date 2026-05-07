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
        List<ImageElement> elements = snapshots.stream().flatMap(snapshot ->
                Stream.concat(buildNode(snapshot).stream(), buildEdges(snapshot).stream())
        ).collect(Collectors.toList());
        return mergeDuplicateTableNodeElements(elements);
    }

    private List<ImageElement> mergeDuplicateTableNodeElements(List<ImageElement> elements) {
        List<ImageElement> results = new ArrayList<>();
        elements.stream()
                .filter(element -> element.group.equals("nodes") && element.data instanceof TableImageData)
                .map(element -> (TableImageData) element.data)
                .collect(Collectors.groupingBy(data -> data.id, Collectors.toList()))
                .values()
                .stream()
                .map(this::mergeTableNodes)
                .map(data -> {
                    ImageElement element = new ImageElement(data);
                    element.classes = new String[]{"table"};
                    element.group = "nodes";
                    return element;
                })
                .forEach(results::add);
        elements.stream()
                .filter(element -> !(element.group.equals("nodes") && element.data instanceof TableImageData))
                .forEach(results::add);
        return results;
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
            data.sqlContents = new String[]{formatSqlContent(snapshot.getTitle(), a.getType(), sql.getContent())};
            return data;
        })).collect(Collectors.groupingBy(d -> d.id, Collectors.toList()))
                .values()
                .stream()
                .map(this::mergeTableNodes)
                .map(d -> {
                    ImageElement element = new ImageElement(d);
                    element.classes = new String[]{"table"};
                    element.group = "nodes";
                    return element;
                }).collect(Collectors.toList());
    }

    private TableImageData mergeTableNodes(List<TableImageData> tableNodes) {
        TableImageData first = tableNodes.get(0);
        first.sqlContents = tableNodes.stream()
                .flatMap(a -> Arrays.stream(a.sqlContents))
                .distinct()
                .toArray(String[]::new);
        return first;
    }

    private String formatSqlContent(String snapshotTitle, String actionType, String sqlContent) {
        String action = actionType == null ? "SQL" : actionType.toUpperCase();
        String title = snapshotTitle == null || snapshotTitle.trim().isEmpty() ? "未知快照" : snapshotTitle.trim();
        return "[" + title + "] " + action + "：" + (sqlContent == null || sqlContent.trim().isEmpty() ? "未采集到 SQL 原文" : sqlContent.trim());
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
