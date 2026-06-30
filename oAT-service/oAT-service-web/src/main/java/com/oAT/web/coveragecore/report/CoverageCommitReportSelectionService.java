package com.oAT.web.coveragecore.report;

import com.oAT.web.esDao.entity.StandardDate;
import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CoverageCommitReportSelectionService {

    private final CoverageReportRepository coverageReportRepository;
    private final CoverageSnapshotContextSupport coverageSnapshotContextSupport;

    public CoverageCommitReportSelectionService(CoverageReportRepository coverageReportRepository,
                                                CoverageSnapshotContextSupport coverageSnapshotContextSupport) {
        this.coverageReportRepository = coverageReportRepository;
        this.coverageSnapshotContextSupport = coverageSnapshotContextSupport;
    }

    public List<CoverageReportIndex> selectVersionCommitReports(String appId, String versionNumber) {
        List<CoverageReportIndex> reports = coverageReportRepository.findByAppIdAndVersionNumber(appId, versionNumber);
        if (reports == null || reports.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, CoverageReportIndex> latestByCommit = new LinkedHashMap<>();
        for (CoverageReportIndex report : reports) {
            if (report == null || !StringUtils.hasText(report.getId())) {
                continue;
            }
            if (CoverageReportTypes.normalize(report.getReportType()) != CoverageReportTypes.CURRENT_COMMIT) {
                continue;
            }
            String commitKey = CoverageCommitKeys.normalize(report.getRepoCommitId());
            if (!StringUtils.hasText(commitKey)) {
                commitKey = report.getId();
            }
            CoverageReportIndex existing = latestByCommit.get(commitKey);
            if (existing == null || isReportAfter(report, existing)) {
                latestByCommit.put(commitKey, report);
            }
        }
        List<CoverageReportIndex> selected = new ArrayList<>(latestByCommit.values());
        selected.sort(Comparator.comparing(CoverageReportIndex::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())));
        return selected;
    }

    public CoverageReportIndex selectLatestCodeReport(List<CoverageReportIndex> commitReports, String branch, String latestCommitId) {
        if (commitReports == null || commitReports.isEmpty()) {
            return null;
        }
        if (StringUtils.hasText(latestCommitId)) {
            String expectedCommit = CoverageCommitKeys.normalize(latestCommitId);
            for (CoverageReportIndex report : commitReports) {
                if (expectedCommit.equals(CoverageCommitKeys.normalize(report.getRepoCommitId()))) {
                    return report;
                }
            }
        }
        List<CoverageReportIndex> candidates = new ArrayList<>(commitReports);
        if (StringUtils.hasText(branch)) {
            List<CoverageReportIndex> branchMatches = candidates.stream()
                    .filter(report -> branch.equals(report.getRepoBranch()))
                    .collect(Collectors.toList());
            if (!branchMatches.isEmpty()) {
                candidates = branchMatches;
            }
        }
        candidates.sort((a, b) -> {
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });
        return candidates.get(0);
    }

    public CoverageReportIndex findReusableVersionFullReport(String appId, String versionNumber,
                                                             CoverageReportIndex latestCommitReport,
                                                             List<CoverageReportIndex> commitReports) {
        List<CoverageReportIndex> reports = coverageReportRepository.findByAppIdAndVersionNumberAndReportType(appId, versionNumber, CoverageReportTypes.VERSION_FULL);
        if (reports == null || reports.isEmpty()) {
            return null;
        }
        String fingerprint = buildCommitReportFingerprint(commitReports);
        String latestCommit = CoverageCommitKeys.normalize(latestCommitReport.getRepoCommitId());
        for (CoverageReportIndex report : reports) {
            if (!Objects.equals(fingerprint, report.getSnapshotFingerprint())) {
                continue;
            }
            if (StringUtils.hasText(latestCommit) && !latestCommit.equals(CoverageCommitKeys.normalize(report.getRepoCommitId()))) {
                continue;
            }
            return report;
        }
        return null;
    }

    public String buildCommitReportFingerprint(List<CoverageReportIndex> commitReports) {
        if (commitReports == null || commitReports.isEmpty()) {
            return "";
        }
        return coverageSnapshotContextSupport.sha256Hex(commitReports.stream()
                .map(report -> String.join("|",
                        report.getId() == null ? "" : report.getId(),
                        CoverageCommitKeys.normalize(report.getRepoCommitId()) == null ? "" : CoverageCommitKeys.normalize(report.getRepoCommitId()),
                        report.getCreateTime() == null ? "" : String.valueOf(report.getCreateTime().getTime()),
                        report.getSnapshotFingerprint() == null ? "" : report.getSnapshotFingerprint()))
                .collect(Collectors.joining(";")));
    }

    public String buildCommitReportLastUpdateTime(List<CoverageReportIndex> commitReports) {
        java.util.Date latest = null;
        if (commitReports != null) {
            for (CoverageReportIndex report : commitReports) {
                java.util.Date candidate = report.getCreateTime();
                if (candidate != null && (latest == null || candidate.after(latest))) {
                    latest = candidate;
                }
            }
        }
        return latest == null ? null : new SimpleDateFormat(StandardDate.dateFormat).format(latest);
    }

    private boolean isReportAfter(CoverageReportIndex candidate, CoverageReportIndex current) {
        if (candidate == null) return false;
        if (current == null) return true;
        if (candidate.getCreateTime() == null) return false;
        if (current.getCreateTime() == null) return true;
        return candidate.getCreateTime().after(current.getCreateTime());
    }
}
