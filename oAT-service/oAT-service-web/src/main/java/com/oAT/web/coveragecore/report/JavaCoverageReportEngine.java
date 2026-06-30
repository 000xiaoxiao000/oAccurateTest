package com.oAT.web.coveragecore.report;

import com.oAT.web.common.Job;

public interface JavaCoverageReportEngine {
    int REPORT_TYPE_VERSION_FULL = 0;
    int REPORT_TYPE_INCREMENTAL = 1;
    int REPORT_TYPE_CURRENT_COMMIT = 2;

    String generateReport(String appId,
                          String versionNumber,
                          String branch,
                          String commitId,
                          Integer reportType,
                          String baseVersionNumber,
                          String baseCommitId,
                          Job<String> job);
}
