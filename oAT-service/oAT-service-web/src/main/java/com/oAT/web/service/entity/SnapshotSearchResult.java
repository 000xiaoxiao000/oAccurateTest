package com.oAT.web.service.entity;

import com.oAT.web.esDao.entity.StandardDate;

import java.io.Serializable;
import java.util.Date;

public class SnapshotSearchResult implements Serializable, StandardDate {
    private String id;
    private String projectId;
    private String appId;
    private String directoryId;
    private String directoryPath;
    // 标题
    private String title;
    // 副标题
    private String subTitle;
    //  主题 图片
    private String headImage;
    // 标题高亮片段
    private String titleFragment;
    // 高亮片段
    private String describeFragments[];
    // sql 高亮片段
    private String sqlContentFragments[];
    // 远程调用高亮片段
    private String remoteContentFragments[];
    private Date updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getDirectoryId() {
        return directoryId;
    }

    public void setDirectoryId(String directoryId) {
        this.directoryId = directoryId;
    }

    public String getDirectoryPath() {
        return directoryPath;
    }

    public void setDirectoryPath(String directoryPath) {
        this.directoryPath = directoryPath;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubTitle() {
        return subTitle;
    }

    public void setSubTitle(String subTitle) {
        this.subTitle = subTitle;
    }

    public String getHeadImage() {
        return headImage;
    }

    public void setHeadImage(String headImage) {
        this.headImage = headImage;
    }

    public String getTitleFragment() {
        return titleFragment;
    }

    public void setTitleFragment(String titleFragment) {
        this.titleFragment = titleFragment;
    }

    public String[] getDescribeFragments() {
        return describeFragments;
    }

    public void setDescribeFragments(String[] describeFragments) {
        this.describeFragments = describeFragments;
    }

    public String[] getSqlContentFragments() {
        return sqlContentFragments;
    }

    public void setSqlContentFragments(String[] sqlContentFragments) {
        this.sqlContentFragments = sqlContentFragments;
    }

    public String[] getRemoteContentFragments() {
        return remoteContentFragments;
    }

    public void setRemoteContentFragments(String[] remoteContentFragments) {
        this.remoteContentFragments = remoteContentFragments;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
