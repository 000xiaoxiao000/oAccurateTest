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
    @Field(type = FieldType.Date, format = {})
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
    private RedisTraceNode redisNode;

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
        } else if (node instanceof SqlTraceNode) {
            this.sqlNode = (SqlTraceNode) node;
        }else if (node instanceof CKSqlTraceNode) {
            this.cksqlNode = (CKSqlTraceNode) node;
        }else if (node instanceof RedisTraceNode) {
            this.redisNode = (RedisTraceNode) node;
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
        } else if (sqlNode != null) {
            return sqlNode;
        }else if (cksqlNode != null) {
            return cksqlNode;
        }else if (redisNode != null) {
            return redisNode;
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

    public RedisTraceNode getRedisNode() {
        return redisNode;
    }

    public void setRedisNode(RedisTraceNode redisNode) {
        this.redisNode = redisNode;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }
}
