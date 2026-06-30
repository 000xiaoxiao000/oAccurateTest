package com.oAT.web.coveragecore.report;

import java.io.Serializable;

public class CoverageReportSummary implements Serializable {
    private String id;
    private String appId;
    private String versionNumber;
    private String repoBranch;
    private String repoCommitId;
    private String createTimeText;
    private String sourceType;
    private String language;
    private String buildId;
    private String testStage;
    private String lastProcessedTime;
    private long totalClasses;
    private long coveredClasses;
    private long totalMethods;
    private long coveredMethods;
    private long totalBranches;
    private long coveredBranches;
    private long totalBranchTargets;
    private long coveredBranchTargets;
    private long totalLines;
    private long coveredLines;
    private int totalComplexity;
    private Integer snapshotCount;
    private Integer reportType;
    private String baseVersionNumber;
    private String baseRepoCommitId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
    public String getRepoBranch() { return repoBranch; }
    public void setRepoBranch(String repoBranch) { this.repoBranch = repoBranch; }
    public String getRepoCommitId() { return repoCommitId; }
    public void setRepoCommitId(String repoCommitId) { this.repoCommitId = repoCommitId; }
    public String getCreateTimeText() { return createTimeText; }
    public void setCreateTimeText(String createTimeText) { this.createTimeText = createTimeText; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }
    public String getTestStage() { return testStage; }
    public void setTestStage(String testStage) { this.testStage = testStage; }
    public String getLastProcessedTime() { return lastProcessedTime; }
    public void setLastProcessedTime(String lastProcessedTime) { this.lastProcessedTime = lastProcessedTime; }
    public long getTotalClasses() { return totalClasses; }
    public void setTotalClasses(long totalClasses) { this.totalClasses = totalClasses; }
    public long getCoveredClasses() { return coveredClasses; }
    public void setCoveredClasses(long coveredClasses) { this.coveredClasses = coveredClasses; }
    public long getTotalMethods() { return totalMethods; }
    public void setTotalMethods(long totalMethods) { this.totalMethods = totalMethods; }
    public long getCoveredMethods() { return coveredMethods; }
    public void setCoveredMethods(long coveredMethods) { this.coveredMethods = coveredMethods; }
    public long getTotalBranches() { return totalBranches; }
    public void setTotalBranches(long totalBranches) { this.totalBranches = totalBranches; }
    public long getCoveredBranches() { return coveredBranches; }
    public void setCoveredBranches(long coveredBranches) { this.coveredBranches = coveredBranches; }
    public long getTotalBranchTargets() { return totalBranchTargets; }
    public void setTotalBranchTargets(long totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
    public long getCoveredBranchTargets() { return coveredBranchTargets; }
    public void setCoveredBranchTargets(long coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
    public long getTotalLines() { return totalLines; }
    public void setTotalLines(long totalLines) { this.totalLines = totalLines; }
    public long getCoveredLines() { return coveredLines; }
    public void setCoveredLines(long coveredLines) { this.coveredLines = coveredLines; }
    public int getTotalComplexity() { return totalComplexity; }
    public void setTotalComplexity(int totalComplexity) { this.totalComplexity = totalComplexity; }
    public Integer getSnapshotCount() { return snapshotCount; }
    public void setSnapshotCount(Integer snapshotCount) { this.snapshotCount = snapshotCount; }
    public Integer getReportType() { return reportType; }
    public void setReportType(Integer reportType) { this.reportType = reportType; }
    public String getBaseVersionNumber() { return baseVersionNumber; }
    public void setBaseVersionNumber(String baseVersionNumber) { this.baseVersionNumber = baseVersionNumber; }
    public String getBaseRepoCommitId() { return baseRepoCommitId; }
    public void setBaseRepoCommitId(String baseRepoCommitId) { this.baseRepoCommitId = baseRepoCommitId; }
}
