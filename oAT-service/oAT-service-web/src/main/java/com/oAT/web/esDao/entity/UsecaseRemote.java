package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

public class UsecaseRemote implements Serializable {
    @Field(type = FieldType.Text)
    /*
    远程调用原内容
     */
    private String[] content;
    @Field(type = FieldType.Keyword)
    /*
    dubbo远程调用 className.method
     */
    private String[] dubbo;
    @Field(type = FieldType.Keyword)
    /*
    http 远程调用 格式 host/uri
     */
    private String[] http;

    public String[] getDubbo() {
        return dubbo;
    }

    public void setDubbo(String[] dubbo) {
        this.dubbo = dubbo;
    }

    public String[] getHttp() {
        return http;
    }

    public void setHttp(String[] http) {
        this.http = http;
    }

    public String[] getContent() {
        return content;
    }

    public void setContent(String[] content) {
        this.content = content;
    }
}