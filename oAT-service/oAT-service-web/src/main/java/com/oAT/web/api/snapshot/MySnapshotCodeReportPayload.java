package com.oAT.web.api.snapshot;

import java.util.List;

public class MySnapshotCodeReportPayload {
    private String appId;
    private CoverageReportSummary report;
    private List<ClassCoverageSummary> classStats;
    private List<MySnapshotCodeRelationshipGroupSummary> codeRelationships;
    private String currentUserRole;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public CoverageReportSummary getReport() { return report; }
    public void setReport(CoverageReportSummary report) { this.report = report; }
    public List<ClassCoverageSummary> getClassStats() { return classStats; }
    public void setClassStats(List<ClassCoverageSummary> classStats) { this.classStats = classStats; }
    public List<MySnapshotCodeRelationshipGroupSummary> getCodeRelationships() { return codeRelationships; }
    public void setCodeRelationships(List<MySnapshotCodeRelationshipGroupSummary> codeRelationships) { this.codeRelationships = codeRelationships; }
    public String getCurrentUserRole() { return currentUserRole; }
    public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
}
