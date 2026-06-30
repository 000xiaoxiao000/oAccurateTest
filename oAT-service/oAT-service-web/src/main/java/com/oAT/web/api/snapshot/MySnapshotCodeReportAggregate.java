package com.oAT.web.api.snapshot;

import java.util.List;

public class MySnapshotCodeReportAggregate {
    private final CoverageReportSummary summary;
    private final List<ClassCoverageSummary> classStats;
    private final List<MySnapshotCodeRelationshipGroupSummary> codeRelationships;
    private final String appId;

    public MySnapshotCodeReportAggregate(CoverageReportSummary summary,
                                         List<ClassCoverageSummary> classStats,
                                         List<MySnapshotCodeRelationshipGroupSummary> codeRelationships,
                                         String appId) {
        this.summary = summary;
        this.classStats = classStats;
        this.codeRelationships = codeRelationships;
        this.appId = appId;
    }

    public CoverageReportSummary getSummary() { return summary; }
    public List<ClassCoverageSummary> getClassStats() { return classStats; }
    public List<MySnapshotCodeRelationshipGroupSummary> getCodeRelationships() { return codeRelationships; }
    public String getAppId() { return appId; }
}
