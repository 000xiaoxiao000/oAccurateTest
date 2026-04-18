package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

public class Snapshot implements Serializable {

    /*
    名称不能为空
     */
    private String name;
    @Field(type = FieldType.Keyword)
    private String projectId;
    @Field(type = FieldType.Keyword)
    private String appId;

    @Field(type = FieldType.Keyword)
    private String traceId;
    @Field(type = FieldType.Keyword)
    private String[] labels;
    @Field(type = FieldType.Keyword)
    private String createUser;
    @Field(type = FieldType.Text)
    private String describe;
    @Field(type = FieldType.Keyword)
    private Boolean share;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String[] getLabels() {
        return labels;
    }

    public void setLabels(String[] labels) {
        this.labels = labels;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    public String getDescribe() {
        return describe;
    }

    public void setDescribe(String describe) {
        this.describe = describe;
    }

    public Boolean getShare() {
        return share;
    }

    public void setShare(Boolean share) {
        this.share = share;
    }
}
