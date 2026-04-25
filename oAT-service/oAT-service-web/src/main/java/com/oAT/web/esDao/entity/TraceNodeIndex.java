package com.oAT.web.esDao.entity;

import com.oAT.agent.model.*;
import com.oAT.web.exceptions.DirtyDataException;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

@Document(indexName = "trace_node", shards = 2)
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
    @Deprecated
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
    }


    public TraceNodeIndex(DubboRemoteTraceNode dubboRemoteNode) {
        this.dubboRemoteNode = dubboRemoteNode;
        init(dubboRemoteNode);
    }

    public TraceNodeIndex(SqlTraceNode sqlNode) {
        this.sqlNode = sqlNode;
        init(sqlNode);
    }

    public TraceNodeIndex(CKSqlTraceNode cksqlNode) {
        this.cksqlNode = cksqlNode;
        init(cksqlNode);
    }

    public TraceNodeIndex(HttpTraceNode httpNode) {
        this.httpNode = httpNode;
        init(httpNode);
    }

    public TraceNodeIndex(FeignTraceNode feignNode) {
        this.feignNode = feignNode;
        init(feignNode);
    }

    public TraceNodeIndex(RedisTraceNode redisNode) {
        this.redisNode = redisNode;
        init(redisNode);
    }

    private void init(TraceNode node) {
        type = node.toType();
        createTime = new Date();
        traceId = node.getTraceId();
        traceNodeId = node.getTraceNodeId();
        if (node.getApp() != null) {
            appId = node.getApp().getAppId();
        }
        id = traceId + "_" + traceNodeId;
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
