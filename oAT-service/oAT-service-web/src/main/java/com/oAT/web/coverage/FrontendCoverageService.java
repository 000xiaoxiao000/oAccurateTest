package com.oAT.web.coverage;

import com.fasterxml.jackson.databind.JsonNode;
import com.oAT.web.common.UtilJson;
import com.oAT.web.coverage.FrontendCoverageReportRepository.FrontendCoverageReport;
import com.oAT.web.coverage.universal.CoverageParser;
import com.oAT.web.coverage.universal.CoverageParserRegistry;
import com.oAT.web.coverage.universal.SourceType;
import com.oAT.web.coverage.universal.UniversalCoverageFile;
import com.oAT.web.coverage.universal.UniversalCoverageService;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.AppService;
import com.oAT.web.service.entity.AppVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FrontendCoverageService {
    private static final Logger logger = LoggerFactory.getLogger(FrontendCoverageService.class);
    public static final String SOURCE_TYPE_FRONTEND = "FRONTEND";

    private final FrontendCoverageReportRepository frontendCoverageReportRepository;
    private final AppService appService;
    private final CoverageParserRegistry coverageParserRegistry;
    private final UniversalCoverageService universalCoverageService;

    public FrontendCoverageService(FrontendCoverageReportRepository frontendCoverageReportRepository,
                                   AppService appService,
                                   CoverageParserRegistry coverageParserRegistry,
                                   UniversalCoverageService universalCoverageService) {
        this.frontendCoverageReportRepository = frontendCoverageReportRepository;
        this.appService = appService;
        this.coverageParserRegistry = coverageParserRegistry;
        this.universalCoverageService = universalCoverageService;
    }

    public FrontendCoverageIngestResult saveReport(String projectId, String appId, FrontendCoverageReportRequest request) {
        Assert.hasText(projectId, "projectId不能为空");
        Assert.hasText(appId, "appId不能为空");
        Assert.notNull(request, "请求体不能为空");
        Assert.notNull(request.getCoverage(), "coverage不能为空");

        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");

        FrontendCoverageReport report = new FrontendCoverageReport();
        report.requestId = request.getRequestId();
        report.projectId = projectId;
        report.appId = appId;
        report.commitId = firstText(request.getCommitId(), app.getCurrentCommitId());
        report.versionNumber = firstText(request.getVersionNumber(), app.getCurrentVersion(), report.commitId);
        report.branch = firstText(request.getBranch(), app.getCurrentBranch());
        report.caseName = request.getCaseName();
        report.buildId = request.getBuildId();
        report.testStage = request.getTestStage();
        report.timestamp = request.getTimestamp();
        report.coverageJson = UtilJson.writeValueAsString(request.getCoverage());
        String reportId = frontendCoverageReportRepository.save(report);
        logger.info("frontend coverage raw report received, rawReportId={}, requestId={}, projectId={}, appId={}, version={}, commitId={}, branch={}, caseName={}, bytes={}",
                reportId, report.requestId, projectId, appId, report.versionNumber, report.commitId, report.branch, report.caseName,
                report.coverageJson == null ? 0 : report.coverageJson.length());
        return new FrontendCoverageIngestResult(reportId, report.requestId, projectId, appId, report.versionNumber, report.commitId, report.branch);
    }

    @Transactional
    public CoverageReportIndex generateReport(String projectId, String appId, FrontendCoverageGenerateRequest request) {
        Assert.hasText(projectId, "projectId不能为空");
        Assert.hasText(appId, "appId不能为空");
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");

        String versionNumber = firstText(request == null ? null : request.getVersionNumber(), app.getCurrentVersion());
        String branch = firstText(request == null ? null : request.getBranch(), app.getCurrentBranch());
        String commitId = firstText(request == null ? null : request.getCommitId(), app.getCurrentCommitId());
        Assert.hasText(versionNumber, "versionNumber不能为空");

        List<FrontendCoverageReport> rawReports = frontendCoverageReportRepository.findByAppAndVersion(appId, versionNumber, commitId);
        Assert.isTrue(!rawReports.isEmpty(), "没有可生成的前端覆盖率上报数据");
        logger.info("frontend coverage report generation matched raw reports, projectId={}, appId={}, version={}, commitId={}, rawCount={}",
                projectId, appId, versionNumber, commitId, rawReports.size());

        Map<String, UniversalCoverageFile> coverageMap = new LinkedHashMap<>();
        CoverageParser parser = coverageParserRegistry.get(SourceType.FRONTEND);
        long lastTimestamp = 0L;
        for (FrontendCoverageReport rawReport : rawReports) {
            lastTimestamp = Math.max(lastTimestamp, rawReport.timestamp == null ? 0L : rawReport.timestamp);
            List<UniversalCoverageFile> files = parser.parse(rawReport.coverageJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

        CoverageReportIndex report = new CoverageReportIndex();
        report.setId(UUID.randomUUID().toString());
        report.setAppId(appId);
        report.setVersionNumber(versionNumber);
        report.setRepoBranch(branch);
        report.setRepoCommitId(commitId);
        report.setCreateTime(new Date());
        report.setSourceType(SourceType.FRONTEND.name());
        report.setLanguage(SourceType.FRONTEND.name());
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

    public static class FrontendCoverageReportRequest {
        private String requestId;
        private String commitId;
        private String versionNumber;
        private String branch;
        private String caseName;
        private String buildId;
        private String testStage;
        private Long timestamp;
        private JsonNode coverage;

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
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
        public JsonNode getCoverage() { return coverage; }
        public void setCoverage(JsonNode coverage) { this.coverage = coverage; }
    }

    public static class FrontendCoverageIngestResult {
        private String rawReportId;
        private String requestId;
        private String projectId;
        private String appId;
        private String versionNumber;
        private String commitId;
        private String branch;

        public FrontendCoverageIngestResult() {
        }

        public FrontendCoverageIngestResult(String rawReportId, String requestId, String projectId, String appId,
                                            String versionNumber, String commitId, String branch) {
            this.rawReportId = rawReportId;
            this.requestId = requestId;
            this.projectId = projectId;
            this.appId = appId;
            this.versionNumber = versionNumber;
            this.commitId = commitId;
            this.branch = branch;
        }

        public String getRawReportId() { return rawReportId; }
        public void setRawReportId(String rawReportId) { this.rawReportId = rawReportId; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getCommitId() { return commitId; }
        public void setCommitId(String commitId) { this.commitId = commitId; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
    }

    public static class FrontendCoverageGenerateRequest {
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
