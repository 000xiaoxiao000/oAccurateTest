package com.oAT.web.language.universal;

import com.fasterxml.jackson.databind.JsonNode;
import com.oAT.web.common.UtilJson;
import com.oAT.web.coverage.UniversalCoverageIngestService;
import com.oAT.web.coverage.universal.SourceType;
import com.oAT.web.coveragecore.ingest.CoverageIngestBackend;
import com.oAT.web.coveragecore.ingest.CoverageRawIngestRequest;
import com.oAT.web.coveragecore.ingest.CoverageRawIngestResult;
import com.oAT.web.coveragecore.ingest.UniversalCoverageAliasReportRequest;
import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.coveragecore.report.CoverageReportBackend;
import com.oAT.web.coveragecore.report.CoverageReportGenerationRequest;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.language.spi.CoverageIngestMetadata;
import com.oAT.web.language.spi.CoverageLanguageAdapter;
import com.oAT.web.language.spi.CoverageLanguageAdapterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Service
public class UniversalCoverageBackend implements CoverageIngestBackend, CoverageReportBackend {
    private final UniversalCoverageIngestService universalCoverageIngestService;
    private final CoverageLanguageAdapterRegistry adapterRegistry;

    public UniversalCoverageBackend(UniversalCoverageIngestService universalCoverageIngestService,
                                    CoverageLanguageAdapterRegistry adapterRegistry) {
        this.universalCoverageIngestService = universalCoverageIngestService;
        this.adapterRegistry = adapterRegistry;
    }

    @Override
    public boolean supports(CoverageLanguage language) {
        return language != null && language != CoverageLanguage.JAVA && language != CoverageLanguage.FRONTEND;
    }

    @Override
    public CoverageRawIngestResult ingest(CoverageRawIngestRequest request) {
        CoverageLanguageAdapter adapter = adapterRegistry.get(request.getLanguage());
        if (adapter != null) {
            adapter.parse(payloadBytes(request.getPayload(), request.getOriginalBody()), metadata(request));
        }
        UniversalCoverageIngestService.UniversalCoverageReportRequest universalRequest =
                new UniversalCoverageIngestService.UniversalCoverageReportRequest();
        universalRequest.setCommitId(request.getCommitId());
        universalRequest.setVersionNumber(request.getVersionNumber());
        universalRequest.setBranch(request.getBranch());
        universalRequest.setCaseName(request.getCaseName());
        universalRequest.setBuildId(request.getBuildId());
        universalRequest.setTestStage(defaultText(request.getTestStage(), "unknown"));
        universalRequest.setTimestamp(request.getTimestamp());
        universalRequest.setCoverageData(payloadAsString(request.getPayload(), request.getOriginalBody()));
        String rawReportId = universalCoverageIngestService.saveReport(
                request.getProjectId(), request.getAppId(), SourceType.from(request.getLanguage().name()), universalRequest);
        return CoverageRawIngestResult.fromRawId(rawReportId);
    }

    @Override
    public String ingestUniversalAlias(String projectId,
                                       String appId,
                                       SourceType sourceType,
                                       UniversalCoverageAliasReportRequest request) {
        UniversalCoverageIngestService.UniversalCoverageReportRequest legacyRequest =
                new UniversalCoverageIngestService.UniversalCoverageReportRequest();
        legacyRequest.setCommitId(request.getCommitId());
        legacyRequest.setVersionNumber(request.getVersionNumber());
        legacyRequest.setBranch(request.getBranch());
        legacyRequest.setCaseName(request.getCaseName());
        legacyRequest.setBuildId(request.getBuildId());
        legacyRequest.setTestStage(defaultText(request.getTestStage(), "unknown"));
        legacyRequest.setTimestamp(request.getTimestamp());
        legacyRequest.setCoverageData(request.getCoverageData());
        return universalCoverageIngestService.saveReport(projectId, appId, sourceType, legacyRequest);
    }

    @Override
    public CoverageReportIndex generateReport(String projectId,
                                              String appId,
                                              SourceType sourceType,
                                              CoverageReportGenerationRequest request) {
        UniversalCoverageIngestService.UniversalCoverageGenerateRequest legacyRequest =
                new UniversalCoverageIngestService.UniversalCoverageGenerateRequest();
        if (request != null) {
            legacyRequest.setVersionNumber(request.getVersionNumber());
            legacyRequest.setBranch(request.getBranch());
            legacyRequest.setCommitId(request.getCommitId());
            legacyRequest.setBuildId(request.getBuildId());
            legacyRequest.setTestStage(request.getTestStage());
            legacyRequest.setReportType(request.getReportType());
            legacyRequest.setBaseVersionNumber(request.getBaseVersionNumber());
            legacyRequest.setBaseCommitId(request.getBaseCommitId());
        }
        return universalCoverageIngestService.generateReport(projectId, appId, sourceType, legacyRequest);
    }

    private CoverageIngestMetadata metadata(CoverageRawIngestRequest request) {
        CoverageIngestMetadata metadata = new CoverageIngestMetadata();
        metadata.setProjectId(request.getProjectId());
        metadata.setAppId(request.getAppId());
        metadata.setLanguage(request.getLanguage());
        metadata.setVersionNumber(request.getVersionNumber());
        metadata.setBranch(request.getBranch());
        metadata.setCommitId(request.getCommitId());
        metadata.setBuildId(request.getBuildId());
        metadata.setTestStage(defaultText(request.getTestStage(), "unknown"));
        metadata.setCaseName(request.getCaseName());
        metadata.setTraceId(request.getTraceId());
        metadata.setTimestamp(request.getTimestamp());
        return metadata;
    }

    private byte[] payloadBytes(JsonNode payload, String fallback) {
        return payloadAsString(payload, fallback).getBytes(StandardCharsets.UTF_8);
    }

    private String payloadAsString(JsonNode payload, String fallback) {
        if (payload == null) {
            return fallback;
        }
        if (payload.isTextual()) {
            return payload.asText();
        }
        return UtilJson.writeValueAsString(payload);
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
