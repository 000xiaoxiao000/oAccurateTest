package com.oAT.web.coverage;

import com.fasterxml.jackson.databind.JsonNode;
import com.oAT.web.common.UtilJson;
import com.oAT.web.coverage.UniversalCoverageRawRepository.UniversalCoverageRawReport;
import com.oAT.web.coverage.universal.CoverageParser;
import com.oAT.web.coverage.universal.CoverageParserRegistry;
import com.oAT.web.coverage.universal.SourceType;
import com.oAT.web.coverage.universal.UniversalCoverageFile;
import com.oAT.web.coverage.universal.UniversalCoverageService;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.AppService;
import com.oAT.web.service.entity.AppVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UniversalCoverageIngestService {
    private final UniversalCoverageRawRepository rawRepository;
    private final AppService appService;
    private final CoverageParserRegistry coverageParserRegistry;
    private final UniversalCoverageService universalCoverageService;

    public UniversalCoverageIngestService(UniversalCoverageRawRepository rawRepository,
                                          AppService appService,
                                          CoverageParserRegistry coverageParserRegistry,
                                          UniversalCoverageService universalCoverageService) {
        this.rawRepository = rawRepository;
        this.appService = appService;
        this.coverageParserRegistry = coverageParserRegistry;
        this.universalCoverageService = universalCoverageService;
    }

    public String saveReport(String projectId, String appId, SourceType sourceType, UniversalCoverageReportRequest request) {
        Assert.hasText(projectId, "projectId不能为空");
        Assert.hasText(appId, "appId不能为空");
        Assert.notNull(sourceType, "sourceType不能为空");
        Assert.notNull(request, "请求体不能为空");
        Assert.hasText(request.getCoverageData(), "coverageData不能为空");

        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");

        UniversalCoverageRawReport report = new UniversalCoverageRawReport();
        report.projectId = projectId;
        report.appId = appId;
        report.sourceType = sourceType.name();
        report.commitId = firstText(request.getCommitId(), app.getCurrentCommitId());
        report.versionNumber = firstText(request.getVersionNumber(), app.getCurrentVersion(), report.commitId);
        report.branch = firstText(request.getBranch(), app.getCurrentBranch());
        report.caseName = request.getCaseName();
        report.buildId = request.getBuildId();
        report.testStage = request.getTestStage();
        report.timestamp = request.getTimestamp();
        report.coverageData = request.getCoverageData();
        return rawRepository.save(report);
    }

    @Transactional
    public CoverageReportIndex generateReport(String projectId, String appId, SourceType sourceType, UniversalCoverageGenerateRequest request) {
        Assert.hasText(projectId, "projectId不能为空");
        Assert.hasText(appId, "appId不能为空");
        Assert.notNull(sourceType, "sourceType不能为空");
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");

        String versionNumber = firstText(request == null ? null : request.getVersionNumber(), app.getCurrentVersion());
        String branch = firstText(request == null ? null : request.getBranch(), app.getCurrentBranch());
        String commitId = firstText(request == null ? null : request.getCommitId(), app.getCurrentCommitId());
        Assert.hasText(versionNumber, "versionNumber不能为空");

        List<UniversalCoverageRawReport> rawReports = rawRepository.findByAppAndVersion(appId, sourceType.name(), versionNumber, commitId);
        Assert.isTrue(!rawReports.isEmpty(), "没有可生成的" + sourceType.name() + "覆盖率上报数据");

        CoverageParser parser = coverageParserRegistry.get(sourceType);
        Map<String, UniversalCoverageFile> coverageMap = new LinkedHashMap<>();
        long lastTimestamp = 0L;
        for (UniversalCoverageRawReport rawReport : rawReports) {
            lastTimestamp = Math.max(lastTimestamp, rawReport.timestamp == null ? 0L : rawReport.timestamp);
            List<UniversalCoverageFile> files = parser.parse(rawReport.coverageData.getBytes(StandardCharsets.UTF_8));
            ClassCoverageIndex.CoverageFootprintRecord footprint = ClassCoverageIndex.CoverageFootprintRecord.of(
                    null,
                    rawReport.caseName,
                    rawReport.testStage,
                    rawReport.buildId,
                    rawReport.timestamp);
            files.forEach(file -> file.withFootprint(footprint));
            Map<String, UniversalCoverageFile> nextMap = universalCoverageService.mergeFiles(files);
            for (Map.Entry<String, UniversalCoverageFile> entry : nextMap.entrySet()) {
                coverageMap.compute(entry.getKey(), (key, existing) -> existing == null ? entry.getValue() : existing.merge(entry.getValue()));
            }
        }
        Assert.isTrue(!coverageMap.isEmpty(), "覆盖率数据解析结果为空");

        CoverageReportIndex report = new CoverageReportIndex();
        report.setId(UUID.randomUUID().toString());
        report.setAppId(appId);
        report.setVersionNumber(versionNumber);
        report.setRepoBranch(branch);
        report.setRepoCommitId(commitId);
        report.setCreateTime(new Date());
        report.setSourceType(sourceType.name());
        report.setLanguage(sourceType.name());
        report.setBuildId(request == null ? null : request.getBuildId());
        report.setTestStage(request == null ? null : request.getTestStage());
        report.setReportType(0);
        report.setLastProcessedTime(lastTimestamp > 0 ? String.valueOf(lastTimestamp) : String.valueOf(System.currentTimeMillis()));

        universalCoverageService.saveReport(report, appId, coverageMap);
        return report;
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

    public static UniversalCoverageReportRequest parseReportRequest(String requestBody) {
        Assert.hasText(requestBody, "请求体不能为空");
        try {
            JsonNode root = UtilJson.getObjectMapper().readTree(requestBody);
            UniversalCoverageReportRequest request = new UniversalCoverageReportRequest();
            request.setCommitId(text(root, "commitId", "commit_id", "commitSha"));
            request.setVersionNumber(text(root, "versionNumber", "version", "version_number"));
            request.setBranch(text(root, "branch", "repoBranch"));
            request.setCaseName(text(root, "caseName", "case_name"));
            request.setBuildId(text(root, "buildId", "build_id"));
            request.setTestStage(text(root, "testStage", "test_stage"));
            request.setTimestamp(longValue(root, "timestamp", "time"));

            JsonNode coverageNode = first(root, "coverageData", "coverage", "data", "profile");
            if (coverageNode == null) {
                request.setCoverageData(requestBody);
            } else if (coverageNode.isTextual()) {
                request.setCoverageData(coverageNode.asText());
            } else {
                request.setCoverageData(UtilJson.writeValueAsString(coverageNode));
            }
            return request;
        } catch (Exception ignored) {
            UniversalCoverageReportRequest request = new UniversalCoverageReportRequest();
            request.setCoverageData(requestBody);
            return request;
        }
    }

    private static JsonNode first(JsonNode node, String... names) {
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

    private static String text(JsonNode node, String... names) {
        JsonNode value = first(node, names);
        return value == null ? null : value.asText();
    }

    private static Long longValue(JsonNode node, String... names) {
        JsonNode value = first(node, names);
        return value == null || value.isNull() ? null : value.asLong();
    }

    public static class UniversalCoverageReportRequest {
        private String commitId;
        private String versionNumber;
        private String branch;
        private String caseName;
        private String buildId;
        private String testStage;
        private Long timestamp;
        private String coverageData;

        public String getCommitId() { return commitId; }
        public void setCommitId(String commitId) { this.commitId = commitId; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getCaseName() { return caseName; }
        public void setCaseName(String caseName) { this.caseName = caseName; }
        public String getBuildId() { return buildId; }
        public void setBuildId(String buildId) { this.buildId = buildId; }
        public String getTestStage() { return testStage; }
        public void setTestStage(String testStage) { this.testStage = testStage; }
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        public String getCoverageData() { return coverageData; }
        public void setCoverageData(String coverageData) { this.coverageData = coverageData; }
    }

    public static class UniversalCoverageGenerateRequest {
        private String versionNumber;
        private String branch;
        private String commitId;
        private String buildId;
        private String testStage;

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
    }
}
