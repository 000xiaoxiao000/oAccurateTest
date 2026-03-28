package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

public class ProjectMember implements Serializable {
    @Field(type = FieldType.Keyword)
    private String projectId;

    @Field(type = FieldType.Keyword)
    private String memberId; // 用户ID

    @Field(type = FieldType.Keyword)
    private String role;// 权限角色

    @Field(type = FieldType.Boolean)
    private Boolean star;//是否为该用户收藏项目

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public ProjectMember() {
    }

    public ProjectMember(String projectId, String memberId, String role) {
        this.projectId = projectId;
        this.memberId = memberId;
        this.role = role;
        star=false;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }


    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public Boolean getStar() {
        return star;
    }

    public void setStar(Boolean star) {
        this.star = star;
    }

}
