package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

@Document(indexName = "coverage_report", type = "doc", shards = 2)
public class CoverageReportIndex implements Serializable {
    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private String appId;
    @Field(type = FieldType.Keyword)
    private String versionNumber;
    @Field(type = FieldType.Keyword)
    private String repoBranch;
    @Field(type = FieldType.Keyword)
    private String repoCommitId;
    @Field(type = FieldType.Date)
    private Date createTime;

    @Field(type = FieldType.Keyword)
    private String lastProcessedTime;

    // Summary Statistics
    private long totalClasses;
    private long coveredClasses;
    private long totalMethods;
    private long coveredMethods;
    private long totalBranches;
    private long coveredBranches;
    private long totalLines;
    private long coveredLines;
    private int totalComplexity;

    @Field(type = FieldType.Integer)
    private Integer reportType; // 0: Full, 1: Incremental

    @Field(type = FieldType.Keyword)
    private String baseVersionNumber;

    @Field(type = FieldType.Keyword)
    private String baseRepoCommitId;

    @Field(type = FieldType.Keyword)
    private String snapshotFingerprint;

    @Field(type = FieldType.Keyword)
    private String snapshotLastUpdateTime;

    @Field(type = FieldType.Integer)
    private Integer snapshotCount;

    @Field(type = FieldType.Text)
    private String snapshotIds;

    // Incremental Summary
    private long incTotalClasses;
    private long incCoveredClasses;
    private long incTotalLines;
    private long incCoveredLines;
    private long incTotalMethods;
    private long incCoveredMethods;
    private long incTotalBranches;
    private long incCoveredBranches;
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

    public int getIncTotalComplexity() { return incTotalComplexity; }
    public void setIncTotalComplexity(int incTotalComplexity) { this.incTotalComplexity = incTotalComplexity; }
}
