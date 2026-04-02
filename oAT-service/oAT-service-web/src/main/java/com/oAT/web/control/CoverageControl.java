package com.oAT.web.control;

import com.oAT.web.common.CoverageSourceClassUtil;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.SystemLog;
import com.oAT.web.service.AppService;
import com.oAT.web.service.CoverageService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SystemLogService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.CoverageComparisonVo;
import com.oAT.web.service.entity.UserVo;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/p/{projectId}/coverage")
public class CoverageControl {

    @Autowired
    private CoverageService coverageService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private AppService appService;

    @Autowired
    private com.oAT.web.service.VersionService versionService;

    @Autowired
    private SystemLogService systemLogService;

    /**
     * 第一层页面：覆盖率概览
     */
    @RequestMapping("/overview")
    public String overview(@PathVariable String projectId, String appId, String versionNumber,
                           @RequestParam(required = false) String reportId,
                           @RequestParam(required = false) String commitId, Model model) {
        CoverageReportIndex report;
        if (StringUtils.hasText(reportId)) {
            report = coverageService.getReport(reportId);
        } else {
            report = coverageService.getLatestReport(appId, versionNumber, commitId);
        }

        // 如果最新的是增量报告，我们也去拿一个全量的
        CoverageReportIndex fullReport = null;
        CoverageReportIndex incReport = null;

        if (report != null) {
            String targetCommitId = StringUtils.hasText(commitId) ? commitId : report.getRepoCommitId();
            if (report.getReportType() == 1) {
                incReport = report;
                fullReport = getLatestFullReport(appId, versionNumber, targetCommitId);
            } else {
                fullReport = report;
                incReport = getLatestIncrementalReport(appId, versionNumber, targetCommitId);
            }
        }

        // 检查是否有比全量报告更新的快照数据
        boolean hasNewerData;
        if (fullReport != null) {
            hasNewerData = coverageService.hasNewerData(appId, versionNumber, fullReport);
            // 获取对比数据
            CoverageComparisonVo comparison = coverageService.getComparison(fullReport.getId());
            model.addAttribute("comparison", comparison);
        } else {
            hasNewerData = coverageService.hasNewerData(appId, versionNumber, null);
        }

        model.addAttribute("report", fullReport);
        model.addAttribute("incReport", incReport);
        model.addAttribute("hasNewerData", hasNewerData);
        model.addAttribute("projectId", projectId);
        model.addAttribute("appId", appId);
        model.addAttribute("versionNumber", versionNumber);
        model.addAttribute("commitId", commitId);

        // Get App info for appName
        AppVo appVo = appService.getApp(appId);
        model.addAttribute("appName", appVo != null ? appVo.getName() : appId);

        // Fetch version info to get branch/commit if possible
        model.addAttribute("version", versionService.getVersionItemList(projectId, appId).stream()
                .filter(v -> v.getVersionNumber().equals(versionNumber) && (commitId == null || commitId.equals(v.getRepoCommitId())))
                .findFirst()
                .orElseGet(() -> versionService.getVersionItemList(projectId, appId).stream()
                        .filter(v -> v.getVersionNumber().equals(versionNumber))
                        .findFirst().orElse(null)));

        // Required by projectHeader.ftl
        model.addAttribute("project", projectService.getProject(projectId));
        model.addAttribute("apps", appService.getAppList(projectId));

        return "coverage/overview";
    }

    /**
     * 第二层页面：详细列表/树
     */
    @RequestMapping("/details")
    public String details(@PathVariable String projectId, String reportId,
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
                          @RequestParam(required = false) Integer maxComplexity,
                          Model model) {
        if ("list".equalsIgnoreCase(viewType)) {
            Page<ClassCoverageIndex> classPage = coverageService.getClassCoveragePage(reportId, className, methodName,
                    minRate, maxRate,
                    minBranchRate, maxBranchRate,
                    minMethodRate, maxMethodRate,
                    minComplexity, maxComplexity,
                    PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "lineRate", "branchRate", "methodRate")));
            model.addAttribute("classPage", classPage);
        } else {
            // Initial Tree nodes (root)
            model.addAttribute("rootNodes", coverageService.getTreeNodes(reportId, "", className, methodName,
                    minRate, maxRate, minBranchRate, maxBranchRate, minMethodRate, maxMethodRate, minComplexity, maxComplexity));
        }

        model.addAttribute("reportId", reportId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("viewType", viewType);

        // Keep search params for pagination links and tree lazy load
        model.addAttribute("className", className);
        model.addAttribute("methodName", methodName);
        model.addAttribute("minRate", minRate);
        model.addAttribute("maxRate", maxRate);
        model.addAttribute("minBranchRate", minBranchRate);
        model.addAttribute("maxBranchRate", maxBranchRate);
        model.addAttribute("minMethodRate", minMethodRate);
        model.addAttribute("maxMethodRate", maxMethodRate);
        model.addAttribute("minComplexity", minComplexity);
        model.addAttribute("maxComplexity", maxComplexity);

        // Extract appId and versionNumber from report to show in header
        CoverageReportIndex report = coverageService.getReport(reportId);
        if (report != null) {
            model.addAttribute("report", report);
            model.addAttribute("reportNeedRegenerate", coverageService.hasNewerData(report.getAppId(), report.getVersionNumber(), report));
            model.addAttribute("appId", report.getAppId());
            model.addAttribute("versionNumber", report.getVersionNumber());
            AppVo appVo = appService.getApp(report.getAppId());
            model.addAttribute("appName", appVo != null ? appVo.getName() : report.getAppId());

            // Fetch version info to get branch/commit if possible
            model.addAttribute("version", versionService.getVersionItemList(projectId, report.getAppId()).stream()
                    .filter(v -> v.getVersionNumber().equals(report.getVersionNumber()) && (report.getRepoCommitId() == null || report.getRepoCommitId().equals(v.getRepoCommitId())))
                    .findFirst()
                    .orElseGet(() -> versionService.getVersionItemList(projectId, report.getAppId()).stream()
                            .filter(v -> v.getVersionNumber().equals(report.getVersionNumber()))
                            .findFirst().orElse(null)));
        }

        // Required by projectHeader.ftl
        model.addAttribute("project", projectService.getProject(projectId));
        model.addAttribute("apps", appService.getAppList(projectId));

        return "coverage/details";
    }

    /**
     * 导出 Excel
     */
    @RequestMapping("/export")
    public void export(@RequestParam String reportId, HttpServletResponse response) throws IOException {
        coverageService.exportReport(reportId, response);
    }

    /**
     * 导出方法级 Excel
     */
    @RequestMapping("/export-methods")
    public void exportMethods(@RequestParam String reportId, HttpServletResponse response) throws IOException {
        coverageService.exportMethodReport(reportId, response);
    }

    /**
     * 生成报告 API
     */
    @PostMapping("/generate")
    @ResponseBody
    public com.oAT.web.control.entity.ResultNotified<String> generate(@PathVariable String projectId, String appId, String versionNumber, String branch, String commitId, @SessionAttribute UserVo user) {
        String jobId = coverageService.startGenerateJob(appId, versionNumber, branch, commitId);

        // 记录日志
        AppVo appVo = appService.getApp(appId);
        String appName = appVo != null ? appVo.getName() : appId;
        SystemLog log = new SystemLog();
        log.setTitle(String.format("%s 生成了应用 [%s] 的 全量 覆盖率报告 [版本:%s, 分支:%s, Commit:%s]",
                user.getName(), appName, versionNumber, branch, commitId));
        log.setUserId(user.getId());
        log.setUserName(user.getName());
        log.setProjectId(projectId);
        log.setAction(SystemLogService.Action.generateReport.toString());
        systemLogService.addLog(log);

        return new com.oAT.web.control.entity.ResultNotified<>(true, "Task started", jobId);
    }

    /**
     * 生成增量报告 API
     */
    @PostMapping("/generate-incremental")
    @ResponseBody
    public com.oAT.web.control.entity.ResultNotified<String> generateIncremental(@PathVariable String projectId, String appId, String versionNumber, String branch, String commitId,
                                                                              String baseVersionNumber, String baseCommitId, @SessionAttribute UserVo user) {
        String jobId = coverageService.startGenerateIncrementalJob(appId, versionNumber, branch, commitId, baseVersionNumber, baseCommitId);

        // 记录日志
        AppVo appVo = appService.getApp(appId);
        String appName = appVo != null ? appVo.getName() : appId;
        SystemLog log = new SystemLog();
        log.setTitle(String.format("%s 生成了应用 [%s] 的 增量 覆盖率报告 [版本:%s, 基准:%s, 分支:%s, Commit:%s]",
                user.getName(), appName, versionNumber, baseVersionNumber, branch, commitId));
        log.setUserId(user.getId());
        log.setUserName(user.getName());
        log.setProjectId(projectId);
        log.setAction(SystemLogService.Action.generateReport.toString());
        systemLogService.addLog(log);

        return new com.oAT.web.control.entity.ResultNotified<>(true, "Incremental task started", jobId);
    }

    /**
     * 获取任务状态 API
     */
    @RequestMapping("/job/{jobId}")
    @ResponseBody
    public com.oAT.web.common.Job<String> getJob(@PathVariable String jobId) {
        return coverageService.getJob(jobId);
    }

    /**
     * 获取趋势图数据 API
     */
    @RequestMapping("/trend-data")
    @ResponseBody
    public List<Map<String, Object>> getTrendData(String appId, String versionNumber) {
        return coverageService.getTrendData(appId, versionNumber);
    }

    /**
     * 查看代码（着色）
     */
    @RequestMapping("/code")
    public String viewCode(@PathVariable String projectId, String appId, String reportId, String className, Model model) {
        // 先获取 classCov 并按页面显示的规则排序，再传入 getColoredSource 的重载方法
        ClassCoverageIndex classCov = coverageService.getClassCoverage(reportId, className);
        String displayClassName = toDisplayClassName(className);

        if (classCov != null && classCov.getMethods() != null) {
            classCov.getMethods().sort((a, b) -> {
                double rateA = a.getTotalLines() > 0 ? (double) a.getCoveredLines() / a.getTotalLines() : 0;
                double rateB = b.getTotalLines() > 0 ? (double) b.getCoveredLines() / b.getTotalLines() : 0;
                if (rateA != rateB) {
                    return Double.compare(rateB, rateA); // Descending
                }
                double bRateA = a.getTotalBranches() > 0 ? (double) a.getCoveredBranches() / a.getTotalBranches() : 0;
                double bRateB = b.getTotalBranches() > 0 ? (double) b.getCoveredBranches() / b.getTotalBranches() : 0;
                return Double.compare(bRateB, bRateA); // Descending
            });
        }

        // 使用按顺序整理过的 classCov 调用覆盖着色（避免模板方法列表顺序与着色锚点不一致）
        String coloredSource = coverageService.getColoredSource(appId, classCov);

        model.addAttribute("coloredSource", coloredSource);
        model.addAttribute("classCov", classCov);
        model.addAttribute("className", className);
        model.addAttribute("rawClassName", className);
        model.addAttribute("displayClassName", displayClassName);
        model.addAttribute("reportId", reportId);

        // 为面包屑补充信息
        model.addAttribute("projectId", projectId);
        model.addAttribute("project", projectService.getProject(projectId));
        model.addAttribute("appId", appId);
        AppVo appVo = appService.getApp(appId);
        model.addAttribute("appName", appVo != null ? appVo.getName() : appId);

        CoverageReportIndex report = coverageService.getReport(reportId);
        if (report != null) {
            model.addAttribute("report", report);
            model.addAttribute("reportNeedRegenerate", coverageService.hasNewerData(appId, report.getVersionNumber(), report));
            model.addAttribute("versionNumber", report.getVersionNumber());
            // Fetch version info to get branch/commit if possible
            model.addAttribute("version", versionService.getVersionItemList(projectId, appId).stream()
                    .filter(v -> v.getVersionNumber().equals(report.getVersionNumber()) && (report.getRepoCommitId() == null || report.getRepoCommitId().equals(v.getRepoCommitId())))
                    .findFirst()
                    .orElseGet(() -> versionService.getVersionItemList(projectId, appId).stream()
                            .filter(v -> v.getVersionNumber().equals(report.getVersionNumber()))
                            .findFirst().orElse(null)));
        }

        return "coverage/code_view";
    }

    private String toDisplayClassName(String className) {
        if (!StringUtils.hasText(className)) {
            return className;
        }
        String normalizedClassName = className.replace('$', '.');
        int lastDot = normalizedClassName.lastIndexOf('.');
        if (lastDot < 0 || lastDot >= normalizedClassName.length() - 1) {
            return normalizedClassName;
        }

        String prefix = normalizedClassName.substring(0, lastDot + 1);
        String tail = normalizedClassName.substring(lastDot + 1);
        return prefix + CoverageSourceClassUtil.toTreeDisplayName(tail, "class");
    }

    /**
     * 删除报告 API
     */
    @PostMapping("/delete")
    @ResponseBody
    public com.oAT.web.control.entity.ResultNotified<String> delete(@PathVariable String projectId, String reportId, @SessionAttribute UserVo user) {
        CoverageReportIndex report = coverageService.getReport(reportId);
        if (report == null) {
            return new com.oAT.web.control.entity.ResultNotified<>(false, "Report not found", null);
        }

        // 权限校验: 检查用户是否是项目成员
        if (projectService.getProjectByProjectIdAndMemberId(projectId, user.getId()) == null) {
            return new com.oAT.web.control.entity.ResultNotified<>(false, "Permission denied", null);
        }

        coverageService.deleteReport(reportId);

        // 获取应用名称和报告类型
        AppVo appVo = appService.getApp(report.getAppId());
        String appName = appVo != null ? appVo.getName() : report.getAppId();
        String reportTypeStr = (report.getReportType() != null && report.getReportType() == 1) ? "增量" : "全量";

        // 记录日志
        SystemLog log = new SystemLog();
        log.setTitle(String.format("%s 删除了应用 [%s] 的 %s 覆盖率报告 [版本:%s] - %s",
                user.getName(), appName, reportTypeStr,
                report.getVersionNumber(),
                reportId));
        log.setUserId(user.getId());
        log.setUserName(user.getName());
        log.setProjectId(projectId);
        log.setAction(SystemLogService.Action.deleteReport.toString());
        systemLogService.addLog(log);

        return new com.oAT.web.control.entity.ResultNotified<>(true, "报告已删除", reportId);
    }

    /**
     * 树型结构的懒加载接口
     */
    @RequestMapping("/treeNodes")
    @ResponseBody
    public List<com.oAT.web.service.entity.CoverageTreeNode> getTreeNodes(@PathVariable String projectId, String reportId,
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
        return coverageService.getTreeNodes(reportId, parentPackage, className, methodName,
                minRate, maxRate, minBranchRate, maxBranchRate, minMethodRate, maxMethodRate, minComplexity, maxComplexity);
    }

    private CoverageReportIndex getLatestFullReport(String appId, String versionNumber, String commitId) {
        return coverageService.getLatestReportByType(appId, versionNumber, 0, commitId);
    }

    private CoverageReportIndex getLatestIncrementalReport(String appId, String versionNumber, String commitId) {
        return coverageService.getLatestReportByType(appId, versionNumber, 1, commitId);
    }
}
