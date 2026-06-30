package com.oAT.web.coveragecore.model;

import java.io.Serializable;

public class CoverageFootprint implements Serializable {
    private String traceId;
    private String caseName;
    private String testStage;
    private String buildId;
    private Long timestamp;

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getCaseName() { return caseName; }
    public void setCaseName(String caseName) { this.caseName = caseName; }
    public String getTestStage() { return testStage; }
    public void setTestStage(String testStage) { this.testStage = testStage; }
    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
