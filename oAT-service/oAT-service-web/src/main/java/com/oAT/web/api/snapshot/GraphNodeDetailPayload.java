package com.oAT.web.api.snapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphNodeDetailPayload {
    private String id;
    private String title;
    private String name;
    private String type;
    private String ip;
    private String logPreview;
    private Map<String, Object> stats = new LinkedHashMap<>();
    private Map<String, Object> request = new LinkedHashMap<>();
    private List<GraphNodeDetailSection> sections = new ArrayList<>();
    private List<GraphSqlSummary> sqlGroups = new ArrayList<>();
    private List<GraphSqlSummary> sqlStatements = new ArrayList<>();
    private List<GraphTableOperationSummary> tableOperations = new ArrayList<>();
    private List<GraphRemoteCallSummary> remoteCalls = new ArrayList<>();
    private List<GraphRedisCommandSummary> redisCommands = new ArrayList<>();
    private List<GraphErrorSummary> errors = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getLogPreview() { return logPreview; }
    public void setLogPreview(String logPreview) { this.logPreview = logPreview; }
    public Map<String, Object> getStats() { return stats; }
    public void setStats(Map<String, Object> stats) { this.stats = stats; }
    public Map<String, Object> getRequest() { return request; }
    public void setRequest(Map<String, Object> request) { this.request = request; }
    public List<GraphNodeDetailSection> getSections() { return sections; }
    public void setSections(List<GraphNodeDetailSection> sections) { this.sections = sections; }
    public List<GraphSqlSummary> getSqlGroups() { return sqlGroups; }
    public void setSqlGroups(List<GraphSqlSummary> sqlGroups) { this.sqlGroups = sqlGroups; }
    public List<GraphSqlSummary> getSqlStatements() { return sqlStatements; }
    public void setSqlStatements(List<GraphSqlSummary> sqlStatements) { this.sqlStatements = sqlStatements; }
    public List<GraphTableOperationSummary> getTableOperations() { return tableOperations; }
    public void setTableOperations(List<GraphTableOperationSummary> tableOperations) { this.tableOperations = tableOperations; }
    public List<GraphRemoteCallSummary> getRemoteCalls() { return remoteCalls; }
    public void setRemoteCalls(List<GraphRemoteCallSummary> remoteCalls) { this.remoteCalls = remoteCalls; }
    public List<GraphRedisCommandSummary> getRedisCommands() { return redisCommands; }
    public void setRedisCommands(List<GraphRedisCommandSummary> redisCommands) { this.redisCommands = redisCommands; }
    public List<GraphErrorSummary> getErrors() { return errors; }
    public void setErrors(List<GraphErrorSummary> errors) { this.errors = errors; }

    public static class GraphNodeDetailSection {
        private String title;
        private String kind = "fields";
        private String content;
        private List<GraphNodeDetailField> fields = new ArrayList<>();

        public GraphNodeDetailSection() {}
        public GraphNodeDetailSection(String title) { this.title = title; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<GraphNodeDetailField> getFields() { return fields; }
        public void setFields(List<GraphNodeDetailField> fields) { this.fields = fields; }
    }

    public static class GraphNodeDetailField {
        private String label;
        private String value;

        public GraphNodeDetailField() {}
        public GraphNodeDetailField(String label, String value) {
            this.label = label;
            this.value = value;
        }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public static class GraphSqlSummary {
        private String type;
        private String sql;
        private String jdbcUrl;
        private String addressIp;
        private String port;
        private String databaseName;
        private String databaseType;
        private List<String> executes = new ArrayList<>();
        private List<String[]> params = new ArrayList<>();
        private Integer count;
        private Long useTime;
        private String beginTime;
        private GraphErrorSummary error;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
        public String getAddressIp() { return addressIp; }
        public void setAddressIp(String addressIp) { this.addressIp = addressIp; }
        public String getPort() { return port; }
        public void setPort(String port) { this.port = port; }
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        public String getDatabaseType() { return databaseType; }
        public void setDatabaseType(String databaseType) { this.databaseType = databaseType; }
        public List<String> getExecutes() { return executes; }
        public void setExecutes(List<String> executes) { this.executes = executes; }
        public List<String[]> getParams() { return params; }
        public void setParams(List<String[]> params) { this.params = params; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public Long getUseTime() { return useTime; }
        public void setUseTime(Long useTime) { this.useTime = useTime; }
        public String getBeginTime() { return beginTime; }
        public void setBeginTime(String beginTime) { this.beginTime = beginTime; }
        public GraphErrorSummary getError() { return error; }
        public void setError(GraphErrorSummary error) { this.error = error; }
    }

    public static class GraphTableOperationSummary {
        private String action;
        private String model;
        private String tableName;
        private List<String> columns = new ArrayList<>();
        private String sql;

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
    }

    public static class GraphRemoteCallSummary {
        private String type;
        private String traceNodeId;
        private String title;
        private String interfaceName;
        private String methodName;
        private String url;
        private String headers;
        private String request;
        private String response;
        private String beginTime;
        private Long useTime;
        private String addressIp;
        private Map<String, String> remoteApp = new LinkedHashMap<>();
        private Map<String, Object> extra = new LinkedHashMap<>();
        private GraphErrorSummary error;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTraceNodeId() { return traceNodeId; }
        public void setTraceNodeId(String traceNodeId) { this.traceNodeId = traceNodeId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getInterfaceName() { return interfaceName; }
        public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getHeaders() { return headers; }
        public void setHeaders(String headers) { this.headers = headers; }
        public String getRequest() { return request; }
        public void setRequest(String request) { this.request = request; }
        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }
        public String getBeginTime() { return beginTime; }
        public void setBeginTime(String beginTime) { this.beginTime = beginTime; }
        public Long getUseTime() { return useTime; }
        public void setUseTime(Long useTime) { this.useTime = useTime; }
        public String getAddressIp() { return addressIp; }
        public void setAddressIp(String addressIp) { this.addressIp = addressIp; }
        public Map<String, String> getRemoteApp() { return remoteApp; }
        public void setRemoteApp(Map<String, String> remoteApp) { this.remoteApp = remoteApp; }
        public Map<String, Object> getExtra() { return extra; }
        public void setExtra(Map<String, Object> extra) { this.extra = extra; }
        public GraphErrorSummary getError() { return error; }
        public void setError(GraphErrorSummary error) { this.error = error; }
    }

    public static class GraphRedisCommandSummary {
        private String type;
        private String command;
        private String host;
        private String port;
        private String beginTime;
        private Long useTime;
        private GraphErrorSummary error;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public String getPort() { return port; }
        public void setPort(String port) { this.port = port; }
        public String getBeginTime() { return beginTime; }
        public void setBeginTime(String beginTime) { this.beginTime = beginTime; }
        public Long getUseTime() { return useTime; }
        public void setUseTime(Long useTime) { this.useTime = useTime; }
        public GraphErrorSummary getError() { return error; }
        public void setError(GraphErrorSummary error) { this.error = error; }
    }

    public static class GraphErrorSummary {
        private String type;
        private String code;
        private String message;
        private String errorStack;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getErrorStack() { return errorStack; }
        public void setErrorStack(String errorStack) { this.errorStack = errorStack; }
    }
}
