package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

/**
 * 用例中心索引
 */
@Document(indexName = "case_center", shards = 2)
public class CaseCenterIndex implements StandardDate {
    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private String type;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date createTime;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date updateTime;

    //实体对象=================================================================
    @Field(type = FieldType.Object)
    Snapshot snapshot;
    @Field(type = FieldType.Object)
    Usecase usecase;
    @Field(type = FieldType.Object)
    UsecaseDirectory directory;

    public CaseCenterIndex() {
    }

    public CaseCenterIndex(Snapshot snapshot) {
        this.snapshot = snapshot;
        this.type = "snapshot";
        createTime = new Date();
        updateTime = new Date();

    }

    public CaseCenterIndex(Usecase usecase) {
        this.usecase = usecase;
        this.type = "usecase";
        createTime = new Date();
        updateTime = new Date();
    }

    public CaseCenterIndex(UsecaseDirectory directory) {
        this.directory = directory;
        this.type = "directory";
        createTime = new Date();
        updateTime = new Date();
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

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    public Usecase getUsecase() {
        return usecase;
    }

    public void setUsecase(Usecase usecase) {
        this.usecase = usecase;
    }

    public UsecaseDirectory getDirectory() {
        return directory;
    }

    public void setDirectory(UsecaseDirectory directory) {
        this.directory = directory;
    }

}
