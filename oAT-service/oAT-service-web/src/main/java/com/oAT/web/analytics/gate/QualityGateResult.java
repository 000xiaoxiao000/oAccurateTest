package com.oAT.web.analytics.gate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class QualityGateResult implements Serializable {
    private String reportId;
    private String appId;
    private String versionNumber;
    private String repoCommitId;
    private String language;
    private String buildId;
    private String testStage;
    private boolean passed;
    private double minLineCoverageRate;
    private double actualLineCoverageRate;
    private int uncoveredLines;
    private int riskyUnits;
    private List<String> reasons = new ArrayList<>();

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
    public String getRepoCommitId() { return repoCommitId; }
    public void setRepoCommitId(String repoCommitId) { this.repoCommitId = repoCommitId; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }
    public String getTestStage() { return testStage; }
    public void setTestStage(String testStage) { this.testStage = testStage; }
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public double getMinLineCoverageRate() { return minLineCoverageRate; }
    public void setMinLineCoverageRate(double minLineCoverageRate) { this.minLineCoverageRate = minLineCoverageRate; }
    public double getActualLineCoverageRate() { return actualLineCoverageRate; }
    public void setActualLineCoverageRate(double actualLineCoverageRate) { this.actualLineCoverageRate = actualLineCoverageRate; }
    public int getUncoveredLines() { return uncoveredLines; }
    public void setUncoveredLines(int uncoveredLines) { this.uncoveredLines = uncoveredLines; }
    public int getRiskyUnits() { return riskyUnits; }
    public void setRiskyUnits(int riskyUnits) { this.riskyUnits = riskyUnits; }
    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons == null ? new ArrayList<>() : reasons; }
}
