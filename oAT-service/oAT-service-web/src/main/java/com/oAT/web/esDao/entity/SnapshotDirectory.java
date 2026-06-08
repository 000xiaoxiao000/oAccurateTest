package com.oAT.web.esDao.entity;


import java.io.Serializable;

public class SnapshotDirectory implements Serializable {

    /*
    根目录 即为ROOT
     */
    private String parentId;
    private Integer id;
    private String name;

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public Integer getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }
}
