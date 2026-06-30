package com.oAT.web.api.snapshot;

import java.util.Date;
import java.util.List;

public class SystemSnapshotSummary {
    private String id;
    private String projectId;
    private String appId;
    private String traceId;
    private String title;
    private String subTitle;
    private String topicImage;
    private String describe;
    private String directory;
    private String version;
    private Integer versionCycle;
    private Date versionLastUpdate;
    private String versionLastUpdateText;
    private String versionLastUpdateRelativeText;
    private String versionNumber;
    private String repoBranch;
    private String repoCommitId;
    private List<String> labels;
    private List<String> principals;
    private Integer reportStatus;
    private Date createTime;
    private Date updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubTitle() { return subTitle; }
    public void setSubTitle(String subTitle) { this.subTitle = subTitle; }
    public String getTopicImage() { return topicImage; }
    public void setTopicImage(String topicImage) { this.topicImage = topicImage; }
    public String getDescribe() { return describe; }
    public void setDescribe(String describe) { this.describe = describe; }
    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Integer getVersionCycle() { return versionCycle; }
    public void setVersionCycle(Integer versionCycle) { this.versionCycle = versionCycle; }
    public Date getVersionLastUpdate() { return versionLastUpdate; }
    public void setVersionLastUpdate(Date versionLastUpdate) { this.versionLastUpdate = versionLastUpdate; }
    public String getVersionLastUpdateText() { return versionLastUpdateText; }
    public void setVersionLastUpdateText(String versionLastUpdateText) { this.versionLastUpdateText = versionLastUpdateText; }
    public String getVersionLastUpdateRelativeText() { return versionLastUpdateRelativeText; }
    public void setVersionLastUpdateRelativeText(String versionLastUpdateRelativeText) { this.versionLastUpdateRelativeText = versionLastUpdateRelativeText; }
    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
    public String getRepoBranch() { return repoBranch; }
    public void setRepoBranch(String repoBranch) { this.repoBranch = repoBranch; }
    public String getRepoCommitId() { return repoCommitId; }
    public void setRepoCommitId(String repoCommitId) { this.repoCommitId = repoCommitId; }
    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }
    public List<String> getPrincipals() { return principals; }
    public void setPrincipals(List<String> principals) { this.principals = principals; }
    public Integer getReportStatus() { return reportStatus; }
    public void setReportStatus(Integer reportStatus) { this.reportStatus = reportStatus; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
