package com.oAT.web.control.api;

import com.oAT.web.api.coverage.CoverageApiPayloads.*;
import com.oAT.web.api.coverage.CoverageApiPayloadMapper;
import com.oAT.web.common.Job;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.coveragecore.model.SourceCoveragePayload;
import com.oAT.web.coveragecore.query.CoverageCoreQueryService;
import com.oAT.web.coveragecore.query.CoverageUnitPage;
import com.oAT.web.coveragecore.query.CoverageUnitQuery;
import com.oAT.web.coveragecore.report.CoverageOverviewQueryService;
import com.oAT.web.coveragecore.report.CoverageFootprintSnapshotService;
import com.oAT.web.coveragecore.report.CoverageFootprintSnapshotService.CoverageFootprintSnapshot;
import com.oAT.web.coveragecore.report.CoverageReportAnalysisService;
import com.oAT.web.coveragecore.report.CoverageReportCommandService;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.SystemLog;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.VersionService;
import com.oAT.web.service.SystemLogService;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.CoverageTreeNode;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UsecaseVo;
import com.oAT.web.service.entity.UserVo;
import com.oAT.web.service.entity.VersionItemVo;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/coverage")
public class CoverageApiControl {

    private final CoverageCoreQueryService coverageCoreQueryService;
    private final CoverageReportCommandService coverageReportCommandService;
    private final CoverageReportAnalysisService coverageReportAnalysisService;
    private final CoverageOverviewQueryService coverageOverviewQueryService;
    private final CoverageFootprintSnapshotService coverageFootprintSnapshotService;
    private final ProjectService projectService;
    private final AppService appService;
    private final VersionService versionService;
    private final SystemLogService systemLogService;
    private final UsecaseService usecaseService;
    private final CoverageApiPayloadMapper coverageApiPayloadMapper;

    public CoverageApiControl(CoverageCoreQueryService coverageCoreQueryService,
                              CoverageReportCommandService coverageReportCommandService,
                              CoverageReportAnalysisService coverageReportAnalysisService,
                              CoverageOverviewQueryService coverageOverviewQueryService,
                              CoverageFootprintSnapshotService coverageFootprintSnapshotService,
                              ProjectService projectService,
                              AppService appService,
                              VersionService versionService,
                              SystemLogService systemLogService,
                              UsecaseService usecaseService,
                              CoverageApiPayloadMapper coverageApiPayloadMapper) {
        this.coverageCoreQueryService = coverageCoreQueryService;
        this.coverageReportCommandService = coverageReportCommandService;
        this.coverageReportAnalysisService = coverageReportAnalysisService;
        this.coverageOverviewQueryService = coverageOverviewQueryService;
        this.coverageFootprintSnapshotService = coverageFootprintSnapshotService;
        this.projectService = projectService;
        this.appService = appService;
        this.versionService = versionService;
        this.systemLogService = systemLogService;
        this.usecaseService = usecaseService;
        this.coverageApiPayloadMapper = coverageApiPayloadMapper;
    }



    @GetMapping("/export")
    public void export(@PathVariable String projectId,
                       @SessionAttribute UserVo user,
                       @RequestParam String reportId,
        HttpServletResponse response) throws IOException {
        ensureProjectAccess(projectId, user);
        coverageReportCommandService.exportReport(reportId, response);
    }

    @GetMapping("/export-methods")
    public void exportMethods(@PathVariable String projectId,
                              @SessionAttribute UserVo user,
                              @RequestParam String reportId,
        HttpServletResponse response) throws IOException {
        ensureProjectAccess(projectId, user);
        coverageReportCommandService.exportMethodReport(reportId, response);
    }

    @PostMapping("/generate")
    public ResultNotified<String> generate(@PathVariable String projectId,
                                           @SessionAttribute UserVo user,
                                           @RequestParam String appId,
                                           @RequestParam String versionNumber,
                                           @RequestParam(required = false) String branch,
                                           @RequestParam(required = false) String commitId) {
        ensureProjectAccess(projectId, user);
        String jobId = coverageReportCommandService.startVersionFullReport(appId, versionNumber, branch, commitId);
        AppVo app = appService.getApp(appId);
        String appName = app == null ? appId : app.getName();
        addCoverageLog(projectId, user, String.format("%s 生成了应用 [%s] 的 全量 覆盖率报告 [版本:%s, 分支:%s, Commit:%s]",
                user.getName(), appName, versionNumber, branch, commitId));
        return new ResultNotified<>(true, "Task started", jobId);
    }

    @PostMapping("/generate-current")
    public ResultNotified<String> generateCurrent(@PathVariable String projectId,
                                                  @SessionAttribute UserVo user,
                                                  @RequestParam String appId,
                                                  @RequestParam(required = false) String versionNumber,
                                                  @RequestParam(required = false) String branch,
                                                  @RequestParam(required = false) String commitId) {
        ensureProjectAccess(projectId, user);
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        String targetVersion = StringUtils.hasText(versionNumber) ? versionNumber : app.getCurrentVersion();
        String targetBranch = StringUtils.hasText(branch) ? branch : app.getCurrentBranch();
        String targetCommit = StringUtils.hasText(commitId) ? commitId : app.getCurrentCommitId();
        Assert.hasText(targetVersion, "当前应用未配置当前版本，无法生成本次 Commit 覆盖率报告");
        Assert.hasText(targetCommit, "当前应用未配置当前 CommitId，无法生成本次 Commit 覆盖率报告");

        String jobId = coverageReportCommandService.startCurrentCommitReport(appId, targetVersion, targetBranch, targetCommit);
        String appName = StringUtils.hasText(app.getName()) ? app.getName() : appId;
        addCoverageLog(projectId, user, String.format("%s 生成了应用 [%s] 的 本次 Commit 覆盖率报告 [版本:%s, 分支:%s, Commit:%s]",
                user.getName(), appName, targetVersion, targetBranch, targetCommit));
        return new ResultNotified<>(true, "Current commit task started", jobId);
    }

    @PostMapping("/generate-incremental")
    public ResultNotified<String> generateIncremental(@PathVariable String projectId,
                                                      @SessionAttribute UserVo user,
                                                      @RequestParam String appId,
                                                      @RequestParam String versionNumber,
                                                      @RequestParam(required = false) String branch,
                                                      @RequestParam(required = false) String commitId,
                                                      @RequestParam String baseVersionNumber,
                                                      @RequestParam(required = false) String baseCommitId) {
        ensureProjectAccess(projectId, user);
        String jobId = coverageReportCommandService.startIncrementalReport(appId, versionNumber, branch, commitId, baseVersionNumber, baseCommitId);
        AppVo app = appService.getApp(appId);
        String appName = app == null ? appId : app.getName();
        addCoverageLog(projectId, user, String.format("%s 生成了应用 [%s] 的 增量 覆盖率报告 [版本:%s, 基准:%s, 分支:%s, Commit:%s]",
                user.getName(), appName, versionNumber, baseVersionNumber, branch, commitId));
        return new ResultNotified<>(true, "Incremental task started", jobId);
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

    @GetMapping("/trend-data")
    public List<Map<String, Object>> trendData(@PathVariable String projectId,
                                               @SessionAttribute UserVo user,
                                               @RequestParam String appId,
                                               @RequestParam String versionNumber) {
        ensureProjectAccess(projectId, user);
        return coverageReportCommandService.getTrendData(appId, versionNumber);
    }

    @GetMapping("/footprints")
    public ResultNotified<List<CoverageFootprintSnapshot>> footprints(@PathVariable String projectId,
                                                                      @SessionAttribute UserVo user,
                                                                      @RequestParam(required = false) String appId,
                                                                      @RequestParam(required = false) String language,
                                                                      @RequestParam(required = false) String versionNumber,
                                                                      @RequestParam(required = false) String commitId) {
        ensureProjectAccess(projectId, user);
        return new ResultNotified<>(true, "获取覆盖率足迹快照成功",
                coverageFootprintSnapshotService.listFootprints(projectId, appId, language, versionNumber, commitId));
    }

    @GetMapping("/footprints/{footprintKey}")
    public ResultNotified<CoverageFootprintSnapshot> footprintDetail(@PathVariable String projectId,
                                                                     @PathVariable String footprintKey,
                                                                     @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        return new ResultNotified<>(true, "获取覆盖率足迹详情成功",
                coverageFootprintSnapshotService.getFootprint(projectId, footprintKey));
    }

    @PostMapping("/footprints/{footprintKey}/delete")
    public ResultNotified<String> deleteFootprint(@PathVariable String projectId,
                                                  @PathVariable String footprintKey,
                                                  @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        CoverageFootprintSnapshot deleted = coverageFootprintSnapshotService.deleteFootprint(projectId, footprintKey);
        if (StringUtils.hasText(deleted.getSnapshotKey())) {
            usecaseService.removeCoverageFootprintRelation(deleted.getSnapshotKey());
        }
        if (StringUtils.hasText(deleted.getRawReportId())) {
            usecaseService.removeCoverageFootprintRelation(deleted.getRawReportId());
        }
        addCoverageLog(projectId, user, String.format("%s 删除了覆盖率足迹 [%s]",
                user.getName(), footprintKey));
        return new ResultNotified<>(true, "覆盖率足迹已删除", footprintKey);
    }

    @GetMapping("/footprints/{footprintKey}/usecases")
    public ResultNotified<List<UsecaseVo>> footprintUsecases(@PathVariable String projectId,
                                                             @PathVariable String footprintKey,
                                                             @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        return new ResultNotified<>(true, "获取覆盖率足迹关联用例成功",
                usecaseService.getUsecasesByCoverageFootprint(projectId, footprintKey));
    }

    @PostMapping("/footprints/{footprintKey}/usecases/bind")
    public ResultNotified<Integer> bindFootprintUsecases(@PathVariable String projectId,
                                                         @PathVariable String footprintKey,
                                                         @SessionAttribute UserVo user,
                                                         @org.springframework.web.bind.annotation.RequestBody Map<String, String[]> body) {
        ensureProjectAccess(projectId, user);
        String[] usecaseIds = body == null ? new String[0] : body.getOrDefault("usecaseIds", new String[0]);
        usecaseService.bindCoverageFootprintToUsecases(projectId, user.getId(), footprintKey, usecaseIds);
        addCoverageLog(projectId, user, String.format("%s 关联了覆盖率足迹 [%s] 的用例，数量:%d",
                user.getName(), footprintKey, usecaseIds.length));
        return new ResultNotified<>(true, "覆盖率足迹关联用例成功", usecaseIds.length);
    }

    @PostMapping("/delete")
    public ResultNotified<String> delete(@PathVariable String projectId,
                                         @SessionAttribute UserVo user,
                                         @RequestParam String reportId) {
        ensureProjectAccess(projectId, user);
        CoverageReportIndex report = coverageReportCommandService.getReport(reportId);
        Assert.notNull(report, "Report not found");
        coverageReportCommandService.deleteReport(reportId);
        AppVo app = appService.getApp(report.getAppId());
        String appName = app == null ? report.getAppId() : app.getName();
        String reportType = reportTypeLabel(report.getReportType());
        SystemLog log = new SystemLog();
        log.setTitle(String.format("%s 删除了应用 [%s] 的 %s 覆盖率报告 [版本:%s] - %s",
                user.getName(), appName, reportType, report.getVersionNumber(), reportId));
        log.setUserId(user.getId());
        log.setUserName(user.getName());
        log.setProjectId(projectId);
        log.setAction(SystemLogService.Action.deleteReport.toString());
        systemLogService.addLog(log);
        return new ResultNotified<>(true, "报告已删除", reportId);
    }

    @GetMapping("/overview")
    public ResultNotified<CoverageOverviewPayload> overview(@PathVariable String projectId,
                                                            @SessionAttribute UserVo user,
                                                            @RequestParam String appId,
                                                            @RequestParam String versionNumber,
                                                            @RequestParam(required = false) String reportId,
                                                            @RequestParam(required = false) String commitId) {
        ensureProjectAccess(projectId, user);
        Assert.hasText(appId, "appId不能为空");
        Assert.hasText(versionNumber, "versionNumber不能为空");

        CoverageOverviewPayload payload = coverageApiPayloadMapper.toLegacyOverviewPayload(
                coverageOverviewQueryService.getOverview(projectId, appId, versionNumber, reportId, commitId, user),
                projectId);
        return new ResultNotified<>(true, "获取覆盖率概览成功", payload);
    }

    @GetMapping("/details")
    public ResultNotified<CoverageDetailsPayload> details(@PathVariable String projectId,
                                                          @SessionAttribute UserVo user,
                                                          @RequestParam String reportId,
                                                          @RequestParam(defaultValue = "list") String viewType,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size,
                                                          @RequestParam(required = false) String className,
                                                          @RequestParam(required = false) String methodName,
                                                          @RequestParam(required = false) Double minRate,
                                                          @RequestParam(required = false) Double maxRate,
                                                          @RequestParam(required = false) Double minBranchRate,
                                                          @RequestParam(required = false) Double maxBranchRate,
                                                          @RequestParam(required = false) Double minMethodRate,
                                                          @RequestParam(required = false) Double maxMethodRate,
                                                          @RequestParam(required = false) Integer minComplexity,
                                                          @RequestParam(required = false) Integer maxComplexity) {
        ensureProjectAccess(projectId, user);
        Assert.hasText(reportId, "reportId不能为空");

        CoverageReportIndex report = coverageCoreQueryService.getReport(reportId);
        Assert.notNull(report, "覆盖率报告不存在");
        AppVo app = appService.getApp(report.getAppId());

        CoverageDetailsPayload payload = new CoverageDetailsPayload();
        payload.setReport(coverageApiPayloadMapper.toCoverageReportSummary(report));
        payload.setApp(coverageApiPayloadMapper.toAppSummary(app));
        payload.setVersion(coverageApiPayloadMapper.toVersionSummary(findVersion(projectId, report.getAppId(), report.getVersionNumber(), report.getRepoCommitId())));
        payload.setReportNeedRegenerate(coverageReportAnalysisService.hasNewerData(report.getAppId(), report.getVersionNumber(), report));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        payload.setViewType(viewType);
        payload.setClassName(className);
        payload.setMethodName(methodName);
        payload.setMinRate(minRate);
        payload.setMaxRate(maxRate);
        payload.setMinBranchRate(minBranchRate);
        payload.setMaxBranchRate(maxBranchRate);
        payload.setMinMethodRate(minMethodRate);
        payload.setMaxMethodRate(maxMethodRate);
        payload.setMinComplexity(minComplexity);
        payload.setMaxComplexity(maxComplexity);

        CoverageUnitQuery query = buildUnitQuery(className, methodName, minRate, maxRate, minBranchRate, maxBranchRate,
                minMethodRate, maxMethodRate, minComplexity, maxComplexity, page, size);

        if ("tree".equalsIgnoreCase(viewType)) {
            payload.setTreeNodes(coverageCoreQueryService.listTreeNodes(reportId, "", query));
        } else {
            CoverageUnitPage unitPage = coverageCoreQueryService.listUnits(reportId, query);
            payload.setClassPage(coverageApiPayloadMapper.toPageSummary(unitPage));
        }
        return new ResultNotified<>(true, "获取覆盖率明细成功", payload);
    }

    @GetMapping("/tree-nodes")
    public ResultNotified<List<CoverageTreeNode>> treeNodes(@PathVariable String projectId,
                                                            @SessionAttribute UserVo user,
                                                            @RequestParam String reportId,
                                                            @RequestParam(required = false) String parentPackage,
                                                            @RequestParam(required = false) String className,
                                                            @RequestParam(required = false) String methodName,
                                                            @RequestParam(required = false) Double minRate,
                                                            @RequestParam(required = false) Double maxRate,
                                                            @RequestParam(required = false) Double minBranchRate,
                                                            @RequestParam(required = false) Double maxBranchRate,
                                                            @RequestParam(required = false) Double minMethodRate,
                                                            @RequestParam(required = false) Double maxMethodRate,
                                                            @RequestParam(required = false) Integer minComplexity,
                                                            @RequestParam(required = false) Integer maxComplexity) {
        ensureProjectAccess(projectId, user);
        Assert.hasText(reportId, "reportId不能为空");
        CoverageUnitQuery query = buildUnitQuery(className, methodName, minRate, maxRate, minBranchRate, maxBranchRate,
                minMethodRate, maxMethodRate, minComplexity, maxComplexity, null, null);
        return new ResultNotified<>(true, "获取覆盖率树节点成功",
                coverageCoreQueryService.listTreeNodes(reportId, parentPackage, query));
    }

    @GetMapping("/code")
    public ResultNotified<CoverageCodePayload> code(@PathVariable String projectId,
                                                    @SessionAttribute UserVo user,
                                                    @RequestParam String appId,
                                                    @RequestParam String reportId,
                                                    @RequestParam String className) {
        ensureProjectAccess(projectId, user);
        Assert.hasText(appId, "appId不能为空");
        Assert.hasText(reportId, "reportId不能为空");
        Assert.hasText(className, "className不能为空");

        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        CoverageReportIndex report = coverageCoreQueryService.getReport(reportId);
        Assert.notNull(report, "覆盖率报告不存在");
        List<com.oAT.web.coveragecore.report.CoverageMethodSummary> methods = coverageCoreQueryService.listMethods(reportId, className);
        SourceCoveragePayload sourceCoverage = coverageCoreQueryService.getSourceCoverage(reportId, className);

        CoverageCodePayload payload = coverageApiPayloadMapper.toCoverageCodePayload(
                app, report, className, methods, sourceCoverage, resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取覆盖率源码详情成功", payload);
    }


    private void addCoverageLog(String projectId, UserVo user, String title) {
        SystemLog log = new SystemLog();
        log.setTitle(title);
        log.setUserId(user.getId());
        log.setUserName(user.getName());
        log.setProjectId(projectId);
        log.setAction(SystemLogService.Action.generateReport.toString());
        systemLogService.addLog(log);
    }

    private VersionItemVo findVersion(String projectId, String appId, String versionNumber, String commitId) {
        List<VersionItemVo> versions = versionService.getVersionItemList(projectId, appId);
        for (VersionItemVo item : versions) {
            if (!versionNumber.equals(item.getVersionNumber())) {
                continue;
            }
            if (!StringUtils.hasText(commitId) || commitId.equals(item.getRepoCommitId())) {
                return item;
            }
        }
        return versions.stream().filter(item -> versionNumber.equals(item.getVersionNumber())).findFirst().orElse(null);
    }

    private boolean isSameCommit(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equals(right.trim());
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

    private ProjectVo ensureProjectAccess(String projectId, UserVo user) {
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        Assert.notNull(project, "找不到指定项目,或者您没有该项目的访问权限");
        return project;
    }

    private String resolveUserRole(String projectId, UserVo user) {
        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);
        for (ProjectMemberVo member : members) {
            if (user.getName() != null && user.getName().equals(member.getMemberName()) && member.getRole() != null) {
                return member.getRole().name();
            }
        }
        return ProjectMemberVo.Role.visitor.name();
    }

    private CoverageUnitQuery buildUnitQuery(String className,
                                             String methodName,
                                             Double minRate,
                                             Double maxRate,
                                             Double minBranchRate,
                                             Double maxBranchRate,
                                             Double minMethodRate,
                                             Double maxMethodRate,
                                             Integer minComplexity,
                                             Integer maxComplexity,
                                             Integer page,
                                             Integer size) {
        CoverageUnitQuery query = new CoverageUnitQuery();
        query.setClassName(className);
        query.setMethodName(methodName);
        query.setMinRate(minRate);
        query.setMaxRate(maxRate);
        query.setMinBranchRate(minBranchRate);
        query.setMaxBranchRate(maxBranchRate);
        query.setMinMethodRate(minMethodRate);
        query.setMaxMethodRate(maxMethodRate);
        query.setMinComplexity(minComplexity);
        query.setMaxComplexity(maxComplexity);
        query.setPage(page);
        query.setSize(size);
        return query;
    }

}
