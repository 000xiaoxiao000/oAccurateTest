package com.oAT.web.control;

import com.oAT.agent.model.*;
import com.oAT.web.common.SqlStatParse;
import com.oAT.web.control.entity.StackItem;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class StackItemHelp {

    Collection<TraceNode> traceNodes;

    public StackItemHelp(Collection<TraceNode> traceNodes) {
        this.traceNodes = traceNodes;
    }

    public List<StackItem> buildItems() {
        List<StackItem> stackItems = traceNodes.stream()
                .map(a -> buildStackItem(a, traceNodes))
                .filter(a -> a != null)
                .sorted((a, b) -> GraphViewHelp.compareNodeId(a.getNodeId(), b.getNodeId()))// 基于NodeID 排序
                .collect(Collectors.toList());
        return stackItems;
    }

    private StackItem buildStackItem(TraceNode node, Collection<TraceNode> traceNodes) {
        StackItem item = new StackItem();
        item.setNodeId(node.getTraceNodeId());
        item.setTraceId(node.getTraceId());
        item.setStatus(node.getStatus());
        item.setUseTime(node.getEndTime() - node.getBeginTime());
        item.setParentId(GraphViewHelp.getParentId(node.getTraceNodeId()));
        if (node instanceof HttpTraceNode) {
            HttpTraceNode httpNode = (HttpTraceNode) node;
            item.setAppName(node.getApp().getAppName());
            String url = httpNode.getRequestUrl();
            if (url.indexOf("?") > -1) {
                url = url.substring(0, url.indexOf("?"));
            }
            item.setServerName(url);
            item.setType("http");
        } else if (node instanceof DubboTraceNode) {
            DubboTraceNode dubboNode = (DubboTraceNode) node;
            String name = traceNodes.stream()
                    .filter(a -> a.getTraceNodeId().equals(dubboNode.getTraceNodeId() + ".remote"))
                    .findAny()
                    .map(a -> a.getApp().getAppName())
                    .orElseGet(() -> {
                        URI uri = GraphViewHelp.buildURI(node, dubboNode.getRemoteUrl());
                        return uri.getHost() + "@" + uri.getPort();
                    });
            item.setAppName(name);

            // 简化只保留类名
            String serviceInterface = dubboNode.getServiceInterface();
            if (serviceInterface.lastIndexOf(".") > 0) {
                serviceInterface = serviceInterface.substring(serviceInterface.lastIndexOf(".") + 1);
            }
            item.setServerName(serviceInterface + "#" + dubboNode.getServiceMethodName());
            item.setType("dubbo");
        } else if (node instanceof SqlTraceNode) {
            SqlTraceNode sqlNode = (SqlTraceNode) node;
            item.setAppName(sqlNode.getDatabase().getName());
            item.setType("sql");
            // sql 解析
            SqlStatParse parse = new SqlStatParse(sqlNode.getSql(), sqlNode.getDatabase().getType());
            String serverName = parse.getAll().stream()
                    .map(a -> a.getModel() + "->" + a.getTableName())
                    .distinct()
                    .collect(Collectors.joining(","));
            item.setServerName(serverName);
        }else if (node instanceof CKSqlTraceNode) {
            CKSqlTraceNode sqlNode = (CKSqlTraceNode) node;
            item.setAppName(sqlNode.getDatabase().getName());
            item.setType("sql");
            // sql 解析
            SqlStatParse parse = new SqlStatParse(sqlNode.getSql(), sqlNode.getDatabase().getType());
            String serverName = parse.getAll().stream()
                    .map(a -> a.getModel() + "->" + a.getTableName())
                    .distinct()
                    .collect(Collectors.joining(","));
            item.setServerName(serverName);
        }else if (node instanceof RedisTraceNode) {
            RedisTraceNode redisNode = (RedisTraceNode) node;
            item.setAppName("redis");
            item.setType("cmd");
            item.setServerName(redisNode.getType());
        } else {
            item = null;
        }

        return item;

    }

}
