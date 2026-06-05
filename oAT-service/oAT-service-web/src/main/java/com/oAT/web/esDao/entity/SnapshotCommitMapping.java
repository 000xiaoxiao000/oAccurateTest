package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

@Document(indexName = "snapshot_commit_mapping", shards = 2)
public class SnapshotCommitMapping implements Serializable, StandardDate {
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss,SSS";

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String snapshotId;

    @Field(type = FieldType.Keyword)
    private String appId;

    @Field(type = FieldType.Keyword)
    private String projectId;

    @Field(type = FieldType.Keyword)
    private String versionNumber;

    @Field(type = FieldType.Keyword)
    private String repoBranch;

    @Field(type = FieldType.Keyword)
    private String repoCommitId;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date createTime;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date snapshotCreateTime;

    @Field(type = FieldType.Keyword)
    private String mappingSource;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(String versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getRepoBranch() {
        return repoBranch;
    }

    public void setRepoBranch(String repoBranch) {
        this.repoBranch = repoBranch;
    }

    public String getRepoCommitId() {
        return repoCommitId;
    }

    public void setRepoCommitId(String repoCommitId) {
        this.repoCommitId = repoCommitId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getSnapshotCreateTime() {
        return snapshotCreateTime;
    }

    public void setSnapshotCreateTime(Date snapshotCreateTime) {
        this.snapshotCreateTime = snapshotCreateTime;
    }

    public String getMappingSource() {
        return mappingSource;
    }

    public void setMappingSource(String mappingSource) {
        this.mappingSource = mappingSource;
    }
}
