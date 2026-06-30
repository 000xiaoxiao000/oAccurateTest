package com.oAT.web.api.snapshot;

import com.oAT.web.service.entity.SnapshotVo;

import java.util.List;

public class MySnapshotReportPayload {
    private SnapshotVo snapshot;
    private CoverageReportSummary report;
    private List<ClassCoverageSummary> classStats;
    private List<MySnapshotCodeRelationshipGroupSummary> codeRelationships;
    private String currentUserRole;

    public SnapshotVo getSnapshot() { return snapshot; }
    public void setSnapshot(SnapshotVo snapshot) { this.snapshot = snapshot; }
    public CoverageReportSummary getReport() { return report; }
    public void setReport(CoverageReportSummary report) { this.report = report; }
    public List<ClassCoverageSummary> getClassStats() { return classStats; }
    public void setClassStats(List<ClassCoverageSummary> classStats) { this.classStats = classStats; }
    public List<MySnapshotCodeRelationshipGroupSummary> getCodeRelationships() { return codeRelationships; }
    public void setCodeRelationships(List<MySnapshotCodeRelationshipGroupSummary> codeRelationships) { this.codeRelationships = codeRelationships; }
    public String getCurrentUserRole() { return currentUserRole; }
    public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
}
