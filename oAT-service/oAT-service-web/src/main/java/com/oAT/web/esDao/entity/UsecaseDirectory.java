package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

public class UsecaseDirectory implements Serializable {

    /*
    根目录 即为ROOT
     */
    @Field(type = FieldType.Keyword)
    private String parentId;
    @Field(type = FieldType.Keyword)
    private String projectId;
    @Field(type = FieldType.Keyword)
    private String name;
    @Field(type = FieldType.Keyword)
    private String[] childId;

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getChildId() {
        return childId;
    }

    public void setChildId(String[] childId) {
        this.childId = childId;
    }
}