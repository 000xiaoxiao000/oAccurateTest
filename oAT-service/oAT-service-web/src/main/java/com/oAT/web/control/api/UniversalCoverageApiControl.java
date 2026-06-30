package com.oAT.web.control.api;

import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.api.ingest.CoverageIngestFacade;
import com.oAT.web.coverage.universal.SourceType;
import com.oAT.web.coveragecore.ingest.UniversalCoverageAliasReportRequest;
import com.oAT.web.coveragecore.report.CoverageReportCommandService;
import com.oAT.web.coveragecore.report.CoverageReportGenerationRequest;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/api/projects/{projectId}/apps/{appId}/coverage/universal/{sourceType}")
@CrossOrigin(originPatterns = "*", allowCredentials = "true", methods = {RequestMethod.POST, RequestMethod.OPTIONS})
public class UniversalCoverageApiControl {
    private final CoverageIngestFacade coverageIngestFacade;
    private final CoverageReportCommandService coverageReportCommandService;
    private final ProjectService projectService;

    public UniversalCoverageApiControl(CoverageIngestFacade coverageIngestFacade,
                                       CoverageReportCommandService coverageReportCommandService,
                                       ProjectService projectService) {
        this.coverageIngestFacade = coverageIngestFacade;
        this.coverageReportCommandService = coverageReportCommandService;
        this.projectService = projectService;
    }

    @PostMapping(value = "/report", consumes = {"application/json", "text/plain", "*/*"})
    public ResultNotified<String> report(@PathVariable String projectId,
                                         @PathVariable String appId,
                                         @PathVariable String sourceType,
                                         @RequestBody String requestBody) {
        SourceType resolvedSourceType = resolveSourceType(sourceType);
        UniversalCoverageAliasReportRequest request = UniversalCoverageAliasReportRequest.parse(requestBody);
        String reportId = coverageIngestFacade.ingestUniversalAlias(projectId, appId, resolvedSourceType, request);
        return new ResultNotified<>(true, resolvedSourceType.name() + "覆盖率上报成功", reportId);
    }

    @RequestMapping(value = "/report", method = RequestMethod.OPTIONS)
    public void reportOptions() {
    }

    @PostMapping("/generate")
    public ResultNotified<String> generate(@PathVariable String projectId,
                                           @PathVariable String appId,
                                           @PathVariable String sourceType,
                                           @SessionAttribute UserVo user,
                                           @RequestBody(required = false) CoverageReportGenerationRequest request) {
        ensureProjectAccess(projectId, user);
        SourceType resolvedSourceType = resolveSourceType(sourceType);
        CoverageReportIndex report = coverageReportCommandService.generateUniversalReport(projectId, appId, resolvedSourceType, request);
        return new ResultNotified<>(true, resolvedSourceType.name() + "覆盖率报告生成成功", report.getId());
    }

    private SourceType resolveSourceType(String value) {
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
}
