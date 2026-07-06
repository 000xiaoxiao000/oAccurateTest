package com.oAT.web.coveragecore.report;

public class CoverageReportGenerationRequest {
    private String versionNumber;
    private String branch;
    private String commitId;
    private String buildId;
    private String testStage;
    private Integer reportType;
    private String baseVersionNumber;
    private String baseCommitId;

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
    public Integer getReportType() { return reportType; }
    public void setReportType(Integer reportType) { this.reportType = reportType; }
    public String getBaseVersionNumber() { return baseVersionNumber; }
    public void setBaseVersionNumber(String baseVersionNumber) { this.baseVersionNumber = baseVersionNumber; }
    public String getBaseCommitId() { return baseCommitId; }
    public void setBaseCommitId(String baseCommitId) { this.baseCommitId = baseCommitId; }
}
