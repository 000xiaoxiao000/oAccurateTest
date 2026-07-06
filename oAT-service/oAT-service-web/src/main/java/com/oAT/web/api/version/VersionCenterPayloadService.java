package com.oAT.web.api.version;

import com.oAT.web.api.version.VersionApiPayloads.AppSummary;
import com.oAT.web.api.version.VersionApiPayloads.CompareJobSummary;
import com.oAT.web.api.version.VersionApiPayloads.CompareReportSummary;
import com.oAT.web.api.version.VersionApiPayloads.CoverageReportCard;
import com.oAT.web.api.version.VersionApiPayloads.VersionCenterPayload;
import com.oAT.web.api.version.VersionApiPayloads.VersionItemSummary;
import com.oAT.web.common.DateUtil;
import com.oAT.web.coverage.FrontendCoverageReportRepository;
import com.oAT.web.coverage.UniversalCoverageRawRepository;
import com.oAT.web.coveragecore.report.CoverageReportAnalysisService;
import com.oAT.web.coveragecore.report.CoverageReportCommandService;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.AppService;
import com.oAT.web.service.VersionService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.CompareJobVo;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.UserVo;
import com.oAT.web.service.entity.VersionCompareReportVo;
import com.oAT.web.service.entity.VersionItemVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class VersionCenterPayloadService {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final VersionService versionService;
    private final AppService appService;
    private final CoverageReportCommandService coverageReportCommandService;
    private final CoverageReportAnalysisService coverageReportAnalysisService;
    private final FrontendCoverageReportRepository frontendCoverageReportRepository;
    private final UniversalCoverageRawRepository universalCoverageRawRepository;

    public VersionCenterPayloadService(VersionService versionService,
                                       AppService appService,
                                       CoverageReportCommandService coverageReportCommandService,
                                       CoverageReportAnalysisService coverageReportAnalysisService,
                                       FrontendCoverageReportRepository frontendCoverageReportRepository,
                                       UniversalCoverageRawRepository universalCoverageRawRepository) {
        this.versionService = versionService;
        this.appService = appService;
        this.coverageReportCommandService = coverageReportCommandService;
        this.coverageReportAnalysisService = coverageReportAnalysisService;
        this.frontendCoverageReportRepository = frontendCoverageReportRepository;
        this.universalCoverageRawRepository = universalCoverageRawRepository;
    }

    public VersionCenterPayload buildVersionCenter(String projectId, String appId, AppVo app, String currentUserRole) {
        List<VersionItemVo> versions = new ArrayList<>(versionService.getVersionItemList(projectId, appId));
        versions.sort(Comparator.comparing(VersionItemVo::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));

        List<VersionCompareReportVo> compareReports = new ArrayList<>(versionService.getCompareReportList(projectId, appId));
        compareReports.sort(Comparator.comparing(VersionCompareReportVo::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));

        List<CoverageReportIndex> coverageReports = new ArrayList<>(coverageReportCommandService.getReportsByAppId(appId));
        coverageReports.sort(Comparator.comparing(CoverageReportIndex::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));

        VersionCenterPayload payload = new VersionCenterPayload();
        payload.setApp(toAppSummary(app));
        payload.setApps(toAppSummaries(appService.getAppList(projectId)));
        payload.setCurrentUserRole(currentUserRole);
        payload.setVersions(versions.stream().map(item -> toVersionItemSummary(item, app, appId)).collect(Collectors.toList()));
        payload.setPackageVersions(versions.stream()
                .filter(item -> {
                    String file = item.getProgramFile();
                    if (!StringUtils.hasText(file)) {
                        return false;
                    }
                    String lower = file.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".jar") || lower.endsWith(".war");
                })
                .map(item -> toVersionItemSummary(item, app, appId))
                .collect(Collectors.toList()));
        payload.setCompareReports(compareReports.stream().map(this::toCompareReportSummary).collect(Collectors.toList()));
        payload.setCoverageReports(coverageReports.stream()
                .map(report -> toCoverageReportCard(report, coverageReportAnalysisService.hasNewerData(appId, report.getVersionNumber(), report)))
                .collect(Collectors.toList()));
        return payload;
    }

    public CompareJobSummary toCompareJobSummary(CompareJobVo job) {
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

    public AppSummary toAppSummary(AppVo app) {
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
        summary.setSourceType(StringUtils.hasText(app.getLanguage()) ? app.getLanguage() : "JAVA");
        return summary;
    }

    public String resolveUserRole(List<ProjectMemberVo> members, UserVo user) {
        for (ProjectMemberVo member : members) {
            if (user.getName() != null && user.getName().equals(member.getMemberName()) && member.getRole() != null) {
                return member.getRole().name();
            }
        }
        return ProjectMemberVo.Role.visitor.name();
    }

    private List<AppSummary> toAppSummaries(List<AppVo> apps) {
        return apps.stream().map(this::toAppSummary).collect(Collectors.toList());
    }

    private VersionItemSummary toVersionItemSummary(VersionItemVo item, AppVo app, String appId) {
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
        summary.setRawCoverageSourceTypes(resolveRawCoverageSourceTypes(appId, summary));
        return summary;
    }

    private List<String> resolveRawCoverageSourceTypes(String appId, VersionItemSummary summary) {
        LinkedHashSet<String> sourceTypes = new LinkedHashSet<>();
        String versionNumber = summary.getVersionNumber();
        String commitId = summary.getRepoCommitId();
        if (frontendCoverageReportRepository.existsByAppAndVersionOrCommit(appId, versionNumber, commitId)
                || (summary.isCurrent() && frontendCoverageReportRepository.existsByApp(appId))) {
            sourceTypes.add("FRONTEND");
        }
        for (String sourceType : Arrays.asList("GO", "PYTHON", "CPP")) {
            if (universalCoverageRawRepository.existsByAppTypeAndVersionOrCommit(appId, sourceType, versionNumber, commitId)) {
                sourceTypes.add(sourceType);
            }
        }
        return new ArrayList<>(sourceTypes);
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
        card.setSourceType(StringUtils.hasText(report.getSourceType()) ? report.getSourceType() : "JAVA");
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

    private String formatDate(java.util.Date date) {
        return date == null ? null : new SimpleDateFormat(DATE_TIME_PATTERN, Locale.CHINA).format(date);
    }
}
