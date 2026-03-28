package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

public class App implements Serializable {

    /**
     * 名称
     */
    @Field(type = FieldType.Keyword)
    private String name;

    /**
     * 范围
     */
    @Field(type = FieldType.Keyword)
    private String range;

    /**
     * 源码工程名称
     */
    @Field(type = FieldType.Keyword)
    private String srcName;

    /**
     * 创建用户ID
     */
    @Field(type = FieldType.Keyword)
    private String createUserId;

    /**
     * 创建项目
     */
    @Field(type = FieldType.Keyword)
    private String createProjectId;

    /**
     * 描述
     */
    @Field(type = FieldType.Text)
    private String describe;

    /**
     * 属性配置
     */
    @Field(type = FieldType.Text)
    private String properties;

    /**
     * 当前版本号
     */
    @Field(type = FieldType.Keyword)
    private String currentVersion;

    /**
     * 当前分支
     */
    @Field(type = FieldType.Keyword)
    private String currentBranch;

    /**
     * 当前CommitId
     */
    @Field(type = FieldType.Keyword)
    private String currentCommitId;


    /**
     * 代码仓库配置
     */
    @Field(type = FieldType.Keyword)
    private String repoAddress;

    @Field(type = FieldType.Keyword)
    private String repoUserName;

    @Field(type = FieldType.Keyword)
    private String repoPassword;


    /**
     * 快照目录
     */
    @Field(type = FieldType.Object)
    private SnapshotDirectory[] snapshotDirs;

    // ============ getter/setter ============

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range;
    }

    public String getSrcName() {
        return srcName;
    }

    public void setSrcName(String srcName) {
        this.srcName = srcName;
    }

    public String getCreateUserId() {
        return createUserId;
    }

    public void setCreateUserId(String createUserId) {
        this.createUserId = createUserId;
    }

    public String getCreateProjectId() {
        return createProjectId;
    }

    public void setCreateProjectId(String createProjectId) {
        this.createProjectId = createProjectId;
    }

    public String getDescribe() {
        return describe;
    }

    public void setDescribe(String describe) {
        this.describe = describe;
    }

    public String getRepoAddress() {
        return repoAddress;
    }

    public void setRepoAddress(String repoAddress) {
        this.repoAddress = repoAddress;
    }

    public String getRepoUserName() {
        return repoUserName;
    }

    public void setRepoUserName(String repoUserName) {
        this.repoUserName = repoUserName;
    }

    public String getRepoPassword() {
        return repoPassword;
    }

    public void setRepoPassword(String repoPassword) {
        this.repoPassword = repoPassword;
    }


    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getCurrentBranch() {
        return currentBranch;
    }

    public void setCurrentBranch(String currentBranch) {
        this.currentBranch = currentBranch;
    }

    public String getCurrentCommitId() {
        return currentCommitId;
    }

    public void setCurrentCommitId(String currentCommitId) {
        this.currentCommitId = currentCommitId;
    }

    public SnapshotDirectory[] getSnapshotDirs() {
        return snapshotDirs;
    }

    public void setSnapshotDirs(SnapshotDirectory[] snapshotDirs) {
        this.snapshotDirs = snapshotDirs;
    }

    public enum Range {
        only,   //只属于指定项目
        all // 属于所有项目
    }

}
