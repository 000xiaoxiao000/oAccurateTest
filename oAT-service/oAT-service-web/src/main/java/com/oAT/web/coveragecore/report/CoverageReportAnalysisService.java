package com.oAT.web.coveragecore.report;

import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.entity.CoverageComparisonVo;
import org.springframework.stereotype.Service;

@Service
public class CoverageReportAnalysisService {
    private final CoverageFreshnessService coverageFreshnessService;
    private final CoverageComparisonQueryService coverageComparisonQueryService;

    public CoverageReportAnalysisService(CoverageFreshnessService coverageFreshnessService,
                                         CoverageComparisonQueryService coverageComparisonQueryService) {
        this.coverageFreshnessService = coverageFreshnessService;
        this.coverageComparisonQueryService = coverageComparisonQueryService;
    }

    public boolean hasNewerData(String appId, String versionNumber, CoverageReportIndex report) {
        return coverageFreshnessService.hasNewerData(appId, versionNumber, report);
    }

    public CoverageComparisonVo getComparison(String reportId) {
        return coverageComparisonQueryService.getComparison(reportId);
    }
}
