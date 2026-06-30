package com.oAT.web.coveragecore.ingest;

import com.fasterxml.jackson.databind.JsonNode;

public class FrontendCoverageAliasReportRequest {
    private String requestId;
    private String commitId;
    private String versionNumber;
    private String branch;
    private String caseName;
    private String buildId;
    private String testStage;
    private Long timestamp;
    private JsonNode coverage;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getCommitId() { return commitId; }
    public void setCommitId(String commitId) { this.commitId = commitId; }
    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getCaseName() { return caseName; }
    public void setCaseName(String caseName) { this.caseName = caseName; }
    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }
    public String getTestStage() { return testStage; }
    public void setTestStage(String testStage) { this.testStage = testStage; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    public JsonNode getCoverage() { return coverage; }
    public void setCoverage(JsonNode coverage) { this.coverage = coverage; }
}
