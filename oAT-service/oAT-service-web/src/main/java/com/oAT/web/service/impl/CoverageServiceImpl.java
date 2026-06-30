package com.oAT.web.service.impl;

import com.oAT.agent.model.*;
import com.oAT.web.common.FriendlyErrorMessageUtil;
import com.oAT.web.common.Job;
import com.oAT.web.coveragecore.report.CoverageComparisonQueryService;
import com.oAT.web.coveragecore.report.CoverageFreshnessService;
import com.oAT.web.coveragecore.report.JavaCoverageReportEngine;
import com.oAT.web.coveragecore.report.CoverageReportDeletionService;
import com.oAT.web.coveragecore.report.CoverageReportExportService;
import com.oAT.web.coveragecore.report.CoverageReportJobService;
import com.oAT.web.coveragecore.report.CoverageReportTypes;
import com.oAT.web.coveragecore.report.CoverageTrendQueryService;
import com.oAT.web.coveragecore.query.CoverageTreeQueryService;
import com.oAT.web.coveragecore.source.CoverageSourceContent;
import com.oAT.web.coveragecore.source.CoverageSourceClassIndexService;
import com.oAT.web.coveragecore.source.CoverageSourceColoringService;
import com.oAT.web.coveragecore.source.CoverageSourceContentService;
import com.oAT.web.esDao.*;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.service.AppService;
import com.oAT.web.service.CoverageService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.CoverageComparisonVo;
import com.oAT.web.service.entity.CoverageTreeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@Service
public class CoverageServiceImpl implements CoverageService, StandardDate {
    private static final Logger logger = LoggerFactory.getLogger(CoverageServiceImpl.class);

    @Autowired
    private TraceNodeRepository traceNodeRepository;
    @Autowired
    private CoverageReportRepository coverageReportRepository;
    @Autowired
    private CoverageSourceContentService coverageSourceContentService;
    @Autowired
    private CoverageSourceClassIndexService coverageSourceClassIndexService;
    @Autowired
    private CoverageSourceColoringService coverageSourceColoringService;
    @Autowired
    private CoverageReportDeletionService coverageReportDeletionService;
    @Autowired
    private CoverageReportExportService coverageReportExportService;
    @Autowired
    private CoverageTrendQueryService coverageTrendQueryService;
    @Autowired
    private CoverageReportJobService coverageReportJobService;
    @Autowired
    private CoverageComparisonQueryService coverageComparisonQueryService;
    @Autowired
    private CoverageFreshnessService coverageFreshnessService;
    @Autowired
    private CoverageTreeQueryService coverageTreeQueryService;
    @Autowired
    private ClassCoverageRepository classCoverageRepository;
    @Autowired
    private AppService appService;
    @Autowired
    private JavaCoverageReportEngine javaCoverageReportEngine;

    @Override
    public String startGenerateJob(String appId, String versionNumber, String branch, String commitId) {
        return startJob(appId, versionNumber, branch, commitId, CoverageReportTypes.VERSION_FULL, null, null);
    }

    @Override
    public String startGenerateCurrentCommitJob(String appId, String versionNumber, String branch, String commitId) {
        return startJob(appId, versionNumber, branch, commitId, CoverageReportTypes.CURRENT_COMMIT, null, null);
    }

    @Override
    public String startGenerateIncrementalJob(String appId, String versionNumber, String branch, String commitId,
                                            String baseVersionNumber, String baseCommitId) {
        return startJob(appId, versionNumber, branch, commitId, CoverageReportTypes.INCREMENTAL, baseVersionNumber, baseCommitId);
    }

    private String startJob(String appId, String versionNumber, String branch, String commitId,
                          Integer reportType, String baseVersionNumber, String baseCommitId) {
        String taskKey = buildReportGenerationKey(appId, versionNumber, commitId, reportType, baseVersionNumber, baseCommitId);

        String appDisplayName = appId;
        try {
            AppVo app = appService.getApp(appId);
            if (app != null) {
                appDisplayName = String.format("%s(%s)", appId, app.getName());
            }
        } catch (Exception e) {
            logger.warn("Get app info failed for logging: {}", appId);
        }

        final String finalAppDisplayName = appDisplayName;
        String jobName = finalAppDisplayName + ":" + versionNumber + " (" + CoverageReportTypes.displayName(reportType) + ")";
        String startMessage = "开始为应用 [" + finalAppDisplayName + "] 生成" + CoverageReportTypes.displayName(reportType) + "覆盖率报告...";
        return coverageReportJobService.startReportGeneration(
                taskKey,
                jobName,
                startMessage,
                job -> generateReport(appId, versionNumber, branch, commitId, reportType, baseVersionNumber, baseCommitId, job),
                e -> toFriendlyError(e.getMessage()));
    }

    private String toFriendlyError(String msg) {
        return FriendlyErrorMessageUtil.general(new RuntimeException(msg));
    }

    @Override
    public Job<String> getJob(String jobId) {
        return coverageReportJobService.getJob(jobId);
    }

    @Override
    public CoverageReportIndex getReport(String reportId) {
        if (!StringUtils.hasText(reportId)) {
            return null;
        }
        return coverageReportRepository.findById(reportId).orElse(null);
    }

    private String buildReportGenerationKey(String appId, String versionNumber, String commitId,
                                           Integer reportType, String baseVersionNumber, String baseCommitId) {
        return String.join("|",
                appId == null ? "" : appId,
                versionNumber == null ? "" : versionNumber,
                commitId == null ? "" : commitId,
                String.valueOf(CoverageReportTypes.normalize(reportType)),
                baseVersionNumber == null ? "" : baseVersionNumber,
                baseCommitId == null ? "" : baseCommitId);
    }

    public String generateReport(String appId, String versionNumber, String branch, String commitId,
                                 Integer reportType, String baseVersionNumber, String baseCommitId, Job<String> job) {
        return javaCoverageReportEngine.generateReport(appId, versionNumber, branch, commitId,
                reportType, baseVersionNumber, baseCommitId, job);
    }

    @Override
    public CoverageReportIndex getLatestReport(String appId, String versionNumber) {
        return getLatestReport(appId, versionNumber, null);
    }

    @Override
    public CoverageReportIndex getLatestReport(String appId, String versionNumber, String commitId) {
        List<CoverageReportIndex> reports;
        if (StringUtils.hasText(commitId)) {
            reports = coverageReportRepository.findByAppIdAndVersionNumberAndRepoCommitId(appId, versionNumber, commitId);
        } else {
            reports = coverageReportRepository.findByAppIdAndVersionNumber(appId, versionNumber);
        }
        return getLatest(reports);
    }

    @Override
    public CoverageReportIndex getLatestReportByType(String appId, String versionNumber, Integer reportType) {
        return getLatestReportByType(appId, versionNumber, reportType, null);
    }

    @Override
    public CoverageReportIndex getLatestReportByType(String appId, String versionNumber, Integer reportType, String commitId) {
        List<CoverageReportIndex> reports;
        if (StringUtils.hasText(commitId)) {
            reports = coverageReportRepository.findByAppIdAndVersionNumberAndReportTypeAndRepoCommitId(appId, versionNumber, reportType, commitId);
        } else {
            reports = coverageReportRepository.findByAppIdAndVersionNumberAndReportType(appId, versionNumber, reportType);
        }
        return getLatest(reports);
    }

    private CoverageReportIndex getLatest(List<CoverageReportIndex> reports) {
        if (reports != null && !reports.isEmpty()) {
            List<CoverageReportIndex> modifiableReports = new ArrayList<>(reports);
            modifiableReports.sort((a, b) -> {
                if (a.getCreateTime() == null) return 1;
                if (b.getCreateTime() == null) return -1;
                return b.getCreateTime().compareTo(a.getCreateTime());
            });
            return modifiableReports.get(0);
        }
        return null;
    }

    @Override
    public void exportReport(String reportId, HttpServletResponse response) throws IOException {
        coverageReportExportService.exportReport(reportId, response);
    }

    @Override
    public void exportMethodReport(String reportId, HttpServletResponse response) throws IOException {
        coverageReportExportService.exportMethodReport(reportId, response);
    }

    @Override
    public ClassCoverageIndex getClassCoverage(String reportId, String className) {
        if (!StringUtils.hasText(reportId) || !StringUtils.hasText(className)) {
            return null;
        }
        return classCoverageRepository.findById(reportId + "_" + className.hashCode()).orElse(null);
    }

    @Override
    public void ensureSourceClassesIndexed(String reportId) {
        coverageSourceClassIndexService.ensureSourceClassesIndexed(reportId);
    }

    @Override
    public CoverageComparisonVo getComparison(String reportId) {
        return coverageComparisonQueryService.getComparison(reportId);
    }

    @Override
    public List<Map<String, Object>> getTrendData(String appId, String versionNumber) {
        return coverageTrendQueryService.getTrendData(appId, versionNumber);
    }

    @Override
    public boolean hasNewerData(String appId, String lastProcessedTime) {
        if (lastProcessedTime == null) return true;
        try {
            String queryTime = lastProcessedTime;
            if (queryTime.length() == 19) {
                queryTime += ",000";
            }
            Date createTime = parse(queryTime);
            Page<TraceNodeIndex> page = traceNodeRepository.findByAppIdAndCreateTimeGreaterThanOrderByCreateTimeAsc(appId, createTime, PageRequest.of(0, 1));
            return page.getTotalElements() > 0;
        } catch (Exception e) {
            logger.warn("Parse lastProcessedTime failed: {}", lastProcessedTime, e);
            return true;
        }
    }

    @Override
    public boolean hasNewerData(String appId, String versionNumber, CoverageReportIndex report) {
        return coverageFreshnessService.hasNewerData(appId, versionNumber, report);
    }

    @Override
    public String getColoredSource(String appId, String reportId, String className) {
        ClassCoverageIndex classCov = getClassCoverage(reportId, className);
        if (classCov == null) return "Coverage data not found for class: " + className;

        return getColoredSource(appId, classCov);
    }

    @Override
    public String getColoredSource(String appId, ClassCoverageIndex classCov) {
        if (classCov == null) {
            return "Coverage data not found.";
        }
        CoverageReportIndex report = StringUtils.hasText(classCov.getReportId())
                ? coverageReportRepository.findById(classCov.getReportId()).orElse(null)
                : null;
        if (report == null) {
            return "Coverage report not found for class: " + classCov.getClassName();
        }
        CoverageSourceContent source = coverageSourceContentService.loadSource(report, classCov);
        if (source.getContent() == null) {
            return StringUtils.hasText(source.getMessage()) ? source.getMessage() : "Source code for " + classCov.getClassName() + " not found.";
        }
        return coverageSourceColoringService.renderColoredSource(source.getContent(), classCov);
    }

    @Override
    public Page<ClassCoverageIndex> getClassCoveragePage(String reportId, String className, String methodName,
                                                         Double minLineRate, Double maxLineRate,
                                                         Double minBranchRate, Double maxBranchRate,
                                                         Double minMethodRate, Double maxMethodRate,
                                                         Integer minComplexity, Integer maxComplexity,
                                                         Pageable pageable) {
        if (reportId == null || reportId.isEmpty()) {
            return new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList(), pageable, 0);
        }
        return classCoverageRepository.findByReportIdFiltered(reportId, className, methodName,
                minLineRate, maxLineRate, minBranchRate, maxBranchRate, minMethodRate, maxMethodRate,
                minComplexity, maxComplexity, pageable);
    }

    @Override
    public List<CoverageTreeNode> getTreeNodes(String reportId, String parentPackage, String classNameSearch, String methodNameSearch,
                                               Double minRate, Double maxRate,
                                               Double minBranchRate, Double maxBranchRate,
                                               Double minMethodRate, Double maxMethodRate,
                                               Integer minComplexity, Integer maxComplexity) {
        return coverageTreeQueryService.getTreeNodes(reportId, parentPackage, classNameSearch, methodNameSearch,
                minRate, maxRate, minBranchRate, maxBranchRate, minMethodRate, maxMethodRate,
                minComplexity, maxComplexity);
    }

    @Override
    public List<CoverageReportIndex> getReportsByAppId(String appId) {
        return coverageReportRepository.findByAppId(appId);
    }

    @Override
    public void deleteReport(String reportId) {
        coverageReportDeletionService.deleteReport(reportId);
    }

}
