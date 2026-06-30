package com.oAT.web.api.snapshot;

public class SaveSnapshotDirectoryRequest {
    private Integer id;
    private String parentId;
    private String name;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
