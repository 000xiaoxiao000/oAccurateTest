package com.oAT.web.coveragecore.ingest;

public class CoverageAliasIngestResult {
    private String rawReportId;
    private String requestId;
    private String projectId;
    private String appId;
    private String versionNumber;
    private String commitId;
    private String branch;

    public String getRawReportId() { return rawReportId; }
    public void setRawReportId(String rawReportId) { this.rawReportId = rawReportId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
    public String getCommitId() { return commitId; }
    public void setCommitId(String commitId) { this.commitId = commitId; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
}
