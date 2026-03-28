package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

public class Comment implements Serializable, StandardDate {
    @Field(type = FieldType.Keyword)
    private String userId;
    @Field(type = FieldType.Keyword)
    private String time;
    @Field(type = FieldType.Keyword)
    private String content;
    @Field(type = FieldType.Object)
    private Replie replies[];

    // ============ getter/setter ============

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Replie[] getReplies() {
        return replies;
    }

    public void setReplies(Replie[] replies) {
        this.replies = replies;
    }


    public static class Replie implements Serializable, StandardDate {
        @Field(type = FieldType.Keyword)
        private String userId;
        @Field(type = FieldType.Keyword)
        private String time;
        @Field(type = FieldType.Keyword)
        private String content;

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

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
