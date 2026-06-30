package com.oAT.web.coveragecore.report;

import com.oAT.web.coverage.universal.SourceType;
import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MultiLanguageCoverageReportService {
    private final List<CoverageReportBackend> reportBackends;

    public MultiLanguageCoverageReportService(List<CoverageReportBackend> reportBackends) {
        this.reportBackends = reportBackends;
    }

    public CoverageReportIndex generateFrontendReport(String projectId,
                                                      String appId,
                                                      CoverageReportGenerationRequest request) {
        return backend(CoverageLanguage.FRONTEND).generateReport(projectId, appId, SourceType.FRONTEND, request);
    }

    public CoverageReportIndex generateUniversalReport(String projectId,
                                                       String appId,
                                                       SourceType sourceType,
                                                       CoverageReportGenerationRequest request) {
        CoverageLanguage language = CoverageLanguage.from(sourceType.name());
        return backend(language).generateReport(projectId, appId, sourceType, request);
    }

    private CoverageReportBackend backend(CoverageLanguage language) {
        return reportBackends.stream()
                .filter(candidate -> candidate.supports(language))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的覆盖率语言: " + language));
    }
}
