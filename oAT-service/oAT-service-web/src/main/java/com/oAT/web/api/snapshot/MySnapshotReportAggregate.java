package com.oAT.web.api.snapshot;

import java.util.List;

public class MySnapshotReportAggregate {
    private final CoverageReportSummary summary;
    private final List<ClassCoverageSummary> classStats;

    public MySnapshotReportAggregate(CoverageReportSummary summary, List<ClassCoverageSummary> classStats) {
        this.summary = summary;
        this.classStats = classStats;
    }

    public CoverageReportSummary getSummary() {
        return summary;
    }

    public List<ClassCoverageSummary> getClassStats() {
        return classStats;
    }
}
