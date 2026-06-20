package com.oAT.web.control;

import com.oAT.agent.model.*;
import com.oAT.web.control.entity.*;
import com.oAT.web.domain.RemoteCallResolver;

import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TraceGraphParse {
    private Map<String, TraceNode> nodes;
    private Map<String, GraphNode> graphNodesMap;
    //graphView
    private List<GraphView.Nodes> graphViewNodes;
    private List<GraphView.Edges> graphViewEdges;
    private String title;
    private GraphView.Nodes defaultNode;
    private RemoteCallResolver remoteCallResolver;

    /**
     * 调用链图形
     */
    public TraceGraphParse(Map<String, TraceNode> nodes) {
        this(nodes, null);
    }

    public TraceGraphParse(Map<String, TraceNode> nodes, RemoteCallResolver remoteCallResolver) {
        Assert.notNull(nodes, "param 'traceNodes' must be not null");
        this.nodes = nodes;
        this.remoteCallResolver = remoteCallResolver;
        graphNodesMap = new HashMap<>();
        //节点
        graphViewNodes = new ArrayList<>();
        //节点与节点间关系（连接线）
        graphViewEdges = new ArrayList<>();
        //解析
        parse();
    }

    private void parse() {
        // 基于应用ID构建分组
        for (Map.Entry<String, TraceNode> m : nodes.entrySet()) {
            // 构建客户端根节点
            if ("0".equals(m.getValue().getTraceNodeId())) {
                ClientGraphNode clientNode = new ClientGraphNode(m.getValue());
                graphNodesMap.put(clientNode.getId(), clientNode);
                if (m.getValue() instanceof HttpTraceNode) {
                    clientNode.setIp(((HttpTraceNode) m.getValue()).getClientIp());
                } else {
                    clientNode.setIp(m.getValue().getAddressIp());
                }
            }
            // 基于应用构建节点
            if (m.getValue().getApp() != null) {
                ApplicationGraphNode node = buildApplicationGraphNode(m.getValue().getApp(), m.getValue().getSessionId());
                node.add(m.getValue());
                node.setIp(m.getValue().getAddressIp());
                if (m.getValue() instanceof HttpTraceNode) {
                    String newLog = ((HttpTraceNode) m.getValue()).getLog();
                    if (newLog != null && !newLog.isEmpty()) {
                        String existingLog = node.getLog();
                        if (existingLog == null || existingLog.isEmpty()) {
                            node.setLog(newLog);
                        } else if (!existingLog.contains(newLog)) {
                            node.setLog(existingLog + newLog);
                        }
                    }
                }
            }
            // 基于远程应用构建节点
            if (m.getValue() instanceof RemoteInvokeNode) {
                // 获取当前节点所对应的远程节点
                TraceNode remoteNode = getRemoteNode(m.getValue().getTraceNodeId());
                if (remoteNode != null) {
                    ApplicationGraphNode node = buildApplicationGraphNode(remoteNode.getApp(), m.getValue().getSessionId());
                    node.setIp(m.getValue().getAddressIp());
                }
            }
            // 构建数据库节点
            if (m.getValue() instanceof SqlTraceNode) {
                DatabaseGraphNode node = buildDatabaseGraphNode((SqlTraceNode) m.getValue());
                node.add((SqlTraceNode) m.getValue());
                node.setIp(((SqlTraceNode) m.getValue()).getDatabase().getAddressIp());
            }
            if (m.getValue() instanceof CKSqlTraceNode) {
                DatabaseGraphNode node = buildDatabaseGraphNode((CKSqlTraceNode) m.getValue());
                node.add((CKSqlTraceNode) m.getValue());
                node.setIp(((CKSqlTraceNode) m.getValue()).getDatabase().getAddressIp());
            }
            if (m.getValue() instanceof RedisTraceNode) {
                RedisGraphNode node = buildRedisGraphNode((RedisTraceNode) m.getValue());
                node.add((RedisTraceNode) m.getValue());
                node.setIp(((RedisTraceNode) m.getValue()).getHost());
            }
        }
        // 执行分组统计
        graphNodesMap.values().forEach(a -> {
            if (a instanceof ApplicationGraphNode) {
                ((ApplicationGraphNode) a).doGroupBySql();
            }
        });
        // 基于分组构建 graph 节点
        for (Map.Entry<String, GraphNode> m : graphNodesMap.entrySet()) {
            GraphView.Nodes node = new GraphView.Nodes();
            node.setId(m.getValue().getId());
            node.setTitle(m.getValue().getName());
            node.setIcon(m.getValue().getType().getIcon());
            node.setSubTitle(m.getValue().getIp());
            node.setState("normal");
            if (m.getValue() instanceof ClientGraphNode) {
                defaultNode = node;
                title = ((ClientGraphNode) m.getValue()).getTitle();
            }
            if (m.getValue() instanceof DatabaseGraphNode) {
                if (!((DatabaseGraphNode) m.getValue()).getErrors().isEmpty()) {
                    node.setState("error");
                }
            }
            if (m.getValue() instanceof ApplicationGraphNode) {
                if (!((ApplicationGraphNode) m.getValue()).getErrors().isEmpty()) {
                    node.setState("error");
                }
            }
            if (m.getValue() instanceof RedisGraphNode) {
                if (!((RedisGraphNode) m.getValue()).getErrors().isEmpty()) {
                    node.setState("error");
                }
            }
            graphViewNodes.add(node);
        }
        // 基于分组当中事件 构建节点关系
        for (Map.Entry<String, GraphNode> m : graphNodesMap.entrySet()) {
            GraphNode group = m.getValue();
            // 关系起始节点
            String fromId = group.getId();
            if (group instanceof ClientGraphNode) {
                TraceNode traceNode = ((ClientGraphNode) group).getTraceNode();
                if (traceNode != null) {
                    String toId = traceNode.getApp() != null ? traceNode.getApp().getAppId() : null;
                    if (toId != null) {
                        buildEdges(fromId, toId, traceNode);
                    }
                }
            } else if (group instanceof ApplicationGraphNode) {
                for (DubboTraceNode dubboNode : ((ApplicationGraphNode) group).getDubboNodes()) {
                    buildRemoteInvokeEdge(fromId, dubboNode, group.getId());
                }
                for (HttpClientTraceNode httpClientNode : ((ApplicationGraphNode) group).getHttpClientNodes()) {
                    buildRemoteInvokeEdge(fromId, httpClientNode, group.getId());
                }
                for (FeignTraceNode feignNode : ((ApplicationGraphNode) group).getFeignNodes()) {
                    buildRemoteInvokeEdge(fromId, feignNode, group.getId());
                }
                for (SofaRpcTraceNode sofaRpcNode : ((ApplicationGraphNode) group).getSofaRpcNodes()) {
                    buildRemoteInvokeEdge(fromId, sofaRpcNode, group.getId());
                }
                for (RabbitMQTraceNode rabbitMQNode : ((ApplicationGraphNode) group).getRabbitMQNodes()) {
                    buildRemoteInvokeEdge(fromId, rabbitMQNode, group.getId());
                }
                for (RocketMQProducerTraceNode rocketMQNode : ((ApplicationGraphNode) group).getRocketMQProducerNodes()) {
                    buildRemoteInvokeEdge(fromId, rocketMQNode, group.getId());
                }
                for (KafkaMQTraceNode kafkaMQNode : ((ApplicationGraphNode) group).getKafkaMQNodes()) {
                    buildRemoteInvokeEdge(fromId, kafkaMQNode, group.getId());
                }
                for (SqlTraceNode sqlNode : ((ApplicationGraphNode) group).getSqlNodes()) {
                    String toId = generateDatabaseGraphNodeId(sqlNode.getDatabase());
                    buildEdges(fromId, toId, sqlNode);
                }
                for (CKSqlTraceNode cksqlNode : ((ApplicationGraphNode) group).getCKSqlNodes()) {
                    String toId = generateDatabaseGraphNodeId(cksqlNode.getDatabase());
                    buildEdges(fromId, toId, cksqlNode);
                }
                for (RedisTraceNode redisTraceNode : ((ApplicationGraphNode) group).getRedisNodes()) {
                    String toId = generateRedisGraphNodeId(redisTraceNode);
                    buildEdges(fromId, toId, redisTraceNode);
                }
            }
        }
    }

    private TraceNode getRemoteNode(String traceNodeId) {
        return nodes.get(traceNodeId + ".remote");
    }

    private void buildRemoteInvokeEdge(String fromId, TraceNode traceNode, String fallbackSourceId) {
        if (traceNode == null) {
            return;
        }
        TraceNode remoteNode = getRemoteNode(traceNode.getTraceNodeId());
        if (remoteNode != null && remoteNode.getApp() != null) {
            buildEdges(fromId, remoteNode.getApp().getAppId(), traceNode);
            return;
        }
        if (traceNode instanceof RemoteInvokeNode) {
            RemoteInvokeNode remoteInvokeNode = (RemoteInvokeNode) traceNode;
            if (remoteInvokeNode.getRemoteApp() != null) {
                ensureApplicationNode(remoteInvokeNode.getRemoteApp());
                buildEdges(fromId, remoteInvokeNode.getRemoteApp().getAppId(), traceNode);
                return;
            }
        }
        if (remoteCallResolver != null) {
            remoteCallResolver.resolve(traceNode, nodes.values()).ifPresent(relation -> {
                ensureApplicationNode(relation.getTargetAppId());
                buildEdges(fallbackSourceId, relation.getTargetAppId(), traceNode);
            });
        }
    }

    private void buildEdges(String from, String to, TraceNode eventNode) {
        GraphView.Edges existEdges = null;
        // 判断连接关系是否已经存在
        for (GraphView.Edges graphEdge : graphViewEdges) {
            if (graphEdge.getFrom().equals(from) && graphEdge.getTo().equals(to) && graphEdge.getType().equals(eventNode.toType())) {
                existEdges = graphEdge;
                break;
            }
        }
        if (existEdges == null) {
            GraphView.Edges e = new GraphView.Edges();
            e.setFrom(from);
            e.setTo(to);
            // 调用名称
            e.setLabel(eventNode.toType());
            // 调用类别
            e.setType(eventNode.toType());
            e.setCount(1);
            graphViewEdges.add(e);
        } else {
            existEdges.setCount(existEdges.getCount() + 1);
        }
    }

    private void ensureApplicationNode(String appId) {
        if (remoteCallResolver == null || graphNodesMap.containsKey(appId)) {
            return;
        }
        remoteCallResolver.getApp(appId).ifPresent(app -> {
            Application application = new Application(app.getId(), app.getName(), app.getSrcName());
            buildApplicationGraphNode(application, null);
        });
    }

    private void ensureApplicationNode(Application application) {
        if (application != null && application.getAppId() != null && !graphNodesMap.containsKey(application.getAppId())) {
            buildApplicationGraphNode(application, null);
        }
    }

    private ApplicationGraphNode buildApplicationGraphNode(Application application, String sessionId) {
        if (!graphNodesMap.containsKey(application.getAppId())) {
            ApplicationGraphNode graphNode = new ApplicationGraphNode(application, sessionId);
            graphNode.setId(application.getAppId());
            graphNode.setName(application.getAppName());
            graphNode.setType(GraphNode.GraphNodeType.APPLICATION);
            graphNodesMap.put(application.getAppId(), graphNode);
        }
        return (ApplicationGraphNode) graphNodesMap.get(application.getAppId());
    }

    private DatabaseGraphNode buildDatabaseGraphNode(SqlTraceNode sqlTraceNode) {
        SqlTraceNode.Database db = sqlTraceNode.getDatabase();
        String id = generateDatabaseGraphNodeId(db);
        if (!graphNodesMap.containsKey(id)) {
            DatabaseGraphNode graphNode = new DatabaseGraphNode(db, sqlTraceNode.getJdbcUrl());
            graphNode.setId(id);
            graphNode.setName(db.getName());
            graphNode.setType(GraphNode.GraphNodeType.DATABASE);
            graphNodesMap.put(id, graphNode);
        }
        return (DatabaseGraphNode) graphNodesMap.get(id);
    }

    private DatabaseGraphNode buildDatabaseGraphNode(CKSqlTraceNode ckSqlTraceNode) {
        CKSqlTraceNode.Database db = ckSqlTraceNode.getDatabase();
        String id = generateDatabaseGraphNodeId(db);
        if (!graphNodesMap.containsKey(id)) {
            DatabaseGraphNode graphNode = new DatabaseGraphNode(db, ckSqlTraceNode.getJdbcUrl());
            graphNode.setId(id);
            graphNode.setName(db.getName());
            graphNode.setType(GraphNode.GraphNodeType.DATABASE);
            graphNodesMap.put(id, graphNode);
        }
        return (DatabaseGraphNode) graphNodesMap.get(id);
    }

    private RedisGraphNode buildRedisGraphNode(RedisTraceNode redisTraceNode) {
        String id = generateRedisGraphNodeId(redisTraceNode);
        if (!graphNodesMap.containsKey(id)) {
            RedisGraphNode redisGraphNode = new RedisGraphNode(redisTraceNode);
            redisGraphNode.setId(id);
            redisGraphNode.setName(id);
            redisGraphNode.setIp(redisTraceNode.getHost());
            redisGraphNode.setType(GraphNode.GraphNodeType.REDIS);
            graphNodesMap.put(id, redisGraphNode);
        }
        return (RedisGraphNode) graphNodesMap.get(id);
    }

    public GraphNode getGraphNode(String nodeId) {
        return graphNodesMap.get(nodeId);
    }

    private static String generateDatabaseGraphNodeId(SqlTraceNode.Database db) {
        return db.getAddressIp() + "-" + db.getPort() + "-" + db.getName();
    }

    private static String generateDatabaseGraphNodeId(CKSqlTraceNode.Database db) {
        return db.getAddressIp() + "-" + db.getPort() + "-" + db.getName();
    }

    private static String generateRedisGraphNodeId(RedisTraceNode redisTraceNode) {
        return redisTraceNode.getHost() + "@" + redisTraceNode.getPort();
    }

    public GraphView getGraphView() {
        GraphView graphData = new GraphView();
        graphData.setEdges(graphViewEdges);
        graphData.setNodes(graphViewNodes);
        graphData.setShowDefaultNode(defaultNode);
        graphData.setTitle(title);
        return graphData;
    }
}
