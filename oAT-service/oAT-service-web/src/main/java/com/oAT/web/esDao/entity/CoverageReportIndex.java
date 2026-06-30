package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;

import java.io.Serializable;
import java.util.Date;

public class CoverageReportIndex implements Serializable {
    @Id
    private String id;
    private String appId;
    private String versionNumber;
    private String repoBranch;
    private String repoCommitId;
    private Date createTime;

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

    private Integer reportType;

    private String baseVersionNumber;

    private String baseRepoCommitId;

    private String snapshotFingerprint;

    private String snapshotLastUpdateTime;

    private Integer snapshotCount;

    private String snapshotIds;

    private long incTotalClasses;
    private long incCoveredClasses;
    private long incTotalLines;
    private long incCoveredLines;
    private long incTotalMethods;
    private long incCoveredMethods;
    private long incTotalBranches;
    private long incCoveredBranches;
    private long incTotalBranchTargets;
    private long incCoveredBranchTargets;
    private int incTotalComplexity;

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
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
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

    public Integer getReportType() { return reportType; }
    public void setReportType(Integer reportType) { this.reportType = reportType; }
    public String getBaseVersionNumber() { return baseVersionNumber; }
    public void setBaseVersionNumber(String baseVersionNumber) { this.baseVersionNumber = baseVersionNumber; }
    public String getBaseRepoCommitId() { return baseRepoCommitId; }
    public void setBaseRepoCommitId(String baseRepoCommitId) { this.baseRepoCommitId = baseRepoCommitId; }
    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public void setSnapshotFingerprint(String snapshotFingerprint) { this.snapshotFingerprint = snapshotFingerprint; }
    public String getSnapshotLastUpdateTime() { return snapshotLastUpdateTime; }
    public void setSnapshotLastUpdateTime(String snapshotLastUpdateTime) { this.snapshotLastUpdateTime = snapshotLastUpdateTime; }
    public Integer getSnapshotCount() { return snapshotCount; }
    public void setSnapshotCount(Integer snapshotCount) { this.snapshotCount = snapshotCount; }
    public String getSnapshotIds() { return snapshotIds; }
    public void setSnapshotIds(String snapshotIds) { this.snapshotIds = snapshotIds; }
    public long getIncTotalClasses() { return incTotalClasses; }
    public void setIncTotalClasses(long incTotalClasses) { this.incTotalClasses = incTotalClasses; }
    public long getIncCoveredClasses() { return incCoveredClasses; }
    public void setIncCoveredClasses(long incCoveredClasses) { this.incCoveredClasses = incCoveredClasses; }
    public long getIncTotalLines() { return incTotalLines; }
    public void setIncTotalLines(long incTotalLines) { this.incTotalLines = incTotalLines; }
    public long getIncCoveredLines() { return incCoveredLines; }
    public void setIncCoveredLines(long incCoveredLines) { this.incCoveredLines = incCoveredLines; }
    public long getIncTotalMethods() { return incTotalMethods; }
    public void setIncTotalMethods(long incTotalMethods) { this.incTotalMethods = incTotalMethods; }
    public long getIncCoveredMethods() { return incCoveredMethods; }
    public void setIncCoveredMethods(long incCoveredMethods) { this.incCoveredMethods = incCoveredMethods; }
    public long getIncTotalBranches() { return incTotalBranches; }
    public void setIncTotalBranches(long incTotalBranches) { this.incTotalBranches = incTotalBranches; }
    public long getIncCoveredBranches() { return incCoveredBranches; }
    public void setIncCoveredBranches(long incCoveredBranches) { this.incCoveredBranches = incCoveredBranches; }
    public long getIncTotalBranchTargets() { return incTotalBranchTargets; }
    public void setIncTotalBranchTargets(long incTotalBranchTargets) { this.incTotalBranchTargets = incTotalBranchTargets; }
    public long getIncCoveredBranchTargets() { return incCoveredBranchTargets; }
    public void setIncCoveredBranchTargets(long incCoveredBranchTargets) { this.incCoveredBranchTargets = incCoveredBranchTargets; }
    public int getIncTotalComplexity() { return incTotalComplexity; }
    public void setIncTotalComplexity(int incTotalComplexity) { this.incTotalComplexity = incTotalComplexity; }
}
