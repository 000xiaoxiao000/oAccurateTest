package com.oAT.web.control.api;

import com.oAT.web.common.Job;
import com.oAT.web.common.PaletteColors;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.SystemLog;
import com.oAT.web.service.AppService;
import com.oAT.web.service.CoverageService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.VersionService;
import com.oAT.web.service.SystemLogService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.CoverageComparisonVo;
import com.oAT.web.service.entity.CoverageTreeNode;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import com.oAT.web.service.entity.VersionItemVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects/{projectId}/coverage")
public class CoverageApiControl {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final CoverageService coverageService;
    private final ProjectService projectService;
    private final AppService appService;
    private final VersionService versionService;
    private final SystemLogService systemLogService;

    public CoverageApiControl(CoverageService coverageService,
                              ProjectService projectService,
                              AppService appService,
                              VersionService versionService,
                              SystemLogService systemLogService) {
        this.coverageService = coverageService;
        this.projectService = projectService;
        this.appService = appService;
        this.versionService = versionService;
        this.systemLogService = systemLogService;
    }



    @GetMapping("/export")
    public void export(@PathVariable String projectId,
                       @SessionAttribute UserVo user,
                       @RequestParam String reportId,
                       HttpServletResponse response) throws IOException {
        ensureProjectAccess(projectId, user);
        coverageService.exportReport(reportId, response);
    }

    @GetMapping("/export-methods")
    public void exportMethods(@PathVariable String projectId,
                              @SessionAttribute UserVo user,
                              @RequestParam String reportId,
                              HttpServletResponse response) throws IOException {
        ensureProjectAccess(projectId, user);
        coverageService.exportMethodReport(reportId, response);
    }

    @PostMapping("/generate")
    public ResultNotified<String> generate(@PathVariable String projectId,
                                           @SessionAttribute UserVo user,
                                           @RequestParam String appId,
                                           @RequestParam String versionNumber,
                                           @RequestParam(required = false) String branch,
                                           @RequestParam(required = false) String commitId) {
        ensureProjectAccess(projectId, user);
        String jobId = coverageService.startGenerateJob(appId, versionNumber, branch, commitId);
        AppVo app = appService.getApp(appId);
        String appName = app == null ? appId : app.getName();
        addCoverageLog(projectId, user, String.format("%s 生成了应用 [%s] 的 全量 覆盖率报告 [版本:%s, 分支:%s, Commit:%s]",
                user.getName(), appName, versionNumber, branch, commitId));
        return new ResultNotified<>(true, "Task started", jobId);
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
        String jobId = coverageService.startGenerateIncrementalJob(appId, versionNumber, branch, commitId, baseVersionNumber, baseCommitId);
        AppVo app = appService.getApp(appId);
        String appName = app == null ? appId : app.getName();
        addCoverageLog(projectId, user, String.format("%s 生成了应用 [%s] 的 增量 覆盖率报告 [版本:%s, 基准:%s, 分支:%s, Commit:%s]",
                user.getName(), appName, versionNumber, baseVersionNumber, branch, commitId));
        return new ResultNotified<>(true, "Incremental task started", jobId);
    }

    @GetMapping("/jobs/{jobId}")
    public Job<String> job(@PathVariable String jobId) {
        return coverageService.getJob(jobId);
    }

    @GetMapping("/trend-data")
    public List<Map<String, Object>> trendData(@PathVariable String projectId,
                                               @SessionAttribute UserVo user,
                                               @RequestParam String appId,
                                               @RequestParam String versionNumber) {
        ensureProjectAccess(projectId, user);
        return coverageService.getTrendData(appId, versionNumber);
    }

    @PostMapping("/delete")
    public ResultNotified<String> delete(@PathVariable String projectId,
                                         @SessionAttribute UserVo user,
                                         @RequestParam String reportId) {
        ensureProjectAccess(projectId, user);
        CoverageReportIndex report = coverageService.getReport(reportId);
        Assert.notNull(report, "Report not found");
        coverageService.deleteReport(reportId);
        AppVo app = appService.getApp(report.getAppId());
        String appName = app == null ? report.getAppId() : app.getName();
        String reportType = Integer.valueOf(1).equals(report.getReportType()) ? "增量" : "全量";
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

        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");

        CoverageReportIndex report = StringUtils.hasText(reportId)
                ? coverageService.getReport(reportId)
                : coverageService.getLatestReport(appId, versionNumber, commitId);

        CoverageReportIndex fullReport = null;
        CoverageReportIndex incrementalReport = null;
        if (report != null) {
            String targetCommitId = StringUtils.hasText(commitId) ? commitId : report.getRepoCommitId();
            if (Integer.valueOf(1).equals(report.getReportType())) {
                incrementalReport = report;
                fullReport = coverageService.getLatestReportByType(appId, versionNumber, 0, targetCommitId);
            } else {
                fullReport = report;
                incrementalReport = coverageService.getLatestReportByType(appId, versionNumber, 1, targetCommitId);
            }
        }

        boolean hasNewerData = fullReport == null
                ? coverageService.hasNewerData(appId, versionNumber, null)
                : coverageService.hasNewerData(appId, versionNumber, fullReport);

        CoverageOverviewPayload payload = new CoverageOverviewPayload();
        payload.setProject(toProjectSummary(projectService.getProject(projectId)));
        payload.setApps(toAppSummaries(appService.getAppList(projectId)));
        payload.setApp(toAppSummary(app));
        payload.setVersion(toVersionSummary(findVersion(projectId, appId, versionNumber, commitId)));
        payload.setReport(toCoverageReportSummary(fullReport));
        payload.setIncrementalReport(toCoverageReportSummary(incrementalReport));
        payload.setHasNewerData(hasNewerData);
        payload.setComparison(toComparisonSummary(fullReport == null ? null : coverageService.getComparison(fullReport.getId())));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        payload.setMascotPrimary(computeMascotPrimary(projectId, payload.getProject().getName()));
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

        CoverageReportIndex report = coverageService.getReport(reportId);
        Assert.notNull(report, "覆盖率报告不存在");
        AppVo app = appService.getApp(report.getAppId());

        CoverageDetailsPayload payload = new CoverageDetailsPayload();
        payload.setReport(toCoverageReportSummary(report));
        payload.setApp(toAppSummary(app));
        payload.setVersion(toVersionSummary(findVersion(projectId, report.getAppId(), report.getVersionNumber(), report.getRepoCommitId())));
        payload.setReportNeedRegenerate(coverageService.hasNewerData(report.getAppId(), report.getVersionNumber(), report));
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

        if ("tree".equalsIgnoreCase(viewType)) {
            payload.setTreeNodes(coverageService.getTreeNodes(reportId, "", className, methodName,
                    minRate, maxRate, minBranchRate, maxBranchRate, minMethodRate, maxMethodRate, minComplexity, maxComplexity));
        } else {
            Page<ClassCoverageIndex> classPage = coverageService.getClassCoveragePage(reportId, className, methodName,
                    minRate, maxRate, minBranchRate, maxBranchRate, minMethodRate, maxMethodRate, minComplexity, maxComplexity,
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lineRate", "branchRate", "methodRate")));
            payload.setClassPage(toPageSummary(classPage));
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
        return new ResultNotified<>(true, "获取覆盖率树节点成功", coverageService.getTreeNodes(reportId, parentPackage, className, methodName,
                minRate, maxRate, minBranchRate, maxBranchRate, minMethodRate, maxMethodRate, minComplexity, maxComplexity));
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
        CoverageReportIndex report = coverageService.getReport(reportId);
        Assert.notNull(report, "覆盖率报告不存在");

        ClassCoverageIndex classCoverage = coverageService.getClassCoverage(reportId, className);
        if (classCoverage != null && classCoverage.getMethods() != null) {
            classCoverage.getMethods().sort((left, right) -> {
                double leftRate = left.getTotalLines() > 0 ? (double) left.getCoveredLines() / left.getTotalLines() : 0;
                double rightRate = right.getTotalLines() > 0 ? (double) right.getCoveredLines() / right.getTotalLines() : 0;
                if (leftRate != rightRate) {
                    return Double.compare(rightRate, leftRate);
                }
                double leftBranchRate = left.getBranchRate() == null ? 0 : left.getBranchRate();
                double rightBranchRate = right.getBranchRate() == null ? 0 : right.getBranchRate();
                return Double.compare(rightBranchRate, leftBranchRate);
            });
        }

        CoverageCodePayload payload = new CoverageCodePayload();
        payload.setApp(toAppSummary(app));
        payload.setReport(toCoverageReportSummary(report));
        payload.setClassName(className);
        payload.setMethods(toMethodSummaries(classCoverage == null ? null : classCoverage.getMethods()));
        payload.setColoredSourceHtml(coverageService.getColoredSource(appId, classCoverage));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
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

    private String computeMascotPrimary(String projectId, String projectName) {
        int hash = Math.abs((projectId + ":" + projectName).hashCode());
        return PaletteColors.pickPrimary(hash / 5 + 13);
    }

    private ProjectSummary toProjectSummary(ProjectVo project) {
        ProjectSummary summary = new ProjectSummary();
        summary.setId(project.getId());
        summary.setName(project.getName());
        summary.setDescribe(project.getDescribe());
        return summary;
    }

    private AppSummary toAppSummary(AppVo app) {
        AppSummary summary = new AppSummary();
        if (app == null) {
            return summary;
        }
        summary.setId(app.getId());
        summary.setName(app.getName());
        summary.setCurrentVersion(app.getCurrentVersion());
        summary.setCurrentBranch(app.getCurrentBranch());
        summary.setCurrentCommitId(app.getCurrentCommitId());
        return summary;
    }

    private List<AppSummary> toAppSummaries(List<AppVo> apps) {
        return apps.stream().map(this::toAppSummary).collect(Collectors.toList());
    }

    private VersionSummary toVersionSummary(VersionItemVo item) {
        if (item == null) {
            return null;
        }
        VersionSummary summary = new VersionSummary();
        summary.setId(item.getId());
        summary.setVersionNumber(item.getVersionNumber());
        summary.setDescribe(item.getDescribe());
        summary.setRepoBranch(item.getRepoBranch());
        summary.setRepoCommitId(item.getRepoCommitId());
        summary.setProgramFile(item.getProgramFile());
        summary.setProgramName(item.getProgramName());
        summary.setCreateTimeText(formatDate(item.getCreateTime()));
        return summary;
    }

    private CoverageReportSummary toCoverageReportSummary(CoverageReportIndex report) {
        if (report == null) {
            return null;
        }
        CoverageReportSummary summary = new CoverageReportSummary();
        summary.setId(report.getId());
        summary.setAppId(report.getAppId());
        summary.setVersionNumber(report.getVersionNumber());
        summary.setRepoBranch(report.getRepoBranch());
        summary.setRepoCommitId(report.getRepoCommitId());
        summary.setCreateTimeText(formatDate(report.getCreateTime()));
        summary.setLastProcessedTime(report.getLastProcessedTime());
        summary.setTotalClasses(report.getTotalClasses());
        summary.setCoveredClasses(report.getCoveredClasses());
        summary.setTotalMethods(report.getTotalMethods());
        summary.setCoveredMethods(report.getCoveredMethods());
        summary.setTotalBranches(report.getTotalBranches());
        summary.setCoveredBranches(report.getCoveredBranches());
        summary.setTotalBranchTargets(report.getTotalBranchTargets());
        summary.setCoveredBranchTargets(report.getCoveredBranchTargets());
        summary.setTotalLines(report.getTotalLines());
        summary.setCoveredLines(report.getCoveredLines());
        summary.setTotalComplexity(report.getTotalComplexity());
        summary.setSnapshotCount(report.getSnapshotCount());
        summary.setReportType(report.getReportType());
        summary.setBaseVersionNumber(report.getBaseVersionNumber());
        summary.setBaseRepoCommitId(report.getBaseRepoCommitId());
        return summary;
    }

    private CoverageComparisonSummary toComparisonSummary(CoverageComparisonVo comparison) {
        if (comparison == null) {
            return null;
        }
        CoverageComparisonSummary summary = new CoverageComparisonSummary();
        summary.setAddedCount(comparison.getAddedCount());
        summary.setStableCount(comparison.getStableCount());
        summary.setDecreasedCount(comparison.getDecreasedCount());
        summary.setAddedMethods(toMethodDiffSummaries(comparison.getAddedMethods()));
        summary.setStableMethods(toMethodDiffSummaries(comparison.getStableMethods()));
        summary.setDecreasedMethods(toMethodDiffSummaries(comparison.getDecreasedMethods()));
        return summary;
    }

    private List<MethodDiffSummary> toMethodDiffSummaries(List<CoverageComparisonVo.MethodDiff> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        return items.stream().map(item -> {
            MethodDiffSummary summary = new MethodDiffSummary();
            summary.setClassName(item.getClassName());
            summary.setMethodName(item.getMethodName());
            summary.setMethodDesc(item.getMethodDesc());
            return summary;
        }).collect(Collectors.toList());
    }

    private PageSummary<ClassCoverageSummary> toPageSummary(Page<ClassCoverageIndex> page) {
        PageSummary<ClassCoverageSummary> summary = new PageSummary<>();
        summary.setContent(page.getContent().stream().map(this::toClassCoverageSummary).collect(Collectors.toList()));
        summary.setPage(page.getNumber());
        summary.setSize(page.getSize());
        summary.setTotalElements(page.getTotalElements());
        summary.setTotalPages(page.getTotalPages());
        return summary;
    }

    private ClassCoverageSummary toClassCoverageSummary(ClassCoverageIndex item) {
        ClassCoverageSummary summary = new ClassCoverageSummary();
        summary.setClassName(item.getClassName());
        summary.setTotalMethods(item.getTotalMethods());
        summary.setCoveredMethods(item.getCoveredMethods());
        summary.setTotalBranches(item.getTotalBranches());
        summary.setCoveredBranches(item.getCoveredBranches());
        summary.setTotalBranchTargets(item.getTotalBranchTargets());
        summary.setCoveredBranchTargets(item.getCoveredBranchTargets());
        summary.setTotalLines(item.getTotalLines());
        summary.setCoveredLines(item.getCoveredLines());
        summary.setTotalComplexity(item.getTotalComplexity());
        summary.setLineRate(item.getLineRate());
        summary.setBranchRate(item.getBranchRate());
        summary.setMethodRate(item.getMethodRate());
        return summary;
    }

    private List<MethodCoverageSummary> toMethodSummaries(List<ClassCoverageIndex.MethodCoverageDetail> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        return items.stream().map(item -> {
            MethodCoverageSummary summary = new MethodCoverageSummary();
            summary.setMethodName(item.getMethodName());
            summary.setMethodDesc(item.getMethodDesc());
            summary.setTotalLines(item.getTotalLines());
            summary.setCoveredLines(item.getCoveredLines());
            summary.setTotalBranches(item.getTotalBranches());
            summary.setCoveredBranches(item.getCoveredBranches());
            summary.setComplexity(item.getComplexity());
            summary.setCovered(item.isCovered());
            summary.setTotalBranchTargets(item.getTotalBranchTargets());
            summary.setCoveredBranchTargets(item.getCoveredBranchTargets());
            summary.setBranchRate(item.getBranchRate());
            return summary;
        }).collect(Collectors.toList());
    }

    private String formatDate(java.util.Date date) {
        return date == null ? null : new SimpleDateFormat(DATE_TIME_PATTERN, Locale.CHINA).format(date);
    }

    public static class CoverageOverviewPayload {
        private ProjectSummary project;
        private List<AppSummary> apps;
        private AppSummary app;
        private VersionSummary version;
        private CoverageReportSummary report;
        private CoverageReportSummary incrementalReport;
        private Boolean hasNewerData;
        private CoverageComparisonSummary comparison;
        private String currentUserRole;
        private String mascotPrimary;

        public ProjectSummary getProject() { return project; }
        public void setProject(ProjectSummary project) { this.project = project; }
        public List<AppSummary> getApps() { return apps; }
        public void setApps(List<AppSummary> apps) { this.apps = apps; }
        public AppSummary getApp() { return app; }
        public void setApp(AppSummary app) { this.app = app; }
        public VersionSummary getVersion() { return version; }
        public void setVersion(VersionSummary version) { this.version = version; }
        public CoverageReportSummary getReport() { return report; }
        public void setReport(CoverageReportSummary report) { this.report = report; }
        public CoverageReportSummary getIncrementalReport() { return incrementalReport; }
        public void setIncrementalReport(CoverageReportSummary incrementalReport) { this.incrementalReport = incrementalReport; }
        public Boolean getHasNewerData() { return hasNewerData; }
        public void setHasNewerData(Boolean hasNewerData) { this.hasNewerData = hasNewerData; }
        public CoverageComparisonSummary getComparison() { return comparison; }
        public void setComparison(CoverageComparisonSummary comparison) { this.comparison = comparison; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
        public String getMascotPrimary() { return mascotPrimary; }
        public void setMascotPrimary(String mascotPrimary) { this.mascotPrimary = mascotPrimary; }
    }

    public static class CoverageDetailsPayload {
        private CoverageReportSummary report;
        private AppSummary app;
        private VersionSummary version;
        private Boolean reportNeedRegenerate;
        private String currentUserRole;
        private String viewType;
        private String className;
        private String methodName;
        private Double minRate;
        private Double maxRate;
        private Double minBranchRate;
        private Double maxBranchRate;
        private Double minMethodRate;
        private Double maxMethodRate;
        private Integer minComplexity;
        private Integer maxComplexity;
        private PageSummary<ClassCoverageSummary> classPage;
        private List<CoverageTreeNode> treeNodes;

        public CoverageReportSummary getReport() { return report; }
        public void setReport(CoverageReportSummary report) { this.report = report; }
        public AppSummary getApp() { return app; }
        public void setApp(AppSummary app) { this.app = app; }
        public VersionSummary getVersion() { return version; }
        public void setVersion(VersionSummary version) { this.version = version; }
        public Boolean getReportNeedRegenerate() { return reportNeedRegenerate; }
        public void setReportNeedRegenerate(Boolean reportNeedRegenerate) { this.reportNeedRegenerate = reportNeedRegenerate; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
        public String getViewType() { return viewType; }
        public void setViewType(String viewType) { this.viewType = viewType; }
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public Double getMinRate() { return minRate; }
        public void setMinRate(Double minRate) { this.minRate = minRate; }
        public Double getMaxRate() { return maxRate; }
        public void setMaxRate(Double maxRate) { this.maxRate = maxRate; }
        public Double getMinBranchRate() { return minBranchRate; }
        public void setMinBranchRate(Double minBranchRate) { this.minBranchRate = minBranchRate; }
        public Double getMaxBranchRate() { return maxBranchRate; }
        public void setMaxBranchRate(Double maxBranchRate) { this.maxBranchRate = maxBranchRate; }
        public Double getMinMethodRate() { return minMethodRate; }
        public void setMinMethodRate(Double minMethodRate) { this.minMethodRate = minMethodRate; }
        public Double getMaxMethodRate() { return maxMethodRate; }
        public void setMaxMethodRate(Double maxMethodRate) { this.maxMethodRate = maxMethodRate; }
        public Integer getMinComplexity() { return minComplexity; }
        public void setMinComplexity(Integer minComplexity) { this.minComplexity = minComplexity; }
        public Integer getMaxComplexity() { return maxComplexity; }
        public void setMaxComplexity(Integer maxComplexity) { this.maxComplexity = maxComplexity; }
        public PageSummary<ClassCoverageSummary> getClassPage() { return classPage; }
        public void setClassPage(PageSummary<ClassCoverageSummary> classPage) { this.classPage = classPage; }
        public List<CoverageTreeNode> getTreeNodes() { return treeNodes; }
        public void setTreeNodes(List<CoverageTreeNode> treeNodes) { this.treeNodes = treeNodes; }
    }

    public static class CoverageCodePayload {
        private AppSummary app;
        private CoverageReportSummary report;
        private String className;
        private List<MethodCoverageSummary> methods;
        private String coloredSourceHtml;
        private String currentUserRole;

        public AppSummary getApp() { return app; }
        public void setApp(AppSummary app) { this.app = app; }
        public CoverageReportSummary getReport() { return report; }
        public void setReport(CoverageReportSummary report) { this.report = report; }
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public List<MethodCoverageSummary> getMethods() { return methods; }
        public void setMethods(List<MethodCoverageSummary> methods) { this.methods = methods; }
        public String getColoredSourceHtml() { return coloredSourceHtml; }
        public void setColoredSourceHtml(String coloredSourceHtml) { this.coloredSourceHtml = coloredSourceHtml; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class CoverageComparisonSummary {
        private int addedCount;
        private int stableCount;
        private int decreasedCount;
        private List<MethodDiffSummary> addedMethods;
        private List<MethodDiffSummary> stableMethods;
        private List<MethodDiffSummary> decreasedMethods;

        public int getAddedCount() { return addedCount; }
        public void setAddedCount(int addedCount) { this.addedCount = addedCount; }
        public int getStableCount() { return stableCount; }
        public void setStableCount(int stableCount) { this.stableCount = stableCount; }
        public int getDecreasedCount() { return decreasedCount; }
        public void setDecreasedCount(int decreasedCount) { this.decreasedCount = decreasedCount; }
        public List<MethodDiffSummary> getAddedMethods() { return addedMethods; }
        public void setAddedMethods(List<MethodDiffSummary> addedMethods) { this.addedMethods = addedMethods; }
        public List<MethodDiffSummary> getStableMethods() { return stableMethods; }
        public void setStableMethods(List<MethodDiffSummary> stableMethods) { this.stableMethods = stableMethods; }
        public List<MethodDiffSummary> getDecreasedMethods() { return decreasedMethods; }
        public void setDecreasedMethods(List<MethodDiffSummary> decreasedMethods) { this.decreasedMethods = decreasedMethods; }
    }

    public static class MethodDiffSummary {
        private String className;
        private String methodName;
        private String methodDesc;

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getMethodDesc() { return methodDesc; }
        public void setMethodDesc(String methodDesc) { this.methodDesc = methodDesc; }
    }

    public static class PageSummary<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;

        public List<T> getContent() { return content; }
        public void setContent(List<T> content) { this.content = content; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public long getTotalElements() { return totalElements; }
        public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    }

    public static class ProjectSummary {
        private String id;
        private String name;
        private String describe;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescribe() { return describe; }
        public void setDescribe(String describe) { this.describe = describe; }
    }

    public static class AppSummary {
        private String id;
        private String name;
        private String currentVersion;
        private String currentBranch;
        private String currentCommitId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }
        public String getCurrentBranch() { return currentBranch; }
        public void setCurrentBranch(String currentBranch) { this.currentBranch = currentBranch; }
        public String getCurrentCommitId() { return currentCommitId; }
        public void setCurrentCommitId(String currentCommitId) { this.currentCommitId = currentCommitId; }
    }

    public static class VersionSummary {
        private String id;
        private String versionNumber;
        private String describe;
        private String repoBranch;
        private String repoCommitId;
        private String programFile;
        private String programName;
        private String createTimeText;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getDescribe() { return describe; }
        public void setDescribe(String describe) { this.describe = describe; }
        public String getRepoBranch() { return repoBranch; }
        public void setRepoBranch(String repoBranch) { this.repoBranch = repoBranch; }
        public String getRepoCommitId() { return repoCommitId; }
        public void setRepoCommitId(String repoCommitId) { this.repoCommitId = repoCommitId; }
        public String getProgramFile() { return programFile; }
        public void setProgramFile(String programFile) { this.programFile = programFile; }
        public String getProgramName() { return programName; }
        public void setProgramName(String programName) { this.programName = programName; }
        public String getCreateTimeText() { return createTimeText; }
        public void setCreateTimeText(String createTimeText) { this.createTimeText = createTimeText; }
    }

    public static class CoverageReportSummary {
        private String id;
        private String appId;
        private String versionNumber;
        private String repoBranch;
        private String repoCommitId;
        private String createTimeText;
        private String lastProcessedTime;
        private long totalClasses;
        private long coveredClasses;
        private long totalMethods;
        private long coveredMethods;
        private long totalBranches;
        private long coveredBranches;
        private long totalBranchTargets;
        private long coveredBranchTargets;
        private long totalLines;
        private long coveredLines;
        private int totalComplexity;
        private Integer snapshotCount;
        private Integer reportType;
        private String baseVersionNumber;
        private String baseRepoCommitId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getRepoBranch() { return repoBranch; }
        public void setRepoBranch(String repoBranch) { this.repoBranch = repoBranch; }
        public String getRepoCommitId() { return repoCommitId; }
        public void setRepoCommitId(String repoCommitId) { this.repoCommitId = repoCommitId; }
        public String getCreateTimeText() { return createTimeText; }
        public void setCreateTimeText(String createTimeText) { this.createTimeText = createTimeText; }
        public String getLastProcessedTime() { return lastProcessedTime; }
        public void setLastProcessedTime(String lastProcessedTime) { this.lastProcessedTime = lastProcessedTime; }
        public long getTotalClasses() { return totalClasses; }
        public void setTotalClasses(long totalClasses) { this.totalClasses = totalClasses; }
        public long getCoveredClasses() { return coveredClasses; }
        public void setCoveredClasses(long coveredClasses) { this.coveredClasses = coveredClasses; }
        public long getTotalMethods() { return totalMethods; }
        public void setTotalMethods(long totalMethods) { this.totalMethods = totalMethods; }
        public long getCoveredMethods() { return coveredMethods; }
        public void setCoveredMethods(long coveredMethods) { this.coveredMethods = coveredMethods; }
        public long getTotalBranches() { return totalBranches; }
        public void setTotalBranches(long totalBranches) { this.totalBranches = totalBranches; }
        public long getCoveredBranches() { return coveredBranches; }
        public void setCoveredBranches(long coveredBranches) { this.coveredBranches = coveredBranches; }
        public long getTotalBranchTargets() { return totalBranchTargets; }
        public void setTotalBranchTargets(long totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
        public long getCoveredBranchTargets() { return coveredBranchTargets; }
        public void setCoveredBranchTargets(long coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
        public long getTotalLines() { return totalLines; }
        public void setTotalLines(long totalLines) { this.totalLines = totalLines; }
        public long getCoveredLines() { return coveredLines; }
        public void setCoveredLines(long coveredLines) { this.coveredLines = coveredLines; }
        public int getTotalComplexity() { return totalComplexity; }
        public void setTotalComplexity(int totalComplexity) { this.totalComplexity = totalComplexity; }
        public Integer getSnapshotCount() { return snapshotCount; }
        public void setSnapshotCount(Integer snapshotCount) { this.snapshotCount = snapshotCount; }
        public Integer getReportType() { return reportType; }
        public void setReportType(Integer reportType) { this.reportType = reportType; }
        public String getBaseVersionNumber() { return baseVersionNumber; }
        public void setBaseVersionNumber(String baseVersionNumber) { this.baseVersionNumber = baseVersionNumber; }
        public String getBaseRepoCommitId() { return baseRepoCommitId; }
        public void setBaseRepoCommitId(String baseRepoCommitId) { this.baseRepoCommitId = baseRepoCommitId; }
    }

    public static class ClassCoverageSummary {
        private String className;
        private int totalMethods;
        private int coveredMethods;
        private int totalBranches;
        private int coveredBranches;
        private int totalBranchTargets;
        private int coveredBranchTargets;
        private int totalLines;
        private int coveredLines;
        private int totalComplexity;
        private Double lineRate;
        private Double branchRate;
        private Double methodRate;

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public int getTotalMethods() { return totalMethods; }
        public void setTotalMethods(int totalMethods) { this.totalMethods = totalMethods; }
        public int getCoveredMethods() { return coveredMethods; }
        public void setCoveredMethods(int coveredMethods) { this.coveredMethods = coveredMethods; }
        public int getTotalBranches() { return totalBranches; }
        public void setTotalBranches(int totalBranches) { this.totalBranches = totalBranches; }
        public int getCoveredBranches() { return coveredBranches; }
        public void setCoveredBranches(int coveredBranches) { this.coveredBranches = coveredBranches; }
        public int getTotalBranchTargets() { return totalBranchTargets; }
        public void setTotalBranchTargets(int totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
        public int getCoveredBranchTargets() { return coveredBranchTargets; }
        public void setCoveredBranchTargets(int coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
        public int getTotalLines() { return totalLines; }
        public void setTotalLines(int totalLines) { this.totalLines = totalLines; }
        public int getCoveredLines() { return coveredLines; }
        public void setCoveredLines(int coveredLines) { this.coveredLines = coveredLines; }
        public int getTotalComplexity() { return totalComplexity; }
        public void setTotalComplexity(int totalComplexity) { this.totalComplexity = totalComplexity; }
        public Double getLineRate() { return lineRate; }
        public void setLineRate(Double lineRate) { this.lineRate = lineRate; }
        public Double getBranchRate() { return branchRate; }
        public void setBranchRate(Double branchRate) { this.branchRate = branchRate; }
        public Double getMethodRate() { return methodRate; }
        public void setMethodRate(Double methodRate) { this.methodRate = methodRate; }
    }

    public static class MethodCoverageSummary {
        private String methodName;
        private String methodDesc;
        private int totalLines;
        private int coveredLines;
        private int totalBranches;
        private int coveredBranches;
        private int complexity;
        private boolean covered;
        private int totalBranchTargets;
        private int coveredBranchTargets;
        private Double branchRate;

        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getMethodDesc() { return methodDesc; }
        public void setMethodDesc(String methodDesc) { this.methodDesc = methodDesc; }
        public int getTotalLines() { return totalLines; }
        public void setTotalLines(int totalLines) { this.totalLines = totalLines; }
        public int getCoveredLines() { return coveredLines; }
        public void setCoveredLines(int coveredLines) { this.coveredLines = coveredLines; }
        public int getTotalBranches() { return totalBranches; }
        public void setTotalBranches(int totalBranches) { this.totalBranches = totalBranches; }
        public int getCoveredBranches() { return coveredBranches; }
        public void setCoveredBranches(int coveredBranches) { this.coveredBranches = coveredBranches; }
        public int getComplexity() { return complexity; }
        public void setComplexity(int complexity) { this.complexity = complexity; }
        public boolean isCovered() { return covered; }
        public void setCovered(boolean covered) { this.covered = covered; }
        public int getTotalBranchTargets() { return totalBranchTargets; }
        public void setTotalBranchTargets(int totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
        public int getCoveredBranchTargets() { return coveredBranchTargets; }
        public void setCoveredBranchTargets(int coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
        public Double getBranchRate() { return branchRate; }
        public void setBranchRate(Double branchRate) { this.branchRate = branchRate; }
    }
}
