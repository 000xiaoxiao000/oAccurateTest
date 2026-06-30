package com.oAT.web.api.snapshot;

import java.util.List;

public class SaveAsSystemSnapshotRequest {
    private String appId;
    private String directory;
    private String title;
    private String describe;
    private String topicImage;
    private Integer versionCycle;
    private List<String> labels;
    private List<String> principals;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescribe() { return describe; }
    public void setDescribe(String describe) { this.describe = describe; }
    public String getTopicImage() { return topicImage; }
    public void setTopicImage(String topicImage) { this.topicImage = topicImage; }
    public Integer getVersionCycle() { return versionCycle; }
    public void setVersionCycle(Integer versionCycle) { this.versionCycle = versionCycle; }
    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }
    public List<String> getPrincipals() { return principals; }
    public void setPrincipals(List<String> principals) { this.principals = principals; }
}
