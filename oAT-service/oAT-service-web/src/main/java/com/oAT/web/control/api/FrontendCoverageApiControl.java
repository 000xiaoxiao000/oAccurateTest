package com.oAT.web.control.api;

import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.common.UtilJson;
import com.oAT.web.coverage.FrontendCoverageService;
import com.oAT.web.coverage.FrontendCoverageService.FrontendCoverageGenerateRequest;
import com.oAT.web.coverage.FrontendCoverageService.FrontendCoverageIngestResult;
import com.oAT.web.coverage.FrontendCoverageService.FrontendCoverageReportRequest;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/api/projects/{projectId}/apps/{appId}/coverage/frontend")
@CrossOrigin(originPatterns = "*", allowCredentials = "true", methods = {RequestMethod.POST, RequestMethod.OPTIONS})
public class FrontendCoverageApiControl {
    private final FrontendCoverageService frontendCoverageService;
    private final ProjectService projectService;

    public FrontendCoverageApiControl(FrontendCoverageService frontendCoverageService,
                                      ProjectService projectService) {
        this.frontendCoverageService = frontendCoverageService;
        this.projectService = projectService;
    }

    @PostMapping(value = "/report", consumes = {"application/json", "text/plain", "*/*"})
    public ResultNotified<FrontendCoverageIngestResult> report(@PathVariable String projectId,
                                                               @PathVariable String appId,
                                                               @RequestHeader HttpHeaders headers,
                                                               @RequestBody String requestBody) {
        FrontendCoverageReportRequest request = parseReportRequest(requestBody);
        String headerRequestId = firstHeader(headers, "X-OAT-Relay-Request-Id", "X-OAT-Request-Id", "X-Request-Id");
        if (StringUtils.hasText(headerRequestId)) {
            request.setRequestId(headerRequestId);
        }
        FrontendCoverageIngestResult result = frontendCoverageService.saveReport(projectId, appId, request);
        return new ResultNotified<>(true, "前端覆盖率原始数据已接收", result);
    }

    @RequestMapping(value = "/report", method = RequestMethod.OPTIONS)
    public void reportOptions() {
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

    private FrontendCoverageReportRequest parseReportRequest(String requestBody) {
        Assert.hasText(requestBody, "请求体不能为空");
        try {
            return UtilJson.getObjectMapper().readValue(requestBody, FrontendCoverageReportRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("前端覆盖率上报请求体不是有效 JSON", e);
        }
    }

    private String firstHeader(HttpHeaders headers, String... names) {
        if (headers == null || names == null) {
            return null;
        }
        for (String name : names) {
            String value = headers.getFirst(name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private ProjectVo ensureProjectAccess(String projectId, UserVo user) {
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        Assert.notNull(project, "找不到指定项目,或者您没有该项目的访问权限");
        return project;
    }
}
