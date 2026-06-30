package com.oAT.web.language.spi;

import com.oAT.web.coveragecore.model.CoverageLanguage;

public class CoverageIngestMetadata {
    private String projectId;
    private String appId;
    private CoverageLanguage language;
    private String versionNumber;
    private String branch;
    private String commitId;
    private String buildId;
    private String testStage;
    private String caseName;
    private String traceId;
    private Long timestamp;

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public CoverageLanguage getLanguage() { return language; }
    public void setLanguage(CoverageLanguage language) { this.language = language; }
    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getCommitId() { return commitId; }
    public void setCommitId(String commitId) { this.commitId = commitId; }
    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }
    public String getTestStage() { return testStage; }
    public void setTestStage(String testStage) { this.testStage = testStage; }
    public String getCaseName() { return caseName; }
    public void setCaseName(String caseName) { this.caseName = caseName; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
