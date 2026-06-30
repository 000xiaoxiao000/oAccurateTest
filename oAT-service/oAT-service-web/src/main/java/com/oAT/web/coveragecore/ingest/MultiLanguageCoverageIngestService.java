package com.oAT.web.coveragecore.ingest;

import com.oAT.web.coverage.universal.SourceType;
import com.oAT.web.coveragecore.model.CoverageLanguage;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

@Service
public class MultiLanguageCoverageIngestService {
    private final List<CoverageIngestBackend> ingestBackends;

    public MultiLanguageCoverageIngestService(List<CoverageIngestBackend> ingestBackends) {
        this.ingestBackends = ingestBackends;
    }

    public CoverageRawIngestResult ingest(CoverageRawIngestRequest request) {
        Assert.notNull(request, "请求体不能为空");
        CoverageLanguage language = request.getLanguage();
        Assert.notNull(language, "language不能为空");
        Assert.hasText(request.getProjectId(), "projectId不能为空");
        Assert.hasText(request.getAppId(), "appId不能为空");
        if (language == CoverageLanguage.JAVA) {
            throw new IllegalArgumentException("JAVA 覆盖率仍通过 /client/* Agent 协议上报");
        }
        return backend(language).ingest(request);
    }

    public CoverageAliasIngestResult ingestFrontendAlias(String projectId,
                                                         String appId,
                                                         FrontendCoverageAliasReportRequest request) {
        Assert.notNull(request, "请求体不能为空");
        return backend(CoverageLanguage.FRONTEND).ingestFrontendAlias(projectId, appId, request);
    }

    public String ingestUniversalAlias(String projectId,
                                       String appId,
                                       SourceType sourceType,
                                       UniversalCoverageAliasReportRequest request) {
        Assert.notNull(request, "请求体不能为空");
        CoverageLanguage language = CoverageLanguage.from(sourceType.name());
        return backend(language).ingestUniversalAlias(projectId, appId, sourceType, request);
    }

    private CoverageIngestBackend backend(CoverageLanguage language) {
        return ingestBackends.stream()
                .filter(candidate -> candidate.supports(language))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的覆盖率语言: " + language));
    }
}
