package com.oAT.web.control;

import com.oAT.web.config.FrontendProperties;
import com.oAT.web.coveragecore.query.CoverageCoreQueryService;
import com.oAT.web.coveragecore.query.CoverageUnitQuery;
import com.oAT.web.coveragecore.report.CoverageReportCommandService;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.SystemLog;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SystemLogService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.UserVo;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/p/{projectId}/coverage")
public class CoverageControl {

    @Autowired
    FrontendProperties frontendProperties;


    @Autowired
    private CoverageCoreQueryService coverageCoreQueryService;

    @Autowired
    private CoverageReportCommandService coverageReportCommandService;

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
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(versionNumber)) {
            return "redirect:" + frontendProperties.url("/p/" + projectId + "/coverage");
        }
        StringBuilder target = new StringBuilder("/p/")
                .append(projectId).append("/apps/").append(appId)
                .append("/coverage?versionNumber=").append(versionNumber);
        if (StringUtils.hasText(reportId)) {
            target.append("&reportId=").append(reportId);
        }
        if (StringUtils.hasText(commitId)) {
            target.append("&commitId=").append(commitId);
        }
        return "redirect:" + frontendProperties.url(target.toString());
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
        CoverageReportIndex report = coverageCoreQueryService.getReport(reportId);
        Assert.notNull(report, "覆盖率报告不存在");
        StringBuilder target = new StringBuilder("/p/")
                .append(projectId).append("/apps/").append(report.getAppId())
                .append("/coverage/details?reportId=").append(reportId);
        if (StringUtils.hasText(viewType)) {
            target.append("&viewType=").append(viewType);
        }
        if (StringUtils.hasText(report.getVersionNumber())) {
            target.append("&versionNumber=").append(report.getVersionNumber());
        }
        if (StringUtils.hasText(report.getRepoCommitId())) {
            target.append("&commitId=").append(report.getRepoCommitId());
        }
        return "redirect:" + frontendProperties.url(target.toString());
    }

    /**
     * 导出 Excel
     */
    @RequestMapping("/export")
    public void export(@RequestParam String reportId, HttpServletResponse response) throws IOException {
        coverageReportCommandService.exportReport(reportId, response);
    }

    /**
     * 导出方法级 Excel
     */
    @RequestMapping("/export-methods")
    public void exportMethods(@RequestParam String reportId, HttpServletResponse response) throws IOException {
        coverageReportCommandService.exportMethodReport(reportId, response);
    }

    /**
     * 生成报告 API
     */
    @PostMapping("/generate")
    @ResponseBody
    public com.oAT.web.control.entity.ResultNotified<String> generate(@PathVariable String projectId, String appId, String versionNumber, String branch, String commitId, @SessionAttribute UserVo user) {
        String jobId = coverageReportCommandService.startVersionFullReport(appId, versionNumber, branch, commitId);

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
        String jobId = coverageReportCommandService.startIncrementalReport(appId, versionNumber, branch, commitId, baseVersionNumber, baseCommitId);

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
        return coverageReportCommandService.getJob(jobId);
    }

    /**
     * 获取趋势图数据 API
     */
    @RequestMapping("/trend-data")
    @ResponseBody
    public List<Map<String, Object>> getTrendData(String appId, String versionNumber) {
        return coverageReportCommandService.getTrendData(appId, versionNumber);
    }

    /**
     * 查看代码（着色）
     */
    @RequestMapping("/code")
    public String viewCode(@PathVariable String projectId, String appId, String reportId, String className, Model model) {
        CoverageReportIndex report = coverageCoreQueryService.getReport(reportId);
        if (report != null) {
            appId = report.getAppId();
        }
        Assert.hasText(appId, "应用不存在");
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/coverage/code?reportId=" + reportId + "&className=" + className);
    }

    /**
     * 删除报告 API
     */
    @PostMapping("/delete")
    @ResponseBody
    public com.oAT.web.control.entity.ResultNotified<String> delete(@PathVariable String projectId, String reportId, @SessionAttribute UserVo user) {
        CoverageReportIndex report = coverageReportCommandService.getReport(reportId);
        if (report == null) {
            return new com.oAT.web.control.entity.ResultNotified<>(false, "Report not found", null);
        }

        // 权限校验: 检查用户是否是项目成员
        if (projectService.getProjectByProjectIdAndMemberId(projectId, user.getId()) == null) {
            return new com.oAT.web.control.entity.ResultNotified<>(false, "Permission denied", null);
        }

        coverageReportCommandService.deleteReport(reportId);

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
        return coverageCoreQueryService.listTreeNodes(reportId, parentPackage, query);
    }
}
