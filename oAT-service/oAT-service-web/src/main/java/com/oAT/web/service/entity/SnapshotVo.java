package com.oAT.web.service.entity;

import com.oAT.web.esDao.entity.Snapshot;

import java.io.Serializable;
import java.util.Date;

public class SnapshotVo extends Snapshot implements Serializable {
    private String id;
    private Date createTime;
    private Date updateTime;
    private String createTimeText;
    private String updateTimeText;
    private String updateTimeRelativeText;
    private Boolean disable;

    public SnapshotVo() {
    }

    public SnapshotVo(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getCreateTimeText() {
        return createTimeText;
    }

    public void setCreateTimeText(String createTimeText) {
        this.createTimeText = createTimeText;
    }

    public String getUpdateTimeText() {
        return updateTimeText;
    }

    public void setUpdateTimeText(String updateTimeText) {
        this.updateTimeText = updateTimeText;
    }

    public String getUpdateTimeRelativeText() {
        return updateTimeRelativeText;
    }

    public void setUpdateTimeRelativeText(String updateTimeRelativeText) {
        this.updateTimeRelativeText = updateTimeRelativeText;
    }

    public Boolean getDisable() {
        return disable;
    }

    public void setDisable(Boolean disable) {
        this.disable = disable;
    }
}
