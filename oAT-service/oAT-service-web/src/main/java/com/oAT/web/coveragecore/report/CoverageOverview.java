package com.oAT.web.coveragecore.report;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CoverageOverview implements Serializable {
    private ProjectSummary project;
    private List<AppSummary> apps = new ArrayList<>();
    private AppSummary app;
    private VersionSummary version;
    private CoverageReportSummary report;
    private CoverageReportSummary versionFullReport;
    private CoverageReportSummary currentCommitReport;
    private CoverageReportSummary incrementalReport;
    private Boolean hasNewerData;
    private CoverageComparisonSummary comparison;
    private CoverageComparisonSummary versionFullComparison;
    private CoverageComparisonSummary currentCommitComparison;
    private CoverageComparisonSummary incrementalComparison;
    private String currentUserRole;

    public ProjectSummary getProject() { return project; }
    public void setProject(ProjectSummary project) { this.project = project; }
    public List<AppSummary> getApps() { return apps; }
    public void setApps(List<AppSummary> apps) { this.apps = apps == null ? new ArrayList<>() : apps; }
    public AppSummary getApp() { return app; }
    public void setApp(AppSummary app) { this.app = app; }
    public VersionSummary getVersion() { return version; }
    public void setVersion(VersionSummary version) { this.version = version; }
    public CoverageReportSummary getReport() { return report; }
    public void setReport(CoverageReportSummary report) { this.report = report; }
    public CoverageReportSummary getVersionFullReport() { return versionFullReport; }
    public void setVersionFullReport(CoverageReportSummary versionFullReport) { this.versionFullReport = versionFullReport; }
    public CoverageReportSummary getCurrentCommitReport() { return currentCommitReport; }
    public void setCurrentCommitReport(CoverageReportSummary currentCommitReport) { this.currentCommitReport = currentCommitReport; }
    public CoverageReportSummary getIncrementalReport() { return incrementalReport; }
    public void setIncrementalReport(CoverageReportSummary incrementalReport) { this.incrementalReport = incrementalReport; }
    public Boolean getHasNewerData() { return hasNewerData; }
    public void setHasNewerData(Boolean hasNewerData) { this.hasNewerData = hasNewerData; }
    public CoverageComparisonSummary getComparison() { return comparison; }
    public void setComparison(CoverageComparisonSummary comparison) { this.comparison = comparison; }
    public CoverageComparisonSummary getVersionFullComparison() { return versionFullComparison; }
    public void setVersionFullComparison(CoverageComparisonSummary versionFullComparison) { this.versionFullComparison = versionFullComparison; }
    public CoverageComparisonSummary getCurrentCommitComparison() { return currentCommitComparison; }
    public void setCurrentCommitComparison(CoverageComparisonSummary currentCommitComparison) { this.currentCommitComparison = currentCommitComparison; }
    public CoverageComparisonSummary getIncrementalComparison() { return incrementalComparison; }
    public void setIncrementalComparison(CoverageComparisonSummary incrementalComparison) { this.incrementalComparison = incrementalComparison; }
    public String getCurrentUserRole() { return currentUserRole; }
    public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
}
