package com.oAT.web.coveragecore.report;

import com.oAT.web.coveragecore.index.CoverageEsIndexService;
import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CoverageTrendQueryService {
    private final CoverageReportRepository coverageReportRepository;
    private final CoverageEsIndexService coverageEsIndexService;

    public CoverageTrendQueryService(CoverageReportRepository coverageReportRepository,
                                     CoverageEsIndexService coverageEsIndexService) {
        this.coverageReportRepository = coverageReportRepository;
        this.coverageEsIndexService = coverageEsIndexService;
    }

    public List<Map<String, Object>> getTrendData(String appId, String versionNumber) {
        List<Map<String, Object>> esTrend = coverageEsIndexService.searchTrendData(appId, versionNumber);
        if (!CollectionUtils.isEmpty(esTrend)) {
            return esTrend;
        }

        List<CoverageReportIndex> repoReports = coverageReportRepository.findByAppIdAndVersionNumber(appId, versionNumber);
        if (repoReports == null || repoReports.isEmpty()) {
            return Collections.emptyList();
        }

        List<CoverageReportIndex> reports = new ArrayList<>(repoReports);
        reports.sort((a, b) -> {
            if (a.getCreateTime() == null) {
                return 1;
            }
            if (b.getCreateTime() == null) {
                return -1;
            }
            return b.getCreateTime().compareTo(a.getCreateTime());
        });

        List<Map<String, Object>> trend = new ArrayList<>();
        SimpleDateFormat formatter = new SimpleDateFormat("MM-dd HH:mm");
        for (CoverageReportIndex report : reports) {
            Map<String, Object> point = new HashMap<>();
            point.put("time", report.getCreateTime() == null ? null : formatter.format(report.getCreateTime()));
            point.put("timestamp", report.getCreateTime() == null ? null : report.getCreateTime().getTime());
            point.put("reportId", report.getId());
            point.put("versionNumber", report.getVersionNumber());
            point.put("repoBranch", report.getRepoBranch());
            point.put("repoCommitId", report.getRepoCommitId());
            point.put("reportType", normalizeReportType(report.getReportType()));
            point.put("baseVersionNumber", report.getBaseVersionNumber());
            point.put("baseRepoCommitId", report.getBaseRepoCommitId());
            point.put("lineCoverage", report.getTotalLines() > 0 ? (double) report.getCoveredLines() / report.getTotalLines() * 100 : 0);
            point.put("methodCoverage", report.getTotalMethods() > 0 ? (double) report.getCoveredMethods() / report.getTotalMethods() * 100 : 0);
            point.put("branchCoverage", report.getTotalBranchTargets() > 0 ? (double) report.getCoveredBranchTargets() / report.getTotalBranchTargets() * 100 : 0);
            trend.add(point);
        }
        return trend;
    }

    private int normalizeReportType(Integer reportType) {
        return reportType == null ? 0 : reportType;
    }
}
