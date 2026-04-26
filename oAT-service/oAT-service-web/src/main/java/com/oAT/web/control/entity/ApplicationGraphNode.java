package com.oAT.web.control.entity;

import com.oAT.agent.model.*;


import com.oAT.agent.model.Error;


import com.oAT.web.common.SqlParseInfo;


import com.oAT.web.common.SqlStatParse;


import java.util.ArrayList;


import java.util.List;


import java.util.Map;


import java.util.function.Function;


import java.util.stream.Collectors;

public class ApplicationGraphNode extends GraphNode {

    // 当前应用信息
    private Application application;

    // 当前应用会话
    private String sessionId;

    // 应用信息，数据库
    private final List<SqlTraceNode> sqlNodes = new ArrayList<>();

    private final List<CKSqlTraceNode> cksqlNodes = new ArrayList<>();

    // sql语句解析
    private List<SqlParseInfo> adds = new ArrayList<>();

    private List<SqlParseInfo> deletes = new ArrayList<>();

    private List<SqlParseInfo> selects = new ArrayList<>();

    private List<SqlParseInfo> updates = new ArrayList<>();

    private List<SqlTraceGroup> sqlGroups;

    // 远程调用
    private final List<DubboTraceNode> dubboNodes = new ArrayList<>();

    private final List<HttpClientTraceNode> httpClientNodes = new ArrayList<>();

    private final List<FeignTraceNode> feignNodes = new ArrayList<>();

    private final List<SofaRpcTraceNode> sofaRpcNodes = new ArrayList<>();

    private final List<RabbitMQTraceNode> rabbitMQNodes = new ArrayList<>();

    private final List<RocketMQProducerTraceNode> rocketMQProducerNodes = new ArrayList<>();

    private final List<KafkaMQTraceNode> kafkaMQNodes = new ArrayList<>();

    // redis
    private final List<RedisTraceNode> redisNodes = new ArrayList<>();

    // 异常堆栈
    private final List<Error> errors = new ArrayList<>();

    // 系统日志
    private String log;

    public ApplicationGraphNode(Application application, String sessionId) {
        this.application = application;
        this.sessionId = sessionId;
    }


    public List<RedisTraceNode> getRedisNodes() {
        return redisNodes;
    }


    public List<SqlTraceNode> getSqlNodes() {
        return sqlNodes;
    }


    public List<CKSqlTraceNode> getCKSqlNodes() {
        return cksqlNodes;
    }


    public List<DubboTraceNode> getDubboNodes() {
        return dubboNodes;
    }


    public List<HttpClientTraceNode> getHttpClientNodes() {
        return httpClientNodes;
    }

    public List<FeignTraceNode> getFeignNodes() {
        return feignNodes;
    }

    public List<SofaRpcTraceNode> getSofaRpcNodes() {
        return sofaRpcNodes;
    }

    public List<RabbitMQTraceNode> getRabbitMQNodes() {
        return rabbitMQNodes;
    }

    public List<RocketMQProducerTraceNode> getRocketMQProducerNodes() {
        return rocketMQProducerNodes;
    }

    public List<KafkaMQTraceNode> getKafkaMQNodes() {
        return kafkaMQNodes;
    }


    public List<Error> getErrors() {
        return errors;
    }


    public List<SqlParseInfo> getAdds() {
        return adds;
    }

    public List<SqlParseInfo> getDeletes() {
        return deletes;
    }

    public List<SqlParseInfo> getSelects() {
        return selects;
    }

    public List<SqlParseInfo> getUpdates() {
        return updates;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public String getSessionId() {
        return sessionId;
    }


    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }


    public List<SqlTraceGroup> getSqlGroups() {
        return sqlGroups;
    }


    public void setSqlGroups(List<SqlTraceGroup> sqlGroups) {
        this.sqlGroups = sqlGroups;
    }

    public void add(TraceNode node) {
        if (node instanceof SqlTraceNode) {
            addSqlNode((SqlTraceNode) node);
        } else if (node instanceof CKSqlTraceNode) {
            addCKSqlNode((CKSqlTraceNode) node);
        } else if (node instanceof DubboTraceNode) {
            dubboNodes.add((DubboTraceNode) node);
        } else if (node instanceof HttpClientTraceNode) {
            httpClientNodes.add((HttpClientTraceNode) node);
        } else if (node instanceof FeignTraceNode) {
            feignNodes.add((FeignTraceNode) node);
        } else if (node instanceof SofaRpcTraceNode) {
            sofaRpcNodes.add((SofaRpcTraceNode) node);
        } else if (node instanceof RabbitMQTraceNode) {
            rabbitMQNodes.add((RabbitMQTraceNode) node);
        } else if (node instanceof RocketMQProducerTraceNode) {
            rocketMQProducerNodes.add((RocketMQProducerTraceNode) node);
        } else if (node instanceof KafkaMQTraceNode) {
            kafkaMQNodes.add((KafkaMQTraceNode) node);
        } else if (node instanceof RedisTraceNode) {
            redisNodes.add((RedisTraceNode) node);
        }

        if (node instanceof StatementError) {
            if (((StatementError) node).getError() != null) {
                errors.add(((StatementError) node).getError());
            }
        }
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public void doGroupBySql() {
        // 基于SQL信息分组
        sqlGroups = groupBySql();

        //SQL 统计基于表名去重
        adds = adds.stream().distinct().collect(Collectors.toList());
        deletes = deletes.stream().distinct().collect(Collectors.toList());
        selects = selects.stream().distinct().collect(Collectors.toList());
        updates = updates.stream().distinct().collect(Collectors.toList());
    }

    private List<SqlTraceGroup> groupBySql() {
        Function<SqlTraceNode, SqlTraceGroup> groupFunction =
                node -> new SqlTraceGroup(node.getDatabase().getAddressIp(),
                        node.getDatabase().getPort(), node.getDatabase().getName(), node.getDatabase().getType(),
                        node.getSql());

        return sqlNodes.stream().collect(Collectors.collectingAndThen(Collectors.groupingBy(groupFunction),
                // 单条指令
                this::convert));
    }

    private void addSqlNode(SqlTraceNode sqlNode) {
        sqlNodes.add(sqlNode);
        // 解析统计SQL
        SqlStatParse sqlParse = new SqlStatParse();
        sqlParse.addSql(sqlNode.getSql(), sqlNode.getDatabase().getType());
        adds.addAll(sqlParse.getAdds());
        deletes.addAll(sqlParse.getDeletes());
        updates.addAll(sqlParse.getUpdates());
        selects.addAll(sqlParse.getSelects());
    }

    private void addCKSqlNode(CKSqlTraceNode cksqlNode) {
        cksqlNodes.add(cksqlNode);
        // 解析统计SQL
        SqlStatParse sqlParse = new SqlStatParse();
        sqlParse.addSql(cksqlNode.getSql(), cksqlNode.getDatabase().getType());
        adds.addAll(sqlParse.getAdds());
        deletes.addAll(sqlParse.getDeletes());
        updates.addAll(sqlParse.getUpdates());
        selects.addAll(sqlParse.getSelects());
    }

    public List<SqlTraceGroup> convert(Map<SqlTraceGroup, List<SqlTraceNode>> map) {
        return map.keySet().stream().peek(t -> {
            map.get(t).forEach(n -> {
                t.addParam(n.getParams());
                t.setExecutes(n.getExecutes());
                t.setJdbcUrl(n.getJdbcUrl());
            });
            //执行次数
            t.setCount(map.get(t).size());
        }).collect(Collectors.toList());
    }
}
