package com.oAT.web.api.coverage;

import com.oAT.web.common.Job;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.coverage.universal.SourceType;
import com.oAT.web.coveragecore.report.CoverageReportCommandService;
import com.oAT.web.coveragecore.report.CoverageReportGenerationRequest;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.SystemLog;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SystemLogService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/coverage")
public class CoverageOperationsApiControl {
    private final CoverageReportCommandService coverageReportCommandService;
    private final ProjectService projectService;
    private final AppService appService;
    private final SystemLogService systemLogService;

    public CoverageOperationsApiControl(CoverageReportCommandService coverageReportCommandService,
                                        ProjectService projectService,
                                        AppService appService,
                                        SystemLogService systemLogService) {
        this.coverageReportCommandService = coverageReportCommandService;
        this.projectService = projectService;
        this.appService = appService;
        this.systemLogService = systemLogService;
    }

    @PostMapping("/apps/{appId}/generate-current")
    public ResultNotified<String> generateCurrent(@PathVariable String appId,
                                                  @RequestParam String projectId,
                                                  @SessionAttribute UserVo user,
                                                  @RequestParam(required = false) String versionNumber,
                                                  @RequestParam(required = false) String branch,
                                                  @RequestParam(required = false) String commitId) {
        ensureProjectAccess(projectId, user);
        AppVo app = ensureAppBelongsToProject(projectId, appId);
        String targetVersion = StringUtils.hasText(versionNumber) ? versionNumber : app.getCurrentVersion();
        String targetBranch = StringUtils.hasText(branch) ? branch : app.getCurrentBranch();
        String targetCommit = StringUtils.hasText(commitId) ? commitId : app.getCurrentCommitId();
        Assert.hasText(targetVersion, "当前应用未配置当前版本，无法生成本次 Commit 覆盖率报告");
        Assert.hasText(targetCommit, "当前应用未配置当前 CommitId，无法生成本次 Commit 覆盖率报告");

        String jobId = coverageReportCommandService.startCurrentCommitReport(appId, targetVersion, targetBranch, targetCommit);
        addCoverageLog(projectId, user, String.format("%s 生成了应用 [%s] 的 本次 Commit 覆盖率报告 [版本:%s, 分支:%s, Commit:%s]",
                user.getName(), StringUtils.hasText(app.getName()) ? app.getName() : appId, targetVersion, targetBranch, targetCommit),
                SystemLogService.Action.generateReport.toString());
        return new ResultNotified<>(true, "Current commit task started", jobId);
    }

    @PostMapping("/apps/{appId}/generate-incremental")
    public ResultNotified<String> generateIncremental(@PathVariable String appId,
                                                      @RequestParam String projectId,
                                                      @SessionAttribute UserVo user,
                                                      @RequestParam String versionNumber,
                                                      @RequestParam(required = false) String branch,
                                                      @RequestParam(required = false) String commitId,
                                                      @RequestParam String baseVersionNumber,
                                                      @RequestParam(required = false) String baseCommitId) {
        ensureProjectAccess(projectId, user);
        AppVo app = ensureAppBelongsToProject(projectId, appId);
        String jobId = coverageReportCommandService.startIncrementalReport(appId, versionNumber, branch, commitId, baseVersionNumber, baseCommitId);
        addCoverageLog(projectId, user, String.format("%s 生成了应用 [%s] 的 增量 覆盖率报告 [版本:%s, 基准:%s, 分支:%s, Commit:%s]",
                user.getName(), StringUtils.hasText(app.getName()) ? app.getName() : appId, versionNumber, baseVersionNumber, branch, commitId),
                SystemLogService.Action.generateReport.toString());
        return new ResultNotified<>(true, "Incremental task started", jobId);
    }

    @PostMapping("/apps/{appId}/frontend/generate")
    public ResultNotified<String> generateFrontend(@PathVariable String appId,
                                                   @RequestParam String projectId,
                                                   @SessionAttribute UserVo user,
                                                   @org.springframework.web.bind.annotation.RequestBody(required = false)
                                                   CoverageReportGenerationRequest request) {
        ensureProjectAccess(projectId, user);
        AppVo app = ensureAppBelongsToProject(projectId, appId);
        CoverageReportIndex report = coverageReportCommandService.generateFrontendReport(projectId, appId, request);
        addCoverageLog(projectId, user, String.format("%s 生成了应用 [%s] 的 FRONTEND 覆盖率报告 [版本:%s]",
                        user.getName(), StringUtils.hasText(app.getName()) ? app.getName() : appId, report.getVersionNumber()),
                SystemLogService.Action.generateReport.toString());
        return new ResultNotified<>(true, "前端覆盖率报告生成成功", report.getId());
    }

    @PostMapping("/apps/{appId}/universal/{sourceType}/generate")
    public ResultNotified<String> generateUniversal(@PathVariable String appId,
                                                    @PathVariable String sourceType,
                                                    @RequestParam String projectId,
                                                    @SessionAttribute UserVo user,
                                                    @org.springframework.web.bind.annotation.RequestBody(required = false)
                                                    CoverageReportGenerationRequest request) {
        ensureProjectAccess(projectId, user);
        AppVo app = ensureAppBelongsToProject(projectId, appId);
        SourceType resolvedSourceType = resolveUniversalSourceType(sourceType);
        CoverageReportIndex report = coverageReportCommandService.generateUniversalReport(projectId, appId, resolvedSourceType, request);
        addCoverageLog(projectId, user, String.format("%s 生成了应用 [%s] 的 %s 覆盖率报告 [版本:%s]",
                        user.getName(), StringUtils.hasText(app.getName()) ? app.getName() : appId, resolvedSourceType.name(), report.getVersionNumber()),
                SystemLogService.Action.generateReport.toString());
        return new ResultNotified<>(true, resolvedSourceType.name() + "覆盖率报告生成成功", report.getId());
    }

    @GetMapping("/jobs/{jobId}")
    public Map<String, Object> job(@PathVariable String jobId) {
        Job<String> job = coverageReportCommandService.getJob(jobId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (job == null) {
            result.put("id", jobId);
            result.put("finish", true);
            result.put("success", false);
            result.put("message", "任务不存在或已过期");
            result.put("progress", 100);
            result.put("progressName", "任务不存在或已过期");
            return result;
        }
        Job.JobProgress progress = job.getProgress();
        boolean success = Job.JobState.finish.equals(job.getState());
        boolean failed = Job.JobState.error.equals(job.getState()) || Job.JobState.terminate.equals(job.getState());
        result.put("id", job.getId());
        result.put("state", job.getState().name());
        result.put("data", job.getData());
        result.put("finish", success || failed);
        result.put("success", !failed);
        result.put("message", progress == null ? job.getState().name() : progress.getName());
        result.put("progress", progress == null ? 0 : progress.getPercent());
        result.put("progressName", progress == null ? job.getState().name() : progress.getName());
        return result;
    }

    @GetMapping("/apps/{appId}/trend-data")
    public java.util.List<Map<String, Object>> trendData(@PathVariable String appId,
                                                         @RequestParam String projectId,
                                                         @SessionAttribute UserVo user,
                                                         @RequestParam String versionNumber) {
        ensureProjectAccess(projectId, user);
        ensureAppBelongsToProject(projectId, appId);
        return coverageReportCommandService.getTrendData(appId, versionNumber);
    }

    @PostMapping("/reports/{reportId}/delete")
    public ResultNotified<String> delete(@PathVariable String reportId,
                                         @RequestParam String projectId,
                                         @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        CoverageReportIndex report = coverageReportCommandService.getReport(reportId);
        Assert.notNull(report, "Report not found");
        AppVo app = ensureAppBelongsToProject(projectId, report.getAppId());
        coverageReportCommandService.deleteReport(reportId);
        addCoverageLog(projectId, user, String.format("%s 删除了应用 [%s] 的 %s 覆盖率报告 [版本:%s] - %s",
                        user.getName(),
                        app == null ? report.getAppId() : app.getName(),
                        reportTypeLabel(report.getReportType()),
                        report.getVersionNumber(),
                        reportId),
                SystemLogService.Action.deleteReport.toString());
        return new ResultNotified<>(true, "报告已删除", reportId);
    }

    private SourceType resolveUniversalSourceType(String value) {
        SourceType sourceType = SourceType.from(value);
        Assert.isTrue(sourceType == SourceType.CPP || sourceType == SourceType.GO || sourceType == SourceType.PYTHON,
                "通用覆盖率接口仅支持 CPP、GO、PYTHON");
        return sourceType;
    }

    private ProjectVo ensureProjectAccess(String projectId, UserVo user) {
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        Assert.notNull(project, "找不到指定项目,或者您没有该项目的访问权限");
        return project;
    }

    private AppVo ensureAppBelongsToProject(String projectId, String appId) {
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        Assert.isTrue(projectId.equals(app.getCreateProjectId()), "应用不属于当前项目");
        return app;
    }

    private void addCoverageLog(String projectId, UserVo user, String title, String action) {
        SystemLog log = new SystemLog();
        log.setTitle(title);
        log.setUserId(user.getId());
        log.setUserName(user.getName());
        log.setProjectId(projectId);
        log.setAction(action);
        systemLogService.addLog(log);
    }

    private String reportTypeLabel(Integer reportType) {
        if (Integer.valueOf(1).equals(reportType)) {
            return "增量";
        }
        if (Integer.valueOf(2).equals(reportType)) {
            return "本次 Commit";
        }
        return "版本全量";
    }
}
