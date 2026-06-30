package com.oAT.web.api.snapshot;

import com.oAT.web.api.common.ApiSummaries.*;
import com.oAT.web.coveragecore.model.SourceCoveragePayload;

import java.util.List;

public class MySnapshotAggregateCodePayload {
    private AppSummary app;
    private String className;
    private List<MethodCoverageSummary> methods;
    private SourceCoveragePayload sourceCoverage;
    private String coloredSourceHtml;
    private String currentUserRole;

    public AppSummary getApp() { return app; }
    public void setApp(AppSummary app) { this.app = app; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public List<MethodCoverageSummary> getMethods() { return methods; }
    public void setMethods(List<MethodCoverageSummary> methods) { this.methods = methods; }
    public SourceCoveragePayload getSourceCoverage() { return sourceCoverage; }
    public void setSourceCoverage(SourceCoveragePayload sourceCoverage) { this.sourceCoverage = sourceCoverage; }
    public String getColoredSourceHtml() { return coloredSourceHtml; }
    public void setColoredSourceHtml(String coloredSourceHtml) { this.coloredSourceHtml = coloredSourceHtml; }
    public String getCurrentUserRole() { return currentUserRole; }
    public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
}
