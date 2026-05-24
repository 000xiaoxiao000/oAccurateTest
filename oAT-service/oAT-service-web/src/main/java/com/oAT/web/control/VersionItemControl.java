package com.oAT.web.control;

import com.oAT.web.config.FrontendProperties;
import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.common.compare.CompareResult;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequestMapping("/p/{projectId}/")
public class VersionItemControl {

    @Autowired
    FrontendProperties frontendProperties;


    private static final Logger logger = LoggerFactory.getLogger(VersionItemControl.class);

    @Autowired
    VersionService versionService;
    @Autowired
    AppService appService;

    @Autowired
    UsecaseService usecaseService;

    @Autowired
    SystemSnapshotService systemSnapshotService;
    @Autowired
    ProjectService projectService;
    @Autowired
    GitService gitService;
    @Autowired
    ResourceService resourceService;

    @Autowired
    ClientSessionService clientSessionService;

    @Autowired
    SnapshotService snapshotService;

    @Autowired
    UserService userService;

    @Autowired
    private CoverageService coverageService;

    @Autowired
    StaticInfoRepository staticInfoRepository;

    @Autowired
    SystemLogService systemLogService;


    @RequestMapping("{appId}/version/new")
    public String addVersion(@PathVariable String projectId, @PathVariable String appId, Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/versions/new");
    }

    @RequestMapping("{appId}/version/doAdd")
    @ResponseBody
    public ResultNotified<String> doAdd(@PathVariable String projectId, @PathVariable String appId, VersionItemVo itemVo) {
        if ("git".equals(itemVo.getSourceType())) {
            // 检查 同版本号 + 分支 + CommitId 是否重复
            VersionItemVo existing = versionService.getVersionByGitInfo(appId, itemVo.getVersionNumber(), itemVo.getRepoBranch(), itemVo.getRepoCommitId());
            if (existing != null) {
                return new ResultNotified<>(false, "该版本号下已存在相同的分支和 CommitID");
            }

            if (StringUtils.hasText(itemVo.getProgramFile())) {
                File programFile = new File(itemVo.getProgramFile());
                if (!programFile.exists()) {
                    programFile = new File(resourceService.getCacheRoot(), itemVo.getProgramFile());
                }

                if (programFile.exists()) {
                    if (!StringUtils.hasText(itemVo.getProgramName())) {
                        itemVo.setProgramName(programFile.getName());
                    }
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

    @RequestMapping(value = "{appId}/version/setCurrent", method = RequestMethod.POST)
    @ResponseBody
    public ResultNotified<String> setCurrentVersion(@PathVariable String projectId,
                                                    @PathVariable String appId,
                                                    String versionNumber,
                                                    String branch,
                                                    String commitId,
                                                    @SessionAttribute UserVo user) {
        try {
            // 权限校验
            List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);
            boolean hasPermission = false;
            for (ProjectMemberVo member : members) {
                if (user.getName().equals(member.getMemberName())) {
                    if (!"visitor".equalsIgnoreCase(String.valueOf(member.getRole()))) {
                        hasPermission = true;
                        break;
                    }
                }
            }

            if (!hasPermission) {
                return new ResultNotified<>(false, "没有权限进行此操作");
            }

            AppVo app = appService.getApp(appId);
            if (app == null) {
                return new ResultNotified<>(false, "应用不存在");
            }

            String oldVersionText = String.format("[%s (分支:%s, Commit:%s)]",
                app.getCurrentVersion() != null ? app.getCurrentVersion() : "未设置",
                app.getCurrentBranch() != null ? app.getCurrentBranch() : "-",
                app.getCurrentCommitId() != null ? (app.getCurrentCommitId().length() > 7 ? app.getCurrentCommitId().substring(0, 7) : app.getCurrentCommitId()) : "-");

            String newVersionText = String.format("[%s (分支:%s, Commit:%s)]",
                versionNumber, branch != null ? branch : "-",
                commitId != null ? (commitId.length() > 7 ? commitId.substring(0, 7) : commitId) : "-");

            app.setCurrentVersion(versionNumber);
            app.setCurrentBranch(branch);
            app.setCurrentCommitId(commitId);

            appService.updateApp(projectId, app);

            // 记录日志
            SystemLog log = new SystemLog();
            log.setTitle(String.format("%s 将应用 %s 的当前版本从 %s 变更为 %s",
                user.getName(), app.getName(), oldVersionText, newVersionText));
            log.setUserId(user.getId());
            log.setUserName(user.getName());
            log.setProjectId(projectId);
            log.setAction(SystemLogService.Action.editApp.toString());
            systemLogService.addLog(log);

            return new ResultNotified<>(true, "设置当前版本成功");
        } catch (Exception e) {
            logger.error("设置当前版本失败", e);
            return new ResultNotified<>(false, "设置当前版本失败: " + e.getMessage());
        }
    }

    @RequestMapping("{appId}/version/checkGitPull")
    @ResponseBody
    public ResultNotified<GitPullEstimateVo> checkGitPull(@PathVariable String appId, String branch, String commitId, String versionNumber, String excludePaths) {
        try {
            AppVo app = appService.getApp(appId);
            String finalBranch = branch != null ? branch.trim() : "";
            String finalCommitId = commitId != null ? commitId.trim() : "";

            ResultNotified<String> excludePathCheckResult = validateExcludePaths(excludePaths);
            if (excludePathCheckResult != null) {
                return new ResultNotified<>(false, excludePathCheckResult.getMessage(), null);
            }

            gitService.checkGitPull(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), finalBranch, finalCommitId);

            // 数据库预检查
            String checkCommitId = finalCommitId;
            if (checkCommitId.isEmpty()) {
                checkCommitId = gitService.getLatestCommitId(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), finalBranch);
            }

            // 检查 同版本号 + 分支 + CommitId 是否重复
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

    @RequestMapping("{appId}/version/package/verifyCommit")
    @ResponseBody
    public ResultNotified<PackageCommitVerifyVo> verifyUploadedPackageCommit(@PathVariable String appId, String programFile, String commitId) {
        try {
            String targetCommitId = StringUtils.hasText(commitId) ? commitId.trim() : readPackageCommitId(programFile);
            PackageCommitVerifyVo verify = verifyRuntimeCommit(appId, targetCommitId);
            return new ResultNotified<>(true, "校验完成", verify);
        } catch (Exception e) {
            return new ResultNotified<>(false, "校验失败: " + e.getMessage(), null);
        }
    }

    @RequestMapping("{appId}/version/git/commit")
    @ResponseBody
    public ResultNotified<String> getGitLatestCommit(@PathVariable String appId, String branch) {
        try {
            AppVo app = appService.getApp(appId);
            String commitId = gitService.getLatestCommitId(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), branch);
            return new ResultNotified<>(true, "获取成功", commitId);
        } catch (Exception e) {
            return new ResultNotified<>(false, "获取失败: " + e.getMessage(), null);
        }
    }

    @RequestMapping("{appId}/version/git/commits")
    @ResponseBody
    public ResultNotified<List<GitCommitOptionVo>> getGitRecentCommits(@PathVariable String appId,
                                                                       String branch,
                                                                       @RequestParam(defaultValue = "20") int limit) {
        try {
            AppVo app = appService.getApp(appId);
            List<GitCommitOptionVo> commits = gitService.getRecentCommits(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), branch, limit);
            return new ResultNotified<>(true, "获取成功", commits);
        } catch (Exception e) {
            return new ResultNotified<List<GitCommitOptionVo>>(false, "获取失败: " + e.getMessage(), Collections.emptyList());
        }
    }

    @RequestMapping("{appId}/version/git/pull")
    @ResponseBody
    public ResultNotified<String> startGitPull(@PathVariable String appId, String branch, String commitId, String excludePaths, String versionNumber) {
        try {
            AppVo app = appService.getApp(appId);
            String finalBranch = branch != null ? branch.trim() : "";
            String finalCommitId = (commitId != null && !commitId.trim().isEmpty()) ? commitId.trim() : null;

            ResultNotified<String> excludePathCheckResult = validateExcludePaths(excludePaths);
            if (excludePathCheckResult != null) {
                return excludePathCheckResult;
            }

            // 如果 commitId 为空，先获取远程最新 commitId，以便查重
            String checkCommitId = finalCommitId;
            if (checkCommitId == null) {
                checkCommitId = gitService.getLatestCommitId(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), finalBranch);
            }

            // 检查 同版本号 + 分支 + CommitId 是否重复
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

    @RequestMapping("{appId}/version/git/deleteCode")
    @ResponseBody
    public ResultNotified<String> deleteCode(@PathVariable String appId, String cachePath) {
        try {
            gitService.deleteCache(cachePath);
            return new ResultNotified<>(true, "删除成功");
        } catch (Exception e) {
            return new ResultNotified<>(false, "删除失败: " + e.getMessage());
        }
    }

    private ResultNotified<String> validateExcludePaths(String excludePaths) {
        if (!StringUtils.hasText(excludePaths)) {
            return null;
        }

        String[] paths = excludePaths.split(",");
        for (String path : paths) {
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

            Path normalizedPath = Paths.get(trimmedPath).normalize();
            String normalized = normalizedPath.toString().replace('\\', '/');
            if (normalized.isEmpty() || ".".equals(normalized) || normalized.startsWith("../")) {
                return new ResultNotified<>(false, "检测失败: 排除路径格式不合法");
            }
        }
        return null;
    }

    private PackageCommitVerifyVo verifyRuntimeCommit(String appId, String targetCommitId) {
        List<ClientSessionVo> onlineSessions = clientSessionService.getOnlineSessionsByAppId(appId);
        if (onlineSessions == null || onlineSessions.isEmpty()) {
            return new PackageCommitVerifyVo(null, targetCommitId, null, false, "探针不在线，无法获取运行时目标系统 CommitId");
        }
        String runtimeCommitId = findRuntimePackageCommitId(appId, onlineSessions);
        String normalizedRuntimeCommitId = normalizeCommitId(runtimeCommitId);
        String normalizedTargetCommitId = normalizeCommitId(targetCommitId);
        Boolean matched = null;
        if (StringUtils.hasText(normalizedRuntimeCommitId) && StringUtils.hasText(normalizedTargetCommitId)) {
            matched = commitIdMatches(normalizedRuntimeCommitId, normalizedTargetCommitId);
        }
        String unavailableReason = StringUtils.hasText(runtimeCommitId) ? null : "探针在线，但暂未上报运行时目标系统 CommitId";
        return new PackageCommitVerifyVo(runtimeCommitId, targetCommitId, matched, true, unavailableReason);
    }

    private String findRuntimePackageCommitId(String appId) {
        return findRuntimePackageCommitId(appId, clientSessionService.getOnlineSessionsByAppId(appId));
    }

    private String findRuntimePackageCommitId(String appId, List<ClientSessionVo> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return null;
        }
        for (ClientSessionVo session : sessions) {
            String commitId = extractCommitIdFromPackageVerifyData(clientSessionService.getPackageVerifyData(session.getSessionId()));
            if (StringUtils.hasText(commitId)) {
                return commitId;
            }
        }
        for (ClientSessionVo session : sessions) {
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
        Matcher matcher =
                Pattern.compile("gitCommitIdFromPackage\\s*[:=]\\s*([0-9a-fA-F]{7,40})").matcher(packageVerifyData);
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
        String[] buildInfoPaths = {
                "META-INF/git.properties",
                "META-INF/build-info.properties",
                "WEB-INF/classes/META-INF/git.properties",
                "WEB-INF/classes/META-INF/build-info.properties",
                "WEB-INF/classes/git.properties",
                "WEB-INF/classes/build-info.properties"
        };
        for (String buildInfoPath : buildInfoPaths) {
            ZipEntry entry = zipFile.getEntry(buildInfoPath);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    private boolean commitIdMatches(String runtimeCommitId, String targetCommitId) {
        String runtime = normalizeCommitId(runtimeCommitId);
        String target = normalizeCommitId(targetCommitId);
        if (!StringUtils.hasText(runtime) || !StringUtils.hasText(target)) {
            return false;
        }
        return runtime.equals(target) || runtime.startsWith(target) || target.startsWith(runtime);
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

    @RequestMapping("{appId}/version/git/status")
    @ResponseBody
    public ResultNotified<GitJobVo> getGitPullStatus(@PathVariable String appId, String jobId) {
         GitJobVo job = gitService.getGitJob(jobId);
         if(job != null) {
             return new ResultNotified<>(true, "查询成功", job);
         } else {
             return new ResultNotified<>(false, "任务不存在", null);
         }
    }

    // 基于两个提交之间的 Git 差异比较（无需检出/构建）
    @RequestMapping("{appId}/version/git/compare")
    public String startGitDiffCompare(@PathVariable String projectId,
                                      @PathVariable String appId,
                                      String branch,
                                      String oldCommit,
                                      String newCommit,
                                      String packageName,
                                      Model model) throws UnsupportedEncodingException {
        AppVo appInfo = appService.getApp(appId);
        // default package scope
        if (packageName == null || packageName.isEmpty()) {
            packageName = "*";
        }
        String jobId = versionService.startCompareFromGit(projectId, appInfo, packageName, branch, oldCommit, newCommit);
        if (jobId == null || jobId.isEmpty()) {
            model.addAttribute("errorMessage", "git diff 比对未能启动");
            return "forward:/error/404";
        }
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/compare?jobId=" + jobId);
    }

    // 验证版本号 是否可用
    @RequestMapping("{appId}/version/checkExist")
    @ResponseBody
    public ResultNotified<Boolean> checkExist(@PathVariable String appId, String name) {
        // 修改为支持同版本号创建：总是返回可用
        return new ResultNotified<>(true, null, true);
    }

    @RequestMapping("{appId}/version/list")
    public String getVersionList(@PathVariable String projectId, @PathVariable String appId,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size,
                                 @SessionAttribute UserVo user,
                                 Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/versions");
    }

    @RequestMapping(value = "{appId}/version/delete", method = RequestMethod.POST)
    @ResponseBody
    public ResultNotified<String> doDelete(String id) {
        versionService.doDeleteVersionItem(id);
        return new ResultNotified<>(true, "版本项目删除成功");
    }

    // 选择管理版本的应用
    @RequestMapping("/version/apps")
    public String openAppListView(@PathVariable String projectId, Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/version/apps");
    }

    // 兼容旧路由：/p/{projectId}/{appId}/version/appList
    @RequestMapping("{appId}/version/appList")
    public String openAppListLegacyView(@PathVariable String projectId, @PathVariable String appId, Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/version/apps");
    }

    // 打开版本比对页面
    @RequestMapping("{appId}/version/compare")
    public String openCompareView(@PathVariable String projectId, @PathVariable String appId, Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/compare");
    }

    @RequestMapping("{appId}/version/compare/start")
    public String doCompare(@PathVariable String projectId,
                            @PathVariable String appId,
                            String sourceFile,
                            String targetFile, String packageName,
                            Model model) {
        AppVo appInfo = appService.getApp(appId);
        String jobId = versionService.startCompareJob(projectId, appInfo, packageName, sourceFile, targetFile);
        if (jobId == null || jobId.isEmpty()) {
            model.addAttribute("errorMessage", "源版本（新）或目标版本（旧）文件未选择");
            return "forward:/error/404";
        }
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/compare?jobId=" + jobId);
    }

    @RequestMapping("/version/compare/console")
    public String openCompareJobConsole(@PathVariable String projectId, String jobId, Model model) {
        if (!StringUtils.hasText(jobId)) {
            model.addAttribute("errorMessage", "比对任务不存在");
            return "forward:/error/404";
        }

        CompareJobVo job = versionService.getCompareJob(jobId);
        if (job == null || job.isFinish()) {
            return "redirect:" + frontendProperties.url("/p/" + projectId + "/version/reports/" + jobId);
        }
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + job.getAppId() + "/compare?jobId=" + jobId);
    }

    @RequestMapping("/version/compare/get")
    @ResponseBody
    public ResultNotified<CompareJobVo> getCompareJob(String jobId) {
        CompareJobVo job = versionService.getCompareJob(jobId);
        if (job == null) {
            return new ResultNotified<>(false, "比对任务不存在或已过期，请重新发起比对。", null);
        }
        return new ResultNotified<>(true, null, job);
    }

    // 打开比对报告列表页
    @RequestMapping("{appId}/version/report/list")
    public String openCompareReportList(@PathVariable String projectId,
                                        @PathVariable String appId,
                                        String tab,
                                        String highlightReportId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/compare");
    }

    @RequestMapping("{appId}/version/report/snapshots")
    public String snapshotsCodeReport(@PathVariable String projectId, @PathVariable String appId, Model model) {
        if (System.currentTimeMillis() >= 0) {
            return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/snapshots");
        }
        Map<String, Map<String, List<StackNodeVo>>> codeRelationships = new HashMap<>();
        List<SystemSnapshot> snapshots = systemSnapshotService.findAll(projectId, appId);

        // 用于计算聚合指标
        long totalMethods;
        long coveredMethods = 0;
        long totalLines = 0;
        long coveredLines = 0;
        long totalBranches = 0;
        long coveredBranches = 0;
        int totalComplexity = 0;

        // 全局去重用，确保同一方法在不同快照中被统计多次时，覆盖行能合并
        // Key: className + methodDescriptor
        Map<String, Set<Integer>> methodCoveredLines = new HashMap<>();
        Map<String, Set<Integer>> methodTotalLines = new HashMap<>();
        Map<String, Integer> methodComplexity = new HashMap<>();
        Map<String, Set<Integer>> methodTotalBranches = new HashMap<>();
        Map<String, Set<Integer>> methodCoveredBranches = new HashMap<>();
        Map<String, Set<String>> methodTotalBranchTargets = new HashMap<>();
        Map<String, Set<String>> methodCoveredBranchTargets = new HashMap<>();

        // 用于类级汇总
        Map<String, Set<String>> classMethods = new HashMap<>();
        Map<String, String> classToAppId = new HashMap<>();

        for (SystemSnapshot snapshot : snapshots) {
            String traceId = snapshot.getTraceId();
            //得到代码关系
            TraceNode traceNode = snapshotService.getTraceNode(traceId, "0");
            if (traceNode instanceof HttpTraceNode) {
                HttpTraceNode httpTraceNode = (HttpTraceNode) traceNode;
                String requestUrl = httpTraceNode.getRequestUrl();

                StackNodeVo[] codeNodes = httpTraceNode.getCodeNodes();
                if (codeNodes != null) {
                    Map<String, List<StackNodeVo>> childNodes =
                            Arrays.stream(codeNodes).collect(Collectors.groupingBy(StackNodeVo::parentId));
                    codeRelationships.put(requestUrl, childNodes);

                    for (StackNodeVo node : codeNodes) {
                        String methodKey = node.getMethodName() + "#" + node.getMethodDescriptor();
                        classMethods.computeIfAbsent(node.getClassName(), k -> new HashSet<>()).add(methodKey);

                        classToAppId.putIfAbsent(node.getClassName(), appId);

                        // 行覆盖
                        if (node.getDoLines() != null) {
                            methodCoveredLines.computeIfAbsent(methodKey, k -> new HashSet<>()).addAll(node.getDoLines());
                        }

                        // 分支覆盖
                        if (node.getExecuteBranch() != null) {
                            methodCoveredBranches.computeIfAbsent(methodKey, k -> new HashSet<>()).addAll(node.getExecuteBranch());
                        }
                        addBranchConditionKeys(methodCoveredBranchTargets, methodKey, node.getExecuteBranchTargetProbeMap());
                    }
                }
            }
        }

        // 从全量静态数据补充总数
        List<StaticSourceInfo> staticInfos = staticInfoRepository.findByAppId(appId);
        for (StaticSourceInfo si : staticInfos) {
            if (si.getClassInfo() == null || si.getClassInfo().getMethodMaps() == null) continue;
            for (StaticSourceMethodInfo mInfo : si.getClassInfo().getMethodMaps().values()) {
                String mKey = mInfo.getMethodName() + "#" + mInfo.getMethodDesc();
                if (methodCoveredLines.containsKey(mKey) || methodCoveredBranches.containsKey(mKey)) {
                    methodTotalLines.computeIfAbsent(mKey, k -> new HashSet<>())
                            .addAll(mInfo.getMethodLineNumberMap() != null ? mInfo.getMethodLineNumberMap() : Collections.emptyList());
                    methodComplexity.put(mKey, mInfo.getCyclomaticComplexityMap() != null ? mInfo.getCyclomaticComplexityMap() : 0);
                    methodTotalBranches.computeIfAbsent(mKey, k -> new HashSet<>())
                            .addAll(mInfo.getBranchLineNumberSet() != null ? mInfo.getBranchLineNumberSet() : Collections.emptyList());
                    addBranchConditionKeys(methodTotalBranchTargets, mKey, mInfo.getBranchLineAndTargetProbeMap());
                    if (methodCoveredBranchTargets.containsKey(mKey)) {
                        Set<String> normalizedKeys = new LinkedHashSet<>();
                        addBranchConditionKeysToSet(normalizedKeys, mInfo.getBranchLineAndTargetProbeMap(),
                                decodeBranchConditionKeys(methodCoveredBranchTargets.get(mKey)));
                        methodCoveredBranchTargets.put(mKey, normalizedKeys);
                    }
                }
            }
        }

        // 计算汇总
        totalMethods = methodTotalLines.size();
        long totalBranchTargets = 0;
        long coveredBranchTargets = 0;
        for (String mKey : methodTotalLines.keySet()) {
            totalLines += methodTotalLines.get(mKey).size();
            coveredLines += methodCoveredLines.getOrDefault(mKey, Collections.emptySet()).size();
            if (methodCoveredLines.containsKey(mKey) && !methodCoveredLines.get(mKey).isEmpty()) {
                coveredMethods++;
            }
            totalComplexity += methodComplexity.getOrDefault(mKey, 0);
            totalBranches += methodTotalBranches.getOrDefault(mKey, Collections.emptySet()).size();
            coveredBranches += methodCoveredBranches.getOrDefault(mKey, Collections.emptySet()).size();
            totalBranchTargets += methodTotalBranchTargets.getOrDefault(mKey, Collections.emptySet()).size();
            coveredBranchTargets += methodCoveredBranchTargets.getOrDefault(mKey, Collections.emptySet()).size();
        }

        // 生成类级详细统计
        List<Map<String, Object>> classStats = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : classMethods.entrySet()) {
            String className = entry.getKey();
            Set<String> methods = entry.getValue();
            String classAppId = classToAppId.get(className);

            long cTotalMethods = methods.size();
            long cCoveredMethods = 0;
            long cTotalLines = 0;
            long cCoveredLines = 0;
            long cTotalBranches = 0;
            long cCoveredBranches = 0;
            long cTotalBranchTargets = 0;
            long cCoveredBranchTargets = 0;
            int cTotalComplexity = 0;

            for (String mKey : methods) {
                cTotalLines += methodTotalLines.get(mKey).size();
                cCoveredLines += methodCoveredLines.getOrDefault(mKey, Collections.emptySet()).size();
                if (methodCoveredLines.containsKey(mKey) && !methodCoveredLines.get(mKey).isEmpty()) {
                    cCoveredMethods++;
                }
                cTotalComplexity += methodComplexity.getOrDefault(mKey, 0);
                cTotalBranches += methodTotalBranches.getOrDefault(mKey, Collections.emptySet()).size();
                cCoveredBranches += methodCoveredBranches.getOrDefault(mKey, Collections.emptySet()).size();
                cTotalBranchTargets += methodTotalBranchTargets.getOrDefault(mKey, Collections.emptySet()).size();
                cCoveredBranchTargets += methodCoveredBranchTargets.getOrDefault(mKey, Collections.emptySet()).size();
            }

            Map<String, Object> cStat = new HashMap<>();
            cStat.put("className", className);
            cStat.put("appId", classAppId);
            cStat.put("totalMethods", cTotalMethods);
            cStat.put("coveredMethods", cCoveredMethods);
            cStat.put("totalLines", cTotalLines);
            cStat.put("coveredLines", cCoveredLines);
            cStat.put("totalBranches", cTotalBranches);
            cStat.put("coveredBranches", cCoveredBranches);
            cStat.put("branchRate", calculateBranchRate(cCoveredBranchTargets, cTotalBranchTargets));
            cStat.put("totalComplexity", cTotalComplexity);
            classStats.add(cStat);
        }

        CoverageReportIndex summary = new CoverageReportIndex();
        summary.setTotalMethods(totalMethods);
        summary.setCoveredMethods(coveredMethods);
        summary.setTotalLines(totalLines);
        summary.setCoveredLines(coveredLines);
        summary.setTotalBranches(totalBranches);
        summary.setCoveredBranches(coveredBranches);
        summary.setTotalBranchTargets(totalBranchTargets);
        summary.setCoveredBranchTargets(coveredBranchTargets);
        summary.setTotalComplexity(totalComplexity);
        summary.setTotalClasses(classMethods.size());
        summary.setCoveredClasses(summary.getTotalClasses());

        model.addAttribute("report", summary);
        model.addAttribute("classStats", classStats);
        model.addAttribute("codeRelationships", codeRelationships);
        model.addAttribute("codeRelatSize", codeRelationships.size());
        model.addAttribute("projectId", projectId);
        model.addAttribute("appId", appId);
        model.addAttribute("fromVersionCenter", true); // 用于模板识别来源

        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/snapshots");
    }

    private double calculateBranchRate(long coveredBranchTargets, long totalBranchTargets) {
        return totalBranchTargets > 0 ? (double) coveredBranchTargets / totalBranchTargets * 100 : 0.0;
    }

    private void addBranchConditionKeys(Map<String, Set<String>> target,
                                        String methodKey,
                                        Map<String, List<Integer>> branchConditionNumbers) {
        if (branchConditionNumbers == null || branchConditionNumbers.isEmpty()) {
            return;
        }
        Set<String> keys = target.computeIfAbsent(methodKey, key -> new LinkedHashSet<>());
        for (Map.Entry<String, List<Integer>> entry : branchConditionNumbers.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            for (Integer conditionNumber : entry.getValue()) {
                if (conditionNumber != null) {
                    keys.add(entry.getKey() + "#" + conditionNumber);
                }
            }
        }
    }

    private Map<String, List<Integer>> normalizeCoveredBranchTargetProbeMap(Map<String, List<Integer>> total,
                                                                            Map<String, List<Integer>> covered) {
        if (total == null || total.isEmpty() || covered == null || covered.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, List<Integer>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : total.entrySet()) {
            List<Integer> totalValues = entry.getValue();
            if (totalValues == null || totalValues.isEmpty()) {
                continue;
            }
            Set<Integer> allowed = new LinkedHashSet<>(totalValues);
            List<Integer> coveredValues = covered.get(entry.getKey());
            if (coveredValues == null || coveredValues.isEmpty()) {
                continue;
            }
            LinkedHashSet<Integer> matched = new LinkedHashSet<>();
            for (Integer value : coveredValues) {
                if (value != null && allowed.contains(value)) {
                    matched.add(value);
                }
            }
            if (!matched.isEmpty()) {
                normalized.put(entry.getKey(), new ArrayList<>(matched));
            }
        }
        return normalized;
    }

    private void addBranchConditionKeysToSet(Set<String> target,
                                             Map<String, List<Integer>> allowedBranchConditionNumbers,
                                             Map<String, List<Integer>> branchConditionNumbers) {
        Map<String, List<Integer>> effective = normalizeCoveredBranchTargetProbeMap(
                allowedBranchConditionNumbers, branchConditionNumbers);
        for (Map.Entry<String, List<Integer>> entry : effective.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            for (Integer conditionNumber : entry.getValue()) {
                if (conditionNumber != null) {
                    target.add(entry.getKey() + "#" + conditionNumber);
                }
            }
        }
    }

    private Map<String, List<Integer>> decodeBranchConditionKeys(Set<String> keys) {
        Map<String, List<Integer>> decoded = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty()) {
            return decoded;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            int split = key.lastIndexOf('#');
            if (split <= 0 || split >= key.length() - 1) {
                continue;
            }
            try {
                int conditionNumber = Integer.parseInt(key.substring(split + 1));
                decoded.computeIfAbsent(key.substring(0, split), k -> new ArrayList<>()).add(conditionNumber);
            } catch (NumberFormatException ignore) {
            }
        }
        return decoded;
    }

    // 支持 AJAX POST 删除，返回 JSON
    @RequestMapping(value = "{appId}/version/report/delete", method = RequestMethod.POST)
    @ResponseBody
    public ResultNotified<String> deleteCompareReport(@PathVariable String projectId, @PathVariable String appId, String reportId) {
        try {
            versionService.deleteCompareReport(projectId, reportId);
            return new ResultNotified<>(true, "报告删除成功");
        } catch (Exception e) {
            return new ResultNotified<>(false, "删除失败: " + e.getMessage());
        }
    }

    @RequestMapping("{appId}/version/file/delete")
    @ResponseBody
    public ResultNotified<String> deleteVersionFile(@PathVariable String projectId, @PathVariable String appId, String filePath) {
        try {
            versionService.deleteCacheFile(filePath);
            return new ResultNotified<>(true, "本地文件已删除", filePath);
        } catch (Exception e) {
            return new ResultNotified<>(false, "删除本地文件失败: " + e.getMessage());
        }
    }

    @RequestMapping(value = "{appId}/version/coverageReport/delete", method = RequestMethod.POST)
    @ResponseBody
    public ResultNotified<String> deleteCoverageReport(@PathVariable String projectId, @PathVariable String appId, String reportId) {
        try {
            coverageService.deleteReport(reportId);
            return new ResultNotified<>(true, "覆盖率报告删除成功");
        } catch (Exception e) {
            return new ResultNotified<>(false, "删除失败: " + e.getMessage());
        }
    }

    @RequestMapping(value = "{appId}/version/snapshotReport/delete", method = RequestMethod.POST)
    @ResponseBody
    public ResultNotified<String> deleteSnapshotReport(@PathVariable String projectId, @PathVariable String appId, String snapshotId) {
        try {
            appService.deleteSnapshot(snapshotId);
            return new ResultNotified<>(true, "快照报告删除成功");
        } catch (Exception e) {
            return new ResultNotified<>(false, "删除失败: " + e.getMessage());
        }
    }

    @RequestMapping("/version/report/detail/{reportId}")
    public String openCompareReport(@PathVariable String projectId, @PathVariable String reportId, Model model) {
        try {
            VersionCompareReport report = versionService.getCompareReport(reportId);
            return "redirect:" + frontendProperties.url("/p/" + projectId + "/version/reports/" + reportId + "?appId=" + report.getAppId());
        } catch (IllegalArgumentException ex) {
            CompareJobVo compareJob = versionService.getCompareJob(reportId);
            String appId = compareJob == null ? "" : compareJob.getAppId();
            return "redirect:" + frontendProperties.url("/p/" + projectId + "/version/reports/" + reportId + "?appId=" + appId);
        }
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
            logger.warn("解析用例目录失败, directoryId={}", directoryId, ex);
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

    public class ImpactHintSummary implements Serializable {
        private Integer snapshotCount;
        private List<String> hitSnapshots = Collections.emptyList();
        private List<String> zeroHitClasses = Collections.emptyList();

        public Integer getSnapshotCount() {
            return snapshotCount;
        }

        public void setSnapshotCount(Integer snapshotCount) {
            this.snapshotCount = snapshotCount;
        }

        public List<String> getHitSnapshots() {
            return hitSnapshots;
        }

        public void setHitSnapshots(List<String> hitSnapshots) {
            this.hitSnapshots = hitSnapshots;
        }

        public List<String> getZeroHitClasses() {
            return zeroHitClasses;
        }

        public void setZeroHitClasses(List<String> zeroHitClasses) {
            this.zeroHitClasses = zeroHitClasses;
        }
    }

    public class UsecaseGroup implements Serializable {
        String directoryName;
        List<UsecaseBo> list;

        public UsecaseGroup(String directoryName, List<UsecaseBo> list) {
            this.directoryName = directoryName;
            this.list = list;
        }

        public String getDirectoryName() {
            return directoryName;
        }

        public List<UsecaseBo> getList() {
            return list;
        }
    }

    public class UsecaseBo implements Serializable {
        private String id;
        private String name;
        private String directoryPath;
        private List<LabelGroup.Label> labels;
        private String[] differences;

        public UsecaseBo(String id, String name, List<LabelGroup.Label> labels, String[] differences) {
            this.id = id;
            this.name = name;
            this.labels = labels;
            this.differences = differences;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public List<LabelGroup.Label> getLabels() {
            return labels;
        }

        public String[] getDifferences() {
            return differences;
        }

        public String getDirectoryPath() {
            return directoryPath;
        }

        public void setDirectoryPath(String directoryPath) {
            this.directoryPath = directoryPath;
        }
    }

}
