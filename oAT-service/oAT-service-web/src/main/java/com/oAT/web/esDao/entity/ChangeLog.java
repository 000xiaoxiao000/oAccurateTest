package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

public class ChangeLog implements Serializable,StandardDate {
    @Field(type = FieldType.Keyword)
    private String userId;
    @Field(type = FieldType.Date, pattern = dateFormat, format = DateFormat.custom)
    private String time;
    @Field(type = FieldType.Keyword)
    private String type;
    @Field(type = FieldType.Text)
    private String content;

    public ChangeLog(String userId, String time, String type, String content) {
        this.userId = userId;
        this.time = time;
        this.type = type;
        this.content = content;
    }

    public ChangeLog() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
