package com.oAT.web.coveragecore.report;

import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.VersionService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.UserVo;
import com.oAT.web.service.entity.VersionItemVo;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

@Service
public class CoverageOverviewQueryService {
    private final CoverageReportRepository coverageReportRepository;
    private final CoverageReportMetadataService metadataService;
    private final ProjectService projectService;
    private final AppService appService;
    private final VersionService versionService;
    private final CoverageReportAnalysisService analysisService;

    public CoverageOverviewQueryService(CoverageReportRepository coverageReportRepository,
                                        CoverageReportMetadataService metadataService,
                                        ProjectService projectService,
                                        AppService appService,
                                        VersionService versionService,
                                        CoverageReportAnalysisService analysisService) {
        this.coverageReportRepository = coverageReportRepository;
        this.metadataService = metadataService;
        this.projectService = projectService;
        this.appService = appService;
        this.versionService = versionService;
        this.analysisService = analysisService;
    }

    public CoverageOverview getOverview(String projectId,
                                        String appId,
                                        String versionNumber,
                                        String reportId,
                                        String commitId,
                                        UserVo user) {
        Assert.hasText(projectId, "projectId不能为空");
        Assert.hasText(appId, "appId不能为空");
        Assert.hasText(versionNumber, "versionNumber不能为空");
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        Assert.isTrue(projectId.equals(app.getCreateProjectId()), "应用不属于当前项目");

        VersionItemVo version = findVersion(projectId, appId, versionNumber, commitId);
        String versionCommitId = version == null ? null : version.getRepoCommitId();
        CoverageReportIndex report = StringUtils.hasText(reportId)
                ? coverageReportRepository.findById(reportId).orElse(null)
                : latestReport(appId, versionNumber, commitId);
        String targetCommitId = StringUtils.hasText(commitId)
                ? commitId
                : report == null ? firstText(app.getCurrentCommitId(), versionCommitId) : report.getRepoCommitId();

        CoverageReportIndex versionFullReport = latestReportByType(appId, versionNumber, 0, versionCommitId);
        CoverageReportIndex currentCommitReport = StringUtils.hasText(targetCommitId)
                ? latestReportByType(appId, versionNumber, 2, targetCommitId)
                : null;
        if (currentCommitReport == null && StringUtils.hasText(targetCommitId) && !targetCommitId.equals(versionCommitId)) {
            currentCommitReport = latestReportByType(appId, versionNumber, 0, targetCommitId);
        }

        CoverageReportIndex incrementalReport = latestReportByType(appId, versionNumber, 1, targetCommitId);
        CoverageReportIndex selectedReport = report;
        if (selectedReport == null) {
            selectedReport = currentCommitReport != null ? currentCommitReport : incrementalReport;
        }
        if (selectedReport != null && Integer.valueOf(1).equals(selectedReport.getReportType())) {
            incrementalReport = selectedReport;
        } else if (selectedReport != null) {
            currentCommitReport = selectedReport;
        }
        if (incrementalReport == null) {
            incrementalReport = latestReportByType(appId, versionNumber, 1, null);
        }

        CoverageReportIndex freshnessReport = selectedReport != null ? selectedReport : currentCommitReport;
        CoverageOverview overview = new CoverageOverview();
        overview.setProject(metadataService.toProjectSummary(projectService.getProject(projectId)));
        overview.setApps(appService.getAppList(projectId).stream().map(metadataService::toAppSummary).toList());
        overview.setApp(metadataService.toAppSummary(app));
        overview.setVersion(metadataService.toVersionSummary(version));
        overview.setReport(metadataService.toReportSummary(selectedReport != null ? selectedReport : currentCommitReport));
        overview.setVersionFullReport(metadataService.toReportSummary(versionFullReport));
        overview.setCurrentCommitReport(metadataService.toReportSummary(currentCommitReport));
        overview.setIncrementalReport(metadataService.toReportSummary(incrementalReport));
        overview.setHasNewerData(freshnessReport == null
                ? analysisService.hasNewerData(appId, versionNumber, null)
                : analysisService.hasNewerData(appId, versionNumber, freshnessReport));
        overview.setComparison(metadataService.toComparisonSummary(selectedReport == null ? null : analysisService.getComparison(selectedReport.getId())));
        overview.setVersionFullComparison(metadataService.toComparisonSummary(versionFullReport == null ? null : analysisService.getComparison(versionFullReport.getId())));
        overview.setCurrentCommitComparison(metadataService.toComparisonSummary(currentCommitReport == null ? null : analysisService.getComparison(currentCommitReport.getId())));
        overview.setIncrementalComparison(metadataService.toComparisonSummary(incrementalReport == null ? null : analysisService.getComparison(incrementalReport.getId())));
        overview.setCurrentUserRole(resolveUserRole(projectId, user));
        return overview;
    }

    private CoverageReportIndex latestReport(String appId, String versionNumber, String commitId) {
        List<CoverageReportIndex> reports = StringUtils.hasText(commitId)
                ? coverageReportRepository.findByAppIdAndVersionNumberAndRepoCommitId(appId, versionNumber, commitId)
                : coverageReportRepository.findByAppIdAndVersionNumber(appId, versionNumber);
        return latest(reports);
    }

    private CoverageReportIndex latestReportByType(String appId, String versionNumber, Integer reportType, String commitId) {
        List<CoverageReportIndex> reports = StringUtils.hasText(commitId)
                ? coverageReportRepository.findByAppIdAndVersionNumberAndReportTypeAndRepoCommitId(appId, versionNumber, reportType, commitId)
                : coverageReportRepository.findByAppIdAndVersionNumberAndReportType(appId, versionNumber, reportType);
        return latest(reports);
    }

    private CoverageReportIndex latest(List<CoverageReportIndex> reports) {
        return reports == null ? null : reports.stream()
                .max(Comparator.comparing(CoverageReportIndex::getCreateTime, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
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

    private String resolveUserRole(String projectId, UserVo user) {
        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);
        for (ProjectMemberVo member : members) {
            if (user.getName() != null && user.getName().equals(member.getMemberName()) && member.getRole() != null) {
                return member.getRole().name();
            }
        }
        return ProjectMemberVo.Role.visitor.name();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
