package com.oAT.web.service.entity;

import com.oAT.web.esDao.entity.UsecaseDirectory;
import com.oAT.web.esDao.entity.UsecaseRemote;
import com.oAT.web.esDao.entity.UsecaseSql;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

public class UsecaseVo implements Serializable {
    private String id;
    private String projectId;
    // 标题
    private String title;
    // 标题图
    private String headImage;
    // 内容
    private String content;
    // 目录
    private String directory;
    // 绑定的快照id
    private String snapshots[];
    //  标签
    private String labels[];
    //  作者
    private String authors[];
    // 最后更新作者
    private String lastUpdateAuthor;
    private Date createTime;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHeadImage() {
        return headImage;
    }

    public void setHeadImage(String headImage) {
        this.headImage = headImage;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String[] getSnapshots() {
        return snapshots;
    }

    public void setSnapshots(String[] snapshots) {
        this.snapshots = snapshots;
    }

    public String[] getLabels() {
        return labels;
    }

    public void setLabels(String[] labels) {
        this.labels = labels;
    }

    public String[] getAuthors() {
        return authors;
    }

    public void setAuthors(String[] authors) {
        this.authors = authors;
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

    public String getLastUpdateAuthor() {
        return lastUpdateAuthor;
    }

    public void setLastUpdateAuthor(String lastUpdateAuthor) {
        this.lastUpdateAuthor = lastUpdateAuthor;
    }

}
