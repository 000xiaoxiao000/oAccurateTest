package com.oAT.web.coveragecore.report;

import com.oAT.web.common.CoverageMethodKeyUtil;
import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.ClassCoverageIndex.MethodCoverageDetail;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.entity.CoverageComparisonVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CoverageComparisonQueryService {
    private static final int REPORT_TYPE_VERSION_FULL = 0;
    private static final int REPORT_TYPE_INCREMENTAL = 1;

    private final CoverageReportRepository coverageReportRepository;
    private final ClassCoverageRepository classCoverageRepository;

    public CoverageComparisonQueryService(CoverageReportRepository coverageReportRepository,
                                          ClassCoverageRepository classCoverageRepository) {
        this.coverageReportRepository = coverageReportRepository;
        this.classCoverageRepository = classCoverageRepository;
    }

    public CoverageComparisonVo getComparison(String reportId) {
        CoverageComparisonVo comparison = new CoverageComparisonVo();
        if (!StringUtils.hasText(reportId)) {
            return comparison;
        }
        CoverageReportIndex currentReport = coverageReportRepository.findById(reportId).orElse(null);
        if (currentReport == null) {
            return comparison;
        }

        CoverageReportIndex previousReport = selectPreviousReport(currentReport);
        if (previousReport == null) {
            return comparison;
        }

        List<ClassCoverageIndex> currentClasses = classCoverageRepository.findByReportId(currentReport.getId());
        List<ClassCoverageIndex> previousClasses = classCoverageRepository.findByReportId(previousReport.getId());

        Map<String, Boolean> previousCoverage = new HashMap<>();
        for (ClassCoverageIndex classCoverage : previousClasses) {
            if (classCoverage.getMethods() == null) {
                continue;
            }
            for (MethodCoverageDetail method : classCoverage.getMethods()) {
                previousCoverage.put(buildMethodKey(classCoverage.getClassName(), method), isMethodCoveredForComparison(method));
            }
        }

        for (ClassCoverageIndex classCoverage : currentClasses) {
            if (classCoverage.getMethods() == null) {
                continue;
            }
            for (MethodCoverageDetail method : classCoverage.getMethods()) {
                String key = buildMethodKey(classCoverage.getClassName(), method);
                boolean currentCovered = isMethodCoveredForComparison(method);
                Boolean previousCovered = previousCoverage.get(key);

                if (previousCovered == null) {
                    if (currentCovered) {
                        comparison.getAddedMethods().add(new CoverageComparisonVo.MethodDiff(
                                classCoverage.getClassName(), method.getMethodName(), method.getMethodDesc()));
                    }
                    continue;
                }

                if (currentCovered) {
                    if (!previousCovered) {
                        comparison.getAddedMethods().add(new CoverageComparisonVo.MethodDiff(
                                classCoverage.getClassName(), method.getMethodName(), method.getMethodDesc()));
                    } else {
                        comparison.getStableMethods().add(new CoverageComparisonVo.MethodDiff(
                                classCoverage.getClassName(), method.getMethodName(), method.getMethodDesc()));
                    }
                } else if (previousCovered) {
                    comparison.getDecreasedMethods().add(new CoverageComparisonVo.MethodDiff(
                            classCoverage.getClassName(), method.getMethodName(), method.getMethodDesc()));
                }
            }
        }

        comparison.setAddedCount(comparison.getAddedMethods().size());
        comparison.setStableCount(comparison.getStableMethods().size());
        comparison.setDecreasedCount(comparison.getDecreasedMethods().size());
        return comparison;
    }

    private CoverageReportIndex selectPreviousReport(CoverageReportIndex currentReport) {
        if (currentReport == null) {
            return null;
        }
        if (isIncrementalReport(currentReport.getReportType())) {
            CoverageReportIndex baseReport = selectExplicitBaseReport(currentReport);
            if (baseReport != null) {
                return baseReport;
            }
        }
        List<CoverageReportIndex> repoReports = coverageReportRepository.findByAppIdAndVersionNumber(
                currentReport.getAppId(), currentReport.getVersionNumber());
        return selectOlderSameTypeReport(currentReport, repoReports);
    }

    private CoverageReportIndex selectExplicitBaseReport(CoverageReportIndex currentReport) {
        if (!StringUtils.hasText(currentReport.getBaseVersionNumber())) {
            return null;
        }
        List<CoverageReportIndex> baseReports = coverageReportRepository.findByAppIdAndVersionNumber(
                currentReport.getAppId(), currentReport.getBaseVersionNumber());
        if (baseReports == null || baseReports.isEmpty()) {
            return null;
        }
        List<CoverageReportIndex> candidates = new ArrayList<>();
        for (CoverageReportIndex report : baseReports) {
            if (report == null || !StringUtils.hasText(report.getId()) || report.getId().equals(currentReport.getId())) {
                continue;
            }
            if (isIncrementalReport(report.getReportType())) {
                continue;
            }
            if (StringUtils.hasText(currentReport.getBaseRepoCommitId())
                    && !Objects.equals(normalizeText(currentReport.getBaseRepoCommitId()), normalizeText(report.getRepoCommitId()))) {
                continue;
            }
            candidates.add(report);
        }
        return latestReport(candidates);
    }

    private CoverageReportIndex selectOlderSameTypeReport(CoverageReportIndex currentReport, List<CoverageReportIndex> repoReports) {
        if (repoReports == null || repoReports.isEmpty()) {
            return null;
        }
        List<CoverageReportIndex> olderReports = new ArrayList<>();
        for (CoverageReportIndex report : repoReports) {
            if (report == null || !StringUtils.hasText(report.getId()) || report.getId().equals(currentReport.getId())) {
                continue;
            }
            if (normalizeReportType(report.getReportType()) != normalizeReportType(currentReport.getReportType())) {
                continue;
            }
            if (currentReport.getCreateTime() != null && report.getCreateTime() != null
                    && !report.getCreateTime().before(currentReport.getCreateTime())) {
                continue;
            }
            olderReports.add(report);
        }
        if (olderReports.isEmpty()) {
            return null;
        }
        olderReports.sort((a, b) -> {
            if (a.getCreateTime() == null) {
                return 1;
            }
            if (b.getCreateTime() == null) {
                return -1;
            }
            return b.getCreateTime().compareTo(a.getCreateTime());
        });
        if (StringUtils.hasText(currentReport.getRepoBranch())) {
            for (CoverageReportIndex report : olderReports) {
                if (currentReport.getRepoBranch().equals(report.getRepoBranch())) {
                    return report;
                }
            }
        }
        return olderReports.get(0);
    }

    private CoverageReportIndex latestReport(List<CoverageReportIndex> reports) {
        if (reports == null || reports.isEmpty()) {
            return null;
        }
        reports.sort((a, b) -> {
            if (a.getCreateTime() == null) {
                return 1;
            }
            if (b.getCreateTime() == null) {
                return -1;
            }
            return b.getCreateTime().compareTo(a.getCreateTime());
        });
        return reports.get(0);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean isIncrementalReport(Integer reportType) {
        return normalizeReportType(reportType) == REPORT_TYPE_INCREMENTAL;
    }

    private int normalizeReportType(Integer reportType) {
        return reportType == null ? REPORT_TYPE_VERSION_FULL : reportType;
    }

    private String buildMethodKey(String className, MethodCoverageDetail method) {
        return className + "#" + CoverageMethodKeyUtil.buildMethodKey(method.getMethodName(), method.getMethodDesc());
    }

    private boolean isMethodCoveredForComparison(MethodCoverageDetail method) {
        if (method == null) {
            return false;
        }
        if (method.getCoveredLines() > 0) {
            return true;
        }
        return method.getCoveredLineNumbers() != null && !method.getCoveredLineNumbers().isEmpty() || method.isCovered();
    }
}
