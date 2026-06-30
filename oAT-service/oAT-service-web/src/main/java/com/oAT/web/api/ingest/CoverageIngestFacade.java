package com.oAT.web.api.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.oAT.web.common.UtilJson;
import com.oAT.web.coverage.universal.SourceType;
import com.oAT.web.coveragecore.ingest.CoverageAliasIngestResult;
import com.oAT.web.coveragecore.ingest.CoverageRawIngestRequest;
import com.oAT.web.coveragecore.ingest.CoverageRawIngestResult;
import com.oAT.web.coveragecore.ingest.FrontendCoverageAliasReportRequest;
import com.oAT.web.coveragecore.ingest.MultiLanguageCoverageIngestService;
import com.oAT.web.coveragecore.ingest.UniversalCoverageAliasReportRequest;
import com.oAT.web.coveragecore.model.CoverageLanguage;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Service
public class CoverageIngestFacade {
    private final MultiLanguageCoverageIngestService multiLanguageCoverageIngestService;

    public CoverageIngestFacade(MultiLanguageCoverageIngestService multiLanguageCoverageIngestService) {
        this.multiLanguageCoverageIngestService = multiLanguageCoverageIngestService;
    }

    public CoverageIngestResult ingestUnified(HttpHeaders headers, String requestBody) {
        UnifiedCoverageReportRequest request = parseRequest(requestBody);
        CoverageLanguage language = CoverageLanguage.from(request.getLanguage());
        CoverageRawIngestRequest rawRequest = new CoverageRawIngestRequest();
        rawRequest.setProjectId(request.getProjectId());
        rawRequest.setAppId(request.getAppId());
        rawRequest.setLanguage(language);
        rawRequest.setVersionNumber(request.getVersionNumber());
        rawRequest.setBranch(request.getBranch());
        rawRequest.setCommitId(request.getCommitId());
        rawRequest.setBuildId(request.getBuildId());
        rawRequest.setTestStage(request.getTestStage());
        rawRequest.setCaseName(request.getCaseName());
        rawRequest.setTraceId(request.getTraceId());
        rawRequest.setRequestId(firstText(request.getRequestId(), firstHeader(headers, "X-OAT-Relay-Request-Id", "X-OAT-Request-Id", "X-Request-Id")));
        rawRequest.setTimestamp(request.getTimestamp());
        rawRequest.setPayload(request.getPayload());
        rawRequest.setOriginalBody(requestBody);
        CoverageRawIngestResult result = multiLanguageCoverageIngestService.ingest(rawRequest);
        return CoverageIngestResult.fromRawId(result.getRawReportId());
    }

    public CoverageAliasIngestResult ingestFrontendAlias(String projectId,
                                                         String appId,
                                                         HttpHeaders headers,
                                                         FrontendCoverageAliasReportRequest request) {
        Assert.notNull(request, "请求体不能为空");
        String headerRequestId = firstHeader(headers, "X-OAT-Relay-Request-Id", "X-OAT-Request-Id", "X-Request-Id");
        if (StringUtils.hasText(headerRequestId)) {
            request.setRequestId(headerRequestId);
        }
        if (!StringUtils.hasText(request.getTestStage())) {
            request.setTestStage("unknown");
        }
        return multiLanguageCoverageIngestService.ingestFrontendAlias(projectId, appId, request);
    }

    public String ingestUniversalAlias(String projectId,
                                       String appId,
                                       SourceType sourceType,
                                       UniversalCoverageAliasReportRequest request) {
        Assert.notNull(request, "请求体不能为空");
        if (!StringUtils.hasText(request.getTestStage())) {
            request.setTestStage("unknown");
        }
        return multiLanguageCoverageIngestService.ingestUniversalAlias(projectId, appId, sourceType, request);
    }

    private UnifiedCoverageReportRequest parseRequest(String requestBody) {
        Assert.hasText(requestBody, "请求体不能为空");
        try {
            JsonNode root = UtilJson.getObjectMapper().readTree(requestBody);
            UnifiedCoverageReportRequest request = new UnifiedCoverageReportRequest();
            request.setProjectId(text(root, "projectId", "project_id"));
            request.setAppId(text(root, "appId", "app_id"));
            request.setLanguage(text(root, "language", "sourceType", "source_type"));
            request.setVersionNumber(text(root, "versionNumber", "version", "version_number"));
            request.setBranch(text(root, "branch", "repoBranch"));
            request.setCommitId(text(root, "commitId", "commit_id", "commitSha"));
            request.setBuildId(text(root, "buildId", "build_id"));
            request.setTestStage(text(root, "testStage", "test_stage"));
            request.setCaseName(text(root, "caseName", "case_name"));
            request.setTraceId(text(root, "traceId", "trace_id"));
            request.setRequestId(text(root, "requestId", "request_id"));
            request.setTimestamp(longValue(root, "timestamp", "time"));
            request.setPayload(first(root, "payload", "coverageData", "coverage", "data", "profile"));
            Assert.notNull(request.getPayload(), "payload不能为空");
            return request;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("统一覆盖率上报请求体不是有效 JSON", e);
        }
    }

    private JsonNode first(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String... names) {
        JsonNode value = first(node, names);
        return value == null ? null : value.asText();
    }

    private Long longValue(JsonNode node, String... names) {
        JsonNode value = first(node, names);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private String firstHeader(HttpHeaders headers, String... names) {
        if (headers == null || names == null) {
            return null;
        }
        for (String name : names) {
            String value = headers.getFirst(name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    public static class UnifiedCoverageReportRequest {
        private String projectId;
        private String appId;
        private String language;
        private String versionNumber;
        private String branch;
        private String commitId;
        private String buildId;
        private String testStage;
        private String caseName;
        private String traceId;
        private String requestId;
        private Long timestamp;
        private JsonNode payload;

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getCommitId() { return commitId; }
        public void setCommitId(String commitId) { this.commitId = commitId; }
        public String getBuildId() { return buildId; }
        public void setBuildId(String buildId) { this.buildId = buildId; }
        public String getTestStage() { return testStage; }
        public void setTestStage(String testStage) { this.testStage = testStage; }
        public String getCaseName() { return caseName; }
        public void setCaseName(String caseName) { this.caseName = caseName; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        public JsonNode getPayload() { return payload; }
        public void setPayload(JsonNode payload) { this.payload = payload; }
    }

    public static class CoverageIngestResult {
        private String rawReportId;

        public static CoverageIngestResult fromRawId(String rawReportId) {
            CoverageIngestResult result = new CoverageIngestResult();
            result.setRawReportId(rawReportId);
            return result;
        }

        public String getRawReportId() { return rawReportId; }
        public void setRawReportId(String rawReportId) { this.rawReportId = rawReportId; }
    }
}
