package com.oAT.web.api.snapshot;

import com.oAT.web.api.common.ApiSummaries.*;
import com.oAT.web.coveragecore.model.SourceCoveragePayload;
import com.oAT.web.coveragecore.report.CoverageFootprintSnapshotService.CoverageFootprintSnapshot;

import java.util.List;

public class SystemSnapshotCodePayload {
    private AppSummary app;
    private SystemSnapshotSummary snapshot;
    private String className;
    private List<MethodCoverageSummary> methods;
    private SourceCoveragePayload sourceCoverage;
    private String coloredSourceHtml;
    private List<CoverageFootprintSnapshot> coverageFootprints;
    private String currentUserRole;

    public AppSummary getApp() { return app; }
    public void setApp(AppSummary app) { this.app = app; }
    public SystemSnapshotSummary getSnapshot() { return snapshot; }
    public void setSnapshot(SystemSnapshotSummary snapshot) { this.snapshot = snapshot; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public List<MethodCoverageSummary> getMethods() { return methods; }
    public void setMethods(List<MethodCoverageSummary> methods) { this.methods = methods; }
    public SourceCoveragePayload getSourceCoverage() { return sourceCoverage; }
    public void setSourceCoverage(SourceCoveragePayload sourceCoverage) { this.sourceCoverage = sourceCoverage; }
    public String getColoredSourceHtml() { return coloredSourceHtml; }
    public void setColoredSourceHtml(String coloredSourceHtml) { this.coloredSourceHtml = coloredSourceHtml; }
    public List<CoverageFootprintSnapshot> getCoverageFootprints() { return coverageFootprints; }
    public void setCoverageFootprints(List<CoverageFootprintSnapshot> coverageFootprints) { this.coverageFootprints = coverageFootprints; }
    public String getCurrentUserRole() { return currentUserRole; }
    public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
}
