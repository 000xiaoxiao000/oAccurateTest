package com.oAT.web.coveragecore.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.oAT.web.coveragecore.model.CoverageLanguage;

public class CoverageRawIngestRequest {
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
    private String requestId;
    private Long timestamp;
    private JsonNode payload;
    private String originalBody;

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
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
    public String getOriginalBody() { return originalBody; }
    public void setOriginalBody(String originalBody) { this.originalBody = originalBody; }
}
