package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

@Document(indexName = "version_center", shards = 2)
public class VersionCenterIndex implements Serializable, StandardDate {
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
    VersionItem versionItem;

    @Field(type = FieldType.Object)
    VersionCompareReport compareReport;

    @Deprecated
    public VersionCenterIndex() {

    }

    public VersionCenterIndex(VersionItem versionItem) {
        this.versionItem = versionItem;
        this.type = "versionItem";
        createTime = new Date();
        updateTime = new Date();
    }

    public VersionCenterIndex(VersionCompareReport compareReport) {
        this.compareReport = compareReport;
        this.type = "compareReport";
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

    public VersionItem getVersionItem() {
        return versionItem;
    }

    public void setVersionItem(VersionItem versionItem) {
        this.versionItem = versionItem;
    }

    public VersionCompareReport getCompareReport() {
        return compareReport;
    }

    public void setCompareReport(VersionCompareReport compareReport) {
        this.compareReport = compareReport;
    }
}
