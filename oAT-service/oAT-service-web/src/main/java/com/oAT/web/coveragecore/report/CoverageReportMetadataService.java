package com.oAT.web.coveragecore.report;

import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.CoverageComparisonVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.VersionItemVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class CoverageReportMetadataService {
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    public CoverageReportSummary toReportSummary(CoverageReportIndex report) {
        if (report == null) {
            return null;
        }
        CoverageReportSummary summary = new CoverageReportSummary();
        summary.setId(report.getId());
        summary.setAppId(report.getAppId());
        summary.setVersionNumber(report.getVersionNumber());
        summary.setRepoBranch(report.getRepoBranch());
        summary.setRepoCommitId(report.getRepoCommitId());
        summary.setCreateTimeText(report.getCreateTime() == null ? null : new SimpleDateFormat(DATE_TIME_PATTERN, Locale.CHINA).format(report.getCreateTime()));
        summary.setSourceType(StringUtils.hasText(report.getSourceType()) ? report.getSourceType() : "JAVA");
        summary.setLanguage(StringUtils.hasText(report.getLanguage()) ? report.getLanguage() : summary.getSourceType());
        summary.setBuildId(report.getBuildId());
        summary.setTestStage(report.getTestStage());
        summary.setLastProcessedTime(report.getLastProcessedTime());
        summary.setTotalClasses(report.getTotalClasses());
        summary.setCoveredClasses(report.getCoveredClasses());
        summary.setTotalMethods(report.getTotalMethods());
        summary.setCoveredMethods(report.getCoveredMethods());
        summary.setTotalBranches(report.getTotalBranches());
        summary.setCoveredBranches(report.getCoveredBranches());
        summary.setTotalBranchTargets(report.getTotalBranchTargets());
        summary.setCoveredBranchTargets(report.getCoveredBranchTargets());
        summary.setTotalLines(report.getTotalLines());
        summary.setCoveredLines(report.getCoveredLines());
        summary.setTotalComplexity(report.getTotalComplexity());
        summary.setSnapshotCount(report.getSnapshotCount());
        summary.setReportType(report.getReportType());
        summary.setBaseVersionNumber(report.getBaseVersionNumber());
        summary.setBaseRepoCommitId(report.getBaseRepoCommitId());
        return summary;
    }

    public AppSummary toAppSummary(AppVo app) {
        AppSummary summary = new AppSummary();
        if (app == null) {
            return summary;
        }
        summary.setId(app.getId());
        summary.setName(app.getName());
        summary.setCurrentVersion(app.getCurrentVersion());
        summary.setCurrentBranch(app.getCurrentBranch());
        summary.setCurrentCommitId(app.getCurrentCommitId());
        summary.setLanguage(StringUtils.hasText(app.getLanguage()) ? app.getLanguage() : "JAVA");
        summary.setSourceType(summary.getLanguage());
        summary.setLanguageConfig(app.getLanguageConfig());
        return summary;
    }

    public ProjectSummary toProjectSummary(ProjectVo project) {
        if (project == null) {
            return null;
        }
        ProjectSummary summary = new ProjectSummary();
        summary.setId(project.getId());
        summary.setName(project.getName());
        summary.setDescribe(project.getDescribe());
        return summary;
    }

    public VersionSummary toVersionSummary(VersionItemVo item) {
        if (item == null) {
            return null;
        }
        VersionSummary summary = new VersionSummary();
        summary.setId(item.getId());
        summary.setVersionNumber(item.getVersionNumber());
        summary.setDescribe(item.getDescribe());
        summary.setRepoBranch(item.getRepoBranch());
        summary.setRepoCommitId(item.getRepoCommitId());
        summary.setProgramFile(item.getProgramFile());
        summary.setProgramName(item.getProgramName());
        summary.setCreateTimeText(item.getCreateTime() == null ? null : new SimpleDateFormat(DATE_TIME_PATTERN, Locale.CHINA).format(item.getCreateTime()));
        return summary;
    }

    public CoverageComparisonSummary toComparisonSummary(CoverageComparisonVo comparison) {
        if (comparison == null) {
            return null;
        }
        CoverageComparisonSummary summary = new CoverageComparisonSummary();
        summary.setAddedCount(comparison.getAddedCount());
        summary.setStableCount(comparison.getStableCount());
        summary.setDecreasedCount(comparison.getDecreasedCount());
        summary.setAddedMethods(toMethodDiffSummaries(comparison.getAddedMethods()));
        summary.setStableMethods(toMethodDiffSummaries(comparison.getStableMethods()));
        summary.setDecreasedMethods(toMethodDiffSummaries(comparison.getDecreasedMethods()));
        return summary;
    }

    private List<MethodDiffSummary> toMethodDiffSummaries(List<CoverageComparisonVo.MethodDiff> methods) {
        if (methods == null) {
            return List.of();
        }
        return methods.stream().map(method -> {
            MethodDiffSummary summary = new MethodDiffSummary();
            summary.setClassName(method.getClassName());
            summary.setMethodName(method.getMethodName());
            summary.setMethodDesc(method.getMethodDesc());
            return summary;
        }).collect(Collectors.toList());
    }
}
