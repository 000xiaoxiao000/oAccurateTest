package com.oAT.web.control.api;

import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.coverage.FrontendCoverageService;
import com.oAT.web.coverage.FrontendCoverageService.FrontendCoverageGenerateRequest;
import com.oAT.web.coverage.FrontendCoverageService.FrontendCoverageReportRequest;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/api/projects/{projectId}/apps/{appId}/coverage/frontend")
public class FrontendCoverageApiControl {
    private final FrontendCoverageService frontendCoverageService;
    private final ProjectService projectService;

    public FrontendCoverageApiControl(FrontendCoverageService frontendCoverageService,
                                      ProjectService projectService) {
        this.frontendCoverageService = frontendCoverageService;
        this.projectService = projectService;
    }

    @PostMapping("/report")
    public ResultNotified<String> report(@PathVariable String projectId,
                                         @PathVariable String appId,
                                         @RequestBody FrontendCoverageReportRequest request) {
        String reportId = frontendCoverageService.saveReport(projectId, appId, request);
        return new ResultNotified<>(true, "前端覆盖率上报成功", reportId);
    }

    @PostMapping("/generate")
    public ResultNotified<String> generate(@PathVariable String projectId,
                                           @PathVariable String appId,
                                           @SessionAttribute UserVo user,
                                           @RequestBody(required = false) FrontendCoverageGenerateRequest request) {
        ensureProjectAccess(projectId, user);
        CoverageReportIndex report = frontendCoverageService.generateReport(projectId, appId, request);
        return new ResultNotified<>(true, "前端覆盖率报告生成成功", report.getId());
    }

    private ProjectVo ensureProjectAccess(String projectId, UserVo user) {
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        Assert.notNull(project, "找不到指定项目,或者您没有该项目的访问权限");
        return project;
    }
}
