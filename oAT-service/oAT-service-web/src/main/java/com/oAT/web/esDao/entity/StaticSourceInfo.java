package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 静态源码数据索引
 */
@Document(indexName = "static_source_info", type = "doc", shards = 2)
public class StaticSourceInfo implements StandardDate {
    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private String appId;
    @Field(type = FieldType.Keyword)
    private String type;
    @Field(type = FieldType.Date, pattern = dateFormat, format = DateFormat.custom)
    private String createTime;
    @Field(type = FieldType.Date, pattern = dateFormat, format = DateFormat.custom)
    private String updateTime;

    // 实体对象
    @Field(type = FieldType.Object)
    private StaticSourceClassInfo classInfo;

    public StaticSourceInfo() {
    }

    public StaticSourceInfo(StaticSourceClassInfo classInfo) {
        this.classInfo = classInfo;
        this.type = "classInfo";
        createTime = currentTimeToString();
        updateTime = currentTimeToString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public StaticSourceClassInfo getClassInfo() {
        return classInfo;
    }

    public void setClassInfo(StaticSourceClassInfo classInfo) {
        this.classInfo = classInfo;
    }
}
