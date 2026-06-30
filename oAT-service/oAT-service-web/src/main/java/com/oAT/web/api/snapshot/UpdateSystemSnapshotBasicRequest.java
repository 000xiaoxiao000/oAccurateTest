package com.oAT.web.api.snapshot;

import java.util.List;

public class UpdateSystemSnapshotBasicRequest {
    private String title;
    private String describe;
    private String version;
    private Integer versionCycle;
    private List<String> labels;
    private List<String> principals;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescribe() { return describe; }
    public void setDescribe(String describe) { this.describe = describe; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Integer getVersionCycle() { return versionCycle; }
    public void setVersionCycle(Integer versionCycle) { this.versionCycle = versionCycle; }
    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }
    public List<String> getPrincipals() { return principals; }
    public void setPrincipals(List<String> principals) { this.principals = principals; }
}
