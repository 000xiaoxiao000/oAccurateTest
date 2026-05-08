package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

@Document(indexName = "client", shards = 2)
public class ClientIndex implements Serializable, StandardDate {
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss,SSS";
    //基础属性 ========================================
    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private String type;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date createTime;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date updateTime;

    public ClientIndex() {
    }

    public ClientIndex(ClientSession session) {
        this.session = session;
        this.type = "session";
        createTime = new Date();
        updateTime = new Date();
    }

    //实体对象=================================================================
    @Field(type = FieldType.Object)
    private ClientSession session;


    public static String getDateFormat() {
        return DATE_FORMAT;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public ClientSession getSession() {
        return session;
    }

    public void setSession(ClientSession session) {
        this.session = session;
    }

    public String getSessionId() {
        return session != null ? this.id : null;
    }
}
