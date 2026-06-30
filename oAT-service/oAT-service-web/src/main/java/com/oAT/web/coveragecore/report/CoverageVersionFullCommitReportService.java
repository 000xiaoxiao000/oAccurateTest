package com.oAT.web.coveragecore.report;

import com.oAT.web.common.Job;
import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CoverageVersionFullCommitReportService {

    @Autowired
    private ClassCoverageRepository classCoverageRepository;

    @Autowired
    private CoverageCommitReportSelectionService coverageCommitReportSelectionService;

    @Autowired
    private CoverageClassAggregationService coverageClassAggregationService;

    @Autowired
    private CoverageReportSummaryPersistenceService coverageReportSummaryPersistenceService;

    public String tryGenerate(String appId, String versionNumber, String branch, String latestCommitId, Job<String> job) {
        List<CoverageReportIndex> commitReports = coverageCommitReportSelectionService.selectVersionCommitReports(appId, versionNumber);
        if (commitReports.isEmpty()) {
            if (job != null) {
                job.getLogger().info("未找到可汇总的本次 Commit 覆盖率报告，将按当前版本快照生成版本全量报告。");
            }
            return null;
        }

        CoverageReportIndex latestCommitReport = coverageCommitReportSelectionService.selectLatestCodeReport(commitReports, branch, latestCommitId);
        if (latestCommitReport == null) {
            return null;
        }

        if (job != null) {
            job.getProgress().next("汇总本版本 Commit 覆盖率", 45);
            job.getLogger().info("版本全量报告将汇总 " + commitReports.size() + " 份本次 Commit 报告，源码着色使用最新 Commit [" + CoverageCommitKeys.shortCommit(latestCommitReport.getRepoCommitId()) + "]。");
        }

        CoverageReportIndex reusableReport = coverageCommitReportSelectionService.findReusableVersionFullReport(appId, versionNumber, latestCommitReport, commitReports);
        if (reusableReport != null) {
            if (job != null) {
                job.getLogger().info("Commit 报告集合未变化，复用已有版本全量报告：" + reusableReport.getId());
                job.getProgress().next("复用已有版本全量报告", 100);
            }
            return reusableReport.getId();
        }

        Map<String, ClassCoverageIndex> coverageMap = loadLatestCodeCoverageSkeleton(latestCommitReport);
        if (coverageMap.isEmpty()) {
            if (job != null) {
                job.getLogger().info("最新 Commit 报告缺少类覆盖率明细，将按当前版本快照生成版本全量报告。");
            }
            return null;
        }

        Map<String, Map<String, ClassCoverageIndex>> perCommitSnapshots = new LinkedHashMap<>();
        int processed = 0;
        for (CoverageReportIndex commitReport : commitReports) {
            List<ClassCoverageIndex> classCoverages = classCoverageRepository.findByReportId(commitReport.getId());
            if (classCoverages == null || classCoverages.isEmpty()) {
                continue;
            }
            Map<String, ClassCoverageIndex> commitCoverageMap = coverageClassAggregationService.toCoverageMap(classCoverages);
            perCommitSnapshots.put(commitReport.getId(), commitCoverageMap);
            coverageClassAggregationService.mergeCommitCoverageIntoLatestSkeleton(coverageMap, commitCoverageMap);
            processed++;
            if (job != null) {
                job.getProgress().loaded = processed;
                job.getProgress().total = commitReports.size();
            }
        }

        if (perCommitSnapshots.isEmpty()) {
            if (job != null) {
                job.getLogger().info("本次 Commit 报告明细为空，将按当前版本快照生成版本全量报告。");
            }
            return null;
        }

        coverageClassAggregationService.recalculateClassCoverageCounters(coverageMap.values());
        coverageClassAggregationService.markCrossCommitCoverageChanges(coverageMap, perCommitSnapshots, job);

        CoverageReportIndex report = new CoverageReportIndex();
        report.setId(UUID.randomUUID().toString());
        report.setAppId(appId);
        report.setVersionNumber(versionNumber);
        report.setRepoBranch(StringUtils.hasText(branch) ? branch : latestCommitReport.getRepoBranch());
        report.setRepoCommitId(latestCommitReport.getRepoCommitId());
        report.setCreateTime(new Date());
        report.setLastProcessedTime(latestCommitReport.getLastProcessedTime());
        report.setReportType(CoverageReportTypes.VERSION_FULL);
        report.setSnapshotFingerprint(coverageCommitReportSelectionService.buildCommitReportFingerprint(commitReports));
        report.setSnapshotLastUpdateTime(coverageCommitReportSelectionService.buildCommitReportLastUpdateTime(commitReports));
        report.setSnapshotCount(commitReports.stream()
                .mapToInt(reportIndex -> reportIndex.getSnapshotCount() == null ? 0 : Math.max(0, reportIndex.getSnapshotCount()))
                .sum());
        report.setSnapshotIds(commitReports.stream()
                .map(CoverageReportIndex::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(",")));

        if (job != null) {
            job.getProgress().next("保存版本全量汇总报告", 20);
        }
        coverageReportSummaryPersistenceService.saveAndCalculateSummary(report, coverageMap, null);
        return report.getId();
    }

    private Map<String, ClassCoverageIndex> loadLatestCodeCoverageSkeleton(CoverageReportIndex latestCommitReport) {
        List<ClassCoverageIndex> latestClasses = classCoverageRepository.findByReportId(latestCommitReport.getId());
        return coverageClassAggregationService.loadLatestCodeCoverageSkeleton(latestClasses);
    }
}
