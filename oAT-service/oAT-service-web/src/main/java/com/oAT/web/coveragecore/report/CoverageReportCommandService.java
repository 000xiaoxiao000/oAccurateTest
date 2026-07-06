package com.oAT.web.coveragecore.report;

import com.oAT.web.common.Job;
import com.oAT.web.coverage.universal.SourceType;
import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.common.FriendlyErrorMessageUtil;
import com.oAT.web.service.AppService;
import com.oAT.web.service.entity.AppVo;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class CoverageReportCommandService {
    private final JavaCoverageReportEngine javaCoverageReportEngine;
    private final CoverageReportJobService coverageReportJobService;
    private final CoverageReportDeletionService coverageReportDeletionService;
    private final CoverageReportExportService coverageReportExportService;
    private final CoverageTrendQueryService coverageTrendQueryService;
    private final CoverageReportRepository coverageReportRepository;
    private final MultiLanguageCoverageReportService multiLanguageCoverageReportService;
    private final AppService appService;

    public CoverageReportCommandService(JavaCoverageReportEngine javaCoverageReportEngine,
                                        CoverageReportJobService coverageReportJobService,
                                        CoverageReportDeletionService coverageReportDeletionService,
                                        CoverageReportExportService coverageReportExportService,
                                        CoverageTrendQueryService coverageTrendQueryService,
                                        CoverageReportRepository coverageReportRepository,
                                        MultiLanguageCoverageReportService multiLanguageCoverageReportService,
                                        AppService appService) {
        this.javaCoverageReportEngine = javaCoverageReportEngine;
        this.coverageReportJobService = coverageReportJobService;
        this.coverageReportDeletionService = coverageReportDeletionService;
        this.coverageReportExportService = coverageReportExportService;
        this.coverageTrendQueryService = coverageTrendQueryService;
        this.coverageReportRepository = coverageReportRepository;
        this.multiLanguageCoverageReportService = multiLanguageCoverageReportService;
        this.appService = appService;
    }

    public String startCurrentCommitReport(String appId, String versionNumber, String branch, String commitId) {
        return startReport(appId, versionNumber, branch, commitId,
                JavaCoverageReportEngine.REPORT_TYPE_CURRENT_COMMIT, null, null);
    }

    public String startVersionFullReport(String appId, String versionNumber, String branch, String commitId) {
        return startReport(appId, versionNumber, branch, commitId,
                JavaCoverageReportEngine.REPORT_TYPE_VERSION_FULL, null, null);
    }

    public String startIncrementalReport(String appId,
                                         String versionNumber,
                                         String branch,
                                         String commitId,
                                         String baseVersionNumber,
                                         String baseCommitId) {
        return startReport(appId, versionNumber, branch, commitId,
                JavaCoverageReportEngine.REPORT_TYPE_INCREMENTAL, baseVersionNumber, baseCommitId);
    }

    public CoverageReportIndex generateFrontendReport(String projectId,
                                                      String appId,
                                                      CoverageReportGenerationRequest request) {
        return multiLanguageCoverageReportService.generateFrontendReport(projectId, appId, request);
    }

    public CoverageReportIndex generateUniversalReport(String projectId,
                                                       String appId,
                                                       SourceType sourceType,
                                                       CoverageReportGenerationRequest request) {
        return multiLanguageCoverageReportService.generateUniversalReport(projectId, appId, sourceType, request);
    }

    public Job<String> getJob(String jobId) {
        return coverageReportJobService.getJob(jobId);
    }

    public List<Map<String, Object>> getTrendData(String appId, String versionNumber) {
        return coverageTrendQueryService.getTrendData(appId, versionNumber);
    }

    public List<CoverageReportIndex> getReportsByAppId(String appId) {
        if (appId == null || appId.isBlank()) {
            return List.of();
        }
        return coverageReportRepository.findByAppId(appId);
    }

    public CoverageReportIndex getReport(String reportId) {
        if (reportId == null || reportId.isBlank()) {
            return null;
        }
        return coverageReportRepository.findById(reportId).orElse(null);
    }

    public void deleteReport(String reportId) {
        coverageReportDeletionService.deleteReport(reportId);
    }

    public void exportReport(String reportId, HttpServletResponse response) throws IOException {
        coverageReportExportService.exportReport(reportId, response);
    }

    public void exportMethodReport(String reportId, HttpServletResponse response) throws IOException {
        coverageReportExportService.exportMethodReport(reportId, response);
    }

    private String startReport(String appId,
                               String versionNumber,
                               String branch,
                               String commitId,
                               Integer reportType,
                               String baseVersionNumber,
                               String baseCommitId) {
        String taskKey = buildReportGenerationKey(appId, versionNumber, commitId, reportType, baseVersionNumber, baseCommitId);
        AppVo app = null;
        String appDisplayName = appId;
        try {
            app = appService.getApp(appId);
            if (app != null) {
                appDisplayName = String.format("%s(%s)", appId, app.getName());
            }
        } catch (Exception ignored) {
            appDisplayName = appId;
        }
        String finalAppDisplayName = appDisplayName;
        String jobName = finalAppDisplayName + ":" + versionNumber + " (" + reportTypeName(reportType) + ")";
        String startMessage = "开始为应用 [" + finalAppDisplayName + "] 生成" + reportTypeName(reportType) + "覆盖率报告...";
        AppVo finalApp = app;
        return coverageReportJobService.startReportGeneration(
                taskKey,
                jobName,
                startMessage,
                job -> {
                    CoverageLanguage language = resolveAppLanguage(finalApp);
                    if (language == CoverageLanguage.JAVA) {
                        return javaCoverageReportEngine.generateReport(appId, versionNumber, branch, commitId, reportType, baseVersionNumber, baseCommitId, job);
                    }
                    job.getProgress().next("合并" + language.name() + "覆盖率上报数据", 80);
                    CoverageReportGenerationRequest request = buildMultiLanguageRequest(
                            versionNumber, branch, commitId, reportType, baseVersionNumber, baseCommitId);
                    String projectId = finalApp == null ? null : finalApp.getCreateProjectId();
                    CoverageReportIndex report = language == CoverageLanguage.FRONTEND
                            ? multiLanguageCoverageReportService.generateFrontendReport(projectId, appId, request)
                            : multiLanguageCoverageReportService.generateUniversalReport(projectId, appId, language.toSourceType(), request);
                    return report.getId();
                },
                e -> FriendlyErrorMessageUtil.general(new RuntimeException(e.getMessage())));
    }

    private CoverageReportGenerationRequest buildMultiLanguageRequest(String versionNumber,
                                                                      String branch,
                                                                      String commitId,
                                                                      Integer reportType,
                                                                      String baseVersionNumber,
                                                                      String baseCommitId) {
        CoverageReportGenerationRequest request = new CoverageReportGenerationRequest();
        request.setVersionNumber(versionNumber);
        request.setBranch(branch);
        request.setCommitId(commitId);
        request.setReportType(normalizeReportType(reportType));
        request.setBaseVersionNumber(baseVersionNumber);
        request.setBaseCommitId(baseCommitId);
        return request;
    }

    private CoverageLanguage resolveAppLanguage(AppVo app) {
        if (app == null) {
            return CoverageLanguage.JAVA;
        }
        return CoverageLanguage.from(app.getLanguage());
    }

    private String buildReportGenerationKey(String appId,
                                            String versionNumber,
                                            String commitId,
                                            Integer reportType,
                                            String baseVersionNumber,
                                            String baseCommitId) {
        return String.join("|",
                appId == null ? "" : appId,
                versionNumber == null ? "" : versionNumber,
                commitId == null ? "" : commitId,
                String.valueOf(normalizeReportType(reportType)),
                baseVersionNumber == null ? "" : baseVersionNumber,
                baseCommitId == null ? "" : baseCommitId);
    }

    private String reportTypeName(Integer reportType) {
        int normalizedType = normalizeReportType(reportType);
        if (normalizedType == JavaCoverageReportEngine.REPORT_TYPE_INCREMENTAL) {
            return "增量";
        }
        if (normalizedType == JavaCoverageReportEngine.REPORT_TYPE_CURRENT_COMMIT) {
            return "本次 Commit";
        }
        return "版本全量";
    }

    private int normalizeReportType(Integer reportType) {
        return reportType == null ? JavaCoverageReportEngine.REPORT_TYPE_VERSION_FULL : reportType;
    }
}
