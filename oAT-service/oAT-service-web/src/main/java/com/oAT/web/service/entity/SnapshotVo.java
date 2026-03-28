package com.oAT.web.service.entity;

import com.oAT.web.esDao.entity.Snapshot;

import java.io.Serializable;
import java.util.Date;

public class SnapshotVo extends Snapshot implements Serializable {
    private String id;
    private Date createTime;
    private Date updateTime;
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

    public Boolean getDisable() {
        return disable;
    }

    public void setDisable(Boolean disable) {
        this.disable = disable;
    }
}
