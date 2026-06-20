package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

/**
 * Trace summary index - one document per trace, optimized for list queries.
 * Stores lightweight metadata and aggregated statistics without heavy payload.
 */
@Document(indexName = "trace_summary")
public class TraceSummaryIndex implements StandardDate, Serializable {
    
    @Id
    private String traceId;
    
    @Field(type = FieldType.Keyword)
    private String projectId;
    
    @Field(type = FieldType.Keyword)
    private String appId;
    
    @Field(type = FieldType.Keyword)
    private String appName;
    
    @Field(type = FieldType.Keyword)
    private String sessionId;
    
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date createTime;
    
    @Field(type = FieldType.Long)
    private Long beginTime;
    
    @Field(type = FieldType.Long)
    private Long endTime;
    
    @Field(type = FieldType.Long)
    private Long useTime;
    
    @Field(type = FieldType.Keyword)
    private String status;
    
    @Field(type = FieldType.Boolean)
    private Boolean hasError;
    
    @Field(type = FieldType.Keyword)
    private String httpMethod;
    
    @Field(type = FieldType.Keyword)
    private String httpUrl;
    
    @Field(type = FieldType.Keyword)
    private String httpUrlPath;
    
    @Field(type = FieldType.Keyword)
    private String httpResponseCode;
    
    @Field(type = FieldType.Keyword)
    private String httpClientIp;
    
    @Field(type = FieldType.Keyword)
    private String httpServerIp;
    
    @Field(type = FieldType.Keyword)
    private String httpServerPort;
    
    @Field(type = FieldType.Boolean)
    private Boolean httpAjax;

    @Field(type = FieldType.Keyword)
    private String entryType;
    @Field(type = FieldType.Keyword)
    private String entryName;
    @Field(type = FieldType.Keyword)
    private String entryProtocol;
    @Field(type = FieldType.Keyword)
    private String entryAppId;
    @Field(type = FieldType.Keyword)
    private String entryAppName;
    @Field(type = FieldType.Keyword)
    private String entryClientIp;
    @Field(type = FieldType.Keyword)
    private String entryTopic;
    @Field(type = FieldType.Keyword)
    private String entryInterface;
    @Field(type = FieldType.Keyword)
    private String entryMethod;
    
    @Field(type = FieldType.Integer)
    private Integer nodeCount;
    
    @Field(type = FieldType.Integer)
    private Integer sqlCount;
    
    @Field(type = FieldType.Integer)
    private Integer remoteCount;
    
    @Field(type = FieldType.Integer)
    private Integer redisCount;
    
    @Field(type = FieldType.Integer)
    private Integer mqCount;
    
    @Field(type = FieldType.Integer)
    private Integer errorCount;
    
    @Field(type = FieldType.Integer)
    private Integer slowNodeCount;

    public TraceSummaryIndex() {}

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Long getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(Long beginTime) {
        this.beginTime = beginTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public Long getUseTime() {
        return useTime;
    }

    public void setUseTime(Long useTime) {
        this.useTime = useTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getHasError() {
        return hasError;
    }

    public void setHasError(Boolean hasError) {
        this.hasError = hasError;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getHttpUrl() {
        return httpUrl;
    }

    public void setHttpUrl(String httpUrl) {
        this.httpUrl = httpUrl;
    }

    public String getHttpUrlPath() {
        return httpUrlPath;
    }

    public void setHttpUrlPath(String httpUrlPath) {
        this.httpUrlPath = httpUrlPath;
    }

    public String getHttpResponseCode() {
        return httpResponseCode;
    }

    public void setHttpResponseCode(String httpResponseCode) {
        this.httpResponseCode = httpResponseCode;
    }

    public String getHttpClientIp() {
        return httpClientIp;
    }

    public void setHttpClientIp(String httpClientIp) {
        this.httpClientIp = httpClientIp;
    }

    public String getHttpServerIp() {
        return httpServerIp;
    }

    public void setHttpServerIp(String httpServerIp) {
        this.httpServerIp = httpServerIp;
    }

    public String getHttpServerPort() {
        return httpServerPort;
    }

    public void setHttpServerPort(String httpServerPort) {
        this.httpServerPort = httpServerPort;
    }

    public Boolean getHttpAjax() {
        return httpAjax;
    }

    public void setHttpAjax(Boolean httpAjax) {
        this.httpAjax = httpAjax;
    }

    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }

    public String getEntryName() { return entryName; }
    public void setEntryName(String entryName) { this.entryName = entryName; }

    public String getEntryProtocol() { return entryProtocol; }
    public void setEntryProtocol(String entryProtocol) { this.entryProtocol = entryProtocol; }

    public String getEntryAppId() { return entryAppId; }
    public void setEntryAppId(String entryAppId) { this.entryAppId = entryAppId; }

    public String getEntryAppName() { return entryAppName; }
    public void setEntryAppName(String entryAppName) { this.entryAppName = entryAppName; }

    public String getEntryClientIp() { return entryClientIp; }
    public void setEntryClientIp(String entryClientIp) { this.entryClientIp = entryClientIp; }

    public String getEntryTopic() { return entryTopic; }
    public void setEntryTopic(String entryTopic) { this.entryTopic = entryTopic; }

    public String getEntryInterface() { return entryInterface; }
    public void setEntryInterface(String entryInterface) { this.entryInterface = entryInterface; }

    public String getEntryMethod() { return entryMethod; }
    public void setEntryMethod(String entryMethod) { this.entryMethod = entryMethod; }

    public Integer getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(Integer nodeCount) {
        this.nodeCount = nodeCount;
    }

    public Integer getSqlCount() {
        return sqlCount;
    }

    public void setSqlCount(Integer sqlCount) {
        this.sqlCount = sqlCount;
    }

    public Integer getRemoteCount() {
        return remoteCount;
    }

    public void setRemoteCount(Integer remoteCount) {
        this.remoteCount = remoteCount;
    }

    public Integer getRedisCount() {
        return redisCount;
    }

    public void setRedisCount(Integer redisCount) {
        this.redisCount = redisCount;
    }

    public Integer getMqCount() {
        return mqCount;
    }

    public void setMqCount(Integer mqCount) {
        this.mqCount = mqCount;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Integer errorCount) {
        this.errorCount = errorCount;
    }

    public Integer getSlowNodeCount() {
        return slowNodeCount;
    }

    public void setSlowNodeCount(Integer slowNodeCount) {
        this.slowNodeCount = slowNodeCount;
    }
}
