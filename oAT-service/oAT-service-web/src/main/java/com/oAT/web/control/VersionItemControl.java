package com.oAT.web.control;

import com.oAT.web.config.FrontendProperties;
import com.oAT.web.api.version.VersionGitWorkflowService;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.coveragecore.report.CoverageReportCommandService;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private CoverageReportCommandService coverageReportCommandService;

    @Autowired
    StaticInfoRepository staticInfoRepository;

    @Autowired
    SystemLogService systemLogService;

    @Autowired
    VersionGitWorkflowService versionGitWorkflowService;


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

        if ("on".equals(itemVo.getSetAsCurrent())) {
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
            GitPullEstimateVo estimate = versionGitWorkflowService.checkGitPull(appId, branch, commitId, versionNumber, excludePaths);
            return new ResultNotified<>(true, "检测通过", estimate);
        } catch (Exception e) {
            return new ResultNotified<>(false, "检测失败: " + e.getMessage(), null);
        }
    }

    @RequestMapping("{appId}/version/package/verifyCommit")
    @ResponseBody
    public ResultNotified<PackageCommitVerifyVo> verifyUploadedPackageCommit(@PathVariable String appId, String programFile, String commitId) {
        try {
            PackageCommitVerifyVo verify = versionGitWorkflowService.verifyUploadedPackageCommit(appId, programFile, commitId);
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
            String jobId = versionGitWorkflowService.startGitPull(appId, branch, commitId, excludePaths, versionNumber);
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
    public String snapshotsCodeReport(@PathVariable String projectId, @PathVariable String appId) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/snapshots");
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
            coverageReportCommandService.deleteReport(reportId);
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

}
