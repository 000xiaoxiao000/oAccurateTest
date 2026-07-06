package com.oAT.web.language.frontend;

import com.fasterxml.jackson.databind.JsonNode;
import com.oAT.web.common.UtilJson;
import com.oAT.web.coverage.FrontendCoverageService;
import com.oAT.web.coverage.universal.SourceType;
import com.oAT.web.coveragecore.ingest.CoverageAliasIngestResult;
import com.oAT.web.coveragecore.ingest.CoverageIngestBackend;
import com.oAT.web.coveragecore.ingest.CoverageRawIngestRequest;
import com.oAT.web.coveragecore.ingest.CoverageRawIngestResult;
import com.oAT.web.coveragecore.ingest.FrontendCoverageAliasReportRequest;
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
public class FrontendCoverageBackend implements CoverageIngestBackend, CoverageReportBackend {
    private final FrontendCoverageService frontendCoverageService;
    private final CoverageLanguageAdapterRegistry adapterRegistry;

    public FrontendCoverageBackend(FrontendCoverageService frontendCoverageService,
                                   CoverageLanguageAdapterRegistry adapterRegistry) {
        this.frontendCoverageService = frontendCoverageService;
        this.adapterRegistry = adapterRegistry;
    }

    @Override
    public boolean supports(CoverageLanguage language) {
        return language == CoverageLanguage.FRONTEND;
    }

    @Override
    public CoverageRawIngestResult ingest(CoverageRawIngestRequest request) {
        CoverageLanguageAdapter adapter = adapterRegistry.get(CoverageLanguage.FRONTEND);
        if (adapter != null) {
            adapter.parse(payloadBytes(request.getPayload(), request.getOriginalBody()), metadata(request));
        }
        FrontendCoverageService.FrontendCoverageReportRequest frontendRequest =
                new FrontendCoverageService.FrontendCoverageReportRequest();
        frontendRequest.setRequestId(request.getRequestId());
        frontendRequest.setCommitId(request.getCommitId());
        frontendRequest.setVersionNumber(request.getVersionNumber());
        frontendRequest.setBranch(request.getBranch());
        frontendRequest.setCaseName(request.getCaseName());
        frontendRequest.setBuildId(request.getBuildId());
        frontendRequest.setTestStage(defaultText(request.getTestStage(), "unknown"));
        frontendRequest.setTimestamp(request.getTimestamp());
        frontendRequest.setCoverage(jsonPayload(request.getPayload()));
        FrontendCoverageService.FrontendCoverageIngestResult result =
                frontendCoverageService.saveReport(request.getProjectId(), request.getAppId(), frontendRequest);
        return CoverageRawIngestResult.fromRawId(result.getRawReportId());
    }

    @Override
    public CoverageAliasIngestResult ingestFrontendAlias(String projectId,
                                                         String appId,
                                                         FrontendCoverageAliasReportRequest request) {
        FrontendCoverageService.FrontendCoverageReportRequest legacyRequest =
                new FrontendCoverageService.FrontendCoverageReportRequest();
        legacyRequest.setRequestId(request.getRequestId());
        legacyRequest.setCommitId(request.getCommitId());
        legacyRequest.setVersionNumber(request.getVersionNumber());
        legacyRequest.setBranch(request.getBranch());
        legacyRequest.setCaseName(request.getCaseName());
        legacyRequest.setBuildId(request.getBuildId());
        legacyRequest.setTestStage(defaultText(request.getTestStage(), "unknown"));
        legacyRequest.setTimestamp(request.getTimestamp());
        legacyRequest.setCoverage(request.getCoverage());

        FrontendCoverageService.FrontendCoverageIngestResult legacyResult =
                frontendCoverageService.saveReport(projectId, appId, legacyRequest);
        CoverageAliasIngestResult result = new CoverageAliasIngestResult();
        result.setRawReportId(legacyResult.getRawReportId());
        result.setRequestId(legacyResult.getRequestId());
        result.setProjectId(legacyResult.getProjectId());
        result.setAppId(legacyResult.getAppId());
        result.setVersionNumber(legacyResult.getVersionNumber());
        result.setCommitId(legacyResult.getCommitId());
        result.setBranch(legacyResult.getBranch());
        return result;
    }

    @Override
    public CoverageReportIndex generateReport(String projectId,
                                              String appId,
                                              SourceType sourceType,
                                              CoverageReportGenerationRequest request) {
        FrontendCoverageService.FrontendCoverageGenerateRequest legacyRequest =
                new FrontendCoverageService.FrontendCoverageGenerateRequest();
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
        return frontendCoverageService.generateReport(projectId, appId, legacyRequest);
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

    private JsonNode jsonPayload(JsonNode payload) {
        if (payload == null || !payload.isTextual()) {
            return payload;
        }
        try {
            return UtilJson.getObjectMapper().readTree(payload.asText());
        } catch (Exception e) {
            return payload;
        }
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
