package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 用例中心索引
 */
@Document(indexName = "case_center", shards = 2)
public class CaseCenterIndex implements StandardDate {
    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private String type;
    @Field(type = FieldType.Date, pattern = dateFormat, format = DateFormat.custom)
    private String createTime;
    @Field(type = FieldType.Date, pattern = dateFormat, format = DateFormat.custom)
    private String updateTime;

    //实体对象=================================================================
    @Field(type = FieldType.Object)
    Snapshot snapshot;
    @Field(type = FieldType.Object)
    Usecase usecase;
    @Field(type = FieldType.Object)
    UsecaseDirectory directory;

    @Deprecated
    public CaseCenterIndex() {
    }

    public CaseCenterIndex(Snapshot snapshot) {
        this.snapshot = snapshot;
        this.type = "snapshot";
        createTime = currentTimeToString();
        updateTime = currentTimeToString();

    }

    public CaseCenterIndex(Usecase usecase) {
        this.usecase = usecase;
        this.type = "usecase";
        createTime = currentTimeToString();
        updateTime = currentTimeToString();
    }

    public CaseCenterIndex(UsecaseDirectory directory) {
        this.directory = directory;
        this.type = "directory";
        createTime = currentTimeToString();
        updateTime = currentTimeToString();
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
