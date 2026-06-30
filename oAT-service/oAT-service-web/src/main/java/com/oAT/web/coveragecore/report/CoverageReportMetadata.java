package com.oAT.web.coveragecore.report;

import java.io.Serializable;

public class CoverageReportMetadata implements Serializable {
    private CoverageReportSummary report;
    private AppSummary app;
    private String currentUserRole;

    public CoverageReportSummary getReport() { return report; }
    public void setReport(CoverageReportSummary report) { this.report = report; }
    public AppSummary getApp() { return app; }
    public void setApp(AppSummary app) { this.app = app; }
    public String getCurrentUserRole() { return currentUserRole; }
    public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
}
