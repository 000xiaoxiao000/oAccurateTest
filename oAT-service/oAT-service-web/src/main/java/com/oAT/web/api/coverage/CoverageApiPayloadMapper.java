package com.oAT.web.api.coverage;

import com.oAT.web.api.coverage.CoverageApiPayloads.AppSummary;
import com.oAT.web.api.coverage.CoverageApiPayloads.ClassCoverageSummary;
import com.oAT.web.api.coverage.CoverageApiPayloads.CoverageCodePayload;
import com.oAT.web.api.coverage.CoverageApiPayloads.CoverageComparisonSummary;
import com.oAT.web.api.coverage.CoverageApiPayloads.CoverageOverviewPayload;
import com.oAT.web.api.coverage.CoverageApiPayloads.CoverageReportSummary;
import com.oAT.web.api.coverage.CoverageApiPayloads.MethodCoverageSummary;
import com.oAT.web.api.coverage.CoverageApiPayloads.MethodDiffSummary;
import com.oAT.web.api.coverage.CoverageApiPayloads.PageSummary;
import com.oAT.web.api.coverage.CoverageApiPayloads.ProjectSummary;
import com.oAT.web.api.coverage.CoverageApiPayloads.VersionSummary;
import com.oAT.web.common.PaletteColors;
import com.oAT.web.coveragecore.model.CoverageBranch;
import com.oAT.web.coveragecore.model.CoverageFunction;
import com.oAT.web.coveragecore.model.CoverageLine;
import com.oAT.web.coveragecore.model.CoverageUnit;
import com.oAT.web.coveragecore.model.SourceCoverageLine;
import com.oAT.web.coveragecore.model.SourceCoveragePayload;
import com.oAT.web.coveragecore.query.CoverageUnitPage;
import com.oAT.web.coveragecore.report.CoverageOverview;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.VersionItemVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class CoverageApiPayloadMapper {
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    public CoverageOverviewPayload toLegacyOverviewPayload(CoverageOverview overview, String projectId) {
        CoverageOverviewPayload payload = new CoverageOverviewPayload();
        payload.setProject(toLegacyProjectSummary(overview.getProject()));
        payload.setApps(overview.getApps().stream().map(this::toLegacyAppSummary).collect(Collectors.toList()));
        payload.setApp(toLegacyAppSummary(overview.getApp()));
        payload.setVersion(toLegacyVersionSummary(overview.getVersion()));
        payload.setReport(toLegacyReportSummary(overview.getReport()));
        payload.setVersionFullReport(toLegacyReportSummary(overview.getVersionFullReport()));
        payload.setCurrentCommitReport(toLegacyReportSummary(overview.getCurrentCommitReport()));
        payload.setIncrementalReport(toLegacyReportSummary(overview.getIncrementalReport()));
        payload.setHasNewerData(overview.getHasNewerData());
        payload.setComparison(toLegacyComparisonSummary(overview.getComparison()));
        payload.setVersionFullComparison(toLegacyComparisonSummary(overview.getVersionFullComparison()));
        payload.setCurrentCommitComparison(toLegacyComparisonSummary(overview.getCurrentCommitComparison()));
        payload.setIncrementalComparison(toLegacyComparisonSummary(overview.getIncrementalComparison()));
        payload.setCurrentUserRole(overview.getCurrentUserRole());
        payload.setMascotPrimary(computeMascotPrimary(projectId, payload.getProject() == null ? "" : payload.getProject().getName()));
        return payload;
    }

    public CoverageCodePayload toCoverageCodePayload(AppVo app,
                                                     CoverageReportIndex report,
                                                     String className,
                                                     List<com.oAT.web.coveragecore.report.CoverageMethodSummary> methods,
                                                     SourceCoveragePayload sourceCoverage,
                                                     String currentUserRole) {
        CoverageCodePayload payload = new CoverageCodePayload();
        payload.setApp(toAppSummary(app));
        payload.setReport(toCoverageReportSummary(report));
        payload.setClassName(className);
        payload.setMethods(toMethodSummaries(methods));
        payload.setSourceCoverage(sourceCoverage);
        payload.setColoredSourceHtml(toLegacyColoredSource(sourceCoverage));
        payload.setCurrentUserRole(currentUserRole);
        return payload;
    }

    public PageSummary<ClassCoverageSummary> toPageSummary(CoverageUnitPage page) {
        PageSummary<ClassCoverageSummary> summary = new PageSummary<>();
        summary.setContent(page.getContent().stream().map(this::toClassCoverageSummary).collect(Collectors.toList()));
        summary.setPage(page.getPage());
        summary.setSize(page.getSize());
        summary.setTotalElements(page.getTotalElements());
        summary.setTotalPages(page.getTotalPages());
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
        summary.setSourceType("JAVA");
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
        summary.setCreateTimeText(formatDate(item.getCreateTime()));
        return summary;
    }

    public CoverageReportSummary toCoverageReportSummary(CoverageReportIndex report) {
        if (report == null) {
            return null;
        }
        CoverageReportSummary summary = new CoverageReportSummary();
        summary.setId(report.getId());
        summary.setAppId(report.getAppId());
        summary.setVersionNumber(report.getVersionNumber());
        summary.setRepoBranch(report.getRepoBranch());
        summary.setRepoCommitId(report.getRepoCommitId());
        summary.setCreateTimeText(formatDate(report.getCreateTime()));
        summary.setSourceType(normalizeSourceType(report.getSourceType()));
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

    public String formatDate(java.util.Date date) {
        return date == null ? null : new SimpleDateFormat(DATE_TIME_PATTERN, Locale.CHINA).format(date);
    }

    public CoverageUnitQueryFields buildUnitQueryFields(String className,
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
        return new CoverageUnitQueryFields(className, methodName, minRate, maxRate, minBranchRate, maxBranchRate,
                minMethodRate, maxMethodRate, minComplexity, maxComplexity, page, size);
    }

    private String computeMascotPrimary(String projectId, String projectName) {
        int hash = Math.abs((projectId + ":" + projectName).hashCode());
        return PaletteColors.pickPrimary(hash / 5 + 13);
    }

    private ProjectSummary toLegacyProjectSummary(com.oAT.web.coveragecore.report.ProjectSummary item) {
        if (item == null) {
            return null;
        }
        ProjectSummary summary = new ProjectSummary();
        summary.setId(item.getId());
        summary.setName(item.getName());
        summary.setDescribe(item.getDescribe());
        return summary;
    }

    private AppSummary toLegacyAppSummary(com.oAT.web.coveragecore.report.AppSummary item) {
        AppSummary summary = new AppSummary();
        if (item == null) {
            return summary;
        }
        summary.setId(item.getId());
        summary.setName(item.getName());
        summary.setCurrentVersion(item.getCurrentVersion());
        summary.setCurrentBranch(item.getCurrentBranch());
        summary.setCurrentCommitId(item.getCurrentCommitId());
        summary.setSourceType(item.getSourceType());
        return summary;
    }

    private VersionSummary toLegacyVersionSummary(com.oAT.web.coveragecore.report.VersionSummary item) {
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
        summary.setCreateTimeText(item.getCreateTimeText());
        return summary;
    }

    private CoverageReportSummary toLegacyReportSummary(com.oAT.web.coveragecore.report.CoverageReportSummary item) {
        if (item == null) {
            return null;
        }
        CoverageReportSummary summary = new CoverageReportSummary();
        summary.setId(item.getId());
        summary.setAppId(item.getAppId());
        summary.setVersionNumber(item.getVersionNumber());
        summary.setRepoBranch(item.getRepoBranch());
        summary.setRepoCommitId(item.getRepoCommitId());
        summary.setCreateTimeText(item.getCreateTimeText());
        summary.setSourceType(item.getSourceType());
        summary.setLastProcessedTime(item.getLastProcessedTime());
        summary.setTotalClasses(item.getTotalClasses());
        summary.setCoveredClasses(item.getCoveredClasses());
        summary.setTotalMethods(item.getTotalMethods());
        summary.setCoveredMethods(item.getCoveredMethods());
        summary.setTotalBranches(item.getTotalBranches());
        summary.setCoveredBranches(item.getCoveredBranches());
        summary.setTotalBranchTargets(item.getTotalBranchTargets());
        summary.setCoveredBranchTargets(item.getCoveredBranchTargets());
        summary.setTotalLines(item.getTotalLines());
        summary.setCoveredLines(item.getCoveredLines());
        summary.setTotalComplexity(item.getTotalComplexity());
        summary.setSnapshotCount(item.getSnapshotCount());
        summary.setReportType(item.getReportType());
        summary.setBaseVersionNumber(item.getBaseVersionNumber());
        summary.setBaseRepoCommitId(item.getBaseRepoCommitId());
        return summary;
    }

    private CoverageComparisonSummary toLegacyComparisonSummary(com.oAT.web.coveragecore.report.CoverageComparisonSummary item) {
        if (item == null) {
            return null;
        }
        CoverageComparisonSummary summary = new CoverageComparisonSummary();
        summary.setAddedCount(item.getAddedCount());
        summary.setStableCount(item.getStableCount());
        summary.setDecreasedCount(item.getDecreasedCount());
        summary.setAddedMethods(toLegacyMethodDiffSummaries(item.getAddedMethods()));
        summary.setStableMethods(toLegacyMethodDiffSummaries(item.getStableMethods()));
        summary.setDecreasedMethods(toLegacyMethodDiffSummaries(item.getDecreasedMethods()));
        return summary;
    }

    private List<MethodDiffSummary> toLegacyMethodDiffSummaries(List<com.oAT.web.coveragecore.report.MethodDiffSummary> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        return items.stream().map(item -> {
            MethodDiffSummary summary = new MethodDiffSummary();
            summary.setClassName(item.getClassName());
            summary.setMethodName(item.getMethodName());
            summary.setMethodDesc(item.getMethodDesc());
            return summary;
        }).collect(Collectors.toList());
    }

    private ClassCoverageSummary toClassCoverageSummary(CoverageUnit unit) {
        UnitMetrics metrics = UnitMetrics.from(unit);
        ClassCoverageSummary summary = new ClassCoverageSummary();
        summary.setClassName(firstText(unit.getUnitKey(), unit.getDisplayName(), unit.getSourcePath()));
        summary.setTotalMethods(metrics.totalFunctions());
        summary.setCoveredMethods(metrics.coveredFunctions());
        summary.setTotalBranches(metrics.totalBranches());
        summary.setCoveredBranches(metrics.coveredBranches());
        summary.setTotalBranchTargets(metrics.totalBranches());
        summary.setCoveredBranchTargets(metrics.coveredBranches());
        summary.setTotalLines(metrics.totalLines());
        summary.setCoveredLines(metrics.coveredLines());
        summary.setTotalComplexity(metrics.complexity());
        summary.setLineRate(metrics.lineRate());
        summary.setBranchRate(metrics.branchRate());
        summary.setMethodRate(metrics.methodRate());
        summary.setHasCodeChanges(false);
        return summary;
    }

    private List<MethodCoverageSummary> toMethodSummaries(List<com.oAT.web.coveragecore.report.CoverageMethodSummary> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        return items.stream().map(item -> {
            MethodCoverageSummary summary = new MethodCoverageSummary();
            summary.setMethodName(item.getMethodName());
            summary.setMethodDesc(item.getMethodDesc());
            summary.setTotalLines(item.getTotalLines());
            summary.setCoveredLines(item.getCoveredLines());
            summary.setTotalBranches(item.getTotalBranches());
            summary.setCoveredBranches(item.getCoveredBranches());
            summary.setComplexity(item.getComplexity());
            summary.setCovered(item.isCovered());
            summary.setTotalBranchTargets(item.getTotalBranchTargets());
            summary.setCoveredBranchTargets(item.getCoveredBranchTargets());
            summary.setBranchRate(item.getBranchRate());
            summary.setHasCodeChanges(false);
            return summary;
        }).collect(Collectors.toList());
    }

    private String toLegacyColoredSource(SourceCoveragePayload sourceCoverage) {
        if (sourceCoverage == null || sourceCoverage.getLines() == null || sourceCoverage.getLines().isEmpty()) {
            return "Coverage data not found";
        }
        StringBuilder html = new StringBuilder();
        html.append("<pre class=\"coverage-source\">");
        for (SourceCoverageLine line : sourceCoverage.getLines()) {
            String cssClass = line.getHits() > 0 ? "covered" : "uncovered";
            html.append("<span class=\"line ")
                    .append(cssClass)
                    .append("\" data-line=\"")
                    .append(line.getLine())
                    .append("\">")
                    .append(HtmlUtils.htmlEscape(line.getText() == null ? "" : line.getText()))
                    .append("</span>\n");
        }
        html.append("</pre>");
        return html.toString();
    }

    private String normalizeSourceType(String sourceType) {
        return StringUtils.hasText(sourceType) ? sourceType : "JAVA";
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    public record CoverageUnitQueryFields(String className,
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
    }

    private record UnitMetrics(int totalLines,
                               int coveredLines,
                               int totalBranches,
                               int coveredBranches,
                               int totalFunctions,
                               int coveredFunctions,
                               int complexity) {
        static UnitMetrics from(CoverageUnit unit) {
            List<CoverageLine> lines = !unit.getLines().isEmpty()
                    ? unit.getLines()
                    : unit.getFunctions().stream().flatMap(function -> function.getLines().stream()).toList();
            List<CoverageBranch> branches = !unit.getBranches().isEmpty()
                    ? unit.getBranches()
                    : unit.getFunctions().stream().flatMap(function -> function.getBranches().stream()).toList();
            int totalFunctions = unit.getFunctions().size();
            int coveredFunctions = (int) unit.getFunctions().stream().filter(UnitMetrics::isFunctionCovered).count();
            int complexity = unit.getFunctions().stream().mapToInt(CoverageFunction::getComplexity).sum();
            return new UnitMetrics(
                    lines.size(),
                    (int) lines.stream().filter(line -> line.getHits() > 0).count(),
                    branches.size(),
                    (int) branches.stream().filter(branch -> branch.getHits() > 0).count(),
                    totalFunctions,
                    coveredFunctions,
                    complexity);
        }

        private static boolean isFunctionCovered(CoverageFunction function) {
            return function.getLines().stream().anyMatch(line -> line.getHits() > 0)
                    || function.getBranches().stream().anyMatch(branch -> branch.getHits() > 0);
        }

        double lineRate() {
            return totalLines > 0 ? (double) coveredLines / totalLines * 100 : 0;
        }

        double branchRate() {
            return totalBranches > 0 ? (double) coveredBranches / totalBranches * 100 : 0;
        }

        double methodRate() {
            return totalFunctions > 0 ? (double) coveredFunctions / totalFunctions * 100 : 0;
        }
    }
}
