package com.oAT.web.analytics.tia;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TestImpactCase implements Serializable {
    private String usecaseId;
    private String caseName;
    private String testStage;
    private String buildId;
    private String traceId;
    private int coveredChangedLines;
    private List<String> impactedUnits = new ArrayList<>();

    public String getUsecaseId() { return usecaseId; }
    public void setUsecaseId(String usecaseId) { this.usecaseId = usecaseId; }
    public String getCaseName() { return caseName; }
    public void setCaseName(String caseName) { this.caseName = caseName; }
    public String getTestStage() { return testStage; }
    public void setTestStage(String testStage) { this.testStage = testStage; }
    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public int getCoveredChangedLines() { return coveredChangedLines; }
    public void setCoveredChangedLines(int coveredChangedLines) { this.coveredChangedLines = coveredChangedLines; }
    public List<String> getImpactedUnits() { return impactedUnits; }
    public void setImpactedUnits(List<String> impactedUnits) { this.impactedUnits = impactedUnits == null ? new ArrayList<>() : impactedUnits; }
}
