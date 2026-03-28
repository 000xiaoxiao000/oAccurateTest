package com.oAT.web.control;

import com.oAT.agent.model.*;
import com.oAT.web.common.SqlStatParse;
import com.oAT.web.control.entity.GraphView;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

public class GraphViewHelp {

    Collection<TraceNode> traceNodes;

    public GraphViewHelp(Collection<TraceNode> nodes) {
        this.traceNodes = nodes;
    }

    /**
     * 1、统计出所有节点(Http起点、应用节点、远程节点、数据库节点)
     *
     * @return
     */
    public GraphView buildGraphView() {
        GraphView graphView = new GraphView();
        List<GraphView.Nodes> nodes = new ArrayList<>();

        // 构建根节点
        TraceNode rootNode = getTraceNode("0");
        // 根节点当前仅支持Http做为入口协议
        Assert.isTrue(rootNode instanceof HttpTraceNode, "当前仅支持 HttpTraceNode 作为入口节点");
        HttpTraceNode httpRootNode = (HttpTraceNode) rootNode;
        GraphView.Nodes rootGraphNode = new GraphView.Nodes();
        rootGraphNode.setId("root");
        rootGraphNode.setTitle("浏览器");
        rootGraphNode.setSubTitle(httpRootNode.getClientIp() != null ? httpRootNode.getClientIp() : "unknown");
        rootGraphNode.setType("browser");
        rootGraphNode.setState("200".equalsIgnoreCase(httpRootNode.getResponseCode()) ? "ok" : "error");
        rootGraphNode.setTips(httpRootNode.getRequestUrl());
        nodes.add(rootGraphNode);
        graphView.setShowDefaultNode(rootGraphNode);
        graphView.setTitle(httpRootNode.getRequestUrl());

        // 构建 Node 节点
        Collection<GraphView.Nodes> normalNodes = traceNodes.stream()
                .map(this::buildGraphNode)
                .filter(Objects::nonNull)
                //去重
                .collect(Collectors.toMap(GraphView.Nodes::getId, v -> v, this::merge))
                .values();
        nodes.addAll(normalNodes);

        // 构建关系
        Collection<GraphView.Edges> normalEdges = traceNodes.stream()
                .map(this::buildEdge)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(k -> k.getFrom() + k.getTo(), v -> v, (v1, v2) -> v1)).values();
        List<GraphView.Edges> edges = new ArrayList<>(normalEdges);

        graphView.setNodes(nodes);
        graphView.setEdges(edges);

        return graphView;
    }

    // 合并Tips
    private GraphView.Nodes merge(GraphView.Nodes node1, GraphView.Nodes node2) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(node1.getTips())) {
            sb.append(node1.getTips());
        }
        // 去重
        if (StringUtils.hasText(node2.getTips())) {
            sb.append("<br>");
            sb.append(node2.getTips());
        }
        if (sb.toString().startsWith("<br>")) {
            sb.delete(0, 4);
        }
        if (StringUtils.hasText(sb.toString())) {
            // 排序 并去重
            String tips = Arrays.stream(sb.toString().split("<br>")).distinct().sorted().collect(Collectors.joining("<br>"));
            node1.setTips(tips);
        }
        return node1;
    }

    private GraphView.Edges buildEdge(TraceNode traceNode) {
        GraphView.Edges edge = new GraphView.Edges();
        if (traceNode instanceof HttpTraceNode && "0".equals(traceNode.getTraceNodeId())) {
            edge.setFrom("root");
            edge.setTo(buildGraphNode(traceNode).getId());
            edge.setLabel("http请求");
            edge.setType("http request");
            edge.setCount(1);
        } else if (traceNode instanceof DubboTraceNode) {
            // 获取父节点对应的Graph ID
            edge.setFrom(buildGraphNode(getTraceNode(getParentId(traceNode.getTraceNodeId()))).getId());
            edge.setTo(buildGraphNode(traceNode).getId());
            edge.setLabel("dubbo调用");
            edge.setType("dubbo invoker");
            edge.setCount(1);
        } else if (traceNode instanceof SqlTraceNode) {
            // 获取父节点对应的Graph ID
            edge.setFrom(buildGraphNode(getTraceNode(getParentId(traceNode.getTraceNodeId()))).getId());
            edge.setTo(buildGraphNode(traceNode).getId());
            edge.setLabel("sql执行");
            edge.setType("sql invoker");
            edge.setCount(1);
        } else if (traceNode instanceof CKSqlTraceNode) {
            // 获取父节点对应的Graph ID
            edge.setFrom(buildGraphNode(getTraceNode(getParentId(traceNode.getTraceNodeId()))).getId());
            edge.setTo(buildGraphNode(traceNode).getId());
            edge.setLabel("CKsql执行");
            edge.setType("CKsql invoker");
            edge.setCount(1);
        }else if (traceNode instanceof RedisTraceNode) {
            // 获取父节点对应的Graph ID
            edge.setFrom(buildGraphNode(getTraceNode(getParentId(traceNode.getTraceNodeId()))).getId());
            edge.setTo(buildGraphNode(traceNode).getId());
            edge.setLabel("redis执行");
            edge.setType("redis invoker");
            edge.setCount(1);
        }else {
            edge = null;
        }
        return edge;
    }

    private TraceNode getTraceNode(String nodeId) {
        //在集合中查询出第一个TraceNode为nodeId(0)的TraceNode
        return traceNodes.stream().filter(a -> nodeId.equals(a.getTraceNodeId())).findFirst().orElse(null);
    }


    public static String getParentId(String nodeId) {
        // 没有父节点
        if (nodeId.equals("0")) {
            return null;
        }
        Assert.isTrue(nodeId.contains("."), "非法的nodeId nodeId=" + nodeId);
        return nodeId.substring(0, nodeId.lastIndexOf("."));
    }

    public static int compareNodeId(String nodeId1, String nodeId2) {
        Assert.hasText(nodeId1, "参数nodeId1不能为空");
        Assert.hasText(nodeId2, "参数nodeId2不能为空");
        /*if (nodeId1.equals(nodeId2)) {
            return 0;
        }
        String[] ids1 = nodeId1.split("\\.");
        String[] ids2 = nodeId2.split("\\.");
        for (int i = 0; i <Math.min(ids1.length,ids2.length) ; i++) {
            if (Integer.parseInt(ids1[i]) > Integer.parseInt(ids2[i])) {
                return 1;
            }
        }
        return ids1.length - ids2.length;*/
        return nodeId1.compareTo(nodeId2);
    }


    private GraphView.Nodes buildGraphNode(TraceNode traceNode) {
        //显示（流程图）节点信息
        GraphView.Nodes graphViewNode = new GraphView.Nodes();
        String url, id;
        int port;
        URI uri;
        if (traceNode instanceof HttpTraceNode) {
            url = ((HttpTraceNode) traceNode).getRequestUrl();
            uri = buildURI(traceNode, url);
            port = uri.getPort() == -1 ? 80 : uri.getPort();
            id = uri.getHost() + "@" + port;
            graphViewNode.setId(id);
            graphViewNode.setSubTitle(id);
            graphViewNode.setTitle(traceNode.getApp().getAppName());
            graphViewNode.setType("http server"); // Http服务
            graphViewNode.setState(((HttpTraceNode) traceNode).getError() == null ? "normal" : "error");
        } else if (traceNode instanceof DubboTraceNode) {
            DubboTraceNode dubboNode = (DubboTraceNode) traceNode;
            url = dubboNode.getRemoteUrl();
            uri = buildURI(traceNode, url);
            id = uri.getHost() + "@" + uri.getPort();
            graphViewNode.setId(id);
            graphViewNode.setSubTitle(id);
            graphViewNode.setTips(getSimpleClassName(dubboNode.getServiceInterface()) + "#" + dubboNode.getServiceMethodName());
            String name = traceNodes.stream()
                    .filter(a -> a.getTraceNodeId().equals(dubboNode.getTraceNodeId() + ".remote"))
                    .findAny()
                    .map(a -> a.getApp().getAppName())
                    .orElseGet(() -> {
                        URI remoteUri = GraphViewHelp.buildURI(traceNode, dubboNode.getRemoteUrl());
                        return remoteUri.getHost() + "@" + remoteUri.getPort();
                    });

            graphViewNode.setTitle(name);
            graphViewNode.setType("dubbo server");// dubbo 服务
            graphViewNode.setState(dubboNode.getError() == null ? "normal" : "error");
        } else if (traceNode instanceof SqlTraceNode) {
            SqlTraceNode sqlNode = (SqlTraceNode) traceNode;
            url = ((SqlTraceNode) traceNode).getJdbcUrl();
            url = url.replaceFirst("jdbc:", "");
            uri = buildURI(traceNode, url);
            id = uri.getHost() + "@" + uri.getPort() + uri.getPath(); // ip host DataBase name
            graphViewNode.setId(id);
            graphViewNode.setSubTitle(uri.getHost() + "@" + uri.getPort());
            graphViewNode.setTitle(uri.getPath().substring(1));
            graphViewNode.setType("DataBase-MySql");
            graphViewNode.setState(((SqlTraceNode) traceNode).getError() == null ? "normal" : "error");
            SqlStatParse parse = new SqlStatParse(sqlNode.getSql(), sqlNode.getDatabase().getType());
            String tip = parse.getAll().stream().map(a -> a.getModel() + " -> " + a.getTableName()).distinct().collect(Collectors.joining("<br>"));
            graphViewNode.setTips(tip);
        }else if (traceNode instanceof CKSqlTraceNode) {
            CKSqlTraceNode sqlNode = (CKSqlTraceNode) traceNode;
            url = ((CKSqlTraceNode) traceNode).getJdbcUrl();
            url = url.replaceFirst("jdbc:", "");
            uri = buildURI(traceNode, url);
            id = uri.getHost() + "@" + uri.getPort() + uri.getPath(); // ip host DataBase name
            graphViewNode.setId(id);
            graphViewNode.setSubTitle(uri.getHost() + "@" + uri.getPort());
            graphViewNode.setTitle(uri.getPath().substring(1));
            graphViewNode.setType("DataBase-ClickHouse");
            graphViewNode.setState(((CKSqlTraceNode) traceNode).getError() == null ? "normal" : "error");
            SqlStatParse parse = new SqlStatParse(sqlNode.getSql(), sqlNode.getDatabase().getType());
            String tip = parse.getAll().stream().map(a -> a.getModel() + " -> " + a.getTableName()).distinct().collect(Collectors.joining("<br>"));
            graphViewNode.setTips(tip);
        }else if (traceNode instanceof RedisTraceNode) {
            RedisTraceNode redisNode = (RedisTraceNode) traceNode;
            id = redisNode.getHost() + "@" + redisNode.getPort(); // ip host port
            graphViewNode.setId(id);
            graphViewNode.setSubTitle(redisNode.getHost() + "@" + redisNode.getPort());
            graphViewNode.setTitle("Redis");
            graphViewNode.setType("redis");
            graphViewNode.setState(((RedisTraceNode) traceNode).getError() == null ? "normal" : "error");
            graphViewNode.setTips(redisNode.getType());
        } else {
            graphViewNode = null;
        }
        return graphViewNode;
    }

    public static URI buildURI(TraceNode node, String url) {
        Assert.notNull(url, "url 不能为空");
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(String.format("url 无法正常解析 url=%s。traceId=%s。nodeId=%s", url, node.getTraceId(),
                    node.getTraceNodeId()), e);
        }
    }

    public static String getSimpleClassName(String className) {
        if (!StringUtils.hasText(className)) {
            return "UnknownClass";
        }
        if (className.lastIndexOf(".") > 0) {
            return className.substring(className.lastIndexOf(".") + 1);
        }
        return className;
    }
}
