package com.oAT.web.coveragecore.ingest;

import com.oAT.web.coverage.universal.SourceType;
import com.oAT.web.coveragecore.model.CoverageLanguage;

public interface CoverageIngestBackend {
    boolean supports(CoverageLanguage language);

    CoverageRawIngestResult ingest(CoverageRawIngestRequest request);

    default CoverageAliasIngestResult ingestFrontendAlias(String projectId,
                                                          String appId,
                                                          FrontendCoverageAliasReportRequest request) {
        throw new UnsupportedOperationException("frontend alias ingest is not supported");
    }

    default String ingestUniversalAlias(String projectId,
                                        String appId,
                                        SourceType sourceType,
                                        UniversalCoverageAliasReportRequest request) {
        throw new UnsupportedOperationException("universal alias ingest is not supported");
    }
}
