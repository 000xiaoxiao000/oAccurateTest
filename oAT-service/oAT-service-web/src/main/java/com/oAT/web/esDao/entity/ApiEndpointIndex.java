package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

@Document(indexName = "api_endpoint_index", shards = 2)
public class ApiEndpointIndex implements Serializable {
    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String appId;

    @Field(type = FieldType.Keyword)
    private String sourceType;

    @Field(type = FieldType.Keyword)
    private String sourceName;

    @Field(type = FieldType.Keyword)
    private String sourceNames;

    @Field(type = FieldType.Keyword)
    private String sourceTypeNames;

    @Field(type = FieldType.Keyword)
    private String endpointType;

    @Field(type = FieldType.Keyword)
    private String url;

    @Field(type = FieldType.Keyword)
    private String httpMethod;

    @Field(type = FieldType.Keyword)
    private String className;

    @Field(type = FieldType.Keyword)
    private String classNames;

    @Field(type = FieldType.Keyword)
    private String methodName;

    @Field(type = FieldType.Keyword)
    private String methodNames;

    @Field(type = FieldType.Keyword)
    private String methodDesc;

    @Field(type = FieldType.Keyword)
    private String methodDescs;

    @Field(type = FieldType.Keyword)
    private String coverageStatus;

    @Field(type = FieldType.Boolean)
    private Boolean covered;

    @Field(type = FieldType.Integer)
    private Integer hitCount;

    @Field(type = FieldType.Integer)
    private Integer mergedSourceCount;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date createTime;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceNames() { return sourceNames; }
    public void setSourceNames(String sourceNames) { this.sourceNames = sourceNames; }
    public String getSourceTypeNames() { return sourceTypeNames; }
    public void setSourceTypeNames(String sourceTypeNames) { this.sourceTypeNames = sourceTypeNames; }
    public String getEndpointType() { return endpointType; }
    public void setEndpointType(String endpointType) { this.endpointType = endpointType; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getClassNames() { return classNames; }
    public void setClassNames(String classNames) { this.classNames = classNames; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getMethodNames() { return methodNames; }
    public void setMethodNames(String methodNames) { this.methodNames = methodNames; }
    public String getMethodDesc() { return methodDesc; }
    public void setMethodDesc(String methodDesc) { this.methodDesc = methodDesc; }
    public String getMethodDescs() { return methodDescs; }
    public void setMethodDescs(String methodDescs) { this.methodDescs = methodDescs; }
    public String getCoverageStatus() { return coverageStatus; }
    public void setCoverageStatus(String coverageStatus) { this.coverageStatus = coverageStatus; }
    public Boolean getCovered() { return covered; }
    public void setCovered(Boolean covered) { this.covered = covered; }
    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }
    public Integer getMergedSourceCount() { return mergedSourceCount; }
    public void setMergedSourceCount(Integer mergedSourceCount) { this.mergedSourceCount = mergedSourceCount; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
