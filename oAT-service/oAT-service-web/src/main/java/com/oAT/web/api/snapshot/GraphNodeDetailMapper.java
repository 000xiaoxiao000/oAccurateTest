package com.oAT.web.api.snapshot;

import com.oAT.agent.model.Application;
import com.oAT.agent.model.CKSqlTraceNode;
import com.oAT.agent.model.DubboTraceNode;
import com.oAT.agent.model.FeignTraceNode;
import com.oAT.agent.model.HttpClientTraceNode;
import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.KafkaMQTraceNode;
import com.oAT.agent.model.RabbitMQTraceNode;
import com.oAT.agent.model.RedisTraceNode;
import com.oAT.agent.model.RocketMQProducerTraceNode;
import com.oAT.agent.model.SofaRpcTraceNode;
import com.oAT.agent.model.SqlTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.api.snapshot.GraphNodeDetailPayload.GraphErrorSummary;
import com.oAT.web.api.snapshot.GraphNodeDetailPayload.GraphNodeDetailField;
import com.oAT.web.api.snapshot.GraphNodeDetailPayload.GraphNodeDetailSection;
import com.oAT.web.api.snapshot.GraphNodeDetailPayload.GraphRedisCommandSummary;
import com.oAT.web.api.snapshot.GraphNodeDetailPayload.GraphRemoteCallSummary;
import com.oAT.web.api.snapshot.GraphNodeDetailPayload.GraphSqlSummary;
import com.oAT.web.api.snapshot.GraphNodeDetailPayload.GraphTableOperationSummary;
import com.oAT.web.common.SqlParseInfo;
import com.oAT.web.control.entity.ApplicationGraphNode;
import com.oAT.web.control.entity.ClientGraphNode;
import com.oAT.web.control.entity.DatabaseGraphNode;
import com.oAT.web.control.entity.GraphNode;
import com.oAT.web.control.entity.RedisGraphNode;
import com.oAT.web.control.entity.SqlTraceGroup;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GraphNodeDetailMapper {
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private GraphNodeDetailMapper() {
    }

    public static GraphNodeDetailPayload toGraphNodeDetail(GraphNode graphNode) {
        GraphNodeDetailPayload payload = new GraphNodeDetailPayload();
        payload.setId(graphNode.getId());
        payload.setName(graphNode.getName());
        payload.setType(graphNode.getType() == null ? null : graphNode.getType().name());
        payload.setIp(graphNode.getIp());

        if (graphNode instanceof ClientGraphNode) {
            appendClientNode(payload, (ClientGraphNode) graphNode);
        } else if (graphNode instanceof ApplicationGraphNode) {
            appendApplicationNode(payload, (ApplicationGraphNode) graphNode);
        } else if (graphNode instanceof DatabaseGraphNode) {
            appendDatabaseNode(payload, (DatabaseGraphNode) graphNode);
        } else if (graphNode instanceof RedisGraphNode) {
            appendRedisNode(payload, (RedisGraphNode) graphNode);
        }
        return payload;
    }

    private static void appendClientNode(GraphNodeDetailPayload payload, ClientGraphNode clientNode) {
        payload.setTitle(clientNode.getTitle());
        TraceNode traceNode = clientNode.getTraceNode();
        if (!(traceNode instanceof HttpTraceNode)) {
            return;
        }
        HttpTraceNode node = (HttpTraceNode) traceNode;
        payload.getStats().put("requestUrl", node.getRequestUrl());
        payload.getStats().put("requestMethod", node.getRequestMethod());
        payload.getStats().put("responseCode", node.getResponseCode());
        payload.getStats().put("useTime", node.getUseTime());
        payload.getStats().put("addressIp", node.getAddressIp());
        payload.getStats().put("error", node.getError() != null);

        GraphNodeDetailSection basic = new GraphNodeDetailSection("基本信息");
        addField(basic, "路径", node.getRequestUrl());
        addField(basic, "请求方法", node.getRequestMethod());
        addField(basic, "状态码", node.getResponseCode());
        addField(basic, "客户端IP", node.getClientIp());
        addField(basic, "Cookie", node.getRequestHeader() == null ? null : node.getRequestHeader().getCookie());
        addField(basic, "user-agent", node.getRequestHeader() == null ? null : node.getRequestHeader().getUserAgent());
        addField(basic, "Referer", node.getRequestHeader() == null ? null : node.getRequestHeader().getReferer());
        addField(basic, "Authorization", node.getRequestHeader() == null ? null : node.getRequestHeader().getAuthorization());
        addField(basic, "服务端IP", node.getServerIp());
        addField(basic, "服务端端口", node.getServerPort());
        addField(basic, "日期时间", formatMillis(node.getBeginTime()));
        addField(basic, "总耗时ms", node.getUseTime());
        addField(basic, "响应类型", node.getResponseType());
        addSection(payload, basic);

        GraphNodeDetailSection params = new GraphNodeDetailSection("参数信息");
        String[] names = node.getRequestParamNames();
        String[] values = node.getRequestParamValues();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                addField(params, names[i], values != null && values.length > i ? values[i] : null);
            }
        }
        params.setContent(defaultText(node.getRequestBody(), "无请求体"));
        addSection(payload, params);
        payload.setRequest(buildHttpRequestPayload(node));
        if (node.getError() != null) {
            payload.getErrors().add(toErrorSummary(node.getError()));
        }
    }

    private static void appendApplicationNode(GraphNodeDetailPayload payload, ApplicationGraphNode applicationNode) {
        GraphNode graphNode = applicationNode;
        payload.setTitle(applicationNode.getApplication() == null ? graphNode.getName() : applicationNode.getApplication().getAppName());
        payload.getStats().put("sessionId", applicationNode.getSessionId());
        payload.getStats().put("sqlCount", applicationNode.getSqlNodes().size() + applicationNode.getCKSqlNodes().size());
        payload.getStats().put("redisCount", applicationNode.getRedisNodes().size());
        payload.getStats().put("remoteCount",
                applicationNode.getDubboNodes().size()
                        + applicationNode.getHttpClientNodes().size()
                        + applicationNode.getFeignNodes().size()
                        + applicationNode.getSofaRpcNodes().size()
                        + applicationNode.getRabbitMQNodes().size()
                        + applicationNode.getRocketMQProducerNodes().size()
                        + applicationNode.getKafkaMQNodes().size());
        payload.getStats().put("errorCount", applicationNode.getErrors().size());

        GraphNodeDetailSection basic = new GraphNodeDetailSection("基本信息");
        Application app = applicationNode.getApplication();
        addField(basic, "应用名称", app == null ? graphNode.getName() : app.getAppName());
        addField(basic, "应用ID", app == null ? null : app.getAppId());
        addField(basic, "工程名称", app == null ? null : app.getProjectSrcName());
        addField(basic, "应用IP", graphNode.getIp());
        addField(basic, "跟踪ID", applicationNode.getSessionId());
        addField(basic, "SQL数", applicationNode.getSqlNodes().size() + applicationNode.getCKSqlNodes().size());
        addField(basic, "远程调用数", payload.getStats().get("remoteCount"));
        addField(basic, "Redis执行数", applicationNode.getRedisNodes().size());
        addField(basic, "异常数", applicationNode.getErrors().size());
        addSection(payload, basic);

        appendSqlGroups(payload, applicationNode.getSqlGroups());
        applicationNode.getSqlNodes().forEach(node -> payload.getSqlStatements().add(toSqlSummary(node)));
        applicationNode.getCKSqlNodes().forEach(node -> payload.getSqlStatements().add(toSqlSummary(node)));
        appendTableOperations(payload, "增", applicationNode.getAdds());
        appendTableOperations(payload, "删", applicationNode.getDeletes());
        appendTableOperations(payload, "改", applicationNode.getUpdates());
        appendTableOperations(payload, "查", applicationNode.getSelects());

        applicationNode.getDubboNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
        applicationNode.getHttpClientNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
        applicationNode.getFeignNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
        applicationNode.getSofaRpcNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
        applicationNode.getRabbitMQNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
        applicationNode.getRocketMQProducerNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
        applicationNode.getKafkaMQNodes().forEach(node -> payload.getRemoteCalls().add(toRemoteCallSummary(node)));
        applicationNode.getRedisNodes().forEach(node -> payload.getRedisCommands().add(toRedisCommandSummary(node)));
        applicationNode.getErrors().forEach(error -> payload.getErrors().add(toErrorSummary(error)));
        if (StringUtils.hasText(applicationNode.getLog())) {
            GraphNodeDetailSection log = new GraphNodeDetailSection("系统日志");
            log.setKind("log");
            log.setContent(limitText(applicationNode.getLog(), 10000));
            addSection(payload, log);
        }
    }

    private static void appendDatabaseNode(GraphNodeDetailPayload payload, DatabaseGraphNode databaseNode) {
        GraphNode graphNode = databaseNode;
        payload.setTitle(graphNode.getName());
        payload.getStats().put("jdbcUrl", databaseNode.getJdbcUrl());
        payload.getStats().put("sqlCount", databaseNode.getSqlNodes().size() + databaseNode.getCksqlNodes().size());
        payload.getStats().put("errorCount", databaseNode.getErrors().size());
        payload.getStats().put("selectCount", databaseNode.getSelects().size());
        payload.getStats().put("insertCount", databaseNode.getAdds().size());
        payload.getStats().put("updateCount", databaseNode.getUpdates().size());
        payload.getStats().put("deleteCount", databaseNode.getDeletes().size());

        GraphNodeDetailSection basic = new GraphNodeDetailSection("基本信息");
        addField(basic, "数据库名称", databaseNode.getDatabase() == null ? null : databaseNode.getDatabase().getName());
        addField(basic, "CK数据库名称", databaseNode.getCkdatabase() == null ? null : databaseNode.getCkdatabase().getName());
        addField(basic, "数据库地址", databaseNode.getDatabase() == null ? null : databaseNode.getDatabase().getAddressIp());
        addField(basic, "数据库端口", databaseNode.getDatabase() == null ? null : databaseNode.getDatabase().getPort());
        addField(basic, "数据库类型", databaseNode.getDatabase() == null ? null : databaseNode.getDatabase().getType());
        addField(basic, "JDBC URL", databaseNode.getJdbcUrl());
        addField(basic, "SQL数", databaseNode.getSqlNodes().size() + databaseNode.getCksqlNodes().size());
        addField(basic, "异常数", databaseNode.getErrors().size());
        addSection(payload, basic);

        databaseNode.getSqlNodes().forEach(node -> payload.getSqlStatements().add(toSqlSummary(node)));
        databaseNode.getCksqlNodes().forEach(node -> payload.getSqlStatements().add(toSqlSummary(node)));
        appendTableOperations(payload, "增", databaseNode.getAdds());
        appendTableOperations(payload, "删", databaseNode.getDeletes());
        appendTableOperations(payload, "改", databaseNode.getUpdates());
        appendTableOperations(payload, "查", databaseNode.getSelects());
        databaseNode.getErrors().forEach(error -> payload.getErrors().add(toErrorSummary(error)));
    }

    private static void appendRedisNode(GraphNodeDetailPayload payload, RedisGraphNode redisNode) {
        GraphNode graphNode = redisNode;
        payload.setTitle(graphNode.getName());
        payload.getStats().put("commandCount", redisNode.getRedisNodes().size());
        payload.getStats().put("errorCount", redisNode.getErrors().size());
        if (redisNode.getRedisTraceNode() != null) {
            payload.getStats().put("host", redisNode.getRedisTraceNode().getHost());
            payload.getStats().put("port", redisNode.getRedisTraceNode().getPort());
            payload.setLogPreview(limitText(redisNode.getRedisTraceNode().getCmd(), 800));
        }
        GraphNodeDetailSection basic = new GraphNodeDetailSection("基本信息");
        addField(basic, "Redis名称", graphNode.getName());
        addField(basic, "Host", redisNode.getRedisTraceNode() == null ? null : redisNode.getRedisTraceNode().getHost());
        addField(basic, "Port", redisNode.getRedisTraceNode() == null ? null : redisNode.getRedisTraceNode().getPort());
        addField(basic, "Redis执行数", redisNode.getRedisNodes().size());
        addField(basic, "异常数", redisNode.getErrors().size());
        addSection(payload, basic);
        redisNode.getRedisNodes().forEach(node -> payload.getRedisCommands().add(toRedisCommandSummary(node)));
        redisNode.getErrors().forEach(error -> payload.getErrors().add(toErrorSummary(error)));
    }

    private static void addSection(GraphNodeDetailPayload payload, GraphNodeDetailSection section) {
        if (!section.getFields().isEmpty() || StringUtils.hasText(section.getContent())) {
            payload.getSections().add(section);
        }
    }

    private static void addField(GraphNodeDetailSection section, String label, Object value) {
        if (!StringUtils.hasText(label)) {
            return;
        }
        section.getFields().add(new GraphNodeDetailField(label, value == null ? "" : String.valueOf(value)));
    }

    private static Map<String, Object> buildHttpRequestPayload(HttpTraceNode node) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("url", node.getRequestUrl());
        request.put("method", node.getRequestMethod());
        request.put("body", node.getRequestBody());
        request.put("params", buildParamMap(node.getRequestParamNames(), node.getRequestParamValues()));
        Map<String, Object> headers = new LinkedHashMap<>();
        if (node.getRequestHeader() != null) {
            headers.put("cookie", node.getRequestHeader().getCookie());
            headers.put("userAgent", node.getRequestHeader().getUserAgent());
            headers.put("referer", node.getRequestHeader().getReferer());
            headers.put("authorization", node.getRequestHeader().getAuthorization());
            headers.put("userHeader", node.getRequestHeader().getUserHeader());
        }
        request.put("headers", headers);
        return request;
    }

    private static Map<String, String> buildParamMap(String[] names, String[] values) {
        Map<String, String> params = new LinkedHashMap<>();
        if (names == null) {
            return params;
        }
        for (int i = 0; i < names.length; i++) {
            params.put(names[i], values != null && values.length > i ? values[i] : null);
        }
        return params;
    }

    private static void appendSqlGroups(GraphNodeDetailPayload payload, List<SqlTraceGroup> groups) {
        if (groups == null) {
            return;
        }
        groups.forEach(group -> {
            GraphSqlSummary summary = new GraphSqlSummary();
            summary.setSql(group.getSql());
            summary.setJdbcUrl(group.getJdbcUrl());
            summary.setAddressIp(group.getAddressIp());
            summary.setPort(group.getPort());
            summary.setDatabaseName(group.getName());
            summary.setDatabaseType(group.getType());
            summary.setExecutes(optionalArray(group.getExecutes()));
            summary.setParams(group.getParams() == null ? new ArrayList<>() : group.getParams());
            summary.setCount(group.getCount());
            payload.getSqlGroups().add(summary);
        });
    }

    private static GraphSqlSummary toSqlSummary(SqlTraceNode node) {
        GraphSqlSummary summary = new GraphSqlSummary();
        summary.setType(node.toType());
        summary.setSql(node.getSql());
        summary.setJdbcUrl(node.getJdbcUrl());
        summary.setAddressIp(node.getDatabase() == null ? null : node.getDatabase().getAddressIp());
        summary.setPort(node.getDatabase() == null ? null : node.getDatabase().getPort());
        summary.setDatabaseName(node.getDatabase() == null ? null : node.getDatabase().getName());
        summary.setDatabaseType(node.getDatabase() == null ? null : node.getDatabase().getType());
        summary.setExecutes(optionalArray(node.getExecutes()));
        summary.setParams(singleParams(node.getParams()));
        summary.setUseTime(node.getUseTime());
        summary.setBeginTime(formatMillis(node.getBeginTime()));
        summary.setError(node.getError() == null ? null : toErrorSummary(node.getError()));
        return summary;
    }

    private static GraphSqlSummary toSqlSummary(CKSqlTraceNode node) {
        GraphSqlSummary summary = new GraphSqlSummary();
        summary.setType(node.toType());
        summary.setSql(node.getSql());
        summary.setJdbcUrl(node.getJdbcUrl());
        summary.setAddressIp(node.getDatabase() == null ? null : node.getDatabase().getAddressIp());
        summary.setPort(node.getDatabase() == null ? null : node.getDatabase().getPort());
        summary.setDatabaseName(node.getDatabase() == null ? null : node.getDatabase().getName());
        summary.setDatabaseType(node.getDatabase() == null ? null : node.getDatabase().getType());
        summary.setExecutes(optionalArray(node.getExecutes()));
        summary.setParams(singleParams(node.getParams()));
        summary.setUseTime(node.getUseTime());
        summary.setBeginTime(formatMillis(node.getBeginTime()));
        summary.setError(node.getError() == null ? null : toErrorSummary(node.getError()));
        return summary;
    }

    private static List<String[]> singleParams(String[] params) {
        List<String[]> list = new ArrayList<>();
        if (params != null) {
            list.add(params);
        }
        return list;
    }

    private static void appendTableOperations(GraphNodeDetailPayload payload, String action, List<SqlParseInfo> operations) {
        if (operations == null) {
            return;
        }
        operations.forEach(operation -> {
            GraphTableOperationSummary summary = new GraphTableOperationSummary();
            summary.setAction(action);
            summary.setModel(operation.getModel());
            summary.setTableName(operation.getTableName());
            summary.setColumns(operation.getColumns());
            summary.setSql(operation.getSql());
            payload.getTableOperations().add(summary);
        });
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(DubboTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "Dubbo");
        summary.setUrl(firstText(node.getRemoteUrl(), node.getRemoteIp()));
        summary.setInterfaceName(node.getServiceInterface());
        summary.setMethodName(node.getServiceMethodName());
        summary.setRequest(node.getInParam());
        summary.setResponse(node.getOutParam());
        return summary;
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(HttpClientTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "HTTP Client");
        summary.setMethodName(node.getServiceMethod());
        summary.setUrl(node.getServiceURL());
        summary.setHeaders(node.getServiceHeaders());
        summary.setRequest(node.getServiceBody());
        return summary;
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(FeignTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "Feign");
        summary.setTitle(firstText(node.getRemoteFeignTargetName(), node.getServiceURL()));
        summary.setMethodName(firstText(node.getRemoteMethod(), node.getServiceMethod()));
        summary.setUrl(firstText(node.getRemoteUrl(), node.getServiceURL()));
        summary.setHeaders(node.getServiceHeaders());
        summary.setRequest(firstText(node.getRemoteBody(), node.getServiceBody()));
        summary.setResponse(node.getRemoteResponse());
        summary.setRemoteApp(toApplicationSummary(node.getRemoteApp()));
        return summary;
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(SofaRpcTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "SofaRPC");
        summary.setUrl(node.getDirectUrl());
        summary.setInterfaceName(firstText(node.getInterfaceName(), node.getTargetServiceUniqueName()));
        summary.setMethodName(node.getMethodName());
        summary.setRequest(node.getInParam());
        summary.setResponse(node.getOutParam());
        summary.getExtra().put("invokeType", node.getInvokeType());
        summary.setRemoteApp(toApplicationSummary(node.getRemoteApp()));
        return summary;
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(RabbitMQTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "RabbitMQ");
        summary.setTitle(firstText(node.getExchange(), node.getRoutingKey()));
        summary.setRequest(node.getBody());
        summary.getExtra().put("exchange", node.getExchange());
        summary.getExtra().put("routingKey", node.getRoutingKey());
        summary.setRemoteApp(toApplicationSummary(node.getRemoteApp()));
        return summary;
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(RocketMQProducerTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "RocketMQ");
        summary.setTitle(node.getProducer());
        summary.setRequest(node.getMessage());
        summary.setRemoteApp(toApplicationSummary(node.getRemoteApp()));
        return summary;
    }

    private static GraphRemoteCallSummary toRemoteCallSummary(KafkaMQTraceNode node) {
        GraphRemoteCallSummary summary = baseRemoteCallSummary(node, "KafkaMQ");
        summary.setRequest(node.getProducerRecord());
        summary.setRemoteApp(toApplicationSummary(node.getRemoteApp()));
        return summary;
    }

    private static GraphRemoteCallSummary baseRemoteCallSummary(TraceNode node, String type) {
        GraphRemoteCallSummary summary = new GraphRemoteCallSummary();
        summary.setType(type);
        summary.setTraceNodeId(node.getTraceNodeId());
        summary.setBeginTime(formatMillis(node.getBeginTime()));
        summary.setUseTime(node.getUseTime());
        summary.setAddressIp(node.getAddressIp());
        if (node instanceof com.oAT.agent.model.StatementError) {
            com.oAT.agent.model.Error error = ((com.oAT.agent.model.StatementError) node).getError();
            summary.setError(error == null ? null : toErrorSummary(error));
        }
        return summary;
    }

    private static Map<String, String> toApplicationSummary(Application application) {
        Map<String, String> summary = new LinkedHashMap<>();
        if (application != null) {
            summary.put("appId", application.getAppId());
            summary.put("appName", application.getAppName());
            summary.put("projectSrcName", application.getProjectSrcName());
        }
        return summary;
    }

    private static GraphRedisCommandSummary toRedisCommandSummary(RedisTraceNode node) {
        GraphRedisCommandSummary summary = new GraphRedisCommandSummary();
        summary.setType(node.getType());
        summary.setCommand(node.getCmd());
        summary.setHost(node.getHost());
        summary.setPort(node.getPort());
        summary.setBeginTime(formatMillis(node.getBeginTime()));
        summary.setUseTime(node.getUseTime());
        summary.setError(node.getError() == null ? null : toErrorSummary(node.getError()));
        return summary;
    }

    private static GraphErrorSummary toErrorSummary(com.oAT.agent.model.Error error) {
        GraphErrorSummary summary = new GraphErrorSummary();
        summary.setType(error.getType());
        summary.setCode(error.getCode());
        summary.setMessage(error.getMessage());
        summary.setErrorStack(error.getErrorStack());
        return summary;
    }

    private static String formatMillis(Long millis) {
        if (millis == null || millis <= 0) {
            return "";
        }
        return new SimpleDateFormat(DATE_TIME_PATTERN).format(new Date(millis));
    }

    private static String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static List<String> optionalArray(String[] values) {
        if (values == null || values.length == 0) {
            return new ArrayList<>();
        }
        return Arrays.asList(values);
    }

    private static String limitText(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
