package com.oAT.web.esDao.entity;

import com.oAT.agent.model.*;
import com.oAT.web.service.TraceEntryDescriptor;
import com.oAT.web.service.TraceEntryDescriptorBuilder;
import com.oAT.web.exceptions.DirtyDataException;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

/**
 * TraceNode index entity - stores trace node data in monthly rolling indices.
 * Large payload fields (requestBody, responseContent, codeNodes, etc.) are controlled
 * by the index template to avoid indexing overhead while preserving storage for detail views.
 */
@Document(indexName = "trace_node")
public class TraceNodeIndex implements StandardDate, Serializable {
    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private String traceId;
    @Field(type = FieldType.Keyword)
    private String traceNodeId;
    @Field(type = FieldType.Keyword)
    private String appId;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date createTime;
    @Field(type = FieldType.Keyword)
    private String type;

    // ===== auxiliary flattened fields =====
    @Field(type = FieldType.Boolean)
    private Boolean root;
    @Field(type = FieldType.Boolean)
    private Boolean hasError;
    @Field(type = FieldType.Keyword)
    private String sessionId;
    @Field(type = FieldType.Keyword)
    private String status;
    @Field(type = FieldType.Keyword)
    private String addressIp;
    @Field(type = FieldType.Long)
    private Long beginTime;
    @Field(type = FieldType.Long)
    private Long endTime;
    @Field(type = FieldType.Long)
    private Long useTime;
    @Field(type = FieldType.Keyword)
    private String appName;

    @Field(type = FieldType.Keyword)
    private String entryType;
    @Field(type = FieldType.Keyword)
    private String entryName;
    @Field(type = FieldType.Keyword)
    private String entryProtocol;
    @Field(type = FieldType.Keyword)
    private String entryTopic;
    @Field(type = FieldType.Keyword)
    private String entryInterface;
    @Field(type = FieldType.Keyword)
    private String entryMethod;

    // ===== HTTP flattened fields =====
    @Field(type = FieldType.Keyword)
    private String httpClientIp;
    @Field(type = FieldType.Keyword)
    private String httpServerIp;
    @Field(type = FieldType.Keyword)
    private String httpServerPort;
    @Field(type = FieldType.Keyword)
    private String httpMethod;
    @Field(type = FieldType.Keyword)
    private String httpUrl;
    @Field(type = FieldType.Keyword)
    private String httpResponseCode;
    @Field(type = FieldType.Boolean)
    private Boolean httpAjax;

    // ===== SQL flattened fields =====
    @Field(type = FieldType.Keyword)
    private String sqlDatabaseName;
    @Field(type = FieldType.Keyword)
    private String sqlDatabaseIp;
    @Field(type = FieldType.Keyword)
    private String sqlDatabaseType;
    @Field(type = FieldType.Keyword)
    private String[] sqlOperations;

    // ===== Remote call flattened fields (Dubbo / Feign / HttpClient / Sofa / MQ) =====
    @Field(type = FieldType.Keyword)
    private String remoteKind;
    @Field(type = FieldType.Keyword)
    private String remoteIp;
    @Field(type = FieldType.Keyword)
    private String remoteUrl;
    @Field(type = FieldType.Keyword)
    private String remoteAppId;
    @Field(type = FieldType.Keyword)
    private String remoteAppName;
    @Field(type = FieldType.Keyword)
    private String remoteInterface;
    @Field(type = FieldType.Keyword)
    private String remoteMethod;
    @Field(type = FieldType.Keyword)
    private String mqTopic;

    // ===== Redis flattened fields =====
    @Field(type = FieldType.Keyword)
    private String redisHost;
    @Field(type = FieldType.Keyword)
    private String redisPort;
    @Field(type = FieldType.Keyword)
    private String redisCommand;

    // ===== original nested node objects =====
    @Field(type = FieldType.Object)
    private DubboTraceNode dubboNode;
    @Field(type = FieldType.Object)
    private DubboRemoteTraceNode dubboRemoteNode;
    @Field(type = FieldType.Object)
    private SqlTraceNode sqlNode;
    @Field(type = FieldType.Object)
    private CKSqlTraceNode cksqlNode;
    @Field(type = FieldType.Object)
    private HttpTraceNode httpNode;
    @Field(type = FieldType.Object)
    private HttpClientTraceNode httpClientNode;
    @Field(type = FieldType.Object)
    private FeignTraceNode feignNode;
    @Field(type = FieldType.Object)
    private RedisTraceNode redisNode;
    @Field(type = FieldType.Object)
    private ServiceTraceNode serviceNode;
    @Field(type = FieldType.Object)
    private SofaRpcTraceNode sofaRpcNode;
    @Field(type = FieldType.Object)
    private SofaRpcRemoteTraceNode sofaRpcRemoteNode;
    @Field(type = FieldType.Object)
    private RabbitMQTraceNode rabbitMQNode;
    @Field(type = FieldType.Object)
    private RabbitMQRemoteTraceNode rabbitMQRemoteNode;
    @Field(type = FieldType.Object)
    private RocketMQProducerTraceNode rocketMQProducerNode;
    @Field(type = FieldType.Object)
    private RocketMQConsumerTraceNode rocketMQConsumerNode;
    @Field(type = FieldType.Object)
    private KafkaMQTraceNode kafkaMQNode;
    @Field(type = FieldType.Object)
    private KafkaMQRemoteTraceNode kafkaMQRemoteNode;

    /**
     * 该构造方法为自动注入保留方法，
     * 应用<span style="color:red">必须使用带参数的构造方法</span>
     */
    public TraceNodeIndex() {}

    public TraceNodeIndex(TraceNode node) {
        if (node instanceof DubboTraceNode) {
            this.dubboNode = (DubboTraceNode) node;
        } else if (node instanceof DubboRemoteTraceNode) {
            this.dubboRemoteNode = (DubboRemoteTraceNode) node;
        } else if (node instanceof HttpTraceNode) {
            this.httpNode = (HttpTraceNode) node;
        } else if (node instanceof HttpClientTraceNode) {
            this.httpClientNode = (HttpClientTraceNode) node;
        } else if (node instanceof FeignTraceNode) {
            this.feignNode = (FeignTraceNode) node;
        } else if (node instanceof SqlTraceNode) {
            this.sqlNode = (SqlTraceNode) node;
        }else if (node instanceof CKSqlTraceNode) {
            this.cksqlNode = (CKSqlTraceNode) node;
        }else if (node instanceof RedisTraceNode) {
            this.redisNode = (RedisTraceNode) node;
        } else if (node instanceof ServiceTraceNode) {
            this.serviceNode = (ServiceTraceNode) node;
        } else if (node instanceof SofaRpcTraceNode) {
            this.sofaRpcNode = (SofaRpcTraceNode) node;
        } else if (node instanceof SofaRpcRemoteTraceNode) {
            this.sofaRpcRemoteNode = (SofaRpcRemoteTraceNode) node;
        } else if (node instanceof RabbitMQTraceNode) {
            this.rabbitMQNode = (RabbitMQTraceNode) node;
        } else if (node instanceof RabbitMQRemoteTraceNode) {
            this.rabbitMQRemoteNode = (RabbitMQRemoteTraceNode) node;
        } else if (node instanceof RocketMQProducerTraceNode) {
            this.rocketMQProducerNode = (RocketMQProducerTraceNode) node;
        } else if (node instanceof RocketMQConsumerTraceNode) {
            this.rocketMQConsumerNode = (RocketMQConsumerTraceNode) node;
        } else if (node instanceof KafkaMQTraceNode) {
            this.kafkaMQNode = (KafkaMQTraceNode) node;
        } else if (node instanceof KafkaMQRemoteTraceNode) {
            this.kafkaMQRemoteNode = (KafkaMQRemoteTraceNode) node;
        } else {
            throw new RuntimeException("unknown " + node.getClass().getName());
        }
        init(node);
        extractFlattenedFields(node);
    }

    public TraceNode toTraceNode() {
        if (dubboNode != null) {
            return dubboNode;
        } else if (dubboRemoteNode != null) {
            return dubboRemoteNode;
        } else if (httpNode != null) {
            return httpNode;
        } else if (httpClientNode != null) {
            return httpClientNode;
        } else if (feignNode != null) {
            return feignNode;
        } else if (sqlNode != null) {
            return sqlNode;
        }else if (cksqlNode != null) {
            return cksqlNode;
        }else if (redisNode != null) {
            return redisNode;
        } else if (serviceNode != null) {
            return serviceNode;
        } else if (sofaRpcNode != null) {
            return sofaRpcNode;
        } else if (sofaRpcRemoteNode != null) {
            return sofaRpcRemoteNode;
        } else if (rabbitMQNode != null) {
            return rabbitMQNode;
        } else if (rabbitMQRemoteNode != null) {
            return rabbitMQRemoteNode;
        } else if (rocketMQProducerNode != null) {
            return rocketMQProducerNode;
        } else if (rocketMQConsumerNode != null) {
            return rocketMQConsumerNode;
        } else if (kafkaMQNode != null) {
            return kafkaMQNode;
        } else if (kafkaMQRemoteNode != null) {
            return kafkaMQRemoteNode;
        }
        throw new DirtyDataException("invalid TraceNode");
    }

    public TraceNodeIndex(DubboTraceNode dubboNode) {
        this.dubboNode = dubboNode;
        init(dubboNode);
        extractFlattenedFields(dubboNode);
    }

    public TraceNodeIndex(DubboRemoteTraceNode dubboRemoteNode) {
        this.dubboRemoteNode = dubboRemoteNode;
        init(dubboRemoteNode);
        extractFlattenedFields(dubboRemoteNode);
    }

    public TraceNodeIndex(SqlTraceNode sqlNode) {
        this.sqlNode = sqlNode;
        init(sqlNode);
        extractFlattenedFields(sqlNode);
    }

    public TraceNodeIndex(CKSqlTraceNode cksqlNode) {
        this.cksqlNode = cksqlNode;
        init(cksqlNode);
        extractFlattenedFields(cksqlNode);
    }

    public TraceNodeIndex(HttpTraceNode httpNode) {
        this.httpNode = httpNode;
        init(httpNode);
        extractFlattenedFields(httpNode);
    }

    public TraceNodeIndex(FeignTraceNode feignNode) {
        this.feignNode = feignNode;
        init(feignNode);
        extractFlattenedFields(feignNode);
    }

    public TraceNodeIndex(RedisTraceNode redisNode) {
        this.redisNode = redisNode;
        init(redisNode);
        extractFlattenedFields(redisNode);
    }

    private void init(TraceNode node) {
        type = node.toType();
        createTime = new Date();
        traceId = node.getTraceId();
        traceNodeId = node.getTraceNodeId();
        if (node.getApp() != null) {
            appId = node.getApp().getAppId();
            appName = node.getApp().getAppName();
        }
        id = traceId + "_" + traceNodeId;
        root = "0".equals(node.getTraceNodeId());
        sessionId = node.getSessionId();
        status = node.getStatus();
        addressIp = node.getAddressIp();
        beginTime = node.getBeginTime();
        endTime = node.getEndTime();
        useTime = node.getUseTime();
        hasError = (node instanceof StatementError) && ((StatementError) node).getError() != null;
        if (root) {
            TraceEntryDescriptor entry = TraceEntryDescriptorBuilder.build(node);
            entryType = entry.getEntryType();
            entryName = entry.getEntryName();
            entryProtocol = entry.getEntryProtocol();
            entryTopic = entry.getEntryTopic();
            entryInterface = entry.getEntryInterface();
            entryMethod = entry.getEntryMethod();
        }
    }

    private void extractFlattenedFields(TraceNode node) {
        if (node instanceof HttpTraceNode) {
            HttpTraceNode http = (HttpTraceNode) node;
            httpClientIp = http.getClientIp();
            httpServerIp = http.getServerIp();
            httpServerPort = http.getServerPort();
            httpMethod = http.getRequestMethod();
            httpUrl = http.getRequestUrl();
            httpResponseCode = http.getResponseCode();
            httpAjax = http.getAjax();
        } else if (node instanceof SqlTraceNode) {
            SqlTraceNode sql = (SqlTraceNode) node;
            if (sql.getDatabase() != null) {
                sqlDatabaseName = sql.getDatabase().getName();
                sqlDatabaseIp = sql.getDatabase().getAddressIp();
                sqlDatabaseType = sql.getDatabase().getType();
            }
            sqlOperations = sql.getExecutes();
        } else if (node instanceof CKSqlTraceNode) {
            CKSqlTraceNode ck = (CKSqlTraceNode) node;
            if (ck.getDatabase() != null) {
                sqlDatabaseName = ck.getDatabase().getName();
                sqlDatabaseIp = ck.getDatabase().getAddressIp();
                sqlDatabaseType = ck.getDatabase().getType();
            }
            sqlOperations = ck.getExecutes();
        } else if (node instanceof DubboTraceNode) {
            DubboTraceNode dubbo = (DubboTraceNode) node;
            remoteKind = "dubbo";
            remoteIp = dubbo.getRemoteIp();
            remoteUrl = dubbo.getRemoteUrl();
            remoteInterface = dubbo.getServiceInterface();
            remoteMethod = dubbo.getServiceMethodName();
            if (dubbo.getRemoteApp() != null) {
                remoteAppId = dubbo.getRemoteApp().getAppId();
                remoteAppName = dubbo.getRemoteApp().getAppName();
            }
        } else if (node instanceof FeignTraceNode) {
            FeignTraceNode feign = (FeignTraceNode) node;
            remoteKind = "feign";
            remoteUrl = feign.getServiceURL();
            remoteMethod = feign.getServiceMethod();
            if (feign.getRemoteApp() != null) {
                remoteAppId = feign.getRemoteApp().getAppId();
                remoteAppName = feign.getRemoteApp().getAppName();
            }
        } else if (node instanceof HttpClientTraceNode) {
            HttpClientTraceNode hc = (HttpClientTraceNode) node;
            remoteKind = "httpClient";
            remoteUrl = hc.getServiceURL();
            remoteMethod = hc.getServiceMethod();
        } else if (node instanceof SofaRpcTraceNode) {
            SofaRpcTraceNode sofa = (SofaRpcTraceNode) node;
            remoteKind = "sofa";
            remoteInterface = sofa.getInterfaceName();
            remoteMethod = sofa.getMethodName();
            if (sofa.getRemoteApp() != null) {
                remoteAppId = sofa.getRemoteApp().getAppId();
                remoteAppName = sofa.getRemoteApp().getAppName();
            }
        } else if (node instanceof RabbitMQTraceNode) {
            RabbitMQTraceNode rabbit = (RabbitMQTraceNode) node;
            remoteKind = "rabbitMQ";
            mqTopic = rabbit.getExchange();
            if (rabbit.getRemoteApp() != null) {
                remoteAppId = rabbit.getRemoteApp().getAppId();
                remoteAppName = rabbit.getRemoteApp().getAppName();
            }
        } else if (node instanceof RocketMQProducerTraceNode) {
            RocketMQProducerTraceNode rocket = (RocketMQProducerTraceNode) node;
            remoteKind = "rocketMQ";
            mqTopic = rocket.getProducer();
            if (rocket.getRemoteApp() != null) {
                remoteAppId = rocket.getRemoteApp().getAppId();
                remoteAppName = rocket.getRemoteApp().getAppName();
            }
        } else if (node instanceof KafkaMQTraceNode) {
            KafkaMQTraceNode kafka = (KafkaMQTraceNode) node;
            remoteKind = "kafkaMQ";
            if (kafka.getRemoteApp() != null) {
                remoteAppId = kafka.getRemoteApp().getAppId();
                remoteAppName = kafka.getRemoteApp().getAppName();
            }
        } else if (node instanceof RedisTraceNode) {
            RedisTraceNode redis = (RedisTraceNode) node;
            redisHost = redis.getHost();
            redisPort = redis.getPort();
            redisCommand = redis.getType();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTraceNodeId() {
        return traceNodeId;
    }

    public void setTraceNodeId(String traceNodeId) {
        this.traceNodeId = traceNodeId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getRoot() { return root; }
    public void setRoot(Boolean root) { this.root = root; }

    public Boolean getHasError() { return hasError; }
    public void setHasError(Boolean hasError) { this.hasError = hasError; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAddressIp() { return addressIp; }
    public void setAddressIp(String addressIp) { this.addressIp = addressIp; }

    public Long getBeginTime() { return beginTime; }
    public void setBeginTime(Long beginTime) { this.beginTime = beginTime; }

    public Long getEndTime() { return endTime; }
    public void setEndTime(Long endTime) { this.endTime = endTime; }

    public Long getUseTime() { return useTime; }
    public void setUseTime(Long useTime) { this.useTime = useTime; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }

    public String getEntryName() { return entryName; }
    public void setEntryName(String entryName) { this.entryName = entryName; }

    public String getEntryProtocol() { return entryProtocol; }
    public void setEntryProtocol(String entryProtocol) { this.entryProtocol = entryProtocol; }

    public String getEntryTopic() { return entryTopic; }
    public void setEntryTopic(String entryTopic) { this.entryTopic = entryTopic; }

    public String getEntryInterface() { return entryInterface; }
    public void setEntryInterface(String entryInterface) { this.entryInterface = entryInterface; }

    public String getEntryMethod() { return entryMethod; }
    public void setEntryMethod(String entryMethod) { this.entryMethod = entryMethod; }

    public String getHttpClientIp() { return httpClientIp; }
    public void setHttpClientIp(String httpClientIp) { this.httpClientIp = httpClientIp; }

    public String getHttpServerIp() { return httpServerIp; }
    public void setHttpServerIp(String httpServerIp) { this.httpServerIp = httpServerIp; }

    public String getHttpServerPort() { return httpServerPort; }
    public void setHttpServerPort(String httpServerPort) { this.httpServerPort = httpServerPort; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getHttpUrl() { return httpUrl; }
    public void setHttpUrl(String httpUrl) { this.httpUrl = httpUrl; }

    public String getHttpResponseCode() { return httpResponseCode; }
    public void setHttpResponseCode(String httpResponseCode) { this.httpResponseCode = httpResponseCode; }

    public Boolean getHttpAjax() { return httpAjax; }
    public void setHttpAjax(Boolean httpAjax) { this.httpAjax = httpAjax; }

    public String getSqlDatabaseName() { return sqlDatabaseName; }
    public void setSqlDatabaseName(String sqlDatabaseName) { this.sqlDatabaseName = sqlDatabaseName; }

    public String getSqlDatabaseIp() { return sqlDatabaseIp; }
    public void setSqlDatabaseIp(String sqlDatabaseIp) { this.sqlDatabaseIp = sqlDatabaseIp; }

    public String getSqlDatabaseType() { return sqlDatabaseType; }
    public void setSqlDatabaseType(String sqlDatabaseType) { this.sqlDatabaseType = sqlDatabaseType; }

    public String[] getSqlOperations() { return sqlOperations; }
    public void setSqlOperations(String[] sqlOperations) { this.sqlOperations = sqlOperations; }

    public String getRemoteKind() { return remoteKind; }
    public void setRemoteKind(String remoteKind) { this.remoteKind = remoteKind; }

    public String getRemoteIp() { return remoteIp; }
    public void setRemoteIp(String remoteIp) { this.remoteIp = remoteIp; }

    public String getRemoteUrl() { return remoteUrl; }
    public void setRemoteUrl(String remoteUrl) { this.remoteUrl = remoteUrl; }

    public String getRemoteAppId() { return remoteAppId; }
    public void setRemoteAppId(String remoteAppId) { this.remoteAppId = remoteAppId; }

    public String getRemoteAppName() { return remoteAppName; }
    public void setRemoteAppName(String remoteAppName) { this.remoteAppName = remoteAppName; }

    public String getRemoteInterface() { return remoteInterface; }
    public void setRemoteInterface(String remoteInterface) { this.remoteInterface = remoteInterface; }

    public String getRemoteMethod() { return remoteMethod; }
    public void setRemoteMethod(String remoteMethod) { this.remoteMethod = remoteMethod; }

    public String getMqTopic() { return mqTopic; }
    public void setMqTopic(String mqTopic) { this.mqTopic = mqTopic; }

    public String getRedisHost() { return redisHost; }
    public void setRedisHost(String redisHost) { this.redisHost = redisHost; }

    public String getRedisPort() { return redisPort; }
    public void setRedisPort(String redisPort) { this.redisPort = redisPort; }

    public String getRedisCommand() { return redisCommand; }
    public void setRedisCommand(String redisCommand) { this.redisCommand = redisCommand; }

    public DubboTraceNode getDubboNode() {
        return dubboNode;
    }

    public void setDubboNode(DubboTraceNode dubboNode) {
        this.dubboNode = dubboNode;
    }

    public DubboRemoteTraceNode getDubboRemoteNode() {
        return dubboRemoteNode;
    }

    public void setDubboRemoteNode(DubboRemoteTraceNode dubboRemoteNode) {
        this.dubboRemoteNode = dubboRemoteNode;
    }

    public SqlTraceNode getSqlNode() {
        return sqlNode;
    }

    public void setSqlNode(SqlTraceNode sqlNode) {
        this.sqlNode = sqlNode;
    }

    public CKSqlTraceNode getCksqlNode() {
        return cksqlNode;
    }

    public void setCksqlNode(CKSqlTraceNode cksqlNode) {
        this.cksqlNode = cksqlNode;
    }

    public HttpTraceNode getHttpNode() {
        return httpNode;
    }

    public void setHttpNode(HttpTraceNode httpNode) {
        this.httpNode = httpNode;
    }

    public FeignTraceNode getFeignNode() {
        return feignNode;
    }

    public void setFeignNode(FeignTraceNode feignNode) {
        this.feignNode = feignNode;
    }

    public HttpClientTraceNode getHttpClientNode() {
        return httpClientNode;
    }

    public void setHttpClientNode(HttpClientTraceNode httpClientNode) {
        this.httpClientNode = httpClientNode;
    }

    public RedisTraceNode getRedisNode() {
        return redisNode;
    }

    public void setRedisNode(RedisTraceNode redisNode) {
        this.redisNode = redisNode;
    }

    public ServiceTraceNode getServiceNode() {
        return serviceNode;
    }

    public void setServiceNode(ServiceTraceNode serviceNode) {
        this.serviceNode = serviceNode;
    }

    public SofaRpcTraceNode getSofaRpcNode() {
        return sofaRpcNode;
    }

    public void setSofaRpcNode(SofaRpcTraceNode sofaRpcNode) {
        this.sofaRpcNode = sofaRpcNode;
    }

    public SofaRpcRemoteTraceNode getSofaRpcRemoteNode() {
        return sofaRpcRemoteNode;
    }

    public void setSofaRpcRemoteNode(SofaRpcRemoteTraceNode sofaRpcRemoteNode) {
        this.sofaRpcRemoteNode = sofaRpcRemoteNode;
    }

    public RabbitMQTraceNode getRabbitMQNode() {
        return rabbitMQNode;
    }

    public void setRabbitMQNode(RabbitMQTraceNode rabbitMQNode) {
        this.rabbitMQNode = rabbitMQNode;
    }

    public RabbitMQRemoteTraceNode getRabbitMQRemoteNode() {
        return rabbitMQRemoteNode;
    }

    public void setRabbitMQRemoteNode(RabbitMQRemoteTraceNode rabbitMQRemoteNode) {
        this.rabbitMQRemoteNode = rabbitMQRemoteNode;
    }

    public RocketMQProducerTraceNode getRocketMQProducerNode() {
        return rocketMQProducerNode;
    }

    public void setRocketMQProducerNode(RocketMQProducerTraceNode rocketMQProducerNode) {
        this.rocketMQProducerNode = rocketMQProducerNode;
    }

    public RocketMQConsumerTraceNode getRocketMQConsumerNode() {
        return rocketMQConsumerNode;
    }

    public void setRocketMQConsumerNode(RocketMQConsumerTraceNode rocketMQConsumerNode) {
        this.rocketMQConsumerNode = rocketMQConsumerNode;
    }

    public KafkaMQTraceNode getKafkaMQNode() {
        return kafkaMQNode;
    }

    public void setKafkaMQNode(KafkaMQTraceNode kafkaMQNode) {
        this.kafkaMQNode = kafkaMQNode;
    }

    public KafkaMQRemoteTraceNode getKafkaMQRemoteNode() {
        return kafkaMQRemoteNode;
    }

    public void setKafkaMQRemoteNode(KafkaMQRemoteTraceNode kafkaMQRemoteNode) {
        this.kafkaMQRemoteNode = kafkaMQRemoteNode;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }
}
