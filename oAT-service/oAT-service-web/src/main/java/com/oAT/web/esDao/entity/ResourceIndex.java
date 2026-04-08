package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

@Document(indexName = "resources", shards = 2)
public class ResourceIndex implements Serializable, StandardDate {
    @Id
    private String id; // 内容的md5
    @Field(type = FieldType.Text, index = false)
    private byte[] content;
    @Field(type = FieldType.Keyword)
    private long contentLength;// 内容大小
    private int referenceCount;// 引用计数
    @Field(type = FieldType.Date, format = {})
    private Date createTime;
    @Field(type = FieldType.Date, format = {})
    private Date updateTime;

    public ResourceIndex() {
    }

    /**
     * 构建新的资源
     *
     * @param content
     */
    public ResourceIndex(byte[] content) {
        this.content = content;
        this.contentLength = content.length;
        this.createTime = new Date();
        this.updateTime = new Date();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    public void setReferenceCount(int referenceCount) {
        this.referenceCount = referenceCount;
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
}
