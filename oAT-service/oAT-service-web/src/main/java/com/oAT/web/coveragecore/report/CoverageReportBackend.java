package com.oAT.web.coveragecore.report;

import com.oAT.web.coverage.universal.SourceType;
import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.esDao.entity.CoverageReportIndex;

public interface CoverageReportBackend {
    boolean supports(CoverageLanguage language);

    CoverageReportIndex generateReport(String projectId,
                                       String appId,
                                       SourceType sourceType,
                                       CoverageReportGenerationRequest request);
}
