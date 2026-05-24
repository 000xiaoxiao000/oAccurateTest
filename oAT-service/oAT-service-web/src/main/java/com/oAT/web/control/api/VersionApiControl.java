package com.oAT.web.control.api;

import com.oAT.web.common.DateUtil;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.VersionCompareReport;
import com.oAT.web.esDao.entity.SystemLog;
import com.oAT.web.service.AppService;
import com.oAT.web.service.CoverageService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.GitService;
import com.oAT.web.service.ResourceService;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.SystemLogService;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.VersionService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.CompareJobVo;
import com.oAT.web.service.entity.GitCommitOptionVo;
import com.oAT.web.service.entity.GitJobVo;
import com.oAT.web.service.entity.GitPullEstimateVo;
import com.oAT.web.service.entity.PackageCommitVerifyVo;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UsecaseDirectoryVo;
import com.oAT.web.service.entity.UsecaseVo;
import com.oAT.web.service.entity.UserVo;
import com.oAT.web.service.entity.VersionCompareReportVo;
import com.oAT.web.service.entity.VersionItemVo;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class VersionApiControl {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final VersionService versionService;
    private final AppService appService;
    private final ProjectService projectService;
    private final CoverageService coverageService;
    private final UsecaseService usecaseService;
    private final GitService gitService;
    private final ResourceService resourceService;
    private final ClientSessionService clientSessionService;
    private final SystemLogService systemLogService;

    public VersionApiControl(VersionService versionService,
                             AppService appService,
                             ProjectService projectService,
                             CoverageService coverageService,
                             UsecaseService usecaseService,
                             GitService gitService,
                             ResourceService resourceService,
                             ClientSessionService clientSessionService,
                             SystemLogService systemLogService) {
        this.versionService = versionService;
        this.appService = appService;
        this.projectService = projectService;
        this.coverageService = coverageService;
        this.usecaseService = usecaseService;
        this.gitService = gitService;
        this.resourceService = resourceService;
        this.clientSessionService = clientSessionService;
        this.systemLogService = systemLogService;
    }

    @GetMapping("/apps/{appId}/version-center")
    public ResultNotified<VersionCenterPayload> versionCenter(@PathVariable String projectId,
                                                              @PathVariable String appId,
                                                              @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");

        List<VersionItemVo> versions = new ArrayList<>(versionService.getVersionItemList(projectId, appId));
        versions.sort(Comparator.comparing(VersionItemVo::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));

        List<VersionCompareReportVo> compareReports = new ArrayList<>(versionService.getCompareReportList(projectId, appId));
        compareReports.sort(Comparator.comparing(VersionCompareReportVo::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));

        List<CoverageReportIndex> coverageReports = new ArrayList<>(coverageService.getReportsByAppId(appId));
        coverageReports.sort(Comparator.comparing(CoverageReportIndex::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));

        VersionCenterPayload payload = new VersionCenterPayload();
        payload.setApp(toAppSummary(app));
        payload.setApps(toAppSummaries(appService.getAppList(projectId)));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        payload.setVersions(versions.stream().map(item -> toVersionItemSummary(item, app)).collect(Collectors.toList()));
        payload.setPackageVersions(versions.stream()
                .filter(item -> {
                    String file = item.getProgramFile();
                    if (!StringUtils.hasText(file)) {
                        return false;
                    }
                    String lower = file.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".jar") || lower.endsWith(".war");
                })
                .map(item -> toVersionItemSummary(item, app))
                .collect(Collectors.toList()));
        payload.setCompareReports(compareReports.stream().map(this::toCompareReportSummary).collect(Collectors.toList()));
        payload.setCoverageReports(coverageReports.stream()
                .map(report -> toCoverageReportCard(report, coverageService.hasNewerData(appId, report.getVersionNumber(), report)))
                .collect(Collectors.toList()));
        return new ResultNotified<>(true, "获取版本中心数据成功", payload);
    }


    @PostMapping("/apps/{appId}/versions")
    public ResultNotified<String> createVersion(@PathVariable String projectId,
                                                @PathVariable String appId,
                                                @SessionAttribute UserVo user,
                                                VersionItemVo itemVo) {
        ensureProjectAccess(projectId, user);
        itemVo.setProjectId(projectId);
        itemVo.setAppId(appId);
        if ("git".equals(itemVo.getSourceType())) {
            VersionItemVo existing = versionService.getVersionByGitInfo(appId, itemVo.getVersionNumber(), itemVo.getRepoBranch(), itemVo.getRepoCommitId());
            if (existing != null) {
                return new ResultNotified<>(false, "该版本号下已存在相同的分支和 CommitID");
            }
            if (StringUtils.hasText(itemVo.getProgramFile())) {
                File programFile = new File(itemVo.getProgramFile());
                if (!programFile.exists()) {
                    programFile = new File(resourceService.getCacheRoot(), itemVo.getProgramFile());
                }
                if (programFile.exists() && !StringUtils.hasText(itemVo.getProgramName())) {
                    itemVo.setProgramName(programFile.getName());
                }
            }
        }
        versionService.addVersionItem(itemVo);
        if (versionService.getVersionItemList(projectId, appId).size() == 1 && "on".equals(itemVo.getSetAsCurrent())) {
            AppVo app = appService.getApp(appId);
            app.setCurrentVersion(itemVo.getVersionNumber());
            app.setCurrentBranch(itemVo.getRepoBranch());
            app.setCurrentCommitId(itemVo.getRepoCommitId());
            appService.updateApp(projectId, app);
        }
        return new ResultNotified<>(true, "版本创建成功");
    }

    @PostMapping("/apps/{appId}/versions/current")
    public ResultNotified<String> setCurrentVersion(@PathVariable String projectId,
                                                    @PathVariable String appId,
                                                    @SessionAttribute UserVo user,
                                                    @RequestParam String versionNumber,
                                                    @RequestParam(required = false) String branch,
                                                    @RequestParam(required = false) String commitId) {
        ensureProjectAccess(projectId, user);
        if (isVisitor(projectId, user)) {
            return new ResultNotified<>(false, "没有权限进行此操作");
        }
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        String oldVersionText = String.format("[%s (分支:%s, Commit:%s)]",
                app.getCurrentVersion() != null ? app.getCurrentVersion() : "未设置",
                app.getCurrentBranch() != null ? app.getCurrentBranch() : "-",
                abbreviateCommit(app.getCurrentCommitId()));
        String newVersionText = String.format("[%s (分支:%s, Commit:%s)]",
                versionNumber, branch != null ? branch : "-", abbreviateCommit(commitId));
        app.setCurrentVersion(versionNumber);
        app.setCurrentBranch(branch);
        app.setCurrentCommitId(commitId);
        appService.updateApp(projectId, app);
        SystemLog log = new SystemLog();
        log.setTitle(String.format("%s 将应用 %s 的当前版本从 %s 变更为 %s", user.getName(), app.getName(), oldVersionText, newVersionText));
        log.setUserId(user.getId());
        log.setUserName(user.getName());
        log.setProjectId(projectId);
        log.setAction(SystemLogService.Action.editApp.toString());
        systemLogService.addLog(log);
        return new ResultNotified<>(true, "设置当前版本成功");
    }

    @PostMapping("/apps/{appId}/versions/delete")
    public ResultNotified<String> deleteVersion(@PathVariable String projectId,
                                                @PathVariable String appId,
                                                @SessionAttribute UserVo user,
                                                @RequestParam String id) {
        ensureProjectAccess(projectId, user);
        versionService.doDeleteVersionItem(id);
        return new ResultNotified<>(true, "版本项目删除成功");
    }

    @PostMapping("/apps/{appId}/version-reports/delete")
    public ResultNotified<String> deleteCompareReport(@PathVariable String projectId,
                                                      @PathVariable String appId,
                                                      @SessionAttribute UserVo user,
                                                      @RequestParam String reportId) {
        ensureProjectAccess(projectId, user);
        versionService.deleteCompareReport(projectId, reportId);
        return new ResultNotified<>(true, "报告删除成功");
    }

    @PostMapping("/apps/{appId}/coverage-reports/delete")
    public ResultNotified<String> deleteCoverageReport(@PathVariable String projectId,
                                                       @PathVariable String appId,
                                                       @SessionAttribute UserVo user,
                                                       @RequestParam String reportId) {
        ensureProjectAccess(projectId, user);
        coverageService.deleteReport(reportId);
        return new ResultNotified<>(true, "覆盖率报告删除成功");
    }

    @GetMapping("/apps/{appId}/version-files/delete")
    public ResultNotified<String> deleteVersionFile(@PathVariable String projectId,
                                                    @PathVariable String appId,
                                                    @SessionAttribute UserVo user,
                                                    @RequestParam String filePath) {
        ensureProjectAccess(projectId, user);
        versionService.deleteCacheFile(filePath);
        return new ResultNotified<>(true, "本地文件已删除", filePath);
    }

    @GetMapping("/apps/{appId}/git/pull-check")
    public ResultNotified<GitPullEstimateVo> checkGitPull(@PathVariable String projectId,
                                                          @PathVariable String appId,
                                                          @SessionAttribute UserVo user,
                                                          @RequestParam(required = false) String branch,
                                                          @RequestParam(required = false) String commitId,
                                                          @RequestParam(required = false) String versionNumber,
                                                          @RequestParam(required = false) String excludePaths) {
        ensureProjectAccess(projectId, user);
        try {
            AppVo app = appService.getApp(appId);
            String finalBranch = branch != null ? branch.trim() : "";
            String finalCommitId = commitId != null ? commitId.trim() : "";
            ResultNotified<String> excludePathCheck = validateExcludePaths(excludePaths);
            if (excludePathCheck != null) {
                return new ResultNotified<>(false, excludePathCheck.getMessage(), null);
            }
            gitService.checkGitPull(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), finalBranch, finalCommitId);
            String checkCommitId = finalCommitId;
            if (!StringUtils.hasText(checkCommitId)) {
                checkCommitId = gitService.getLatestCommitId(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), finalBranch);
            }
            if (StringUtils.hasText(versionNumber)) {
                VersionItemVo existing = versionService.getVersionByGitInfo(appId, versionNumber.trim(), finalBranch, checkCommitId);
                if (existing != null) {
                    return new ResultNotified<>(false, "该版本号下已存在相同的分支和 CommitID (版本号: " + existing.getVersionNumber() + ")", null);
                }
            }
            GitPullEstimateVo estimate = gitService.estimateGitPull(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), finalBranch, checkCommitId, excludePaths);
            estimate.setPackageCommitVerify(verifyRuntimeCommit(appId, checkCommitId));
            return new ResultNotified<>(true, "检测通过", estimate);
        } catch (Exception e) {
            return new ResultNotified<>(false, "检测失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/apps/{appId}/git/latest-commit")
    public ResultNotified<String> latestCommit(@PathVariable String projectId,
                                               @PathVariable String appId,
                                               @SessionAttribute UserVo user,
                                               @RequestParam String branch) {
        ensureProjectAccess(projectId, user);
        AppVo app = appService.getApp(appId);
        return new ResultNotified<>(true, "获取成功", gitService.getLatestCommitId(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), branch));
    }

    @GetMapping("/apps/{appId}/git/commits")
    public ResultNotified<List<GitCommitOptionVo>> commits(@PathVariable String projectId,
                                                           @PathVariable String appId,
                                                           @SessionAttribute UserVo user,
                                                           @RequestParam String branch,
                                                           @RequestParam(defaultValue = "20") int limit) {
        ensureProjectAccess(projectId, user);
        AppVo app = appService.getApp(appId);
        return new ResultNotified<>(true, "获取成功", gitService.getRecentCommits(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), branch, limit));
    }

    @PostMapping("/apps/{appId}/git/pull")
    public ResultNotified<String> startGitPull(@PathVariable String projectId,
                                               @PathVariable String appId,
                                               @SessionAttribute UserVo user,
                                               @RequestParam(required = false) String branch,
                                               @RequestParam(required = false) String commitId,
                                               @RequestParam(required = false) String excludePaths,
                                               @RequestParam(required = false) String versionNumber) {
        ensureProjectAccess(projectId, user);
        try {
            AppVo app = appService.getApp(appId);
            String finalBranch = branch != null ? branch.trim() : "";
            String finalCommitId = StringUtils.hasText(commitId) ? commitId.trim() : null;
            ResultNotified<String> excludePathCheck = validateExcludePaths(excludePaths);
            if (excludePathCheck != null) {
                return excludePathCheck;
            }
            String checkCommitId = finalCommitId;
            if (checkCommitId == null) {
                checkCommitId = gitService.getLatestCommitId(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), finalBranch);
            }
            if (StringUtils.hasText(versionNumber)) {
                VersionItemVo existing = versionService.getVersionByGitInfo(appId, versionNumber.trim(), finalBranch, checkCommitId);
                if (existing != null) {
                    return new ResultNotified<>(false, "该版本号下已存在相同的分支和 CommitID (版本号: " + existing.getVersionNumber() + ")", null);
                }
            }
            String jobId = gitService.startGitPullJob(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), finalBranch, finalCommitId, excludePaths);
            return new ResultNotified<>(true, "开始拉取", jobId);
        } catch (Exception e) {
            return new ResultNotified<>(false, "远程代码拉取失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/apps/{appId}/git/jobs/{jobId}")
    public ResultNotified<GitJobVo> gitPullStatus(@PathVariable String projectId,
                                                  @PathVariable String appId,
                                                  @PathVariable String jobId,
                                                  @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        GitJobVo job = gitService.getGitJob(jobId);
        return job == null ? new ResultNotified<>(false, "任务不存在", null) : new ResultNotified<>(true, "查询成功", job);
    }

    @GetMapping("/apps/{appId}/git/cache")
    public ResultNotified<String> deleteGitCache(@PathVariable String projectId,
                                                 @PathVariable String appId,
                                                 @SessionAttribute UserVo user,
                                                 @RequestParam String cachePath) {
        ensureProjectAccess(projectId, user);
        gitService.deleteCache(cachePath);
        return new ResultNotified<>(true, "删除成功");
    }

    @GetMapping("/apps/{appId}/packages/commit-verify")
    public ResultNotified<PackageCommitVerifyVo> verifyPackageCommit(@PathVariable String projectId,
                                                                     @PathVariable String appId,
                                                                     @SessionAttribute UserVo user,
                                                                     @RequestParam String programFile,
                                                                     @RequestParam(required = false) String commitId) {
        ensureProjectAccess(projectId, user);
        try {
            String targetCommitId = StringUtils.hasText(commitId) ? commitId.trim() : readPackageCommitId(programFile);
            return new ResultNotified<>(true, "校验完成", verifyRuntimeCommit(appId, targetCommitId));
        } catch (Exception e) {
            return new ResultNotified<>(false, "校验失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/apps/{appId}/compare-jobs")
    public ResultNotified<CompareJobPayload> startCompare(@PathVariable String projectId,
                                                          @PathVariable String appId,
                                                          @SessionAttribute UserVo user,
                                                          @RequestBody StartCompareRequest request) {
        ensureProjectAccess(projectId, user);
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");

        String packageName = StringUtils.hasText(request.getPackageName()) ? request.getPackageName() : "*";
        String jobId;
        if ("git".equalsIgnoreCase(request.getMode())) {
            Assert.hasText(request.getBranch(), "branch不能为空");
            Assert.hasText(request.getOldCommit(), "oldCommit不能为空");
            Assert.hasText(request.getNewCommit(), "newCommit不能为空");
            jobId = versionService.startCompareFromGit(projectId, app, packageName,
                    request.getBranch(), request.getOldCommit(), request.getNewCommit());
        } else {
            Assert.hasText(request.getSourceFile(), "sourceFile不能为空");
            Assert.hasText(request.getTargetFile(), "targetFile不能为空");
            jobId = versionService.startCompareJob(projectId, app, packageName,
                    request.getSourceFile(), request.getTargetFile());
        }
        Assert.hasText(jobId, "比对任务启动失败");
        CompareJobVo job = versionService.getCompareJob(jobId);
        CompareJobPayload payload = new CompareJobPayload();
        payload.setJobId(jobId);
        payload.setJob(job == null ? null : toCompareJobSummary(job));
        return new ResultNotified<>(true, "比对任务已启动", payload);
    }

    @GetMapping("/apps/{appId}/compare-jobs/{jobId}")
    public ResultNotified<CompareJobSummary> compareJob(@PathVariable String projectId,
                                                        @PathVariable String appId,
                                                        @PathVariable String jobId,
                                                        @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        CompareJobVo job = versionService.getCompareJob(jobId);
        Assert.notNull(job, "比对任务不存在");
        return new ResultNotified<>(true, "获取比对任务成功", toCompareJobSummary(job));
    }

    @GetMapping("/version/reports/{reportId}")
    public ResultNotified<VersionReportDetailPayload> compareReport(@PathVariable String projectId,
                                                                    @PathVariable String reportId,
                                                                    @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        try {
            VersionCompareReport report = versionService.getCompareReport(reportId);
            VersionReportDetailPayload payload = new VersionReportDetailPayload();
            payload.setState("ready");
            payload.setApp(toAppSummary(appService.getApp(report.getAppId())));
            payload.setReport(toCompareReportDetailSummary(report));
            payload.setDifferences(buildDifferenceGroups(report));
            payload.setUsecases(buildUsecaseImpacts(reportId, report));
            payload.setImpactHints(buildImpactHintSummary(report.getJobLog()));
            return new ResultNotified<>(true, "获取比对报告成功", payload);
        } catch (IllegalArgumentException ex) {
            VersionReportDetailPayload payload = new VersionReportDetailPayload();
            payload.setState("pending");
            payload.setRetryMessage("比对报告正在生成或索引刷新中，页面会自动重试。");
            CompareJobVo compareJob = versionService.getCompareJob(reportId);
            payload.setApp(compareJob == null ? null : toAppSummary(appService.getApp(compareJob.getAppId())));
            return new ResultNotified<>(true, "比对报告暂未就绪", payload);
        }
    }

    private List<DifferenceGroupSummary> buildDifferenceGroups(VersionCompareReport report) {
        Map<String, DifferenceGroupSummary> groups = new LinkedHashMap<>();
        VersionCompareReport.Difference[] diffs = report.getDifferences();
        if (diffs == null) {
            return new ArrayList<>();
        }
        for (VersionCompareReport.Difference difference : diffs) {
            if (difference == null) {
                continue;
            }
            if ("class".equals(difference.getType())) {
                DifferenceGroupSummary group = groups.computeIfAbsent(difference.getValue(), className -> {
                    DifferenceGroupSummary item = new DifferenceGroupSummary();
                    item.setClassName(className);
                    item.setModel(difference.getModel());
                    item.setMethods(new ArrayList<>());
                    return item;
                });
                if (!StringUtils.hasText(group.getModel())) {
                    group.setModel(difference.getModel());
                }
            } else if ("method".equals(difference.getType())) {
                MethodDifferenceSummary method = parseMethodDifference(difference);
                if (method == null) {
                    continue;
                }
                DifferenceGroupSummary group = groups.computeIfAbsent(method.getClassName(), className -> {
                    DifferenceGroupSummary item = new DifferenceGroupSummary();
                    item.setClassName(className);
                    item.setModel("update");
                    item.setMethods(new ArrayList<>());
                    return item;
                });
                group.getMethods().add(method);
            }
        }
        return new ArrayList<>(groups.values());
    }

    private MethodDifferenceSummary parseMethodDifference(VersionCompareReport.Difference difference) {
        String raw = difference.getValue();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String className = null;
        String methodName = null;
        String methodDesc = "";
        String[] tabParts = raw.split("\t");
        if (tabParts.length >= 3) {
            className = tabParts[0];
            methodName = tabParts[1];
            methodDesc = tabParts[2];
        } else {
            String[] spaceParts = raw.split(" ");
            if (spaceParts.length >= 3) {
                className = spaceParts[0];
                methodName = spaceParts[1];
                methodDesc = spaceParts[2];
            } else if (spaceParts.length == 2) {
                className = spaceParts[0];
                methodName = spaceParts[1];
            }
        }
        if (!StringUtils.hasText(className) || !StringUtils.hasText(methodName)) {
            return null;
        }
        MethodDifferenceSummary summary = new MethodDifferenceSummary();
        summary.setClassName(className);
        summary.setMethodName(methodName);
        summary.setMethodDesc(methodDesc);
        summary.setModel(difference.getModel());
        return summary;
    }

    private List<UsecaseImpactSummary> buildUsecaseImpacts(String reportId, VersionCompareReport report) {
        List<UsecaseImpactSummary> result = new ArrayList<>();
        AtomicInteger skippedDeletedUsecaseCount = new AtomicInteger();
        if (report.getCases() == null) {
            return result;
        }
        for (VersionCompareReport.ImpactCase impactCase : report.getCases()) {
            if (impactCase == null || !StringUtils.hasText(impactCase.getCaseId())) {
                continue;
            }
            try {
                UsecaseVo usecase = usecaseService.getUsecase(report.getProjectId(), impactCase.getCaseId());
                UsecaseImpactSummary summary = new UsecaseImpactSummary();
                summary.setId(usecase.getId());
                summary.setTitle(usecase.getTitle());
                summary.setDirectoryPath(resolveUsecaseDirectoryPath(report.getProjectId(),
                        Optional.ofNullable(usecase.getDirectory()).orElse("root")));
                summary.setDifferences(impactCase.getDifferences() == null ? new String[0] : impactCase.getDifferences());
                result.add(summary);
            } catch (IllegalArgumentException ex) {
                skippedDeletedUsecaseCount.incrementAndGet();
            } catch (Exception ignore) {
            }
        }
        result.sort(Comparator.comparing(UsecaseImpactSummary::getDirectoryPath, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(UsecaseImpactSummary::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)));
        return result;
    }

    private String resolveUsecaseDirectoryPath(String projectId, String directoryId) {
        if (!StringUtils.hasText(directoryId) || "root".equalsIgnoreCase(directoryId)) {
            return "ROOT";
        }
        try {
            List<UsecaseDirectoryVo> tiers = usecaseService.getDirectoryTier(projectId, directoryId);
            if (tiers == null || tiers.isEmpty()) {
                return directoryId;
            }
            List<String> names = new ArrayList<>();
            Collections.reverse(tiers);
            for (UsecaseDirectoryVo vo : tiers) {
                if (vo != null && StringUtils.hasText(vo.getName())) {
                    names.add(vo.getName());
                }
            }
            return names.isEmpty() ? directoryId : String.join(" / ", names);
        } catch (Exception ex) {
            return directoryId;
        }
    }

    private ImpactHintSummary buildImpactHintSummary(String jobLog) {
        ImpactHintSummary summary = new ImpactHintSummary();
        if (!StringUtils.hasText(jobLog)) {
            return summary;
        }

        Pattern snapshotCountPattern = Pattern.compile("当前应用快照数：(\\d+)");
        Matcher countMatcher = snapshotCountPattern.matcher(jobLog);
        int maxSnapshotCount = -1;
        while (countMatcher.find()) {
            int count = Integer.parseInt(countMatcher.group(1));
            if (count > maxSnapshotCount) {
                maxSnapshotCount = count;
            }
        }
        if (maxSnapshotCount >= 0) {
            summary.setSnapshotCount(maxSnapshotCount);
        }

        Pattern hitPattern = Pattern.compile("命中快照：([^\\n\\r]+)");
        Matcher hitMatcher = hitPattern.matcher(jobLog);
        LinkedHashSet<String> snapshotTitles = new LinkedHashSet<>();
        while (hitMatcher.find()) {
            String title = hitMatcher.group(1).trim();
            if (StringUtils.hasText(title) && !"-".equals(title)) {
                Arrays.stream(title.split(",")).map(String::trim).filter(StringUtils::hasText).forEach(snapshotTitles::add);
            }
        }
        summary.setHitSnapshots(new ArrayList<>(snapshotTitles));

        Pattern zeroPattern = Pattern.compile("查找快照影响 类名：([^\\s]+).*?影响数：0");
        Matcher zeroMatcher = zeroPattern.matcher(jobLog);
        LinkedHashSet<String> zeroHitClasses = new LinkedHashSet<>();
        while (zeroMatcher.find()) {
            zeroHitClasses.add(zeroMatcher.group(1).trim());
        }
        summary.setZeroHitClasses(new ArrayList<>(zeroHitClasses));
        return summary;
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

    private AppSummary toAppSummary(AppVo app) {
        if (app == null) {
            return null;
        }
        AppSummary summary = new AppSummary();
        summary.setId(app.getId());
        summary.setName(app.getName());
        summary.setCurrentVersion(app.getCurrentVersion());
        summary.setCurrentBranch(app.getCurrentBranch());
        summary.setCurrentCommitId(app.getCurrentCommitId());
        summary.setRepoConfigured(StringUtils.hasText(app.getRepoAddress()));
        return summary;
    }

    private List<AppSummary> toAppSummaries(List<AppVo> apps) {
        return apps.stream().map(this::toAppSummary).collect(Collectors.toList());
    }

    private VersionItemSummary toVersionItemSummary(VersionItemVo item, AppVo app) {
        VersionItemSummary summary = new VersionItemSummary();
        summary.setId(item.getId());
        summary.setVersionNumber(item.getVersionNumber());
        summary.setDescribe(item.getDescribe());
        summary.setProgramFile(item.getProgramFile());
        summary.setProgramName(item.getProgramName());
        summary.setSourceType(item.getSourceType());
        summary.setRepoBranch(item.getRepoBranch());
        summary.setRepoCommitId(item.getRepoCommitId());
        summary.setCreateTimeText(formatDate(item.getCreateTime()));
        summary.setCreateTimeRelativeText(item.getCreateTime() == null ? null : DateUtil.timeDifference(item.getCreateTime()));
        summary.setFileExist(item.isFileExist());
        summary.setHasReport(item.isHasReport());
        summary.setCurrent(matchesCurrentVersion(item, app));
        return summary;
    }

    private boolean matchesCurrentVersion(VersionItemVo item, AppVo app) {
        if (item == null || app == null || !StringUtils.hasText(item.getVersionNumber())) {
            return false;
        }
        if (!item.getVersionNumber().equals(app.getCurrentVersion())) {
            return false;
        }
        String currentBranch = StringUtils.hasText(app.getCurrentBranch()) ? app.getCurrentBranch() : "";
        String itemBranch = StringUtils.hasText(item.getRepoBranch()) ? item.getRepoBranch() : "";
        if (!currentBranch.equals(itemBranch)) {
            return false;
        }
        String currentCommit = StringUtils.hasText(app.getCurrentCommitId()) ? app.getCurrentCommitId() : "";
        String itemCommit = StringUtils.hasText(item.getRepoCommitId()) ? item.getRepoCommitId() : "";
        return currentCommit.equals(itemCommit);
    }

    private CompareReportSummary toCompareReportSummary(VersionCompareReportVo report) {
        CompareReportSummary summary = new CompareReportSummary();
        summary.setId(report.getId());
        summary.setName(report.getName());
        summary.setCreateTimeText(formatDate(report.getCreateTime()));
        summary.setCreateTimeRelativeText(report.getCreateTime() == null ? null : DateUtil.timeDifference(report.getCreateTime()));
        summary.setSourceVersion(report.getSourceVersion());
        summary.setTargetVersion(report.getTargetVersion());
        summary.setGitBranch(report.getGitBranch());
        summary.setGitOldCommit(report.getGitOldCommit());
        summary.setGitNewCommit(report.getGitNewCommit());
        summary.setAddClassCount(report.getAddClassCount());
        summary.setUpdateClassCount(report.getUpdateClassCount());
        summary.setDeleteClassCount(report.getDeleteClassCount());
        summary.setAddMethodCount(report.getAddMethodCount());
        summary.setUpdateMethodCount(report.getUpdateMethodCount());
        summary.setDeleteMethodCount(report.getDeleteMethodCount());
        summary.setImpactCaseCount(report.getImpactCaseCount());
        return summary;
    }

    private CoverageReportCard toCoverageReportCard(CoverageReportIndex report, boolean hasNewerData) {
        CoverageReportCard card = new CoverageReportCard();
        card.setId(report.getId());
        card.setVersionNumber(report.getVersionNumber());
        card.setRepoBranch(report.getRepoBranch());
        card.setRepoCommitId(report.getRepoCommitId());
        card.setCreateTimeText(formatDate(report.getCreateTime()));
        card.setCreateTimeRelativeText(report.getCreateTime() == null ? null : DateUtil.timeDifference(report.getCreateTime()));
        card.setReportType(report.getReportType());
        card.setBaseVersionNumber(report.getBaseVersionNumber());
        card.setBaseRepoCommitId(report.getBaseRepoCommitId());
        card.setSnapshotCount(report.getSnapshotCount());
        card.setTotalClasses(report.getTotalClasses());
        card.setCoveredClasses(report.getCoveredClasses());
        card.setTotalMethods(report.getTotalMethods());
        card.setCoveredMethods(report.getCoveredMethods());
        card.setTotalLines(report.getTotalLines());
        card.setCoveredLines(report.getCoveredLines());
        card.setHasNewerData(hasNewerData);
        return card;
    }

    private CompareJobSummary toCompareJobSummary(CompareJobVo job) {
        CompareJobSummary summary = new CompareJobSummary();
        summary.setId(job.getId());
        summary.setName(job.getName());
        summary.setProgress(job.getProgress());
        summary.setProgressName(job.getProgressName());
        summary.setFinish(job.isFinish());
        summary.setError(job.isError());
        summary.setErrorMessage(job.getErrorMessage());
        summary.setLog(job.getLog());
        summary.setSourceFile(job.getSourceFile());
        summary.setTargetFile(job.getTargetFile());
        summary.setGitBranch(job.getGitBranch());
        summary.setGitOldCommit(job.getGitOldCommit());
        summary.setGitNewCommit(job.getGitNewCommit());
        summary.setAddClassCount(job.getAddClassCount());
        summary.setUpdateClassCount(job.getUpdateClassCount());
        summary.setDeleteClassCount(job.getDeleteClassCount());
        summary.setAddMethodCount(job.getAddMethodCount());
        summary.setUpdateMethodCount(job.getUpdateMethodCount());
        summary.setDeleteMethodCount(job.getDeleteMethodCount());
        summary.setBeginTimeText(formatDate(job.getBegin()));
        summary.setEndTimeText(formatDate(job.getEnd()));
        return summary;
    }

    private CompareReportDetailSummary toCompareReportDetailSummary(VersionCompareReport report) {
        CompareReportDetailSummary summary = new CompareReportDetailSummary();
        summary.setJobId(report.getJobId());
        summary.setProjectId(report.getProjectId());
        summary.setAppId(report.getAppId());
        summary.setJobName(report.getJobName());
        summary.setSourceVersion(report.getSourceVersion());
        summary.setTargetVersion(report.getTargetVersion());
        summary.setGitBranch(report.getGitBranch());
        summary.setGitOldCommit(report.getGitOldCommit());
        summary.setGitNewCommit(report.getGitNewCommit());
        summary.setCreateTimeText(formatDate(report.getCreateTime()));
        summary.setAddClassCount(report.getAddClassCount());
        summary.setUpdateClassCount(report.getUpdateClassCount());
        summary.setDeleteClassCount(report.getDeleteClassCount());
        summary.setAddMethodCount(report.getAddMethodCount());
        summary.setUpdateMethodCount(report.getUpdateMethodCount());
        summary.setDeleteMethodCount(report.getDeleteMethodCount());
        summary.setImpactCaseCount(report.getImpactCaseCount());
        summary.setJobLog(report.getJobLog());
        return summary;
    }


    private boolean isVisitor(String projectId, UserVo user) {
        return projectService.getProjectMembers(projectId).stream()
                .filter(member -> user.getName().equals(member.getMemberName()))
                .map(ProjectMemberVo::getRole)
                .anyMatch(role -> ProjectMemberVo.Role.visitor.equals(role));
    }

    private String abbreviateCommit(String commitId) {
        if (!StringUtils.hasText(commitId)) {
            return "-";
        }
        return commitId.length() > 7 ? commitId.substring(0, 7) : commitId;
    }

    private ResultNotified<String> validateExcludePaths(String excludePaths) {
        if (!StringUtils.hasText(excludePaths)) {
            return null;
        }
        for (String path : excludePaths.split(",")) {
            String trimmedPath = path.trim();
            if (!StringUtils.hasText(trimmedPath)) {
                continue;
            }
            if (trimmedPath.contains("..")) {
                return new ResultNotified<>(false, "检测失败: 排除路径不能包含 ..");
            }
            if (trimmedPath.startsWith("/") || trimmedPath.startsWith("\\") || trimmedPath.matches("^[A-Za-z]:.*")) {
                return new ResultNotified<>(false, "检测失败: 排除路径不能是绝对路径");
            }
            String normalized = java.nio.file.Paths.get(trimmedPath).normalize().toString().replace('\\', '/');
            if (normalized.isEmpty() || ".".equals(normalized) || normalized.startsWith("../")) {
                return new ResultNotified<>(false, "检测失败: 排除路径格式不合法");
            }
        }
        return null;
    }

    private PackageCommitVerifyVo verifyRuntimeCommit(String appId, String targetCommitId) {
        List<com.oAT.server.model.ClientSessionVo> onlineSessions = clientSessionService.getOnlineSessionsByAppId(appId);
        if (onlineSessions == null || onlineSessions.isEmpty()) {
            return new PackageCommitVerifyVo(null, targetCommitId, null, false, "探针不在线，无法获取运行时目标系统 CommitId");
        }
        String runtimeCommitId = findRuntimePackageCommitId(appId, onlineSessions);
        String runtime = normalizeCommitId(runtimeCommitId);
        String target = normalizeCommitId(targetCommitId);
        Boolean matched = null;
        if (StringUtils.hasText(runtime) && StringUtils.hasText(target)) {
            matched = runtime.equals(target) || runtime.startsWith(target) || target.startsWith(runtime);
        }
        String unavailableReason = StringUtils.hasText(runtimeCommitId) ? null : "探针在线，但暂未上报运行时目标系统 CommitId";
        return new PackageCommitVerifyVo(runtimeCommitId, targetCommitId, matched, true, unavailableReason);
    }

    private String findRuntimePackageCommitId(String appId, List<com.oAT.server.model.ClientSessionVo> sessions) {
        for (com.oAT.server.model.ClientSessionVo session : sessions) {
            String commitId = extractCommitIdFromPackageVerifyData(clientSessionService.getPackageVerifyData(session.getSessionId()));
            if (StringUtils.hasText(commitId)) {
                return commitId;
            }
        }
        for (com.oAT.server.model.ClientSessionVo session : sessions) {
            if (session.getClientInfo() == null || !StringUtils.hasText(session.getClientInfo().getAppKey())) {
                continue;
            }
            String commitId = extractCommitIdFromPackageVerifyData(clientSessionService.getLatestPackageVerifyDataByAppId(session.getClientInfo().getAppKey()));
            if (StringUtils.hasText(commitId)) {
                return commitId;
            }
        }
        return null;
    }

    private String extractCommitIdFromPackageVerifyData(String packageVerifyData) {
        if (!StringUtils.hasText(packageVerifyData)) {
            return null;
        }
        Matcher matcher = Pattern.compile("gitCommitIdFromPackage\\s*[:=]\\s*([0-9a-fA-F]{7,40})").matcher(packageVerifyData);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = Pattern.compile("[0-9a-fA-F]{7,40}").matcher(packageVerifyData);
        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group();
        }
        return lastMatch;
    }

    private String readPackageCommitId(String cachePath) throws Exception {
        if (!StringUtils.hasText(cachePath)) {
            throw new IllegalArgumentException("程序文件不能为空");
        }
        File cacheRoot = new File(resourceService.getCacheRoot()).getCanonicalFile();
        File packageFile = new File(cacheRoot, cachePath).getCanonicalFile();
        if (!packageFile.getPath().startsWith(cacheRoot.getPath() + File.separator) || !packageFile.exists() || !packageFile.isFile()) {
            throw new IllegalArgumentException("程序文件不存在");
        }
        try (ZipFile zipFile = new ZipFile(packageFile)) {
            ZipEntry entry = findBuildInfoEntry(zipFile);
            if (entry == null) {
                return null;
            }
            Properties props = new Properties();
            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                props.load(inputStream);
            }
            String commitId = props.getProperty("git.commit.id");
            if (!StringUtils.hasText(commitId)) {
                commitId = props.getProperty("git.commit.id.abbrev");
            }
            return commitId;
        }
    }

    private ZipEntry findBuildInfoEntry(ZipFile zipFile) {
        String[] paths = {
                "META-INF/git.properties",
                "META-INF/build-info.properties",
                "WEB-INF/classes/META-INF/git.properties",
                "WEB-INF/classes/META-INF/build-info.properties",
                "WEB-INF/classes/git.properties",
                "WEB-INF/classes/build-info.properties"
        };
        for (String path : paths) {
            ZipEntry entry = zipFile.getEntry(path);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    private String normalizeCommitId(String commitId) {
        if (!StringUtils.hasText(commitId)) {
            return null;
        }
        String normalized = commitId.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = Pattern.compile("[0-9a-f]{8,40}").matcher(normalized);
        if (matcher.find()) {
            return matcher.group();
        }
        return normalized;
    }

    private String formatDate(java.util.Date date) {
        return date == null ? null : new SimpleDateFormat(DATE_TIME_PATTERN, Locale.CHINA).format(date);
    }

    public static class StartCompareRequest {
        private String mode;
        private String sourceFile;
        private String targetFile;
        private String packageName;
        private String branch;
        private String oldCommit;
        private String newCommit;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getSourceFile() { return sourceFile; }
        public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
        public String getTargetFile() { return targetFile; }
        public void setTargetFile(String targetFile) { this.targetFile = targetFile; }
        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getOldCommit() { return oldCommit; }
        public void setOldCommit(String oldCommit) { this.oldCommit = oldCommit; }
        public String getNewCommit() { return newCommit; }
        public void setNewCommit(String newCommit) { this.newCommit = newCommit; }
    }

    public static class VersionCenterPayload {
        private AppSummary app;
        private List<AppSummary> apps;
        private String currentUserRole;
        private List<VersionItemSummary> versions;
        private List<VersionItemSummary> packageVersions;
        private List<CompareReportSummary> compareReports;
        private List<CoverageReportCard> coverageReports;

        public AppSummary getApp() { return app; }
        public void setApp(AppSummary app) { this.app = app; }
        public List<AppSummary> getApps() { return apps; }
        public void setApps(List<AppSummary> apps) { this.apps = apps; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
        public List<VersionItemSummary> getVersions() { return versions; }
        public void setVersions(List<VersionItemSummary> versions) { this.versions = versions; }
        public List<VersionItemSummary> getPackageVersions() { return packageVersions; }
        public void setPackageVersions(List<VersionItemSummary> packageVersions) { this.packageVersions = packageVersions; }
        public List<CompareReportSummary> getCompareReports() { return compareReports; }
        public void setCompareReports(List<CompareReportSummary> compareReports) { this.compareReports = compareReports; }
        public List<CoverageReportCard> getCoverageReports() { return coverageReports; }
        public void setCoverageReports(List<CoverageReportCard> coverageReports) { this.coverageReports = coverageReports; }
    }

    public static class CompareJobPayload {
        private String jobId;
        private CompareJobSummary job;

        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }
        public CompareJobSummary getJob() { return job; }
        public void setJob(CompareJobSummary job) { this.job = job; }
    }

    public static class VersionReportDetailPayload {
        private String state;
        private String retryMessage;
        private AppSummary app;
        private CompareReportDetailSummary report;
        private List<DifferenceGroupSummary> differences;
        private List<UsecaseImpactSummary> usecases;
        private ImpactHintSummary impactHints;

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getRetryMessage() { return retryMessage; }
        public void setRetryMessage(String retryMessage) { this.retryMessage = retryMessage; }
        public AppSummary getApp() { return app; }
        public void setApp(AppSummary app) { this.app = app; }
        public CompareReportDetailSummary getReport() { return report; }
        public void setReport(CompareReportDetailSummary report) { this.report = report; }
        public List<DifferenceGroupSummary> getDifferences() { return differences; }
        public void setDifferences(List<DifferenceGroupSummary> differences) { this.differences = differences; }
        public List<UsecaseImpactSummary> getUsecases() { return usecases; }
        public void setUsecases(List<UsecaseImpactSummary> usecases) { this.usecases = usecases; }
        public ImpactHintSummary getImpactHints() { return impactHints; }
        public void setImpactHints(ImpactHintSummary impactHints) { this.impactHints = impactHints; }
    }

    public static class AppSummary {
        private String id;
        private String name;
        private String currentVersion;
        private String currentBranch;
        private String currentCommitId;
        private boolean repoConfigured;

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
        public boolean isRepoConfigured() { return repoConfigured; }
        public void setRepoConfigured(boolean repoConfigured) { this.repoConfigured = repoConfigured; }
    }

    public static class VersionItemSummary {
        private String id;
        private String versionNumber;
        private String describe;
        private String programFile;
        private String programName;
        private String sourceType;
        private String repoBranch;
        private String repoCommitId;
        private String createTimeText;
        private String createTimeRelativeText;
        private boolean fileExist;
        private boolean hasReport;
        private boolean current;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getDescribe() { return describe; }
        public void setDescribe(String describe) { this.describe = describe; }
        public String getProgramFile() { return programFile; }
        public void setProgramFile(String programFile) { this.programFile = programFile; }
        public String getProgramName() { return programName; }
        public void setProgramName(String programName) { this.programName = programName; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getRepoBranch() { return repoBranch; }
        public void setRepoBranch(String repoBranch) { this.repoBranch = repoBranch; }
        public String getRepoCommitId() { return repoCommitId; }
        public void setRepoCommitId(String repoCommitId) { this.repoCommitId = repoCommitId; }
        public String getCreateTimeText() { return createTimeText; }
        public void setCreateTimeText(String createTimeText) { this.createTimeText = createTimeText; }
        public String getCreateTimeRelativeText() { return createTimeRelativeText; }
        public void setCreateTimeRelativeText(String createTimeRelativeText) { this.createTimeRelativeText = createTimeRelativeText; }
        public boolean isFileExist() { return fileExist; }
        public void setFileExist(boolean fileExist) { this.fileExist = fileExist; }
        public boolean isHasReport() { return hasReport; }
        public void setHasReport(boolean hasReport) { this.hasReport = hasReport; }
        public boolean isCurrent() { return current; }
        public void setCurrent(boolean current) { this.current = current; }
    }

    public static class CompareReportSummary {
        private String id;
        private String name;
        private String createTimeText;
        private String createTimeRelativeText;
        private String sourceVersion;
        private String targetVersion;
        private String gitBranch;
        private String gitOldCommit;
        private String gitNewCommit;
        private int addClassCount;
        private int updateClassCount;
        private int deleteClassCount;
        private int addMethodCount;
        private int updateMethodCount;
        private int deleteMethodCount;
        private int impactCaseCount;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCreateTimeText() { return createTimeText; }
        public void setCreateTimeText(String createTimeText) { this.createTimeText = createTimeText; }
        public String getCreateTimeRelativeText() { return createTimeRelativeText; }
        public void setCreateTimeRelativeText(String createTimeRelativeText) { this.createTimeRelativeText = createTimeRelativeText; }
        public String getSourceVersion() { return sourceVersion; }
        public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }
        public String getTargetVersion() { return targetVersion; }
        public void setTargetVersion(String targetVersion) { this.targetVersion = targetVersion; }
        public String getGitBranch() { return gitBranch; }
        public void setGitBranch(String gitBranch) { this.gitBranch = gitBranch; }
        public String getGitOldCommit() { return gitOldCommit; }
        public void setGitOldCommit(String gitOldCommit) { this.gitOldCommit = gitOldCommit; }
        public String getGitNewCommit() { return gitNewCommit; }
        public void setGitNewCommit(String gitNewCommit) { this.gitNewCommit = gitNewCommit; }
        public int getAddClassCount() { return addClassCount; }
        public void setAddClassCount(int addClassCount) { this.addClassCount = addClassCount; }
        public int getUpdateClassCount() { return updateClassCount; }
        public void setUpdateClassCount(int updateClassCount) { this.updateClassCount = updateClassCount; }
        public int getDeleteClassCount() { return deleteClassCount; }
        public void setDeleteClassCount(int deleteClassCount) { this.deleteClassCount = deleteClassCount; }
        public int getAddMethodCount() { return addMethodCount; }
        public void setAddMethodCount(int addMethodCount) { this.addMethodCount = addMethodCount; }
        public int getUpdateMethodCount() { return updateMethodCount; }
        public void setUpdateMethodCount(int updateMethodCount) { this.updateMethodCount = updateMethodCount; }
        public int getDeleteMethodCount() { return deleteMethodCount; }
        public void setDeleteMethodCount(int deleteMethodCount) { this.deleteMethodCount = deleteMethodCount; }
        public int getImpactCaseCount() { return impactCaseCount; }
        public void setImpactCaseCount(int impactCaseCount) { this.impactCaseCount = impactCaseCount; }
    }

    public static class CoverageReportCard {
        private String id;
        private String versionNumber;
        private String repoBranch;
        private String repoCommitId;
        private String createTimeText;
        private String createTimeRelativeText;
        private Integer reportType;
        private String baseVersionNumber;
        private String baseRepoCommitId;
        private Integer snapshotCount;
        private long totalClasses;
        private long coveredClasses;
        private long totalMethods;
        private long coveredMethods;
        private long totalLines;
        private long coveredLines;
        private boolean hasNewerData;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getRepoBranch() { return repoBranch; }
        public void setRepoBranch(String repoBranch) { this.repoBranch = repoBranch; }
        public String getRepoCommitId() { return repoCommitId; }
        public void setRepoCommitId(String repoCommitId) { this.repoCommitId = repoCommitId; }
        public String getCreateTimeText() { return createTimeText; }
        public void setCreateTimeText(String createTimeText) { this.createTimeText = createTimeText; }
        public String getCreateTimeRelativeText() { return createTimeRelativeText; }
        public void setCreateTimeRelativeText(String createTimeRelativeText) { this.createTimeRelativeText = createTimeRelativeText; }
        public Integer getReportType() { return reportType; }
        public void setReportType(Integer reportType) { this.reportType = reportType; }
        public String getBaseVersionNumber() { return baseVersionNumber; }
        public void setBaseVersionNumber(String baseVersionNumber) { this.baseVersionNumber = baseVersionNumber; }
        public String getBaseRepoCommitId() { return baseRepoCommitId; }
        public void setBaseRepoCommitId(String baseRepoCommitId) { this.baseRepoCommitId = baseRepoCommitId; }
        public Integer getSnapshotCount() { return snapshotCount; }
        public void setSnapshotCount(Integer snapshotCount) { this.snapshotCount = snapshotCount; }
        public long getTotalClasses() { return totalClasses; }
        public void setTotalClasses(long totalClasses) { this.totalClasses = totalClasses; }
        public long getCoveredClasses() { return coveredClasses; }
        public void setCoveredClasses(long coveredClasses) { this.coveredClasses = coveredClasses; }
        public long getTotalMethods() { return totalMethods; }
        public void setTotalMethods(long totalMethods) { this.totalMethods = totalMethods; }
        public long getCoveredMethods() { return coveredMethods; }
        public void setCoveredMethods(long coveredMethods) { this.coveredMethods = coveredMethods; }
        public long getTotalLines() { return totalLines; }
        public void setTotalLines(long totalLines) { this.totalLines = totalLines; }
        public long getCoveredLines() { return coveredLines; }
        public void setCoveredLines(long coveredLines) { this.coveredLines = coveredLines; }
        public boolean isHasNewerData() { return hasNewerData; }
        public void setHasNewerData(boolean hasNewerData) { this.hasNewerData = hasNewerData; }
    }

    public static class CompareJobSummary {
        private String id;
        private String name;
        private int progress;
        private String progressName;
        private boolean finish;
        private boolean error;
        private String errorMessage;
        private String log;
        private String sourceFile;
        private String targetFile;
        private String gitBranch;
        private String gitOldCommit;
        private String gitNewCommit;
        private int addClassCount;
        private int updateClassCount;
        private int deleteClassCount;
        private int addMethodCount;
        private int updateMethodCount;
        private int deleteMethodCount;
        private String beginTimeText;
        private String endTimeText;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getProgress() { return progress; }
        public void setProgress(int progress) { this.progress = progress; }
        public String getProgressName() { return progressName; }
        public void setProgressName(String progressName) { this.progressName = progressName; }
        public boolean isFinish() { return finish; }
        public void setFinish(boolean finish) { this.finish = finish; }
        public boolean isError() { return error; }
        public void setError(boolean error) { this.error = error; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public String getLog() { return log; }
        public void setLog(String log) { this.log = log; }
        public String getSourceFile() { return sourceFile; }
        public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
        public String getTargetFile() { return targetFile; }
        public void setTargetFile(String targetFile) { this.targetFile = targetFile; }
        public String getGitBranch() { return gitBranch; }
        public void setGitBranch(String gitBranch) { this.gitBranch = gitBranch; }
        public String getGitOldCommit() { return gitOldCommit; }
        public void setGitOldCommit(String gitOldCommit) { this.gitOldCommit = gitOldCommit; }
        public String getGitNewCommit() { return gitNewCommit; }
        public void setGitNewCommit(String gitNewCommit) { this.gitNewCommit = gitNewCommit; }
        public int getAddClassCount() { return addClassCount; }
        public void setAddClassCount(int addClassCount) { this.addClassCount = addClassCount; }
        public int getUpdateClassCount() { return updateClassCount; }
        public void setUpdateClassCount(int updateClassCount) { this.updateClassCount = updateClassCount; }
        public int getDeleteClassCount() { return deleteClassCount; }
        public void setDeleteClassCount(int deleteClassCount) { this.deleteClassCount = deleteClassCount; }
        public int getAddMethodCount() { return addMethodCount; }
        public void setAddMethodCount(int addMethodCount) { this.addMethodCount = addMethodCount; }
        public int getUpdateMethodCount() { return updateMethodCount; }
        public void setUpdateMethodCount(int updateMethodCount) { this.updateMethodCount = updateMethodCount; }
        public int getDeleteMethodCount() { return deleteMethodCount; }
        public void setDeleteMethodCount(int deleteMethodCount) { this.deleteMethodCount = deleteMethodCount; }
        public String getBeginTimeText() { return beginTimeText; }
        public void setBeginTimeText(String beginTimeText) { this.beginTimeText = beginTimeText; }
        public String getEndTimeText() { return endTimeText; }
        public void setEndTimeText(String endTimeText) { this.endTimeText = endTimeText; }
    }

    public static class CompareReportDetailSummary {
        private String jobId;
        private String projectId;
        private String appId;
        private String jobName;
        private String sourceVersion;
        private String targetVersion;
        private String gitBranch;
        private String gitOldCommit;
        private String gitNewCommit;
        private String createTimeText;
        private int addClassCount;
        private int updateClassCount;
        private int deleteClassCount;
        private int addMethodCount;
        private int updateMethodCount;
        private int deleteMethodCount;
        private int impactCaseCount;
        private String jobLog;

        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getJobName() { return jobName; }
        public void setJobName(String jobName) { this.jobName = jobName; }
        public String getSourceVersion() { return sourceVersion; }
        public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }
        public String getTargetVersion() { return targetVersion; }
        public void setTargetVersion(String targetVersion) { this.targetVersion = targetVersion; }
        public String getGitBranch() { return gitBranch; }
        public void setGitBranch(String gitBranch) { this.gitBranch = gitBranch; }
        public String getGitOldCommit() { return gitOldCommit; }
        public void setGitOldCommit(String gitOldCommit) { this.gitOldCommit = gitOldCommit; }
        public String getGitNewCommit() { return gitNewCommit; }
        public void setGitNewCommit(String gitNewCommit) { this.gitNewCommit = gitNewCommit; }
        public String getCreateTimeText() { return createTimeText; }
        public void setCreateTimeText(String createTimeText) { this.createTimeText = createTimeText; }
        public int getAddClassCount() { return addClassCount; }
        public void setAddClassCount(int addClassCount) { this.addClassCount = addClassCount; }
        public int getUpdateClassCount() { return updateClassCount; }
        public void setUpdateClassCount(int updateClassCount) { this.updateClassCount = updateClassCount; }
        public int getDeleteClassCount() { return deleteClassCount; }
        public void setDeleteClassCount(int deleteClassCount) { this.deleteClassCount = deleteClassCount; }
        public int getAddMethodCount() { return addMethodCount; }
        public void setAddMethodCount(int addMethodCount) { this.addMethodCount = addMethodCount; }
        public int getUpdateMethodCount() { return updateMethodCount; }
        public void setUpdateMethodCount(int updateMethodCount) { this.updateMethodCount = updateMethodCount; }
        public int getDeleteMethodCount() { return deleteMethodCount; }
        public void setDeleteMethodCount(int deleteMethodCount) { this.deleteMethodCount = deleteMethodCount; }
        public int getImpactCaseCount() { return impactCaseCount; }
        public void setImpactCaseCount(int impactCaseCount) { this.impactCaseCount = impactCaseCount; }
        public String getJobLog() { return jobLog; }
        public void setJobLog(String jobLog) { this.jobLog = jobLog; }
    }

    public static class DifferenceGroupSummary {
        private String className;
        private String model;
        private List<MethodDifferenceSummary> methods;

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public List<MethodDifferenceSummary> getMethods() { return methods; }
        public void setMethods(List<MethodDifferenceSummary> methods) { this.methods = methods; }
    }

    public static class MethodDifferenceSummary {
        private String className;
        private String methodName;
        private String methodDesc;
        private String model;

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getMethodDesc() { return methodDesc; }
        public void setMethodDesc(String methodDesc) { this.methodDesc = methodDesc; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class UsecaseImpactSummary {
        private String id;
        private String title;
        private String directoryPath;
        private String[] differences;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDirectoryPath() { return directoryPath; }
        public void setDirectoryPath(String directoryPath) { this.directoryPath = directoryPath; }
        public String[] getDifferences() { return differences; }
        public void setDifferences(String[] differences) { this.differences = differences; }
    }

    public static class ImpactHintSummary {
        private Integer snapshotCount;
        private List<String> hitSnapshots = Collections.emptyList();
        private List<String> zeroHitClasses = Collections.emptyList();

        public Integer getSnapshotCount() { return snapshotCount; }
        public void setSnapshotCount(Integer snapshotCount) { this.snapshotCount = snapshotCount; }
        public List<String> getHitSnapshots() { return hitSnapshots; }
        public void setHitSnapshots(List<String> hitSnapshots) { this.hitSnapshots = hitSnapshots; }
        public List<String> getZeroHitClasses() { return zeroHitClasses; }
        public void setZeroHitClasses(List<String> zeroHitClasses) { this.zeroHitClasses = zeroHitClasses; }
    }
}
