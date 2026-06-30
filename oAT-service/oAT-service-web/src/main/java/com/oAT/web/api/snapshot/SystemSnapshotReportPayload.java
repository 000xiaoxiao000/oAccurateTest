package com.oAT.web.api.snapshot;

import com.oAT.web.api.common.ApiSummaries.*;
import com.oAT.web.coveragecore.report.CoverageFootprintSnapshotService.CoverageFootprintSnapshot;

import java.util.List;

public class SystemSnapshotReportPayload {
    private AppSummary app;
    private SystemSnapshotSummary snapshot;
    private CoverageReportSummary report;
    private List<ClassCoverageSummary> classStats;
    private List<MySnapshotCodeRelationshipGroupSummary> codeRelationships;
    private List<CoverageFootprintSnapshot> coverageFootprints;
    private String currentUserRole;

    public AppSummary getApp() { return app; }
    public void setApp(AppSummary app) { this.app = app; }
    public SystemSnapshotSummary getSnapshot() { return snapshot; }
    public void setSnapshot(SystemSnapshotSummary snapshot) { this.snapshot = snapshot; }
    public CoverageReportSummary getReport() { return report; }
    public void setReport(CoverageReportSummary report) { this.report = report; }
    public List<ClassCoverageSummary> getClassStats() { return classStats; }
    public void setClassStats(List<ClassCoverageSummary> classStats) { this.classStats = classStats; }
    public List<MySnapshotCodeRelationshipGroupSummary> getCodeRelationships() { return codeRelationships; }
    public void setCodeRelationships(List<MySnapshotCodeRelationshipGroupSummary> codeRelationships) { this.codeRelationships = codeRelationships; }
    public List<CoverageFootprintSnapshot> getCoverageFootprints() { return coverageFootprints; }
    public void setCoverageFootprints(List<CoverageFootprintSnapshot> coverageFootprints) { this.coverageFootprints = coverageFootprints; }
    public String getCurrentUserRole() { return currentUserRole; }
    public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
}
