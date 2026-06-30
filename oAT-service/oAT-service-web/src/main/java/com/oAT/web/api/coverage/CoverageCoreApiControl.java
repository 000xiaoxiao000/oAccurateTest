package com.oAT.web.api.coverage;

import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.coveragecore.model.BuildSession;
import com.oAT.web.coveragecore.model.CoverageModule;
import com.oAT.web.coveragecore.model.CoverageUnit;
import com.oAT.web.coveragecore.model.SourceCoveragePayload;
import com.oAT.web.coveragecore.query.CoverageCoreQueryService;
import com.oAT.web.coveragecore.query.CoverageUnitPage;
import com.oAT.web.coveragecore.query.CoverageUnitQuery;
import com.oAT.web.coveragecore.report.CoverageMethodSummary;
import com.oAT.web.coveragecore.report.CoverageOverview;
import com.oAT.web.coveragecore.report.CoverageOverviewQueryService;
import com.oAT.web.coveragecore.report.CoverageReportCommandService;
import com.oAT.web.coveragecore.report.CoverageReportMetadata;
import com.oAT.web.coveragecore.report.CoverageReportMetadataService;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v2/coverage")
public class CoverageCoreApiControl {
    private final CoverageCoreQueryService coverageCoreQueryService;
    private final CoverageReportMetadataService coverageReportMetadataService;
    private final CoverageOverviewQueryService coverageOverviewQueryService;
    private final CoverageReportCommandService coverageReportCommandService;
    private final ProjectService projectService;
    private final AppService appService;

    public CoverageCoreApiControl(CoverageCoreQueryService coverageCoreQueryService,
                                  CoverageReportMetadataService coverageReportMetadataService,
                                  CoverageOverviewQueryService coverageOverviewQueryService,
                                  CoverageReportCommandService coverageReportCommandService,
                                  ProjectService projectService,
                                  AppService appService) {
        this.coverageCoreQueryService = coverageCoreQueryService;
        this.coverageReportMetadataService = coverageReportMetadataService;
        this.coverageOverviewQueryService = coverageOverviewQueryService;
        this.coverageReportCommandService = coverageReportCommandService;
        this.projectService = projectService;
        this.appService = appService;
    }

    @GetMapping("/apps/{appId}/overview")
    public ResultNotified<CoverageOverview> overview(@PathVariable String appId,
                                                     @RequestParam String projectId,
                                                     @RequestParam String versionNumber,
                                                     @RequestParam(required = false) String reportId,
                                                     @RequestParam(required = false) String commitId,
                                                     @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        ensureAppBelongsToProject(projectId, appId);
        return new ResultNotified<>(true, "获取统一覆盖率概览成功",
                coverageOverviewQueryService.getOverview(projectId, appId, versionNumber, reportId, commitId, user));
    }

    @GetMapping("/reports/{reportId}")
    public ResultNotified<CoverageReportMetadata> report(@PathVariable String reportId,
                                                         @RequestParam String projectId,
                                                         @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        CoverageReportIndex report = coverageCoreQueryService.getReport(reportId);
        AppVo app = ensureReportAppBelongsToProject(projectId, report);
        CoverageReportMetadata metadata = new CoverageReportMetadata();
        metadata.setReport(coverageReportMetadataService.toReportSummary(report));
        metadata.setApp(coverageReportMetadataService.toAppSummary(app));
        metadata.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取统一覆盖率报告摘要成功", metadata);
    }

    @GetMapping("/reports/{reportId}/export")
    public void export(@PathVariable String reportId,
                       @RequestParam String projectId,
                       @SessionAttribute UserVo user,
                       HttpServletResponse response) throws IOException {
        ensureProjectAccess(projectId, user);
        CoverageReportIndex report = coverageCoreQueryService.getReport(reportId);
        ensureReportAppBelongsToProject(projectId, report);
        coverageReportCommandService.exportReport(reportId, response);
    }

    @GetMapping("/reports/{reportId}/export-methods")
    public void exportMethods(@PathVariable String reportId,
                              @RequestParam String projectId,
                              @SessionAttribute UserVo user,
                              HttpServletResponse response) throws IOException {
        ensureProjectAccess(projectId, user);
        CoverageReportIndex report = coverageCoreQueryService.getReport(reportId);
        ensureReportAppBelongsToProject(projectId, report);
        coverageReportCommandService.exportMethodReport(reportId, response);
    }

    @GetMapping("/reports/{reportId}/units")
    public ResultNotified<CoverageUnitsPayload> units(@PathVariable String reportId,
                                                      @RequestParam String projectId,
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
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size,
                                                      @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        CoverageReportIndex report = coverageCoreQueryService.getReport(reportId);
        ensureReportAppBelongsToProject(projectId, report);
        CoverageUnitQuery query = buildUnitQuery(className, methodName, minRate, maxRate, minBranchRate, maxBranchRate,
                minMethodRate, maxMethodRate, minComplexity, maxComplexity, page, size);
        CoverageUnitPage unitPage = coverageCoreQueryService.listUnits(reportId, query);
        CoverageUnitsPayload payload = new CoverageUnitsPayload();
        payload.setReportId(reportId);
        payload.setLanguage(report.getLanguage() == null ? report.getSourceType() : report.getLanguage());
        payload.setUnits(unitPage.getContent());
        payload.setPage(unitPage.getPage());
        payload.setSize(unitPage.getSize());
        payload.setTotalElements(unitPage.getTotalElements());
        payload.setTotalPages(unitPage.getTotalPages());
        return new ResultNotified<>(true, "获取统一覆盖率单元成功", payload);
    }

    @GetMapping("/reports/{reportId}/modules")
    public ResultNotified<CoverageModulesPayload> modules(@PathVariable String reportId,
                                                          @RequestParam String projectId,
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
                                                          @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        CoverageReportIndex report = coverageCoreQueryService.getReport(reportId);
        ensureReportAppBelongsToProject(projectId, report);
        CoverageUnitQuery query = buildUnitQuery(className, methodName, minRate, maxRate, minBranchRate, maxBranchRate,
                minMethodRate, maxMethodRate, minComplexity, maxComplexity, null, null);
        CoverageModulesPayload payload = new CoverageModulesPayload();
        payload.setReportId(reportId);
        payload.setLanguage(report.getLanguage() == null ? report.getSourceType() : report.getLanguage());
        payload.setModules(coverageCoreQueryService.listModules(reportId, query));
        return new ResultNotified<>(true, "获取统一覆盖率模块成功", payload);
    }

    @GetMapping("/reports/{reportId}/source")
    public ResultNotified<SourceCoveragePayload> source(@PathVariable String reportId,
                                                        @RequestParam String projectId,
                                                        @RequestParam String unitKey,
                                                        @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        CoverageReportIndex report = coverageCoreQueryService.getReport(reportId);
        ensureReportAppBelongsToProject(projectId, report);
        return new ResultNotified<>(true, "获取结构化源码覆盖率成功", coverageCoreQueryService.getSourceCoverage(reportId, unitKey));
    }

    @GetMapping("/reports/{reportId}/methods")
    public ResultNotified<CoverageMethodsPayload> methods(@PathVariable String reportId,
                                                          @RequestParam String projectId,
                                                          @RequestParam String unitKey,
                                                          @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        CoverageReportIndex report = coverageCoreQueryService.getReport(reportId);
        ensureReportAppBelongsToProject(projectId, report);
        CoverageMethodsPayload payload = new CoverageMethodsPayload();
        payload.setReportId(reportId);
        payload.setUnitKey(unitKey);
        payload.setLanguage(report.getLanguage() == null ? report.getSourceType() : report.getLanguage());
        payload.setMethods(coverageCoreQueryService.listMethods(reportId, unitKey));
        return new ResultNotified<>(true, "获取统一覆盖率方法摘要成功", payload);
    }

    @GetMapping("/apps/{appId}/build-sessions")
    public ResultNotified<BuildSessionsPayload> buildSessions(@PathVariable String appId,
                                                              @RequestParam String projectId,
                                                              @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        ensureAppBelongsToProject(projectId, appId);
        BuildSessionsPayload payload = new BuildSessionsPayload();
        payload.setAppId(appId);
        payload.setSessions(coverageCoreQueryService.listBuildSessions(appId));
        return new ResultNotified<>(true, "获取构建会话成功", payload);
    }

    private ProjectVo ensureProjectAccess(String projectId, UserVo user) {
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        Assert.notNull(project, "找不到指定项目,或者您没有该项目的访问权限");
        return project;
    }

    private AppVo ensureReportAppBelongsToProject(String projectId, CoverageReportIndex report) {
        Assert.notNull(report, "覆盖率报告不存在");
        return ensureAppBelongsToProject(projectId, report.getAppId());
    }

    private AppVo ensureAppBelongsToProject(String projectId, String appId) {
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        Assert.isTrue(projectId.equals(app.getCreateProjectId()), "应用不属于当前项目");
        return app;
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

    public static class CoverageUnitsPayload {
        private String reportId;
        private String language;
        private List<CoverageUnit> units;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;

        public String getReportId() { return reportId; }
        public void setReportId(String reportId) { this.reportId = reportId; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public List<CoverageUnit> getUnits() { return units; }
        public void setUnits(List<CoverageUnit> units) { this.units = units; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public long getTotalElements() { return totalElements; }
        public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    }

    public static class BuildSessionsPayload {
        private String appId;
        private List<BuildSession> sessions;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public List<BuildSession> getSessions() { return sessions; }
        public void setSessions(List<BuildSession> sessions) { this.sessions = sessions; }
    }

    public static class CoverageMethodsPayload {
        private String reportId;
        private String unitKey;
        private String language;
        private List<CoverageMethodSummary> methods;

        public String getReportId() { return reportId; }
        public void setReportId(String reportId) { this.reportId = reportId; }
        public String getUnitKey() { return unitKey; }
        public void setUnitKey(String unitKey) { this.unitKey = unitKey; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public List<CoverageMethodSummary> getMethods() { return methods; }
        public void setMethods(List<CoverageMethodSummary> methods) { this.methods = methods; }
    }

    public static class CoverageModulesPayload {
        private String reportId;
        private String language;
        private List<CoverageModule> modules;

        public String getReportId() { return reportId; }
        public void setReportId(String reportId) { this.reportId = reportId; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public List<CoverageModule> getModules() { return modules; }
        public void setModules(List<CoverageModule> modules) { this.modules = modules; }
    }
}
