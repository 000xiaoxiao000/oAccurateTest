package com.oAT.web.coveragecore.model;

import com.oAT.web.esDao.entity.CoverageReportIndex;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BuildSession implements Serializable {
    private String buildId;
    private String appId;
    private String versionNumber;
    private String repoBranch;
    private String repoCommitId;
    private Date firstReportTime;
    private Date lastReportTime;
    private List<String> testStages = new ArrayList<>();
    private List<String> reportIds = new ArrayList<>();
    private long totalReports;
    private long totalLines;
    private long coveredLines;
    private long totalMethods;
    private long coveredMethods;
    private long totalBranchTargets;
    private long coveredBranchTargets;

    public static BuildSession fromReport(CoverageReportIndex report) {
        BuildSession session = new BuildSession();
        session.setBuildId(report.getBuildId());
        session.setAppId(report.getAppId());
        session.setVersionNumber(report.getVersionNumber());
        session.setRepoBranch(report.getRepoBranch());
        session.setRepoCommitId(report.getRepoCommitId());
        session.merge(report);
        return session;
    }

    public void merge(CoverageReportIndex report) {
        if (report == null) {
            return;
        }
        if (firstReportTime == null || (report.getCreateTime() != null && report.getCreateTime().before(firstReportTime))) {
            firstReportTime = report.getCreateTime();
        }
        if (lastReportTime == null || (report.getCreateTime() != null && report.getCreateTime().after(lastReportTime))) {
            lastReportTime = report.getCreateTime();
        }
        if (report.getTestStage() != null && !testStages.contains(report.getTestStage())) {
            testStages.add(report.getTestStage());
        }
        if (report.getId() != null && !reportIds.contains(report.getId())) {
            reportIds.add(report.getId());
        }
        totalReports++;
        totalLines += report.getTotalLines();
        coveredLines += report.getCoveredLines();
        totalMethods += report.getTotalMethods();
        coveredMethods += report.getCoveredMethods();
        totalBranchTargets += report.getTotalBranchTargets();
        coveredBranchTargets += report.getCoveredBranchTargets();
    }

    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
    public String getRepoBranch() { return repoBranch; }
    public void setRepoBranch(String repoBranch) { this.repoBranch = repoBranch; }
    public String getRepoCommitId() { return repoCommitId; }
    public void setRepoCommitId(String repoCommitId) { this.repoCommitId = repoCommitId; }
    public Date getFirstReportTime() { return firstReportTime; }
    public void setFirstReportTime(Date firstReportTime) { this.firstReportTime = firstReportTime; }
    public Date getLastReportTime() { return lastReportTime; }
    public void setLastReportTime(Date lastReportTime) { this.lastReportTime = lastReportTime; }
    public List<String> getTestStages() { return testStages; }
    public void setTestStages(List<String> testStages) { this.testStages = testStages == null ? new ArrayList<>() : testStages; }
    public List<String> getReportIds() { return reportIds; }
    public void setReportIds(List<String> reportIds) { this.reportIds = reportIds == null ? new ArrayList<>() : reportIds; }
    public long getTotalReports() { return totalReports; }
    public void setTotalReports(long totalReports) { this.totalReports = totalReports; }
    public long getTotalLines() { return totalLines; }
    public void setTotalLines(long totalLines) { this.totalLines = totalLines; }
    public long getCoveredLines() { return coveredLines; }
    public void setCoveredLines(long coveredLines) { this.coveredLines = coveredLines; }
    public long getTotalMethods() { return totalMethods; }
    public void setTotalMethods(long totalMethods) { this.totalMethods = totalMethods; }
    public long getCoveredMethods() { return coveredMethods; }
    public void setCoveredMethods(long coveredMethods) { this.coveredMethods = coveredMethods; }
    public long getTotalBranchTargets() { return totalBranchTargets; }
    public void setTotalBranchTargets(long totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
    public long getCoveredBranchTargets() { return coveredBranchTargets; }
    public void setCoveredBranchTargets(long coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
}
