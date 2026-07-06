package com.oAT.web.api.analytics;

import com.oAT.web.analytics.gate.QualityGateResult;
import com.oAT.web.analytics.testgap.TestGapAnalysisService;
import com.oAT.web.analytics.testgap.TestGapReport;
import com.oAT.web.analytics.tia.TestImpactAnalysisReport;
import com.oAT.web.analytics.tia.TestImpactAnalysisService;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.coveragecore.query.CoverageCoreQueryService;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v2/coverage")
public class CoverageAnalyticsApiControl {
    private final TestGapAnalysisService testGapAnalysisService;
    private final TestImpactAnalysisService testImpactAnalysisService;
    private final CoverageCoreQueryService coverageCoreQueryService;
    private final ProjectService projectService;
    private final AppService appService;

    public CoverageAnalyticsApiControl(TestGapAnalysisService testGapAnalysisService,
                                       TestImpactAnalysisService testImpactAnalysisService,
                                       CoverageCoreQueryService coverageCoreQueryService,
                                       ProjectService projectService,
                                       AppService appService) {
        this.testGapAnalysisService = testGapAnalysisService;
        this.testImpactAnalysisService = testImpactAnalysisService;
        this.coverageCoreQueryService = coverageCoreQueryService;
        this.projectService = projectService;
        this.appService = appService;
    }

    @GetMapping("/reports/{reportId}/test-gap")
    public ResultNotified<TestGapReport> testGap(@PathVariable String reportId,
                                                 @RequestParam String projectId,
                                                 @SessionAttribute UserVo user) {
        ensureReportAccess(projectId, reportId, user);
        return new ResultNotified<>(true, "获取测试缺口分析成功", testGapAnalysisService.analyze(reportId));
    }

    @GetMapping("/reports/{reportId}/quality-gate")
    public ResultNotified<QualityGateResult> qualityGate(@PathVariable String reportId,
                                                         @RequestParam String projectId,
                                                         @RequestParam(defaultValue = "80") double minLineCoverageRate,
                                                         @SessionAttribute UserVo user) {
        ensureReportAccess(projectId, reportId, user);
        return new ResultNotified<>(true, "获取质量门禁结果成功", testGapAnalysisService.evaluateGate(reportId, minLineCoverageRate));
    }

    @GetMapping("/apps/{appId}/quality-gate")
    public ResultNotified<QualityGateResult> appQualityGate(@PathVariable String appId,
                                                            @RequestParam String projectId,
                                                            @RequestParam(required = false) String versionNumber,
                                                            @RequestParam(required = false) String commitId,
                                                            @RequestParam(required = false) String buildId,
                                                            @RequestParam(required = false) String testStage,
                                                            @RequestParam(required = false) Integer reportType,
                                                            @RequestParam(defaultValue = "80") double minLineCoverageRate,
                                                            @SessionAttribute UserVo user) {
        ensureAppAccess(projectId, appId, user);
        CoverageReportIndex report = selectQualityGateReport(appId, versionNumber, commitId, buildId, testStage, reportType);
        if (report == null) {
            return new ResultNotified<>(true, "未找到匹配的覆盖率报告",
                    unmatchedQualityGateResult(appId, versionNumber, commitId, buildId, testStage, minLineCoverageRate));
        }
        return new ResultNotified<>(true, "获取应用质量门禁结果成功",
                testGapAnalysisService.evaluateGate(report.getId(), minLineCoverageRate));
    }

    @GetMapping("/reports/{reportId}/test-impact")
    public ResultNotified<TestImpactAnalysisReport> testImpact(@PathVariable String reportId,
                                                               @RequestParam String projectId,
                                                               @RequestParam(required = false) String changedLines,
                                                               @SessionAttribute UserVo user) {
        ensureReportAccess(projectId, reportId, user);
        return new ResultNotified<>(true, "获取测试影响分析成功", testImpactAnalysisService.analyze(reportId, changedLines));
    }

    private void ensureReportAccess(String projectId, String reportId, UserVo user) {
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        Assert.notNull(project, "找不到指定项目,或者您没有该项目的访问权限");
        CoverageReportIndex report = coverageCoreQueryService.getReport(reportId);
        AppVo app = appService.getApp(report.getAppId());
        Assert.notNull(app, "应用不存在");
        Assert.isTrue(projectId.equals(app.getCreateProjectId()), "应用不属于当前项目");
    }

    private void ensureAppAccess(String projectId, String appId, UserVo user) {
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        Assert.notNull(project, "找不到指定项目,或者您没有该项目的访问权限");
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        Assert.isTrue(projectId.equals(app.getCreateProjectId()), "应用不属于当前项目");
    }

    private CoverageReportIndex selectQualityGateReport(String appId,
                                                        String versionNumber,
                                                        String commitId,
                                                        String buildId,
                                                        String testStage,
                                                        Integer reportType) {
        List<CoverageReportIndex> reports = coverageCoreQueryService.listReportsByAppId(appId).stream()
                .filter(report -> matches(versionNumber, report.getVersionNumber()))
                .filter(report -> matches(commitId, report.getRepoCommitId()))
                .filter(report -> matches(buildId, report.getBuildId()))
                .filter(report -> matches(testStage, report.getTestStage()))
                .filter(report -> reportType == null || reportType.equals(report.getReportType()))
                .sorted(Comparator.comparing(CoverageReportIndex::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return reports.isEmpty() ? null : reports.get(0);
    }

    private QualityGateResult unmatchedQualityGateResult(String appId,
                                                         String versionNumber,
                                                         String commitId,
                                                         String buildId,
                                                         String testStage,
                                                         double minLineCoverageRate) {
        QualityGateResult result = new QualityGateResult();
        result.setAppId(appId);
        result.setVersionNumber(versionNumber);
        result.setRepoCommitId(commitId);
        result.setBuildId(buildId);
        result.setTestStage(testStage);
        result.setPassed(false);
        result.setMinLineCoverageRate(minLineCoverageRate);
        result.getReasons().add("未找到匹配的覆盖率报告，无法执行质量门禁");
        result.getReasons().add("请检查应用、版本、Commit、Build、测试阶段或报告类型是否与报告元数据一致");
        return result;
    }

    private boolean matches(String expected, String actual) {
        return !StringUtils.hasText(expected) || expected.equals(actual);
    }
}
