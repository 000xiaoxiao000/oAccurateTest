package com.oAT.web.api.snapshot;

import java.util.Date;

public class CoverageReportSummary {
    private String id;
    private String appId;
    private String versionNumber;
    private String repoBranch;
    private String repoCommitId;
    private Date createTime;
    private String createTimeText;
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
    public String getCreateTimeText() { return createTimeText; }
    public void setCreateTimeText(String createTimeText) { this.createTimeText = createTimeText; }
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
}
