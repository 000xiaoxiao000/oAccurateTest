package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

public class VersionItem implements Serializable {
    @Field(type = FieldType.Keyword)
    private String appId;
    @Field(type = FieldType.Keyword)
    private String projectId;
    @Field(type = FieldType.Keyword)
    private String versionNumber;
    @Field(type = FieldType.Text)
    private String describe;
    @Field(type = FieldType.Keyword)
    private String programFile;
    @Field(type = FieldType.Keyword)
    private String[] configFile;
    @Field(type = FieldType.Keyword)
    private String[] databaseFile;

    @Field(type = FieldType.Keyword)
    private String sourceType;

    @Field(type = FieldType.Keyword)
    private String repoBranch;

    @Field(type = FieldType.Keyword)
    private String repoCommitId;

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

    public String getDescribe() {
        return describe;
    }

    public void setDescribe(String describe) {
        this.describe = describe;
    }

    public String getProgramFile() {
        return programFile;
    }

    public void setProgramFile(String programFile) {
        this.programFile = programFile;
    }

    public String[] getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String[] configFile) {
        this.configFile = configFile;
    }

    public String[] getDatabaseFile() {
        return databaseFile;
    }

    public void setDatabaseFile(String[] databaseFile) {
        this.databaseFile = databaseFile;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
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
}
