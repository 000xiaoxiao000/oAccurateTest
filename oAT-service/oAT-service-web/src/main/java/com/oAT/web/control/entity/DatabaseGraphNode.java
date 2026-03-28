package com.oAT.web.control.entity;

import com.oAT.agent.model.CKSqlTraceNode;
import com.oAT.agent.model.Error;
import com.oAT.agent.model.SqlTraceNode;
import com.oAT.web.common.SqlParseInfo;
import com.oAT.web.common.SqlStatParse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DatabaseGraphNode extends GraphNode{

    // 数据库信息
    private SqlTraceNode.Database database;
    private String jdbcUrl;
    private List<SqlTraceNode> sqlNodes = new ArrayList<>();
    private List<SqlTraceGroup> sqlGroups = new ArrayList<>();

    private CKSqlTraceNode.Database ckdatabase;
    private List<CKSqlTraceNode> cksqlNodes = new ArrayList<>();

    // 异常堆栈
    private List<Error> errors = new ArrayList<>();
    // sql语句解析
    private List<SqlParseInfo> adds = new ArrayList<>();
    private List<SqlParseInfo> deletes = new ArrayList<>();
    private List<SqlParseInfo> selects = new ArrayList<>();
    private List<SqlParseInfo> updates = new ArrayList<>();


    public DatabaseGraphNode(SqlTraceNode.Database database, String jdbcUrl) {
        this.database = database;
        this.jdbcUrl = jdbcUrl;
    }

    public DatabaseGraphNode(CKSqlTraceNode.Database database, String jdbcUrl) {
        this.ckdatabase = database;
        this.jdbcUrl = jdbcUrl;
    }

    public SqlTraceNode.Database getDatabase() {
        return database;
    }

    public void setDatabase(SqlTraceNode.Database database) {
        this.database = database;
    }

    public List<SqlTraceNode> getSqlNodes() {
        return sqlNodes;
    }

    public void setSqlNodes(List<SqlTraceNode> sqlNodes) {
        this.sqlNodes = sqlNodes;
    }

    public CKSqlTraceNode.Database getCkdatabase() {
        return ckdatabase;
    }

    public void setCkdatabase(CKSqlTraceNode.Database ckdatabase) {
        this.ckdatabase = ckdatabase;
    }

    public List<CKSqlTraceNode> getCksqlNodes() {
        return cksqlNodes;
    }

    public void setCksqlNodes(List<CKSqlTraceNode> cksqlNodes) {
        this.cksqlNodes = cksqlNodes;
    }

    public List<Error> getErrors() {
        return errors;
    }

    public void setErrors(List<Error> errors) {
        this.errors = errors;
    }

    public List<SqlParseInfo> getAdds() {
        return adds;
    }

    public void setAdds(List<SqlParseInfo> adds) {
        this.adds = adds;
    }

    public List<SqlParseInfo> getDeletes() {
        return deletes;
    }

    public void setDeletes(List<SqlParseInfo> deletes) {
        this.deletes = deletes;
    }

    public List<SqlParseInfo> getSelects() {
        return selects;
    }

    public void setSelects(List<SqlParseInfo> selects) {
        this.selects = selects;
    }

    public List<SqlParseInfo> getUpdates() {
        return updates;
    }

    public void setUpdates(List<SqlParseInfo> updates) {
        this.updates = updates;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void add(SqlTraceNode sqlNode) {
        sqlNodes.add(sqlNode);
        SqlStatParse sqlParse = new SqlStatParse();
        sqlParse.addSql(sqlNode.getSql(),
                sqlNode.getDatabase().getType());
        adds.addAll(sqlParse.getAdds());
        deletes.addAll(sqlParse.getDeletes());
        updates.addAll(sqlParse.getUpdates());
        selects.addAll(sqlParse.getSelects());

        //  基于SQL统计分组
        sqlGroups = this.groupBySql();
    }

    private List<SqlTraceGroup> groupBySql() {
        Function<SqlTraceNode, SqlTraceGroup> groupFunction = node ->
                new SqlTraceGroup(node.getDatabase().getAddressIp(),
                        node.getDatabase().getName(),
                        node.getDatabase().getPort(),
                        node.getDatabase().getType(),
                        node.getSql());

        List<SqlTraceGroup> list =
                sqlNodes.stream().collect(
                        Collectors.collectingAndThen(
                                Collectors.groupingBy(groupFunction),
                                t -> convert(t)   // 单条指令
                        ));
        return list;
    }

    public List<SqlTraceGroup> convert(Map<SqlTraceGroup, List<SqlTraceNode>> map) {
        return map.keySet().stream().peek(t -> {
            map.get(t).stream().forEach(n -> {
                t.addParam(n.getParams());
            });
        }).collect(Collectors.toList());
    }


    public void add(CKSqlTraceNode cksqlNode) {
        cksqlNodes.add(cksqlNode);
        SqlStatParse sqlParse = new SqlStatParse();
        sqlParse.addSql(cksqlNode.getSql(),
                cksqlNode.getDatabase().getType());
        adds.addAll(sqlParse.getAdds());
        deletes.addAll(sqlParse.getDeletes());
        updates.addAll(sqlParse.getUpdates());
        selects.addAll(sqlParse.getSelects());

        //  基于SQL统计分组
        sqlGroups = groupBySql();
    }

    private List<SqlTraceGroup> groupByCKSql() {
        Function<CKSqlTraceNode, SqlTraceGroup> groupFunction = node ->
                new SqlTraceGroup(node.getDatabase().getAddressIp(),
                        node.getDatabase().getName(),
                        node.getDatabase().getPort(),
                        node.getDatabase().getType(),
                        node.getSql());

        List<SqlTraceGroup> list =
                cksqlNodes.stream().collect(
                        Collectors.collectingAndThen(
                                Collectors.groupingBy(groupFunction),
                                t -> convertCK(t)   // 单条指令
                        ));
        return list;
    }

    public List<SqlTraceGroup> convertCK(Map<SqlTraceGroup, List<CKSqlTraceNode>> map) {
        return map.keySet().stream().peek(t -> {
            map.get(t).stream().forEach(n -> {
                t.addParam(n.getParams());
            });
        }).collect(Collectors.toList());
    }

}
